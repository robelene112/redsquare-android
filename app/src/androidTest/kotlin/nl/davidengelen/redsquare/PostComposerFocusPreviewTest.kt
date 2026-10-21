package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.google.gson.JsonObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Actual writing order, local return and platform IME through the native UI. No publication. */
class PostComposerFocusPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun writingPrecedesOptionalAttachmentsAndSendRemainsReachableWithLargerTextAndKeyboard() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val folder = File(context.cacheDir, "composer-focus-evidence").apply { mkdirs() }
        val hierarchyProblems = mutableListOf<String>()
        withAppFrameFixture("composer-focus", "composer-focus-evidence") { activity ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.withPostViewports(activity, folder, "composer-focus", scales = listOf(1.3f)) { label, _ ->
                val draft = "A focused draft for $label.\nWriting comes first.\nKeep this exact text when I return."
                ui.onNode(hasText("New post") and hasClickAction()).performClick()
                val editor = hasSetTextAction() and isEnabled()
                ui.waitUntil(10_000) { ui.onNode(editor).isDisplayed() }
                ui.onNode(editor).performTextReplacement(draft)
                val writingNode = ui.onNode(editor).assertIsDisplayed().fetchSemanticsNode()
                val attachmentNode = ui.onNode(hasText("Add images") and hasClickAction())
                    .assertIsDisplayed().fetchSemanticsNode()
                // The IME can move the app header between separate observations. Compare both
                // live coordinates in one UI-thread action instead of mixing two layout frames.
                val (writingBounds, attachmentBounds) = ui.runOnIdle {
                    writingNode.boundsInRoot to attachmentNode.boundsInRoot
                }
                assertTrue("Both writing and attachment targets retain real bounds",
                    writingBounds.width > 0 && writingBounds.height > 0 &&
                        attachmentBounds.width > 0 && attachmentBounds.height > 0)
                captureNativeScreen(File(folder, "$label-writing.png"))
                File(folder, "$label-hierarchy.json").writeText(JsonObject().apply {
                    addProperty("writingTop", writingBounds.top)
                    addProperty("writingBottom", writingBounds.bottom)
                    addProperty("attachmentTop", attachmentBounds.top)
                    addProperty("writingPrecedesAttachments", writingBounds.bottom <= attachmentBounds.top)
                    addProperty("sameUiThreadObservation", true)
                }.toString())
                if (writingBounds.bottom > attachmentBounds.top) hierarchyProblems += label

                fun keyboardVisible(): Boolean {
                    var visible = false
                    activity.onActivity { screen ->
                        visible = ViewCompat.getRootWindowInsets(screen.window.decorView)
                            ?.isVisible(WindowInsetsCompat.Type.ime()) == true
                    }
                    return visible
                }
                ui.onNode(editor).performClick()
                ui.waitUntil(10_000) { keyboardVisible() }
                ui.onNode(editor).assertTextEquals(draft).assertIsDisplayed()
                ui.onNode(hasText("Send") and hasClickAction() and isEnabled()).assertIsDisplayed()
                var sendTarget = requireNotNull(device.findObject(By.text("Send")))
                while (!sendTarget.isClickable) sendTarget = requireNotNull(sendTarget.parent) {
                    "Send must expose a clickable native ancestor"
                }
                val send = sendTarget.visibleBounds
                var keyboardTop = 0
                var statusBottom = 0
                activity.onActivity { screen ->
                    val insets = requireNotNull(ViewCompat.getRootWindowInsets(screen.window.decorView))
                    keyboardTop = device.displayHeight - insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                    statusBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
                }
                val minimum = 48 * context.resources.displayMetrics.density - 1
                assertTrue("Send retains a full48dp target above the real IME", sendTarget.isEnabled &&
                    send.width() >= minimum && send.height() >= minimum && send.left >= 0 &&
                    send.right <= device.displayWidth && send.top >= statusBottom && send.bottom <= keyboardTop)
                captureNativeScreen(File(folder, "$label-ime.png"))
                File(folder, "$label-ime.json").writeText(JsonObject().apply {
                    addProperty("fontScale", context.resources.configuration.fontScale)
                    addProperty("keyboardVisible", keyboardVisible())
                    addProperty("keyboardTop", keyboardTop)
                    addProperty("statusBottom", statusBottom)
                    addProperty("sendBounds", send.toShortString())
                }.toString())
                device.pressBack()
                ui.waitUntil(10_000) { !keyboardVisible() }
                ui.onNodeWithText("Back").performClick()
                ui.waitUntil(10_000) { ui.onNode(hasText("New post") and hasClickAction()).isDisplayed() }
                ui.onNode(hasText("New post") and hasClickAction()).performClick()
                ui.waitUntil(10_000) { ui.onNode(editor).isDisplayed() }
                ui.onNode(editor).assertTextEquals(draft)
                captureNativeScreen(File(folder, "$label-returned-draft.png"))
                ui.onNodeWithText("Save draft").performClick()
                ui.waitUntil(10_000) { ui.onNode(hasText("New post") and hasClickAction()).isDisplayed() }
            }
        }
        assertTrue("Writing must precede optional attachment actions: $hierarchyProblems", hierarchyProblems.isEmpty())
    }
}
