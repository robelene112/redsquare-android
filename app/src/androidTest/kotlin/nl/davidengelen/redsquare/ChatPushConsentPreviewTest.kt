package nl.davidengelen.redsquare

import org.junit.Test

class ChatPushConsentPreviewTest : ChatPushConsentFixture() {
    @Test fun backgroundChatConsentPersistsAndEitherFeatureCanKeepTheSharedSubscription() = consentJourney(false)
}
