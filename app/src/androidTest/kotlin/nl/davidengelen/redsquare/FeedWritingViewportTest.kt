package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** All writing modes use actual drafts, identity and IME in representative native viewports. */
class FeedWritingViewportTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun narrowWritingKeepsTheRealSignerTargetsAndDrafts() = exercise(320)
    @Test fun pixelSizedWritingKeepsTheRealSignerTargetsAndDrafts() = exercise(412)

    private fun exercise(width: Int) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "feed-writing-evidence").apply { mkdirs() }
        val signer = "Anon-${AndroidWalletStore(context).loadOrCreate().publicKey.take(6)}"
        withAppFrameFixture("writing-$width", "feed-writing-evidence") { activity ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.withPostViewports(activity, folder, "writing", widthDp = width) { label, _ ->
                val scale = context.resources.configuration.fontScale
                val reply = scale > 1.1f && scale < 1.5f
                val quote = scale > 1.5f
                if (reply) {
                    ui.clickVisiblePostBody("Native Red Square reference post")
                    ui.waitUntil(15_000) { ui.onNodeWithText("Conversation").isDisplayed() }
                }
                fun open() {
                    val action = if (reply) "Reply" else if (quote) "Quote" else "New post"
                    if (quote) ui.onNodeWithTag("feed-post-list").performScrollToNode(hasText(action) and hasClickAction())
                    val target = ui.onNode(hasText(action) and hasClickAction())
                    if (quote) target.performScrollTo()
                    target.assertIsDisplayed().performClick()
                    ui.waitUntil(15_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                }
                open()
                ui.onNodeWithText("Posting as").assertExists()
                ui.onAllNodesWithText(signer).fetchSemanticsNodes().also { assertTrue("The real signing identity is shown", it.isNotEmpty()) }
                if (reply || quote) ui.onNodeWithText("Native Red Square reference post", useUnmergedTree = true).assertExists()
                val draft = "Unsent ${if (reply) "reply" else if (quote) "quote" else "post"} for $label. Keep this exact text."
                val editor = ui.onNode(hasSetTextAction() and isEnabled())
                editor.performScrollTo().performClick().performTextReplacement(draft)
                ui.waitUntil(10_000) { focusedWindowImeBottom() > 0 }
                ui.onNode(hasText("Send") and hasClickAction() and isEnabled()).assertIsDisplayed()
                captureNativeScreen(File(folder, "$label-ime.png"))
                // The real close action preserves unsent work; it never publishes it.
                ui.onNode(hasText(if (reply) "Back" else "Save draft") and hasClickAction() and isEnabled()).performClick()
                ui.waitUntil(15_000) { ui.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty() }
                open()
                editor.performScrollTo().assertTextContains(draft)
                captureNativeScreen(File(folder, "$label-restored.png"))
                ui.onNode(hasText("Back") and hasClickAction() and isEnabled()).performClick()
                ui.waitUntil(15_000) { ui.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty() }
                if (reply) ui.onNode(hasText("Back") and hasClickAction()).performClick()
                ui.waitUntil(15_000) { ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
            }
        }
    }
}
