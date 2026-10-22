package nl.davidengelen.redsquare

import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.ComposeTestRule

private class ProfileNavigationDeadline(timeoutMillis: Long) {
    private val end = SystemClock.elapsedRealtime() + timeoutMillis
    fun remaining(): Long = (end - SystemClock.elapsedRealtime()).also {
        check(it > 0) { "Profile navigation exceeded its existing observation deadline" }
    }
}

/** Navigate the visible secondary sheet, leaving the profile history position alone. */
internal fun ComposeTestRule.openProfileActions(timeoutMillis: Long = 15_000) {
    val deadline = ProfileNavigationDeadline(timeoutMillis)
    val action = hasText("Profile actions") and hasClickAction() and isEnabled()
    waitUntil(deadline.remaining()) { onNode(action).isDisplayed() }
    onNode(action).performClick()
    waitUntil(deadline.remaining()) { onNodeWithText("Public identity").isDisplayed() }
}

internal fun ComposeTestRule.closeProfileActions() {
    val deadline = ProfileNavigationDeadline(15_000)
    onNode(hasText("Done") and hasClickAction()).performClick()
    waitUntil(deadline.remaining()) { onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
}

/** Exact full-key inspection stays a real visible assertion, never an offscreen readiness marker. */
internal fun ComposeTestRule.inspectProfileIdentity(author: String, inspect: ComposeTestRule.() -> Unit = {}) {
    val deadline = ProfileNavigationDeadline(15_000)
    openProfileActions(deadline.remaining())
    onNodeWithText("Public identity").performClick()
    waitUntil(deadline.remaining()) { onNodeWithText(author).isDisplayed() }
    onNodeWithText(author).performScrollTo().assertIsDisplayed()
    inspect()
    onNode(hasText("Done") and hasClickAction()).performClick()
    waitUntil(deadline.remaining()) { onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
}

/** Reveal actual lazy history, sharing the original observation budget with loading and scrolling. */
internal fun ComposeTestRule.revealProfileHistoryItem(text: String, timeoutMillis: Long = 15_000) {
    val deadline = ProfileNavigationDeadline(timeoutMillis)
    waitUntil(deadline.remaining()) {
        onNode(hasText("Refresh") and hasClickAction() and isEnabled()).isDisplayed()
    }
    val history = onNode(hasScrollToIndexAction())
    history.performScrollToNode(hasText(text))
    // Lazy scrolling can place the target behind the pinned history tabs.
    // Reveal its actual hit area before the caller clicks, not just its semantics.
    val historyTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and
        (hasText("Posts") or hasText("Replies") or hasText("Likes"))
    val tabs = onAllNodes(historyTab).fetchSemanticsNodes()
    check(tabs.size == 3) { "Require the three profile history tabs" }
    val tabBottom = tabs.maxOf { it.boundsInRoot.bottom }
    val targetTop = onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top
    if (targetTop < tabBottom) history.performSemanticsAction(SemanticsActions.ScrollBy) { scroll ->
        check(scroll(0f, targetTop - tabBottom))
    }
    waitForIdle()
    onNodeWithText(text).assertIsDisplayed()
    check(onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top >= tabBottom) {
        "Profile history target is covered by the pinned tabs"
    }
    deadline.remaining()
}
