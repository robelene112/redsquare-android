package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class StoreSellerManagementPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private fun reviewDelisting() {
        ui.waitUntil(15_000) { ui.onAllNodesWithText("Field notes access key").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        ui.onNode(hasScrollAction()).performScrollToNode(hasText("Delist item"))
        ui.onNodeWithText("Delist item").performClick()
        ui.waitUntil(15_000) { ui.onNodeWithText("Authorize and delist").isDisplayed() }
    }
    @Test fun anInterruptedDelistingRestoresTheSameSellerSpendAndObservesTheExactReturnedStock() = withVaultDeviceCredential { device ->
        withStoreSellerFixture("delist", expected = 1, prelisted = true) { scene, _, _ ->
            ui.openNativeStoreSelling()
            reviewDelisting()
            ui.onNodeWithText("Items to return: 2").performScrollTo().assertIsDisplayed()
            assertTrue(scene.peer.transactions.isEmpty())
            captureNativeScreen(File(scene.evidence, "exact-existing-store-stock-delist-review.png"))
            scene.peer.disconnectAfterTransaction = true
            ui.onNodeWithText("Authorize and delist").performClick()
            waitForSystemPin(device); assertTrue(scene.peer.transactions.isEmpty()); enterRealSystemPin(device)
            ui.waitUntil(25_000) { ui.onNode(hasText("Retry original delisting") and isEnabled()).isDisplayed() }
            val node = requireNotNull(scene.seller); val original = requireNotNull(node.delist()).transaction.encoded()
            assertEquals(1, scene.peer.transactions.size)
            scene.activity.recreate()
            ui.waitUntil(15_000) { ui.onNode(hasText("New listing") and isEnabled()).isDisplayed() }
            ui.onNode(hasScrollAction()).performScrollToNode(hasText("Delisting submitted. Waiting for this node."))
            ui.onNodeWithText("Delisting submitted. Waiting for this node.").performClick()
            ui.waitUntil(5_000) { ui.onNodeWithText("Check delisting").isDisplayed() }
            assertEquals(original, requireNotNull(node.delist()).transaction.encoded())
            node.confirmDelist.set(true); scene.peer.disconnectAfterTransaction = false
            ui.onNodeWithText("Check delisting").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Delisting observed here. The exact returned stock is in your wallet.").isDisplayed() }
            assertEquals(1, scene.peer.transactions.size)
            ui.onAllNodesWithText("Retry original delisting").assertCountEquals(0)
            captureNativeScreen(File(scene.evidence, "original-delisting-included-and-current-stock-returned.png"))
            ui.onNodeWithText("Done").performClick()
            ui.onNodeWithText("Refresh Store").performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("New listing") and isEnabled()).isDisplayed() }
            ui.onAllNodesWithText("Delist item").assertCountEquals(0)
        }
    }
    @Test fun remainingStockAfterAPartialSaleUsesItsAuthenticatedLineageThroughRecreation() = withVaultDeviceCredential { device ->
        withStoreSellerFixture("remaining", expected = 1, prelisted = true, remaining = true) { scene, _, _ ->
            ui.openNativeStoreSelling()
            reviewDelisting()
            ui.onNodeWithText("Items to return: 1").performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("Authorize and delist").performClick()
            waitForSystemPin(device); assertTrue(scene.peer.transactions.isEmpty()); enterRealSystemPin(device)
            ui.waitUntil(25_000) { ui.onNode(hasText("Retry original delisting") and isEnabled()).isDisplayed() }
            val node = requireNotNull(scene.seller)
            val original = requireNotNull(node.delist())
            assertEquals(java.math.BigInteger.ONE, original.quantity)
            assertEquals(java.math.BigInteger("50000000"), original.inputs[1].amount)
            scene.activity.recreate()
            ui.waitUntil(15_000) { ui.onNode(hasText("New listing") and isEnabled()).isDisplayed() }
            ui.onNode(hasScrollAction()).performScrollToNode(hasText("Delisting submitted. Waiting for this node."))
            ui.onNodeWithText("Delisting submitted. Waiting for this node.").performClick()
            ui.waitUntil(5_000) { ui.onNodeWithText("Check delisting").isDisplayed() }
            node.confirmDelist.set(true)
            ui.onNodeWithText("Check delisting").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Delisting observed here. The exact returned stock is in your wallet.").isDisplayed() }
            assertEquals(1, scene.peer.transactions.size)
            assertEquals(original.transaction.encoded(), requireNotNull(node.delist()).transaction.encoded())
            captureNativeScreen(File(scene.evidence, "partial-sale-exact-remaining-stock-returned.png"))
        }
    }

}
