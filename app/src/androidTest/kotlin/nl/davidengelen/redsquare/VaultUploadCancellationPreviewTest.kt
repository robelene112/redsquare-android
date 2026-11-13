package nl.davidengelen.redsquare

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch

class VaultUploadCancellationPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun notificationCancellationKeepsTheOriginalAndReadOnlyCheckCanVerifyALostUploadReply() = withVaultDeviceCredential { device ->
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val granted = Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        check(granted) { "The host must grant and restore notification permission outside instrumentation" }
        run {
            withVaultReadingFixture("cancel-lost-reply", captureFolder = "vault-upload-cancellation-evidence", retainTransfers = true, minting = true) { scene ->
                val server = requireNotNull(scene.mint)
                val gate = CountDownLatch(1).also { server.uploadGate = it }
                val bytes = "Keep this original file after the upload reply is lost".toByteArray()
                try {
                    ui.reviewVaultFile(scene, "vault-cancelled.bin", bytes)
                    enterRealSystemPin(device)
                    ui.waitUntil(25_000) { scene.peer.vaultUploads.size == 1 }
                    val manager = context.getSystemService(NotificationManager::class.java)
                    ui.waitUntil(5_000) { manager.activeNotifications.any {
                        it.notification.extras.getString(Notification.EXTRA_TITLE) == "Uploading to Vault"
                    } }
                    val notification = manager.activeNotifications.single {
                        it.notification.extras.getString(Notification.EXTRA_TITLE) == "Uploading to Vault"
                    }.notification
                    assertFalse(notification.extras.getString(Notification.EXTRA_TEXT).orEmpty().contains("vault-cancelled.bin"))
                    val stop = notification.actions.single { it.title.toString() == "Stop upload" }
                    val originalRequest = scene.peer.vaultUploads.single().id
                    scene.peer.expectClosedVaultUploadReply(originalRequest)
                    stop.actionIntent.send() // The actual public Android notification control.
                    ui.waitUntil(10_000) { ui.onNodeWithText("Waiting for availability").isDisplayed() }
                    assertEquals(1, scene.peer.transactions.size)
                    assertEquals(1, scene.peer.vaultUploads.size)
                    captureNativeScreen(File(scene.evidence, "notification-stopped-original-file-and-key-retained.png"))
                    gate.countDown()
                    ui.waitUntil(5_000) { runCatching { server.storedContents().contentEquals(bytes) }.getOrDefault(false) }
                    ui.waitUntil(5_000) { scene.peer.observedClosedVaultUploadReplies == listOf(originalRequest) }
                    ui.onNodeWithText("Check availability").performScrollTo().performClick()
                    ui.waitUntil(15_000) { ui.onNodeWithText("Upload complete").isDisplayed() }
                    assertEquals("Read-only recovery must not mint again", 1, scene.peer.transactions.size)
                    assertEquals("Read-only recovery must not upload again", 1, scene.peer.vaultUploads.size)
                    assertTrue("Lost reply recovery must verify the authenticated remote file", scene.peer.vaultRequests.isNotEmpty())
                    assertArrayEquals(bytes, server.storedContents())
                    captureNativeScreen(File(scene.evidence, "lost-reply-recovered-by-authenticated-read.png"))
                    ui.onNodeWithText("Back to files").performScrollTo().performClick()
                    ui.waitUntil(15_000) { ui.onNodeWithText("vault-cancelled.bin").isDisplayed() }
                } finally { gate.countDown() }
            }
        }
    }
}
