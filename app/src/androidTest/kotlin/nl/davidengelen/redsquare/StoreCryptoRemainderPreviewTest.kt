package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class StoreCryptoRemainderPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun aSaitoPricedRemainderAfterProviderSettlementCanCreateItsOwnBoundInvoice() = remaining(false)
    @Test fun aCryptoPricedRemainderAfterProviderSettlementCanCreateItsOwnBoundInvoice() = remaining(true)
    private fun remaining(web3: Boolean) = withVaultDeviceCredential { device ->
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val reference = instrumentation.context.assets.open(if (web3) "native-store-crypto-partial-reference.json" else "native-store-provider-saito-partial-reference.json")
            .reader().use { JsonParser.parseReader(it).asJsonObject }
        val provider = OwnedStoreCryptoFixture()
        withStorePurchaseFixture("public-remainder-$web3", fullPurchase = true, crypto = provider, web3 = web3, remainder = reference) { scene ->
            scene.node.funded = false
            val owner = scene.node.reference.get("buyer").asString
            assertTrue(StoreOrderStore(context, owner).read().isEmpty())
            ui.openNativeStoreOtherMethods("1")
            ui.waitUntil(15_000) { ui.onAllNodesWithText("Pay with USDT").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNodeWithText("Pay with USDT").performScrollTo().performClick()
            val amount = reference.getAsJsonObject("invoice").get("expected_deposit").asString.toBigDecimal().stripTrailingZeros().toPlainString()
            ui.waitUntil(15_000) { ui.onNodeWithText("Estimated payment: $amount USDT").isDisplayed() }
            ui.onNodeWithText("Quantity: 1").assertIsDisplayed()
            ui.onNodeWithText("Create payment instructions").performScrollTo().performClick()
            waitForSystemPin(device); assertEquals(0, provider.reservations); enterRealSystemPin(device)
            ui.waitUntil(25_000) { ui.onNodeWithText("Payment: $amount USDT").isDisplayed() }
            val order = StoreOrderStore(context, owner).read().single()
            assertEquals(reference.getAsJsonObject("settlement").getAsJsonObject("fulfillment").get("id").asString, order.product.signature)
            assertEquals(java.math.BigInteger.ONE, order.product.quantity)
            assertEquals(java.math.BigInteger.ONE, order.external?.carrier?.quantity)
            assertTrue(order.product.provenance.size >= 2)
            assertEquals(1, provider.reservations); assertTrue(scene.node.peer.transactions.isEmpty())
            val previousCarrier = reference.getAsJsonObject("carrier").get("id").asString
            assertTrue(scene.node.peer.cryptoRequests.none { it.body.get("request").asString == "buysaito purchase status" &&
                it.body.getAsJsonObject("data").get("request_id").asString == previousCarrier })
            captureNativeScreen(File(scene.evidence, "public-remainder-$web3-new-original-invoice.png"))
        }
    }
}
