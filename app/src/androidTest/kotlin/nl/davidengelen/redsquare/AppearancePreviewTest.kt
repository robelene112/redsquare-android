package nl.davidengelen.redsquare

import android.content.res.Configuration
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
        val originalNight = device.executeShellCommand("cmd uimode night").trim().removePrefix("Night mode: ")
        check(originalNight in listOf("yes", "no", "auto", "custom_schedule", "custom_bedtime")) {
            "Unsupported original night mode; no system setting changed"
        }
        val controls = File(context.cacheDir, "appearance-evidence/system-controls.txt")
        check(controls.parentFile!!.isDirectory || controls.parentFile!!.mkdirs())
        controls.writeText("Original night mode: $originalNight\n")
        controls.appendText(device.executeShellCommand("id") + "\n")
        controls.appendText(device.executeShellCommand("dumpsys uimode") + "\n")
        // The guarded AOSP Android 10 image locks night mode, but its Shell lacks MODIFY_DAY_NIGHT_MODE.
        // Root changes only this emulator's system setting; the app still runs with its ordinary UID.
        val nightCommand = if (Build.VERSION.SDK_INT == 29) "su 0 cmd uimode night" else "cmd uimode night"
        if (Build.VERSION.SDK_INT == 29) controls.appendText(device.executeShellCommand("su 0 id") + "\n")
        fun night(value: String, awaitConfiguration: Boolean = true) {
            val result = device.executeShellCommand("$nightCommand $value").trim()
            controls.appendText("Requested $value; $result\n")
            if (result != "Night mode: $value") {
                controls.appendText(device.executeShellCommand("dumpsys uimode") + "\n")
                controls.appendText(device.executeShellCommand("logcat -d -s UiModeManager:I") + "\n")
            }
            assertEquals("Night mode: $value", result)
            if (awaitConfiguration && value in listOf("yes", "no")) {
                val expected = if (value == "yes") Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                ui.waitUntil(5_000) { context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == expected }
                controls.appendText("Applied configuration: $expected\n")
            }
        }
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        val root = instrumentation.context.assets.open("native-feed-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow")
        }
        val peer = ControlledFeedPeer("[$root]")
        val font = EmulatorFontScale(context, File(context.cacheDir, "appearance-evidence"), "settings-130")
        try {
            font.apply(1.3f)
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
                            assertTrue("Appearance must preserve the full wallet and portable options",
                                originalWallet == walletStore.loadExisting().exportJson())
                            assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
                            assertTrue(peer.transactions.isEmpty()); assertTrue(peer.reports.isEmpty()); peer.assertHealthy()
                        }
                        fun choose(label: String) {
                            ui.onNodeWithText("Settings").performClick()
                            ui.onNodeWithText("Privacy and safety").assertIsDisplayed()
                            ui.onNodeWithText("Node").assertIsDisplayed()
                            ui.onNodeWithText("Notifications and privacy").assertIsDisplayed()
                            ui.onNodeWithText("Appearance").assertIsDisplayed()
                            capture("settings-overview-${label.lowercase().replace(' ', '-')}.png")
                            ui.onNodeWithText("Appearance").performScrollTo().performClick()
                            ui.waitUntil(5_000) { ui.onAllNodesWithText("Appearance").fetchSemanticsNodes().isNotEmpty() }
                            capture("appearance-detail-${label.lowercase().replace(' ', '-')}.png")
                            ui.onNodeWithText(label).performScrollTo().performClick()
                            ui.waitUntil(5_000) {
                                ui.onAllNodes(hasText(label) and isSelected()).fetchSemanticsNodes().isNotEmpty()
                            }
                            ui.onNodeWithText(label).assertIsSelected()
                            capture("appearance-selected-${label.lowercase().replace(' ', '-')}.png")
                            ui.onNodeWithText("Back to Settings").performClick()

                            ui.onNodeWithText("Privacy and safety").performScrollTo().performClick()
                            ui.onNodeWithText("Personal controls").assertIsDisplayed()
                            ui.onNodeWithText("Saito username").assertIsDisplayed()
                            capture("privacy-detail-${label.lowercase().replace(' ', '-')}.png")
                            ui.onNodeWithText("Back to Settings").performClick()

                            ui.onNodeWithText("Node").performScrollTo().performClick()
                            ui.onNodeWithText("Each node has its own available history and moderation. Your wallet stays the same.")
                                .performScrollTo().assertIsDisplayed()
                            ui.onNode(hasText(SaitoBackend.OWNER.domain) and isSelected()).assertIsDisplayed()
                            capture("node-detail-${label.lowercase().replace(' ', '-')}.png")
                            val blockedSelection = File(context.noBackupFilesDir, "backend.pending")
                            assertFalse("No unresolved selection should exist before the controlled failure", blockedSelection.exists())
                            assertTrue("Create an isolated backend-store failure", blockedSelection.mkdir())
                            withImageFixtureCleanup({
                                check(blockedSelection.delete()) { "Remove the isolated backend-store failure" }
                            }) {
                                ui.onNodeWithText(SaitoBackend.OFFICIAL.domain).performScrollTo().performClick()
                                ui.waitUntil(10_000) {
                                    ui.onAllNodesWithText("Your node selection could not be saved. Try again.")
                                        .fetchSemanticsNodes().isNotEmpty()
                                }
                                ui.onNode(hasText(SaitoBackend.OWNER.domain) and isSelected()).assertIsDisplayed()
                                capture("node-save-error-${label.lowercase().replace(' ', '-')}.png")
                            }
                            unchanged()
                            ui.onNodeWithText("Back to Settings").performClick()

                            ui.onNodeWithText("Notifications and privacy").performScrollTo().performClick()
                            ui.onNodeWithText("Notifications and privacy").assertIsDisplayed()
                            capture("notifications-detail-${label.lowercase().replace(' ', '-')}.png")
                            ui.onNodeWithText("Back to Settings").performClick()

                            ui.onNodeWithText("About").performClick()
                            ui.onNodeWithText("A native Android client for RedSquare on Saito.")
                                .performScrollTo()
                            ui.onNodeWithText("Independently developed by Angels’ Foundry.").performScrollTo()
                            ui.onNodeWithText("Version ", substring = true).performScrollTo()
                            capture("about-${label.lowercase().replace(' ', '-')}.png")
                            ui.onNodeWithText("Done").performClick()
                        }
                        // The harness starts with a fresh diagnostic installation. Its default must follow Android.
                        waitForSurface(true); unchanged(); capture("default-system-dark.png")
                        choose("Light")
                        waitForSurface(false); unchanged(); capture("light.png")
                        activity.recreate()
                        waitForSurface(false); unchanged(); capture("light-reopened.png")
                        choose("Dark")
                        waitForSurface(true); unchanged(); capture("dark.png")
                        night("no")
                        waitForSurface(true); unchanged(); capture("dark-system-light.png")
                        activity.recreate()
                        waitForSurface(true); unchanged(); capture("dark-reopened.png")
                        night("yes")
                        choose("System default")
                        waitForSurface(true); unchanged(); capture("system-dark.png")
                        night("no")
                        waitForSurface(false); unchanged(); capture("system-light.png")
                    }
                }
            }
        } finally {
            font.restore()
            night(originalNight, awaitConfiguration = false)
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
