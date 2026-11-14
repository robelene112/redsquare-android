package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class StorePurchasePreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun actualReviewShowsTheExactQuantityTotalFeesAndCounterpartyBeforeAnySigningOrPayment() = withStorePurchaseFixture("review") { scene ->
        ui.openStorePurchaseProduct()
        ui.waitUntil(15_000) { ui.onNodeWithText("Total: 2.5 SAITO").isDisplayed() }
        ui.onNodeWithText("Quantity: 1").assertIsDisplayed()
        ui.onNodeWithText("Store fee: 0 SAITO").assertIsDisplayed()
        ui.onNode(hasText("Network fee: 0 SAITO") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        ui.onNodeWithText("Confirm and pay").performScrollTo().assertIsEnabled()
        assertTrue(scene.node.peer.transactions.isEmpty())
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(StoreOrderStore(context, scene.node.reference.get("buyer").asString).read().isEmpty())
        captureNativeScreen(File(scene.evidence, "review-exact-quantity-and-total.png"))
        ui.onNodeWithText(scene.node.reference.get("store").asString).performScrollTo().assertIsDisplayed()
        captureNativeScreen(File(scene.evidence, "review-original-offer-and-store-counterparty.png"))
        ui.onNodeWithText("Done").performScrollTo().performClick()
    }
    @Test fun insufficientSpendableFundsLeaveNoSignedOrderOrPayment() = withStorePurchaseFixture("insufficient") { scene ->
        scene.node.funded = false
        ui.openStorePurchaseProduct()
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val message = context.getString(R.string.store_purchase_insufficient)
        ui.waitUntil(15_000) { ui.onNodeWithText(message).isDisplayed() }
        assertTrue(scene.node.peer.transactions.isEmpty())
        assertTrue(StoreOrderStore(context, scene.node.reference.get("buyer").asString).read().isEmpty())
        captureNativeScreen(File(scene.evidence, "insufficient-funds-no-payment.png"))
    }
    @Test fun buyingTheCompleteListedQuantityReceivesTheWholeExactAsset() = withVaultDeviceCredential { device ->
        withStorePurchaseFixture("whole-quantity", 1, fullPurchase = true) { scene ->
            scene.node.phase = OwnedStorePurchaseFixture.Phase.DELIVERED
            ui.openStorePurchaseProduct("2")
            ui.waitUntil(15_000) { ui.onNodeWithText("Total: 5 SAITO").isDisplayed() }
            ui.onNodeWithText("Quantity: 2").assertIsDisplayed()
            ui.onNodeWithText("Confirm and pay").performScrollTo().performClick(); waitForSystemPin(device); enterRealSystemPin(device)
            ui.waitUntil(25_000) { ui.onNode(hasText("Article received") and hasAnyAncestor(isDialog())).isDisplayed() }
            assertEquals(java.math.BigInteger.TWO, requireNotNull(scene.node.candidate()).quantity)
            assertEquals(1, scene.node.peer.transactions.size)
            captureNativeScreen(File(scene.evidence, "whole-listed-quantity-received.png"))
        }
    }
    @Test fun lostAnswersAndDelayedExactDeliveryRecoverTheSameOrderWithoutAnotherPayment() = withVaultDeviceCredential { device ->
        withStorePurchaseFixture("lost-delivery", 1) { scene ->
            scene.node.peer.disconnectAfterTransaction = true
            ui.openStorePurchaseProduct()
            ui.waitUntil(15_000) { ui.onAllNodesWithText("Confirm and pay").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNodeWithText("Confirm and pay").performScrollTo().performClick(); waitForSystemPin(device)
            assertTrue(scene.node.peer.transactions.isEmpty()); enterRealSystemPin(device)
            ui.waitUntil(25_000) { scene.node.peer.transactions.size == 1 && ui.onNode(hasText("Retry original payment") and isEnabled()).isDisplayed() }
            val original = requireNotNull(scene.node.candidate()).transaction.encoded()
            captureNativeScreen(File(scene.evidence, "submitted-original-payment-awaits-delivery.png"))
            scene.activity.recreate()
            ui.waitUntil(15_000) { ui.onNodeWithContentDescription("Switch app: Red Square").isDisplayed() }
            ui.onNodeWithContentDescription("Switch app: Red Square").performClick()
            ui.onNodeWithText("Store").performScrollTo().performClick()
            ui.onNodeWithText("Orders").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Field notes access key").isDisplayed() }
            ui.onNodeWithText("Field notes access key").performClick()
            ui.waitUntil(5_000) { ui.onNodeWithText("Check order").isDisplayed() }
            scene.node.phase = OwnedStorePurchaseFixture.Phase.PAID
            ui.onNodeWithText("Check order").performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("Payment observed. Waiting for delivery.") and hasAnyAncestor(isDialog())).isDisplayed() }
            assertEquals(1, scene.node.peer.transactions.size)
            captureNativeScreen(File(scene.evidence, "payment-confirmed-delivery-still-unknown.png"))
            scene.node.phase = OwnedStorePurchaseFixture.Phase.SETTLED_WITHOUT_ASSET
            ui.onNodeWithText("Check order").performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("Store settled. Checking current ownership.") and hasAnyAncestor(isDialog())).isDisplayed() }
            ui.onAllNodesWithText("Article received").assertCountEquals(0)
            scene.node.phase = OwnedStorePurchaseFixture.Phase.DELIVERED
            ui.onNodeWithText("Check order").performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("Article received") and hasAnyAncestor(isDialog())).isDisplayed() }
            assertEquals(1, scene.node.peer.transactions.size); assertEquals(original, requireNotNull(scene.node.candidate()).transaction.encoded())
            captureNativeScreen(File(scene.evidence, "exact-partial-purchase-delivered.png"))
        }
    }
}
