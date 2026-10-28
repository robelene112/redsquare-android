package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.RedSquarePosts
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import mockwebserver3.MockWebServer
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.rules.TestName
import java.net.InetAddress
import java.net.ServerSocket

class FeedPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @get:Rule val testName = TestName()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Before fun clearPublicFeedCache() {
        listOf("feed.owner.v1", "feed.owner.v1.bak", "feed.owner.v1.new").forEach {
            java.io.File(instrumentation.targetContext.noBackupFilesDir, it).delete()
        }
    }

    @Test fun authenticatedFeedSurvivesReopeningAndRecoversWithoutLosingPosts() {
        val fixture = asset("native-feed-reference.json")
        val peer = ControlledFeedPeer("[${fixture.get("archiveRow")}]")
        // MockWebServer disables address reuse for port0. Reserve an ephemeral
        // loopback port, then explicitly bind both outage/recovery listeners to it.
        // A competing bind during the handoff remains a setup failure.
        val restartPort = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val server = peer.start(restartPort)
        val port = server.port
        var replacement: MockWebServer? = null
        try {
            ActivityScenario.launch<MainActivity>(intent(port)).use { activity ->
                captureFailure {
                    waitForText("Native Red Square reference post")
                    ui.onNodeWithText("Anon-jesTu2").assertIsDisplayed()
                    ui.onNodeWithText("Refresh").assertDoesNotExist()
                    peer.assertRequestObserved()
                    server.close()
                    activity.recreate()
                    waitForText("Native Red Square reference post")
                    waitForText("Node unavailable")
                    replacement = peer.start(port)
                    ui.pullToRefresh("feed-post-list")
                    waitForText("Back online")
                    ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
                    ui.waitUntil(6_000) { ui.onAllNodes(hasText("Back online")).fetchSemanticsNodes().isEmpty() }
                    ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
                    peer.assertHealthy()
                }
            }
        } finally { server.close(); replacement?.close() }
    }

    @Test fun refreshAndRecreationKeepTheVisiblePostWhenNewPostsArrive() {
        val fixture = asset("native-feed-scroll-reference.json")
        val peer = ControlledFeedPeer(fixture.get("initialRows").toString())
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(intent(server.port)).use { activity ->
                captureFailure {
                    waitForText("Reference post 20")
                    ui.onNode(hasScrollAction()).performScrollToNode(hasText("Reference post 05"))
                    ui.onNodeWithText("Reference post 05").assertIsDisplayed()
                    ui.onNode(hasScrollAction()).performScrollToIndex(0)
                    ui.onNodeWithText("Reference post 20").assertIsDisplayed()
                    peer.rows = fixture.get("refreshedRows").toString()
                    val before = peer.requests.get()
                    ui.pullToRefresh("feed-post-list")
                    ui.waitUntil(15_000) { peer.requests.get() >= before + 2 }
                    ui.waitForIdle()
                    ui.onNodeWithText("Reference post 20").assertIsDisplayed()
                    activity.recreate()
                    waitForText("Reference post 20")
                    ui.onNodeWithText("Reference post 20").assertIsDisplayed()
                    ui.onNode(hasScrollAction()).performScrollToIndex(0)
                    waitForText("New reference post")
                    peer.assertHealthy()
                }
            }
        }
    }

    @Test fun pullRefreshWorksWhenTheFeedIsEmpty() {
        val fixture = asset("native-feed-reference.json")
        val peer = ControlledFeedPeer("[]")
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(intent(server.port)).use {
                captureFailure {
                    waitForText("No posts are available from this node yet.")
                    ui.onNodeWithText("Refresh").assertDoesNotExist()
                    ui.onNode(hasTestTag("feed-post-list")).assertIsDisplayed()
                    peer.rows = "[${fixture.get("archiveRow")}]"
                    val before = peer.requests.get()
                    ui.pullToRefresh("feed-post-list")
                    ui.waitUntil(15_000) { peer.requests.get() >= before + 2 }
                    waitForText("Native Red Square reference post")
                    waitForText("Feed updated.")
                    peer.assertHealthy()
                }
            }
        }
    }

    @Test fun unavailableCacheWarnsWithoutHidingLivePostsAndCanRecover() {
        val original = asset("native-feed-reference.json")
        val updated = asset("native-feed-scroll-reference.json").getAsJsonArray("refreshedRows")[0]
        val peer = ControlledFeedPeer("[${original.get("archiveRow")}]")
        val blocked = java.io.File(instrumentation.targetContext.noBackupFilesDir, "feed.owner.v1")
        val preserved = java.io.File(instrumentation.targetContext.noBackupFilesDir, "feed.test.saved")
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(intent(server.port)).use {
                captureFailure {
                    ui.waitUntil(15_000) {
                        peer.requests.get() > 0 &&
                            ui.onNode(hasTestTag("feed-post-list")).isDisplayed()
                    }
                    peer.assertHealthy()
                    ui.onNode(hasScrollAction()).performScrollToNode(hasText("Native Red Square reference post"))
                    waitForText("Native Red Square reference post")
                    check(blocked.renameTo(preserved))
                    check(blocked.mkdir())
                    val obstruction = java.io.File(blocked, "obstruction").apply { writeText("public fixture") }
                    try {
                        peer.rows = "[$updated]"
                        val beforeRefresh = peer.requests.get()
                        ui.pullToRefresh("feed-post-list")
                        ui.waitUntil(15_000) { peer.requests.get() > beforeRefresh }
                        ui.waitUntil(15_000) {
                            try {
                                ui.onNode(hasScrollAction()).performScrollToNode(hasText("New reference post"))
                                true
                            } catch (_: AssertionError) { false }
                        }
                        waitForText("New reference post")
                        waitForText("Some posts could not be saved or opened for offline reading.")
                    } finally {
                        check(obstruction.delete())
                        check(blocked.delete())
                        check(preserved.renameTo(blocked))
                    }
                    ui.pullToRefresh("feed-post-list")
                    ui.waitUntil(15_000) {
                        ui.onAllNodes(hasText("Some posts could not be saved or opened for offline reading.")).fetchSemanticsNodes().isEmpty()
                    }
                    ui.onNodeWithText("New reference post").assertIsDisplayed()
                    peer.assertHealthy()
                }
            }
        }
    }

    @Test fun unreadableRefreshExplainsTheProblemWhileKeepingCachedPosts() {
        val fixture = asset("native-feed-reference.json")
        val peer = ControlledFeedPeer("[${fixture.get("archiveRow")}]")
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(intent(server.port)).use {
                captureFailure {
                    waitForText("Native Red Square reference post")
                    // Feed rows appear before the initial two archive streams finish.
                    // Observe a completed load before asking the UI to refresh it.
                    ui.waitUntil(15_000) {
                        peer.requests.get() >= 2 &&
                            ui.onNode(hasTestTag("feed-post-list")).isDisplayed()
                    }
                    val invalid = fixture.getAsJsonObject("archiveRow").deepCopy().apply { addProperty("tx", "{}") }
                    peer.rows = "[$invalid]"
                    val before = peer.requests.get()
                    ui.pullToRefresh("feed-post-list")
                    ui.waitUntil(15_000) { peer.requests.get() > before }
                    waitForText("The node returned posts this app could not read.")
                    ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
                    peer.assertHealthy()
                }
            }
        }
    }

    @Test fun conversationPullRefreshKeepsTheSelectedPostAndReportsOfflineRecovery() {
        val fixture = asset("native-feed-reference.json")
        val postText = "Native Red Square reference post"
        AndroidWalletStore(instrumentation.targetContext).loadOrCreate()
        val archiveRow = fixture.get("archiveRow").toString()
        val peer = ControlledFeedPeer("[$archiveRow]", archiveResponse = { "[$archiveRow]" })
        val restartPort = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val server = peer.start(restartPort)
        var replacement: MockWebServer? = null
        try {
            ActivityScenario.launch<MainActivity>(intent(server.port)).use {
                captureFailure {
                    waitForText(postText)
                    val feedBodyHeight = firstPostBodyLineHeight()
                    val feedRequestCount = peer.requests.get()
                    ui.onNode(hasText(postText) and hasClickAction()).performClick()
                    ui.waitUntil(15_000) {
                        ui.onAllNodesWithText("Conversation").fetchSemanticsNodes().isNotEmpty() &&
                            ui.onAllNodes(hasTestTag("conversation-post-list")).fetchSemanticsNodes().isNotEmpty()
                    }
                    ui.waitUntil(15_000) {
                        peer.requests.get() > feedRequestCount &&
                            ui.onAllNodesWithText("Loading replies").fetchSemanticsNodes().isEmpty()
                    }
                    ui.onNodeWithText("Refresh").assertDoesNotExist()
                    ui.onNodeWithText(postText).assertIsDisplayed()
                    check(firstPostBodyLineHeight() < feedBodyHeight) {
                        "Conversation body should use a tighter line height than the main feed"
                    }
                    val firstRequestCount = peer.requests.get()
                    ui.pullToRefresh("conversation-post-list")
                    ui.waitUntil(15_000) { peer.requests.get() > firstRequestCount }
                    ui.waitUntil(5_000) { ui.onNodeWithText("Conversation updated.").isDisplayed() }
                    ui.waitUntil(15_000) { ui.onNodeWithText(postText).isDisplayed() }

                    server.close()
                    ui.pullToRefresh("conversation-post-list")
                    waitForText("Replies could not be refreshed. Available posts remain visible.")
                    ui.onNodeWithText(postText).assertIsDisplayed()

                    replacement = peer.start(restartPort)
                    val recoveryRequestCount = peer.requests.get()
                    ui.pullToRefresh("conversation-post-list")
                    ui.waitUntil(15_000) { peer.requests.get() > recoveryRequestCount }
                    ui.waitUntil(15_000) {
                        ui.onAllNodes(hasText("Replies could not be refreshed. Available posts remain visible."))
                            .fetchSemanticsNodes().isEmpty()
                    }
                    ui.onNodeWithText(postText).assertIsDisplayed()
                    peer.assertHealthy()
                }
            }
        } finally {
            server.close()
            replacement?.close()
        }
    }

    @Test fun conversationBodyStaysVisibleAtLargerTextScaleInLightAndDarkThemes() {
        val context = instrumentation.targetContext
        val appearance = context.getSharedPreferences("appearance", 0)
        val originalAppearance = appearance.getString("mode", null)
        val evidence = java.io.File(context.cacheDir, "conversation-typography-evidence")
        val font = EmulatorFontScale(context, evidence, "conversation")
        val author = SaitoWallet.create()
        val postText = ("A long conversation body wraps across many lines at larger text sizes. ").repeat(10) +
            "The final sentence stays available after scrolling."
        val row = postingArchiveRow(SaitoTransaction.create(author, RedSquarePosts.message(postText), 1_700_000_000_000))
        val peer = ControlledFeedPeer("[$row]", archiveResponse = { "[$row]" })
        val server = peer.start()
        var primary: Throwable? = null
        try {
            check(appearance.edit().putString("mode", "LIGHT").commit())
            ActivityScenario.launch<MainActivity>(intent(server.port)).use { activity ->
                captureFailure {
                    waitForText(postText)
                    font.apply(1.3f)
                    ui.waitUntil(10_000) { ui.onNodeWithText(postText).isDisplayed() }
                    ui.onNode(hasText(postText) and hasClickAction()).performClick()
                    ui.waitUntil(15_000) {
                        ui.onAllNodesWithText("Conversation").fetchSemanticsNodes().isNotEmpty() &&
                            ui.onAllNodes(hasTestTag("conversation-post-list")).fetchSemanticsNodes().isNotEmpty()
                    }
                    ui.onNodeWithTag("post-body-text", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
                    assertLongPostLayout(postText)
                    ui.onNodeWithText("Reply").assertIsDisplayed()
                    check(appearance.edit().putString("mode", "DARK").commit())
                    activity.recreate()
                    ui.waitUntil(15_000) {
                        ui.onAllNodesWithText("Conversation").fetchSemanticsNodes().isNotEmpty() &&
                            ui.onAllNodes(hasTestTag("conversation-post-list")).fetchSemanticsNodes().isNotEmpty()
                    }
                    ui.onNodeWithTag("post-body-text", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
                    assertLongPostLayout(postText)
                    ui.onNodeWithText("Reply").assertIsDisplayed()
                    peer.assertHealthy()
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary,
                { server.close() },
                { font.restore() },
                { check(appearance.edit().apply {
                    if (originalAppearance == null) remove("mode") else putString("mode", originalAppearance)
                }.commit()) })
        }
    }

    private fun firstPostBodyLineHeight(): Float {
        val layout = firstPostBodyLayout()
        return layout.getLineBottom(0) - layout.getLineTop(0)
    }

    private fun firstPostBodyLayout(): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        ui.onNodeWithTag("post-body-text", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            check(it(layouts))
        }
        return requireNotNull(layouts.firstOrNull())
    }

    private fun assertLongPostLayout(postText: String) {
        val layout = firstPostBodyLayout()
        check(layout.lineCount > 5) { "Long conversation text should wrap at the larger supported size" }
        check(layout.getLineEnd(layout.lineCount - 1) == postText.length) {
            "Conversation text should keep its complete final line at the larger supported size"
        }
    }

    private fun asset(name: String) = JsonParser.parseReader(instrumentation.context.assets.open(name).reader()).asJsonObject
    private fun intent(port: Int) = Intent(instrumentation.targetContext, MainActivity::class.java)
        .putExtra("feedPeerUrl", "ws://127.0.0.1:$port/wsopen")
    private fun waitForText(text: String) {
        ui.waitUntil(15_000) { ui.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText(text).assertIsDisplayed()
    }
    private fun captureFailure(check: () -> Unit) {
        try { check() } catch (failure: Throwable) {
            captureFeedEvidence("feed-ui-failures/${testName.methodName}.png")
            captureFeedEvidence("feed-ui-failure.png")
            throw failure
        }
    }
}
