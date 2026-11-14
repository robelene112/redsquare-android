package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import java.math.BigInteger
import org.junit.Assert.*
import org.junit.Test

class SaitoStoreWithdrawalTest {
    private fun reference() = JsonParser.parseString(requireNotNull(javaClass.getResourceAsStream("/native-store-seller-payments-reference.json"))
        .bufferedReader().use { it.readText() }).asJsonObject

    @Test fun sellerReviewsEligibleProceedsAndRecoversTheExactOfficialSignedWithdrawal() {
        val ref = reference()
        val report = SaitoStoreWithdrawal.readProceeds(ref.get("eligible").toString())
        assertEquals("6.25", report.eligible.getValue("USDT").toPlainString())
        assertEquals("9.5", report.pending.getValue("USDT").toPlainString())
        val now = ref.get("timestamp").asLong
        val quote = SaitoStoreWithdrawal.readQuote(ref.get("quote").toString(), ref.get("store").asString, "USDT",
            "0x1111111111111111111111111111111111111111", now)
        val row = ref.getAsJsonObject("funding")
        val input = SaitoBalanceSnapshot.Slip(row.get("block").asString.toBigInteger(), row.get("ordinal").asString.toBigInteger(),
            row.get("index").asInt, row.get("amount").asString.toBigInteger(), row.get("type").asInt)
        val candidate = SaitoStoreWithdrawal.create(SaitoWallet.fromPrivateKey("0".repeat(63) + "2"), quote, listOf(input), BigInteger("5000000"), now)
        assertEquals(ref.getAsJsonObject("candidate").get("networkBase64").asString, candidate.transaction.encoded())
        val restored = SaitoStoreWithdrawal.restore(candidate.transaction.encoded(), ref.get("store").asString)
        assertEquals(candidate.transaction.id, restored.transaction.id)
        assertEquals("6.25", restored.quote.amount.toPlainString())
        assertEquals(listOf(input), restored.inputs)
        assertEquals(BigInteger("5000000"), restored.fee)
        assertFalse(restored.quote.validAt(now + 25 * 60_000))
        assertEquals(SaitoStoreWithdrawal.State.SUBMITTED, SaitoStoreWithdrawal.readResult(ref.get("submitted").toString()).state)
        assertEquals(SaitoStoreWithdrawal.State.FORWARD_FAILED, SaitoStoreWithdrawal.readResult(ref.get("lostTreasuryReply").toString()).state)
    }
    @Test fun anActuallyFreeOfficialWithdrawalNeedsNoSyntheticSpendableBalance() {
        val ref = reference(); val zero = ref.getAsJsonObject("zero"); val now = zero.get("timestamp").asLong
        val quote = SaitoStoreWithdrawal.readQuote(zero.get("quote").toString(), ref.get("store").asString, "USDT",
            "0x1111111111111111111111111111111111111111", now)
        val candidate = SaitoStoreWithdrawal.create(SaitoWallet.fromPrivateKey("0".repeat(63) + "2"), quote, emptyList(), BigInteger.ZERO, now)
        assertEquals(zero.getAsJsonObject("candidate").get("networkBase64").asString, candidate.transaction.encoded())
        assertTrue(SaitoStoreWithdrawal.restore(candidate.transaction.encoded(), quote.store).inputs.isEmpty())
        assertEquals(BigInteger.ZERO, candidate.fee)
    }
    @Test fun aDifferentDestinationUnsignedAmountOrExpiredQuoteCannotBecomeANewWithdrawal() {
        val ref = reference(); val now = ref.get("timestamp").asLong; val store = ref.get("store").asString
        val quote = ref.getAsJsonObject("quote")
        assertThrows(IllegalArgumentException::class.java) { SaitoStoreWithdrawal.readQuote(quote.toString(), store, "USDT", "another-address", now) }
        assertThrows(IllegalArgumentException::class.java) { SaitoStoreWithdrawal.readQuote(quote.toString(), store, "BTC", "0x1111111111111111111111111111111111111111", now) }
        assertThrows(IllegalArgumentException::class.java) { SaitoStoreWithdrawal.readQuote(quote.toString(), store, "USDT", "0x1111111111111111111111111111111111111111", now + 25 * 60_000) }
        quote.getAsJsonObject("data").addProperty("withdrawal_amount", "7")
        assertThrows(IllegalArgumentException::class.java) { SaitoStoreWithdrawal.readQuote(quote.toString(), store, "USDT", "0x1111111111111111111111111111111111111111", now) }
    }
}
