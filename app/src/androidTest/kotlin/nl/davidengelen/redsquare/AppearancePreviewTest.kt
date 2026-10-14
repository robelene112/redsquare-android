package nl.davidengelen.redsquare

import android.app.UiModeManager
import android.content.Intent
import android.os.Build
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Observable appearance changes, with the real wallet and selection unchanged. */
class AppearancePreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun systemAndExplicitThemesRemainReadableAfterReopeningWithoutReplacingIdentity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
        val device = UiDevice.getInstance(instrumentation)
        val originalNight = context.getSystemService(UiModeManager::class.java).nightMode
        fun night(value: String) { device.executeShellCommand("cmd uimode night $value") }
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        val root = instrumentation.context.assets.open("native-feed-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow")
        }
        val peer = ControlledFeedPeer("[$root]")
        try {
            night("yes")
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { activity ->
                    withFailureCapture({ capture("appearance-first-failure.png") }) {
                        ui.waitUntil(20_000) {
                            ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
                        }
                        val walletStore = AndroidWalletStore(context)
                        val originalWallet = walletStore.loadExisting().exportJson() // Device-only comparison, never captured.
                        fun unchanged() {
                            assertEquals(originalWallet, walletStore.loadExisting().exportJson())
                            assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
                            assertTrue(peer.transactions.isEmpty()); assertTrue(peer.reports.isEmpty()); peer.assertHealthy()
                        }
                        fun choose(label: String) {
                            ui.onNodeWithText("Settings").performClick()
                            ui.waitUntil(5_000) { ui.onAllNodesWithText("Appearance").fetchSemanticsNodes().isNotEmpty() }
                            ui.onNodeWithText(label).performScrollTo().performClick()
                            ui.onNodeWithText("Done").performClick()
                        }
                        choose("Light")
                        waitForSurface(false); unchanged(); capture("light.png")
                        activity.recreate()
                        waitForSurface(false); unchanged(); capture("light-reopened.png")
                        choose("Dark")
                        waitForSurface(true); unchanged(); capture("dark.png")
                        choose("System default")
                        waitForSurface(true); unchanged(); capture("system-dark.png")
                        night("no")
                        waitForSurface(false); unchanged(); capture("system-light.png")
                    }
                }
            }
        } finally {
            night(when (originalNight) {
                UiModeManager.MODE_NIGHT_YES -> "yes"
                UiModeManager.MODE_NIGHT_NO -> "no"
                else -> "auto"
            })
        }
    }

    private fun waitForSurface(dark: Boolean) {
        ui.waitUntil(10_000) {
            // Sample the app's empty left margin, away from content and system bars.
            val bitmap = ui.onRoot().captureToImage().asAndroidBitmap()
            val color = bitmap.getPixel(2, 32)
            val brightness = (android.graphics.Color.red(color) + android.graphics.Color.green(color) +
                android.graphics.Color.blue(color)) / 3
            if (dark) brightness < 70 else brightness > 200
        }
    }

    private fun capture(name: String) = captureNativeScreen(File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "appearance-evidence/$name"))
}
