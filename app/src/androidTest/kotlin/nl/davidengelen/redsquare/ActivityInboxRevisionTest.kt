package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.util.Base64
import nl.davidengelen.redsquare.feed.AndroidPostRevisions
import nl.davidengelen.redsquare.saito.*
import org.junit.Rule
import org.junit.Test

/** Same public author-proof observation seam as mountedQuoteKeepsCommentaryWhileOriginalFollowsVerifiedEditAndDeletion. */
class ActivityInboxRevisionTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun retainedMentionPreviewFollowsLearnedEditAndDeletionThroughOfflineRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val folder = File(context.cacheDir, "activity-revision-evidence").apply { mkdirs() }
        val reference = instrumentation.context.assets.open("native-notification-reference.json").reader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("incomingMention")
        }
        val original = RedSquareFeed.decode(reference.get("tx").asString)
        val author = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Published unsafe fixture only.
        val revisedText = "Updated activity preview"
        fun command(request: String, time: Long) = SaitoTransaction.create(author, JsonObject().apply {
            addProperty("module", "RedSquare"); addProperty("request", request)
            add("data", JsonObject().apply {
                addProperty("tweet_id", original.id)
                if (request == "edit tweet") addProperty("text", revisedText)
            })
        }, time, original.recipients)
        val edit = command("edit tweet", 1_700_000_001_000)
        val edited = JsonParser.parseString(original.envelope).asJsonObject.apply {
            val options = JsonObject().apply { addProperty("update_tx", edit.envelope()) }
            addProperty("opt", Base64.getEncoder().encodeToString(options.toString().toByteArray()))
        }
        withActivityInbox("revisions", listOf("incomingMention"), evidenceFolder = "activity-revision-evidence") { activity, peer ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
            ui.onNode(hasText("Notifications") and hasClickAction()).performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText(original.text).isDisplayed() }
            val revisions = AndroidPostRevisions(context, SaitoBackend.OWNER)
            revisions.observe(RedSquareFeed.decode(edited.toString()))
            ui.waitUntil(15_000) { ui.onNodeWithText(revisedText).isDisplayed() }
            ui.onAllNodesWithText(original.text).assertCountEquals(0)
            captureNativeScreen(File(folder, "learned-edit.png"), revisedText)
            revisions.confirmDeletion(original, RedSquareAuthorActions.decode(command("delete tweet", 1_700_000_002_000).envelope()))
            ui.waitUntil(15_000) { ui.onNodeWithText("Post preview unavailable").isDisplayed() }
            ui.onNodeWithText("Mentioned you").assertIsDisplayed()
            ui.onNodeWithText("1 unread").assertIsDisplayed()
            ui.onAllNodesWithText(original.text).assertCountEquals(0)
            ui.onAllNodesWithText(revisedText).assertCountEquals(0)
            captureNativeScreen(File(folder, "learned-deletion.png"), "Post preview unavailable")
            peer.rejectNewConnections = true
            activity.recreate()
            ui.waitUntil(15_000) { ui.onNodeWithText("Could not refresh activity.").isDisplayed() }
            ui.onNodeWithText("Post preview unavailable").assertIsDisplayed()
            ui.onNodeWithText("Mentioned you").assertIsDisplayed()
            ui.onNodeWithText("1 unread").assertIsDisplayed()
            ui.onAllNodesWithText(original.text).assertCountEquals(0)
            ui.onAllNodesWithText(revisedText).assertCountEquals(0)
            captureNativeScreen(File(folder, "deleted-offline-recreated.png"), "Post preview unavailable")
        }
    }
}
