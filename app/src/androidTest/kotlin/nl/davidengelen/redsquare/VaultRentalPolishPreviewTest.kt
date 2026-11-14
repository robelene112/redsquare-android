package nl.davidengelen.redsquare

import android.os.Debug
import android.os.SystemClock
import androidx.lifecycle.lifecycleScope
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
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            val loaded = scope.async { library.load() }
            try { ui.waitUntil(15_000) { loaded.isCompleted }; runBlocking { loaded.await() } }
            finally { if (!loaded.isCompleted) runBlocking { loaded.cancelAndJoin() } }
            assertTrue(library.entries.single().creatorMaster)
            val start = SystemClock.uptimeMillis()
            Debug.startMethodTracingSampling(File(scene.evidence, "creator-discovery.trace").absolutePath, 16 * 1024 * 1024, 100)
            val refreshed = scope.async { repeat(20) { library.refreshLocal() } }
            try { ui.waitUntil(15_000) { refreshed.isCompleted }; runBlocking { refreshed.await() } }
            finally { Debug.stopMethodTracing(); scope.cancel(); runBlocking { refreshed.cancelAndJoin() } }
            assertTrue(library.entries.single().creatorMaster)
            assertEquals("Upload notes.txt", library.entries.single().name)
            File(scene.evidence, "creator-refresh-observation.json").writeText(com.google.gson.JsonObject().apply {
                addProperty("refreshes", 20); addProperty("elapsedMillis", SystemClock.uptimeMillis() - start)
                addProperty("authenticatedMasterRetained", true)
                addProperty("qualification", "Instrumented emulator method trace; no physical frame-rate claim")
            }.toString())
        }
    }
    @Test fun nativeRentalDetailsOpenAndCloseAtNormalAndReducedMotion() {
        val device = androidx.test.uiautomator.UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
        for (scale in listOf(0, 1)) withNativeModuleMotionScale(scale) {
            withVaultReadingFixture("rental-motion-$scale", creatorRental = true) { scene ->
                ui.openNativeVault()
                ui.waitUntil(15_000) { ui.onNodeWithText("Upload notes.txt").isDisplayed() }
                observeModuleWindowMotionScale(scene.activity, scale, scene.evidence)
                fun movie(name: String, action: () -> Unit) {
                    val file = File(scene.evidence, "rental-scale-$scale-$name.mp4")
                    val recording = recordNativeTransition(file, recordingSeconds = 5, dismiss = action)
                    val observed = retainNativeMotionFrames(file)
                    observed.add("recording", recording)
                    File(scene.evidence, "rental-scale-$scale-$name.json").writeText(observed.toString())
                }
                movie("open") {
                    requireNotNull(device.findObject(androidx.test.uiautomator.By.text("Rental details"))).click()
                    check(device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.text("Done")), 5_000))
                }
                movie("close") {
                    requireNotNull(device.findObject(androidx.test.uiautomator.By.text("Done"))).click()
                    check(device.wait(androidx.test.uiautomator.Until.gone(androidx.test.uiautomator.By.text("Done")), 5_000))
                }
                ui.onNodeWithText("Rental details").assertIsDisplayed()
                assertTrue(scene.peer.transactions.isEmpty()); assertTrue(scene.peer.vaultUploads.isEmpty())
            }
        }
    }

}
