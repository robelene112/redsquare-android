package nl.davidengelen.redsquare

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File

/** A disabled file row explains itself without closing the menu or changing the writer's focus. */
internal fun ComposeTestRule.assertFileActionUnavailable(reason: String, draft: String, capture: File) {
    val editor = hasText("Message") and hasSetTextAction()
    val focusedBefore = onNode(editor).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Focused) == true
    onNodeWithContentDescription("Add media").assertIsEnabled().assertIsDisplayed().performClick()
    onNodeWithText("Send a file").assertIsDisplayed().assertIsNotEnabled().performTouchInput { click() }
    onNodeWithText(reason).assertIsDisplayed()
    onNodeWithText("Add photo").assertIsEnabled()
    onNodeWithText("Add GIF").assertIsEnabled()
    captureNativeScreen(capture)
    UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
    waitUntil(5_000) { onAllNodesWithText("Send a file").fetchSemanticsNodes().isEmpty() }
    onNode(editor).assertIsEnabled().assertTextContains(draft)
    org.junit.Assert.assertEquals(focusedBefore, onNode(editor).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Focused) == true)
}
