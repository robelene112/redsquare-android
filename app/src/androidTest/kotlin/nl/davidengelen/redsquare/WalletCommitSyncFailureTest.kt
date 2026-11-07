package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Fault only the OS directory-sync boundary, observing the real app's UI. */
class WalletCommitSyncFailureTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun retryCannotExposeIdentityUntilThePublicationBarrierIsDurable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("nl.davidengelen.redsquare.dev", context.packageName)
        val intent = Intent(context, MainActivity::class.java).putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/").putExtra("feedDisabled", true)
            .putExtra("walletCommitSyncUnavailable", true)
        ActivityScenario.launch<MainActivity>(intent).use { activity ->
            assertRecovery()
            ui.onNodeWithText("Retry").performClick()
            assertRecovery()
            activity.onActivity { it.intent.removeExtra("walletCommitSyncUnavailable") }
            ui.onNodeWithText("Retry").performClick()
            ui.openWallet()
            val address = displayedWalletAddress(ui)
            assertEquals(44, address.length)
            ui.closeWalletOverview()
            activity.recreate()
            ui.openWallet()
            assertEquals(address, displayedWalletAddress(ui))
        }
    }

    private fun assertRecovery() {
        waitForWalletRecovery(ui)
        ui.openWallet()
        ui.onNodeWithContentDescription("Wallet address").assertDoesNotExist()
        ui.onNodeWithText("Wallet unavailable. Close this window and try again.").assertExists()
        ui.closeWalletOverview()
    }
}
