package nl.davidengelen.redsquare

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.talk.*
import org.junit.Assert.*
import org.junit.Test
import org.webrtc.VideoSink
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Actual camera/foreground types and real Home, screen-off and notification return. */
class TalkCameraLifecyclePreviewTest {
    @Test fun enablingVideoInAVoiceRoomUpdatesForegroundOwnershipAndPausesAndResumesActualCameraCapture() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallets = AndroidWalletStore(context); val previous = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context); val previousBackend = backends.load()
        val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val evidence = File(context.cacheDir, "talk-camera-lifecycle-evidence").apply { mkdirs() }
        val fixture = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||")
        var account: TalkAccount? = null; var token: String? = null; var primary: Throwable? = null
        fun await(label: String, timeout: Long = 5000, goal: () -> Boolean) {
            val end = SystemClock.elapsedRealtime() + timeout
            while (!goal() && SystemClock.elapsedRealtime() < end) Thread.sleep(25)
            assertTrue("$label: phase=${TalkRuntime.call.value?.phase}, paused=${TalkRuntime.call.value?.cameraPaused}", goal())
        }
        try {
            backends.save(SaitoBackend.OWNER); wallets.replace(previous, owner)
            fixture.start().use { server ->
                val endpoint = "ws://127.0.0.1:${server.port}/wsopen"
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", endpoint).putExtra("feedOfficialPeerUrl", endpoint)).use { activity ->
                    withFailureCapture({
                        File(evidence, "failure-state.txt").writeText("${TalkRuntime.call.value}\n${TalkRuntime.diagnostic.value}")
                        File(evidence, "failure-services.txt").writeText(device.executeShellCommand("dumpsys activity services ${context.packageName}"))
                        captureNativeScreen(File(evidence, "failure.png"))
                    }) {
                        TalkRuntime.visibility(true)
                        account = TalkRuntime.account(context, owner, SaitoBackend.OWNER, endpoint).also { it.attach() }
                        val room = TalkRoom(SaitoWallet.create().publicKey, owner.publicKey, mode = TalkMode.VOICE)
                        assertTrue(runBlocking { requireNotNull(account).start(TalkLink(SaitoBackend.OWNER, room), null, false, true) })
                        token = requireNotNull(TalkRuntime.call.value).token
                        assertNull(TalkRuntime.call.value?.localVideo)
                        TalkRuntime.controls(requireNotNull(token), camera = true)
                        await("Camera can be enabled in an existing voice room", 10_000) { TalkRuntime.call.value?.localVideo != null }
                        val track = requireNotNull(TalkRuntime.call.value?.localVideo)
                        val frames = AtomicInteger()
                        val sink = VideoSink { if (it.buffer.width >= 320) frames.incrementAndGet() }
                        track.addSink(sink)
                        try {
                            await("Real camera frames arrive after enabling video", 10_000) { frames.get() >= 3 }
                            if (android.os.Build.VERSION.SDK_INT >= 30) {
                                var services = ""
                                await("Foreground ownership includes the enabled camera") {
                                    services = device.executeShellCommand("dumpsys activity services ${context.packageName}")
                                    Regex("(?:foregroundServiceType|types)=(0x[0-9a-fA-F]+|[0-9]+)").findAll(services).any {
                                        val value = it.groupValues[1]
                                        val type = if (value.startsWith("0x")) value.drop(2).toInt(16) else value.toInt()
                                        type and android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA != 0
                                    }
                                }
                                File(evidence, "video-services.txt").writeText(services)
                            }
                            requireNotNull(device.wait(androidx.test.uiautomator.Until.findObject(
                                androidx.test.uiautomator.By.text("Return to call")), 5000)).click()
                            val firstCamera = requireNotNull(TalkRuntime.call.value).frontCamera
                            val beforeSwitch = frames.get()
                            requireNotNull(device.wait(androidx.test.uiautomator.Until.findObject(
                                androidx.test.uiautomator.By.text("Switch camera")), 5000)).click()
                            await("The actual Switch camera button changes camera and resumes frames") {
                                TalkRuntime.call.value?.frontCamera != firstCamera && frames.get() >= beforeSwitch + 3
                            }
                            captureNativeScreen(File(evidence, "switched-camera.png"))
                            device.pressHome()
                            await("Camera is paused outside the visible app") { TalkRuntime.call.value?.cameraPaused == true }
                            Thread.sleep(200)
                            val pausedFrames = frames.get()
                            device.sleep(); Thread.sleep(600)
                            assertEquals("Paused camera supplies no new frames", pausedFrames, frames.get())
                            assertTrue(requireNotNull(TalkRuntime.call.value).phase.started)
                            device.wakeUp(); device.executeShellCommand("wm dismiss-keyguard")
                            val alert = requireNotNull(context.getSystemService(android.app.NotificationManager::class.java)
                                .activeNotifications.singleOrNull { it.id == TalkService.ID })
                            alert.notification.contentIntent.send()
                            await("Notification returns to the same call and resumes its camera") {
                                TalkRuntime.call.value?.let { it.token == token && !it.cameraPaused } == true && TalkRuntime.visible
                            }
                            await("Resumed camera produces real frames") { frames.get() >= pausedFrames + 3 }
                            captureNativeScreen(File(evidence, "resumed-call.png"))
                            TalkRuntime.end(requireNotNull(token))
                            await("End releases camera") { TalkRuntime.call.value?.phase == CallPhase.ENDED && TalkRuntime.call.value?.localVideo == null }
                            File(evidence, "camera.txt").writeText("startedAsVoice=true\nvideoEnabled=true\nactualNativeCameraSwitch=true\nactualFrames=${frames.get()}\nbackgroundCameraPaused=true\nscreenOffCallRetained=true\nexactNotificationReturn=true\nresumedCameraFrames=true\n")
                        } finally { runCatching { track.removeSink(sink) } }
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            runCatching { device.wakeUp() }; token?.let(TalkRuntime::end); account?.detach(); TalkRuntime.visibility(false); Thread.sleep(300)
            cleanupAfterTest(primary, { wallets.replace(wallets.loadOrCreate(), previous) }, { backends.save(previousBackend) },
                { context.stopService(Intent(context, TalkService::class.java)) })
        }
    }
}
