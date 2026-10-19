package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppFrameReadingPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun returningFromChatRetainsTheScrolledFeedThroughRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val rows = instrumentation.context.assets.open("native-feed-scroll-reference.json").reader().use {
            JsonParser.parseReader(it).asJsonObject.get("initialRows").toString()
        }
        withAppFrameFixture("reading-position", "app-frame-reading-evidence", rows) { activity ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Reference post 20").isDisplayed() }
            ui.onNode(hasScrollAction()).performScrollToNode(hasText("Reference post 05"))
            val before = ui.onNodeWithText("Reference post 05").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
            capture("scrolled-feed-before-chat.png")
            ui.onNode(hasText("Chat") and hasClickAction()).performClick()
            ui.waitUntil(10_000) { ui.onNodeWithText("Public key").isDisplayed() }
            ui.onNode(hasText("Chat") and isSelected()).assertIsDisplayed()
            ui.onNodeWithText("Back up your wallet").assertDoesNotExist()
            ui.onNode(hasText("Home") and hasClickAction()).performClick()
            assertReadingPosition(before)
            capture("scrolled-feed-after-chat.png")
            activity.recreate()
            assertReadingPosition(before)
            capture("scrolled-feed-after-recreation.png")
        }
    }

    private fun assertReadingPosition(expected: Float) {
        ui.waitUntil(15_000) { ui.onNodeWithText("Reference post 05").isDisplayed() &&
            ui.onNodeWithText("Back up your wallet").isDisplayed() && ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
        assertEquals("Return to the same visible reading position", expected,
            ui.onNodeWithText("Reference post 05").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    private fun capture(name: String) = captureNativeScreen(File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "app-frame-reading-evidence/$name"))
}
