package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import org.junit.Rule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Test

class StoreCryptoRecoveryPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun anUnavailableProviderDoesNotLookLikeAnUnsupportedAppOrCreateAnOrder() = withStorePurchaseFixture("provider-unavailable") { scene ->
        ui.openStorePurchaseProduct()
        ui.waitUntil(15_000) { ui.onNodeWithText("Confirm and pay").isDisplayed() }
        ui.onNodeWithText("Done").performScrollTo().performClick()
        ui.onNodeWithText("Other payment methods").performScrollTo().performClick()
        ui.waitUntil(15_000) { ui.onNodeWithText("Other payment methods are unavailable on this node. Your order has not been charged.").isDisplayed() }
        assertTrue(scene.node.peer.transactions.isEmpty())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(StoreOrderStore(context, scene.node.reference.get("buyer").asString).read().isEmpty())
        captureNativeScreen(java.io.File(scene.evidence, "provider-unavailable-without-signing-an-order.png"))
    }

    @Test fun providerInstructionsAndSettlementRecoverAsTheSameEncryptedWalletOrder() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        fun reference(name: String) = instrumentation.context.assets.open(name).reader().use { JsonParser.parseReader(it).asJsonObject }
        val source = reference("native-store-crypto-settlement-reference.json")
        val methods = SaitoStoreCrypto.readMethods(reference("native-store-crypto-reference.json").get("methods").toString())
        val currency = methods.currencies.single { it.ticker == "USDT" }
        val carrier = SaitoStoreCarrier.restore(source.getAsJsonObject("carrier").get("networkBase64").asString)
        val listing = reference("native-store-web3-listing-reference.json").getAsJsonObject("listing")
        val product = SaitoStoreLineage.readSaved(listing.get("id").asString, listing.get("envelope").asString, emptyList())
        val state = ChatFixtureState(context, listOf("store-orders.${carrier.buyer}."), carrier.buyer)
        var primary: Throwable? = null
        try {
            state.prepare()
            val journal = StoreOrderStore(context, carrier.buyer)
            val order = journal.reserveExternal(SaitoBackend.OWNER, carrier, product, currency, source.get("store").asString, "Ethereum")
            assertEquals(carrier.transaction.id, order.id)
            assertNull(order.fundedPayment)
            assertTrue(order.reservedInputs.isEmpty())
            journal.markSending(order)
            journal.recordProvider(order.id, source.get("invoice").toString())
            val recovered = StoreOrderStore(context, carrier.buyer).read().single()
            assertEquals(order.id, recovered.id)
            assertEquals("7.5", requireNotNull(recovered.external?.invoice).amount.stripTrailingZeros().toPlainString())
            assertEquals("Ethereum", recovered.external?.network)
            assertTrue(recovered.mayHaveSent)
            assertThrows(IllegalArgumentException::class.java) { journal.discard(order.id) }
            journal.recordProvider(order.id, source.get("providerStatus").toString())
            val settled = StoreOrderStore(context, carrier.buyer).read().single()
            assertEquals(order.id, settled.id)
            assertEquals(source.getAsJsonObject("purchase").get("id").asString, settled.fundedPayment?.transaction?.id)
            assertEquals(carrier.buyer, settled.fundedPayment?.buyer)
            assertTrue(settled.reservedInputs.isEmpty())
            assertEquals(1, journal.read().size)
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally { cleanupAfterTest(primary, { state.restoreFiles() }, { state.restorePreferences() }) }
    }
}
