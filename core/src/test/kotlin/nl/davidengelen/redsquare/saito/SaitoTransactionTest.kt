package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64

class SaitoTransactionTest {
    @Test fun rootTransactionAndPeerHopMatchIndependentWasmAndEllipticBytes() {
        val reference = requireNotNull(javaClass.getResourceAsStream("/native-posting-reference.json")).bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject
        }
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixture, never fund.
        val transaction = SaitoTransaction.create(wallet, reference.getAsJsonObject("body"), reference.get("timestamp").asLong)
        assertEquals(reference.get("id").asString, transaction.id)
        assertEquals(reference.get("networkBase64").asString, transaction.encoded())
        assertEquals(reference.get("envelope").asString, transaction.envelope())
        assertEquals(reference.get("broadcastBase64").asString,
            Base64.getEncoder().encodeToString(transaction.broadcast(wallet, reference.get("destination").asString)))
        assertEquals(transaction.encoded(), SaitoTransaction.restore(reference.get("networkBase64").asString).encoded())
    }
}
