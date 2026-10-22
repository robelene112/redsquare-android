package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Search is deliberate: keyboard submission reads history, clearing never does. */
class SearchEntryPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun keyboardSearchAndClearKeepUserInControlOfSearchRequests() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val folder = File(context.cacheDir, "search-entry-evidence").apply { mkdirs() }
        val rows = instrumentation.context.assets.open("native-feed-reference.json").reader().use {
            JsonParser.parseReader(it).asJsonObject.get("archiveRow").let { row -> "[$row]" }
        }
        val searches = AtomicInteger()
        val quoteSearches = AtomicInteger()
        lateinit var peer: ControlledFeedPeer
        withAppFrameFixture("keyboard-search-clear", "search-entry-evidence", "[]",
            configurePeer = { peer = it }, archiveResponse = { criteria ->
            if (criteria.get("limit").asInt == 100 && !criteria.has("field4")) {
                when (criteria.get("field1").asString) {
                    "RedSquare" -> { searches.incrementAndGet(); rows }
                    "RedSquareRetweet" -> { quoteSearches.incrementAndGet(); "[]" }
                    else -> error("Unexpected search history stream")
                }
            } else "[]"
        }) { activity ->
            ui.waitUntil(15_000) { ui.onNode(hasText("Later") and hasClickAction()).isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.onNode(hasText("Search") and hasClickAction()).performClick()
            val editor = hasSetTextAction() and isEnabled()
            ui.waitUntil(15_000) { ui.onNode(editor).isDisplayed() }
            ui.onNode(editor).performClick().performTextInput("reference post")
            ui.waitUntil(10_000) { focusedWindowImeBottom() > 0 }
            captureNativeScreen(File(folder, "keyboard-before-submit.png"))
            assertEquals("Typing must not search posts", 0, searches.get())
            assertEquals("Typing must not search quotes", 0, quoteSearches.get())
            ui.onNode(editor).performImeAction()
            ui.waitUntil(15_000) {
                searches.get() == 1 && quoteSearches.get() == 1 &&
                    ui.onNodeWithText("Native Red Square reference post").isDisplayed()
            }
            ui.waitUntil(10_000) { focusedWindowImeBottom() == 0 }
            captureNativeScreen(File(folder, "keyboard-search-result.png"))

            ui.onNodeWithContentDescription("Clear search").assertIsDisplayed().performClick()
            ui.onNode(editor).assert(hasText(""))
            ui.onAllNodesWithText("Native Red Square reference post").assertCountEquals(0)
            captureNativeScreen(File(folder, "cleared-search.png"))
            activity.recreate()
            ui.waitUntil(15_000) { ui.onNode(editor).isDisplayed() }
            ui.onNode(editor).assert(hasText(""))
            ui.onAllNodesWithText("Native Red Square reference post").assertCountEquals(0)
            assertEquals("Clearing and recreation must not start another history request", 1, searches.get())
            assertEquals("Clearing and recreation must not start another quote request", 1, quoteSearches.get())
            assertTrue("Ordinary text search must not check a username",
                peer.registryQueries.none { it.get("request").asString == "registry namecheck" })

            ui.onNode(editor).performClick().performTextInput("reference post")
            ui.onNode(editor).performImeAction()
            ui.waitUntil(15_000) {
                searches.get() == 2 && quoteSearches.get() == 2 &&
                    ui.onNodeWithText("Native Red Square reference post").isDisplayed()
            }
            captureNativeScreen(File(folder, "deliberate-search-after-clear.png"))
        }
    }
}
