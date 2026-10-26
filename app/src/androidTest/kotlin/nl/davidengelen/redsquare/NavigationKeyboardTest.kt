package nl.davidengelen.redsquare

import android.view.KeyEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import java.io.File
import org.junit.Rule
import org.junit.Test

/** Native keyboard traversal and visible names at the actual adaptive navigation targets. */
class NavigationKeyboardTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun largeTextDestinationsKeepKeyboardFocusSelectionAndDiscoverableNames() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = File(instrumentation.targetContext.cacheDir, "navigation-keyboard-evidence").apply { mkdirs() }
        val device = UiDevice.getInstance(instrumentation)
        val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        withAppFrameFixture("navigation-keyboard", "navigation-keyboard-evidence", "[]", profileArchiveResponse = { "[]" }) { activity ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.withPostViewports(activity, folder, "keyboard", scales = listOf(2f)) { label, _ ->
                check(device.pressKeyCode(KeyEvent.KEYCODE_TAB))
                ui.waitUntil(5_000) {
                    var keyboard = false
                    activity.onActivity { keyboard = !it.window.decorView.isInTouchMode }
                    keyboard
                }
                ui.onNode(hasText("Home") and tab).performSemanticsAction(SemanticsActions.RequestFocus) { check(it()) }
                val names = listOf("Home", "Search", "Notifications", "Chat", "Your profile")
                for ((index, name) in names.withIndex()) {
                    if (index > 0) check(device.pressKeyCode(KeyEvent.KEYCODE_TAB))
                    ui.waitUntil(5_000) { ui.onNode(hasText(name) and tab and isFocused()).isDisplayed() }
                    ui.onNode(hasText(name) and tab).assertIsFocused()
                    captureNativeScreen(File(folder, "$label-focus-$index.png"))
                }
                check(device.pressDPadLeft())
                ui.waitUntil(5_000) { ui.onNode(hasText("Chat") and tab and isFocused()).isDisplayed() }
                check(device.pressDPadCenter())
                ui.waitUntil(10_000) { ui.onNode(hasText("Chat") and tab and isSelected()).isDisplayed() }
                ui.onNodeWithText("New chat").assertIsDisplayed()
                captureNativeScreen(File(folder, "$label-chat-keyboard-selected.png"))
                ui.onNode(hasText("Home") and tab).performClick()
                ui.waitUntil(10_000) { ui.onNode(hasText("Home") and tab and isSelected()).isDisplayed() }
                // Each actual target has a visible full caption on long press, without a test alias.
                for ((index, name) in names.withIndex()) {
                    val caption = listOf("Home", "Search", "Activity", "Chat", "Profile")[index]
                    ui.onNode(hasText(name) and tab).performTouchInput { longClick() }
                    ui.waitUntil(5_000) { ui.onNode(hasText(caption) and !hasClickAction()).isDisplayed() }
                    for (other in listOf("Home", "Search", "Activity", "Chat", "Profile").filter { it != caption }) {
                        ui.onNode(hasText(other) and !hasClickAction()).assertDoesNotExist()
                    }
                    captureNativeScreen(File(folder, "$label-name-$index.png"))
                    // The nonmodal caption expires without changing navigation or stealing focus.
                    ui.waitUntil(5_000) { !ui.onNode(hasText(caption) and !hasClickAction()).isDisplayed() }
                }
                for ((index, name) in names.drop(1).withIndex()) {
                    ui.onNode(hasText(name) and tab).performClick()
                    ui.waitUntil(15_000) {
                        if (ui.onNodeWithText("Not now").isDisplayed()) ui.onNodeWithText("Not now").performClick()
                        ui.onNode(hasText(name) and tab and isSelected()).isDisplayed() &&
                            !ui.onNodeWithText("Not now").isDisplayed() &&
                            device.hasObject(By.text(name).selected(true))
                    }
                    captureNativeScreen(File(folder, "$label-selected-${index + 1}.png"))
                }
                ui.onNode(hasText("Home") and tab).performClick()
                ui.waitUntil(10_000) { ui.onNode(hasText("Home") and tab and isSelected()).isDisplayed() }
            }
        }
    }
}
