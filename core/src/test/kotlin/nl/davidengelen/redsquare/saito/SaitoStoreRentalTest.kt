package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class SaitoStoreRentalTest {
    @Test fun anOfficialRentalMintRetainsItsExactFileRightsAndRecoverableOriginal() {
        val reference = JsonParser.parseString(requireNotNull(javaClass.getResourceAsStream("/native-store-rental-reference.json")).bufferedReader().use { it.readText() }).asJsonObject
        val original = reference.getAsJsonObject("mint").get("networkBase64").asString
        val recovered = SaitoStoreMint.restore(original)
        assertEquals(original, recovered.transaction.encoded())
        assertEquals(reference.getAsJsonObject("mint").get("id").asString, recovered.transaction.id)
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val master = SaitoBalanceSnapshot.parse(reference.get("masterSnapshot").asString.toByteArray(), wallet.publicKey, 1700000999000).nfts.single()
        val key = SaitoVault.readKey(master, reference.getAsJsonObject("master").get("envelope").asString)
        val inputs = reference.getAsJsonArray("inputs").map { value -> value.asJsonObject.let {
            SaitoBalanceSnapshot.Slip(it.get("block").asString.toBigInteger(), it.get("ordinal").asString.toBigInteger(), it.get("index").asInt, it.get("amount").asString.toBigInteger(), it.get("type").asInt)
        } }
        val created = SaitoStoreMint.createRental(wallet, inputs, key, 3, java.math.BigInteger.ONE,
            "Three hours of field notes", "Temporary access. Downloaded copies cannot be recalled.", recovered.image, 1700000999000)
        assertEquals(original, created.transaction.encoded())
    }
    @Test fun rentalListingRestoresTheOfficialDelegationWithoutMovingTheVaultMaster() {
        val reference = JsonParser.parseString(requireNotNull(javaClass.getResourceAsStream("/native-store-rental-listing-reference.json")).bufferedReader().use { it.readText() }).asJsonObject
        val original = reference.getAsJsonObject("listing").get("networkBase64").asString
        val recovered = SaitoStoreListing.restore(original)
        assertEquals(original, recovered.transaction.encoded())
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val inventory = reference.getAsJsonObject("inventory")
        val holding = SaitoBalanceSnapshot.parse(inventory.get("snapshot").asString.toByteArray(), wallet.publicKey, 1700002000000).nfts.single()
        val created = SaitoStoreListing.create(wallet, holding, inventory.getAsJsonObject("key").get("envelope").asString,
            reference.get("store").asString, recovered.terms, 1700002000000)
        assertEquals(original, created.transaction.encoded())
        assertEquals("store-nft-rental", created.holding.type)
    }
    @Test fun theOfficialStoreDeliveryOpensOnlyTheReviewedFileForTheActualRenter() {
        fun reference(name: String) = JsonParser.parseString(requireNotNull(javaClass.getResourceAsStream("/$name.json")).bufferedReader().use { it.readText() }).asJsonObject
        val offer = reference("native-store-rental-listing-reference")
        val payment = reference("native-store-rental-purchase-reference")
        val product = requireNotNull(SaitoStore.readListing(SaitoStore.readCatalogue(offer.get("page").toString()).items.single(), offer.getAsJsonObject("listing").get("envelope").asString))
        val purchase = SaitoStorePurchase.restore(payment.getAsJsonObject("purchase").get("networkBase64").asString)
        val receipt = payment.getAsJsonObject("settlement").getAsJsonObject("fulfillment").get("networkBase64").asString
        val entitlement = SaitoStoreRental.entitlement(purchase, product, receipt)
        val grant = requireNotNull(entitlement.grant)
        assertEquals(purchase.buyer, grant.renter)
        assertEquals(1700012800000L, grant.expiresAt)
        val file = reference("native-vault-rental-reference")
        val response = com.google.gson.JsonObject().apply {
            addProperty("status", "success"); add("txs", com.google.gson.JsonArray().apply { add(file.getAsJsonObject("file").get("envelope")) })
        }.toString()
        assertArrayEquals(java.util.Base64.getDecoder().decode(file.get("contentsBase64").asString), grant.readFile(response).bytes)
        assertEquals(setOf(purchase.buyer), grant.eligibleActorsAt(1700012799999))
        assertTrue(grant.eligibleActorsAt(1700012800000).isEmpty())
        assertEquals(setOf(product.creator), grant.eligibleActorsAt(1700012800001))
    }
    @Test fun theCreatorExplicitlyRequestsRestorationOfTheBoundLoanWithoutMovingAKey() {
        val reference = JsonParser.parseString(requireNotNull(javaClass.getResourceAsStream("/native-vault-rental-reference.json")).bufferedReader().use { it.readText() }).asJsonObject
        val path = SaitoVaultRental.parse(reference.get("creator").asString, reference.get("fileId").asString,
            reference.get("masterSource").asString, reference.get("hops").toString())
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val requested = SaitoEnvelope.decode(path.restoreMaster(wallet, 1700001001001).envelope())
        assertEquals(wallet.publicKey, requested.author)
        assertEquals(JsonParser.parseString("""{"request":"vault restore rental","data":{"file_id":"${path.fileId}","renter":"${reference.get("renter").asString}","expires_at":1700001001000}}"""), requested.body)
        assertThrows(IllegalArgumentException::class.java) { path.restoreMaster(SaitoWallet.fromPrivateKey("0".repeat(63) + "1"), 1700001001001) }
    }
}
