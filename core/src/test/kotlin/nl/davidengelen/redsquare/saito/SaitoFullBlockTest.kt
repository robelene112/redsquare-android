package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.util.Base64

class SaitoFullBlockTest {
    private val reference get() = requireNotNull(javaClass.getResourceAsStream("/native-block-reference.json"))
        .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }

    @Test fun matchingSignaturesDoNotAuthenticateChangedOriginalFundingCoordinates() {
        val reference = reference
        val payment = SaitoPayment.restore(reference.getAsJsonObject("payment").get("candidateBase64").asString)
        val changed = Base64.getDecoder().decode(reference.getAsJsonObject("mutations").get("changedOriginalInputBase64").asString)
        val block = SaitoFullBlock.parse(changed)
        assertEquals("b33d2fc4aee4333b6cb1e395c0b852aa3b1aa268e21702b3819b34b2a3eab289", block.hash)
        assertFalse("An identical signature/root must not hide a changed original input location", block.contains(payment))
        assertEquals(BigInteger("2000000"), payment.inputs.first().block)
    }

    @Test fun exactFundedPaymentCanBeRecognizedInAnIndependentSignedFullBlock() {
        val reference = reference
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
