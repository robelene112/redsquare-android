package nl.davidengelen.redsquare

import android.content.Intent
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.StaleObjectException
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

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
