package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import java.math.BigInteger
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class SaitoPaymentChainTest {
    private val reference get() = requireNotNull(javaClass.getResourceAsStream("/native-block-reference.json"))
        .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }

    @Test fun anUnchangedHashWithAlteredOriginalInputCoordinatesIsNotFreshInclusionEvidence() {
        val vector = reference
        val previous = SaitoPaymentChain.Inclusion(BigInteger("2000002"),
            "b33d2fc4aee4333b6cb1e395c0b852aa3b1aa268e21702b3819b34b2a3eab289", BigInteger("2000002"),
            "b33d2fc4aee4333b6cb1e395c0b852aa3b1aa268e21702b3819b34b2a3eab289", 1700000010000)
        val author = vector.getAsJsonObject("payment").get("author").asString
        val snapshot = SaitoBalanceSnapshot.parse("1700000010000-2000002-${previous.hash}.snap\n".toByteArray(), author, 1700000010000)
        val changed = Base64.getDecoder().decode(vector.getAsJsonObject("mutations").get("changedOriginalInputBase64").asString)
        assertEquals(SaitoPaymentChain.PriorStatus.UNKNOWN, SaitoPaymentChain.check(previous, snapshot) { changed })
    }

    @Test fun missingAncestorDataCannotDisplaceTheLastVerifiedInclusion() {
        val vector = reference
        val previous = SaitoPaymentChain.Inclusion(BigInteger("2000002"),
            "6ed747ba2da6068f793a60291131ae086a623576dbd2557c31ad854640945856", BigInteger("2000003"),
            "bf72deac4b8c7dae48258dd2f4c7e4c3f13ed986e27da7650abdf573027579ba", 1700000020000)
        val author = vector.getAsJsonObject("payment").get("author").asString
        val tipHash = "34a627c2eb1307a95ac0227db3aac8961eec5a085b04771b40aa545ac00bec24"
        val snapshot = SaitoBalanceSnapshot.parse("1700000020000-2000003-$tipHash.snap\n".toByteArray(), author, 1700000020000)
        assertEquals(SaitoPaymentChain.PriorStatus.UNKNOWN, SaitoPaymentChain.check(previous, snapshot) { hash ->
            if (hash == tipHash) Base64.getDecoder().decode(vector.getAsJsonObject("alternateTip").get("fullBase64").asString)
            else null
        })
        assertEquals("6ed747ba2da6068f793a60291131ae086a623576dbd2557c31ad854640945856", previous.hash)
    }

    @Test fun aVerifiedReplacementAtThePreviouslyIncludedHeightEstablishesReorganization() {
        val vector = reference
        val previous = SaitoPaymentChain.Inclusion(BigInteger("2000002"),
            "6ed747ba2da6068f793a60291131ae086a623576dbd2557c31ad854640945856", BigInteger("2000003"),
            "bf72deac4b8c7dae48258dd2f4c7e4c3f13ed986e27da7650abdf573027579ba", 1700000020000)
        val author = vector.getAsJsonObject("payment").get("author").asString
        val tipHash = "34a627c2eb1307a95ac0227db3aac8961eec5a085b04771b40aa545ac00bec24"
        val snapshot = SaitoBalanceSnapshot.parse("1700000020000-2000003-$tipHash.snap\n".toByteArray(), author, 1700000020000)
        val responses = mapOf(
            tipHash to Base64.getDecoder().decode(vector.getAsJsonObject("alternateTip").get("fullBase64").asString),
            "66e1534d013a39ec151e42f14ca2f314e54d7c63650acdceb7261b54637e3438" to
                Base64.getDecoder().decode(vector.getAsJsonObject("alternateBlock").get("fullBase64").asString))
        assertEquals(SaitoPaymentChain.PriorStatus.REORGANIZED, SaitoPaymentChain.check(previous, snapshot) { responses[it] })
    }

    @Test fun anUnrelatedValidPaymentBlockDoesNotProveSelectedChainInclusion() {
        val vector = reference
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
        val vector = reference
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
