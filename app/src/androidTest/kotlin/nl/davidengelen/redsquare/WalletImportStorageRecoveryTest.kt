package nl.davidengelen.redsquare

import android.content.Intent
import android.system.Os
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import nl.davidengelen.redsquare.saito.SaitoWallet

/** Existing public persistence operation, real Keystore/AtomicFile, fault only at OS sync. */
class WalletImportStorageRecoveryTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun failedDirectorySyncAfterReplacementWriteRestoresPublishedWalletOptions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("nl.davidengelen.redsquare.dev", context.packageName)
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .putExtra("feedDisabled", true)).use { scenario ->
            ui.onNodeWithText("Wallet").performClick()
            val address = displayedWalletAddress(ui)
            val previous = AndroidWalletStore(context).loadExisting()
            assertEquals(address, previous.publicKey)
            val syncs = AtomicInteger()
            val storage = AndroidWalletStore(context) { descriptor ->
                if (syncs.incrementAndGet() == 2) throw IOException("Controlled replacement directory sync failure")
                Os.fsync(descriptor)
            }
            // Published unsafe scalar1 is solely an isolated replacement fixture, never fund it.
            assertThrows(IllegalStateException::class.java) {
                storage.replace(previous, SaitoWallet.fromPrivateKey("0".repeat(63) + "1"))
            }
            assertTrue("The controlled directory-sync failure must be reached", syncs.get() >= 2)
            val recovered = AndroidWalletStore(context).loadExisting()
            assertEquals(address, recovered.publicKey)
            assertTrue("Recovered options must equal the outgoing options",
                previous.exportJson() == recovered.exportJson())
            ui.onNodeWithText("Done").performClick()
            scenario.recreate()
            ui.onNodeWithText("Wallet").performClick()
            assertEquals(address, displayedWalletAddress(ui))
            captureFeedEvidence("wallet-storage-recovery-evidence/failed-save-recreated.png")
        }
    }
}
