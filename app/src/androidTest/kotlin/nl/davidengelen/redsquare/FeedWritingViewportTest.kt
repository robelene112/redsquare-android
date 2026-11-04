package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.gson.JsonObject
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
            ui.withPostViewports(activity, folder, "writing", widthDp = width) { label, theme ->
                ui.waitUntil(15_000) { ui.onNodeWithTag("feed-post-list").isDisplayed() }
                val scale = context.resources.configuration.fontScale
                val reply = scale > 1.1f && scale < 1.5f
                val quote = scale > 1.5f
                if (reply) {
                    ui.clickVisiblePostBody("Native Red Square reference post")
                    ui.waitUntil(15_000) { ui.onNodeWithText("Conversation").isDisplayed() }
                }
                fun closed() {
                    // Disabled text fields lose SetText during saving. Observe the actual
                    // destination instead of confusing a busy editor with a closed one.
                    ui.waitUntil(15_000) {
                        if (reply) ui.onNode(hasText("Reply") and hasClickAction() and isEnabled()).isDisplayed()
                        else ui.onNodeWithTag("feed-post-list").isDisplayed()
                    }
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
                val educationAcknowledged = acknowledgeKeyboardFontEducation()
                editor.performScrollTo().assertIsDisplayed()
                var visibleHeight = 0f
                var completeHeight = 0f
                var inkPixels = 0
                ui.waitUntil(10_000) { runCatching {
                    visibleHeight = editor.fetchSemanticsNode().boundsInRoot.height
                    val full = editor.getUnclippedBoundsInRoot()
                    completeHeight = (full.bottom - full.top).value * context.resources.displayMetrics.density
                    if (visibleHeight < 80 * context.resources.displayMetrics.density || completeHeight > visibleHeight + 1) false
                    else {
                        val bitmap = editor.captureToImage().asAndroidBitmap()
                        inkPixels = 0
                        for (y in 4 until bitmap.height - 4) for (x in 16 until bitmap.width - 16) {
                            val color = bitmap.getPixel(x, y)
                            val red = android.graphics.Color.red(color)
                            val green = android.graphics.Color.green(color)
                            val blue = android.graphics.Color.blue(color)
                            if (if (theme == "Dark") minOf(red, green, blue) > 160 else maxOf(red, green, blue) < 110) inkPixels++
                        }
                        inkPixels > 40
                    }
                }.getOrDefault(false) }
                File(folder, "$label-visible-writing.json").writeText(JsonObject().apply {
                    addProperty("fontScale", context.resources.configuration.fontScale)
                    addProperty("keyboardVisible", focusedWindowImeBottom() > 0)
                    addProperty("keyboardEducationAcknowledged", educationAcknowledged)
                    addProperty("visibleEditorHeightPixels", visibleHeight)
                    addProperty("completeEditorHeightPixels", completeHeight)
                    addProperty("minimumHeightDp", 80)
                    addProperty("actualTextInkPixels", inkPixels)
                }.toString())
                ui.onNode(hasText("Send") and hasClickAction() and isEnabled()).assertIsDisplayed()
                captureNativeScreen(File(folder, "$label-ime.png"))
                // The real close action preserves unsent work; it never publishes it.
                ui.onNode(hasText(if (reply) "Back" else "Save draft") and hasClickAction() and isEnabled()).performClick()
                closed()
                open()
                editor.performScrollTo().assertTextContains(draft)
                captureNativeScreen(File(folder, "$label-restored.png"))
                ui.onNode(hasText("Back") and hasClickAction() and isEnabled()).performClick()
                closed()
                if (reply) ui.onNode(hasText("Back") and hasClickAction() and isEnabled()).performClick()
                ui.waitUntil(15_000) { ui.onNodeWithTag("feed-post-list").isDisplayed() }
            }
        }
    }
    private fun acknowledgeKeyboardFontEducation(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 37) return false
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val notice = By.pkg("com.google.android.inputmethod.latin").text("Keyboard font size updated")
        if (device.wait(Until.findObject(notice), 1_000) == null) return false
        val confirm = requireNotNull(device.findObject(By.pkg("com.google.android.inputmethod.latin").text("OK")))
        check(confirm.isEnabled && confirm.isClickable)
        confirm.click()
        ui.waitUntil(10_000) { !device.hasObject(notice) && focusedWindowImeBottom() > 0 }
        return true
    }

}
