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
import java.util.Base64
import java.util.regex.Pattern

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
        val bytes = AccountRecoveryBackup.seal(wallet, listOf(archive), phrase.toCharArray())
        val encoded = Base64.getEncoder().encodeToString(bytes)
        val filename = "00-protected-recovery.saito"
        val testPackage = instrumentation.context.packageName
        val destination = "files/wallet-test-documents/$filename"
        assertTrue(device.executeShellCommand("run-as $testPackage sh -c 'mkdir -p files/wallet-test-documents && echo $encoded | base64 -d > $destination'").isBlank())
        val evidence = File(context.cacheDir, "protected-recovery-evidence").apply { mkdirs() }
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).putExtra("feedDisabled", true)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:1/wsopen").putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:1/wsopen")
                .putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/")).use {
                withFailureCapture({ captureNativeScreen(File(evidence, "failure.png")) }) {
                    ui.waitUntil(15_000) { ui.onNodeWithText("Home").isDisplayed() }
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
            bytes.fill(0)
            device.executeShellCommand("run-as $testPackage rm $destination")
        }
    }

    private fun pickAndReachUnlock(device: UiDevice, filename: String) {
        val row = By.text(ExportTestDocumentsProvider.TITLE).res("android", "title")
        val drawer = By.res(Pattern.compile(".*:id/roots_list"))
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                if (device.currentPackageName == context.packageName &&
                    ui.onNode(hasText("Unlock backup") and hasClickAction()).isDisplayed()) return
                val opened = device.findObject(drawer)
                if (opened != null) {
                    opened.findObject(row)?.click()
                    device.wait(Until.gone(drawer), 2_000)
                } else {
                    val file = device.findObject(By.text(filename).res("android", "title"))
                    if (file != null) file.click()
                    else device.findObject(By.desc("Show roots"))?.click()
                }
            } catch (_: StaleObjectException) { /* Reobserve the actual picker. */ }
            SystemClock.sleep(100)
        }
        error("Protected backup passphrase control did not appear after the file picker")
    }
}
