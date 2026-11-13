package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class VaultUploadRecoveryPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun delayedOwnershipIsCheckedWithoutRebroadcastingTheKeyOrUploadingTheFileAgain() = withVaultDeviceCredential { device ->
        withVaultReadingFixture("delayed-ownership", captureFolder = "vault-upload-recovery-evidence", retainTransfers = true, minting = true) { scene ->
            scene.ownsKey.set(false)
            val bytes = "A file waiting for its original Vault key".toByteArray()
            ui.reviewVaultFile(scene, "vault-delayed.bin", bytes)
            enterRealSystemPin(device)
            ui.waitUntil(25_000) { ui.onNodeWithText("Waiting for availability").isDisplayed() }
            assertEquals(1, scene.peer.transactions.size)
            assertEquals(1, scene.peer.vaultUploads.size)
            assertArrayEquals(bytes, requireNotNull(scene.mint).storedContents())
            captureNativeScreen(File(scene.evidence, "file-accepted-key-still-unobserved.png"))
            scene.ownsKey.set(true)
            ui.onNodeWithText("Check availability").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Upload complete").isDisplayed() }
            assertEquals("A read-only check must not submit another mint", 1, scene.peer.transactions.size)
            assertEquals("A read-only check must not upload accepted bytes again", 1, scene.peer.vaultUploads.size)
            captureNativeScreen(File(scene.evidence, "original-key-now-observed.png"))
            ui.onNodeWithText("Back to files").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("vault-delayed.bin").isDisplayed() }
        }
    }

    @Test fun aRejectedFileIsRetriedAfterFreshAuthenticationWithoutMintingAnotherKey() = withVaultDeviceCredential { device ->
        withVaultReadingFixture("rejected-upload", captureFolder = "vault-upload-recovery-evidence", retainTransfers = true, minting = true) { scene ->
            val server = requireNotNull(scene.mint)
            server.rejectUpload.set(true)
            val bytes = "Retry these exact reviewed bytes, using the same Vault key".toByteArray()
            ui.reviewVaultFile(scene, "vault-retry.bin", bytes)
            enterRealSystemPin(device)
            ui.waitUntil(25_000) { ui.onNodeWithText("Waiting for availability").isDisplayed() }
            assertEquals(1, scene.peer.transactions.size)
            assertEquals(1, scene.peer.vaultUploads.size)
            val originalMint = scene.peer.transactions.single().copyOf()
            captureNativeScreen(File(scene.evidence, "upload-refused-original-key-retained.png"))
            server.rejectUpload.set(false)
            ui.onNodeWithText("Retry original upload").performScrollTo().performClick()
            waitForSystemPin(device)
            assertEquals("Retry must wait for fresh device authorization", 1, scene.peer.vaultUploads.size)
            assertEquals(1, scene.peer.transactions.size)
            enterRealSystemPin(device)
            ui.waitUntil(25_000) { ui.onNodeWithText("Upload complete").isDisplayed() }
            assertEquals(1, scene.peer.transactions.size)
            assertArrayEquals(originalMint, scene.peer.transactions.single())
            assertEquals(2, scene.peer.vaultUploads.size)
            assertEquals("The original signed file must be reused", scene.peer.vaultUploads[0].body.get("data"), scene.peer.vaultUploads[1].body.get("data"))
            assertArrayEquals(bytes, server.storedContents())
            captureNativeScreen(File(scene.evidence, "same-key-exact-file-retry-complete.png"))
            ui.onNodeWithText("Back to files").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("vault-retry.bin").isDisplayed() }
        }
    }
}
