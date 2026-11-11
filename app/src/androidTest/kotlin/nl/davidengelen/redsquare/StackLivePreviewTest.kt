package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import java.io.File
import org.junit.Rule
import org.junit.Test

/** Actual module reading over the owned signed-Archive boundary, not the historical design fixture. */
class StackLivePreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    private fun rows(): String {
        val context = InstrumentationRegistry.getInstrumentation().context
        val row = context.assets.open("native-stack-reference.json").reader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("reader")
        }.deepCopy()
        return "[$row]"
    }

    private fun switch(current: String, next: String) {
        ui.revealAppNavigation()
        ui.onNodeWithContentDescription("Switch app: $current").performClick()
        ui.onNode(hasText(next) and hasClickAction() and hasAnyAncestor(isPopup() or isDialog())).performClick()
        ui.waitUntil(5_000) { ui.onNodeWithContentDescription("Switch app: $next").isDisplayed() }
    }

    @Test fun genuineSignedStoryOpensItsAuthorAndSurvivesOfflineRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val feedRows = instrumentation.context.assets.open("native-feed-reference.json").reader().use {
            JsonParser.parseReader(it).asJsonObject.get("archiveRow").let { row -> "[$row]" }
        }
        val stackRows = rows()
        withAppFrameFixture("stack-live", "stack-live-evidence", configurePeer = {}, archiveResponse = { criteria ->
            when (criteria.get("field1").asString) { "Stack" -> stackRows; "RedSquare" -> feedRows; else -> "[]" }
        }) { activity ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            switch("Red Square", "Stack")
            ui.onNodeWithText("Stack design preview").assertDoesNotExist()
            ui.onNodeWithText("A Node in a Browser Tab").assertDoesNotExist()
            ui.waitUntil(15_000) { ui.onAllNodesWithText("Open networks, real writing").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("stack-live-content").performScrollToNode(hasText("Open networks, real writing"))
            ui.onNodeWithText("Open networks, real writing").performClick()
            ui.onNodeWithText("Article").assertIsDisplayed()
            ui.onNodeWithTag("stack-live-content").performScrollToNode(hasText("Original Markdown.", substring = true))
            ui.onNodeWithText("Original Markdown.", substring = true).assertIsDisplayed()
            ui.onNodeWithText("![Literal](stack:image:missing)", substring = true).assertExists()
            ui.onNodeWithText("![Fenced example](stack:image:missing)", substring = true).assertExists()
            captureNativeScreen(File(instrumentation.targetContext.cacheDir, "stack-live-evidence/article.png"))
            ui.revealAppNavigation()
            ui.onNodeWithContentDescription("Back").performClick()
            ui.onNodeWithText("Open networks, real writing").performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("Open networks, real writing").performClick()
            ui.onNodeWithTag("stack-live-content").performScrollToNode(hasText("Anon-pncSjs"))
            ui.onNode(hasText("Anon-pncSjs") and hasClickAction()).performClick()
            ui.waitUntil(5_000) { ui.onNodeWithText("Follow").isDisplayed() }
            ui.onNodeWithText("Follow").performClick()
            ui.waitUntil(5_000) { ui.onNode(hasText("Following") and hasClickAction() and hasAnyAncestor(hasTestTag("stack-live-content"))).isDisplayed() }
            ui.revealAppNavigation()
            ui.onNodeWithContentDescription("Back").performClick()
            ui.onNodeWithContentDescription("Back").performClick()
            ui.onNode(hasText("Following") and hasClickAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Selected)).performClick()
            ui.onNodeWithTag("stack-live-content").performScrollToNode(hasText("Open networks, real writing"))
            ui.onNodeWithText("Open networks, real writing").assertIsDisplayed()
            // Recreate against an explicitly unavailable peer. The same signed story must come from disk.
            activity.onActivity { it.intent.putExtra("feedPeerUrl", "ws://127.0.0.1:1/wsopen") }
            activity.recreate()
            ui.waitUntil(15_000) { ui.onAllNodesWithText("Open networks, real writing").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("stack-live-content").performScrollToNode(hasText("Open networks, real writing"))
            ui.onNodeWithTag("stack-live-content").performScrollToIndex(0)
            ui.waitUntil(15_000) { ui.onNodeWithText("Could not refresh articles. Saved articles remain available.").isDisplayed() }
            ui.onNodeWithTag("stack-live-content").performScrollToNode(hasText("Open networks, real writing"))
            ui.onNodeWithText("Open networks, real writing").performClick()
            ui.onNodeWithTag("stack-live-content").performScrollToNode(hasText("Original Markdown.", substring = true))
            ui.onNodeWithText("Original Markdown.", substring = true).assertIsDisplayed()
            captureNativeScreen(File(instrumentation.targetContext.cacheDir, "stack-live-evidence/offline-article.png"))
        }
    }

    @Test fun liveCardsAndReadingRemainUsableAcrossThemesAndTextSizes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val feedRows = instrumentation.context.assets.open("native-feed-reference.json").reader().use {
            JsonParser.parseReader(it).asJsonObject.get("archiveRow").let { row -> "[$row]" }
        }
        val stackRows = rows()
        val folder = File(instrumentation.targetContext.cacheDir, "stack-live-evidence").apply { mkdirs() }
        withAppFrameFixture("stack-live-viewports", "stack-live-evidence", archiveResponse = { criteria ->
            when (criteria.get("field1").asString) { "Stack" -> stackRows; "RedSquare" -> feedRows; else -> "[]" }
        }) { activity ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.withPostViewports(activity, folder, "stack-live", listOf(1f, 1.3f, 2f), 320) { label, _ ->
                switch("Red Square", "Stack")
                ui.waitUntil(15_000) { ui.onAllNodesWithText("Open networks, real writing").fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithTag("stack-live-content").performScrollToNode(hasText("Open networks, real writing"))
                ui.onNodeWithText("Open networks, real writing").assertIsDisplayed()
                captureNativeScreen(File(folder, "$label-card.png"))
                ui.onNodeWithText("Open networks, real writing").performClick()
                ui.onNodeWithTag("stack-live-content").performScrollToNode(hasText("Original Markdown.", substring = true))
                ui.onNodeWithText("Original Markdown.", substring = true).assertIsDisplayed()
                captureNativeScreen(File(folder, "$label-reader.png"))
                ui.revealAppNavigation()
                ui.onNodeWithContentDescription("Back").performClick()
                switch("Stack", "Red Square")
                ui.waitUntil(15_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
            }
        }
    }

    @Test fun emptyNodeOffersARealChoiceWithoutInventingStories() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val feedRows = instrumentation.context.assets.open("native-feed-reference.json").reader().use {
            JsonParser.parseReader(it).asJsonObject.get("archiveRow").let { row -> "[$row]" }
        }
        withAppFrameFixture("stack-empty", "stack-live-evidence", archiveResponse = { criteria ->
            if (criteria.get("field1").asString == "RedSquare") feedRows else "[]"
        }) { _ ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            switch("Red Square", "Stack")
            ui.waitUntil(15_000) { ui.onAllNodesWithText("Choose node").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("stack-live-content").performScrollToNode(hasText("Choose node"))
            ui.onNodeWithText("Choose node").performClick()
            ui.waitUntil(5_000) { ui.onNodeWithText("saito.io").isDisplayed() }
            ui.onNodeWithText("saito.io").assertHasClickAction()
            ui.onNodeWithText("A Node in a Browser Tab").assertDoesNotExist()
            captureNativeScreen(File(instrumentation.targetContext.cacheDir, "stack-live-evidence/node-choice.png"))
        }
    }
}
