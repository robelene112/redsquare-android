package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
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
            captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "module-shell-evidence/module-picker.png"))
            androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
            ui.onNodeWithContentDescription("Account").assertIsDisplayed().performClick()
            ui.onNode(hasText("Wallet") and hasClickAction()).assertIsDisplayed()
            captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "module-shell-evidence/account-sheet.png"))
            ui.onNode(hasText("Wallet") and hasClickAction()).performClick()
            ui.onNodeWithText("Export wallet").performScrollTo().assertIsDisplayed()
            // Account overviews use the actual shared module selector in their own window.
            ui.onNode(hasContentDescription("Switch app: Red Square") and hasAnyAncestor(isDialog()))
                .assertIsDisplayed().performClick()
            ui.onNode(hasText("Stack") and hasClickAction() and hasAnyAncestor(isDialog()))
                .assertIsDisplayed().assertIsEnabled().performClick()
            ui.waitUntil(5_000) { ui.onNodeWithText("Stack design preview").isDisplayed() }
            ui.onNode(hasText("Wallet") and hasAnyAncestor(isDialog()) and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertDoesNotExist()
            switchApp("Stack", "Red Square")
            ui.waitUntil(15_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
            ui.openWallet()
            ui.closeWalletOverview()
            ui.onNodeWithContentDescription("Account").performClick()
            ui.onNode(hasText("Settings") and hasClickAction()).assertIsDisplayed().performClick()
            ui.onNodeWithText("Appearance").assertExists()
            ui.closeSettingsOverview()
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
            ui.onNodeWithText("Good ideas deserve\na little more room.").assertIsDisplayed()
            ui.onNode(hasText("For you") and isSelected()).assertIsDisplayed()
            ui.onNodeWithContentDescription("Write an article").assertIsDisplayed()
            captureNativeScreen(File(instrumentation.targetContext.cacheDir,
                "module-shell-evidence/stack-discover.png"))
            ui.onNodeWithTag("stack-content").performScrollToNode(hasText("A Node in a Browser Tab"))
            ui.onNodeWithText("A Node in a Browser Tab").assertIsDisplayed().performClick()
            ui.onNodeWithText("Article preview").assertIsDisplayed()
            captureNativeScreen(File(instrumentation.targetContext.cacheDir,
                "module-shell-evidence/stack-article.png"))
            ui.onNodeWithTag("stack-content").performScrollToNode(hasText("Maya Chen"))
            ui.onNodeWithText("Maya Chen").assertIsDisplayed().performClick()
            ui.onNodeWithTag("stack-content").performScrollToNode(hasText("The quiet value of an open network"))
            ui.onNodeWithText("The quiet value of an open network").assertIsDisplayed().performClick()
            ui.onNode(hasText("Back") and hasClickAction()).performClick()
            ui.onNode(hasText("Back") and hasClickAction()).performClick()
            ui.onNodeWithText("A Node in a Browser Tab").assertIsDisplayed()
            switchApp("Stack", "Red Square")
            ui.waitUntil(15_000) { ui.onNodeWithText("Reference post 05").isDisplayed() }
            assertEquals("Switching modules retains the actual feed reading anchor", before,
                ui.onNodeWithText("Reference post 05").fetchSemanticsNode().boundsInRoot.top, 1f)
            ui.onNode(hasText("Your profile") and hasClickAction()).performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("Edit profile") and hasClickAction()).isDisplayed() }
            ui.onNode(hasText("Edit profile") and hasClickAction()).performScrollTo().performClick()
            val draft = "My real unsent profile draft survives switching apps"
            val editorDeadline = android.os.SystemClock.elapsedRealtime() + 10_000
            ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().size == 1 }
            ui.onNode(hasSetTextAction()).performScrollTo().assertIsDisplayed()
            check(android.os.SystemClock.elapsedRealtime() <= editorDeadline) { "Editor reachability must meet its original10s budget" }
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
            ui.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(draft)))
            switchApp("Red Square", "Stack")
            ui.onNodeWithText("Article preview").assertIsDisplayed()
            ui.onNodeWithText("A Node in a Browser Tab").assertIsDisplayed()
            switchApp("Stack", "Red Square")
            val returnDeadline = android.os.SystemClock.elapsedRealtime() + 15_000
            ui.waitUntil(15_000) { ui.onAllNodes(hasSetTextAction() and hasText(draft)).fetchSemanticsNodes().size == 1 }
            ui.onNode(hasSetTextAction()).performScrollTo().assertIsDisplayed().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(draft)))
            check(android.os.SystemClock.elapsedRealtime() <= returnDeadline) { "Returning work must meet its original15s budget" }
            activity.recreate()
            ui.waitUntil(15_000) { ui.onAllNodes(hasSetTextAction() and hasText(draft)).fetchSemanticsNodes().size == 1 }
            ui.onNode(hasSetTextAction()).performScrollTo().assertIsDisplayed().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(draft)))
            captureNativeScreen(File(instrumentation.targetContext.cacheDir,
                "module-shell-evidence/retained-real-profile-draft.png"))
        }
    }

    private fun switchApp(current: String, next: String) {
        ui.revealAppNavigation()
        ui.onNodeWithContentDescription("Switch app: $current").assertIsDisplayed().performClick()
        ui.onNode(hasText(next) and hasClickAction() and hasAnyAncestor(isPopup() or isDialog()))
            .assertIsDisplayed().assertIsEnabled().performClick()
        // Guarded navigation saves local work before committing the selected module.
        // Observe the actual destination rather than assuming IO completes with the tap.
        ui.waitUntil(5_000) { ui.onNodeWithContentDescription("Switch app: $next").isDisplayed() }
    }

}
