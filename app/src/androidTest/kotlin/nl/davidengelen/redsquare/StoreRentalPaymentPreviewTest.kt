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
class StoreRentalPaymentPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun bnbUsesTheDiscoveredBnbNetworkAndReceivesTheExactAsset() = currency("BNB")
    @Test fun bitcoinUsesTheDiscoveredBitcoinNetworkAndReceivesTheExactAsset() = currency("BTC")
    @Test fun etherUsesTheDiscoveredEthereumNetworkAndReceivesTheExactAsset() = currency("ETH")
    @Test fun usdcUsesTheDiscoveredEthereumNetworkAndReceivesTheExactAsset() = currency("USDC")
    @Test fun usdtUsesTheDiscoveredEthereumNetworkAndReceivesTheExactAsset() = currency("USDT")
    @Test fun solanaUsesTheDiscoveredSolanaNetworkAndReceivesTheExactAsset() = currency("SOL")
    @Test fun tronUsesTheDiscoveredTronNetworkAndReceivesTheExactAsset() = currency("TRX")

    @Test fun aUsdtPricedRentalAlsoReceivesItsExactVaultEntitlement() = currency("USDT", web3 = true)

    private fun currency(ticker: String, web3: Boolean = false) = withVaultDeviceCredential { device ->
        val provider = OwnedStoreCryptoFixture()
        withStorePurchaseFixture("currency-${ticker.lowercase()}", rental = true, web3 = web3, crypto = provider) { scene ->
            scene.node.funded = false
            ui.openNativeStoreOtherMethods("1", illustrated = true)
            ui.waitUntil(15_000) { ui.onAllNodesWithText("Pay with $ticker").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            val advertised = provider.methods.getAsJsonArray("ac").map { it.asJsonObject }
            assertEquals(7, advertised.size)
            for (item in advertised) ui.onNodeWithText("Pay with ${item.get("ticker").asString}").assertExists()
            ui.onNodeWithText("Pay with $ticker").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onAllNodesWithText("Estimated payment: ${if (web3) "3.75" else "0.002751"} $ticker").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNodeWithText("Estimated payment: ${if (web3) "3.75" else "0.002751"} $ticker").performScrollTo().assertIsDisplayed()
            val metadata = advertised.single { it.get("ticker").asString == ticker }
            val network = provider.chains.getAsJsonObject(metadata.get("chain_id").asString).getAsJsonObject("data").get("name").asString
            ui.onNodeWithText("Network: $network").performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("Create payment instructions").performScrollTo().performClick()
            waitForSystemPin(device); assertEquals(0, provider.reservations); enterRealSystemPin(device)
            ui.waitUntil(25_000) { ui.onAllNodesWithText("Payment: ${if (web3) "3.75" else "0.002751"} $ticker").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNodeWithText("Payment: ${if (web3) "3.75" else "0.002751"} $ticker").performScrollTo().assertIsDisplayed()
            assertEquals(1, provider.reservations); assertTrue(scene.node.peer.transactions.isEmpty())
            captureNativeScreen(File(scene.evidence, "method-${ticker.lowercase()}-native-instructions.png"))
            scene.node.phase = OwnedStorePurchaseFixture.Phase.DELIVERED
            ui.onNodeWithText("Check order").performScrollTo().performClick()
            ui.waitUntil(25_000) { ui.onAllNodes(hasText("Article received") and hasAnyAncestor(isDialog())).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNode(hasText("Article received") and hasAnyAncestor(isDialog())).performScrollTo().assertIsDisplayed()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val owner = requireNotNull(provider.original).buyer
            val order = StoreOrderStore(context, owner).read().single()
            assertEquals(ticker, order.external?.currency?.ticker)
            assertEquals(network, order.external?.network)
            assertEquals(owner, order.receipt?.asset?.owner)
            assertEquals(java.math.BigInteger.ONE, order.receipt?.asset?.quantity)
            assertEquals(1, provider.reservations); assertTrue(scene.node.peer.transactions.isEmpty())
            assertTrue(order.reservedInputs.isEmpty())
            assertEquals("store-nft-rental", order.receipt?.asset?.type)
            val right = SaitoStoreRental.entitlement(requireNotNull(order.fundedPayment), order.product, requireNotNull(order.receipt).encoded)
            assertEquals(owner, requireNotNull(right.grant).renter)
            ui.waitUntil(5_000) { ui.onAllNodesWithText("Open in Vault").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNodeWithText("Open in Vault").performScrollTo().assertIsEnabled().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithContentDescription("Switch app: Vault").isDisplayed() && ui.onNodeWithText("Upload notes.txt").isDisplayed() }
            captureNativeScreen(File(scene.evidence, "method-${ticker.lowercase()}-exact-delivery.png"))
        }
    }
}
