package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Harness starts this observable flow on a fresh isolated .dev installation. */
class BackupRecoveryPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun reminderCanBeDismissedWhileRecoveryGuidanceRemainsAvailable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .putExtra("feedDisabled", true)).use { scenario ->
            try {
                ui.waitUntil(10_000) {
                    ui.onAllNodes(hasText("Back up your wallet")).fetchSemanticsNodes().isNotEmpty()
                }
                ui.onNodeWithText("Back up your wallet").assertIsDisplayed()
                ui.onNodeWithText("Settings").performClick()
                ui.onNodeWithText("Done").performClick()
                ui.onNodeWithText("Wallet").performClick()
                val address = displayedWalletAddress(ui)
                ui.onNodeWithText("Done").performClick()
                ui.onNodeWithText("Later").performClick()
                ui.onNodeWithText("Back up your wallet").assertDoesNotExist()
                scenario.recreate()
                ui.onNodeWithText("Wallet").performClick()
                assertEquals(address, displayedWalletAddress(ui))
                ui.onNodeWithText("Recovery guide").performClick()
                ui.onNodeWithText("The saved JSON file is not encrypted.", substring = true).assertIsDisplayed()
                captureFeedEvidence("wallet-backup-evidence/private-backup-guidance.png")
                ui.onNodeWithText("does not include all cached Chat history", substring = true)
                    .performScrollTo().assertIsDisplayed()
                captureFeedEvidence("wallet-backup-evidence/recovery-guide.png")
                ui.onNodeWithText("Close guide").performClick()
                assertEquals(address, displayedWalletAddress(ui))
                ui.onNodeWithText("Done").performClick()
                ui.onNodeWithText("Back up your wallet").assertDoesNotExist()
                captureFeedEvidence("wallet-backup-evidence/dismissed.png")
            } finally { captureFeedEvidence("wallet-backup-evidence/screen.png") }
        }
    }
}
