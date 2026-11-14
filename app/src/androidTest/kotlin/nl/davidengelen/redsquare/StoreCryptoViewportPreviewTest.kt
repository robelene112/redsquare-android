package nl.davidengelen.redsquare

import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

private object StoreCryptoViewportMotionScale : MotionDurationScale { override val scaleFactor = 0f }

class StoreCryptoViewportPreviewTest {
    @get:Rule val ui = createEmptyComposeRule(StoreCryptoViewportMotionScale)
    @Test fun externalReviewRetainsTheExactCurrencyNetworkFeesAndFortyEightDpActionsAcrossTwelveNativeCells() = withNativeModuleMotionScale(0) {
        for (width in listOf(320, 412)) {
            val provider = OwnedStoreCryptoFixture()
            withStorePurchaseFixture("crypto-review-viewport-$width", fullPurchase = true, crypto = provider) { scene ->
                ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }; ui.onNodeWithText("Later").performClick()
                ui.withPostViewports(scene.activity, scene.evidence, "crypto-review-$width", listOf(1f, 1.3f, 2f), width) { label, _ ->
                    ui.switchStackModule("Red Square", "Store", scrollTarget = true)
                    ui.waitUntil(15_000) { ui.onNodeWithText("Field notes access key").isDisplayed() }
                    ui.onNodeWithText("Field notes access key").performClick()
                    ui.waitUntil(15_000) { ui.onAllNodesWithText("5 SAITO").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() ||
                        ui.onAllNodesWithText("2.5 SAITO").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    val loading = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(nl.davidengelen.redsquare.R.string.store_loading_product)
                    ui.waitUntil(15_000) { ui.onAllNodesWithText(loading).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty() }
                    ui.onNode(hasScrollAction()).performScrollToNode(hasText("Other payment methods"))
                    ui.onNodeWithText("Quantity").performScrollTo().performTextReplacement("2")
                    ui.finishNativeStoreQuantityEntry()
                    ui.onNodeWithText("Other payment methods").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
                    ui.waitUntil(15_000) { ui.onAllNodesWithText("Pay with USDT").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.onNodeWithText("Pay with USDT").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
                    val estimate = hasText("Estimated payment: 0.005501 USDT") and hasAnyAncestor(isDialog())
                    ui.waitUntil(15_000) { ui.onAllNodes(estimate).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.onNode(estimate).performScrollTo().assertIsDisplayed()
                    ui.onNodeWithText("Network: Ethereum").performScrollTo().assertIsDisplayed()
                    captureNativeScreen(File(scene.evidence, "$label-quoted-currency-and-network.png"))
                    ui.onNodeWithText("The provider conversion is included in the quoted amount. Your sending wallet may charge a separate network fee.").performScrollTo().assertIsDisplayed()
                    ui.onNodeWithText("Create payment instructions").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                    ui.onNodeWithText("Done").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                    captureNativeScreen(File(scene.evidence, "$label-instruction-authority-and-exit.png"))
                    assertEquals(0, provider.reservations); assertTrue(scene.node.peer.transactions.isEmpty())
                    ui.onNodeWithText("Done").performClick()
                    ui.waitUntil(5_000) { ui.onAllNodesWithText("Create payment instructions").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty() }
                    ui.onNodeWithText("Back").performClick()
                    ui.switchStackModule("Store", "Red Square", scrollTarget = true)
                }
            }
        }
    }
    @Test fun retainedInvoiceAmountAddressQrAndCopyActionsStayReachableAcrossTwelveNativeCells() = withNativeModuleMotionScale(0) {
        withVaultDeviceCredential { device ->
            for (width in listOf(320, 412)) {
                val provider = OwnedStoreCryptoFixture()
                withStorePurchaseFixture("crypto-invoice-viewport-$width", fullPurchase = true, crypto = provider) { scene ->
                    ui.requestNativeStoreUsdtInvoice(device)
                    ui.onNodeWithText("Done").performScrollTo().performClick()
                    ui.waitUntil(5_000) { ui.onAllNodesWithText("Payment: 0.005501 USDT").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty() }
                    ui.onNodeWithText("Back").performClick()
                    ui.switchStackModule("Store", "Red Square", scrollTarget = true)
                    ui.withPostViewports(scene.activity, scene.evidence, "crypto-invoice-$width", listOf(1f, 1.3f, 2f), width) { label, _ ->
                        ui.switchStackModule("Red Square", "Store", scrollTarget = true)
                        ui.waitUntil(15_000) { ui.onNode(hasText("Orders") and hasClickAction()).isDisplayed() }
                        ui.onNode(hasText("Orders") and hasClickAction()).performClick()
                        ui.waitUntil(15_000) { ui.onNodeWithText("Field notes access key").isDisplayed() }
                        ui.onNodeWithText("Field notes access key").performClick()
                        val total = hasText("Payment: 0.005501 USDT") and hasAnyAncestor(isDialog())
                        ui.waitUntil(15_000) { ui.onAllNodes(total).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                        ui.onNode(total).performScrollTo().assertIsDisplayed()
                        ui.onNodeWithText("Network: Ethereum").performScrollTo().assertIsDisplayed()
                        captureNativeScreen(File(scene.evidence, "$label-retained-invoice-total.png"))
                        ui.onNodeWithText("Check order").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                        ui.onNodeWithText("Copy payment address").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                        ui.onNodeWithContentDescription("Payment address QR code").performScrollTo().assertIsDisplayed()
                        captureNativeScreen(File(scene.evidence, "$label-destination-qr-and-copy.png"))
                        ui.onNodeWithText("Done").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
                        ui.waitUntil(5_000) { ui.onAllNodes(total).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty() }
                        assertEquals(1, provider.reservations); assertTrue(scene.node.peer.transactions.isEmpty())
                        ui.switchStackModule("Store", "Red Square", scrollTarget = true)
                    }
                }
            }
        }
    }
}
