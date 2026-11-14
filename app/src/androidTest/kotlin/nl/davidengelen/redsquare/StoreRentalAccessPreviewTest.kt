package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class StoreRentalAccessPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private fun payAndOpen(scene: StorePurchaseScene, device: UiDevice) {
        scene.node.phase = OwnedStorePurchaseFixture.Phase.DELIVERED
        ui.openStorePurchaseProduct(illustrated = true)
        ui.waitUntil(15_000) { ui.onAllNodesWithText("Confirm and pay").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        ui.onNodeWithText("Confirm and pay").performScrollTo().performClick()
        waitForSystemPin(device); assertTrue(scene.node.peer.transactions.isEmpty()); enterRealSystemPin(device)
        ui.waitUntil(25_000) { ui.onNode(hasText("Article received") and hasAnyAncestor(isDialog())).isDisplayed() }
        ui.waitUntil(5_000) { ui.onAllNodesWithText("Open in Vault").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        ui.onNodeWithText("Open in Vault").performScrollTo().assertIsEnabled().performClick()
        ui.waitUntil(15_000) { ui.onNodeWithContentDescription("Switch app: Vault").isDisplayed() && ui.onNodeWithText("Upload notes.txt").isDisplayed() }
        assertEquals(1, scene.node.peer.transactions.size)
    }
    @Test fun anAuthenticatedReceivedRentalOpensItsVaultDestinationWithoutCopyingAnyKey() = withVaultDeviceCredential { device ->
        withStorePurchaseFixture("rental-vault-entry", expectedTransactions = 1, rental = true) { scene ->
            payAndOpen(scene, device)
            captureNativeScreen(File(scene.evidence, "received-rental-native-vault-destination.png"))
            ui.onNodeWithText("Back to order").performClick()
            ui.waitUntil(15_000) { ui.onAllNodesWithText("Check order").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNodeWithText("Check order").performScrollTo().assertIsDisplayed()
            assertEquals(1, scene.node.peer.transactions.size)
        }
    }
    @Test fun activationAndOfflineExportSurviveRecreationAndProviderExpiryWithoutAnotherPayment() = withVaultDeviceCredential { device ->
        withStorePurchaseFixture("rental-offline", expectedTransactions = 1, rental = true) { scene ->
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val destination = android.net.Uri.parse("content://nl.davidengelen.redsquare.dev.test.fileshare/vault-rental-saved.bin")
            context.contentResolver.call(destination, "prepare", "vault-rental-saved.bin", android.os.Bundle().apply { putByteArray("bytes", byteArrayOf()) })
            androidx.test.espresso.intent.Intents.init()
            try {
                androidx.test.espresso.intent.Intents.intending(androidx.test.espresso.intent.matcher.IntentMatchers.hasAction(android.content.Intent.ACTION_CREATE_DOCUMENT))
                    .respondWith(android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_OK, android.content.Intent().setData(destination).addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)))
                payAndOpen(scene, device)
                ui.onNodeWithText("Keep offline").performScrollTo().performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("Saved for offline use").isDisplayed() }
                val provider = requireNotNull(scene.rental)
                assertEquals(1, provider.checkouts)
                val reads = provider.reads
                scene.activity.recreate()
                ui.waitUntil(15_000) { ui.onNodeWithText("Upload notes.txt").isDisplayed() }
                assertEquals(reads, provider.reads)
                provider.time = OwnedStoreRentalFixture.Time.AFTER; provider.available = false
                ui.onNodeWithText("Save as").performScrollTo().performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("File saved").isDisplayed() && ui.onNode(hasText("Save as") and isEnabled()).isDisplayed() }
                val expected = java.util.Base64.getDecoder().decode(scene.node.reference.getAsJsonObject("rentalAccess").get("contentsBase64").asString)
                assertArrayEquals(expected, context.contentResolver.openInputStream(destination)!!.use { it.readBytes() })
                assertEquals(reads, provider.reads); assertEquals(1, provider.checkouts)
                assertEquals(1, scene.node.peer.transactions.size)
                captureNativeScreen(File(scene.evidence, "rental-cached-exact-export-after-expiry-outage.png"))
            } finally { androidx.test.espresso.intent.Intents.release() }
        }
    }
}
