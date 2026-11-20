package nl.davidengelen.redsquare.saito

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Base64

class SaitoTransactionTest {
    @Test fun repeatedAuthenticatedReadsKeepIndependentBodiesAndRecipientLists() {
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixture, never fund.
        val envelope = SaitoTransaction.create(wallet, JsonObject().apply { addProperty("text", "Unchanged signed text") }, 1).envelope()
        repeat(2) {
            val read = SaitoEnvelope.decodePayload(envelope)
            assertEquals("Unchanged signed text", read.body.asJsonObject.get("text").asString)
            assertEquals(listOf(wallet.publicKey), read.recipients)
            read.body.asJsonObject.addProperty("text", "A caller changed its own object")
            assertThrows(UnsupportedOperationException::class.java) { (read.recipients as MutableList<String>).clear() }
        }
        assertEquals("Unchanged signed text", SaitoEnvelope.decode(envelope).body.get("text").asString)
    }

    @Test fun anAlteredMessageWithTheSameSignatureNeverReusesAnAuthenticatedRead() {
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixture, never fund.
        val original = SaitoTransaction.create(wallet, JsonObject().apply { addProperty("text", "Authentic text") }, 1).envelope()
        SaitoEnvelope.decodePayload(original)
        val altered = JsonParser.parseString(original).asJsonObject.apply {
            addProperty("m", Base64.getEncoder().encodeToString("{\"text\":\"Different unsigned text\"}".toByteArray(Charsets.UTF_8)))
        }.toString()
        try {
            SaitoEnvelope.decodePayload(altered)
            org.junit.Assert.fail("Altering message bytes cannot reuse the original signature")
        } catch (_: IllegalArgumentException) { }
        assertEquals("Authentic text", SaitoEnvelope.decodePayload(original).body.asJsonObject.get("text").asString)
    }

    @Test(expected = SaitoTransaction.InvalidMessage::class)
    fun jsonEscapingCountsTowardTheCompleteEncodedMessageLimit() {
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixture, never fund.
        // Quotes require two bytes each in JSON, before the surrounding metadata.
        val body = com.google.gson.JsonObject().apply { addProperty("text", "\"".repeat(524_288)) }
        SaitoTransaction.create(wallet, body, 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedUnicodeIsRejectedBeforeSigningInsteadOfChangingThePublishedText() {
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixture, never fund.
        val body = com.google.gson.JsonObject().apply {
            addProperty("module", "RedSquare")
            addProperty("request", "create tweet")
            add("data", com.google.gson.JsonObject().apply { addProperty("text", "Broken \uD800 text") })
        }
        SaitoTransaction.create(wallet, body, 1)
    }

    @Test fun replyRecipientAndOutputIndicesMatchIndependentWasmBytes() {
        val reference = requireNotNull(javaClass.getResourceAsStream("/native-posting-reference.json")).bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("reply")
        }
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Public unsafe fixture, never fund.
        val transaction = SaitoTransaction.create(wallet, reference.getAsJsonObject("body"), reference.get("timestamp").asLong,
            reference.getAsJsonArray("recipients").map { it.asString })
        assertIndependentVector(reference, wallet, transaction)
    }

    @Test fun rootTransactionAndPeerHopMatchIndependentWasmAndEllipticBytes() {
        val reference = requireNotNull(javaClass.getResourceAsStream("/native-posting-reference.json")).bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject
        }
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixture, never fund.
        val transaction = SaitoTransaction.create(wallet, reference.getAsJsonObject("body"), reference.get("timestamp").asLong)
        assertIndependentVector(reference, wallet, transaction)
    }

    private fun assertIndependentVector(reference: JsonObject, wallet: SaitoWallet, transaction: SaitoTransaction) {
        assertEquals(reference.get("id").asString, transaction.id)
        assertEquals(reference.get("networkBase64").asString, transaction.encoded())
        assertEquals(reference.get("envelope").asString, transaction.envelope())
        assertEquals(reference.get("broadcastBase64").asString,
            Base64.getEncoder().encodeToString(transaction.broadcast(wallet, reference.get("destination").asString)))
        assertEquals(transaction.encoded(), SaitoTransaction.restore(reference.get("networkBase64").asString).encoded())
    }

}
