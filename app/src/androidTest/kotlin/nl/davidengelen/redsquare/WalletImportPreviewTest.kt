package nl.davidengelen.redsquare

import android.content.Intent
import android.app.KeyguardManager
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.StaleObjectException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class WalletImportPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun invalidSelectedBackupShowsRecoveryAndRetainsTheCurrentWallet() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .putExtra("feedDisabled", true)).use { scenario ->
            ui.onNodeWithText("Wallet").performClick()
            val address = displayedWalletAddress(ui)
            ui.onNodeWithText("Import wallet").performClick()
            selectImportRoot(device)
            val file = By.text("invalid-import.json")
            check(device.wait(Until.hasObject(file), 10_000))
            requireNotNull(device.findObject(file)).click()
            val failed = "That file is not a valid Saito wallet backup. Your wallet is unchanged."
            ui.waitUntil(15_000) { ui.onAllNodes(hasText(failed)).fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText(failed).assertIsDisplayed()
            assertEquals(address, displayedWalletAddress(ui))
            ui.onNodeWithText("Import wallet").assertIsEnabled()
            captureFeedEvidence("wallet-import-evidence/invalid-backup.png")
            ui.onNodeWithText("Done").performClick()
            scenario.recreate()
            ui.onNodeWithText("Wallet").performClick()
            assertEquals(address, displayedWalletAddress(ui))
            captureFeedEvidence("wallet-import-evidence/invalid-backup-recovery.png")
        }
    }

    @Test fun selectedValidBackupsPreviewSameOrDifferentIdentityWithoutActivation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        assertFalse(keyguard.isDeviceSecure)
        device.executeShellCommand("locksettings set-pin --user 0 $EXPORT_TEST_PIN")
        try {
            ui.waitUntil(10_000) { keyguard.isDeviceSecure }
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedDisabled", true)).use {
                ui.onNodeWithText("Wallet").performClick()
                val address = displayedWalletAddress(ui)
                val original = "same-import-${UUID.randomUUID()}.json"
                ui.onNodeWithText("Export wallet").performClick()
                chooseTestBackup(device, original)
                enterRealSystemPin(device)
                ui.waitUntil(15_000) { ui.onAllNodes(hasText("Wallet backup saved")).fetchSemanticsNodes().isNotEmpty() }
                // Second identity is published scalar1 from the independent Saito reference.
                // Never fund it. Neither fixture nor backup bytes are retained as evidence.
                val cases = listOf(
                    Triple(original, "Restore this wallet", address),
                    Triple("reference-import.json", "Replace your wallet", "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR"),
                )
                for ((name, title, expected) in cases) {
                    ui.onNodeWithText("Import wallet").performClick()
                    selectImportRoot(device)
                    val file = By.text(name)
                    check(device.wait(Until.hasObject(file), 10_000))
                    requireNotNull(device.findObject(file)).click()
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText(title)).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText(title).assertIsDisplayed()
                    ui.onNodeWithContentDescription("Selected wallet address").assertTextEquals(expected)
                    assertEquals(address, displayedWalletAddress(ui))
                    captureFeedEvidence("wallet-import-evidence/valid-preview-${if (name == original) "same" else "different"}.png")
                }
                DocumentsContract.deleteDocument(context.contentResolver,
                    DocumentsContract.buildDocumentUri(ExportTestDocumentsProvider.AUTHORITY, original))
            }
        } finally { device.executeShellCommand("locksettings clear --old $EXPORT_TEST_PIN --user 0") }
    }

    @Test fun replacementRequiresANewBackupBoundToTheCurrentSelection() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        assertFalse(keyguard.isDeviceSecure)
        device.executeShellCommand("locksettings set-pin --user 0 $EXPORT_TEST_PIN")
        val oldBackup = "before-import-${UUID.randomUUID()}.json"
        val freshBackup = "guarded-import-${UUID.randomUUID()}.json"
        try {
            ui.waitUntil(10_000) { keyguard.isDeviceSecure }
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedDisabled", true)).use {
                ui.onNodeWithText("Wallet").performClick()
                val address = displayedWalletAddress(ui)
                ui.onNodeWithText("Export wallet").performClick()
                chooseTestBackup(device, oldBackup)
                enterRealSystemPin(device)
                ui.waitUntil(15_000) { ui.onAllNodes(hasText("Wallet backup saved")).fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText("Import wallet").performClick()
                selectImportRoot(device)
                requireNotNull(device.wait(Until.findObject(By.text("reference-import.json")), 10_000)).click()
                ui.waitUntil(15_000) { ui.onAllNodes(hasText("Replace your wallet")).fetchSemanticsNodes().isNotEmpty() }
                // A saved backup made before selection must not authorize replacement.
                val required = "Back up your current wallet before continuing with replacement."
                val saved = "Current wallet backed up for this replacement"
                ui.onNodeWithText(required).performScrollTo().assertIsDisplayed()
                assertEquals(address, displayedWalletAddress(ui))
                ui.onNodeWithText("Back up current wallet").performScrollTo().performClick()
                chooseTestBackup(device, freshBackup)
                enterRealSystemPin(device)
                ui.waitUntil(15_000) { ui.onAllNodes(hasText(saved)).fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText(saved).performScrollTo().assertIsDisplayed()
                ui.onNodeWithText(required).assertDoesNotExist()
                assertEquals(address, displayedWalletAddress(ui))
                captureFeedEvidence("wallet-import-evidence/fresh-candidate-backup.png")
                // Selecting again invalidates proof even when the file's identity is the same.
                ui.onNodeWithText("Import wallet").performScrollTo().performClick()
                selectImportRoot(device)
                requireNotNull(device.wait(Until.findObject(By.text("reference-import.json")), 10_000)).click()
                ui.waitUntil(15_000) { ui.onAllNodes(hasText("Replace your wallet")).fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText(required).performScrollTo().assertIsDisplayed()
                ui.onNodeWithText(saved).assertDoesNotExist()
                assertEquals(address, displayedWalletAddress(ui))
                captureFeedEvidence("wallet-import-evidence/reselected-backup-required.png")
            }
        } finally {
            for (name in listOf(oldBackup, freshBackup)) runCatching {
                DocumentsContract.deleteDocument(context.contentResolver,
                    DocumentsContract.buildDocumentUri(ExportTestDocumentsProvider.AUTHORITY, name))
            }
            device.executeShellCommand("locksettings clear --old $EXPORT_TEST_PIN --user 0")
        }
    }

    private fun selectImportRoot(device: UiDevice) {
        val row = By.text(ExportTestDocumentsProvider.TITLE).res("android", "title")
        val title = By.text(ExportTestDocumentsProvider.TITLE)
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (!device.hasObject(row) && device.hasObject(title)) return
            try {
                val entry = device.findObject(row)
                if (entry != null) {
                    entry.click()
                    device.wait(Until.gone(row), 2_000)
                } else device.findObject(By.desc("Show roots"))?.click()
            } catch (_: StaleObjectException) { /* Observe the current picker after its layout changes. */ }
            SystemClock.sleep(100)
        }
        captureFeedEvidence("wallet-import-evidence/root-selection-failure.png")
        error("Test backup folder did not become the visible picker location")
    }
}
