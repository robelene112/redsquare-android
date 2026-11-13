package nl.davidengelen.redsquare

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class VaultMintingPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun aReviewedAndAuthenticatedFileUploadCreatesOneOwnedKeyAndStoresExactBytes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(!context.getSystemService(KeyguardManager::class.java).isDeviceSecure)
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("locksettings set-pin --user 0 $EXPORT_TEST_PIN")
        try {
            withVaultReadingFixture("minting", captureFolder = "vault-minting-evidence", retainTransfers = true, minting = true) { scene ->
                val bytes = "A native Vault upload\nSaito café 🔴\n".toByteArray()
                val source = Uri.parse("content://nl.davidengelen.redsquare.dev.test.fileshare/vault-minting.bin")
                scene.context.contentResolver.call(source, "prepare", "vault-minting.bin", Bundle().apply { putByteArray("bytes", bytes) })
                Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(android.app.Instrumentation.ActivityResult(
                    Activity.RESULT_OK, Intent().setData(source).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
                ui.openNativeVault()
                ui.waitUntil(5_000) { ui.onNodeWithText("Upload file").isDisplayed() }
                ui.onNodeWithText("Upload file").performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("Review upload").isDisplayed() }
                ui.onNodeWithText("1 SAITO").assertIsDisplayed()
                ui.onNodeWithText("Create key and upload").performScrollTo().assertIsEnabled().performClick()
                enterRealSystemPin(device)
                ui.waitUntil(25_000) { ui.onNodeWithText("Upload complete").isDisplayed() }
                assertEquals(1, scene.peer.transactions.size)
                assertEquals(1, scene.peer.vaultUploads.size)
                assertArrayEquals(bytes, requireNotNull(scene.mint).storedContents())
                captureNativeScreen(File(scene.evidence, "owned-key-uploaded.png"))
                ui.onNodeWithText("Back to files").performScrollTo().performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("vault-minting.bin").isDisplayed() }
                captureNativeScreen(File(scene.evidence, "new-owned-file.png"))
            }
        } finally { device.executeShellCommand("locksettings clear --old $EXPORT_TEST_PIN --user 0") }
    }
}
