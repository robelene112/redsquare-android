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
        assertFalse(wrongCreator.delivered); assertNull(wrongCreator.receipt)
        val lost = SaitoStoreChain.observe(purchase, snapshot, snapshot.nfts.single().creator) { null }
        assertNull(lost.payment); assertNull(lost.receipt); assertFalse(lost.delivered)
    }

    @Test fun olderHistoryContinuesAcrossChecksAndRetainedDeliveryRechecksExactCurrentOwnership() {
        val vector = requireNotNull(javaClass.getResourceAsStream("/native-store-purchase-reference.json"))
            .reader().use { JsonParser.parseReader(it).asJsonObject }
        val purchase = SaitoStorePurchase.restore(vector.getAsJsonObject("purchase").get("networkBase64").asString)
        val old = vector.getAsJsonObject("blocks").getAsJsonObject("older")
        val snapshotText = old.get("snapshot").asString
        val snapshot = SaitoBalanceSnapshot.parse(snapshotText.toByteArray(), purchase.author, snapshotText.substringBefore('-').toLong())
        val replies = old.getAsJsonObject("blocks").entrySet().associate { it.key to java.util.Base64.getDecoder().decode(it.value.asString) }
        val creator = snapshot.nfts.single().creator
        val first = SaitoStoreChain.observe(purchase, snapshot, creator) { replies[it] }
        assertNull(first.payment); assertFalse(first.delivered); assertNotNull(first.cursor)
        val second = SaitoStoreChain.observe(purchase, snapshot, creator, cursor = first.cursor) { replies[it] }
        assertNotNull(second.payment); assertTrue(second.delivered); assertNotNull(second.receipt)
        val retained = SaitoStoreChain.observe(purchase, snapshot, creator, second.payment, second.receipt, second.receiptBlock) { replies[it] }
        assertTrue(retained.delivered); assertTrue(retained.historical)
        val noAsset = SaitoBalanceSnapshot.parse((snapshotText.substringBefore('\n') + "\n").toByteArray(), purchase.author, snapshot.timestamp)
        val moved = SaitoStoreChain.observe(purchase, noAsset, creator, second.payment, second.receipt, second.receiptBlock) { replies[it] }
        assertFalse(moved.delivered); assertNotNull(moved.receipt)
    }

    @Test fun storeAmountsStayInsideTheOfficialExactNumberRange() {
        val vector = requireNotNull(javaClass.getResourceAsStream("/native-store-purchase-reference.json"))
            .reader().use { JsonParser.parseReader(it).asJsonObject }
        val amounts = vector.getAsJsonObject("exactNumbers")
        assertNotEquals(amounts.get("firstRounded").asString, amounts.get("numberRoundTrip").asString)
        val purchase = SaitoStorePurchase.restore(vector.getAsJsonObject("purchase").get("networkBase64").asString)
        val input = purchase.inputs.single().copy(amount = BigInteger("18446744073709551615"))
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
        assertEquals(amounts.get("maximum").asString.toBigInteger(), SaitoStorePurchase.create(wallet, listOf(input), purchase.store,
            purchase.nftId, BigInteger.ONE, amounts.get("maximum").asString.toBigInteger(), BigInteger.ZERO, BigInteger.ZERO, purchase.timestamp).amount)
        try {
            SaitoStorePurchase.create(wallet, listOf(input), purchase.store, purchase.nftId, BigInteger.ONE,
                amounts.get("firstRounded").asString.toBigInteger(), BigInteger.ZERO, BigInteger.ZERO, purchase.timestamp)
            fail("Rounded Store buckets cannot be signed")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun completeQuantityUsesTheActualOfficialWasmPurchaseAndFulfillment() {
        val vector = requireNotNull(javaClass.getResourceAsStream("/native-store-full-purchase-reference.json"))
            .reader().use { JsonParser.parseReader(it).asJsonObject }
        val purchase = SaitoStorePurchase.restore(vector.getAsJsonObject("purchase").get("networkBase64").asString)
        assertEquals(BigInteger.TWO, purchase.quantity); assertEquals(BigInteger("500000000"), purchase.amount)
        val receipt = SaitoStoreReceipt.read(purchase, vector.getAsJsonObject("settlement").getAsJsonObject("fulfillment").get("networkBase64").asString)
        assertEquals(BigInteger.TWO, requireNotNull(receipt.asset).quantity)
    }

    @Test fun aLateSettlementKeepsItsContinuationUntilTheOriginalPaymentIsFound() {
        val vector = requireNotNull(javaClass.getResourceAsStream("/native-store-purchase-reference.json"))
            .reader().use { JsonParser.parseReader(it).asJsonObject }
        val purchase = SaitoStorePurchase.restore(vector.getAsJsonObject("purchase").get("networkBase64").asString)
        val delayed = vector.getAsJsonObject("blocks").getAsJsonObject("delayed")
        val text = delayed.get("snapshot").asString
        val snapshot = SaitoBalanceSnapshot.parse(text.toByteArray(), purchase.author, text.substringBefore('-').toLong())
        val replies = delayed.getAsJsonObject("blocks").entrySet().associate { it.key to java.util.Base64.getDecoder().decode(it.value.asString) }
        val creator = snapshot.nfts.single().creator
        val first = SaitoStoreChain.observe(purchase, snapshot, creator) { replies[it] }
        assertNotNull(first.receipt); assertNull(first.payment); assertFalse(first.delivered); assertNotNull(first.cursor)
        val second = SaitoStoreChain.observe(purchase, snapshot, creator, first.payment, first.receipt, first.receiptBlock, first.cursor) { replies[it] }
        assertNotNull(second.payment); assertTrue(second.delivered)
        // A bounded read interruption retains its verified prefix rather than starting from the tip again.
        var reads = 0
        val partial = SaitoStoreChain.observe(purchase, snapshot, creator) { if (++reads <= 3) replies[it] else null }
        assertNotNull(partial.cursor); assertEquals(snapshot.height - java.math.BigInteger.valueOf(3), partial.cursor?.height)
        val requested = arrayListOf<String>()
        SaitoStoreChain.observe(purchase, snapshot, creator, partial.payment, partial.receipt, partial.receiptBlock, partial.cursor) {
            requested.add(it); replies[it]
        }
        assertEquals(partial.cursor?.hash, requested.first())
    }

}
