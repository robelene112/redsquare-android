package nl.davidengelen.redsquare

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import org.junit.Rule
import org.junit.Test

class PublishingPostQueuePreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun unacceptedBackgroundPostsKeepTheirOriginalAndCanBeCancelled() =
        PublishingQueueFixture(ui, PublishingQueueFixture.Kind.POST).exercise(cancelQueued = true)
    @Test fun explicitAgreementResumesTheQueuedPostWithoutSigningAnotherAttempt() =
        PublishingQueueFixture(ui, PublishingQueueFixture.Kind.POST).exercise(cancelQueued = false)
}
