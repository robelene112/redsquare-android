package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File

/** Actual user navigation around conversation controls, without changing fixture assertions. */
internal fun ComposeTestRule.withMessageAction(text: String, action: SemanticsNodeInteraction.() -> Unit) {
    withMessageAction(text, false, action)
}

internal fun ComposeTestRule.withLastMessageAction(text: String, action: SemanticsNodeInteraction.() -> Unit) {
    withMessageAction(text, true, action)
}

private fun ComposeTestRule.withMessageAction(text: String, last: Boolean, action: SemanticsNodeInteraction.() -> Unit) {
    var primary: Throwable? = null
    try { openMessageActions(last); onNodeWithText(text).action() }
    catch (failure: Throwable) { primary = failure; throw failure }
    finally { cleanupAfterTest(primary, { closeMessageActions() }) }
}

internal fun ComposeTestRule.openMessageActions(last: Boolean = false) {
    if (onAllNodes(isPopup()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()) return
    val buttons = onAllNodesWithContentDescription("Message actions")
    waitUntil(15_000) { buttons.fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
    val visible = buttons.fetchSemanticsNodes().indices.filter { buttons[it].isDisplayed() }
    val button = buttons[(if (last) visible.lastOrNull() else visible.firstOrNull()) ?: 0]
    button.performScrollTo().assertIsDisplayed().performClick()
    waitUntil(5_000) { onNode(isPopup()).isDisplayed() }
}

internal fun ComposeTestRule.closeMessageActions() {
    waitForIdle()
    if (onAllNodes(isPopup()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()) {
        val reported = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        println("Back injection reported $reported; observing message-menu dismissal")
        waitUntil(5_000) { onAllNodes(isPopup()).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty() }
    }
}

internal fun ComposeTestRule.withConversationDetails(action: () -> Unit) {
    val opener = hasContentDescription("Conversation details") and hasClickAction()
    val title = hasText("Conversation details") and hasAnyAncestor(isDialog())
    val alreadyOpen = onNode(title).isDisplayed()
    val opened = alreadyOpen || onNode(opener).isDisplayed()
    if (opened && !alreadyOpen) onNode(opener).performClick()
    var primary: Throwable? = null
    try { action() }
    catch (failure: Throwable) { primary = failure; throw failure }
    finally {
        cleanupAfterTest(primary, { if (opened && !alreadyOpen && onNode(title).isDisplayed())
            onNode(hasText("Done") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        })
    }
}

internal fun ComposeTestRule.withConversationControl(text: String, action: SemanticsNodeInteraction.() -> Unit) =
    withConversationDetails {
        val control = onNodeWithText(text)
        if (!control.isDisplayed() && control.fetchSemanticsNodesOrEmpty().isNotEmpty()) control.performScrollTo()
        control.action()
    }

private fun SemanticsNodeInteraction.fetchSemanticsNodesOrEmpty() =
    runCatching { listOf(fetchSemanticsNode()) }.getOrDefault(emptyList())

/** Full details and contextual actions, then a real system IME with readable history. */
internal fun ComposeTestRule.inspectConversationControls(activity: ActivityScenario<MainActivity>, folder: File,
    label: String, identity: String, group: Boolean) {
    onNodeWithText("Like").assertDoesNotExist()
    onNodeWithText("Reply").assertDoesNotExist()
    onNodeWithText("Copy").assertDoesNotExist()
    withConversationDetails {
        onNodeWithText(if (group) "Group messages are unencrypted." else "Private messages are encrypted.")
            .performScrollTo().assertIsDisplayed()
        onNodeWithText(identity).performScrollTo().assertIsDisplayed()
        onNodeWithText("Refresh").performScrollTo().assertIsDisplayed()
        if (group) onNodeWithText("Members").performScrollTo().assertIsDisplayed()
        captureNativeScreen(File(folder, "$label-full-details.png"))
    }
    withMessageAction("Copy") {
        assertIsDisplayed().assertIsEnabled()
        captureNativeScreen(File(folder, "$label-message-actions.png"))
    }
    fun keyboardVisible(): Boolean {
        var visible = false
        activity.onActivity { screen -> visible = ViewCompat.getRootWindowInsets(screen.window.decorView)
            ?.isVisible(WindowInsetsCompat.Type.ime()) == true }
        return visible
    }
    onNodeWithText("Message").performClick()
    waitUntil(10_000) { keyboardVisible() }
    val scale = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.fontScale
    onNodeWithContentDescription(if (group) "Group messages" else "Private messages")
        .assertHeightIsAtLeast(if (scale <= 1.3f) 96.dp else 48.dp)
    onNodeWithText(if (group) "Unencrypted group" else "Encrypted").assertIsDisplayed()
    onNodeWithText("Send").assertIsDisplayed()
    captureNativeScreen(File(folder, "$label-history-with-ime.png"))
    val reported = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
    println("Back injection reported $reported; observing system keyboard dismissal")
    waitUntil(10_000) { !keyboardVisible() }
}
