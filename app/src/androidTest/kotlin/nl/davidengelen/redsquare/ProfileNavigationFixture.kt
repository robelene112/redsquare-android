package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule

/** Navigate the visible secondary sheet, leaving the profile history position alone. */
internal fun ComposeTestRule.openProfileActions() {
    val action = hasText("Profile actions") and hasClickAction() and isEnabled()
    waitUntil(15_000) { onNode(action).isDisplayed() }
    onNode(action).performClick()
    waitUntil(15_000) { onNodeWithText("Public identity").isDisplayed() }
}

internal fun ComposeTestRule.closeProfileActions() {
    onNode(hasText("Done") and hasClickAction()).performClick()
    waitUntil(15_000) { onAllNodesWithText("Public identity").fetchSemanticsNodes().isEmpty() }
}

/** Exact full-key inspection stays a real visible assertion, never an offscreen readiness marker. */
internal fun ComposeTestRule.inspectProfileIdentity(author: String, inspect: ComposeTestRule.() -> Unit = {}) {
    openProfileActions()
    onNodeWithText("Public identity").performClick()
    waitUntil(15_000) { onNodeWithText(author).isDisplayed() }
    onNodeWithText(author).performScrollTo().assertIsDisplayed()
    inspect()
    onNode(hasText("Done") and hasClickAction()).performClick()
    waitUntil(15_000) { onNode(hasText("Profile actions") and hasClickAction()).isDisplayed() }
}
