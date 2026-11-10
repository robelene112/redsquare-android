package nl.davidengelen.redsquare

import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.RedSquareFeed
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class FeedReadingRedesignTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun threadProfileAndUnsentWritingReturnToTheSameFeedPostAndPixel() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val rows = instrumentation.context.assets.open("native-feed-scroll-reference.json").reader().use {
            JsonParser.parseReader(it).asJsonObject.get("initialRows").toString()
        }
        val post = RedSquareFeed.readArchive(rows).posts.single { it.text == "Reference post 10" }
        withAppFrameFixture("feed-reading", "feed-reading-evidence", rows) {
            try {
            val readyBy = SystemClock.uptimeMillis() + 15_000
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            // Dismissing the backup reminder does not finish the asynchronous feed read.
            // Observe actual content within the original initial-readiness budget.
            val remaining = readyBy - SystemClock.uptimeMillis()
            check(remaining > 0) { "Initial feed readiness exceeded its 15-second budget" }
            ui.waitUntil(remaining) {
                ui.onNodeWithText("Reference post 20", useUnmergedTree = true).isDisplayed() &&
                    ui.refreshableContentReady()
            }
            ui.onNodeWithTag("feed-post-list").performScrollToNode(hasTestTag("post-card-${post.id.take(12)}"))
            ui.revealAppNavigation()
            val target = ui.onNodeWithText("Reference post 10", useUnmergedTree = true)
            ui.onNodeWithTag("post-card-${post.id.take(12)}").performScrollTo().assertIsDisplayed()
            target.assertIsDisplayed()
            val before = target.fetchSemanticsNode().boundsInRoot.top
            fun returned() {
                ui.revealAppNavigation()
                ui.waitUntil(15_000) { target.isDisplayed() }
                assertEquals("The same feed post retains its pixel offset", before, target.fetchSemanticsNode().boundsInRoot.top, 1f)
            }
            target.performTouchInput { click(center) }
            ui.waitUntil(15_000) { ui.onNodeWithText("Conversation").isDisplayed() }
            ui.onNode(hasText("Back") and hasClickAction()).performClick(); returned()
            ui.onNode(hasContentDescription("View profile for Anon-jesTu2") and
                hasAnyAncestor(hasTestTag("post-card-${post.id.take(12)}"))).assertIsDisplayed().performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("Profile") and !hasClickAction()).isDisplayed() }
            ui.onNode(hasText("Back") and hasClickAction()).performClick(); returned()
            ui.onNode(hasText("New post") and hasClickAction()).performClick()
            val editor = ui.onNode(hasSetTextAction() and isEnabled())
            ui.waitUntil(15_000) { editor.isDisplayed() }
            editor.performScrollTo().performTextReplacement("A retained unsent draft from the scrolled feed")
            val save = ui.onNode(hasText("Save draft") and hasClickAction())
            val control = save.fetchSemanticsNode()
            val snapshotAt = SystemClock.elapsedRealtime()
            val priorBounds = control.boundsInRoot
            val priorDisabled = control.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
            var observedAt = 0L
            var observedCenter = androidx.compose.ui.geometry.Offset.Zero
            var observedSize = androidx.compose.ui.unit.IntSize.Zero
            // Android performClick delegates to this same public touch injection and default click.
            save.performTouchInput {
                observedCenter = center; observedSize = visibleSize
                observedAt = SystemClock.elapsedRealtime()
                click()
            }
            File(instrumentation.targetContext.cacheDir, "feed-reading-evidence/save-touch.json").writeText(
                com.google.gson.JsonObject().apply {
                    addProperty("priorSnapshotAt", snapshotAt); addProperty("priorBounds", priorBounds.toString())
                    addProperty("priorDisabled", priorDisabled); addProperty("observedAt", observedAt)
                    addProperty("localCenter", observedCenter.toString()); addProperty("visibleSize", observedSize.toString())
                }.toString())
            returned()
            ui.onNode(hasText("New post") and hasClickAction()).performClick()
            ui.waitUntil(15_000) { editor.isDisplayed() }
            editor.assertTextContains("A retained unsent draft from the scrolled feed")
            ui.onNode(hasText("Back") and hasClickAction()).performClick(); returned()
            captureNativeScreen(File(instrumentation.targetContext.cacheDir, "feed-reading-evidence/retained-feed-anchor.png"))
            } finally {
                runCatching {
                    val device = androidx.test.uiautomator.UiDevice.getInstance(instrumentation)
                    File(instrumentation.targetContext.cacheDir, "feed-reading-evidence/save-transition.log").writeText(
                        device.executeShellCommand("logcat -d -v brief -s DEBUG-save-9798:D *:S"))
                }
            }
        }
    }
}
