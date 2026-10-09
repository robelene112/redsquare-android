package nl.davidengelen.redsquare

import android.app.KeyguardManager
import android.content.Intent
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import java.net.InetAddress
import java.util.UUID

/** Actual file/import/export UI; the known scalar1 fixture must never be funded. */
class WalletImportRiskPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun outgoingOtherAssetsAndSavedNamesCannotBeClearedByASaitoZeroResponse() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        assertFalse(keyguard.isDeviceSecure)
        device.executeShellCommand("locksettings set-pin --user 0 $EXPORT_TEST_PIN")
        val originalFile = "risk-original-${UUID.randomUUID()}.json"
        val currentBackup = "risk-outgoing-${UUID.randomUUID()}.json"
        val server = MockWebServer()
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest) = MockResponse.Builder()
                .body("${System.currentTimeMillis()}-1234-${"a".repeat(64)}.snap\n").build()
        }
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        try {
            ui.waitUntil(10_000) { keyguard.isDeviceSecure }
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedDisabled", true)
                .putExtra("balanceApiUrl", "http://127.0.0.1:${server.port}/balance/")).use {
                ui.onNodeWithText("Wallet").performClick()
                val originalAddress = displayedWalletAddress(ui)
                ui.onNodeWithText("Export wallet").performClick()
                chooseTestBackup(device, originalFile)
                enterRealSystemPin(device)
                ui.waitUntil(15_000) { ui.onAllNodes(hasText("Wallet backup saved")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                select(device, "risk-import.json")
                ui.waitUntil(15_000) { ui.onAllNodes(hasText("Server reports zero SAITO")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                // The incoming metadata must not describe the outgoing native wallet.
                ui.onNodeWithText("Saved name: reference").assertDoesNotExist()
                ui.onNodeWithText("Skip backup").performScrollTo().performClick()
                ui.onNodeWithContentDescription("Replacement risks acknowledged").performScrollTo().performClick()
                ui.onNodeWithText("Confirm replacement").performScrollTo().performClick()
                ui.waitUntil(15_000) { ui.onAllNodes(hasText("Wallet imported")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                assertEquals("jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR", displayedWalletAddress(ui))
                select(device, originalFile)
                ui.waitUntil(15_000) { ui.onAllNodes(hasText("Other assets could not be checked")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                ui.onNodeWithText("Other assets could not be checked").assertExists()
                ui.onNodeWithText("Saved name: reference").assertExists()
                ui.onNodeWithText("Pending work belongs to your current wallet").assertExists()
                ui.onNodeWithText("Skip backup").assertDoesNotExist()
                ui.onNodeWithContentDescription("Replacement risks acknowledged").performScrollTo().performClick()
                ui.onNodeWithText("Confirm replacement").performScrollTo().assertIsNotEnabled()
                captureFeedEvidence("wallet-risk-evidence/other-assets-require-backup.png")
                ui.onNodeWithText("Back up current wallet").performScrollTo().performClick()
                chooseTestBackup(device, currentBackup)
                enterRealSystemPin(device)
                ui.waitUntil(15_000) { ui.onAllNodes(hasText("Current wallet backed up for this replacement")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                ui.onNodeWithText("Confirm replacement").performScrollTo().assertIsEnabled().performClick()
                ui.waitUntil(15_000) { ui.onAllNodes(hasText("Wallet imported")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                assertEquals(originalAddress, displayedWalletAddress(ui))
                captureFeedEvidence("wallet-risk-evidence/original-wallet-restored.png")
            }
        } finally {
            for (name in listOf(originalFile, currentBackup)) runCatching {
                DocumentsContract.deleteDocument(context.contentResolver,
                    DocumentsContract.buildDocumentUri(ExportTestDocumentsProvider.AUTHORITY, name))
            }
            device.executeShellCommand("locksettings clear --old $EXPORT_TEST_PIN --user 0")
            server.close()
        }
    }

    private fun select(device: UiDevice, name: String) {
        ui.onNodeWithText("Import wallet").performScrollTo().performClick()
        val row = By.text(ExportTestDocumentsProvider.TITLE).res("android", "title")
        val title = By.text(ExportTestDocumentsProvider.TITLE)
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (!device.hasObject(row) && device.hasObject(title)) break
            try {
                val entry = device.findObject(row)
                if (entry != null) { entry.click(); device.wait(Until.gone(row), 2_000) }
                else device.findObject(By.desc("Show roots"))?.click()
            } catch (_: StaleObjectException) { /* Reobserve the real picker. */ }
            SystemClock.sleep(100)
        }
        val selector = By.text(name)
        if (device.wait(Until.hasObject(selector), 2_000) != true) {
            try { UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().text(name)) }
            catch (_: UiObjectNotFoundException) { /* Retain the actual picker if unavailable. */ }
        }
        val file = device.wait(Until.findObject(selector), 10_000)
        if (file == null) captureFeedEvidence("wallet-risk-evidence/file-selection-failure.png")
        requireNotNull(file).click()
    }
}
