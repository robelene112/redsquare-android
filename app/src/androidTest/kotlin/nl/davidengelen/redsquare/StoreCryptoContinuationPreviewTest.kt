package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class StoreCryptoContinuationPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun closingInstructionsStillTracksALaterPaymentAndTheExactDelivery() = withVaultDeviceCredential { device ->
        val provider = OwnedStoreCryptoFixture()
        withStorePurchaseFixture("closed-instructions-late-delivery", fullPurchase = true, crypto = provider) { scene ->
            ui.requestNativeStoreUsdtInvoice(device)
            ui.onNodeWithText("Close payment instructions").performScrollTo().performClick()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            ui.waitUntil(5_000) { ui.onNodeWithText(context.getString(R.string.store_crypto_closed)).isDisplayed() }
            ui.onAllNodesWithText("Copy payment address").assertCountEquals(0)
            assertEquals(1, provider.reservations)
            captureNativeScreen(File(scene.evidence, "closed-instructions-retain-the-original-order.png"))
            scene.node.phase = OwnedStorePurchaseFixture.Phase.DELIVERED
            ui.onNodeWithText("Check order").performScrollTo().performClick()
            ui.waitUntil(25_000) { ui.onNode(hasText("Article received") and hasAnyAncestor(isDialog())).isDisplayed() }
            assertEquals(1, provider.reservations); assertTrue(scene.node.peer.transactions.isEmpty())
            captureNativeScreen(File(scene.evidence, "late-payment-after-closing-still-delivers.png"))
        }
    }
    @Test fun expiredInstructionsCannotOfferAnotherCopyOrCreateAnotherInvoice() = withVaultDeviceCredential { device ->
        val provider = OwnedStoreCryptoFixture().apply { reservationLifetime = 1 }
        withStorePurchaseFixture("expired-external-instructions", fullPurchase = true, crypto = provider) { scene ->
            ui.requestNativeStoreUsdtInvoice(device)
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            ui.waitUntil(5_000) { ui.onNodeWithText(context.getString(R.string.store_crypto_expired)).isDisplayed() }
            ui.onAllNodesWithText("Copy payment address").assertCountEquals(0)
            ui.onAllNodesWithContentDescription("Payment address QR code").assertCountEquals(0)
            ui.onNodeWithText("Check order").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("Check order") and isEnabled()).isDisplayed() }
            assertEquals(1, provider.reservations); assertTrue(scene.node.peer.transactions.isEmpty())
            captureNativeScreen(File(scene.evidence, "expired-instructions-do-not-offer-a-payment-destination.png"))
        }
    }
    @Test fun aRemoteTreasuryCanOmitItsCopyAndTheOriginalIsRecoveredFromActualBlockHistory() = withVaultDeviceCredential { device ->
        val provider = OwnedStoreCryptoFixture().apply { missingTransactionCopy = true }
        withStorePurchaseFixture("treasury-copy-omitted", fullPurchase = true, crypto = provider) { scene ->
            ui.requestNativeStoreUsdtInvoice(device)
            scene.node.phase = OwnedStorePurchaseFixture.Phase.DELIVERED
            ui.onNodeWithText("Check order").performScrollTo().performClick()
            ui.waitUntil(25_000) { ui.onNode(hasText("Article received") and hasAnyAncestor(isDialog())).isDisplayed() }
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val order = StoreOrderStore(context, requireNotNull(provider.original).buyer).read().single()
            assertNotNull(order.fundedPayment)
            assertNotNull(order.external?.discovered)
            assertEquals("", com.google.gson.JsonParser.parseString(order.external?.status).asJsonObject.get("outbound_tx").asString)
            assertEquals(provider.payment?.transaction?.encoded(), order.fundedPayment?.transaction?.encoded())
            assertEquals(1, provider.reservations); assertTrue(scene.node.peer.transactions.isEmpty())
            captureNativeScreen(File(scene.evidence, "original-treasury-payment-discovered-and-delivered.png"))
        }
    }
}

internal fun androidx.compose.ui.test.junit4.ComposeTestRule.requestNativeStoreUsdtInvoice(device: androidx.test.uiautomator.UiDevice) {
    openNativeStoreOtherMethods()
    waitUntil(15_000) { onAllNodesWithText("Pay with USDT").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
    onNodeWithText("Pay with USDT").performScrollTo().performClick()
    waitUntil(15_000) { onNodeWithText("Estimated payment: 0.005501 USDT").isDisplayed() }
    onNodeWithText("Create payment instructions").performScrollTo().performClick()
    waitForSystemPin(device); enterRealSystemPin(device)
    waitUntil(25_000) { onNodeWithText("Payment: 0.005501 USDT").isDisplayed() }
}
