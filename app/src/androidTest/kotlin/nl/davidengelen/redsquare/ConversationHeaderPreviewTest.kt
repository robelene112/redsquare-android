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

class ConversationHeaderPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun compactHistoryAndCombinedRecoveryKeepAuthenticRepliesAndUnsentWork() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val rows = instrumentation.context.assets.open("native-thread-reference.json").reader().use { JsonParser.parseReader(it).asJsonObject }
        val root = rows.getAsJsonObject("root")
        val reply = rows.getAsJsonObject("firstReply")
        val nested = rows.getAsJsonObject("nestedReply")
        val standalone = postingArchiveRow(SaitoTransaction.create(
            SaitoWallet.fromPrivateKey("0".repeat(63) + "1"), // Published unsafe fixture only.
            RedSquarePosts.message("A standalone reading header"), 1_700_000_999_001L))
        val release = CountDownLatch(1)
        val recovered = AtomicBoolean(false)
        var peer: ControlledFeedPeer? = null
        val fault = File(context.noBackupFilesDir, "conversation.owner.${root.get("sig").asString}.v1")
        val obstacle = File(fault, "owned-cache-fault")
        withAppFrameFixture("combined-recovery", "conversation-header-evidence", "[$standalone]",
            configurePeer = { peer = it },
            archiveResponse = { query ->
                if (query.get("field5")?.asString == standalone.get("sig").asString) "[$standalone]"
                else if (query.has("field5")) {
                    check(release.await(10, TimeUnit.SECONDS))
                    if (recovered.get()) "[$root,$reply,$nested]" else "[$reply,$nested,{}]"
                } else if (query.has("sig")) { if (recovered.get()) "[$root]" else "[]" } else if (query.has("field4")) "[$standalone]" else "[$reply]"
            }) { activity ->
            var primary: Throwable? = null
            try {
                ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
                ui.onNodeWithText("Later").performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("A standalone reading header").isDisplayed() }
                ui.onNodeWithText("A standalone reading header", useUnmergedTree = true).performTouchInput { click(center) }
                ui.waitUntil(15_000) { ui.onNodeWithText("Conversation").isDisplayed() }
                ui.onNodeWithText(context.getString(R.string.conversation_history)).assertDoesNotExist()
                ui.onNode(hasText("Conversation information") and hasClickAction()).assertIsDisplayed()
                ui.onNode(hasText("Back") and hasClickAction()).performClick()
                ui.waitUntil(15_000) { ui.onNodeWithTag("feed-post-list").isDisplayed() }
                // General feed deliberately excludes replies. Open the authentic reply through Search.
                ui.onNode(hasText("Search") and hasClickAction()).performClick()
                val search = ui.onNode(hasSetTextAction() and isEnabled())
                ui.waitUntil(15_000) { search.isDisplayed() }
                search.performClick().performTextInput("Independent")
                search.performImeAction()
                ui.waitUntil(15_000) { ui.onNodeWithText("Independent Saito reply").isDisplayed() }
                ui.waitUntil(10_000) { focusedWindowImeBottom() == 0 }
                check(fault.mkdir()); obstacle.writeText("public owned fixture")
                ui.onNodeWithText("Independent Saito reply", useUnmergedTree = true).performTouchInput { click(center) }
                ui.waitUntil(15_000) { ui.onNodeWithText("Conversation").isDisplayed() }
                ui.onNodeWithText(context.getString(R.string.conversation_history)).assertDoesNotExist()
                ui.onNode(hasText("Back") and hasClickAction()).assertIsDisplayed()
                ui.onNode(hasText("Reply") and hasClickAction()).assertIsDisplayed()
                ui.onNodeWithText(context.getString(R.string.conversation_loading)).assertIsDisplayed()
                captureNativeScreen(File(context.cacheDir, "conversation-header-evidence/loading.png"))
                release.countDown()
                ui.waitUntil(15_000) { ui.onNodeWithText("Parent post unavailable").isDisplayed() }
                ui.onNode(hasText("Conversation information") and hasClickAction()).performClick()
                ui.onNodeWithText(context.getString(R.string.conversation_history)).assertIsDisplayed()
                captureNativeScreen(File(context.cacheDir, "conversation-header-evidence/history-details.png"))
                ui.onNodeWithText("Done").performClick()
                ui.onNodeWithContentDescription("Details for Parent post unavailable").performClick()
                ui.onNodeWithText(context.getString(R.string.conversation_parent_unavailable)).assertIsDisplayed()
                ui.onNodeWithText(context.getString(R.string.conversation_unreadable)).assertIsDisplayed()
                ui.onNodeWithText(context.getString(R.string.feed_cache_warning)).assertIsDisplayed()
                captureNativeScreen(File(context.cacheDir, "conversation-header-evidence/all-explanations.png"))
                ui.onNodeWithText("Done").performClick()
                captureNativeScreen(File(context.cacheDir, "conversation-header-evidence/compact-recovery.png"))
                check(obstacle.delete()); check(fault.delete()); recovered.set(true)
                ui.onNode(hasText("Retry") and hasClickAction() and hasAnyAncestor(hasTestTag("conversation-status"))).assertIsEnabled().performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText(context.getString(R.string.conversation_refresh_success)).isDisplayed() }
                ui.onNodeWithTag("conversation-post-list").performScrollToNode(hasText("Native Red Square reference post"))
                ui.onNodeWithText("Native Red Square reference post", useUnmergedTree = true).assertIsDisplayed()
                ui.onNodeWithText("Parent post unavailable").assertDoesNotExist()
                requireNotNull(peer).rejectNewConnections = true
                ui.refreshVisibleView()
                ui.waitUntil(20_000) { ui.onNodeWithText("Could not refresh replies").isDisplayed() }
                ui.onNodeWithContentDescription("Details for Could not refresh replies").performClick()
                ui.onNodeWithText(context.getString(R.string.conversation_error)).assertIsDisplayed()
                ui.onNodeWithText("Done").performClick()
                captureNativeScreen(File(context.cacheDir, "conversation-header-evidence/cached-outage.png"))
                requireNotNull(peer).rejectNewConnections = false
                ui.onNode(hasText("Retry") and hasClickAction() and hasAnyAncestor(hasTestTag("conversation-status"))).performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText(context.getString(R.string.conversation_refresh_success)).isDisplayed() }
                ui.onNode(hasText("Reply") and hasClickAction()).performClick()
                val editor = ui.onNode(hasSetTextAction() and isEnabled())
                ui.waitUntil(15_000) { editor.isDisplayed() }
                editor.performTextInput("An exact unsent reply during the thread redesign")
                ui.onNode(hasText("Save draft") and hasClickAction()).performClick()
                ui.waitUntil(15_000) { ui.onNode(hasText("Reply") and hasClickAction()).isDisplayed() }
                ui.onNode(hasText("Reply") and hasClickAction()).performClick()
                ui.waitUntil(15_000) { editor.isDisplayed() }
                editor.assertTextContains("An exact unsent reply during the thread redesign")
                captureNativeScreen(File(context.cacheDir, "conversation-header-evidence/recovered-reply.png"))
                ui.onNode(hasText("Back") and hasClickAction()).performClick()
                ui.waitUntil(15_000) { ui.onNodeWithTag("conversation-post-list").isDisplayed() }
            } catch (failure: Throwable) {
                primary = failure
                try { captureNativeScreen(File(context.cacheDir, "conversation-header-evidence/before-cleanup-failure.png")) }
                catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
                throw failure
            }
            finally {
                release.countDown()
                if (fault.isDirectory) cleanupAfterTest(primary, { activity.close() },
                    { check(!obstacle.exists() || obstacle.delete()) }, { check(fault.delete()) })
            }
        }
    }
}
