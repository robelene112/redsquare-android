package nl.davidengelen.redsquare

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
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
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
            ui.onNode(hasText("Save draft") and hasClickAction()).performClick(); returned()
            ui.onNode(hasText("New post") and hasClickAction()).performClick()
            ui.waitUntil(15_000) { editor.isDisplayed() }
            editor.assertTextContains("A retained unsent draft from the scrolled feed")
            ui.onNode(hasText("Back") and hasClickAction()).performClick(); returned()
            captureNativeScreen(File(instrumentation.targetContext.cacheDir, "feed-reading-evidence/retained-feed-anchor.png"))
        }
    }
}
