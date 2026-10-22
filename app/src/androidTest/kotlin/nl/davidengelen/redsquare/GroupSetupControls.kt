package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule

/** Use the actual single-key fallback while retaining each original group's protocol assertions. */
internal fun ComposeTestRule.addGroupMemberByKey(key: String) {
    val add = hasText("Add by public key") and hasClickAction() and isEnabled()
    onNodeWithContentDescription("Group setup").performScrollToNode(add)
    onNode(add).assertIsDisplayed().performClick()
    waitUntil(15_000) { onNodeWithText("Add group member").isDisplayed() }
    onNodeWithText("Public key").performClick().performTextReplacement(key)
    onNode(hasText("Add member") and hasClickAction() and isEnabled()).assertIsDisplayed().performClick()
    waitUntil(15_000) { onAllNodesWithText("Add group member").fetchSemanticsNodes().isEmpty() }
}

/** Cancelled publishing retains a member that can still be removed and added again. */
internal fun ComposeTestRule.assertEditableGroupMember(key: String) {
    val remove = hasContentDescription("Remove member $key") and hasClickAction() and isEnabled()
    onNodeWithContentDescription("Group setup").performScrollToNode(remove)
    onNode(remove).assertIsDisplayed().performClick()
    onNodeWithContentDescription("Remove member $key").assertDoesNotExist()
    addGroupMemberByKey(key)
    onNodeWithContentDescription("Group setup").performScrollToNode(remove)
    onNode(remove).assertIsDisplayed().assertIsEnabled()
}
