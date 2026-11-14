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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class StoreAnchorPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun aDelayedCatalogueReadKeepsItsDeepCardAndPixelOffsetAcrossProductAndModuleReturns() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val reference = instrumentation.context.assets.open("native-store-listing-reference.json")
            .reader().use { JsonParser.parseReader(it).asJsonObject }
        val gate = AtomicReference<CountDownLatch?>()
        val page = reference.getAsJsonObject("page").deepCopy().apply {
            add("listings", JsonArray().apply {
                // Summary-only synthetic padding; only the final actual signed offer is opened.
                repeat(23) { index -> add(reference.getAsJsonObject("page").getAsJsonArray("listings")[0].asJsonObject.deepCopy().apply {
                    addProperty("title", "Reported preceding item ${index + 1}")
                    addProperty("listing_signature", (index + 1).toString(16).padStart(128, '0'))
                }) }
                add(reference.getAsJsonObject("page").getAsJsonArray("listings")[0])
            })
            getAsJsonObject("pagination").addProperty("total", 24)
        }.toString()
        try {
            withAppFrameFixture("store-anchor", "store-anchor-evidence", feedRows = "[]", advertisedServices = "archive||;Store||",
                archiveResponse = { query ->
                    if (query.get("sig")?.asString == reference.getAsJsonObject("listing").get("id").asString)
                        JsonArray().apply { add(JsonObject().apply { addProperty("tx", reference.getAsJsonObject("listing").get("envelope").asString) }) }.toString()
                    else "[]"
                }, configurePeer = { peer -> peer.storeResponse = { request ->
                    if (request.get("request").asString == "load-listing")
                        JsonObject().apply { add("listing", reference.getAsJsonObject("page").getAsJsonArray("listings")[0]) }.toString()
                    else {
                        val waiting = gate.get()
                        if (waiting != null) { check(waiting.await(15, TimeUnit.SECONDS)); gate.compareAndSet(waiting, null) }
                        page
                    }
                } }) {
                try {
                ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }; ui.onNodeWithText("Later").performClick()
                ui.switchStackModule("Red Square", "Store", scrollTarget = true)
                ui.waitUntil(15_000) { ui.onNodeWithText("Reported preceding item 1").isDisplayed() }
                ui.onNode(hasScrollAction()).performScrollToNode(hasText("Field notes access key"))
                val card = ui.onNodeWithText("Field notes access key")
                card.assertIsDisplayed()
                val top = card.getUnclippedBoundsInRoot().top.value
                captureNativeScreen(File(context.cacheDir, "store-anchor-evidence/deep-card-before.png"))
                ui.onNodeWithText("Field notes access key").performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("Original creator-authenticated field notes.").isDisplayed() }
                val fromProduct = CountDownLatch(1); gate.set(fromProduct)
                ui.onNodeWithText("Back").performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText(context.getString(R.string.store_loading)).isDisplayed() }
                fromProduct.countDown()
                ui.waitUntil(15_000) { ui.onNodeWithText("Field notes access key").isDisplayed() }
                val productReturnTop = ui.onNodeWithText("Field notes access key").getUnclippedBoundsInRoot().top.value
                assertEquals("Product return keeps the actual card coordinate", top, productReturnTop, 2f)
                ui.switchStackModule("Store", "Red Square", scrollTarget = true)
                val fromModule = CountDownLatch(1); gate.set(fromModule)
                ui.switchStackModule("Red Square", "Store", scrollTarget = true)
                ui.waitUntil(15_000) { ui.onNodeWithText(context.getString(R.string.store_loading)).isDisplayed() }
                fromModule.countDown()
                ui.waitUntil(15_000) { ui.onNodeWithText("Field notes access key").isDisplayed() }
                val moduleReturnTop = ui.onNodeWithText("Field notes access key").getUnclippedBoundsInRoot().top.value
                assertEquals("Module return keeps the actual card coordinate", top, moduleReturnTop, 2f)
                File(context.cacheDir, "store-anchor-evidence/reading-anchor.json").writeText(JsonObject().apply {
                    addProperty("beforeTopDp", top); addProperty("productReturnTopDp", productReturnTop); addProperty("moduleReturnTopDp", moduleReturnTop)
                }.toString())
                captureNativeScreen(File(context.cacheDir, "store-anchor-evidence/deep-card-after.png"))
                } finally { gate.getAndSet(null)?.countDown() }
            }
        } finally { gate.getAndSet(null)?.countDown() }
    }
}
