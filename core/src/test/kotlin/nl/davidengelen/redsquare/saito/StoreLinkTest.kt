package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class StoreLinkTest {
    @Test fun publicStoreTargetsMatchTheActualPinnedAndDeployedRouter() {
        val vector = requireNotNull(javaClass.getResourceAsStream("/native-store-catalogue-reference.json"))
            .reader().use { JsonParser.parseReader(it).asJsonObject }
        for (value in vector.getAsJsonArray("routes")) {
            val row = value.asJsonObject
            val url = row.get("url").asString
            val expected = row.getAsJsonObject("expected")
            val actual = StoreLink.parse(url)
            assertNotNull(url, actual)
            requireNotNull(actual)
            assertEquals(url, if (url.startsWith("https://saito.io/")) SaitoBackend.OFFICIAL else SaitoBackend.OWNER, actual.backend)
            assertEquals(url, expected.get("publicKey")?.asString?.ifEmpty { null }, actual.seller)
            assertEquals(url, expected.get("nft_id")?.asString, actual.nftId)
            assertEquals(url, expected.get("signature")?.asString, actual.signature)
            assertEquals(url, expected.get("category").asString, actual.category)
        }
    }
}
