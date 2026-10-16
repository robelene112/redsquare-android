package nl.davidengelen.redsquare

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Host invokes each OS choice separately, revoking between processes on API33+. */
class NotificationPermissionPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun allowingAlertsKeepsInAppReadingOptional() = chooseAlerts(true)
    @Test fun denyingAlertsKeepsInAppReadingAvailable() = chooseAlerts(false)

    private fun chooseAlerts(allow: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(Build.HARDWARE in listOf("ranchu", "goldfish"))
        check(Build.VERSION.SDK_INT >= 33 || allow)
        val store = AndroidWalletStore(context)
        val wallet = store.loadOrCreate()
        val original = wallet.exportJson()
        val reads = AtomicInteger()
        val preferences = context.getSharedPreferences("notification-permission", 0)
        val retained = ChatFixtureState(context, listOf("socialpush.${wallet.publicKey}."), wallet.publicKey)
        val peer = ControlledFeedPeer("[]", archiveResponse = { criteria ->
            if (criteria.get("field1").asString == "RedSquareLike") {
                check(criteria.get("field3").asString == wallet.publicKey)
                reads.incrementAndGet()
            }
            "[]"
        }, quoteArchiveResponse = { "[]" })
        var primary: Throwable? = null
        try {
            retained.prepare()
            check(preferences.edit().remove("offered").commit())
            if (Build.VERSION.SDK_INT >= 33) assertEquals(PackageManager.PERMISSION_DENIED,
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { scenario ->
                    withFailureCapture({ captureNativeScreen(File(context.cacheDir,
                        "notification-permission-evidence/${if (allow) "allow" else "deny"}-failure.png")) }) {
                        waitFor("Notifications")
                        ui.onAllNodesWithText("Not now").assertCountEquals(0)
                        ui.onNodeWithText("Notifications").performClick()
                        waitFor("Android notification alerts")
                        ui.onNodeWithText("Enable alerts").performClick()
                        val device = UiDevice.getInstance(instrumentation)
                        if (Build.VERSION.SDK_INT >= 33) {
                            val button = if (allow) "permission_allow_button" else "permission_deny_button"
                            val selector = By.res("com.android.permissioncontroller", button)
                            ui.waitUntil(15_000) { device.hasObject(selector) }
                            captureNativeScreen(File(context.cacheDir,
                                "notification-permission-evidence/${if (allow) "allow" else "deny"}-system.png"))
                            device.findObject(selector).click()
                            ui.waitUntil(15_000) { context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                                if (allow) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED }
                        } else {
                            ui.waitUntil(15_000) { device.hasObject(By.pkg("com.android.settings")) }
                            captureNativeScreen(File(context.cacheDir, "notification-permission-evidence/settings-system.png"))
                            device.pressBack()
                        }
                        ui.waitUntil(15_000) { preferences.getBoolean("offered", false) }
                        waitForEnabled("Refresh")
                        ui.onAllNodesWithText("Not now").assertCountEquals(0)
                        ui.waitUntil(15_000) { reads.get() > 0 }
                        val beforeRefresh = reads.get()
                        ui.onNodeWithText("Refresh").performClick()
                        ui.waitUntil(15_000) { reads.get() > beforeRefresh }
                        waitForEnabled("Refresh")
                        ui.onAllNodesWithText(context.getString(R.string.notifications_read_error)).assertCountEquals(0)
                        captureNativeScreen(File(context.cacheDir,
                            "notification-permission-evidence/${if (allow) "allow" else "deny"}-reading.png"), "Notifications")
                        val beforeReopening = reads.get()
                        scenario.recreate()
                        ui.waitUntil(15_000) { reads.get() > beforeReopening }
                        waitForEnabled("Refresh")
                        ui.onAllNodesWithText(context.getString(R.string.notifications_read_error)).assertCountEquals(0)
                        ui.onAllNodesWithText("Not now").assertCountEquals(0)
                        ui.onNodeWithText("Back").performClick()
                        waitFor("Notifications")
                        check(original == store.loadOrCreate().exportJson()) { "Permission choice must preserve the wallet" }
                        peer.assertRequestObserved(); peer.assertHealthy()
                        assertTrue(peer.clientKeys.isNotEmpty() && peer.clientKeys.all { it == wallet.publicKey })
                        assertTrue(peer.transactions.isEmpty()); assertTrue(peer.reports.isEmpty())
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, {
                retained.restoreFiles()
            }, {
                retained.restorePreferences()
            }, { check(original == store.loadOrCreate().exportJson()) { "Permission choice must preserve the wallet" } })
        }
    }

    private fun waitFor(text: String) = ui.waitUntil(15_000) {
        ui.onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }
    private fun waitForEnabled(text: String) = ui.waitUntil(15_000) {
        ui.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }
}
