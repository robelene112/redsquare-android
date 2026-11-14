package nl.davidengelen.redsquare

import android.os.Debug
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import kotlinx.coroutines.*
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.vault.VaultLibrary
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Actual native library discovery and repeated local refresh, with a bounded Android method trace. */
class VaultRentalPolishPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun creatorMasterRefreshKeepsItsAuthenticatedEntryAndRecordsActualMainThreadWork() {
        withVaultReadingFixture("rental-discovery-profile", creatorRental = true) { scene ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }; ui.onNodeWithText("Later").performClick()
            lateinit var library: VaultLibrary
            scene.activity.onActivity { screen ->
                library = VaultLibrary(scene.context, AndroidWalletStore(scene.context).loadExisting(), SaitoBackend.OWNER,
                    requireNotNull(screen.intent.getStringExtra("feedPeerUrl")), WalletBalances.fromIntent(screen.intent))
            }
            runBlocking { withContext(Dispatchers.Main) { withTimeout(15_000) { library.load() } } }
            assertTrue(library.entries.single().creatorMaster)
            val start = SystemClock.uptimeMillis()
            Debug.startMethodTracing(File(scene.evidence, "creator-discovery.trace").absolutePath, 16 * 1024 * 1024)
            try {
                runBlocking { withContext(Dispatchers.Main) { withTimeout(15_000) { repeat(20) { library.refreshLocal() } } } }
            } finally { Debug.stopMethodTracing() }
            assertTrue(library.entries.single().creatorMaster)
            assertEquals("Upload notes.txt", library.entries.single().name)
            File(scene.evidence, "creator-refresh-observation.json").writeText(com.google.gson.JsonObject().apply {
                addProperty("refreshes", 20); addProperty("elapsedMillis", SystemClock.uptimeMillis() - start)
                addProperty("authenticatedMasterRetained", true)
                addProperty("qualification", "Instrumented emulator method trace; no physical frame-rate claim")
            }.toString())
        }
    }
}
