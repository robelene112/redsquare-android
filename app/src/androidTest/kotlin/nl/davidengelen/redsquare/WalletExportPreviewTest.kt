package nl.davidengelen.redsquare

import android.app.KeyguardManager
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

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
}
