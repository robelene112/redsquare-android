package nl.davidengelen.redsquare

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import org.junit.Rule
import org.junit.Test

/** Real local block/unblock actions do not invent arrivals or move an older reader. */
class ConversationFilteringPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun blockingAndRevealingCachedMessagesDoNotSignalNewArrivals() =
        CachedPrivateConversationFixture(ui).exercise(filtering = true)
}
