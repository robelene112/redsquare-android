package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule

/** Tap the uniquely named post control itself, rather than an offscreen semantic node. */
internal fun ComposeTestRule.clickVisiblePostContent(text: String) {
    waitUntil(20_000) {
        onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }
    onNode(hasScrollAction()).performScrollToNode(hasText(text))
    onNodeWithText(text).assertIsDisplayed().assertIsEnabled().performClick()
}
