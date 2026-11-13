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
        lateinit var peer: ControlledFeedPeer
        withAppFrameFixture("store-media", "store-media-evidence", feedRows = "[]", advertisedServices = "archive||;Store||",
            archiveResponse = { query ->
                if (query.get("sig")?.asString == reference.getAsJsonObject("listing").get("id").asString)
                    JsonArray().apply { add(JsonObject().apply { addProperty("tx", reference.getAsJsonObject("listing").get("envelope").asString) }) }.toString()
                else "[]"
            }, configurePeer = { owned ->
                peer = owned
                owned.storeResponse = { request ->
                    if (request.get("request").asString == "load-listing")
                        JsonObject().apply { add("listing", reference.getAsJsonObject("page").getAsJsonArray("listings")[0]) }.toString()
                    else reference.get("page").toString()
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
            ui.onNodeWithText("Message seller").performScrollTo().assertIsDisplayed()
            assertTrue(peer.transactions.isEmpty()); assertTrue(peer.vaultUploads.isEmpty())
        }
    }
}
