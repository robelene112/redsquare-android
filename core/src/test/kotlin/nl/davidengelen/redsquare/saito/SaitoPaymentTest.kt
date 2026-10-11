package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigInteger
import java.util.Base64

class SaitoPaymentTest {
    @Test fun fundedPaymentCanBeRestoredWithoutWeakeningZeroPaymentRestoration() {
        val vector = requireNotNull(javaClass.getResourceAsStream("/native-payment-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("payment") }
        val encoded = vector.get("networkBase64").asString
        val restored = SaitoPayment.restore(encoded)
        assertEquals(vector.get("id").asString, restored.transaction.id)
        assertEquals(encoded, restored.transaction.encoded())
        assertEquals(vector.get("envelope").asString, restored.transaction.envelope())
        try {
            SaitoTransaction.restore(encoded)
            fail("Zero-payment restoration must not accept a funded candidate")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun fundedPaymentAndPeerHopMatchIndependentPinnedWasmBytes() {
        val vector = requireNotNull(javaClass.getResourceAsStream("/native-payment-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("payment") }
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Published unsafe fixture. Never fund.
        val inputs = vector.getAsJsonArray("inputs").map { entry ->
            val row = entry.asJsonObject
            SaitoBalanceSnapshot.Slip(BigInteger(row.get("block").asString), BigInteger(row.get("ordinal").asString),
                row.get("index").asInt, BigInteger(row.get("amount").asString), row.get("type").asInt)
        }
        val body = vector.getAsJsonObject("body")
        val payment = SaitoPayment.create(wallet, inputs, vector.get("recipient").asString,
            BigInteger(vector.get("amount").asString), BigInteger(vector.get("fee").asString),
            vector.get("timestamp").asLong, body.get("hash").asString, body.get("memo").asString)
        val transaction = payment.transaction
        assertEquals(vector.get("id").asString, transaction.id)
        assertEquals(vector.get("networkBase64").asString, transaction.encoded())
        assertEquals(vector.get("envelope").asString, transaction.envelope())
        assertEquals(vector.get("broadcastBase64").asString, Base64.getEncoder().encodeToString(
            transaction.broadcast(wallet, vector.get("destination").asString)))
    }
}
