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
                device.findObject(By.desc("Show roots"))?.click()
                val root = requireNotNull(device.wait(Until.findObject(By.text(ExportTestDocumentsProvider.TITLE)), 10_000))
                root.click()
                val filename = requireNotNull(device.wait(Until.findObject(By.res("android", "title")
                    .clazz("android.widget.EditText")), 10_000))
                filename.text = name
                requireNotNull(device.wait(Until.findObject(By.res("android", "button1")), 10_000)).click()
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

    private fun enterRealSystemPin(device: UiDevice) {
        val credential = By.res(Pattern.compile("(?:com\\.android\\.settings:id/password_entry|com\\.android\\.systemui:id/lockPassword)"))
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            val entry = device.findObject(credential)
                ?: device.findObject(By.pkg("com.android.systemui").clazz("android.widget.EditText"))
            if (entry != null) { entry.text = TEST_PIN; device.pressEnter(); return }
            device.findObject(By.text("Use PIN"))?.click()
            if (device.currentPackageName == "com.android.systemui") {
                saveCredentialControlMetadata()
                // New system credential surfaces can omit legacy View IDs.
                // Hardware digit events still go to Android's focused input.
                TEST_PIN.forEach { device.pressKeyCode(KeyEvent.KEYCODE_0 + (it - '0')) }
                device.pressEnter()
                return
            }
            SystemClock.sleep(100)
        }
        error("Credential entry unavailable in ${device.currentPackageName}")
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
