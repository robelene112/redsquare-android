package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule

/** Use the reply action belonging to this post, rather than another author's matching label. */
internal fun postReplyAction(postId: String): SemanticsMatcher =
    hasContentDescription("Reply to post by ", substring = true) and hasClickAction() and
        hasAnyAncestor(hasTestTag("post-card-${postId.take(12)}"))

internal fun ComposeTestRule.revealPostReply(postId: String): SemanticsNodeInteraction {
    val action = postReplyAction(postId)
    onNodeWithTag("conversation-post-list").performScrollToNode(action)
    return onNode(action).performScrollTo().assertIsDisplayed().assertIsEnabled()
}

internal fun ComposeTestRule.replyToPost(postId: String) {
    revealPostReply(postId).performClick()
}
