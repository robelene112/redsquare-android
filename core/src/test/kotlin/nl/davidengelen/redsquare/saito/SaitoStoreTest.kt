package nl.davidengelen.redsquare.saito

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class SaitoStoreTest {
    @Test fun boundedCatalogueRequestsMatchTheOfficialBrowserBuilderAndEveryCategory() {
        val vector = requireNotNull(javaClass.getResourceAsStream("/native-store-catalogue-reference.json"))
            .reader().use { JsonParser.parseReader(it).asJsonObject }
        assertEquals(vector.getAsJsonArray("categories").map { it.asString }, StoreCategory.entries.map { it.wire })
        for (value in vector.getAsJsonArray("requests")) {
            val row = value.asJsonObject
            val options = row.getAsJsonObject("options")
            val query = StoreQuery(options.get("public_key")?.asString,
                options.get("category")?.asString?.let { category -> StoreCategory.entries.single { it.wire == category } },
                options.get("offset")?.asInt ?: 0, options.get("page_size")?.asInt ?: 24,
                if (options.get("status")?.asString == "sold") StoreStock.SOLD else StoreStock.ACTIVE)
            val expected = JsonObject().apply { add("request", row.get("request")); add("data", row.get("data")) }
            assertEquals(expected, JsonParser.parseString(SaitoStore.catalogueRequest(query)))
        }
    }

    @Test fun actualStoreSummariesKeepTheirExactOfferIdentityPriceAndCurrentReportedStock() {
        val vector = requireNotNull(javaClass.getResourceAsStream("/native-store-listing-reference.json"))
            .reader().use { JsonParser.parseReader(it).asJsonObject }
        val page = SaitoStore.readCatalogue(vector.get("page").toString())
        assertEquals(1, page.items.size)
        val offer = page.items.single()
        assertEquals(vector.get("nftId").asString, offer.nftId)
        assertEquals(vector.get("seller").asString, offer.seller)
        assertEquals(vector.getAsJsonObject("listing").get("id").asString, offer.signature)
        assertEquals("Field notes access key", offer.title)
        assertEquals(StoreCategory.ACCESS, offer.category)
        assertEquals("SAITO", requireNotNull(offer.price).currency)
        assertEquals(0, java.math.BigDecimal("2.5").compareTo(offer.price.amount))
        assertEquals(java.math.BigInteger("2"), offer.available)
        assertEquals(java.math.BigInteger("2"), offer.total)
        assertEquals(0, page.offset); assertEquals(1, page.total); assertNull(page.nextOffset)
        assertEquals(0, page.ignored)
    }


    @Test fun productDetailsBindTheOriginalSignedSellerAssetAndTerms() {
        val vector = requireNotNull(javaClass.getResourceAsStream("/native-store-listing-reference.json"))
            .reader().use { JsonParser.parseReader(it).asJsonObject }
        val offer = SaitoStore.readCatalogue(vector.get("page").toString()).items.single()
        val envelope = vector.getAsJsonObject("listing").get("envelope").asString
        val product = SaitoStore.readListing(offer, envelope)
        assertNotNull(product)
        requireNotNull(product)
        assertEquals(offer.nftId, product.nftId); assertEquals(offer.seller, product.seller)
        assertEquals(offer.signature, product.signature)
        assertEquals("Field notes access key", product.title)
        assertEquals("Original creator-authenticated field notes.", product.description)
        assertEquals(StoreCategory.ACCESS, product.category)
        assertEquals(0, java.math.BigDecimal("2.5").compareTo(requireNotNull(product.price).amount))
        assertEquals(java.math.BigInteger("2"), product.quantity)
        assertEquals("vault-nft-key", product.assetType)
        assertEquals(envelope, product.envelope)
        assertNull(SaitoStore.readListing(offer.copy(seller = vector.get("store").asString), envelope))
        assertNull(SaitoStore.readListing(offer.copy(signature = "a".repeat(128)), envelope))
        assertNull(SaitoStore.readListing(offer.copy(nftId = "1".repeat(66)), envelope))
        val altered = JsonParser.parseString(envelope).asJsonObject
        val bytes = java.util.Base64.getDecoder().decode(altered.get("m").asString)
        val metadata = String(bytes, Charsets.UTF_8).replace("Field notes access key", "Another offer")
        altered.addProperty("m", java.util.Base64.getEncoder().encodeToString(metadata.toByteArray()))
        assertNull(SaitoStore.readListing(offer, altered.toString()))
    }

    @Test fun exactProductLookupAndArchiveNeverSubstituteAnotherOffer() {
        val vector = requireNotNull(javaClass.getResourceAsStream("/native-store-listing-reference.json"))
            .reader().use { JsonParser.parseReader(it).asJsonObject }
        val offer = SaitoStore.readCatalogue(vector.get("page").toString()).items.single()
        val target = StoreLink(SaitoBackend.OWNER, offer.seller, offer.nftId)
        val request = JsonParser.parseString(SaitoStore.listingRequest(target)).asJsonObject
        assertEquals("load-listing", request.get("request").asString)
        assertEquals(offer.seller, request.getAsJsonObject("data").get("seller").asString)
        assertEquals(offer.nftId, request.getAsJsonObject("data").get("nft_id").asString)
        val response = JsonObject().apply { add("listing", vector.getAsJsonObject("page").getAsJsonArray("listings")[0]) }.toString()
        assertEquals(offer, SaitoStore.readOffer(response, target))
        assertNull(SaitoStore.readOffer("{\"err\":\"Listing unavailable\"}", target))
        assertNull(SaitoStore.readOffer(response, target.copy(nftId = "a".repeat(66))))
        val signed = vector.getAsJsonObject("listing").get("envelope").asString
        val archive = com.google.gson.JsonArray().apply { add(JsonObject().apply { addProperty("tx", signed) }) }.toString()
        assertNotNull(SaitoStore.readListingArchive(offer, archive))
        assertNull(SaitoStore.readListingArchive(offer.copy(signature = "a".repeat(128)), archive))
        assertEquals(offer.signature, JsonParser.parseString(SaitoStore.listingArchiveRequest(offer.signature))
            .asJsonObject.getAsJsonObject("data").get("sig").asString)
        assertEquals("${SaitoBackend.OWNER.webUrl}/store/${offer.seller}/${offer.nftId}", target.url())
    }

    @Test fun productMediaComesFromTheActualSignedListingAndNeverAnUnsignedSummaryHint() {
        val vector = requireNotNull(javaClass.getResourceAsStream("/native-store-image-listing-reference.json"))
            .reader().use { JsonParser.parseReader(it).asJsonObject }
        val summary = SaitoStore.readCatalogue(vector.get("page").toString()).items.single()
        val product = requireNotNull(SaitoStore.readListing(summary, vector.getAsJsonObject("listing").get("envelope").asString))
        assertEquals("Illustrated field notes access key", product.title)
        assertEquals(vector.getAsJsonObject("body").getAsJsonObject("data").get("image").asString, product.image)
        assertTrue(requireNotNull(product.image).startsWith("data:image/png;base64,"))
        assertFalse(product.browserOnly)
    }

    @Test fun exactOriginalOfferRemainsReadableAfterTheNodeReportsItUnavailable() {
        val vector = requireNotNull(javaClass.getResourceAsStream("/native-store-image-listing-reference.json"))
            .reader().use { JsonParser.parseReader(it).asJsonObject }
        val signature = vector.getAsJsonObject("listing").get("id").asString
        val archive = com.google.gson.JsonArray().apply {
            add(JsonObject().apply { addProperty("tx", vector.getAsJsonObject("listing").get("envelope").asString) })
        }.toString()
        val product = requireNotNull(SaitoStore.readOriginalListingArchive(signature, archive))
        assertEquals(signature, product.signature)
        assertEquals(vector.get("seller").asString, product.seller)
        assertEquals(vector.get("nftId").asString, product.nftId)
        assertEquals("Illustrated field notes access key", product.title)
        assertEquals(0, java.math.BigDecimal("3.75").compareTo(requireNotNull(product.price).amount))
        assertNull(SaitoStore.readOriginalListingArchive("a".repeat(128), archive))
    }

}
