package nl.davidengelen.redsquare

import android.app.KeyguardManager
import android.content.Intent
import android.provider.DocumentsContract
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.SaitoWallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.io.InputStream

class WalletExportPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun unavailableDeviceAuthenticationExplainsSetupWithoutExportingOrChangingIdentity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertFalse(context.getSystemService(KeyguardManager::class.java).isDeviceSecure)
        val intent = Intent(context, MainActivity::class.java).putExtra("feedDisabled", true)
        ActivityScenario.launch<MainActivity>(intent).use {
            try {
                ui.onNodeWithText("Wallet").performClick()
                val address = displayedWalletAddress(ui)
                ui.onNodeWithText("Export wallet").performClick()
                val guidance = "Set a screen lock in Android settings to export your wallet."
                ui.waitUntil(10_000) { ui.onAllNodes(hasText(guidance)).fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText(guidance).assertIsDisplayed()
                assertEquals(address, displayedWalletAddress(ui))
                captureFeedEvidence("wallet-export-evidence/no-lock.png")
                ui.onNodeWithText("Done").performClick()
                ui.onNodeWithText("Wallet").performClick()
                assertEquals(address, displayedWalletAddress(ui))
            } finally { captureFeedEvidence("wallet-export-evidence/screen.png") }
        }
    }

    @Test fun realPinAuthenticationAndDocumentSaveProduceACompatibleBackup() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val device = UiDevice.getInstance(instrumentation)
        assertFalse(keyguard.isDeviceSecure)
        val name = "wallet-test-${UUID.randomUUID()}.json"
        val uri = DocumentsContract.buildDocumentUri(ExportTestDocumentsProvider.AUTHORITY, name)
        device.executeShellCommand("locksettings set-pin --user 0 $EXPORT_TEST_PIN")
        try {
            ui.waitUntil(10_000) { keyguard.isDeviceSecure }
            check(device.executeShellCommand("locksettings verify --old $EXPORT_TEST_PIN --user 0")
                .contains("Lock credential verified successfully"))
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedDisabled", true)).use {
                try {
                ui.onNodeWithText("Wallet").performClick()
                val address = displayedWalletAddress(ui)
                ui.onNodeWithText("Export wallet").performClick()
                chooseTestBackup(device, name)
                enterRealSystemPin(device)
                ui.waitUntil(15_000) { ui.onAllNodes(hasText("Wallet backup saved")).fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText("Wallet backup saved").assertIsDisplayed()
                val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use {
                    it.readBoundedBackup()
                }
                try {
                    val json = bytes.toString(Charsets.UTF_8)
                    assertEquals(address, SaitoWallet.fromBackup(json).publicKey)
                    val wallet = JsonParser.parseString(json).asJsonObject.getAsJsonObject("wallet")
                    assertEquals("5.677", wallet.get("version").asString)
                    assertEquals(address, wallet.get("publicKey").asString)
                    assertEquals(address, displayedWalletAddress(ui))
                } finally { bytes.fill(0) }
                captureFeedEvidence("wallet-export-evidence/real-pin-success.png")
                DocumentsContract.deleteDocument(context.contentResolver, uri)
                } catch (failure: Throwable) {
                    captureFeedEvidence("wallet-export-evidence/real-pin-failure.png")
                    throw failure
                }
            }
        } finally {
            device.executeShellCommand("locksettings clear --old $EXPORT_TEST_PIN --user 0")
        }
    }

    @Test fun wrongPinAndCancelledAuthenticationLeaveNoBackupAndRetainIdentity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val device = UiDevice.getInstance(instrumentation)
        assertFalse(keyguard.isDeviceSecure)
        device.executeShellCommand("locksettings set-pin --user 0 $EXPORT_TEST_PIN")
        try {
            ui.waitUntil(10_000) { keyguard.isDeviceSecure }
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedDisabled", true)).use {
                ui.onNodeWithText("Wallet").performClick()
                val address = displayedWalletAddress(ui)
                val name = "wallet-cancel-${UUID.randomUUID()}.json"
                val uri = DocumentsContract.buildDocumentUri(ExportTestDocumentsProvider.AUTHORITY, name)
                ui.onNodeWithText("Export wallet").performClick()
                chooseTestBackup(device, name)
                enterRealSystemPin(device, "135790")
                check(device.wait(Until.hasObject(By.textContains("Wrong PIN")), 10_000))
                assertEquals(-1, requireNotNull(context.contentResolver.openInputStream(uri)).use { it.read() })
                cancelRealSystemAuthentication(device, context.packageName)
                val cancelled = "Wallet export canceled. Your wallet is unchanged."
                ui.waitUntil(10_000) { ui.onAllNodes(hasText(cancelled)).fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText(cancelled).assertIsDisplayed()
                ui.onNodeWithText("Wallet backup saved").assertDoesNotExist()
                ui.onNodeWithText("Export wallet").assertIsEnabled()
                assertEquals(address, displayedWalletAddress(ui))
                captureFeedEvidence("wallet-export-evidence/authentication-cancelled.png")
            }
        } finally { device.executeShellCommand("locksettings clear --old $EXPORT_TEST_PIN --user 0") }
    }

    @Test fun stalledProviderReturnsToAReadableFailureAndAllowsAnotherExport() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val device = UiDevice.getInstance(instrumentation)
        assertFalse(keyguard.isDeviceSecure)
        device.executeShellCommand("locksettings set-pin --user 0 $EXPORT_TEST_PIN")
        try {
            ui.waitUntil(10_000) { keyguard.isDeviceSecure }
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedDisabled", true)).use {
                ui.onNodeWithText("Wallet").performClick()
                val address = displayedWalletAddress(ui)
                ui.onNodeWithText("Export wallet").performClick()
                chooseTestBackup(device, "stalled-read-${UUID.randomUUID()}.json")
                enterRealSystemPin(device)
                val failed = "Your wallet backup could not be saved and verified. Try again with another save location."
                ui.waitUntil(40_000) { ui.onAllNodes(hasText(failed)).fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText(failed).assertIsDisplayed()
                ui.onNodeWithText("Wallet backup saved").assertDoesNotExist()
                ui.onNodeWithText("Export wallet").assertIsEnabled()
                assertEquals(address, displayedWalletAddress(ui))
                captureFeedEvidence("wallet-export-evidence/provider-deadline.png")
            }
        } finally { device.executeShellCommand("locksettings clear --old $EXPORT_TEST_PIN --user 0") }
    }

    @Test fun failedWritesAndInvalidReadbackRequireFreshAuthenticationForSuccessfulRetry() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val device = UiDevice.getInstance(instrumentation)
        assertFalse(keyguard.isDeviceSecure)
        device.executeShellCommand("locksettings set-pin --user 0 $EXPORT_TEST_PIN")
        try {
            ui.waitUntil(10_000) { keyguard.isDeviceSecure }
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedDisabled", true)).use {
                ui.onNodeWithText("Wallet").performClick()
                val address = displayedWalletAddress(ui)
                for (fault in listOf("write-fails", "read-fails", "truncated-read")) {
                    ui.onNodeWithText("Export wallet").performClick()
                    chooseTestBackup(device, "$fault-${UUID.randomUUID()}.json")
                    enterRealSystemPin(device)
                    val failed = "Your wallet backup could not be saved and verified. Try again with another save location."
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText(failed)).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText(failed).assertIsDisplayed()
                    ui.onNodeWithText("Wallet backup saved").assertDoesNotExist()
                    ui.onNodeWithText("Export wallet").assertIsEnabled()
                    assertEquals(address, displayedWalletAddress(ui))
                    captureFeedEvidence("wallet-export-evidence/$fault.png")
                }
                val name = "retry-${UUID.randomUUID()}.json"
                val uri = DocumentsContract.buildDocumentUri(ExportTestDocumentsProvider.AUTHORITY, name)
                ui.onNodeWithText("Export wallet").performClick()
                chooseTestBackup(device, name)
                // Previous successful credentials must not authorize this attempt.
                waitForSystemPin(device)
                assertEquals(-1, requireNotNull(context.contentResolver.openInputStream(uri)).use { it.read() })
                enterRealSystemPin(device)
                ui.waitUntil(15_000) { ui.onAllNodes(hasText("Wallet backup saved")).fetchSemanticsNodes().isNotEmpty() }
                val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readBoundedBackup() }
                try { assertEquals(address, SaitoWallet.fromBackup(bytes.toString(Charsets.UTF_8)).publicKey) }
                finally { bytes.fill(0) }
                assertEquals(address, displayedWalletAddress(ui))
                captureFeedEvidence("wallet-export-evidence/provider-retry.png")
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            }
        } finally { device.executeShellCommand("locksettings clear --old $EXPORT_TEST_PIN --user 0") }
    }

    private fun InputStream.readBoundedBackup(): ByteArray {
        val buffer = ByteArray(SaitoWallet.MAX_BACKUP_BYTES + 1)
        try {
            var size = 0
            while (size < buffer.size) {
                val count = read(buffer, size, buffer.size - size)
                if (count < 0) break
                check(count > 0)
                size += count
            }
            check(size in 1..SaitoWallet.MAX_BACKUP_BYTES)
            return buffer.copyOf(size)
        } finally { buffer.fill(0) }
    }

}
