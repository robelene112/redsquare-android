package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import java.math.BigInteger
import org.junit.Assert.*
import org.junit.Test

class SaitoStorePurchaseTest {
    @Test fun aFundedPurchaseMatchesTheActualOfficialBuilderAndWasmWallet() {
        val reference = requireNotNull(javaClass.getResourceAsStream("/native-store-purchase-reference.json"))
            .reader().use { JsonParser.parseReader(it).asJsonObject }
        val input = SaitoBalanceSnapshot.Slip(BigInteger.TEN, BigInteger.TWO, 0, BigInteger("1000000000"), 0)
        val purchase = SaitoStorePurchase.create(SaitoWallet.fromPrivateKey("0".repeat(63) + "4"), listOf(input),
            reference.get("store").asString, reference.get("nftId").asString, BigInteger.ONE,
            BigInteger("250000000"), BigInteger.ZERO, BigInteger("10000000"), reference.get("timestamp").asLong)
        assertEquals(reference.getAsJsonObject("purchase").get("networkBase64").asString, purchase.transaction.encoded())
        assertEquals(purchase.transaction.encoded(), SaitoStorePurchase.restore(purchase.transaction.encoded()).transaction.encoded())
        assertEquals(BigInteger("260000000"), purchase.total)
        try { SaitoPayment.restore(purchase.transaction.encoded()); fail("A Store escrow is not an ordinary payment") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun exactStoreFulfillmentAndRefundAreBoundToTheOriginalEscrowAndAsset() {
        val vector = requireNotNull(javaClass.getResourceAsStream("/native-store-purchase-reference.json"))
            .reader().use { JsonParser.parseReader(it).asJsonObject }
        val purchase = SaitoStorePurchase.restore(vector.getAsJsonObject("purchase").get("networkBase64").asString)
        val receipt = vector.getAsJsonObject("settlement")
        val delivered = SaitoStoreReceipt.read(purchase, receipt.getAsJsonObject("fulfillment").get("networkBase64").asString)
        assertEquals(SaitoStoreReceipt.Kind.FULFILLMENT, delivered.kind)
        assertEquals(purchase.nftId, requireNotNull(delivered.asset).id)
        assertEquals(purchase.quantity, delivered.asset.quantity)
        assertEquals(purchase.author, delivered.asset.owner)
        assertEquals(BigInteger("21"), delivered.paymentInput.block)
        val refund = SaitoStoreReceipt.read(purchase, receipt.getAsJsonObject("refund").get("networkBase64").asString)
        assertEquals(SaitoStoreReceipt.Kind.REFUND, refund.kind)
        assertEquals("insufficient-quantity", refund.reason)
        val another = SaitoStorePurchase.create(SaitoWallet.fromPrivateKey("0".repeat(63) + "4"), purchase.inputs,
            purchase.store, purchase.nftId, purchase.quantity, purchase.unitPrice, purchase.storeFee, purchase.networkFee, purchase.timestamp + 1)
        try { SaitoStoreReceipt.read(another, receipt.getAsJsonObject("fulfillment").get("networkBase64").asString); fail("Another order cannot satisfy this purchase") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun selectedChainSeparatesPaymentSettlementAndTheExactPresentAsset() {
        val vector = requireNotNull(javaClass.getResourceAsStream("/native-store-purchase-reference.json"))
            .reader().use { JsonParser.parseReader(it).asJsonObject }
        val purchase = SaitoStorePurchase.restore(vector.getAsJsonObject("purchase").get("networkBase64").asString)
        val blocks = vector.getAsJsonObject("blocks")
        val time = blocks.getAsJsonObject("delivery").get("timestamp").asLong
        val snapshot = SaitoBalanceSnapshot.parse(blocks.get("snapshot").asString.toByteArray(), purchase.author, time)
        val replies = listOf("delivery", "payment").associate { name -> blocks.getAsJsonObject(name).let {
            it.get("hash").asString to java.util.Base64.getDecoder().decode(it.get("fullBase64").asString)
        } }
        val result = SaitoStoreChain.observe(purchase, snapshot, vector.getAsJsonObject("settlement").getAsJsonObject("row").getAsJsonObject("utxo_slip1").get("publicKey").asString) { replies[it] }
        assertNotNull(result.payment); assertNotNull(result.receipt); assertTrue(result.delivered); assertFalse(result.refunded)
        val noAsset = SaitoBalanceSnapshot.parse((blocks.getAsJsonObject("delivery").let {
            "${it.get("timestamp").asString}-22-${it.get("hash").asString}.snap\n"
        }).toByteArray(), purchase.author, time)
        val missing = SaitoStoreChain.observe(purchase, noAsset, snapshot.nfts.single().creator) { replies[it] }
        assertNotNull(missing.receipt); assertFalse(missing.delivered)
        val wrongCreator = SaitoStoreChain.observe(purchase, snapshot, purchase.store) { replies[it] }
        assertFalse(wrongCreator.delivered)
        val lost = SaitoStoreChain.observe(purchase, snapshot, snapshot.nfts.single().creator) { null }
        assertNull(lost.payment); assertNull(lost.receipt); assertFalse(lost.delivered)
    }

}
