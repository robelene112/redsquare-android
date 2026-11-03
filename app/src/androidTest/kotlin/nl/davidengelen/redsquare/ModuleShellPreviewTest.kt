package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
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

    @Test fun moduleSwitchKeepsTheFeedAnchorAndUnsentProfileDraft() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val rows = instrumentation.context.assets.open("native-feed-scroll-reference.json").reader().use {
            com.google.gson.JsonParser.parseReader(it).asJsonObject.get("initialRows").toString()
        }
        withAppFrameFixture("module-retention", "module-shell-evidence", rows) { activity ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Reference post 20").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.onNodeWithTag("feed-post-list").performScrollToNode(hasText("Reference post 05"))
            val before = ui.onNodeWithText("Reference post 05").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
            switchApp("Red Square", "Stack")
            ui.onNodeWithText("Stack design preview").assertIsDisplayed()
            ui.onNodeWithText("A Node in a Browser Tab").assertIsDisplayed().performClick()
            ui.onNodeWithText("Article preview").assertIsDisplayed()
            switchApp("Stack", "Red Square")
            ui.waitUntil(15_000) { ui.onNodeWithText("Reference post 05").isDisplayed() }
            assertEquals("Switching modules retains the actual feed reading anchor", before,
                ui.onNodeWithText("Reference post 05").fetchSemanticsNode().boundsInRoot.top, 1f)
            ui.onNode(hasText("Your profile") and hasClickAction()).performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("Edit profile") and hasClickAction()).isDisplayed() }
            ui.onNode(hasText("Edit profile") and hasClickAction()).performScrollTo().performClick()
            val draft = "My real unsent profile draft survives switching apps"
            ui.waitUntil(10_000) { ui.onNode(hasSetTextAction() and isEnabled()).isDisplayed() }
            ui.onNode(hasSetTextAction()).performTextReplacement(draft)
            ui.onNode(hasSetTextAction()).performClick()
            fun keyboardVisible(): Boolean {
                var visible = false
                activity.onActivity { screen -> visible = androidx.core.view.ViewCompat.getRootWindowInsets(screen.window.decorView)
                    ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true }
                return visible
            }
            ui.waitUntil(10_000) { keyboardVisible() }
            androidx.test.uiautomator.UiDevice.getInstance(instrumentation).pressBack()
            ui.waitUntil(10_000) { !keyboardVisible() }
            ui.onNode(hasSetTextAction()).assertTextEquals(draft)
            switchApp("Red Square", "Stack")
            ui.onNodeWithText("Article preview").assertIsDisplayed()
            ui.onNodeWithText("A Node in a Browser Tab").assertIsDisplayed()
            switchApp("Stack", "Red Square")
            ui.waitUntil(15_000) { ui.onNode(hasSetTextAction() and hasText(draft)).isDisplayed() }
            ui.onNode(hasSetTextAction()).assertTextEquals(draft)
            activity.recreate()
            ui.waitUntil(15_000) { ui.onAllNodes(hasSetTextAction() and hasText(draft)).fetchSemanticsNodes().size == 1 }
            ui.onNode(hasSetTextAction()).performScrollTo().assertIsDisplayed().assertTextEquals(draft)
            captureNativeScreen(File(instrumentation.targetContext.cacheDir,
                "module-shell-evidence/retained-real-profile-draft.png"))
        }
    }

    private fun switchApp(current: String, next: String) {
        ui.onNodeWithContentDescription("Switch app: $current").assertIsDisplayed().performClick()
        ui.onNode(hasText(next) and hasClickAction() and hasAnyAncestor(isPopup()))
            .assertIsDisplayed().assertIsEnabled().performClick()
    }

}
