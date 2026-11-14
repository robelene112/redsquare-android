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

class StoreRecoveryPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun aSilentCatalogueRequestBecomesAnActionableRetry() = silentReply(true)
    @Test fun aSilentProductRequestBecomesAnActionableRetry() = silentReply(false)
    private fun silentReply(catalogue: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val reference = instrumentation.context.assets.open("native-store-listing-reference.json")
            .reader().use { JsonParser.parseReader(it).asJsonObject }
        lateinit var peer: ControlledFeedPeer
        withAppFrameFixture("store-silent-$catalogue", "store-recovery-evidence", feedRows = "[]", advertisedServices = "archive||;Store||",
            archiveResponse = { query ->
                if (query.get("sig")?.asString == reference.getAsJsonObject("listing").get("id").asString)
                    JsonArray().apply { add(JsonObject().apply { addProperty("tx", reference.getAsJsonObject("listing").get("envelope").asString) }) }.toString()
                else "[]"
            }, configurePeer = { owned ->
                peer = owned; peer.withholdStoreReply = catalogue
                owned.storeResponse = { request ->
                    if (request.get("request").asString == "load-listing")
                        JsonObject().apply { add("listing", reference.getAsJsonObject("page").getAsJsonArray("listings")[0]) }.toString()
                    else reference.get("page").toString()
                }
            }) {
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }; ui.onNodeWithText("Later").performClick()
            ui.switchStackModule("Red Square", "Store", scrollTarget = true)
            if (!catalogue) {
                ui.waitUntil(15_000) { ui.onNodeWithText("Field notes access key").isDisplayed() }
                peer.withholdStoreReply = true
                ui.onNodeWithText("Field notes access key").performClick()
            }
            ui.waitUntil(15_000) { ui.onNodeWithText("Could not load Store. Try again.").isDisplayed() }
            ui.onNodeWithText("Refresh Store").assertIsEnabled()
            captureNativeScreen(File(context.cacheDir, "store-recovery-evidence/actionable-silent-$catalogue.png"))
            peer.withholdStoreReply = false
            ui.onNodeWithText("Retry").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText(if (catalogue) "Field notes access key" else "Original creator-authenticated field notes.").isDisplayed() }
            assertTrue(peer.transactions.isEmpty()); assertTrue(peer.vaultUploads.isEmpty())
        }
    }
}
