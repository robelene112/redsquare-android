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
            ui.onNodeWithText("Key transaction submitted").performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("File accepted by this node").performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("Key ownership not yet observed").performScrollTo().assertIsDisplayed()
            captureNativeScreen(File(scene.evidence, "file-accepted-key-still-unobserved.png"))
            ui.onNodeWithContentDescription("Back to files").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Files").isDisplayed() }
            ui.onNodeWithText("Offline").performClick()
            ui.onNodeWithText("Open upload").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Waiting for availability").isDisplayed() }
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
            ui.onNodeWithText("This node rejected the file. Your original key and file are retained.").performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("Check availability").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Waiting for availability").isDisplayed() }
            ui.onNodeWithText("Key ownership verified").performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("This node rejected the file. Your original key and file are retained.").performScrollTo().assertIsDisplayed()
            assertEquals("A failed read-only check must not retry a rejected upload", 1, scene.peer.vaultUploads.size)
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

    @Test fun insufficientAvailableFundsKeepTheFileAndCanRecoverBeforeTheFirstAuthenticatedSend() = withVaultDeviceCredential { device ->
        withVaultReadingFixture("insufficient-upload", captureFolder = "vault-upload-recovery-evidence", retainTransfers = true, minting = true) { scene ->
            val server = requireNotNull(scene.mint)
            server.availableNolan = 50_000_000
            val bytes = "Keep the file until the actual key deposit is available".toByteArray()
            ui.reviewVaultFile(scene, "vault-budget.bin", bytes)
            ui.waitUntil(15_000) {
                ui.onNodeWithText("You need 1 SAITO in available funds for this key. Your file is saved.").isDisplayed()
            }
            assertEquals(scene.context.packageName, device.currentPackageName)
            assertTrue(scene.peer.transactions.isEmpty())
            assertTrue(scene.peer.vaultUploads.isEmpty())
            captureNativeScreen(File(scene.evidence, "insufficient-deposit-no-mint-no-auth.png"))
            server.availableNolan = 200_000_000
            ui.onNodeWithText("Create key and upload").performScrollTo().performClick()
            enterRealSystemPin(device)
            ui.waitUntil(25_000) { ui.onNodeWithText("Upload complete").isDisplayed() }
            assertEquals(1, scene.peer.transactions.size)
            assertEquals(1, scene.peer.vaultUploads.size)
            assertArrayEquals(bytes, server.storedContents())
            captureNativeScreen(File(scene.evidence, "retained-file-deposit-now-available.png"))
            ui.onNodeWithText("Back to files").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("vault-budget.bin").isDisplayed() }
        }
    }


    @Test fun unavailableDeviceAuthenticationDoesNotPublishAndCanRecoverInTheSameReview() {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val keyguard = context.getSystemService(android.app.KeyguardManager::class.java)
        val device = androidx.test.uiautomator.UiDevice.getInstance(instrumentation)
        check(!keyguard.isDeviceSecure)
        try {
            withVaultReadingFixture("authentication-unavailable", captureFolder = "vault-upload-recovery-evidence", retainTransfers = true, minting = true) { scene ->
                val bytes = "Keep this reviewed file while device authentication is unavailable".toByteArray()
                ui.reviewVaultFile(scene, "vault-auth.bin", bytes)
                ui.waitUntil(15_000) { ui.onNodeWithText("Device authentication could not start. Try again.").isDisplayed() }
                assertTrue(scene.peer.transactions.isEmpty()); assertTrue(scene.peer.vaultUploads.isEmpty())
                captureNativeScreen(File(scene.evidence, "authentication-unavailable-no-publication.png"))
                device.executeShellCommand("locksettings set-pin --user 0 $EXPORT_TEST_PIN")
                ui.onNodeWithText("Create key and upload").performScrollTo().performClick()
                enterRealSystemPin(device)
                ui.waitUntil(25_000) { ui.onNodeWithText("Upload complete").isDisplayed() }
                assertEquals(1, scene.peer.transactions.size); assertEquals(1, scene.peer.vaultUploads.size)
                assertArrayEquals(bytes, requireNotNull(scene.mint).storedContents())
                captureNativeScreen(File(scene.evidence, "same-review-authentication-now-available.png"))
                ui.onNodeWithText("Back to files").performScrollTo().performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("vault-auth.bin").isDisplayed() }
            }
        } finally { if (keyguard.isDeviceSecure) device.executeShellCommand("locksettings clear --old $EXPORT_TEST_PIN --user 0") }
    }

}
