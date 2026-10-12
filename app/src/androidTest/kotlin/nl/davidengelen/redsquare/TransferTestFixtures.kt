package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import nl.davidengelen.redsquare.saito.SaitoWallet
import java.util.regex.Pattern

/** Shared controlled funding and observed-IME handling; journeys retain their own assertions. */
internal fun reviewNativeTransfer(ui: ComposeTestRule, device: UiDevice) {
    val keyboard = By.pkg(Pattern.compile("com\\.(?:google\\.)?android\\.inputmethod\\.latin"))
    ui.waitUntil(5_000) { device.hasObject(keyboard) || ui.onNodeWithText("Review transfer").isDisplayed() }
    if (device.hasObject(keyboard)) {
        device.pressBack() // Only dismiss a positively observed system IME.
        check(device.wait(Until.gone(keyboard), 5_000))
    }
    ui.waitUntil(5_000) { ui.onNodeWithText("Review transfer").isDisplayed() }
    ui.onNodeWithText("Review transfer").performClick()
}


internal fun fundedTransferServer() = MockWebServer().apply {
    dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            if (request.target == "/options") return MockResponse.Builder().body(
                "{\"consensus\":{\"genesis_period\":80640,\"default_social_stake_period\":100,\"block_confirmation_limit\":1}}").build()
            val key = request.target.removePrefix("/balance/")
            if (request.target != "/balance/$key" || !SaitoWallet.isPublicKey(key)) {
                return MockResponse.Builder().code(400).build()
            }
            return MockResponse.Builder().body(
                "${System.currentTimeMillis()}-1234-${"a".repeat(64)}.snap\n" +
                    "$key 1001 7 2 500000000 0\n" +
                    "$key 1002 9 0 250000000 7\n").build()
        }
    }
}
