package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class StoreCryptoPaymentPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun reviewedExternalInstructionsRequireAuthorizationAndReturnToTheOriginalDeliveredOrder() = withVaultDeviceCredential { device ->
        val provider = OwnedStoreCryptoFixture()
        withStorePurchaseFixture("external-instructions", fullPurchase = true, crypto = provider) { scene ->
            ui.openStorePurchaseProduct("2")
            ui.waitUntil(15_000) { ui.onNodeWithText("Total: 5 SAITO").isDisplayed() }
            ui.onNodeWithText("Done").performScrollTo().performClick()
            ui.onNodeWithText("Other payment methods").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onAllNodesWithText("Pay with USDT").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNodeWithText("Pay with USDT").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Estimated payment: 0.005501 USDT").isDisplayed() }
            ui.onNodeWithText("Network: Ethereum").assertIsDisplayed()
            ui.onNodeWithText("Create payment instructions").performScrollTo().assertIsEnabled()
            assertEquals(0, provider.reservations); assertTrue(scene.node.peer.transactions.isEmpty())
            captureNativeScreen(File(scene.evidence, "external-review-before-original-invoice.png"))
            ui.onNodeWithText("Create payment instructions").performClick(); waitForSystemPin(device)
            assertEquals(0, provider.reservations); enterRealSystemPin(device)
            ui.waitUntil(25_000) { ui.onNodeWithText("Payment: 0.005501 USDT").isDisplayed() }
            ui.onNodeWithText("Copy payment address").performScrollTo().performClick()
            ui.onNodeWithText("Address copied.").assertIsDisplayed()
            val clipboard = InstrumentationRegistry.getInstrumentation().targetContext.getSystemService(android.content.ClipboardManager::class.java)
            assertEquals("owned-usdt-payment-address", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
            assertEquals(1, provider.reservations); assertTrue(scene.node.peer.transactions.isEmpty())
            val original = requireNotNull(provider.original).transaction.encoded()
            captureNativeScreen(File(scene.evidence, "bound-native-payment-instructions.png"))
            scene.node.phase = OwnedStorePurchaseFixture.Phase.DELIVERED
            ui.onNodeWithText("Check order").performScrollTo().performClick()
            ui.waitUntil(25_000) { ui.onNode(hasText("Article received") and hasAnyAncestor(isDialog())).isDisplayed() }
            assertEquals(1, provider.reservations); assertTrue(scene.node.peer.transactions.isEmpty())
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val order = StoreOrderStore(context, requireNotNull(provider.original).buyer).read().single()
            assertEquals(original, order.originalTransaction.encoded())
            assertEquals("USDT", order.external?.invoice?.currency?.ticker)
            assertEquals(order.external?.carrier?.buyer, order.fundedPayment?.buyer)
            assertNotEquals(order.external?.carrier?.buyer, SaitoEnvelope.decode(requireNotNull(order.fundedPayment).transaction.envelope()).author)
            assertTrue(order.reservedInputs.isEmpty())
            captureNativeScreen(File(scene.evidence, "external-payment-returned-to-exact-delivery.png"))
        }
    }
    @Test fun anActualCryptoPricedOfferKeepsItsDenominationAndOnlyOffersItsOwnPaymentCurrency() = withVaultDeviceCredential { device ->
        val provider = OwnedStoreCryptoFixture()
        withStorePurchaseFixture("crypto-priced-offer", crypto = provider, web3 = true) { scene ->
            scene.node.funded = false
            ui.openNativeStoreOtherMethods("2")
            ui.waitUntil(15_000) { ui.onNodeWithText("Pay with USDT").isDisplayed() }
            ui.onAllNodesWithText("Pay with BTC").assertCountEquals(0)
            ui.onNodeWithText("Pay with USDT").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Estimated payment: 7.5 USDT").isDisplayed() }
            ui.onNodeWithText("Item total: 7.5 USDT").assertIsDisplayed()
            ui.onNodeWithText("Create payment instructions").performScrollTo().performClick()
            waitForSystemPin(device); enterRealSystemPin(device)
            ui.waitUntil(25_000) { ui.onNodeWithText("Payment: 7.5 USDT").isDisplayed() }
            assertEquals(1, provider.reservations); assertTrue(scene.node.peer.transactions.isEmpty())
            scene.node.phase = OwnedStorePurchaseFixture.Phase.DELIVERED
            ui.onNodeWithText("Check order").performScrollTo().performClick()
            ui.waitUntil(25_000) { ui.onNode(hasText("Article received") and hasAnyAncestor(isDialog())).isDisplayed() }
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val order = StoreOrderStore(context, requireNotNull(provider.original).buyer).read().single()
            assertEquals("USDT", order.external?.carrier?.price?.currency)
            assertEquals(java.math.BigDecimal("7.5"), requireNotNull(order.external?.invoice).amount.stripTrailingZeros())
            assertEquals(java.math.BigInteger("681800000000"), order.fundedPayment?.amount)
            assertEquals(1, provider.reservations); assertTrue(scene.node.peer.transactions.isEmpty())
            captureNativeScreen(File(scene.evidence, "crypto-priced-offer-exact-provider-and-delivery.png"))
        }
    }

}
