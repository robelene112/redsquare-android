package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class StoreCataloguePreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun genuineStoreOffersAndOfficialCategoryFiltersLoadWithoutAnyPublication() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val reference = InstrumentationRegistry.getInstrumentation().context.assets.open("native-store-listing-reference.json")
            .reader().use { JsonParser.parseReader(it).asJsonObject }
        lateinit var peer: ControlledFeedPeer
        withAppFrameFixture("store-catalogue", "store-catalogue-evidence", feedRows = "[]", advertisedServices = "archive||;Store||",
            configurePeer = { owned ->
                peer = owned
                owned.storeResponse = { request ->
                    assertEquals("load-listings", request.get("request").asString)
                    val data = request.getAsJsonObject("data")
                    assertEquals("Store", data.get("module").asString)
                    assertEquals("", data.get("public_key").asString)
                    assertEquals(0, data.get("offset").asInt)
                    assertEquals(24, data.get("page_size").asInt)
                    assertEquals("active", data.get("status").asString)
                    reference.getAsJsonObject("page").deepCopy().apply {
                        addProperty("category", data.get("category").asString)
                        if (data.get("category").asString !in listOf("", "Access Keys")) {
                            add("listings", JsonArray()); getAsJsonObject("pagination").apply { addProperty("total", 0); addProperty("total_pages", 0) }
                        }
                    }.toString()
                }
            }) {
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.onNodeWithContentDescription("Switch app: Red Square").performClick()
            ui.waitUntil(5_000) { ui.onNodeWithText("Store").isDisplayed() }
            ui.onNodeWithText("Store").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Field notes access key").isDisplayed() }
            ui.onNodeWithText("2.5 SAITO").assertIsDisplayed()
            ui.onNodeWithText("All categories").performClick()
            for (name in listOf("Apps & Games", "Extensions", "Themes", "Image NFTs", "Tokens", "Access Keys", "Merchandise", "Other")) {
                ui.onNodeWithContentDescription("Filter by $name").performScrollTo().assertIsDisplayed()
            }
            ui.onNodeWithContentDescription("Filter by Access Keys").performScrollTo().performClick()
            ui.waitUntil(15_000) { peer.storeRequests.any { it.getAsJsonObject("data").get("category").asString == "Access Keys" } }
            ui.waitUntil(15_000) { ui.onNodeWithText("Field notes access key").isDisplayed() }
            assertTrue(peer.transactions.isEmpty()); assertTrue(peer.vaultUploads.isEmpty())
            assertEquals(nl.davidengelen.redsquare.saito.SaitoBackend.OWNER,
                nl.davidengelen.redsquare.saito.AndroidBackendStore(context).load())
            captureNativeScreen(File(context.cacheDir, "store-catalogue-evidence/real-filtered-store-offer.png"))
        }
    }
}
