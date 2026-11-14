package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class StoreRentalAccessPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun anAuthenticatedReceivedRentalOpensItsVaultDestinationWithoutCopyingAnyKey() = withVaultDeviceCredential { device ->
        withStorePurchaseFixture("rental-vault-entry", expectedTransactions = 1, rental = true) { scene ->
            scene.node.phase = OwnedStorePurchaseFixture.Phase.DELIVERED
            ui.openStorePurchaseProduct(illustrated = true)
            ui.onNodeWithText("Confirm and pay").performScrollTo().performClick()
            waitForSystemPin(device); assertTrue(scene.node.peer.transactions.isEmpty()); enterRealSystemPin(device)
            ui.waitUntil(25_000) { ui.onNode(hasText("Article received") and hasAnyAncestor(isDialog())).isDisplayed() }
            ui.onNodeWithText("Open in Vault").performScrollTo().assertIsEnabled().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithContentDescription("Switch app: Vault").isDisplayed() }
            ui.onNodeWithText("Upload notes.txt").assertIsDisplayed()
            assertEquals(1, scene.node.peer.transactions.size)
            captureNativeScreen(File(scene.evidence, "received-rental-native-vault-destination.png"))
        }
    }
}
