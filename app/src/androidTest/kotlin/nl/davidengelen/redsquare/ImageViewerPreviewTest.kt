package nl.davidengelen.redsquare

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.gson.JsonParser
import java.io.File
import org.junit.Rule
import org.junit.Test

/** Native viewer actions and physical keyboard focus, using independent signed image fixtures. */
class ImageViewerPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun keyboardZoomNavigationAndCloseReturnToTheOriginalAttachment() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val folder = File(instrumentation.targetContext.cacheDir, "image-viewer-evidence").apply { mkdirs() }
        val fixture = JsonParser.parseReader(instrumentation.context.assets.open("native-images-reference.json").reader()).asJsonObject
        withAppFrameFixture("viewer-keyboard", "image-viewer-evidence", "[${fixture.getAsJsonObject("ordinary")}]") { activity ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("Refresh") and isEnabled()).isDisplayed() }
            val attachment = hasContentDescription("Image 1 of 2") and hasClickAction()
            ui.onNode(hasScrollToIndexAction()).performScrollToNode(attachment)
            check(device.pressKeyCode(KeyEvent.KEYCODE_TAB))
            ui.waitUntil(5_000) {
                var keyboard = false
                activity.onActivity { keyboard = !it.window.decorView.isInTouchMode }
                keyboard
            }
            ui.onNode(attachment).performSemanticsAction(SemanticsActions.RequestFocus) { check(it()) }
            check(device.pressDPadCenter())
            ui.waitUntil(10_000) { ui.onNodeWithText("Close image").isDisplayed() }
            ui.onNode(hasText("Previous image") and hasClickAction()).assertIsNotEnabled()
            ui.onNode(hasText("Zoom out") and hasClickAction()).assertIsNotEnabled()
            ui.onNode(hasText("Reset zoom") and hasClickAction()).assertIsNotEnabled()
            val zoom = hasText("Zoom in") and hasClickAction()
            ui.onNode(zoom).performSemanticsAction(SemanticsActions.RequestFocus) { check(it()) }
            check(device.pressDPadCenter())
            ui.waitUntil(5_000) { ui.onNodeWithText("150%").isDisplayed() }
            captureNativeScreen(File(folder, "keyboard-zoom.png"), "150%")
            repeat(7) { ui.onNode(zoom).performClick() }
            ui.onNodeWithText("500%").assertIsDisplayed()
            ui.onNode(zoom).assertIsNotEnabled()
            ui.onNodeWithText("Reset zoom").performClick()
            ui.onNodeWithText("100%").assertIsDisplayed()
            ui.onNodeWithText("Next image").performClick()
            ui.onNodeWithText("Image 2 of 2").assertIsDisplayed()
            ui.onNodeWithText("Next image").assertIsNotEnabled()
            ui.onNodeWithText("100%").assertIsDisplayed()
            ui.onNodeWithText("Previous image").performClick()
            val close = hasText("Close image") and hasClickAction()
            ui.onNode(close).performSemanticsAction(SemanticsActions.RequestFocus) { check(it()) }
            // Cycling actual Tab input stays in the modal window.
            repeat(10) {
                check(device.pressKeyCode(KeyEvent.KEYCODE_TAB))
                ui.onNode(isFocused()).assert(hasAnyAncestor(isDialog()))
            }
            ui.onNode(close).performSemanticsAction(SemanticsActions.RequestFocus) { check(it()) }
            check(device.pressDPadCenter())
            ui.waitUntil(10_000) { ui.onAllNodesWithText("Close image").fetchSemanticsNodes().isEmpty() }
            ui.onNode(attachment).assertIsFocused().assertIsDisplayed()
            captureNativeScreen(File(folder, "keyboard-return.png"), "Image 1 of 2")
        }
    }
}
