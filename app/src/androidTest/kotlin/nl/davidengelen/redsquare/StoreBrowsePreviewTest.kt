package nl.davidengelen.redsquare

import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

private object StoreViewportMotionScale : MotionDurationScale { override val scaleFactor = 0f }

class StoreBrowsePreviewTest {
    @get:Rule val ui = createEmptyComposeRule(StoreViewportMotionScale)
    @Test fun aLargeReportedCatalogueRecoversAndLoadsBoundedPages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val reference = InstrumentationRegistry.getInstrumentation().context.assets.open("native-store-listing-reference.json")
            .reader().use { JsonParser.parseReader(it).asJsonObject }
        val attempts = java.util.concurrent.atomic.AtomicInteger()
        lateinit var peer: ControlledFeedPeer
        withAppFrameFixture("store-large", "store-browse-evidence", feedRows = "[]", advertisedServices = "archive||;Store||",
            configurePeer = { owned ->
                peer = owned
                // Deliberately synthetic node-reported summaries for continuation, not signed product evidence.
                owned.storeResponse = { request ->
                    if (attempts.getAndIncrement() == 0) "invalid catalogue response" else {
                        val data = request.getAsJsonObject("data")
                        val offset = data.get("offset").asInt
                        assertEquals(24, data.get("page_size").asInt)
                        reference.getAsJsonObject("page").deepCopy().apply {
                            add("listings", JsonArray().apply {
                                repeat(24) { index -> add(reference.getAsJsonObject("page").getAsJsonArray("listings")[0].asJsonObject.deepCopy().apply {
                                    addProperty("title", "Reported product ${offset + index + 1}")
                                    addProperty("listing_signature", (offset + index + 1).toString(16).padStart(128, '0'))
                                }) }
                            })
                            getAsJsonObject("pagination").apply {
                                addProperty("offset", offset); addProperty("total", 4096); addProperty("has_next", true)
                            }
                        }.toString()
                    }
                }
            }) {
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }; ui.onNodeWithText("Later").performClick()
            ui.switchStackModule("Red Square", "Store", scrollTarget = true)
            ui.waitUntil(15_000) { ui.onNodeWithText("Could not load Store. Try again.").isDisplayed() }
            ui.onNodeWithText("Retry").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Reported product 1").isDisplayed() }
            ui.onNode(hasScrollAction()).performScrollToNode(hasText("More listings"))
            ui.onNodeWithText("More listings").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Reported product 25").isDisplayed() }
            assertTrue(peer.storeRequests.any { it.getAsJsonObject("data").get("offset").asInt == 24 })
            assertTrue(peer.storeRequests.all { it.getAsJsonObject("data").get("page_size").asInt == 24 })
            assertTrue(peer.transactions.isEmpty())
            captureNativeScreen(File(context.cacheDir, "store-browse-evidence/bounded-next-page.png"))
        }
    }

    @Test fun aNodeWithoutStoreShowsANeutralExplanationAndNoRequestsOrWrites() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        lateinit var peer: ControlledFeedPeer
        withAppFrameFixture("store-missing", "store-browse-evidence", feedRows = "[]", advertisedServices = "archive||",
            configurePeer = { peer = it }) {
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }; ui.onNodeWithText("Later").performClick()
            ui.switchStackModule("Red Square", "Store", scrollTarget = true)
            ui.waitUntil(15_000) { ui.onNodeWithText("Store is not available on this node.").isDisplayed() }
            ui.onNodeWithText(context.getString(R.string.vault_choose_node)).assertIsDisplayed()
            assertTrue(peer.storeRequests.isEmpty()); assertTrue(peer.transactions.isEmpty())
            captureNativeScreen(File(context.cacheDir, "store-browse-evidence/selected-node-without-store.png"))
        }
    }

    @Test fun realProductAndCategoryControlsRemainReachableInTwelveNativeCells() = withNativeModuleMotionScale(0) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val reference = InstrumentationRegistry.getInstrumentation().context.assets.open("native-store-listing-reference.json")
            .reader().use { JsonParser.parseReader(it).asJsonObject }
        for (width in listOf(320, 412)) {
            withAppFrameFixture("store-viewport-$width", "store-browse-evidence", feedRows = "[]", advertisedServices = "archive||;Store||",
                archiveResponse = { query ->
                    if (query.get("sig")?.asString == reference.getAsJsonObject("listing").get("id").asString)
                        JsonArray().apply { add(JsonObject().apply { addProperty("tx", reference.getAsJsonObject("listing").get("envelope").asString) }) }.toString()
                    else "[]"
                }, configurePeer = { peer -> peer.storeResponse = { request ->
                    if (request.get("request").asString == "load-listing")
                        JsonObject().apply { add("listing", reference.getAsJsonObject("page").getAsJsonArray("listings")[0]) }.toString()
                    else reference.get("page").toString()
                } }) { activity ->
                ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }; ui.onNodeWithText("Later").performClick()
                val folder = File(context.cacheDir, "store-browse-evidence").apply { mkdirs() }
                ui.withPostViewports(activity, folder, "store-$width", listOf(1f, 1.3f, 2f), width) { label, _ ->
                    ui.switchStackModule("Red Square", "Store", scrollTarget = true)
                    ui.waitUntil(15_000) { ui.onNodeWithText("Field notes access key").isDisplayed() }
                    ui.onNodeWithText("All categories").performClick()
                    ui.onNodeWithContentDescription("Filter by Other").performScrollTo().assertIsDisplayed()
                    captureNativeScreen(File(folder, "$label-categories.png"))
                    ui.onNode(hasText("All categories") and hasAnyAncestor(isDialog())).performScrollTo().performClick()
                    ui.waitUntil(15_000) { ui.onNodeWithText("Field notes access key").isDisplayed() }
                    ui.onNodeWithText("Field notes access key").performClick()
                    ui.waitUntil(15_000) {
                        ui.onAllNodesWithText("2.5 SAITO").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
                    }
                    ui.onNode(hasScrollAction()).performScrollToNode(hasText("Message seller"))
                    ui.onNodeWithText("Message seller").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                    captureNativeScreen(File(folder, "$label-product.png"))
                    ui.onNode(hasScrollAction()).performScrollToNode(hasText("2.5 SAITO"))
                    ui.onNodeWithText("2.5 SAITO").assertIsDisplayed()
                    ui.onNodeWithText("Back").performClick()
                    ui.switchStackModule("Store", "Red Square", scrollTarget = true)
                    ui.waitUntil(5_000) { ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
                }
            }
        }
    }
}
