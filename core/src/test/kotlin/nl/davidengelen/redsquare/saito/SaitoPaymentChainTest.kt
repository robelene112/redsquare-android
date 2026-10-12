package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import java.math.BigInteger
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class SaitoPaymentChainTest {
    @Test fun anUnrelatedValidPaymentBlockDoesNotProveSelectedChainInclusion() {
        val vector = requireNotNull(javaClass.getResourceAsStream("/native-block-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val payment = SaitoPayment.restore(vector.getAsJsonObject("payment").get("candidateBase64").asString)
        val tipHash = "bf72deac4b8c7dae48258dd2f4c7e4c3f13ed986e27da7650abdf573027579ba"
        val snapshot = SaitoBalanceSnapshot.parse("1700000020000-2000003-$tipHash.snap\n".toByteArray(),
            payment.author, 1700000020000)
        val unrelated = Base64.getDecoder().decode(vector.getAsJsonObject("block").get("fullBase64").asString)
        assertTrue("The unrelated block really contains the original payment", SaitoFullBlock.parse(unrelated).contains(payment))
        assertNull(SaitoPaymentChain.observe(payment, snapshot) { hash ->
            if (hash == tipHash) Base64.getDecoder().decode(vector.getAsJsonObject("chainTip").get("fullBase64").asString)
            else unrelated
        })
    }

    @Test fun exactPaymentIsIncludedOnlyThroughVerifiedAncestryFromTheSelectedSnapshot() {
        val vector = requireNotNull(javaClass.getResourceAsStream("/native-block-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val payment = SaitoPayment.restore(vector.getAsJsonObject("payment").get("candidateBase64").asString)
        val tipHash = "bf72deac4b8c7dae48258dd2f4c7e4c3f13ed986e27da7650abdf573027579ba"
        val inclusionHash = "6ed747ba2da6068f793a60291131ae086a623576dbd2557c31ad854640945856"
        val snapshot = SaitoBalanceSnapshot.parse("1700000020000-2000003-$tipHash.snap\n".toByteArray(),
            payment.author, 1700000020000)
        val responses = mapOf(
            tipHash to Base64.getDecoder().decode(vector.getAsJsonObject("chainTip").get("fullBase64").asString),
            inclusionHash to Base64.getDecoder().decode(vector.getAsJsonObject("oddBlock").get("fullBase64").asString))
        val requested = ArrayList<String>()
        val inclusion = requireNotNull(SaitoPaymentChain.observe(payment, snapshot) { hash ->
            requested.add(hash); responses[hash]
        })
        assertEquals(listOf(tipHash, inclusionHash), requested)
        assertEquals(BigInteger("2000002"), inclusion.height)
        assertEquals(inclusionHash, inclusion.hash)
        assertEquals(BigInteger("2000003"), inclusion.tipHeight)
        assertEquals(tipHash, inclusion.tipHash)
        assertEquals(1700000020000, inclusion.tipTimestamp)
    }
}
