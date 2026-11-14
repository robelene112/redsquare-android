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
        val mint = SaitoImageMint.create(wallet, inputs, "Native red image", "A public image NFT.", image, vector.get("timestamp").asLong)
        assertEquals(vector.getAsJsonObject("mint").get("networkBase64").asString, mint.transaction.encoded())
        val restored = SaitoImageMint.restore(mint.transaction.encoded())
        assertEquals(mint.nftId, restored.nftId); assertEquals(BigInteger("100000000"), restored.deposit)
        assertEquals(inputs, restored.inputs)
        assertEquals(image, restored.image)
    }
}
