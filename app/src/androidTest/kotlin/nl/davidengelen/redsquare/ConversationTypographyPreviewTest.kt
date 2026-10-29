package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.saito.RedSquarePosts
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName

class ConversationTypographyPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @get:Rule val testName = TestName()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Before fun clearPublicFeedCache() {
        deletePublicFeedCache()
    }

    @After fun removeTypographyFixtureFromPublicFeedCache() {
        deletePublicFeedCache()
    }

    private fun deletePublicFeedCache() {
        listOf("feed.owner.v1", "feed.owner.v1.bak", "feed.owner.v1.new").forEach {
            java.io.File(instrumentation.targetContext.noBackupFilesDir, it).delete()
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
        val postTag = "post-card-${row.get("sig").asString.take(12)}"
        val peer = ControlledFeedPeer("[$row]", archiveResponse = { "[$row]" })
        val server = peer.start()
        var primary: Throwable? = null
        try {
            recordStage(evidence, "fixture-ready")
            check(appearance.edit().putString("mode", "LIGHT").commit())
            ActivityScenario.launch<MainActivity>(intent(server.port)).use { activity ->
                captureFailure {
                    waitForText(postText)
                    recordStage(evidence, "feed-visible")
                    font.apply(1.3f)
                    recordStage(evidence, "font-scale-applied")
                    ui.waitUntil(10_000) { ui.onNodeWithText(postText).isDisplayed() }
                    recordStage(evidence, "scaled-post-visible")
                    ui.onNodeWithTag(postTag).performClick()
                    recordStage(evidence, "post-click-complete")
                    waitForConversation()
                    recordStage(evidence, "conversation-open")
                    assertCompleteBody(postText)
                    captureFeedEvidence("conversation-typography-evidence/light.png")
                    ui.onNodeWithText("Reply").assertIsDisplayed()
                    check(appearance.edit().putString("mode", "DARK").commit())
                    activity.recreate()
                    recordStage(evidence, "dark-recreated")
                    waitForConversation()
                    recordStage(evidence, "dark-conversation-open")
                    assertCompleteBody(postText)
                    captureFeedEvidence("conversation-typography-evidence/dark.png")
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

    private fun assertCompleteBody(postText: String) {
        ui.onNodeWithTag("post-body-text", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        val layout = bodyLayout()
        check(layout.lineCount > 5) { "Long conversation text should wrap at the larger supported size" }
        check(layout.getLineEnd(layout.lineCount - 1) == postText.length) {
            "Conversation text should keep its complete final line at the larger supported size"
        }
    }

    private fun bodyLayout(): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        ui.onNodeWithTag("post-body-text", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { check(it(layouts)) }
        return requireNotNull(layouts.firstOrNull())
    }

    private fun recordStage(folder: java.io.File, stage: String) {
        folder.mkdirs()
        java.io.File(folder, "stage.txt").writeText(stage)
    }

    private fun intent(port: Int) = Intent(instrumentation.targetContext, MainActivity::class.java)
        .putExtra("feedPeerUrl", "ws://127.0.0.1:$port/wsopen")

    private fun waitForText(text: String) {
        ui.waitUntil(15_000) { ui.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText(text).assertIsDisplayed()
    }

    private fun waitForConversation() {
        ui.waitUntil(15_000) {
            ui.onAllNodesWithText("Conversation").fetchSemanticsNodes().isNotEmpty() &&
                ui.onAllNodes(hasTestTag("conversation-post-list")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun captureFailure(check: () -> Unit) {
        try { check() } catch (failure: Throwable) {
            captureFeedEvidence("conversation-typography-evidence/${testName.methodName}-failure.png")
            throw failure
        }
    }
}
