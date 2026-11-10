package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry

/** Use the reply action belonging to this post, rather than another author's matching label. */
internal fun postReplyAction(postId: String): SemanticsMatcher =
    hasContentDescription("Reply to post by ", substring = true) and hasClickAction() and
        hasAnyAncestor(hasTestTag("post-card-${postId.take(12)}"))

internal fun ComposeTestRule.revealPostReply(postId: String): SemanticsNodeInteraction {
    val action = postReplyAction(postId)
    val control = onNode(action)
    val minimum = 48 * InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density - 1
    val alreadyVisible = runCatching {
        val bounds = control.fetchSemanticsNode().boundsInRoot
        control.isDisplayed() && bounds.width >= minimum && bounds.height >= minimum
    }.getOrDefault(false)
    if (!alreadyVisible) {
        onNodeWithTag("conversation-post-list").performScrollToNode(action)
        control.performScrollTo()
    }
    return control.assertIsDisplayed().assertIsEnabled()
}

internal fun ComposeTestRule.replyToPost(postId: String) {
    revealPostReply(postId).performClick()
}
