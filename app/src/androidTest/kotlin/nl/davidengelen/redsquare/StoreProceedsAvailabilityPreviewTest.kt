package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class StoreProceedsAvailabilityPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun immatureProceedsAndAnUnavailableProviderCannotStartAWithdrawal() {
        val crypto = OwnedStoreCryptoFixture(); val treasury = OwnedStoreWithdrawalFixture().apply { settled = false }
        withStoreSellerFixture("proceeds-maturity", crypto = crypto) { scene, _, _ ->
            treasury.attach(scene); ui.openNativeStoreSelling(); ui.onNodeWithText("Proceeds").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Waiting for settlement: 15.75 USDT").isDisplayed() }
            ui.onNodeWithText("Available to request: 0 USDT").assertIsDisplayed()
            ui.onAllNodesWithText("Withdraw USDT").assertCountEquals(0)
            treasury.settled = true; crypto.available = false
            ui.onNodeWithText("Refresh Store").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Payment methods are unavailable. Saved requests are still available below.").isDisplayed() }
            ui.onNodeWithText("Withdraw USDT").performScrollTo().assertIsNotEnabled()
            assertEquals(0, treasury.quotes); assertTrue(treasury.submissions.isEmpty())
            captureNativeScreen(File(scene.evidence, "matured-proceeds-provider-unavailable.png"))
            crypto.available = true; ui.onNodeWithText("Refresh Store").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("Withdraw USDT") and isEnabled()).isDisplayed() }
            assertTrue(scene.peer.transactions.isEmpty())
        }
    }
    @Test fun anExpiredStoreQuoteCannotReserveOrSubmitANewWithdrawal() {
        val treasury = OwnedStoreWithdrawalFixture().apply { quoteAge = 26 * 60_000 }
        withStoreSellerFixture("expired-withdrawal", crypto = OwnedStoreCryptoFixture()) { scene, _, _ ->
            treasury.attach(scene); ui.openNativeStoreSelling(); ui.onNodeWithText("Proceeds").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Available to request: 6.25 USDT").isDisplayed() }
            ui.onNodeWithText("Withdraw USDT").performScrollTo().performClick()
            ui.enterNativeStoreDestination("0x1111111111111111111111111111111111111111")
            ui.onNodeWithText("Review withdrawal").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Could not prepare this withdrawal. Check the address, available funds and payment service, then try again.").isDisplayed() }
            val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "2").publicKey
            assertTrue(StoreWithdrawalStore(InstrumentationRegistry.getInstrumentation().targetContext, owner).read().isEmpty())
            assertEquals(1, treasury.quotes); assertTrue(treasury.submissions.isEmpty()); assertTrue(scene.peer.transactions.isEmpty())
            captureNativeScreen(File(scene.evidence, "expired-quote-no-reservation-or-submission.png"))
        }
    }
}
