package nl.davidengelen.redsquare

import androidx.compose.ui.graphics.toPixelMap
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

class StoreMediaPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun theActualSignedOfferCoverIsDecodedAndDisplayedNatively() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val reference = instrumentation.context.assets.open("native-store-image-listing-reference.json")
            .reader().use { JsonParser.parseReader(it).asJsonObject }
        val original = instrumentation.context.assets.open("native-store-listing-reference.json")
            .reader().use { JsonParser.parseReader(it).asJsonObject }
        val unavailable = java.util.concurrent.atomic.AtomicBoolean()
        lateinit var peer: ControlledFeedPeer
        withAppFrameFixture("store-media", "store-media-evidence", feedRows = "[]", advertisedServices = "archive||;Store||",
            archiveResponse = { query ->
                if (query.get("sig")?.asString == reference.getAsJsonObject("listing").get("id").asString)
                    JsonArray().apply { add(JsonObject().apply { addProperty("tx", reference.getAsJsonObject("listing").get("envelope").asString) }) }.toString()
                else "[]"
            }, configurePeer = { owned ->
                peer = owned
                owned.storeResponse = { request ->
                    if (request.get("request").asString == "load-listing" && unavailable.get()) "{\"err\":\"Listing unavailable\"}"
                    else if (request.get("request").asString == "load-listing") {
                        val exact = request.getAsJsonObject("data").get("signature")?.asString == reference.getAsJsonObject("listing").get("id").asString
                        // Both real signed offers share seller/NFT. Pair lookup returns the first, signature lookup the chosen second.
                        JsonObject().apply { add("listing", (if (exact) reference else original).getAsJsonObject("page").getAsJsonArray("listings")[0]) }.toString()
                    } else reference.getAsJsonObject("page").deepCopy().apply {
                        getAsJsonArray("listings").add(original.getAsJsonObject("page").getAsJsonArray("listings")[0])
                        getAsJsonObject("pagination").addProperty("total", 2)
                    }.toString()
                }
            }) {
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }; ui.onNodeWithText("Later").performClick()
            ui.switchStackModule("Red Square", "Store", scrollTarget = true)
            ui.waitUntil(15_000) { ui.onNodeWithText("Illustrated field notes access key").isDisplayed() }
            ui.onNodeWithText("Illustrated field notes access key").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithContentDescription("Image 1 of 1").isDisplayed() }
            val pixels = ui.onNodeWithContentDescription("Image 1 of 1").captureToImage().toPixelMap()
            val centre = pixels[pixels.width / 2, pixels.height / 2]
            assertTrue("The signed red cover must actually render", centre.red > centre.blue + 0.3f && centre.red > centre.green + 0.3f)
            captureNativeScreen(File(context.cacheDir, "store-media-evidence/signed-native-cover.png"))
            ui.onNode(hasScrollAction()).performScrollToNode(hasText("3.75 SAITO"))
            ui.onNodeWithText("3.75 SAITO").assertIsDisplayed()
            ui.onNode(hasScrollAction()).performScrollToNode(hasText("Message seller"))
            ui.onNodeWithText("Message seller").assertIsDisplayed()
            assertTrue(peer.storeRequests.any { it.getAsJsonObject("data").get("signature")?.asString == reference.getAsJsonObject("listing").get("id").asString })
            unavailable.set(true)
            ui.onNodeWithText("Refresh Store").performClick()
            ui.onNode(hasScrollAction()).performScrollToIndex(0)
            ui.waitUntil(15_000) { ui.onNodeWithText("This offer is no longer available on this node.").isDisplayed() }
            ui.onNode(hasScrollAction()).performScrollToNode(hasText("Original quantity: 2"))
            ui.onNodeWithText("Original quantity: 2").assertIsDisplayed()
            ui.onNodeWithText("3.75 SAITO").assertIsDisplayed()
            ui.onNodeWithText("2 available").assertDoesNotExist()
            captureNativeScreen(File(context.cacheDir, "store-media-evidence/historical-signed-offer.png"))
            assertTrue(peer.transactions.isEmpty()); assertTrue(peer.vaultUploads.isEmpty())
        }
    }
}
