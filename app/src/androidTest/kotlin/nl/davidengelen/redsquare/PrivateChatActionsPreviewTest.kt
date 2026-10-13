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

    @Test fun explicitPrivateLikeAndUnlikeForwardDistinctOriginalsAndRemainAfterReopening() =
        history.exerciseHistory(HistoryCase.LIKE)

    @Test fun incomingPrivateReactionsKeepOriginalIdentityAcrossDuplicatesAndReopening() =
        history.exerciseHistory(HistoryCase.INCOMING_LIKE)

    @Test fun anIncomingReactionDuringSubmissionKeepsNewerForwardedStatusAndBothLikes() =
        history.exerciseHistory(HistoryCase.CONCURRENT_LIKE)

    @Test fun anUncertainPrivateReactionRetriesItsExactOriginalWithoutTogglingAgain() =
        history.exerciseHistory(HistoryCase.REACTION_RETRY)
    @Test fun switchingBackendsKeepsUncertainAndForwardedReactionsOnTheirOriginalServer() =
        history.exerciseHistory(HistoryCase.REACTION_BACKENDS)
}
