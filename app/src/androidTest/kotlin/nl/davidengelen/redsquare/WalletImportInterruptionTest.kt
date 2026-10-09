package nl.davidengelen.redsquare

import android.content.Intent
import android.os.Process
import android.system.Os
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsEnabled
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.saito.SaitoWallet
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Real process termination at the protected-store boundary; host runs phases separately. */
class WalletImportInterruptionTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun terminateAfterReplacementWriteBeforeRecoveryCommit() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("nl.davidengelen.redsquare.dev", context.packageName)
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).putExtra("feedDisabled", true))
        ui.onNodeWithText("Wallet").performClick()
        val address = displayedWalletAddress(ui)
        val previous = AndroidWalletStore(context).loadExisting()
        assertEquals(address, previous.publicKey)
        val syncs = AtomicInteger()
        val storage = AndroidWalletStore(context) { descriptor ->
            Os.fsync(descriptor)
            if (syncs.incrementAndGet() == 2) {
                // Recovery record has been verified, replacement file synced,
                // and commit has not removed the authoritative outgoing record.
                File(context.cacheDir, "wallet-import-interruption-state.txt").outputStream().use {
                    it.write("$address\n".toByteArray())
                    it.fd.sync()
                }
                Process.killProcess(Process.myPid())
                error("Expected test process termination")
            }
        }
        storage.replace(previous, SaitoWallet.fromPrivateKey("0".repeat(63) + "1"))
        error("Expected test process termination before commit")
    }

    @Test fun coldStartupRecoversTheOutgoingPublishedIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("nl.davidengelen.redsquare.dev", context.packageName)
        val address = File(context.cacheDir, "wallet-import-interruption-state.txt").readText().trim()
        check(address.matches(Regex("[1-9A-HJ-NP-Za-km-z]{44}")))
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .putExtra("feedDisabled", true)).use {
            ui.onNodeWithText("Wallet").performClick()
            assertEquals(address, displayedWalletAddress(ui))
            ui.onNodeWithText("Import wallet").assertIsEnabled()
            ui.onNodeWithText("Wallet imported").assertDoesNotExist()
            captureFeedEvidence("wallet-import-process-evidence/cold-recovery.png")
        }
    }
}
