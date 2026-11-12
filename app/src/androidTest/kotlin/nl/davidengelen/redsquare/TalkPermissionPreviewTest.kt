package nl.davidengelen.redsquare

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.talk.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.webrtc.VideoSink
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

/** Actual Android microphone denial/retry. The host revokes before instrumentation to preserve process ownership. */
class TalkPermissionPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun denyingMicrophoneKeepsCaptureOffAndAnExplicitAllowedRetryStartsNativeCapture() {
        val instrumentation = InstrumentationRegistry.getInstrumentation(); val context = instrumentation.targetContext
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
        withAppFrameFixture("talk-permission", "talk-permission-evidence", advertisedServices = "archive||;relay||") { activity ->
            var token: String? = null
            try {
                ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
                ui.onNodeWithText("Later").performClick()
                val owner = AndroidWalletStore(context).loadExisting().publicKey
                val link = TalkLink(SaitoBackend.OWNER, TalkRoom(SaitoWallet.create().publicKey, owner)).url()
                activity.onActivity { it.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(context.packageName)) }
                ui.waitUntil(5000) { ui.onNodeWithText("Ready to join?").isDisplayed() }
                fun join() {
                    ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Join room") and hasClickAction())
                    ui.onNode(hasText("Join room") and hasClickAction()).performClick()
                }
                val device = UiDevice.getInstance(instrumentation)
                join()
                requireNotNull(device.wait(Until.findObject(By.res(Pattern.compile(".*:id/permission_deny_button"))), 5000)).click()
                val label = context.getString(R.string.talk_permissions_needed)
                ui.waitUntil(5000) { ui.onNodeWithText(label).isDisplayed() }
                assertTrue(TalkRuntime.call.value?.phase?.live != true)
                assertNull(TalkRuntime.call.value?.localVideo)
                assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
                captureNativeScreen(File(context.cacheDir, "talk-permission-evidence/denied-without-capture.png"))
                join()
                requireNotNull(device.wait(Until.findObject(By.res(Pattern.compile(".*:id/permission_allow(_foreground_only)?_button"))), 5000)).click()
                ui.waitUntil(10_000) { TalkRuntime.call.value?.localVideo != null }
                val call = requireNotNull(TalkRuntime.call.value).also { token = it.token }
                assertTrue(call.phase.started)
                assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
                val frames = AtomicInteger(); val track = requireNotNull(call.localVideo)
                val sink = VideoSink { frames.incrementAndGet() }
                track.addSink(sink)
                try { ui.waitUntil(10_000) { frames.get() >= 3 } }
                finally { runCatching { track.removeSink(sink) } }
                captureNativeScreen(File(context.cacheDir, "talk-permission-evidence/allowed-retry.png"))
                File(context.cacheDir, "talk-permission-evidence/result.txt")
                    .writeText("actualAndroidDenial=true\nnoCaptureAfterDenial=true\nexplicitAllowedRetry=true\nactualCameraFrames=${frames.get()}\n")
                ui.onNode(hasText("End call") and hasClickAction()).performClick()
                ui.waitUntil(5000) { TalkRuntime.call.value?.phase == CallPhase.ENDED }
            } finally { token?.let(TalkRuntime::end) }
        }
    }
}
