package nl.davidengelen.redsquare

import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import java.io.File
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import mockwebserver3.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real two-node routing and independent signed-feed work, including a late previous-node response. */
class NodeBlockBoundaryTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun changingNodesCancelsTheOldReadAndNeverReusesAnotherNodesHeight() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val produced = CountDownLatch(1)
        val oldStarted = AtomicLong()
        val calls = AtomicInteger()
        MockWebServer().use { owner -> MockWebServer().use { official ->
            owner.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    check(request.target == "/options")
                    val number = calls.incrementAndGet()
                    val height = when (number) {
                        1 -> 1111L
                        2 -> {
                            oldStarted.set(SystemClock.elapsedRealtime()); entered.countDown()
                            check(release.await(12, TimeUnit.SECONDS)); produced.countDown(); 7777L
                        }
                        else -> 2222L
                    }
                    return MockResponse.Builder().body(options(height)).build()
                }
            }
            official.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    check(request.target == "/options")
                    return MockResponse.Builder().body(options(9999)).build()
                }
            }
            owner.start(InetAddress.getByName("127.0.0.1"), 0)
            official.start(InetAddress.getByName("127.0.0.1"), 0)
            withAppFrameFixture("node-swap", "node-boundary-evidence",
                nodeBlockApiUrl = "http://127.0.0.1:${owner.port}/options",
                nodeBlockOfficialApiUrl = "http://127.0.0.1:${official.port}/options") { _ ->
                try {
                    ready()
                    ui.waitUntil(15_000) { ui.onNodeWithText("Block 1,111").isDisplayed() }
                    ui.onNodeWithContentDescription("Connection details").performClick()
                    checkNode()
                    ui.waitUntil(5_000) { entered.count == 0L }
                    ui.onNodeWithText("Done").performClick()
                    ui.waitUntil(5_000) { ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
                    chooseNode("saito.io")
                    ui.waitUntil(15_000) { ui.onNodeWithText("Block 9,999").isDisplayed() }
                    ui.onNodeWithText("Block 1,111").assertDoesNotExist()
                    val elapsed = SystemClock.elapsedRealtime() - oldStarted.get()
                    assertTrue("The previous response must be released inside the original10s request deadline", elapsed < 10_000)
                    release.countDown()
                    ui.waitUntil(5_000) { produced.count == 0L }
                    ui.onNodeWithText("Block 7,777").assertDoesNotExist()
                    ui.onNodeWithText("Node: saito.io").assertIsDisplayed()
                    capture("official-after-late-owner")
                    chooseNode("saito.davidengelen.nl")
                    ui.waitUntil(15_000) { ui.onNodeWithText("Block 2,222").isDisplayed() && ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
                    ui.onNodeWithText("Block 9,999").assertDoesNotExist()
                    ui.onNodeWithText("Block 7,777").assertDoesNotExist()
                    ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
                    capture("owner-read-again")
                    File(folder(), "late-response-timing.json").writeText("{\"releasedAfterMillis\":$elapsed,\"requestDeadlineMillis\":10000}")
                } finally { release.countDown() }
            }
        } }
    }

    @Test fun aNewBlockDoesNotFinishAPendingFeedRefresh() {
        val height = AtomicLong(4444)
        val hold = AtomicBoolean(false)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val feedStarted = AtomicLong()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val row = instrumentation.context.assets.open("native-feed-reference.json").reader().use {
            JsonParser.parseReader(it).asJsonObject.get("archiveRow").toString()
        }
        MockWebServer().use { node ->
            node.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    check(request.target == "/options")
                    return MockResponse.Builder().body(options(height.get())).build()
                }
            }
            node.start(InetAddress.getByName("127.0.0.1"), 0)
            withAppFrameFixture("refresh-independent", "node-boundary-evidence",
                nodeBlockApiUrl = "http://127.0.0.1:${node.port}/options", archiveResponse = { criteria ->
                    if (criteria.get("field1").asString == "RedSquare") {
                        if (hold.get() && criteria.has("field4") && !criteria.has("sig")) {
                            feedStarted.compareAndSet(0L, SystemClock.elapsedRealtime())
                            entered.countDown(); check(release.await(15, TimeUnit.SECONDS))
                        }
                        "[$row]"
                    } else "[]"
                }) { _ ->
                try {
                    ready()
                    ui.waitUntil(15_000) { ui.onNodeWithText("Block 4,444").isDisplayed() && ui.refreshableContentReady() }
                    ui.onNodeWithTag("feed-post-list").performScrollToIndex(0)
                    hold.set(true)
                    ui.pullToRefresh("feed-post-list")
                    ui.waitUntil(5_000) { entered.count == 0L }
                    height.set(5555)
                    ui.onNodeWithContentDescription("Connection details").performClick()
                    checkNode()
                    ui.waitUntil(5_000) { ui.onNode(hasText("Block 5,555") and hasAnyAncestor(isDialog())).isDisplayed() }
                    ui.onNodeWithText("Done").performClick()
                    ui.waitUntil(5_000) { ui.onAllNodes(isDialog()).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty() && ui.onNodeWithText("Block 5,555").isDisplayed() }
                    assertTrue("Block arrival must be observed while the original feed RPC is still pending",
                        SystemClock.elapsedRealtime() - feedStarted.get() < 12_000)
                    assertFalse("Pending feed work must remain pending after a new block", ui.refreshableContentReady())
                    ui.onNodeWithText("Feed updated.").assertDoesNotExist()
                    ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
                    capture("new-block-feed-pending")
                    release.countDown()
                    ui.waitUntil(15_000) { ui.onNodeWithText("Feed updated.").isDisplayed() && ui.refreshableContentReady() }
                    ui.onNodeWithText("Block 5,555").assertIsDisplayed()
                    capture("feed-applied-separately")
                } finally { release.countDown() }
            }
        }
    }

    private fun ready() {
        ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
        ui.onNodeWithText("Later").performClick()
        ui.waitUntil(15_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
    }
    private fun chooseNode(domain: String) {
        ui.openSettings()
        ui.onNodeWithText("Node").performScrollTo().performClick()
        ui.onNodeWithText(domain).performScrollTo().performClick()
        ui.waitUntil(15_000) { ui.onNodeWithText("Node: $domain").isDisplayed() }
    }
    private fun checkNode() {
        val action = hasText("Check again") and hasClickAction()
        ui.waitUntil(5_000) { ui.onNode(action and isEnabled()).isDisplayed() }
        ui.onNode(action).assertIsEnabled().performClick()
    }
    private fun folder() = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
        "node-boundary-evidence").apply { mkdirs() }
    private fun capture(name: String) { ui.waitForIdle(); captureNativeScreen(File(folder(), "$name.png")) }
    private fun options(height: Long) = """{"blockchain":{"last_block_id":$height,"last_timestamp":${System.currentTimeMillis() - 1_000},"last_block_hash":"${"ef".repeat(32)}"}}"""
}
