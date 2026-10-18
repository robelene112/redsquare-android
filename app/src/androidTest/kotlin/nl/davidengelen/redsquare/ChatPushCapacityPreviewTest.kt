package nl.davidengelen.redsquare

import org.junit.Test

class ChatPushCapacityPreviewTest : ChatPushConsentFixture() {
    @Test fun oneHundredAndOneGroupsKeepPrivateAndSocialDemandWithVisibleAutomaticCapacityRecovery() = consentJourney(true)
}
