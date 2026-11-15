package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule

/** Exercise the shared guarded navigation after a recreated writer finishes its selection. */
internal fun ComposeTestRule.returnFromStackAfterPreparedMedia() {
    fun switch(current: String, next: String) {
        revealAppNavigation()
        onNodeWithContentDescription("Switch app: $current").assertIsDisplayed().performClick()
        onNode(hasText(next) and hasClickAction() and hasAnyAncestor(isPopup() or isDialog()))
            .assertIsDisplayed().assertIsEnabled().performClick()
        waitUntil(5_000) { onNodeWithContentDescription("Switch app: $next").isDisplayed() }
    }
    switch("Red Square", "Stack")
    switch("Stack", "Red Square")
}
