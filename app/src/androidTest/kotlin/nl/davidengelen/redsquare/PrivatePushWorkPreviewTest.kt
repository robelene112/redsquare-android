package nl.davidengelen.redsquare

import org.junit.Test

/** Existing five worker journeys retain their class/method identities and original host deadline. */
class PrivatePushWorkPreviewTest : PrivatePushWorkFixture() {
    @Test fun chatOnlyConsentReceivesAndDecryptsAnOpaqueWakeWithoutAnActivityAndDoesNotReAlertAfterDismissal() = privateJourney()

    @Test fun ordinaryPrivateReceiptDoesNotReAlertWhenItsOpaqueWakeArrivesLater() = privateJourney(Journey.ORDINARY_FIRST)

    @Test fun ordinaryChatRetainsItsMessageWhenAlertBookkeepingIsUnavailableAndItsWakeCanRecover() =
        privateJourney(Journey.RECEIPT_RECOVERY)

    @Test fun aPendingPrivateMessageSuppressedWhileBlockedDoesNotAlertAfterUnblocking() =
        privateJourney(Journey.BLOCKED_PENDING)

    @Test fun aRetainedOfficialWakeChecksCurrentIdentityThenUsesTheChosenPreviewAndOpensItsExactConversation() =
        privateJourney(Journey.SCOPED_PREVIEW)

}
