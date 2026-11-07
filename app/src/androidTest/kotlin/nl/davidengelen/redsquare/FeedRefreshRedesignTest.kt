package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class FeedRefreshRedesignTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun readingDuringAPendingTopPullKeepsTheNewlyChosenPostAndPixel() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val initial = instrumentation.context.assets.open("native-feed-scroll-reference.json").reader().use {
            JsonParser.parseReader(it).asJsonObject.get("initialRows").toString()
        }
        val rows = AtomicReference(initial)
        val pending = AtomicBoolean(false)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        withAppFrameFixture("read-during-refresh", "feed-refresh-evidence", initial, archiveResponse = { criteria ->
            if (criteria.get("field1").asString == "RedSquare") {
                if (pending.get() && criteria.has("field4") && !criteria.has("sig")) {
                    entered.countDown(); check(release.await(15, TimeUnit.SECONDS))
                }
                rows.get()
            } else "[]"
        }) {
            try {
                ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
                ui.onNodeWithText("Later").performClick()
                ui.waitUntil(15_000) { ui.refreshableContentReady() }
                ui.onNodeWithTag("feed-post-list").performScrollToIndex(0)
                val fresh = postingArchiveRow(SaitoTransaction.create(SaitoWallet.fromPrivateKey("0".repeat(63) + "1"),
                    RedSquarePosts.message("A new post while you read"), 1_700_000_100_000)) // Published unsafe fixture only.
                rows.set(com.google.gson.JsonArray().apply {
                    add(fresh); JsonParser.parseString(initial).asJsonArray.take(19).forEach { add(it) }
                }.toString())
                pending.set(true)
                ui.pullToRefresh("feed-post-list")
                ui.waitUntil(5_000) { entered.count == 0L }
                ui.onNodeWithTag("feed-post-list").performScrollToNode(hasText("Reference post 10"))
                ui.revealAppNavigation()
                val target = ui.onNodeWithText("Reference post 10", useUnmergedTree = true).assertIsDisplayed()
                val before = target.fetchSemanticsNode().boundsInRoot.top
                release.countDown()
                val observations = com.google.gson.JsonArray()
                val observedFrom = android.os.SystemClock.elapsedRealtime()
                var lastObservation: String? = null
                var clippingCaptured = false
                try {
                    ui.waitUntil(15_000) {
                        val successVisible = ui.onNodeWithText("Feed updated.").isDisplayed()
                        val targetVisible = target.isDisplayed()
                        val observation = com.google.gson.JsonObject().apply {
                            addProperty("successVisible", successVisible)
                            addProperty("targetVisible", targetVisible)
                            addProperty("successNodes", ui.onAllNodesWithText("Feed updated.").fetchSemanticsNodes().size)
                            addProperty("targetBounds", ui.onAllNodesWithText("Reference post 10", useUnmergedTree = true)
                                .fetchSemanticsNodes().singleOrNull()?.boundsInRoot?.toString())
                            addProperty("listBounds", ui.onNodeWithTag("feed-post-list").fetchSemanticsNode().boundsInRoot.toString())
                        }
                        val signature = observation.toString()
                        if (signature != lastObservation && observations.size() < 128) {
                            observation.addProperty("elapsedMillis", android.os.SystemClock.elapsedRealtime() - observedFrom)
                            observations.add(observation); lastObservation = signature
                        }
                        if (successVisible && !targetVisible && !clippingCaptured) {
                            clippingCaptured = true
                            captureNativeScreen(File(instrumentation.targetContext.cacheDir,
                                "feed-refresh-evidence/success-with-clipped-target.png"))
                        }
                        successVisible && targetVisible
                    }
                } finally {
                    File(instrumentation.targetContext.cacheDir, "feed-refresh-evidence/refresh-visibility-observations.json")
                        .writeText(com.google.gson.JsonObject().apply {
                            addProperty("nonAtomicObservations", true)
                            add("observations", observations)
                        }.toString())
                }
                assertEquals("A completed pull must not override the reader's subsequent scroll", before,
                    target.fetchSemanticsNode().boundsInRoot.top, 1f)
                captureNativeScreen(File(instrumentation.targetContext.cacheDir, "feed-refresh-evidence/reading-during-pending-refresh.png"))
            } finally { release.countDown() }
        }
    }

    @Test fun physicalPullAnnouncesSuccessOnlyAfterAppliedResultsAndRetainsThemThroughAnOutage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val initial = instrumentation.context.assets.open("native-feed-scroll-reference.json").reader().use {
            JsonParser.parseReader(it).asJsonObject.get("initialRows").toString()
        }
        val fresh = postingArchiveRow(SaitoTransaction.create(SaitoWallet.fromPrivateKey("0".repeat(63) + "1"),
            RedSquarePosts.message("Applied refresh row"), 1_700_000_100_000)) // Published unsafe fixture only.
        val rows = AtomicReference(initial)
        val gate = AtomicBoolean(false)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        lateinit var peer: ControlledFeedPeer
        withAppFrameFixture("refresh-results", "feed-refresh-evidence", initial, configurePeer = { peer = it }, archiveResponse = { criteria ->
            if (criteria.get("field1").asString == "RedSquare") {
                if (gate.get() && criteria.has("field4") && !criteria.has("sig")) {
                    entered.countDown(); check(release.await(15, TimeUnit.SECONDS))
                }
                rows.get()
            } else "[]"
        }) {
            try {
                ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
                ui.onNodeWithText("Later").performClick()
                ui.waitUntil(15_000) { ui.refreshableContentReady() }
                ui.onNodeWithTag("feed-post-list").performScrollToIndex(0)
                val updated = com.google.gson.JsonArray().apply {
                    add(fresh); JsonParser.parseString(initial).asJsonArray.take(19).forEach { add(it) }
                }
                rows.set(updated.toString()); gate.set(true)
                ui.pullToRefresh("feed-post-list")
                ui.waitUntil(5_000) { entered.count == 0L }
                ui.onNodeWithText("Feed updated.").assertDoesNotExist()
                ui.onNodeWithText("Applied refresh row").assertDoesNotExist()
                captureNativeScreen(File(context.cacheDir, "feed-refresh-evidence/request-pending.png"))
                release.countDown()
                ui.waitUntil(15_000) { ui.onNodeWithText("Applied refresh row").isDisplayed() && ui.onNodeWithText("Feed updated.").isDisplayed() }
                captureNativeScreen(File(context.cacheDir, "feed-refresh-evidence/results-applied.png"))
                ui.waitUntil(5_000) { ui.refreshableContentReady() }
                peer.rejectNewConnections = true; peer.rejectionDelayMillis = 2_000
                ui.pullToRefresh("feed-post-list")
                ui.waitUntil(20_000) { ui.onNodeWithText("Node unavailable").isDisplayed() && ui.refreshableContentReady() }
                ui.onNodeWithText("Feed updated.").assertDoesNotExist()
                ui.onNodeWithText("Applied refresh row").assertIsDisplayed()
                captureNativeScreen(File(context.cacheDir, "feed-refresh-evidence/cached-outage.png"))
                peer.rejectNewConnections = false
                ui.pullToRefresh("feed-post-list")
                ui.waitUntil(20_000) { ui.onNodeWithText("Feed updated.").isDisplayed() && ui.onNodeWithText("Applied refresh row").isDisplayed() }
                captureNativeScreen(File(context.cacheDir, "feed-refresh-evidence/recovered-results.png"))
            } finally { release.countDown() }
        }
    }
}
