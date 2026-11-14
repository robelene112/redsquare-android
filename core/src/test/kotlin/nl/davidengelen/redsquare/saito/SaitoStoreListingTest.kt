package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import java.math.BigInteger
import org.junit.Assert.*
import org.junit.Test

class SaitoStoreListingTest {
    private fun fixture(name: String) = JsonParser.parseString(requireNotNull(javaClass.getResourceAsStream("/$name.json"))
        .bufferedReader().use { it.readText() }).asJsonObject

    @Test fun existingInventoryProducesTheOfficialSignedListingAndRecoversExactTerms() {
        val source = fixture("native-vault-reference")
        val reference = fixture("native-store-listing-reference")
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val holding = SaitoBalanceSnapshot.parse(source.get("snapshot").asString.toByteArray(), wallet.publicKey, 1700001000000).nfts.single()
        val listing = SaitoStoreListing.create(wallet, holding, source.getAsJsonObject("key").get("envelope").asString,
            reference.get("store").asString, StoreListingTerms("Field notes access key", "Original creator-authenticated field notes.",
                StoreCategory.ACCESS, "2.5", BigInteger.TWO), 1700002000000)
        assertEquals(reference.getAsJsonObject("listing").get("networkBase64").asString, listing.transaction.encoded())
        val restored = SaitoStoreListing.restore(listing.transaction.encoded())
        assertEquals(listing.transaction.id, restored.transaction.id)
        assertEquals("2.5", restored.terms.price)
        assertEquals(holding.witnesses, restored.holding.witnesses)
        assertEquals(BigInteger.TWO, restored.terms.quantity)
    }
    @Test fun alternatePricedInventoryRecoversAndProducesTheOfficialSignedOfferWithoutChangingDenomination() {
        val source = fixture("native-vault-reference")
        val reference = fixture("native-store-web3-listing-reference")
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val expected = reference.getAsJsonObject("listing").get("networkBase64").asString
        val restored = SaitoStoreListing.restore(expected)
        assertEquals("3.75", restored.terms.price)
        val holding = SaitoBalanceSnapshot.parse(source.get("snapshot").asString.toByteArray(), wallet.publicKey, 1700001000000).nfts.single()
        val listing = SaitoStoreListing.create(wallet, holding, source.getAsJsonObject("key").get("envelope").asString,
            reference.get("store").asString, restored.terms, 1700002000000)
        assertEquals(expected, listing.transaction.encoded())
        assertEquals(expected, SaitoStoreListing.restore(listing.transaction.encoded()).transaction.encoded())
    }
    @Test fun anAlternatePricedOfferCanReturnItsExactEscrowTupleToTheSeller() {
        val vector = fixture("native-store-web3-delist-reference")
        val listing = vector.getAsJsonObject("listing")
        val archive = com.google.gson.JsonArray().apply { add(com.google.gson.JsonObject().apply { add("tx", listing.get("envelope")) }) }.toString()
        val product = requireNotNull(SaitoStoreLineage.read(listing.get("id").asString) { archive })
        assertEquals("USDT", product.price?.currency)
        val delist = SaitoStoreDelist.create(SaitoWallet.fromPrivateKey("0".repeat(63) + "2"), product, vector.get("row").toString(), 1700004000000)
        assertEquals(vector.getAsJsonObject("delist").get("networkBase64").asString, delist.transaction.encoded())
        assertEquals(delist.transaction.id, SaitoStoreDelist.restore(delist.transaction.encoded(), product).transaction.id)
    }
    @Test fun partialSalePreservesTheOfficialRemainingQuantityAndSplitDeposit() {
        val source = fixture("native-vault-reference")
        val reference = fixture("native-store-partial-listing-reference")
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val holding = SaitoBalanceSnapshot.parse(source.get("snapshot").asString.toByteArray(), wallet.publicKey, 1700001000000).nfts.single()
        val listing = SaitoStoreListing.create(wallet, holding, source.getAsJsonObject("key").get("envelope").asString,
            reference.get("store").asString, StoreListingTerms("Field notes access key", "Original creator-authenticated field notes.",
                StoreCategory.ACCESS, "2.5", BigInteger.ONE), 1700002000000)
        assertEquals(reference.getAsJsonObject("listing").get("networkBase64").asString, listing.transaction.encoded())
        assertEquals(BigInteger.ONE, SaitoStoreListing.restore(listing.transaction.encoded()).terms.quantity)
    }
    @Test fun purchasedInventoryRetainsTheAuthenticatedStoreReceiptWhenResold() {
        val vector = fixture("native-store-resale-reference")
        val inventory = vector.getAsJsonObject("inventory")
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
        val holding = SaitoBalanceSnapshot.parse(inventory.get("snapshot").asString.toByteArray(), wallet.publicKey, 1700004003000).nfts.single()
        val purchase = SaitoStorePurchase.restore(inventory.getAsJsonObject("purchase").get("networkBase64").asString)
        val receipt = inventory.getAsJsonObject("receipt").get("networkBase64").asString
        val listing = SaitoStoreListing.createFromReceipt(wallet, holding, purchase, receipt, vector.get("store").asString,
            StoreListingTerms("Field notes access key", "Original creator-authenticated field notes.", StoreCategory.ACCESS, "2.5", BigInteger.ONE), 1700002000000)
        assertEquals(vector.getAsJsonObject("listing").get("networkBase64").asString, listing.transaction.encoded())
        assertThrows(IllegalArgumentException::class.java) { SaitoEnvelope.decodeBound(inventory.getAsJsonObject("receipt").get("envelope").asString) }
        assertThrows(IllegalArgumentException::class.java) {
            SaitoStoreListing.receiptMetadata(SaitoNft.fromSnapshot(SaitoNft.Row(holding.owner, holding.positions[1]),
                SaitoNft.Row(wallet.publicKey, holding.positions[0]), SaitoNft.Row(Base58.encode(holding.id.chunked(2).map { it.toInt(16).toByte() }.toByteArray()), holding.positions[2])), purchase, receipt)
        }
    }
    @Test fun remainingStockHydratesItsOfficialLineageAndCanBeDelisted() {
        val reference = fixture("native-store-listing-reference")
        val vector = fixture("native-store-remaining-delist-reference")
        val envelopes = listOf(vector.getAsJsonObject("listing"), vector.getAsJsonObject("purchase"), vector.getAsJsonObject("priorFulfillment"))
        val archives = envelopes.associate { it.get("id").asString to com.google.gson.JsonArray().apply {
            add(com.google.gson.JsonObject().apply { add("tx", it.get("envelope")) })
        }.toString() }
        val signature = vector.getAsJsonObject("priorFulfillment").get("id").asString
        val product = requireNotNull(SaitoStoreLineage.read(signature) { archives[it] })
        assertEquals(BigInteger.ONE, product.quantity)
        assertEquals(reference.get("seller").asString, product.seller)
        assertEquals(product, SaitoStoreLineage.restore(product))
        assertNull(SaitoStoreLineage.read(signature) { if (it == vector.getAsJsonObject("purchase").get("id").asString) null else archives[it] })
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val delist = SaitoStoreDelist.create(wallet, product, vector.get("row").toString(), 1700004000000)
        assertEquals(vector.getAsJsonObject("delist").get("networkBase64").asString, delist.transaction.encoded())
        assertEquals(BigInteger.ONE, delist.quantity)
        assertEquals(BigInteger("50000000"), delist.inputs[1].amount)
        assertEquals(delist.transaction.encoded(), SaitoStoreDelist.restore(delist.transaction.encoded(), product).transaction.encoded())
    }
    @Test fun aMultiLotPurchaseBindsTheRemainingLotRatherThanTheFullySoldPrimary() {
        val vector = fixture("native-store-multi-delist-reference")
        val envelopes = listOf("listing", "primaryListing", "purchase", "priorFulfillment").map { vector.getAsJsonObject(it) }
        val archives = envelopes.associate { it.get("id").asString to com.google.gson.JsonArray().apply {
            add(com.google.gson.JsonObject().apply { add("tx", it.get("envelope")) })
        }.toString() }
        val signature = vector.getAsJsonObject("priorFulfillment").get("id").asString
        val product = requireNotNull(SaitoStoreLineage.read(signature) { archives[it] })
        assertEquals(BigInteger.ONE, product.quantity)
        assertEquals(product, SaitoStoreLineage.restore(product))
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val delist = SaitoStoreDelist.create(wallet, product, vector.get("row").toString(), 1700004000000)
        assertEquals(vector.getAsJsonObject("delist").get("networkBase64").asString, delist.transaction.encoded())
    }
    @Test fun archivedRoutingAndAssignedOutputPositionsPreserveAuthenticatedInventory() {
        val vector = fixture("native-store-listing-reference")
        val original = SaitoStoreListing.restore(vector.getAsJsonObject("listing").get("networkBase64").asString)
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val envelope = routedArchiveEnvelope(original.transaction, wallet, vector.get("store").asString)
        val archive = com.google.gson.JsonArray().apply { add(com.google.gson.JsonObject().apply { addProperty("tx", envelope) }) }.toString()
        val product = requireNotNull(SaitoStoreLineage.read(original.transaction.id) { archive })
        assertEquals("Field notes access key", product.title)
        assertEquals(original.holding.id, product.nftId)
        assertEquals(product, SaitoStoreLineage.restore(product))
        val remaining = fixture("native-store-remaining-delist-reference")
        val payment = SaitoStorePurchase.restore(remaining.getAsJsonObject("purchase").get("networkBase64").asString)
        val routedPayment = routedArchiveEnvelope(payment.transaction, SaitoWallet.fromPrivateKey("0".repeat(63) + "4"), vector.get("store").asString)
        val envelopes = listOf(remaining.getAsJsonObject("listing"), remaining.getAsJsonObject("purchase"), remaining.getAsJsonObject("priorFulfillment"))
        val sources = envelopes.associate { it.get("id").asString to com.google.gson.JsonArray().apply {
            add(com.google.gson.JsonObject().apply { addProperty("tx", if (it.get("id").asString == payment.transaction.id) routedPayment else it.get("envelope").asString) })
        }.toString() }
        val recovered = requireNotNull(SaitoStoreLineage.read(remaining.getAsJsonObject("priorFulfillment").get("id").asString) { sources[it] })
        assertEquals(BigInteger.ONE, recovered.quantity)
        assertEquals(recovered, SaitoStoreLineage.restore(recovered))
    }
    private fun routedArchiveEnvelope(transaction: SaitoTransaction, wallet: SaitoWallet, store: String): String {
        val wire = transaction.broadcast(wallet, store).drop(1).toByteArray()
        val header = java.nio.ByteBuffer.wrap(wire)
        assertEquals(1, header.getInt(12))
        val inputs = header.getInt(0); val outputs = header.getInt(4)
        repeat(outputs) { index -> val offset = 93 + 59 * (inputs + index); header.putLong(offset + 41, 42); header.putLong(offset + 49, 1) }
        val start = 93 + 59 * (inputs + outputs); val end = start + header.getInt(8)
        val detached = wire.copyOfRange(0, start) + wire.copyOfRange(end, wire.size)
        java.nio.ByteBuffer.wrap(detached).putInt(8, 0)
        return com.google.gson.JsonObject().apply {
            addProperty("t", java.util.Base64.getEncoder().encodeToString(detached))
            addProperty("m", java.util.Base64.getEncoder().encodeToString(wire.copyOfRange(start, end)))
            addProperty("opt", "e30=")
        }.toString()
    }
    @Test fun sellerInventoryKeepsApprovalSeparateFromAvailabilityAndRealSales() {
        val reference = fixture("native-store-listing-reference")
        val active = reference.getAsJsonObject("page").getAsJsonArray("listings")[0].asJsonObject.deepCopy().apply { addProperty("approved", 0) }
        val sold = active.deepCopy().apply { addProperty("listing_signature", "1".repeat(128)); addProperty("quantity_available", 0); addProperty("status", 0); addProperty("quantity_sold", 1); addProperty("buyer", reference.get("store").asString) }
        val reply = com.google.gson.JsonObject().apply {
            add("seller", reference.get("seller")); add("active", com.google.gson.JsonArray().apply { add(active) })
            add("sold", com.google.gson.JsonArray().apply { add(sold) })
        }
        val inventory = SaitoStoreSeller.readInventory(reply.toString(), reference.get("seller").asString)
        assertEquals(2, inventory.size)
        assertFalse(inventory[0].approved); assertFalse(inventory[0].sold)
        assertTrue(inventory[1].sold); assertEquals(BigInteger.ONE, inventory[1].quantitySold)
        assertEquals(reference.get("store").asString, inventory[1].buyer)
        assertThrows(IllegalArgumentException::class.java) { SaitoStoreSeller.readInventory(reply.toString(), reference.get("store").asString) }
    }
    @Test fun delistingMatchesTheOfficialSellerWitnessAndReturnsTheExactOriginalStock() {
        val reference = fixture("native-store-listing-reference")
        val vector = fixture("native-store-delist-reference")
        val product = requireNotNull(SaitoStore.readListing(SaitoStore.readCatalogue(reference.get("page").toString()).items.single(),
            reference.getAsJsonObject("listing").get("envelope").asString))
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val delist = SaitoStoreDelist.create(wallet, product, vector.get("row").toString(), 1700004000000)
        assertEquals(vector.getAsJsonObject("delist").get("networkBase64").asString, delist.transaction.encoded())
        val restored = SaitoStoreDelist.restore(delist.transaction.encoded(), product)
        assertEquals(delist.transaction.id, restored.transaction.id)
        assertEquals(BigInteger.TWO, restored.quantity)
        assertEquals(wallet.publicKey, restored.author)
        assertTrue(restored.matchesSpend(vector.get("row").toString()))
        assertFalse(restored.matchesSpend(vector.getAsJsonObject("row").deepCopy().apply { addProperty("quantity", 1) }.toString()))
        assertEquals(4.toByte(), restored.transaction.broadcast(wallet, reference.get("store").asString).first())
        assertThrows(IllegalArgumentException::class.java) { SaitoTransaction.restore(delist.transaction.encoded()) }
        assertThrows(IllegalArgumentException::class.java) { SaitoEnvelope.decodeBound(delist.transaction.envelope()) }
    }
    @Test fun delistedCustodyRequiresTheOriginalOnTheSelectedChainAndExactCurrentReturn() {
        val reference = fixture("native-store-listing-reference")
        val vector = fixture("native-store-delist-reference")
        val product = requireNotNull(SaitoStore.readListing(SaitoStore.readCatalogue(reference.get("page").toString()).items.single(), reference.getAsJsonObject("listing").get("envelope").asString))
        val delist = SaitoStoreDelist.restore(vector.getAsJsonObject("delist").get("networkBase64").asString, product)
        val snapshot = SaitoBalanceSnapshot.parse(vector.get("returnedSnapshot").asString.toByteArray(), product.seller, 1700004003000)
        val block = java.util.Base64.getDecoder().decode(vector.getAsJsonObject("block").get("fullBase64").asString)
        val observation = requireNotNull(SaitoStoreDelistChain.observe(delist, snapshot) { block })
        assertEquals(BigInteger.valueOf(21), observation.height); assertEquals(BigInteger.ZERO, observation.ordinal)
        assertTrue(observation.returned)
        val missing = SaitoBalanceSnapshot.parse((vector.get("returnedSnapshot").asString.substringBefore('\n') + "\n").toByteArray(), product.seller, 1700004003000)
        assertFalse(requireNotNull(SaitoStoreDelistChain.observe(delist, missing) { block }).returned)
        assertNull(SaitoStoreDelistChain.observe(delist, snapshot) { null })
    }
    @Test fun aNewPublicImageUsesTheOfficialImageObjectAndWasmMint() {
        val vector = fixture("native-store-image-reference")
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val inputs = vector.getAsJsonArray("inputs").map { row -> row.asJsonObject.let {
            SaitoBalanceSnapshot.Slip(it.get("block").asString.toBigInteger(), it.get("ordinal").asString.toBigInteger(), it.get("index").asInt, it.get("amount").asString.toBigInteger(), it.get("type").asInt)
        } }
        val image = vector.getAsJsonObject("body").getAsJsonObject("data").get("image").asString
        val mint = SaitoStoreMint.create(wallet, inputs, "Native red image", "A public image NFT.", image, vector.get("timestamp").asLong)
        assertEquals(vector.getAsJsonObject("mint").get("networkBase64").asString, mint.transaction.encoded())
        val restored = SaitoStoreMint.restore(mint.transaction.encoded())
        assertEquals(mint.nftId, restored.nftId); assertEquals(BigInteger("100000000"), restored.deposit)
        assertEquals(inputs, restored.inputs)
        assertEquals(image, restored.image)
    }
    @Test fun stackInventoryRetainsTheOfficialCreatorDurationAndDelegationHop() {
        val vector = fixture("native-store-stack-listing-reference")
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val inventory = vector.getAsJsonObject("inventory")
        val holding = SaitoBalanceSnapshot.parse(inventory.get("snapshot").asString.toByteArray(), wallet.publicKey, 1700001000000).nfts.single()
        val listed = SaitoStoreListing.create(wallet, holding, inventory.getAsJsonObject("mint").get("envelope").asString,
            vector.get("store").asString, StoreListingTerms("Native Stack subscription", "Compatible routing on a Store listing.", StoreCategory.APPS, "2.5", BigInteger.TWO), 1700002000000)
        assertEquals(vector.getAsJsonObject("listing").get("networkBase64").asString, listed.transaction.encoded())
        assertEquals("stack", SaitoStoreListing.restore(listed.transaction.encoded()).holding.type)
    }
    @Test fun atrCustodyAtLaterTupleOffsetsUsesTheOfficialNormalizedDelistOutputs() {
        val reference = fixture("native-store-listing-reference")
        val vector = fixture("native-store-delist-atr-reference")
        val product = requireNotNull(SaitoStore.readListing(SaitoStore.readCatalogue(reference.get("page").toString()).items.single(), reference.getAsJsonObject("listing").get("envelope").asString))
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val delist = SaitoStoreDelist.create(wallet, product, vector.get("row").toString(), 1700004000000)
        assertEquals(vector.getAsJsonObject("delist").get("networkBase64").asString, delist.transaction.encoded())
        assertEquals(listOf(3, 4, 5), delist.inputs.map { it.index })
        assertEquals(1, delist.inputs[1].type)
        val snapshot = SaitoBalanceSnapshot.parse(vector.get("returnedSnapshot").asString.toByteArray(), wallet.publicKey, 1700004003000)
        val block = java.util.Base64.getDecoder().decode(vector.getAsJsonObject("block").get("fullBase64").asString)
        assertTrue(requireNotNull(SaitoStoreDelistChain.observe(delist, snapshot) { block }).returned)
    }
    @Test fun aMarketplaceSubmissionDistinguishesPendingReviewFromRejection() {
        val signature = "1".repeat(128)
        assertFalse(SaitoStoreSeller.readSubmission(signature, "{\"ok\":true,\"signature\":\"$signature\",\"approved\":2}"))
        assertThrows(SaitoStoreSeller.Rejected::class.java) {
            SaitoStoreSeller.readSubmission(signature, "{\"err\":\"Unauthorized\"}")
        }
    }
    @Test fun officialMarketplaceTransitionsRetainPendingAndModeratorRejectedStates() {
        val vector = fixture("native-store-moderation-reference")
        val reference = fixture("native-store-listing-reference")
        for (case in vector.getAsJsonArray("cases")) {
            val sample = case.asJsonObject; val response = sample.get("response").toString()
            if (sample.get("before").asInt == 3) assertThrows(SaitoStoreSeller.Rejected::class.java) {
                SaitoStoreSeller.readSubmission(vector.get("signature").asString, response)
            } else assertEquals(sample.get("after").asInt == 1, SaitoStoreSeller.readSubmission(vector.get("signature").asString, response))
        }
        for (state in listOf(0, -1, 1, 2)) {
            val row = reference.getAsJsonObject("page").getAsJsonArray("listings")[0].asJsonObject.deepCopy().apply { addProperty("approved", state) }
            val body = com.google.gson.JsonObject().apply { add("seller", reference.get("seller")); add("active", com.google.gson.JsonArray().apply { add(row) }); add("sold", com.google.gson.JsonArray()) }
            val item = SaitoStoreSeller.readInventory(body.toString(), reference.get("seller").asString).single()
            assertEquals(StoreApproval.entries.single { it.wire == state }, item.approval)
        }
    }
}
