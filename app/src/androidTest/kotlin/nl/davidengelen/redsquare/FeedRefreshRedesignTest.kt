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
                val updated = JsonParser.parseString(initial).asJsonArray.apply { add(fresh) }
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
