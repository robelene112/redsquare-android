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
            fun returned(deadline: Long? = null) {
                ui.revealAppNavigation()
                val remaining = deadline?.minus(SystemClock.uptimeMillis()) ?: 15_000
                check(remaining > 0) { "Save and return exceeded the original15-second budget" }
                ui.waitUntil(remaining) { target.isDisplayed() }
                if (deadline != null) check(SystemClock.uptimeMillis() <= deadline)
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
            // A real IME can move the Save target after editor readiness. Observe the
            // actual input target within the original15-second save/return budget.
            val saveDeadline = SystemClock.uptimeMillis() + 15_000
            fun remainingSave(): Long = (saveDeadline - SystemClock.uptimeMillis()).also {
                check(it > 0) { "Save and return exceeded the original15-second budget" }
            }
            editor.performScrollTo().performClick()
            editor.performTextReplacement("A retained unsent draft from the scrolled feed")
            val save = ui.onNode(hasText("Save draft") and hasClickAction() and isEnabled())
            val device = androidx.test.uiautomator.UiDevice.getInstance(instrumentation)
            var previous: androidx.compose.ui.geometry.Rect? = null
            var stableSince = 0L
            val observations = com.google.gson.JsonArray()
            try {
                ui.waitUntil(remainingSave()) {
                    val ime = focusedWindowImeBottom()
                    val shown = save.isDisplayed()
                    val bounds = if (shown) save.fetchSemanticsNode().boundsInRoot else null
                    val now = SystemClock.uptimeMillis()
                    val usable = ime > 0 && bounds != null && bounds.width > 0 && bounds.height > 0 &&
                        bounds.top >= 0 && bounds.bottom <= device.displayHeight - ime
                    if (!usable || bounds != previous) { previous = if (usable) bounds else null; stableSince = now }
                    if (observations.size() < 64) observations.add(com.google.gson.JsonObject().apply {
                        addProperty("elapsedMillis", now); addProperty("imeBottom", ime)
                        addProperty("shown", shown); addProperty("bounds", bounds?.toString())
                        addProperty("usable", usable)
                    })
                    usable && now - stableSince >= 100
                }
                remainingSave()
                save.assertIsDisplayed().assertIsEnabled().performClick()
                returned(saveDeadline)
            } finally {
                runCatching { File(instrumentation.targetContext.cacheDir,
                    "feed-reading-evidence/save-target-readiness.json").writeText(observations.toString()) }
            }
            ui.onNode(hasText("New post") and hasClickAction()).performClick()
            ui.waitUntil(15_000) { editor.isDisplayed() }
            editor.assertTextContains("A retained unsent draft from the scrolled feed")
            ui.onNode(hasText("Back") and hasClickAction()).performClick(); returned()
            captureNativeScreen(File(instrumentation.targetContext.cacheDir, "feed-reading-evidence/retained-feed-anchor.png"))
        }
    }
}
