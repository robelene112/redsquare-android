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

/** Tap the rendered body itself, since a merged media card's center may be its image. */
internal fun ComposeTestRule.clickVisiblePostBody(text: String) {
    waitUntil(20_000) {
        onAllNodes(hasText(text), useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }
    onNode(hasScrollAction(), useUnmergedTree = true).performScrollToNode(hasText(text))
    onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed().assertIsEnabled().performClick()
}

/** Open a named post's secondary menu before invoking its real visible action. */
internal fun ComposeTestRule.clickVisiblePostMenuAction(authorName: String, action: String) {
    val more = hasContentDescription("Post actions for $authorName") and hasClickAction()
    waitUntil(20_000) {
        onAllNodes(more).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }
    onNode(hasScrollAction()).performScrollToNode(more)
    onNode(more).assertIsDisplayed().assertIsEnabled().performClick()
    waitUntil(20_000) { onNode(hasText(action) and isEnabled()).isDisplayed() }
    onNode(hasText(action) and isEnabled()).assertIsDisplayed().performClick()
}

/** Open the account named by a post header, including its contextual avatar action. */
internal fun ComposeTestRule.clickVisiblePostProfile(authorName: String) {
    val profile = hasContentDescription("View profile for $authorName") and hasClickAction()
    waitUntil(20_000) {
        onAllNodes(profile).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }
    onNode(hasScrollAction()).performScrollToNode(profile)
    onNode(profile).assertIsDisplayed().assertIsEnabled().performClick()
}
