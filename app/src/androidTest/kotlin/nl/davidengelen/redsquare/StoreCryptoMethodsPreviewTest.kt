package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Each independently discovered currency uses the native reviewed/PIN/owned-provider/delivery path. */
class StoreCryptoMethodsPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun bnbUsesTheDiscoveredBnbNetworkAndReceivesTheExactAsset() = currency("BNB")
    @Test fun bitcoinUsesTheDiscoveredBitcoinNetworkAndReceivesTheExactAsset() = currency("BTC")
    @Test fun etherUsesTheDiscoveredEthereumNetworkAndReceivesTheExactAsset() = currency("ETH")
    @Test fun usdcUsesTheDiscoveredEthereumNetworkAndReceivesTheExactAsset() = currency("USDC")
    @Test fun usdtUsesTheDiscoveredEthereumNetworkAndReceivesTheExactAsset() = currency("USDT")
    @Test fun solanaUsesTheDiscoveredSolanaNetworkAndReceivesTheExactAsset() = currency("SOL")
    @Test fun tronUsesTheDiscoveredTronNetworkAndReceivesTheExactAsset() = currency("TRX")

    private fun currency(ticker: String) = withVaultDeviceCredential { device ->
        val provider = OwnedStoreCryptoFixture()
        withStorePurchaseFixture("currency-${ticker.lowercase()}", fullPurchase = true, crypto = provider) { scene ->
            scene.node.funded = false
            ui.openNativeStoreOtherMethods("2")
            ui.waitUntil(15_000) { ui.onAllNodesWithText("Pay with $ticker").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            val advertised = provider.methods.getAsJsonArray("ac").map { it.asJsonObject }
            assertEquals(7, advertised.size)
            for (item in advertised) ui.onNodeWithText("Pay with ${item.get("ticker").asString}").assertExists()
            ui.onNodeWithText("Pay with $ticker").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Estimated payment: 0.005501 $ticker").isDisplayed() }
            val metadata = advertised.single { it.get("ticker").asString == ticker }
            val network = provider.chains.getAsJsonObject(metadata.get("chain_id").asString).getAsJsonObject("data").get("name").asString
            ui.onNodeWithText("Network: $network").assertIsDisplayed()
            ui.onNodeWithText("Create payment instructions").performScrollTo().performClick()
            waitForSystemPin(device); assertEquals(0, provider.reservations); enterRealSystemPin(device)
            ui.waitUntil(25_000) { ui.onNodeWithText("Payment: 0.005501 $ticker").isDisplayed() }
            assertEquals(1, provider.reservations); assertTrue(scene.node.peer.transactions.isEmpty())
            captureNativeScreen(File(scene.evidence, "method-${ticker.lowercase()}-native-instructions.png"))
            scene.node.phase = OwnedStorePurchaseFixture.Phase.DELIVERED
            ui.onNodeWithText("Check order").performScrollTo().performClick()
            ui.waitUntil(25_000) { ui.onNode(hasText("Article received") and hasAnyAncestor(isDialog())).isDisplayed() }
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val owner = requireNotNull(provider.original).buyer
            val order = StoreOrderStore(context, owner).read().single()
            assertEquals(ticker, order.external?.currency?.ticker)
            assertEquals(network, order.external?.network)
            assertEquals(owner, order.receipt?.asset?.owner)
            assertEquals(java.math.BigInteger.valueOf(2), order.receipt?.asset?.quantity)
            assertEquals(1, provider.reservations); assertTrue(scene.node.peer.transactions.isEmpty())
            assertTrue(order.reservedInputs.isEmpty())
            captureNativeScreen(File(scene.evidence, "method-${ticker.lowercase()}-exact-delivery.png"))
        }
    }
}

internal fun ComposeTestRule.openNativeStoreOtherMethods(quantity: String = "2") {
    waitUntil(15_000) { onNodeWithText("Later").isDisplayed() }; onNodeWithText("Later").performClick()
    onNodeWithContentDescription("Switch app: Red Square").performClick()
    onNodeWithText("Store").performScrollTo().performClick()
    waitUntil(15_000) { onNodeWithText("Field notes access key").isDisplayed() }
    onNodeWithText("Field notes access key").performClick()
    waitUntil(15_000) { onNodeWithText("Original creator-authenticated field notes.").isDisplayed() }
    if (quantity != "1") {
        onNodeWithText("Quantity").performScrollTo().performTextReplacement(quantity)
        finishNativeStoreQuantityEntry()
    }
    onNodeWithText("Other payment methods").performScrollTo().performClick()
}

/** Complete real keyboard input before scrolling to a control whose position depends on IME insets. */
internal fun ComposeTestRule.finishNativeStoreQuantityEntry() {
    val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
    fun showing(): Boolean? {
        var visible: Boolean? = null
        instrumentation.runOnMainSync {
            val root = android.view.inspector.WindowInspector.getGlobalWindowViews().singleOrNull { it.isShown && it.hasWindowFocus() }
            visible = root?.let { androidx.core.view.ViewCompat.getRootWindowInsets(it) }?.let { insets ->
                insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) ||
                    insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom > 0
            }
        }
        return visible
    }
    waitUntil(15_000) { showing() == true }
    androidx.test.uiautomator.UiDevice.getInstance(instrumentation).pressBack()
    waitUntil(5_000) { showing() == false }
}
