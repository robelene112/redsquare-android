package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.util.Base64

class SaitoFullBlockTest {
    @Test fun exactFundedPaymentCanBeRecognizedInAnIndependentSignedFullBlock() {
        val reference = requireNotNull(javaClass.getResourceAsStream("/native-block-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val blockVector = reference.getAsJsonObject("block")
        val paymentVector = reference.getAsJsonObject("payment")
        val payment = SaitoPayment.restore(paymentVector.get("candidateBase64").asString)
        val block = SaitoFullBlock.parse(Base64.getDecoder().decode(blockVector.get("fullBase64").asString))
        assertEquals(BigInteger("2000002"), block.height)
        assertEquals(BigInteger("1700000010000"), block.timestamp)
        assertEquals("b33d2fc4aee4333b6cb1e395c0b852aa3b1aa268e21702b3819b34b2a3eab289", block.hash)
        assertEquals("ab".repeat(32), block.previousHash)
        assertTrue("Original signed payment must match its independently assigned block outputs", block.contains(payment))
        assertEquals(paymentVector.get("candidateBase64").asString, payment.transaction.encoded())
    }
}
