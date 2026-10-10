package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import mockwebserver3.MockWebServer
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.rules.TestName

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
        val server = peer.start()
        val port = server.port
        var replacement: MockWebServer? = null
        try {
            ActivityScenario.launch<MainActivity>(intent(port)).use { activity ->
                captureFailure {
                    waitForText("Native Red Square reference post")
                    ui.onNodeWithText("Anon-jesTu2").assertIsDisplayed()
                    peer.assertRequestObserved()
                    server.close()
                    activity.recreate()
                    waitForText("Native Red Square reference post")
                    waitForText("Server unavailable")
                    replacement = peer.start(port)
                    ui.onNodeWithText("Refresh").performClick()
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
                    peer.rows = fixture.get("refreshedRows").toString()
                    val before = peer.requests.get()
                    ui.onNodeWithText("Refresh").performClick()
                    ui.waitUntil(15_000) { peer.requests.get() > before }
                    ui.waitUntil(15_000) {
                        ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
                    }
                    ui.waitForIdle()
                    ui.onNodeWithText("Reference post 05").assertIsDisplayed()
                    activity.recreate()
                    waitForText("Reference post 05")
                    ui.onNodeWithText("Reference post 05").assertIsDisplayed()
                    ui.onNode(hasScrollAction()).performScrollToIndex(0)
                    waitForText("New reference post")
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
                    waitForText("Native Red Square reference post")
                    check(blocked.renameTo(preserved))
                    check(blocked.mkdir())
                    val obstruction = java.io.File(blocked, "obstruction").apply { writeText("public fixture") }
                    try {
                        peer.rows = "[$updated]"
                        ui.onNodeWithText("Refresh").performClick()
                        ui.waitUntil(15_000) {
                            ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
                        }
                        ui.onNode(hasScrollAction()).performScrollToNode(hasText("New reference post"))
                        waitForText("New reference post")
                        waitForText("Some posts could not be saved or opened for offline reading.")
                    } finally {
                        check(obstruction.delete())
                        check(blocked.delete())
                        check(preserved.renameTo(blocked))
                    }
                    ui.onNodeWithText("Refresh").performClick()
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
                    val invalid = fixture.getAsJsonObject("archiveRow").deepCopy().apply { addProperty("tx", "{}") }
                    peer.rows = "[$invalid]"
                    ui.onNodeWithText("Refresh").performClick()
                    waitForText("The server returned posts this app could not read.")
                    ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
                    peer.assertHealthy()
                }
            }
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
