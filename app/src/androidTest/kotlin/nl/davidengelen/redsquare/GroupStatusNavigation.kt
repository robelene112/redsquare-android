package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule

/** Status text/actions may load asynchronously below the finite status viewport. */
internal fun ComposeTestRule.waitForGroupStatus(text: String, enabled: Boolean = false) {
    val target = if (enabled) hasText(text) and isEnabled() else hasText(text)
    waitUntil(15_000) {
        try {
            if (!onNode(target).isDisplayed()) {
                val details = onNodeWithContentDescription("Conversation information and controls")
                val viewport = if (details.isDisplayed()) details
                    else onNodeWithContentDescription("Group status and actions")
                viewport.performScrollToNode(hasText(text))
            }
        } catch (_: AssertionError) {
            // A not-yet-published status is retried inside the original readiness deadline.
            return@waitUntil false
        }
        onNode(target).isDisplayed()
    }
}
