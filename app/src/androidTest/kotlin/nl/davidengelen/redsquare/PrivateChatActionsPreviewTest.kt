package nl.davidengelen.redsquare

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import nl.davidengelen.redsquare.PrivateChatHistoryFixture.HistoryCase
import org.junit.Rule
import org.junit.Test

class PrivateChatActionsPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val history = PrivateChatHistoryFixture(ui)

    @Test fun aNativeReplyRetainsItsQuoteAndTypedDraftUntilExplicitSending() =
        history.exerciseHistory(HistoryCase.NATIVE_REPLY)

    @Test fun anAuthenticatedZeroTimestampReplyStillRequiresTypedBodyBeforeSending() =
        history.exerciseHistory(HistoryCase.NATIVE_REPLY_ZERO)
}
