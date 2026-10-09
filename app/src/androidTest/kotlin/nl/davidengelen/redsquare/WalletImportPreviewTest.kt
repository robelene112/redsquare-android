package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
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
            check(device.wait(Until.hasObject(By.desc("Show roots")), 15_000))
            requireNotNull(device.findObject(By.desc("Show roots"))).click()
            val root = By.text(ExportTestDocumentsProvider.TITLE).res("android", "title")
            check(device.wait(Until.hasObject(root), 10_000))
            requireNotNull(device.findObject(root)).click()
            check(device.wait(Until.gone(root), 10_000))
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
}
