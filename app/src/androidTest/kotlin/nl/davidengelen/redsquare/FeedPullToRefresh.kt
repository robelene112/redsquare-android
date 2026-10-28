package nl.davidengelen.redsquare

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.junit4.ComposeTestRule

/** Exercise the same downward pull users make at the top of a feed or thread list. */
internal fun ComposeTestRule.pullToRefresh(tag: String) {
    onNode(hasTestTag(tag)).performTouchInput { swipeDown() }
}
