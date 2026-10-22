package nl.davidengelen.redsquare

import android.os.SystemClock
import androidx.compose.ui.test.*
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
