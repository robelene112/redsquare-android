package nl.davidengelen.redsquare

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import org.junit.Rule
import org.junit.Test

/** Additional normal and large text cells run within their own 180 second class budget. */
class GroupConversationPolishPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun groupDetailsAndMessageControlsRemainReachableWithNormalAndLargeText() =
        GroupConversationFixture(ui).exercise(listOf(1f, 2f), "group-conversation-polish")
}
