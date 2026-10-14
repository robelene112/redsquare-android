package nl.davidengelen.redsquare.saito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import java.math.BigInteger
import org.junit.Test
import com.google.gson.JsonParser

class SaitoWalletTest {
    @Test fun namedGroupHintsSurviveFullBackupWithoutBecomingPrivateContactsOrChangingIdentity() {
        val owner=SaitoWallet.fromPrivateKey("0".repeat(63)+"2") // Public unsafe fixtures only.
        val contact=SaitoWallet.fromPrivateKey("0".repeat(63)+"1").publicKey
        val groupKey=SaitoWallet.fromPrivateKey("0".repeat(63)+"3")
        val otherGroup=SaitoWallet.fromPrivateKey("0".repeat(63)+"4").publicKey
        val options=JsonParser.parseString(owner.withChatContact(contact)
            .withNamedChatGroup(groupKey.publicKey,"Untrusted saved group name",groupKey).exportJson()).asJsonObject
        options.getAsJsonObject("chat").add("groups",com.google.gson.JsonArray().apply{
            add(groupKey.publicKey);add(PrivateChat.conversationId(owner.publicKey,contact));add(otherGroup)
            add(groupKey.publicKey);add(owner.publicKey)
        })
        val imported=SaitoWallet.fromBackup(options.toString())
        val before=imported.exportJson()
        assertEquals(listOf(groupKey.publicKey,otherGroup),imported.namedChatGroupIds())
        assertEquals(listOf(contact),imported.chatContacts())
        assertEquals(listOf(PrivateChat.conversationId(owner.publicKey,contact)),imported.chatHistoryIds())
        assertEquals(owner.publicKey,imported.publicKey)
        assertEquals(before,imported.exportJson())
        assertEquals(emptyList<String>(),owner.namedChatGroupIds())
        val keychainOnly=options.deepCopy().apply{remove("chat")}
        val keychainWallet=SaitoWallet.fromBackup(keychainOnly.toString())
        val keychainBefore=keychainWallet.exportJson()
        assertEquals(listOf(groupKey.publicKey),keychainWallet.namedChatGroupIds())
        assertEquals(listOf(contact),keychainWallet.chatContacts())
        assertEquals(keychainBefore,keychainWallet.exportJson())
        val legacy=options.deepCopy().apply{add("chat",getAsJsonObject("chat").get("groups"))}
        assertEquals(listOf(groupKey.publicKey,otherGroup),SaitoWallet.fromBackup(legacy.toString()).namedChatGroupIds())
    }

    @Test fun `an imported null keychain accepts a first private contact without changing original options`() {
        val initial = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val contact = SaitoWallet.fromPrivateKey("0".repeat(63) + "1").publicKey
        val options = JsonParser.parseString(initial.exportJson()).asJsonObject.apply {
            add("keys", com.google.gson.JsonNull.INSTANCE)
        }
        val original = SaitoWallet.fromBackup(options.toString())
        assertEquals(emptyList<String>(), original.chatContacts())
        assertEquals(1, original.chatSharedSecrets(contact).size)
        val updated = original.withChatContact(contact)
        assertEquals(listOf(contact), SaitoWallet.fromBackup(updated.exportJson()).chatContacts())
        assertEquals(options, JsonParser.parseString(original.exportJson()))
        assertEquals(initial.publicKey, updated.publicKey)
    }
    @Test fun `a new private contact survives compatible wallet export without replacing identity or module data`() {
        val initial = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Public unsafe fixture only.
        val contact = SaitoWallet.fromPrivateKey("0".repeat(63) + "1").publicKey
        val options = JsonParser.parseString(initial.exportJson()).asJsonObject.apply {
            add("Chat", JsonParser.parseString("""{"unread":3,"custom":"preserve this"}"""))
            add("keys", com.google.gson.JsonArray().apply { add(com.google.gson.JsonObject().apply {
                addProperty("publicKey", initial.publicKey); addProperty("identifier", "Example@saito")
            }) })
        }
        val original = SaitoWallet.fromBackup(options.toString())
        val updated = original.withChatContact(contact)
        val expected = options.deepCopy().apply {
            getAsJsonArray("keys").add(com.google.gson.JsonObject().apply {
                addProperty("publicKey", contact); addProperty("added", true) // Existing Saito QR contact schema.
            })
        }
        assertEquals(expected, JsonParser.parseString(updated.exportJson()))
        assertEquals(options, JsonParser.parseString(original.exportJson()))
        assertEquals(initial.publicKey, updated.publicKey)
        assertEquals(listOf(contact), SaitoWallet.fromBackup(updated.exportJson()).chatContacts())
    }

    // Published scalar-one curve fixture, never a user's wallet.
    private val syntheticPrivateKey = "0".repeat(63) + "1"

    @Test fun `a maximum length Saito username remains an outgoing wallet replacement risk after backup import`() {
        val options = JsonParser.parseString(SaitoWallet.fromPrivateKey(syntheticPrivateKey).exportJson()).asJsonObject
        options.add("keys", JsonParser.parseString("""[
            {"publicKey":"pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU","identifier":"OtherWallet@saito"},
            {"publicKey":"jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR","identifier":"N12345678901234567890123456789012345678901234@saito"}
        ]"""))
        val imported = SaitoWallet.fromBackup(options.toString())
        assertEquals("N12345678901234567890123456789012345678901234", imported.replacementRisks().savedName)
        assertEquals(options, JsonParser.parseString(imported.exportJson()))
    }

    @Test fun `cached Bound slips remain unknown assets even without separate NFT module metadata`() {
        // WalletSlip.toJson at pinned saito-js wallet.ts uses numeric slipType, not type.
        // A synthetic cached indicator, not a live NFT or chain inclusion claim.
        val options = JsonParser.parseString(SaitoWallet.fromPrivateKey(syntheticPrivateKey).exportJson()).asJsonObject
        options.getAsJsonObject("wallet").add("slips", JsonParser.parseString("""[
            {"utxokey":"0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f817980000000000000001000000000000000000000000000000000109",
             "lc":true,"spent":false,"blockId":1,"txIndex":0,"slipIndex":0,"amount":1,"slipType":9}
        ]"""))
        val restored = SaitoWallet.fromBackup(options.toString())
        assertTrue(restored.replacementRisks().otherAssetsUnknown)
        assertEquals(options, JsonParser.parseString(restored.exportJson()))
    }

    @Test fun `an existing Saito private key restores its independently verified public identity`() {
        assertEquals(
            "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR",
            SaitoWallet.fromPrivateKey(syntheticPrivateKey).publicKey,
        )
    }
    @Test fun `an empty message signs exactly as the independently checked Saito runtime`() {
        assertEquals(
            "8008843d4e0261c035aea14623aba44be40389f0492f3979f8a4b5ac1742a0337cacc2c608402a297695beb98398f52f406c6ed1247f713d97fe8b19975a16e4",
            SaitoWallet.fromPrivateKey(syntheticPrivateKey).sign(byteArrayOf()),
        )
    }
    @Test fun `independently signed Saito messages verify and altered messages fail`() {
        val message = "Red Square Android synthetic reference".toByteArray(Charsets.UTF_8)
        val signature = "25faa62d6039f9ad39f9179d3757f6982ae148888bad2b5cefd706fbdffc060019b9c7c51cd49ef41dd5d6798f44937e04bc93055dd7e215b830193cb8e61cb6"
        val publicKey = "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR"
        assertTrue(SaitoWallet.verify(message, signature, publicKey))
        assertFalse(SaitoWallet.verify("Changed message".toByteArray(), signature, publicKey))
        assertFalse(SaitoWallet.verify(message, signature, "invalid"))
    }
    @Test fun `web backup round trip retains identity and unknown Chat and module options`() {
        val original = """{"wallet":{"privateKey":"$syntheticPrivateKey","publicKey":"jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR","version":5.677},"keychain":{"keys":[{"aes_secret":"synthetic chat state"}]},"peers":[{"host":"inert.example"}]}"""
        val restored = SaitoWallet.fromBackup(original)
        assertEquals("jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR", restored.publicKey)
        assertEquals(JsonParser.parseString(original), JsonParser.parseString(restored.exportJson()))
    }

    @Test fun `fresh local identities are independently created and survive compatible backup restoration`() {
        val first = SaitoWallet.create()
        val second = SaitoWallet.create()
        assertFalse(first.publicKey == second.publicKey)
        assertEquals(44, first.publicKey.length)
        assertEquals(first.publicKey, SaitoWallet.fromBackup(first.exportJson()).publicKey)
    }
    @Test fun `UTF8 and published binary messages reproduce independent compact signatures`() {
        val wallet = SaitoWallet.fromPrivateKey(syntheticPrivateKey)
        assertEquals(
            "25faa62d6039f9ad39f9179d3757f6982ae148888bad2b5cefd706fbdffc060019b9c7c51cd49ef41dd5d6798f44937e04bc93055dd7e215b830193cb8e61cb6",
            wallet.sign("Red Square Android synthetic reference".toByteArray(Charsets.UTF_8)),
        )
        assertEquals(
            "ba0bb729f9579ce14f963a496af1ab2bcb6ccf19199c8d7d83ca78c4755c4a1e7190d9076c252804833862cd9fb746552e53c22fd3707f3bf660741bea798717",
            wallet.sign("Saito 🔴 café\n".toByteArray(Charsets.UTF_8)),
        )
        // Explicitly published Rust test material, not a user's signing key.
        val published = SaitoWallet.fromPrivateKey("4a16ffa08e5fc440772ee962c1d730041f12c7008a6e5c704d13dfd3d1905e0d")
        assertEquals("28Mh8nEhxymH9bFMhSKU51pnSQAnqURuPYkXTUqY2ueDM", published.publicKey)
        val bytes = "5a16ffa08e5fc440772ee962c1d730041f12c7008a6e5c704d13dfd3d1906e0d".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertEquals(
            "11c0e19856726c42c8ac3ec8e469057f5f8a882f7206377525db00899835b03f6ec3010d19534a5703dd9b1004b4f0e31d19582cdd5aec794541d0d0f339db7c",
            published.sign(bytes),
        )
        assertEquals(published.publicKey, SaitoWallet.fromBackup(published.exportJson()).publicKey)
    }

    @Test fun `noncanonical and malformed signatures are refused as in the Saito runtime`() {
        val signature = "8008843d4e0261c035aea14623aba44be40389f0492f3979f8a4b5ac1742a0337cacc2c608402a297695beb98398f52f406c6ed1247f713d97fe8b19975a16e4"
        val publicKey = "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR"
        val order = BigInteger("fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141", 16)
        val highS = (order - BigInteger(signature.substring(64), 16)).toString(16).padStart(64, '0')
        assertFalse(SaitoWallet.verify(byteArrayOf(), signature.take(64) + highS, publicKey))
        assertFalse(SaitoWallet.verify(byteArrayOf(), "0".repeat(128), publicKey))
        assertFalse(SaitoWallet.verify(byteArrayOf(), "not a signature", publicKey))
        assertFalse(SaitoWallet.verify(byteArrayOf(), signature, "1".repeat(44)))
    }

    @Test fun `invalid or mismatched backup keys fail before restoring an identity`() {
        listOf("0".repeat(64), "f".repeat(64), "short", "g".repeat(64)).forEach { key ->
            assertThrows(IllegalArgumentException::class.java) { SaitoWallet.fromPrivateKey(key) }
        }
        val valid = SaitoWallet.fromPrivateKey(syntheticPrivateKey).exportJson()
        val mismatched = JsonParser.parseString(valid).asJsonObject.apply {
            getAsJsonObject("wallet").addProperty("publicKey", "28Mh8nEhxymH9bFMhSKU51pnSQAnqURuPYkXTUqY2ueDM")
        }
        listOf("{}", "{invalid}", "null", mismatched.toString()).forEach { json ->
            assertThrows(IllegalArgumentException::class.java) { SaitoWallet.fromBackup(json) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            SaitoWallet.fromBackup(" ".repeat(SaitoWallet.MAX_BACKUP_BYTES + 1))
        }
        assertFalse(SaitoWallet.fromPrivateKey(syntheticPrivateKey).toString().contains(syntheticPrivateKey))
    }
    @Test fun `null-valued wallet and module backup fields survive re-export`() {
        val options = JsonParser.parseString(SaitoWallet.fromPrivateKey(syntheticPrivateKey).exportJson()).asJsonObject
        options.add("unknownModule", com.google.gson.JsonNull.INSTANCE)
        options.getAsJsonObject("wallet").add("unknownOption", com.google.gson.JsonNull.INSTANCE)
        assertEquals(options, JsonParser.parseString(SaitoWallet.fromBackup(options.toString()).exportJson()))
    }
}
