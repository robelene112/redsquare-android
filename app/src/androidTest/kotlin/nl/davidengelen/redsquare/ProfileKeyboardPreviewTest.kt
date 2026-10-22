package nl.davidengelen.redsquare

import android.view.KeyEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File
import org.junit.Rule
import org.junit.Test

/** Native keyboard and D-pad input after one explicit initial focus, without publication. */
class ProfileKeyboardPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun profileActionsFollowVisibleKeyboardOrderAndDpadSelectsHistoryTabs() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = File(instrumentation.targetContext.cacheDir, "profile-keyboard-evidence").apply { mkdirs() }
        val device = UiDevice.getInstance(instrumentation)
        val order = mutableListOf<String>()
        withAppFrameFixture("profile-keyboard", "profile-keyboard-evidence", "[]",
            profileArchiveResponse = { "[]" }) { activity ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.onNode(hasText("Your profile") and hasClickAction()).performClick()
            val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
            ui.waitUntil(15_000) {
                ui.onNode(hasText("Posts") and tab).isDisplayed() &&
                    ui.onNode(hasText("Refresh") and hasClickAction() and isEnabled()).isDisplayed()
            }
            val back = hasText("Back") and hasClickAction()
            // Enter keyboard mode through actual input before establishing the declared start.
            // A touch-mode initial-focus rejection must not stand in for physical-keyboard proof.
            check(device.pressKeyCode(KeyEvent.KEYCODE_TAB))
            ui.waitUntil(10_000) {
                var keyboardMode = false
                activity.onActivity { keyboardMode = !it.window.decorView.isInTouchMode }
                keyboardMode
            }
            ui.onNode(back).performSemanticsAction(SemanticsActions.RequestFocus) { check(it()) }
            fun focused(label: String) {
                ui.waitUntil(10_000) { ui.onNode(hasText(label) and isFocused()).isDisplayed() }
                ui.onNode(hasText(label) and isFocused()).assertIsFocused().assertIsDisplayed()
                order += label
                captureNativeScreen(File(folder, "${order.size}-${label.lowercase().replace(' ', '-')}-focused.png"))
            }
            focused("Back")
            for (label in listOf("Refresh", "Profile actions", "Edit profile", "Identity details", "Posts", "Replies", "Likes")) {
                check(device.pressKeyCode(KeyEvent.KEYCODE_TAB))
                focused(label)
            }
            // Physical directional input moves focus independently of selection.
            check(device.pressDPadLeft())
            focused("Replies")
            ui.onNode(hasText("Posts") and tab).assertIsSelected()
            ui.onNode(hasText("Replies") and tab).assertIsNotSelected()
            check(device.pressDPadCenter())
            ui.waitUntil(10_000) { ui.onNode(hasText("Replies") and tab and isSelected()).isDisplayed() }
            check(device.pressDPadRight())
            focused("Likes")
            check(device.pressDPadCenter())
            ui.waitUntil(10_000) { ui.onNode(hasText("Likes") and tab and isSelected()).isDisplayed() }
            ui.onNode(hasText("Replies") and tab).assertIsNotSelected()
            captureNativeScreen(File(folder, "likes-selected-and-focused.png"))
            File(folder, "physical-focus-order.txt").writeText(order.joinToString("\n", postfix = "\n"))
            ui.onNode(back).performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
        }
    }
}
