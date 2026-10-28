package nl.davidengelen.redsquare

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.junit4.ComposeTestRule

/** Exercise the same downward pull users make at the top of a feed or thread list. */
internal fun ComposeTestRule.pullToRefresh(tag: String) {
    onNode(hasTestTag(tag)).performTouchInput {
        val x = size.width / 2f
        swipe(
            start = Offset(x, size.height * 0.08f),
            end = Offset(x, size.height * 0.72f),
            durationMillis = 600
        )
    }
}
