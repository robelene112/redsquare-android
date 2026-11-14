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
}
