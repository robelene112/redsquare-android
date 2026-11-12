package nl.davidengelen.redsquare

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.talk.*
import org.junit.Assert.*
import org.junit.Test
import org.webrtc.VideoSink
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Public native call boundary plus the real emulator camera, without any public development peer. */
class TalkCapturePreviewTest {
    @Test fun startingAVideoRoomProducesActualNativeCameraFramesAndEndingReleasesIt() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallets = AndroidWalletStore(context)
        val original = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context)
        val originalBackend = backends.load()
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val evidence = File(context.cacheDir, "talk-capture-evidence").apply { mkdirs() }
        val fixture = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", relayResponse = { "{\"success\":1}" })
        var account: TalkAccount? = null
        var token: String? = null
        var primary: Throwable? = null
        try {
            backends.save(SaitoBackend.OWNER); wallets.replace(original, wallet)
            fixture.start().use { server ->
                val endpoint = "ws://127.0.0.1:${server.port}/wsopen"
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", endpoint).putExtra("feedOfficialPeerUrl", endpoint)).use { activity ->
                    withFailureCapture({
                        File(evidence, "failure-state.txt").writeText(TalkRuntime.call.value?.let {
                            "phase=${it.phase} localVideo=${it.localVideo != null} diagnostic=${TalkRuntime.diagnostic.value}"
                        } ?: "no call")
                        File(evidence, "fixture-health.txt").writeText(runCatching { fixture.assertHealthy(); "healthy" }
                            .getOrElse { it.toString() })
                        captureNativeScreen(File(evidence, "failure.png"))
                    }) {
                        TalkRuntime.visibility(true)
                        account = TalkRuntime.account(context, wallet, SaitoBackend.OWNER, endpoint).also { it.attach() }
                        assertTrue(TalkCapture.permitted(context, camera = true))
                        val room = TalkRoom(SaitoWallet.create().publicKey, wallet.publicKey)
                        assertTrue(runBlocking { requireNotNull(account).start(TalkLink(SaitoBackend.OWNER, room), null, camera = true, microphone = true) })
                        token = requireNotNull(TalkRuntime.call.value).token
                        val frames = AtomicInteger()
                        val frameSeen = CountDownLatch(1)
                        val deadline = SystemClock.elapsedRealtime() + 10_000
                        while (TalkRuntime.call.value?.localVideo == null && SystemClock.elapsedRealtime() < deadline) Thread.sleep(25)
                        val track = TalkRuntime.call.value?.localVideo
                        val sink = VideoSink { frame ->
                            if (frame.buffer.width >= 320 && frame.buffer.height >= 240 && frames.incrementAndGet() >= 3) frameSeen.countDown()
                        }
                        try {
                            track?.addSink(sink)
                            assertTrue("A started video room must produce real camera frames", frameSeen.await(10, TimeUnit.SECONDS))
                        } finally { if (track != null) runCatching { track.removeSink(sink) } }
                        TalkRuntime.end(requireNotNull(token))
                        val end = SystemClock.elapsedRealtime() + 5_000
                        while (TalkRuntime.call.value?.phase?.live == true && SystemClock.elapsedRealtime() < end) Thread.sleep(25)
                        assertEquals(CallPhase.ENDED, TalkRuntime.call.value?.phase)
                        assertNull(TalkRuntime.call.value?.localVideo)
                        File(evidence, "camera.txt").writeText("Actual emulator camera frames=${frames.get()}; ended=ENDED; retainedTrack=false")
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            token?.let(TalkRuntime::end); account?.detach(); TalkRuntime.visibility(false)
            Thread.sleep(300)
            cleanupAfterTest(primary,
                { wallets.replace(wallets.loadOrCreate(), original) }, { backends.save(originalBackend) },
                { context.stopService(Intent(context, TalkService::class.java)) })
        }
    }
}
