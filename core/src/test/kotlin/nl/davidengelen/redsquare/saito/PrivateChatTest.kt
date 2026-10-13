package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class PrivateChatTest {
    @Test fun importedConversationIdsRecoverAuthenticatedCounterpartiesWithoutSavedContactKeys() {
        val reference = requireNotNull(javaClass.getResourceAsStream("/native-private-chat-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject }
        val options = JsonParser.parseString(SaitoWallet.fromPrivateKey("0".repeat(63) + "2").exportJson()).asJsonObject
        options.add("chat", com.google.gson.JsonObject().apply {
            add("groups", com.google.gson.JsonArray().apply { add(reference.get("groupId")) })
        })
        val wallet = SaitoWallet.fromBackup(options.toString())
        assertEquals(emptyList<String>(), wallet.chatContacts())
        assertEquals(listOf(reference.get("groupId").asString), wallet.chatHistoryIds())
        val original = reference.get("archiveEnvelope").asString
        val recovered = PrivateChat.recover(wallet, original, wallet.chatHistoryIds().single())
        assertEquals(reference.get("sender").asString, recovered.counterparty)
        assertEquals(reference.get("signature").asString, recovered.message.id)
        assertEquals(reference.getAsJsonObject("plaintext").get("message").asString, recovered.message.text)
        assertEquals(original, recovered.envelope)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            PrivateChat.recover(wallet, original, "0".repeat(64))
        }
        assertEquals(options, JsonParser.parseString(wallet.exportJson()))
    }
    @Test fun anUnsignedRelayCannotChangeTheOriginalAuthenticatedPrivateMessage() {
        val reference = requireNotNull(javaClass.getResourceAsStream("/native-private-chat-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject }
        val recipient = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val inner = reference.getAsJsonObject("signedInner").deepCopy()
        fun packet(): ByteArray {
            val relay = com.google.gson.JsonObject().apply {
                addProperty("module", "Relay"); addProperty("request", "chat relay"); add("data", inner)
            }
            return SaitoWire.applicationRequest(reference.get("sender").asString,
                relay.toString().toByteArray(Charsets.UTF_8), 1, reference.get("timestamp").asLong).drop(5).toByteArray()
        }
        val received = PrivateChat.receive(recipient, packet())
        assertEquals(reference.get("sender").asString, received.counterparty)
        assertEquals(reference.get("signature").asString, received.message.id)
        assertEquals(reference.getAsJsonObject("plaintext").get("message").asString, received.message.text)
        // Alter only the inner signed payload, while leaving the outer unsigned relay well-formed.
        inner.addProperty("buffer", java.util.Base64.getEncoder().encodeToString("\"forged\"".toByteArray()))
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { PrivateChat.receive(recipient, packet()) }
    }

    @Test fun correctlySignedMalformedCipherFieldsDoNotBecomeReadablePrivateMessages() {
        val reference = requireNotNull(javaClass.getResourceAsStream("/native-private-chat-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val recipient = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val sender = reference.getAsJsonArray("vectors")[0].asJsonObject.get("sender").asString
        reference.getAsJsonArray("malformedCiphertexts").forEach { element ->
            val envelope = element.asJsonObject.get("archiveEnvelope").asString
            assertEquals(element.asJsonObject.get("signature").asString, SaitoEnvelope.decodePayload(envelope).id)
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                PrivateChat.read(recipient, envelope, sender)
            }
        }
    }

    @Test fun importedExchangedPassphrasesKeepTheExistingClientsExactLetterCase() {
        val reference = requireNotNull(javaClass.getResourceAsStream("/native-private-chat-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[3].asJsonObject }
        fun restore(scalar: String, counterparty: String): SaitoWallet {
            val options = JsonParser.parseString(SaitoWallet.fromPrivateKey("0".repeat(63) + scalar).exportJson()).asJsonObject
            options.add("keys", com.google.gson.JsonArray().apply { add(com.google.gson.JsonObject().apply {
                addProperty("publicKey", counterparty)
                addProperty("aes_secret", reference.get("sharedSecretHex").asString)
            }) })
            return SaitoWallet.fromBackup(options.toString())
        }
        val sender = restore("1", reference.get("recipient").asString)
        val transaction = PrivateChat.create(sender, reference.get("recipient").asString,
            reference.getAsJsonObject("plaintext").get("message").asString, reference.get("timestamp").asLong,
            randomSalt = { ByteArray(8) { (it + 16).toByte() } })
        assertEquals(reference.get("networkBase64").asString, transaction.encoded())
        val recipient = restore("2", reference.get("sender").asString)
        assertEquals(reference.getAsJsonObject("plaintext").get("message").asString,
            PrivateChat.read(recipient, reference.get("archiveEnvelope").asString, reference.get("sender").asString).text)
    }

    @Test fun aLegacyWalletKeyFieldRestoresTheExistingClientsExchangedKeyMessage() {
        val reference = requireNotNull(javaClass.getResourceAsStream("/native-private-chat-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[2].asJsonObject }
        val options = JsonParser.parseString(SaitoWallet.fromPrivateKey("0".repeat(63) + "2").exportJson()).asJsonObject
        options.add("keys", com.google.gson.JsonArray().apply { add(com.google.gson.JsonObject().apply {
            addProperty("publickey", reference.get("sender").asString) // Legacy exported field used by Saito.
            addProperty("aes_secret", reference.get("sharedSecretHex").asString)
        }) })
        val original = options.toString()
        val restored = SaitoWallet.fromBackup(original)
        val message = PrivateChat.read(restored, reference.get("archiveEnvelope").asString, reference.get("sender").asString)
        assertEquals(reference.getAsJsonObject("plaintext").get("message").asString, message.text)
        assertEquals(JsonParser.parseString(original), JsonParser.parseString(restored.exportJson()))
    }

    @Test fun nativeAndExistingClientsExchangeTheOriginalSignedCiphertextThroughRelayJson() {
        val reference = requireNotNull(javaClass.getResourceAsStream("/native-private-chat-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject }
        val sender = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe reference.
        val recipient = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val transaction = PrivateChat.create(sender, recipient.publicKey,
            reference.getAsJsonObject("plaintext").get("message").asString, reference.get("timestamp").asLong,
            randomSalt = { ByteArray(8) { it.toByte() } })
        assertEquals(reference.getAsJsonObject("signedInner"), transaction.signedJson())
        val received = PrivateChat.read(recipient, reference.getAsJsonObject("signedInner"), sender.publicKey)
        assertEquals(reference.get("signature").asString, received.id)
        assertEquals(reference.getAsJsonObject("plaintext").get("message").asString, received.text)
    }

    @Test fun importedExchangedKeysRestorePrivateHistoryAndRemainPreferredForSending() {
        val vectors = requireNotNull(javaClass.getResourceAsStream("/native-private-chat-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors") }
        val reference = vectors[2].asJsonObject
        fun imported(scalar: String, counterparty: String): SaitoWallet {
            val options = JsonParser.parseString(SaitoWallet.fromPrivateKey("0".repeat(63) + scalar).exportJson()).asJsonObject
            options.add("keys", com.google.gson.JsonArray().apply {
                add(com.google.gson.JsonObject().apply {
                    addProperty("publicKey", counterparty)
                    addProperty("aes_secret", reference.get("sharedSecretHex").asString)
                })
            })
            return SaitoWallet.fromBackup(options.toString())
        }
        val sender = imported("1", reference.get("recipient").asString)
        val transaction = PrivateChat.create(sender, reference.get("recipient").asString,
            reference.getAsJsonObject("plaintext").get("message").asString, reference.get("timestamp").asLong,
            randomSalt = { ByteArray(8) { (it + 8).toByte() } })
        assertEquals(reference.get("networkBase64").asString, transaction.encoded())
        val recipient = imported("2", reference.get("sender").asString)
        assertEquals(reference.getAsJsonObject("plaintext").get("message").asString,
            PrivateChat.read(recipient, reference.get("archiveEnvelope").asString, reference.get("sender").asString).text)
        // The same imported wallet still reads older implicit-key messages, as the existing client does.
        assertEquals(vectors[0].asJsonObject.getAsJsonObject("plaintext").get("message").asString,
            PrivateChat.read(recipient, vectors[0].asJsonObject.get("archiveEnvelope").asString,
                reference.get("sender").asString).text)
    }

    @Test fun nativePrivateSendMatchesIndependentClientEncryptionAndSignedBytes() {
        val reference = requireNotNull(javaClass.getResourceAsStream("/native-private-chat-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject }
        val sender = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe test key.
        val transaction = PrivateChat.create(sender, reference.get("recipient").asString,
            reference.getAsJsonObject("plaintext").get("message").asString, reference.get("timestamp").asLong,
            randomSalt = { ByteArray(8) { it.toByte() } }) // Fixed entropy only at the test seam.
        assertEquals(reference.get("signature").asString, transaction.id)
        assertEquals(reference.get("networkBase64").asString, transaction.encoded())
        assertEquals(reference.get("archiveEnvelope").asString, transaction.envelope())
        assertEquals(transaction.encoded(), SaitoTransaction.restore(transaction.encoded()).encoded())
    }

    @Test fun recipientReadsAnAuthenticatedEncryptedMessageFromThePinnedSaitoClient() {
        val reference = requireNotNull(javaClass.getResourceAsStream("/native-private-chat-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject }
        // Public unsafe independent reference keys. Never fund these identities.
        val recipient = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val message = PrivateChat.read(recipient, reference.get("archiveEnvelope").asString,
            reference.get("sender").asString)
        assertEquals(reference.get("signature").asString, message.id)
        assertEquals(reference.get("plaintext").asJsonObject.get("message").asString, message.text)
        assertEquals(reference.get("sender").asString, message.author)
        assertEquals(reference.get("groupId").asString, message.conversationId)
    }
}
