package nl.davidengelen.redsquare

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import org.junit.Rule
import org.junit.Test

class ChatImagePreparationRecreationTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun aSelectedPrivatePhotoContinuesOnceAcrossRecreationWhileTheProviderIsReading() =
        ChatImageFixture(ui).exercisePhoto(false, ChatPhotoFlow.PREPARATION_RECREATION)
    @Test fun aSelectedGroupPhotoContinuesOnceAcrossRecreationWhileTheProviderIsReading() =
        ChatImageFixture(ui).exercisePhoto(true, ChatPhotoFlow.PREPARATION_RECREATION)
}
