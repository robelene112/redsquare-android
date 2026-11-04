package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.ComposeTestRule

/** Follow the actual account menu rather than restoring old header shortcuts for tests. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeTestRule.revealAppNavigation() {
    val navigation = SemanticsMatcher("Available app navigation action") { node ->
        node.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.CustomActions)
            ?.any { it.label == "Show app navigation" } == true
    }
    if (onAllNodes(navigation).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty())
        onNode(navigation).performCustomAccessibilityActionWithLabel("Show app navigation")
}

internal fun ComposeTestRule.openAccountMenu() {
    val menu = (hasText("Wallet") or hasText("Settings")) and hasClickAction() and hasAnyAncestor(isPopup())
    if (onAllNodes(menu).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()) return
    revealAppNavigation()
    onNodeWithContentDescription("Account").assertIsDisplayed().assertIsEnabled().performClick()
}

internal fun ComposeTestRule.openWallet() {
    openAccountMenu()
    onNode(hasText("Wallet") and hasClickAction()).assertIsDisplayed().assertIsEnabled().performClick()
}

internal fun ComposeTestRule.openSettings() {
    openAccountMenu()
    onNode(hasText("Settings") and hasClickAction()).assertIsDisplayed().assertIsEnabled().performClick()
}

/** Retain the caller's original first-use/keyboard readiness deadline. */
internal fun ComposeTestRule.waitForAccountMenu(timeoutMillis: Long) {
    waitUntil(timeoutMillis) { onNode(hasContentDescription("Account") and hasClickAction() and isEnabled()).isDisplayed() }
}
