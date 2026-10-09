package nl.davidengelen.redsquare

import android.app.KeyguardManager
import android.content.Intent
import android.provider.DocumentsContract
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.SaitoWallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.regex.Pattern
import java.io.File

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
        device.executeShellCommand("locksettings set-pin --user 0 $TEST_PIN")
        try {
            ui.waitUntil(10_000) { keyguard.isDeviceSecure }
            check(device.executeShellCommand("locksettings verify --old $TEST_PIN --user 0")
                .contains("Lock credential verified successfully"))
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedDisabled", true)).use {
                try {
                ui.onNodeWithText("Wallet").performClick()
                val address = displayedWalletAddress(ui)
                ui.onNodeWithText("Export wallet").performClick()
                check(device.wait(Until.hasObject(By.res("android", "title")), 15_000))
                if (device.findObject(By.desc("Show roots")) != null) {
                    operateSystemControl(device, By.desc("Show roots")) { click() }
                }
                operateSystemControl(device, By.text(ExportTestDocumentsProvider.TITLE)) { click() }
                operateSystemControl(device, By.res("android", "title").clazz("android.widget.EditText")) { text = name }
                operateSystemControl(device, By.res("android", "button1")
                    .pkg(Pattern.compile("com\\.(?:android|google\\.android)\\.documentsui")).enabled(true)) { click() }
                enterRealSystemPin(device)
                ui.waitUntil(15_000) { ui.onAllNodes(hasText("Wallet backup saved")).fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText("Wallet backup saved").assertIsDisplayed()
                val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use {
                    val content = it.readBytes()
                    check(content.size in 1..SaitoWallet.MAX_BACKUP_BYTES)
                    content
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
            device.executeShellCommand("locksettings clear --old $TEST_PIN --user 0")
        }
    }

    @Test fun wrongPinAndCancelledAuthenticationLeaveNoBackupAndRetainIdentity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val device = UiDevice.getInstance(instrumentation)
        assertFalse(keyguard.isDeviceSecure)
        device.executeShellCommand("locksettings set-pin --user 0 $TEST_PIN")
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
                val deadline = SystemClock.uptimeMillis() + 10_000
                while (device.currentPackageName != context.packageName && SystemClock.uptimeMillis() < deadline) {
                    device.pressBack()
                    SystemClock.sleep(100)
                }
                val cancelled = "Wallet export canceled. Your wallet is unchanged."
                ui.waitUntil(10_000) { ui.onAllNodes(hasText(cancelled)).fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText(cancelled).assertIsDisplayed()
                ui.onNodeWithText("Wallet backup saved").assertDoesNotExist()
                ui.onNodeWithText("Export wallet").assertIsEnabled()
                assertEquals(address, displayedWalletAddress(ui))
                captureFeedEvidence("wallet-export-evidence/authentication-cancelled.png")
            }
        } finally { device.executeShellCommand("locksettings clear --old $TEST_PIN --user 0") }
    }

    private fun chooseTestBackup(device: UiDevice, name: String) {
        check(device.wait(Until.hasObject(By.res("android", "title")), 15_000))
        if (device.findObject(By.desc("Show roots")) != null) {
            operateSystemControl(device, By.desc("Show roots")) { click() }
        }
        operateSystemControl(device, By.text(ExportTestDocumentsProvider.TITLE)) { click() }
        operateSystemControl(device, By.res("android", "title").clazz("android.widget.EditText")) { text = name }
        operateSystemControl(device, By.res("android", "button1")
            .pkg(Pattern.compile("com\\.(?:android|google\\.android)\\.documentsui")).enabled(true)) { click() }
    }

    private fun enterRealSystemPin(device: UiDevice, pin: String = TEST_PIN) {
        val credential = By.res(Pattern.compile("(?:com\\.android\\.settings:id/password_entry|com\\.android\\.systemui:id/lockPassword)"))
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            val entry = device.findObject(credential)
                ?: device.findObject(By.pkg("com.android.systemui").clazz("android.widget.EditText"))
            if (entry != null) { entry.text = pin; device.pressEnter(); return }
            device.findObject(By.text("Use PIN"))?.click()
            if (device.currentPackageName == "com.android.systemui") {
                saveCredentialControlMetadata()
                // New system credential surfaces can omit legacy View IDs.
                // Hardware digit events still go to Android's focused input.
                pin.forEach { device.pressKeyCode(KeyEvent.KEYCODE_0 + (it - '0')) }
                device.pressEnter()
                return
            }
            SystemClock.sleep(100)
        }
        error("Credential entry unavailable in ${device.currentPackageName}")
    }

    private fun operateSystemControl(device: UiDevice, selector: BySelector, action: UiObject2.() -> Unit) {
        val deadline = SystemClock.uptimeMillis() + 20_000
        while (SystemClock.uptimeMillis() < deadline) {
            val control = device.wait(Until.findObject(selector), 1_000) ?: continue
            try { control.action(); return }
            catch (_: StaleObjectException) { /* Query a fresh node after system layout changes. */ }
        }
        error("Android system control unavailable")
    }

    private fun saveCredentialControlMetadata() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val publicControls = StringBuilder()
        var remaining = 500
        fun visit(node: AccessibilityNodeInfo) {
            if (remaining-- <= 0) return
            if (node.packageName?.toString() == "com.android.systemui") {
                publicControls.append(node.className).append(" id=").append(node.viewIdResourceName)
                    .append(" editable=").append(node.isEditable).append(" password=").append(node.isPassword)
                    .append(" focused=").append(node.isFocused).append('\n')
            }
            for (index in 0 until node.childCount) node.getChild(index)?.let(::visit)
        }
        instrumentation.uiAutomation.rootInActiveWindow?.let(::visit)
        val output = File(instrumentation.targetContext.cacheDir, "wallet-export-evidence/credential-controls.txt")
        output.parentFile!!.mkdirs()
        output.writeText(publicControls.toString())
    }

    companion object { private const val TEST_PIN = "246813" }
}
