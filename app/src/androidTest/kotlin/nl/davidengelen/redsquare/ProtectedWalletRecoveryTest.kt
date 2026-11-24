package nl.davidengelen.redsquare

import android.content.Intent
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.AccountRecoveryBackup
import nl.davidengelen.redsquare.spaces.SpaceCrypto
import nl.davidengelen.redsquare.spaces.SpaceHistoryKeys
import nl.davidengelen.redsquare.spaces.retainedSpaceArchives
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.regex.Pattern
import android.provider.DocumentsContract
import android.app.KeyguardManager
import nl.davidengelen.redsquare.spaces.AndroidSpaceClientStore
import nl.davidengelen.redsquare.spaces.AndroidSpacesRecovery

class ProtectedWalletRecoveryTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun actualFilePickerUnlocksProtectedHistoryOnlyAfterExplicitReview() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val device = UiDevice.getInstance(instrumentation)
        val wallet = AndroidWalletStore(context).loadOrCreate()
        val service = SaitoWallet.create()
        val scope = SpaceCrypto.Scope(service.publicKey, "a".repeat(64), "b".repeat(64))
        val original = SpaceCrypto.device(wallet.publicKey, "c".repeat(64)).create(scope)
        val message = original.publish(wallet, "message", "d".repeat(64), "e".repeat(128), "Recovered native history", 1)
        val archive = SpaceHistoryKeys.restore(original.historyBackup(), wallet.publicKey)
        val phrase = "isolated device recovery fixture"
        val filename = "protected-recovery-${java.util.UUID.randomUUID()}.saito"
        val uri = DocumentsContract.buildDocumentUri(ExportTestDocumentsProvider.AUTHORITY, filename)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        assertFalse(keyguard.isDeviceSecure)
        val intent = Intent(context, MainActivity::class.java).putExtra("feedDisabled", true)
            .putExtra("feedPeerUrl", "ws://127.0.0.1:1/wsopen").putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:1/wsopen")
            .putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/")
        val evidence = File(context.cacheDir, "protected-recovery-evidence").apply { mkdirs() }
        AndroidSpacesRecovery(context) { error("The recovery fixture uses only locally staged archives") }.retain(wallet, listOf(archive))
        device.executeShellCommand("locksettings set-pin --user 0 $EXPORT_TEST_PIN")
        try {
            ui.waitUntil(10_000) { keyguard.isDeviceSecure }
            ActivityScenario.launch<MainActivity>(intent).use {
                withFailureCapture({ captureNativeScreen(File(evidence, "export-failure.png")) }) {
                    ui.waitForAccountMenu(15_000)
                    ui.openWallet()
                    ui.onNode(hasText("Back up identity and history") and hasClickAction()).performScrollTo().performClick()
                    ui.onNode(hasSetTextAction() and hasText("Passphrase")).performTextInput(phrase)
                    ui.onNode(hasSetTextAction() and hasText("Repeat passphrase")).performTextInput(phrase)
                    androidx.test.espresso.Espresso.closeSoftKeyboard()
                    ui.onNode(hasText("Choose file") and hasClickAction()).assertIsEnabled().performClick()
                    chooseTestBackup(device, filename)
                    enterRealSystemPin(device)
                    ui.waitUntil(15_000) { ui.onNodeWithText("Wallet backup saved").isDisplayed() }
                    val saved = requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytes() }
                    try {
                        assertTrue(AccountRecoveryBackup.recognizes(saved))
                        val decoded = AccountRecoveryBackup.open(saved, phrase.toCharArray())
                        assertEquals(wallet.publicKey, decoded.wallet.publicKey)
                        assertEquals("Recovered native history", decoded.history.single { key -> key.scope == scope }.read(message.envelope()).text)
                    } finally { saved.fill(0) }
                    captureNativeScreen(File(evidence, "00-verified-protected-export.png"), "Wallet backup saved")
                }
            }
            AndroidSpaceClientStore(context, wallet.publicKey, "local-recovery", wallet.publicKey).use { store ->
                store.transaction {
                    val key = "archive/${scope.serviceKey}/${scope.spaceId}/${scope.channelId}/${scope.generation}"
                    require(get(key) != null)
                    remove(key)
                    val count = requireNotNull(get("archive-count")).decodeToString().toInt()
                    put("archive-count", (count - 1).toString().encodeToByteArray())
                }
            }
            ActivityScenario.launch<MainActivity>(intent).use {
                withFailureCapture({ captureNativeScreen(File(evidence, "failure.png")) }) {
                    ui.waitForAccountMenu(15_000)
                    ui.openWallet()
                    assertEquals(wallet.publicKey, displayedWalletAddress(ui))
                    ui.onNode(hasText("Import wallet") and hasClickAction()).performScrollTo().assertIsEnabled().performClick()
                    pickAndReachUnlock(device, filename)
                    val field = ui.onNode(hasSetTextAction() and hasAnyAncestor(isDialog()))
                    val confirm = ui.onNode(hasText("Unlock backup") and hasClickAction())
                    field.performTextInput("wrong fixture passphrase")
                    confirm.assertIsEnabled().performClick()
                    ui.waitUntil(10_000) { ui.onNodeWithText("Could not unlock this backup. Check the passphrase and try again.").isDisplayed() }
                    assertTrue(retainedSpaceArchives(context, wallet.publicKey, service.publicKey).isEmpty())
                    field.performTextReplacement(phrase)
                    confirm.assertIsEnabled().performClick()
                    val historyHint = context.getString(R.string.wallet_protected_history_available)
                    ui.waitUntil(10_000) { ui.onAllNodesWithText(historyHint).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText(historyHint).performScrollTo().assertIsDisplayed()
                    captureNativeScreen(File(evidence, "01-reviewed-archive.png"), historyHint)
                    assertTrue(retainedSpaceArchives(context, wallet.publicKey, service.publicKey).isEmpty())
                    val finish = ui.onNode(hasText("Confirm restore") and hasClickAction())
                    finish.performScrollTo().assertIsNotEnabled()
                    ui.onNodeWithContentDescription("Replacement risks acknowledged").performScrollTo().performClick()
                    finish.performScrollTo().assertIsEnabled().performClick()
                    ui.waitUntil(15_000) { ui.onNodeWithText("Wallet imported").isDisplayed() }
                    assertEquals(wallet.publicKey, AndroidWalletStore(context).loadExisting().publicKey)
                    val keys = retainedSpaceArchives(context, wallet.publicKey, service.publicKey).single()
                    assertEquals(scope, keys.scope)
                    assertEquals("Recovered native history", keys.read(message.envelope()).text)
                    captureNativeScreen(File(evidence, "02-retained-archive.png"), "Wallet imported")
                }
            }
        } finally {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            device.executeShellCommand("locksettings clear --old $EXPORT_TEST_PIN --user 0")
        }
    }

    private fun pickAndReachUnlock(device: UiDevice, filename: String) {
        val row = By.text(ExportTestDocumentsProvider.TITLE).res("android", "title")
        val drawer = By.res(Pattern.compile(".*:id/roots_list"))
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                if (device.currentPackageName == context.packageName &&
                    ui.onAllNodes(hasText("Unlock backup") and hasClickAction()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()) return
                val opened = device.findObject(drawer)
                if (opened != null) {
                    if (opened.findObject(row) != null) clickTestBackupRoot()
                    device.wait(Until.gone(drawer), 2_000)
                } else {
                    val file = device.findObject(By.text(filename))
                    if (file != null) file.click()
                    else device.findObject(By.desc("Show roots"))?.click()
                }
            } catch (_: StaleObjectException) { /* Reobserve the actual picker. */ }
            SystemClock.sleep(100)
        }
        error("Protected backup passphrase control did not appear after the file picker")
    }
}
