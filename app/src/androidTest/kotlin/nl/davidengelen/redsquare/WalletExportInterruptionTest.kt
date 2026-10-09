package nl.davidengelen.redsquare

import android.app.KeyguardManager
import android.content.Intent
import android.os.Process
import android.provider.DocumentsContract
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Host runs these phases separately; prepare deliberately terminates its process. */
class WalletExportInterruptionTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun prepareExportThenTerminateBeforeAuthentication() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertTrue(context.getSystemService(KeyguardManager::class.java).isDeviceSecure)
        val device = UiDevice.getInstance(instrumentation)
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .putExtra("feedDisabled", true))
        ui.onNodeWithText("Wallet").performClick()
        val address = displayedWalletAddress(ui)
        val name = "interrupted-${UUID.randomUUID()}.json"
        ui.onNodeWithText("Export wallet").performClick()
        chooseTestBackup(device, name)
        waitForSystemPin(device)
        val uri = DocumentsContract.buildDocumentUri(ExportTestDocumentsProvider.AUTHORITY, name)
        assertEquals(-1, requireNotNull(context.contentResolver.openInputStream(uri)).use { it.read() })
        // Public identity and synthetic filename only, never wallet options or credentials.
        File(context.cacheDir, "wallet-export-interruption-state.txt").outputStream().use {
            it.write("$address\n$name\n".toByteArray())
            it.fd.sync()
        }
        Process.killProcess(Process.myPid())
        error("Expected test process termination")
    }

    @Test fun coldRecoveryRetainsIdentityAndRequiresNewAuthentication() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val state = File(context.cacheDir, "wallet-export-interruption-state.txt").readLines()
        check(state.size == 2)
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .putExtra("feedDisabled", true)).use {
            ui.onNodeWithText("Wallet").performClick()
            assertEquals(state[0], displayedWalletAddress(ui))
            ui.onNodeWithText("Wallet backup saved").assertDoesNotExist()
            ui.onNodeWithText("Export wallet").assertIsEnabled()
            captureFeedEvidence("wallet-export-evidence/process-recovery.png")
            val name = "after-interruption-${UUID.randomUUID()}.json"
            val uri = DocumentsContract.buildDocumentUri(ExportTestDocumentsProvider.AUTHORITY, name)
            ui.onNodeWithText("Export wallet").performClick()
            chooseTestBackup(device, name)
            waitForSystemPin(device)
            assertEquals(-1, requireNotNull(context.contentResolver.openInputStream(uri)).use { it.read() })
            enterRealSystemPin(device)
            ui.waitUntil(15_000) { ui.onAllNodes(hasText("Wallet backup saved")).fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText("Wallet backup saved").assertIsDisplayed()
            assertEquals(state[0], displayedWalletAddress(ui))
            captureFeedEvidence("wallet-export-evidence/process-retry.png")
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        }
    }
}
