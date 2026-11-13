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

}
