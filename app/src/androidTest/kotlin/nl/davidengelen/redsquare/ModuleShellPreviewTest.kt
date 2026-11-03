package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test

/** The shared shell uses the real wallet and existing account destinations. */
class ModuleShellPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun modulePickerAndAccountMenuKeepTheRealFeedAndWallet() =
        withAppFrameFixture("module-account", "module-shell-evidence") { activity ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
            ui.onNodeWithContentDescription("Switch app: Red Square").assertIsDisplayed().assertHasClickAction().performClick()
            ui.onNode(hasText("Red Square") and isSelected()).assertIsDisplayed()
            ui.onNodeWithText("Stack").assertIsDisplayed()
            androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
            ui.onNodeWithContentDescription("Account").assertIsDisplayed().performClick()
            ui.onNode(hasText("Wallet") and hasClickAction()).assertIsDisplayed().performClick()
            ui.onNodeWithText("Export wallet").performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("Done").performClick()
            ui.onNodeWithContentDescription("Account").performClick()
            ui.onNode(hasText("Settings") and hasClickAction()).assertIsDisplayed().performClick()
            ui.onNodeWithText("Appearance").assertExists()
            ui.onNodeWithText("Done").performClick()
            activity.recreate()
            ui.waitUntil(15_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
            ui.onNodeWithContentDescription("Switch app: Red Square").assertIsDisplayed()
            ui.onNode(hasText("Home") and isSelected()).assertIsDisplayed()
            captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "module-shell-evidence/account-return.png"))
        }
}
