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
        val actual = payment.getAsJsonObject("rentalAccess")
        assertEquals(actual.get("loanSource").asString, grant.loanScript.source)
        val requested = SaitoEnvelope.decode(entitlement.checkout(SaitoWallet.fromPrivateKey("0".repeat(63) + "4"), 1700004000001).envelope())
        assertEquals(actual.getAsJsonObject("checkoutMessage"), SaitoEnvelope.decode(requested.body.get("data").asString).body)
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
    @Test fun theCreatorRecoversTheActualRentalFromPublicTransactionsWithoutTheBuyersInvoice() {
        fun fixture(name: String) = JsonParser.parseString(requireNotNull(javaClass.getResourceAsStream("/$name.json")).bufferedReader().use { it.readText() }).asJsonObject
        val offer = fixture("native-store-rental-listing-reference")
        val payment = fixture("native-store-rental-purchase-reference")
        val product = requireNotNull(SaitoStore.readListing(SaitoStore.readCatalogue(offer.get("page").toString()).items.single(), offer.getAsJsonObject("listing").get("envelope").asString))
        val receipt = payment.getAsJsonObject("settlement").getAsJsonObject("fulfillment").get("envelope").asString
        val archive = com.google.gson.JsonArray().apply { add(com.google.gson.JsonObject().apply { add("tx", payment.getAsJsonObject("purchase").get("envelope")) }) }.toString()
        val path = SaitoStoreRental.creatorEntitlement(product, receipt, archive)
        assertEquals(product.creator, path.creator)
        assertEquals(payment.get("buyer").asString, requireNotNull(path.grant).renter)
        assertEquals(1700012800000L, requireNotNull(path.grant).expiresAt)
        assertThrows(IllegalArgumentException::class.java) { SaitoStoreRental.creatorEntitlement(product, receipt, "[]") }
    }
    @Test fun everyAdvertisedMethodRetainsTheActualRentalCarrierAndAuthenticatesProviderFundedAccess() {
        fun fixture(name: String) = JsonParser.parseString(requireNotNull(javaClass.getResourceAsStream("/$name.json")).bufferedReader().use { it.readText() }).asJsonObject
        val vector = fixture("native-store-rental-crypto-reference")
        val provider = fixture("native-store-rental-provider-reference")
        val methods = SaitoStoreCrypto.readMethods(vector.get("methods").toString())
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
        val carrier = SaitoStoreCarrier.create(wallet, vector.get("store").asString, vector.get("nftId").asString,
            java.math.BigInteger.ONE, StorePrice("SAITO", java.math.BigDecimal("2.5")), vector.get("timestamp").asLong)
        assertEquals(vector.getAsJsonObject("saitoCarrier").get("networkBase64").asString, carrier.transaction.encoded())
        assertEquals(7, provider.getAsJsonArray("saitoQuotes").size())
        for (value in provider.getAsJsonArray("saitoQuotes")) {
            val row = value.asJsonObject
            val currency = methods.currencies.single { it.ticker == row.getAsJsonObject("currency").get("ticker").asString }.copy(usd = java.math.BigDecimal.ONE)
            val invoice = SaitoStoreCrypto.readInvoice(carrier, currency, provider.get("provider").asString, row.get("quote").toString())
            assertEquals(0, java.math.BigDecimal("0.002751").compareTo(invoice.amount))
            assertEquals(0, SaitoStoreCrypto.estimate(carrier.price, carrier.quantity, currency, java.math.BigDecimal("0.001"), vector.get("timestamp").asLong).compareTo(invoice.amount))
        }
        for ((paymentName, listingName) in listOf("native-store-rental-crypto-settlement-reference" to "native-store-rental-web3-listing-reference",
            "native-store-rental-saito-provider-settlement-reference" to "native-store-rental-listing-reference")) {
            val payment = fixture(paymentName); val offer = fixture(listingName)
            val original = SaitoStoreCarrier.restore(payment.getAsJsonObject("carrier").get("networkBase64").asString)
            val currency = methods.currencies.single { it.ticker == "USDT" }
            val invoice = SaitoStoreCrypto.readInvoice(original, currency, payment.get("store").asString, payment.get("invoice").toString())
            val funded = SaitoStoreIssuance.read(original, invoice, payment.get("providerStatus").toString())
            val product = requireNotNull(SaitoStore.readListing(SaitoStore.readCatalogue(offer.get("page").toString()).items.single(), offer.getAsJsonObject("listing").get("envelope").asString))
            val received = payment.getAsJsonObject("settlement").getAsJsonObject("fulfillment").get("networkBase64").asString
            val path = SaitoStoreRental.entitlement(funded, product, received)
            assertEquals(wallet.publicKey, requireNotNull(path.grant).renter)
            assertEquals(1700012800000L, requireNotNull(path.grant).expiresAt)
            assertEquals("dd369adba25604597aa6e29f74e7312558fe045114d11fe0c6709a9f1fc336bc15fd2082e4fa2e7587854e0311551f01758680f0739fbc268a9993c6dca1c9fe", path.fileId)
        }
    }
    @Test fun aBrowserPurchasedHoldingRecoversItsSignedRentalWithoutAnyLocalAndroidOrder() {
        fun fixture(name: String) = JsonParser.parseString(requireNotNull(javaClass.getResourceAsStream("/$name.json")).bufferedReader().use { it.readText() }).asJsonObject
        val offer = fixture("native-store-rental-listing-reference")
        val paid = fixture("native-store-rental-purchase-reference")
        val owner = paid.get("buyer").asString
        val holding = SaitoBalanceSnapshot.parse(paid.getAsJsonObject("blocks").get("snapshot").asString.toByteArray(), owner, 1700004000000).nfts.single()
        val product = requireNotNull(SaitoStore.readListing(SaitoStore.readCatalogue(offer.get("page").toString()).items.single(), offer.getAsJsonObject("listing").get("envelope").asString))
        val envelope = paid.getAsJsonObject("settlement").getAsJsonObject("fulfillment").get("envelope").asString
        val refs = SaitoStoreRental.publicReceipt(envelope)
        assertEquals(product.signature, refs.inventory)
        assertEquals(paid.getAsJsonObject("purchase").get("id").asString, refs.payment)
        val archive = com.google.gson.JsonArray().apply { add(com.google.gson.JsonObject().apply { add("tx", paid.getAsJsonObject("purchase").get("envelope")) }) }.toString()
        val path = SaitoStoreRental.heldEntitlement(holding, product, envelope, archive)
        assertEquals(owner, requireNotNull(path.grant).renter)
        assertEquals(1700012800000L, requireNotNull(path.grant).expiresAt)
        val source = requireNotNull(SaitoStoreRental.offer(product)).source
        val metadata = source.data("").apply { addProperty("link", "https://saitoverse.com/vault") }
        assertEquals("https://saitoverse.com/vault", StoreRentalSource.read(product.creator, metadata).data("").get("link").asString)
        assertFalse(requireNotNull(SaitoStoreRental.offer(product)).availableAt(1700012800000L))
    }
    @Test fun anActualPartialRentalFulfillmentLeavesAssignedInventoryUnavailableForAnotherPayment() {
        fun fixture(name: String) = JsonParser.parseString(requireNotNull(javaClass.getResourceAsStream("/$name.json")).bufferedReader().use { it.readText() }).asJsonObject
        val original = fixture("native-store-rental-multiple-listing-reference")
        val paid = fixture("native-store-rental-partial-reference")
        val delivery = paid.getAsJsonObject("settlement").getAsJsonObject("fulfillment")
        val rows = listOf(original.getAsJsonObject("listing"), paid.getAsJsonObject("purchase"), delivery).associate { it.get("id").asString to it.get("envelope").asString }
        val remaining = requireNotNull(SaitoStoreLineage.read(delivery.get("id").asString) { id -> rows[id]?.let { encoded ->
            com.google.gson.JsonArray().apply { add(com.google.gson.JsonObject().apply { addProperty("tx", encoded) }) }.toString()
        } })
        assertEquals(java.math.BigInteger.ONE, remaining.quantity)
        val offer = requireNotNull(SaitoStoreRental.offer(remaining))
        assertEquals(paid.get("buyer").asString, requireNotNull(offer.path.grant).renter)
        assertFalse(offer.availableAt(1700004000001L))
    }
}
