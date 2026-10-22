package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Read a result and its author without losing the original place or requesting history again. */
class SearchReadingPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun threadProfileAndRecreationKeepTheSameSearchReadingPosition() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = File(instrumentation.targetContext.cacheDir, "search-reading-evidence").apply { mkdirs() }
        val rows = instrumentation.context.assets.open("native-search-reference.json").reader().use {
            JsonParser.parseReader(it).asJsonObject.get("firstPage").toString()
        }
        val searches = AtomicInteger()
        val quotes = AtomicInteger()
        withAppFrameFixture("search-reading", "search-reading-evidence", "[]",
            archiveResponse = { criteria ->
                if (criteria.get("limit").asInt == 100 && !criteria.has("field4") && !criteria.has("field2")) {
                    searches.incrementAndGet(); rows
                } else "[]"
            }, quoteArchiveResponse = { criteria ->
                if (criteria.get("limit").asInt == 100 && !criteria.has("field4") && !criteria.has("field2")) quotes.incrementAndGet()
                "[]"
            }) { activity ->
            ui.waitUntil(15_000) { ui.onNode(hasText("Later") and hasClickAction()).isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.onNode(hasText("Search") and hasClickAction()).performClick()
            ui.onNode(hasSetTextAction()).performTextInput("Available")
            ui.waitUntil(15_000) { ui.onNode(hasContentDescription("Search history") and isEnabled()).isDisplayed() }
            ui.onNode(hasSetTextAction()).performImeAction()
            ui.waitUntil(15_000) { ui.onNodeWithText("Available newest result").isDisplayed() && quotes.get() == 1 }
            val anchor = "Available page post 050"
            ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(anchor))
            val before = ui.onNodeWithText(anchor).assertIsDisplayed().getUnclippedBoundsInRoot().top.value
            captureNativeScreen(File(folder, "before-opening-result.png"), anchor)
            fun assertPosition() {
                ui.waitUntil(15_000) { ui.onNodeWithText(anchor).isDisplayed() }
                ui.onNode(hasSetTextAction()).assertTextContains("Available")
                assertEquals("Return to the same visible result without scrolling", before,
                    ui.onNodeWithText(anchor).getUnclippedBoundsInRoot().top.value, 1f)
                assertEquals("Returning does not repeat the post search", 1, searches.get())
                assertEquals("Returning does not repeat the quote search", 1, quotes.get())
            }
            ui.onNodeWithText(anchor).performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Conversation").isDisplayed() }
            ui.onNode(hasText("Back") or hasContentDescription("Back")).performClick()
            assertPosition()
            captureNativeScreen(File(folder, "after-thread.png"), anchor)
            val card = hasText(anchor) and hasClickAction()
            ui.onNode(hasContentDescription("View profile for", substring = true) and hasAnyAncestor(card)).performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Profile").isDisplayed() }
            ui.onNode(hasText("Back") or hasContentDescription("Back")).performClick()
            assertPosition()
            captureNativeScreen(File(folder, "after-profile.png"), anchor)
            activity.recreate()
            assertPosition()
            captureNativeScreen(File(folder, "after-recreation.png"), anchor)
        }
    }
}
