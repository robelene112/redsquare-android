package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.RedSquareFeed
import nl.davidengelen.redsquare.saito.SaitoWallet
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Actual retained profile post/offset, not a fresh scroll to the expected item after Back. */
class ProfileReadingReturnTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun ownProfileKeepsTheExactPostAfterConversationAndUnsentAuthorEdit() = withProfileIdentityWallet {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val rows = instrumentation.context.assets.open("native-feed-scroll-reference.json").reader().use {
            JsonParser.parseReader(it).asJsonObject.get("initialRows").toString()
        }
        withAppFrameFixture("profile-reading", "profile-redesign-evidence", rows) {
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.onNode(hasText("Your profile") and hasClickAction()).performClick()
            ui.revealProfileHistoryItem("Reference post 10")
            val targetId = RedSquareFeed.readArchive(rows).posts.single { it.text == "Reference post 10" }.id
            val density = instrumentation.targetContext.resources.displayMetrics.density
            ui.onNode(hasScrollToIndexAction()).performSemanticsAction(SemanticsActions.ScrollBy) {
                check(it(0f, -64 * density))
            }
            val target = ui.onNodeWithText("Reference post 10")
            val before = target.fetchSemanticsNode().boundsInRoot.top
            target.performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Conversation").isDisplayed() }
            ui.onNode(hasText("Back") and hasClickAction()).performClick()
            ui.waitUntil(15_000) { target.isDisplayed() }
            assertEquals("Back retains the visible profile post", before, target.fetchSemanticsNode().boundsInRoot.top, 1f)
            ui.onNode(hasContentDescription("Post actions for Anon-jesTu2") and
                hasAnyAncestor(hasTestTag("post-card-${targetId.take(12)}")))
                .assertIsDisplayed().performClick()
            ui.onNodeWithText("Edit").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Edit post").isDisplayed() }
            val editor = ui.onNode(hasSetTextAction() and isEnabled())
            ui.waitUntil(15_000) { editor.isDisplayed() }
            editor.performTextReplacement("Unsent edit from the retained profile position")
            ui.onNode(hasText("Back") and hasClickAction()).performClick()
            ui.waitUntil(15_000) { target.isDisplayed() }
            assertEquals("Unsent editing retains the profile post and pixel offset", before,
                target.fetchSemanticsNode().boundsInRoot.top, 1f)
            captureNativeScreen(File(instrumentation.targetContext.cacheDir, "profile-redesign-evidence/profile-return-anchor.png"))
        }
    }
}

/** Published unsafe fixture identity, restored only after the nested Activity/fixtures close. */
internal fun withProfileIdentityWallet(block: () -> Unit) {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    check(context.packageName == "nl.davidengelen.redsquare.dev" &&
        android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
    val storage = AndroidWalletStore(context)
    val previous = storage.loadOrCreate()
    val fixture = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
    var failure: Throwable? = null
    try { storage.replace(previous, fixture); block() }
    catch (error: Throwable) { failure = error; throw error }
    finally { cleanupAfterTest(failure, {
        storage.replace(storage.loadExisting(), previous)
        check(storage.loadExisting().exportJson() == previous.exportJson())
    }) }
}
