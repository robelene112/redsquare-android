package nl.davidengelen.redsquare

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch

class VaultUploadBackgroundPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun anAuthenticatedUploadContinuesAcrossModuleHomeAndActualScreenOffAndReturnsCompleted() = withVaultDeviceCredential { device ->
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val granted = Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        check(granted) { "The host must grant and restore notification permission outside instrumentation" }
        run {
            withVaultReadingFixture("background-upload", captureFolder = "vault-upload-background-evidence", retainTransfers = true, minting = true) { scene ->
                val gate = CountDownLatch(1).also { requireNotNull(scene.mint).uploadGate = it }
                val bytes = "Complete this same Vault upload while the phone screen is off".toByteArray()
                try {
                    ui.reviewVaultFile(scene, "vault-background.bin", bytes)
                    enterRealSystemPin(device)
                    ui.waitUntil(25_000) { scene.peer.vaultUploads.size == 1 }
                    val manager = context.getSystemService(NotificationManager::class.java)
                    ui.waitUntil(5_000) { manager.activeNotifications.any {
                        it.notification.extras.getString(Notification.EXTRA_TITLE) == "Uploading to Vault"
                    } }
                    val notice = manager.activeNotifications.single {
                        it.notification.extras.getString(Notification.EXTRA_TITLE) == "Uploading to Vault"
                    }.notification
                    assertTrue(notice.flags and Notification.FLAG_FOREGROUND_SERVICE != 0)
                    assertTrue(notice.flags and Notification.FLAG_ONGOING_EVENT != 0)
                    ui.switchStackModule("Vault", "Red Square")
                    val before = scene.balanceRequests()
                    device.pressHome(); device.sleep()
                    val power = context.getSystemService(PowerManager::class.java)
                    assertFalse("The actual display must be off", power.isInteractive)
                    val offAt = SystemClock.elapsedRealtime()
                    gate.countDown()
                    val deadline = offAt + 10_000
                    while (scene.balanceRequests() <= before && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(25)
                    assertTrue("The upload must progress to fresh ownership IO while the display is off", scene.balanceRequests() > before)
                    assertFalse(power.isInteractive)
                    assertArrayEquals(bytes, requireNotNull(scene.mint).storedContents())
                    File(scene.evidence, "actual-screen-off-progress.json").writeText(com.google.gson.JsonObject().apply {
                        addProperty("screenOffAtElapsedMillis", offAt); addProperty("observedAtElapsedMillis", SystemClock.elapsedRealtime())
                        addProperty("isInteractive", power.isInteractive)
                        addProperty("balanceRequestsBefore", before); addProperty("balanceRequestsAfter", scene.balanceRequests())
                        addProperty("mintFrames", scene.peer.transactions.size); addProperty("fileUploads", scene.peer.vaultUploads.size)
                    }.toString())
                    // Clear only the disposable emulator credential so returning from the real
                    // lock screen does not masquerade as another upload authorization.
                    device.executeShellCommand("locksettings clear --old $EXPORT_TEST_PIN --user 0")
                    device.wakeUp(); device.executeShellCommand("wm dismiss-keyguard")
                    context.startActivity(Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    ui.switchStackModule("Red Square", "Vault", scrollTarget = true)
                    ui.waitUntil(15_000) { ui.onNodeWithText("Upload complete").isDisplayed() }
                    assertEquals(1, scene.peer.transactions.size); assertEquals(1, scene.peer.vaultUploads.size)
                    captureNativeScreen(File(scene.evidence, "completed-after-actual-background-and-screen-off.png"))
                    ui.onNodeWithText("Back to files").performScrollTo().performClick()
                    ui.waitUntil(15_000) { ui.onNodeWithText("vault-background.bin").isDisplayed() }
                } finally { gate.countDown(); device.wakeUp(); device.executeShellCommand("wm dismiss-keyguard") }
            }
        }
    }
}
