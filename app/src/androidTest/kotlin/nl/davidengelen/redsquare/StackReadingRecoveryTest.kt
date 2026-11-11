package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import org.junit.Rule
import org.junit.Test

class StackReadingRecoveryTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun anOlderArticleOutsideTheNewestCacheSurvivesAuthorBackAndOfflineRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val feedRows = instrumentation.context.assets.open("native-feed-reference.json").reader().use {
            JsonParser.parseReader(it).asJsonObject.get("archiveRow").let { row -> "[$row]" }
        }
        // Public unsafe fixture identity only. Padding exercises the ordinary byte budget without a private API.
        val writer = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val rows = (1..30).map { index ->
            val timestamp = 1700001000100L - index
            val body = JsonObject().apply {
                addProperty("module", "Stack"); addProperty("request", "create stack post request")
                add("data", JsonObject().apply {
                    addProperty("type", "stack_post"); addProperty("title", "Continued story $index")
                    addProperty("content", "The original content of continued story $index.")
                    addProperty("excerpt", "Available older writing")
                    addProperty("padding", "x".repeat(500_000))
                })
            }
            JsonObject().apply {
                addProperty("created_at", timestamp)
                addProperty("tx", SaitoTransaction.create(writer, body, timestamp).envelope())
            }
        }
        withAppFrameFixture("stack-reading-window", "stack-reading-evidence", archiveResponse = { criteria ->
            if (criteria.get("field1").asString == "Stack") {
                val before = criteria.get("created_earlier_than").asLong
                JsonArray().apply { rows.filter { it.get("created_at").asLong < before }.take(10).forEach(::add) }.toString()
            } else if (criteria.get("field1").asString == "RedSquare") feedRows else "[]"
        }) { activity ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.switchStackModule("Red Square", "Stack")
            ui.waitUntil(15_000) { ui.onAllNodesWithText("Continued story 1").fetchSemanticsNodes().isNotEmpty() }
            for (last in listOf(20, 30)) {
                ui.onNodeWithTag("stack-live-content").performScrollToNode(hasText("Load more articles"))
                ui.onNodeWithText("Load more articles").assertIsEnabled().performClick()
                val deadline = android.os.SystemClock.uptimeMillis() + 15_000
                ui.waitUntil(15_000) {
                    try {
                        ui.onNodeWithTag("stack-live-content").performScrollToNode(hasText("Continued story $last"))
                        true
                    } catch (failure: AssertionError) {
                        if (failure.message.orEmpty().contains("scroll", ignoreCase = true)) false else throw failure
                    }
                }
                check(android.os.SystemClock.uptimeMillis() <= deadline) { "Older content must be reachable within fifteen seconds" }
            }
            ui.onNodeWithText("Continued story 30").assertIsDisplayed().performClick()
            ui.onNodeWithTag("stack-live-content").performScrollToNode(hasText("Anon-pncSjs"))
            ui.onNode(hasText("Anon-pncSjs") and hasClickAction()).performClick()
            ui.waitUntil(10_000) { ui.onNodeWithText("Follow").isDisplayed() }
            ui.revealAppNavigation()
            ui.onNode(hasText("Back") and hasClickAction()).performClick()
            ui.onNodeWithTag("stack-live-content").performScrollToNode(hasText("The original content of continued story 30."))
            ui.onNodeWithText("The original content of continued story 30.").assertIsDisplayed()
            val originalPixel = ui.onNodeWithText("The original content of continued story 30.").fetchSemanticsNode().boundsInRoot.top
            activity.onActivity { it.intent.putExtra("feedPeerUrl", "ws://127.0.0.1:1/wsopen") }
            activity.recreate()
            ui.waitUntil(15_000) { ui.onNodeWithText("The original content of continued story 30.").isDisplayed() }
            org.junit.Assert.assertEquals(originalPixel,
                ui.onNodeWithText("The original content of continued story 30.").fetchSemanticsNode().boundsInRoot.top, 1f)
            ui.onNodeWithTag("stack-live-content").performScrollToNode(hasText("The original content of continued story 30."))
            ui.onNodeWithText("The original content of continued story 30.").assertIsDisplayed()
            captureNativeScreen(File(instrumentation.targetContext.cacheDir, "stack-reading-evidence/older-offline-article.png"))
        }
    }
}
