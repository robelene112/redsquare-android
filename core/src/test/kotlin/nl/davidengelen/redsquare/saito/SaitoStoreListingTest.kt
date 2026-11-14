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
}
