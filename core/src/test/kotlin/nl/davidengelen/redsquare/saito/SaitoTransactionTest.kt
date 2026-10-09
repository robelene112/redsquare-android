package nl.davidengelen.redsquare.saito

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64

class SaitoTransactionTest {
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
