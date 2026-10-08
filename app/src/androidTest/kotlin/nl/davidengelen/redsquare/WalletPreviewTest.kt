package nl.davidengelen.redsquare

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Runs only against the isolated .dev application, never the signed user preview. */
@RunWith(AndroidJUnit4::class)
class WalletPreviewTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun firstLaunchCreatesAnIdentityAndReopeningWalletKeepsIt() {
        ui.onNodeWithText("Wallet").performClick()
        val address = displayedAddress()
        assertTrue(address.length == 44)
        ui.onNodeWithText("Done").performClick()
        ui.activityRule.scenario.recreate()
        ui.onNodeWithText("Wallet").performClick()
        val reopenedAddress = displayedAddress()
        ui.onNodeWithContentDescription("Wallet address").assertIsDisplayed()
        assertEquals(address, reopenedAddress)
    }

    private fun displayedAddress(): String {
        ui.waitUntil(10_000) {
            ui.onAllNodes(androidx.compose.ui.test.hasContentDescription("Wallet address"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        return ui.onNodeWithContentDescription("Wallet address")
            .fetchSemanticsNode().config[SemanticsProperties.Text].single().text
    }

    @Test fun missingSavedWalletShowsRecoveryInsteadOfCreatingAnotherIdentity() {
        ui.onNodeWithText("Wallet").performClick()
        val address = displayedAddress()
        ui.onNodeWithText("Done").performClick()
        val file = File(ui.activity.noBackupFilesDir, "wallet.v1")
        val retained = File(ui.activity.noBackupFilesDir, "wallet.v1.test-retained")
        assertTrue(file.renameTo(retained))
        try {
            ui.activityRule.scenario.recreate()
            ui.waitUntil(10_000) {
                ui.onAllNodes(androidx.compose.ui.test.hasText("Retry")).fetchSemanticsNodes().isNotEmpty()
            }
            ui.onNodeWithText("Retry").assertIsDisplayed()
            ui.onNodeWithText("Wallet").performClick()
            ui.onNodeWithContentDescription("Wallet address").assertDoesNotExist()
            ui.onNodeWithText("Wallet unavailable. Close this window and try again.").assertIsDisplayed()
            ui.onNodeWithText("Done").performClick()
            assertTrue(retained.renameTo(file))
            ui.onNodeWithText("Retry").performClick()
            ui.onNodeWithText("Wallet").performClick()
            assertEquals(address, displayedAddress())
        } finally {
            if (retained.exists()) {
                file.delete()
                assertTrue(retained.renameTo(file))
            }
        }
    }

    @Test fun damagedSavedWalletCanBeRecoveredWithoutChangingIdentity() {
        ui.onNodeWithText("Wallet").performClick()
        val address = displayedAddress()
        ui.onNodeWithText("Done").performClick()
        val file = File(ui.activity.noBackupFilesDir, "wallet.v1")
        val retained = File(ui.activity.noBackupFilesDir, "wallet.v1.test-retained")
        assertTrue(file.renameTo(retained))
        try {
            // Controlled storage corruption, without reading wallet plaintext.
            file.writeBytes(byteArrayOf(1, 0, 0))
            ui.activityRule.scenario.recreate()
            ui.waitUntil(10_000) {
                ui.onAllNodes(androidx.compose.ui.test.hasText("Retry")).fetchSemanticsNodes().isNotEmpty()
            }
            ui.onNodeWithText("Wallet").performClick()
            ui.onNodeWithContentDescription("Wallet address").assertDoesNotExist()
            ui.onNodeWithText("Wallet unavailable. Close this window and try again.").assertIsDisplayed()
            ui.onNodeWithText("Done").performClick()
            assertTrue(file.delete())
            assertTrue(retained.renameTo(file))
            ui.onNodeWithText("Retry").performClick()
            ui.onNodeWithText("Wallet").performClick()
            assertEquals(address, displayedAddress())
        } finally {
            if (retained.exists()) {
                file.delete()
                assertTrue(retained.renameTo(file))
            }
        }
    }
}
