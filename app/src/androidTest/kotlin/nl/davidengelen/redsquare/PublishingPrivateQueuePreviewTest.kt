package nl.davidengelen.redsquare

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import org.junit.Rule
import org.junit.Test

class PublishingPrivateQueuePreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun unacceptedBackgroundPrivateMessagesKeepTheirOriginalAndCanBeCancelled() =
        PublishingQueueFixture(ui, PublishingQueueFixture.Kind.PRIVATE).exercise(cancelQueued = true)
    @Test fun explicitAgreementResumesTheQueuedPrivateMessageWithoutSigningAnotherAttempt() =
        PublishingQueueFixture(ui, PublishingQueueFixture.Kind.PRIVATE).exercise(cancelQueued = false)
}
