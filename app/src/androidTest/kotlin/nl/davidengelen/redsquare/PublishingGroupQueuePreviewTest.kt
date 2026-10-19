package nl.davidengelen.redsquare

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import org.junit.Rule
import org.junit.Test

class PublishingGroupQueuePreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun unacceptedBackgroundGroupMessagesKeepTheirOriginalAndCanBeCancelled() =
        PublishingQueueFixture(ui, PublishingQueueFixture.Kind.GROUP).exercise(cancelQueued = true)
    @Test fun requiredTermsKeepGroupQueueCancellationReachableWithLargerTextAndOfflineWarnings() =
        PublishingQueueFixture(ui, PublishingQueueFixture.Kind.GROUP).exercise(cancelQueued = true, largeFont = true)
    @Test fun explicitAgreementResumesTheQueuedGroupMessageWithoutSigningAnotherAttempt() =
        PublishingQueueFixture(ui, PublishingQueueFixture.Kind.GROUP).exercise(cancelQueued = false)
}
