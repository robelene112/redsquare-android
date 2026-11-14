package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class StoreCryptoAvailabilityPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun theActualProviderCanBeUnavailableAndRecoverWithoutCreatingAnyInvoice() {
        val provider = OwnedStoreCryptoFixture().apply { available = false }
        withStorePurchaseFixture("provider-outage-recovery", crypto = provider) { scene ->
            ui.openNativeStoreOtherMethods()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val message = context.getString(R.string.store_crypto_unavailable)
            ui.waitUntil(15_000) { ui.onNodeWithText(message).isDisplayed() }
            assertEquals(0, provider.reservations); assertTrue(scene.node.peer.transactions.isEmpty())
            assertTrue(StoreOrderStore(context, scene.node.reference.get("buyer").asString).read().isEmpty())
            captureNativeScreen(File(scene.evidence, "actual-provider-unavailable-no-invoice.png"))
            provider.available = true
            ui.onNodeWithText("Other payment methods").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onAllNodesWithText("Pay with USDT").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            assertEquals(0, provider.reservations); assertTrue(scene.node.peer.transactions.isEmpty())
            ui.onNodeWithText("Done").performScrollTo().performClick()
        }
    }
    @Test fun anOldProviderIsDistinguishedFromAnUnsupportedPaymentCurrency() {
        val provider = OwnedStoreCryptoFixture().apply { supported = false }
        withStorePurchaseFixture("provider-invoice-binding-update", crypto = provider) { scene ->
            ui.openNativeStoreOtherMethods()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val message = context.getString(R.string.store_crypto_provider_old)
            ui.waitUntil(15_000) { ui.onNodeWithText(message).isDisplayed() }
            assertEquals(0, provider.reservations); assertTrue(scene.node.peer.transactions.isEmpty())
            assertTrue(StoreOrderStore(context, scene.node.reference.get("buyer").asString).read().isEmpty())
            captureNativeScreen(File(scene.evidence, "older-provider-needs-bound-invoices-no-payment.png"))
        }
    }
    @Test fun conflictingInstructionsAreRejectedAndTheOriginalInvoiceCanBeRecoveredReadOnly() = withVaultDeviceCredential { device ->
        val provider = OwnedStoreCryptoFixture().apply { conflicting = true }
        withStorePurchaseFixture("conflicting-original-instructions", fullPurchase = true, crypto = provider) { scene ->
            ui.openNativeStoreOtherMethods()
            ui.waitUntil(15_000) { ui.onAllNodesWithText("Pay with USDT").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNodeWithText("Pay with USDT").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Estimated payment: 0.005501 USDT").isDisplayed() }
            ui.onNodeWithText("Create payment instructions").performScrollTo().performClick()
            waitForSystemPin(device); enterRealSystemPin(device)
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val rejected = context.getString(R.string.store_crypto_other_order)
            ui.waitUntil(25_000) { ui.onNodeWithText(rejected).isDisplayed() }
            val owner = requireNotNull(provider.original).buyer
            val journal = StoreOrderStore(context, owner)
            val pending = journal.read().single()
            assertNull(pending.external?.invoice); assertTrue(pending.mayHaveSent)
            assertEquals(1, provider.reservations); assertTrue(scene.node.peer.transactions.isEmpty())
            ui.onAllNodesWithText("Copy payment address").assertCountEquals(0)
            captureNativeScreen(File(scene.evidence, "different-original-instructions-not-accepted.png"))
            ui.onNodeWithText("Check order").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Payment: 0.005501 USDT").isDisplayed() }
            assertEquals(pending.id, journal.read().single().id)
            assertEquals(1, provider.reservations); assertTrue(provider.statusReads > 0)
            assertTrue(scene.node.peer.transactions.isEmpty())
            captureNativeScreen(File(scene.evidence, "original-invoice-restored-without-another-reservation.png"))
        }
    }
    @Test fun anOriginalWhichDefinitelyNeverLeftTheAppCanBeDiscardedFromOrders() {
        val provider = OwnedStoreCryptoFixture()
        withStorePurchaseFixture("discard-definitely-unsent-external-order", fullPurchase = true, crypto = provider) { scene ->
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            fun resource(name: String) = instrumentation.context.assets.open(name).reader().use { com.google.gson.JsonParser.parseReader(it).asJsonObject }
            val vector = resource("native-store-crypto-reference.json")
            val carrier = SaitoStoreCarrier.restore(vector.getAsJsonObject("saitoCarrier").get("networkBase64").asString)
            val listing = resource("native-store-listing-reference.json").getAsJsonObject("listing")
            val product = SaitoStoreLineage.readSaved(listing.get("id").asString, listing.get("envelope").asString, emptyList())
            val currency = SaitoStoreCrypto.readMethods(provider.methods.toString()).currencies.single { it.ticker == "USDT" }
            val journal = StoreOrderStore(context, carrier.buyer)
            journal.reserveExternal(SaitoBackend.OWNER, carrier, product, currency, vector.get("store").asString, "Ethereum")
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }; ui.onNodeWithText("Later").performClick()
            ui.onNodeWithContentDescription("Switch app: Red Square").performClick(); ui.onNodeWithText("Store").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("Orders") and hasClickAction()).isDisplayed() }; ui.onNode(hasText("Orders") and hasClickAction()).performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Field notes access key").isDisplayed() }; ui.onNodeWithText("Field notes access key").performClick()
            ui.waitUntil(15_000) { ui.onAllNodesWithText("Discard unsent order").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            assertFalse(journal.read().single().mayHaveSent)
            ui.onNodeWithText("Discard unsent order").performScrollTo().performClick()
            ui.waitUntil(5_000) { journal.read().isEmpty() }
            assertEquals(0, provider.reservations); assertTrue(scene.node.peer.transactions.isEmpty())
            captureNativeScreen(File(scene.evidence, "definitely-unsent-original-discarded-without-provider-action.png"))
        }
    }

}
