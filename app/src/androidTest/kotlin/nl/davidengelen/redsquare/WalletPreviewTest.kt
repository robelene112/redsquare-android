package nl.davidengelen.redsquare

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.After
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
    @get:Rule val ui = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var activity: MainActivity

    @Before fun openWalletTestApp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java).putExtra("feedDisabled", true))
        scenario.onActivity { activity = it }
    }

    @After fun closeWalletTestApp() { scenario.close() }

    @Test fun firstLaunchCreatesAnIdentityAndReopeningWalletKeepsIt() {
        ui.onNodeWithText("Wallet").performClick()
        val address = displayedAddress()
        assertTrue(address.length == 44)
        ui.onNodeWithText("Done").performClick()
        scenario.recreate()
        scenario.onActivity { activity = it }
        ui.onNodeWithText("Wallet").performClick()
        val reopenedAddress = displayedAddress()
        ui.onNodeWithContentDescription("Wallet address").assertIsDisplayed()
        assertEquals(address, reopenedAddress)
    }

    private fun displayedAddress(): String = displayedWalletAddress(ui)

    @Test fun missingSavedWalletShowsRecoveryInsteadOfCreatingAnotherIdentity() {
        checkStorageRecovery { }
    }

    @Test fun damagedSavedWalletCanBeRecoveredWithoutChangingIdentity() {
        checkStorageRecovery { file ->
            // Controlled storage corruption, without reading wallet plaintext.
            file.writeBytes(byteArrayOf(1, 0, 0))
        }
    }

    private fun checkStorageRecovery(damage: (File) -> Unit) {
        ui.onNodeWithText("Wallet").performClick()
        val address = displayedAddress()
        ui.onNodeWithText("Done").performClick()
        val file = File(activity.noBackupFilesDir, "wallet.v1")
        val retained = File(activity.noBackupFilesDir, "wallet.v1.test-retained")
        assertTrue(file.renameTo(retained))
        try {
            damage(file)
            scenario.recreate()
            scenario.onActivity { activity = it }
            ui.waitUntil(10_000) {
                ui.onAllNodes(androidx.compose.ui.test.hasText("Retry")).fetchSemanticsNodes().isNotEmpty()
            }
            ui.onNodeWithText("Wallet").performClick()
            ui.onNodeWithContentDescription("Wallet address").assertDoesNotExist()
            ui.onNodeWithText("Wallet unavailable. Close this window and try again.").assertIsDisplayed()
            ui.onNodeWithText("Done").performClick()
            if (file.exists()) assertTrue(file.delete())
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
