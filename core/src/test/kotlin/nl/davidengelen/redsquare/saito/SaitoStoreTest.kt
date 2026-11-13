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
}
