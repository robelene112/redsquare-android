package nl.davidengelen.redsquare

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class VaultRentalMasterPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun aRentalMasterIsReviewedMintedAndRetrievedWithItsOriginalCreatorRules() = withVaultDeviceCredential { device ->
        withVaultReadingFixture("rental-master", captureFolder = "vault-rental-master-evidence", retainTransfers = true,
            minting = true, rentalMint = true) { scene ->
            val bytes = "A real native rental-master file\n".toByteArray()
            val source = Uri.parse("content://nl.davidengelen.redsquare.dev.test.fileshare/vault-rental.bin")
            scene.context.contentResolver.call(source, "prepare", "vault-rental.bin", Bundle().apply { putByteArray("bytes", bytes) })
            Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(android.app.Instrumentation.ActivityResult(
                Activity.RESULT_OK, Intent().setData(source).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
            ui.openNativeVault()
            ui.waitUntil(5_000) { ui.onNode(hasText("Upload file") and isEnabled()).isDisplayed() }
            ui.onNodeWithText("Upload file").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Review upload").isDisplayed() }
            ui.onNodeWithText("Change access rules").performScrollTo().performClick()
            ui.waitUntil(5_000) { ui.onNodeWithText("File access rules").isDisplayed() }
            ui.onNode(hasText("Rental master key") and hasClickAction()).performClick().assertIsSelected()
            ui.onNodeWithText("Use these rules").performClick()
            ui.waitUntil(5_000) {
                ui.onAllNodesWithText("File access rules").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty() &&
                    ui.onNodeWithText("Review upload").isDisplayed()
            }
            ui.onNodeWithText("Rental master key").performScrollTo().assertIsDisplayed()
            assertTrue(scene.peer.transactions.isEmpty()); assertTrue(scene.peer.vaultUploads.isEmpty())
            ui.onNodeWithText("Create key and upload").performScrollTo().performClick()
            enterRealSystemPin(device)
            ui.waitUntil(25_000) { ui.onNodeWithText("Upload complete").isDisplayed() }
            assertEquals("vault-nft-rental", requireNotNull(scene.mint).candidate()?.keyType)
            assertArrayEquals(bytes, requireNotNull(scene.mint).storedContents())
            assertEquals(1, scene.peer.transactions.size); assertEquals(1, scene.peer.vaultUploads.size)
            captureNativeScreen(File(scene.evidence, "native-rental-master-owned.png"))
            ui.onNodeWithText("Back to files").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("vault-rental.bin").isDisplayed() }
            ui.onNodeWithText("Access file").performScrollTo().performClick()
            ui.waitUntil(5_000) { ui.onNodeWithText("File access").isDisplayed() }
            ui.onNodeWithText("Keep offline").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Available offline").isDisplayed() }
            ui.onNodeWithText("Save as…").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("File saved").isDisplayed() }
            val exported = requireNotNull(scene.context.contentResolver.openInputStream(scene.destination)).use { it.readBytes() }
            assertArrayEquals(bytes, exported)
            assertEquals(1, scene.peer.transactions.size); assertEquals(1, scene.peer.vaultUploads.size)
            captureNativeScreen(File(scene.evidence, "rental-master-creator-exact-export.png"))
        }
    }
}
