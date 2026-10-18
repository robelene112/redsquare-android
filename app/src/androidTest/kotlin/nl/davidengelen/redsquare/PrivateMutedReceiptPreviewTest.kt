package nl.davidengelen.redsquare

import org.junit.Test

class PrivateMutedReceiptPreviewTest : PrivatePushWorkFixture() {
    @Test fun aPendingPrivateMessageMutedDuringUnavailableBookkeepingDoesNotAlertAfterUnmuting() =
        privateJourney(Journey.MUTED_PENDING)
}
