package nl.davidengelen.redsquare

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.*
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.talk.*
import org.junit.Assert.*
import org.junit.Test
import org.webrtc.*
import java.io.File
import java.nio.ByteBuffer
import java.util.Base64
import java.util.concurrent.atomic.*

class TalkDirectPreviewTest {
    @Test fun anAcceptedLegacyBrowserCallAuthenticatesBeforeRingingAndConnectsWithNativeAudioOnly() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallets = AndroidWalletStore(context); val previous = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context); val previousBackend = backends.load()
        val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val caller = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
        val browser = AtomicReference<TalkBrowserPeer>()
        val sequence = AtomicInteger(6000)
        val evidence = File(context.cacheDir, "talk-direct-evidence").apply { mkdirs() }
        val fixture = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", relayResponse = { inner ->
            val envelope = SaitoEnvelope.fromSignedJson(inner)
            val signed = SaitoEnvelope.decode(envelope)
            if (caller.publicKey in signed.recipients) browser.get().receive(envelope)
            "{\"success\":1}"
        })
        var account: TalkAccount? = null; var token: String? = null; var primary: Throwable? = null
        fun await(label: String, timeout: Long = 20_000, goal: () -> Boolean) {
            val end = SystemClock.elapsedRealtime() + timeout
            while (!goal() && SystemClock.elapsedRealtime() < end) {
                browser.get()?.errors?.firstOrNull()?.let { throw AssertionError("$label: $it") }
                Thread.sleep(50)
            }
            assertTrue("$label: phase=${TalkRuntime.call.value?.phase}, diagnostic=${TalkRuntime.diagnostic.value}", goal())
        }
        try {
            backends.save(SaitoBackend.OWNER); wallets.replace(previous, owner)
            fixture.start().use { server ->
                val endpoint = "ws://127.0.0.1:${server.port}/wsopen"
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", endpoint).putExtra("feedOfficialPeerUrl", endpoint)).use { activity ->
                    TalkRuntime.visibility(true)
                    account = TalkRuntime.account(context, owner, SaitoBackend.OWNER, endpoint).also { it.attach() }
                    await("Relay authenticated", 5000) { fixture.authenticatedConnectionIds.isNotEmpty() }
                    TalkBrowserPeer(caller, owner, "#e02020", 660, send@{ envelope ->
                        val signed = runCatching { SaitoEnvelope.decode(envelope) }.getOrNull()
                        if (signed != null) {
                            check(signed.author == caller.publicKey)
                            if (owner.publicKey !in signed.recipients) return@send
                        } else check(requireNotNull(TalkProtocol.read(envelope, owner.publicKey)).sender == caller.publicKey)
                        val value = JsonParser.parseString(envelope).asJsonObject
                        val header = Base64.getDecoder().decode(value.get("t").asString)
                        val body = Base64.getDecoder().decode(value.get("m").asString)
                        ByteBuffer.wrap(header).putInt(8, body.size)
                        fixture.sendIncoming(header + body, sequence.incrementAndGet())
                    }).use { peer ->
                        browser.set(peer)
                        withFailureCapture({
                            File(evidence, "failure-state.txt").writeText("${TalkRuntime.call.value}\n${TalkRuntime.diagnostic.value}\n${peer.errors}\nmedia=${peer.media}\ncaptures=${peer.captures}\ndiagnostics=${peer.diagnostics}")
                            captureNativeScreen(File(evidence, "failure.png"))
                        }) {
                            peer.dial(TalkRoom(SaitoWallet.create().publicKey, caller.publicKey))
                            await("Legacy caller proves its signed SDP/DTLS before INCOMING") { TalkRuntime.call.value?.phase == CallPhase.INCOMING }
                            token = requireNotNull(TalkRuntime.call.value).token
                            assertEquals(caller.publicKey, TalkRuntime.call.value?.direct)
                            assertNull(TalkRuntime.call.value?.localVideo)
                            assertEquals("The browser has not captured before consent", 0, peer.captures.get())
                            val device = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                            requireNotNull(device.wait(androidx.test.uiautomator.Until.findObject(
                                androidx.test.uiautomator.By.text("Answer with audio")), 5000)).click()
                            await("Accepted native audio-only call connects", 25_000) {
                                TalkRuntime.call.value?.let { it.phase == CallPhase.ACTIVE && it.participants.singleOrNull()?.let { p -> p.audio != null && p.video != null } == true } == true
                            }
                            assertTrue("Answer opens the native call screen", device.wait(androidx.test.uiautomator.Until.hasObject(
                                androidx.test.uiautomator.By.desc("Switch app: Saito Talk")), 5000))
                            assertFalse(requireNotNull(TalkRuntime.call.value).camera)
                            assertNull(TalkRuntime.call.value?.localVideo)
                            val frames = AtomicInteger()
                            val track = requireNotNull(TalkRuntime.call.value?.participants?.single()?.video)
                            val sink = VideoSink { if (it.buffer.width >= 320) frames.incrementAndGet() }
                            track.addSink(sink)
                            try { await("Actual caller video reaches the accepted audio-only Android participant", 10_000) { frames.get() >= 3 } }
                            finally { runCatching { track.removeSink(sink) } }
                            TalkRuntime.end(requireNotNull(token))
                            await("End releases the accepted direct call", 5000) { TalkRuntime.call.value?.phase == CallPhase.ENDED }
                            File(evidence, "direct.txt").writeText("legacyUnsignedInvitation=true\nauthenticatedBeforeRinging=true\nnoCaptureBeforeConsent=true\nnativeCamera=false\nactualRemoteVideoFrames=${frames.get()}\nended=true\n")
                            fixture.assertHealthy()
                        }
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            token?.let(TalkRuntime::end); account?.detach(); TalkRuntime.visibility(false); Thread.sleep(300)
            cleanupAfterTest(primary, { wallets.replace(wallets.loadOrCreate(), previous) }, { backends.save(previousBackend) },
                { context.stopService(Intent(context, TalkService::class.java)) })
        }
    }
}
