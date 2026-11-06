package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule

/** Follow both expanded profile actions and compact saved-choice rows through real confirmation. */
internal fun ComposeTestRule.confirmAccountControl(label: String, action: String, detail: String,
    active: SemanticsMatcher) {
    val entry = (hasText(label) or hasText(action)) and hasClickAction() and isEnabled()
    waitUntil(15_000) {
        onAllNodes(entry).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }
    onNode(entry).performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
    // The explanation is also inline in Profile actions. Only the actual
    // confirmation owns Cancel; never activate the action behind that window.
    val dialog = isDialog() and hasAnyDescendant(hasText(label)) and
        hasAnyDescendant(hasText("Cancel") and hasClickAction())
    val confirmation = hasText(action) and hasClickAction() and hasAnyAncestor(dialog)
    waitUntil(15_000) { onNode(confirmation).isDisplayed() }
    onNode(hasText(detail) and hasAnyAncestor(dialog)).assertIsDisplayed()
    onNode(confirmation).assertIsEnabled().performClick()
    waitUntil(15_000) {
        onAllNodes(active).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }
}
