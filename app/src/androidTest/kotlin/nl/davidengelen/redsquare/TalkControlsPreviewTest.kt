package nl.davidengelen.redsquare

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.google.gson.*
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.talk.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteOrder
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import kotlin.math.*

/** Actual peer PCM and native button input while an owned Relay request remains blocked. */
class TalkControlsPreviewTest {
    @Test fun microphoneChoiceReachesThePeerAndMuteSilencesActualRtpEvenWhileRelayIsWaiting() = controls(false)
    @Test fun aWaitingRoomRosterAcknowledgementCannotDelayNativeMute() = controls(true)
    private fun controls(roster: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallets = AndroidWalletStore(context); val previous = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context); val previousBackend = backends.load()
        val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val caller = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
        val browser = AtomicReference<TalkBrowserPeer>()
        val hold = AtomicBoolean(); val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val sequence = AtomicInteger(7000); val reads = AtomicInteger()
        val evidence = File(context.cacheDir, "talk-controls-evidence").apply { mkdirs() }
        val fixture = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", relayResponse = { inner ->
            val envelope = SaitoEnvelope.fromSignedJson(inner); val signed = SaitoEnvelope.decode(envelope)
            if (hold.get() && signed.body.get("request")?.asString == if (roster) "call-list-response" else "toggle-video") {
                entered.countDown(); check(release.await(15, TimeUnit.SECONDS))
            }
            if (caller.publicKey in signed.recipients) browser.get().receive(envelope)
            "{\"success\":1}"
        })
        var account: TalkAccount? = null; var token: String? = null; var primary: Throwable? = null
        var sample = 0L
        RtcPlatform.audioInputForTest { buffer, channels, rate, bytes ->
            reads.incrementAndGet(); val pcm = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            for (offset in 0 until bytes step (2 * channels)) {
                val value = (sin(2 * PI * 440 * sample++ / rate) * 7000).toInt().toShort()
                for (channel in 0 until channels) pcm.putShort(offset + 2 * channel, value)
            }
        }
        fun await(label: String, timeout: Long = 5000, goal: () -> Boolean) {
            val end = SystemClock.elapsedRealtime() + timeout
            while (!goal() && SystemClock.elapsedRealtime() < end) {
                browser.get()?.errors?.firstOrNull()?.let { throw AssertionError("$label: $it") }
                Thread.sleep(25)
            }
            assertTrue("$label: phase=${TalkRuntime.call.value?.phase}, peer=${browser.get()?.media}", goal())
        }
        try {
            backends.save(SaitoBackend.OWNER); wallets.replace(previous, owner)
            fixture.start().use { server ->
                val endpoint = "ws://127.0.0.1:${server.port}/wsopen"
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", endpoint).putExtra("feedOfficialPeerUrl", endpoint)).use { activity ->
                    withFailureCapture({
                        File(evidence, "failure-state-$roster.txt").writeText("${TalkRuntime.call.value}\n${browser.get()?.microphones}\n${browser.get()?.media}\n${browser.get()?.errors}\nreads=${reads.get()} relayHeld=${hold.get()}")
                        captureNativeScreen(File(evidence, "failure-$roster.png"))
                    }) {
                        TalkRuntime.visibility(true)
                        account = TalkRuntime.account(context, owner, SaitoBackend.OWNER, endpoint).also { it.attach() }
                        val room = TalkRoom(SaitoWallet.create().publicKey, owner.publicKey)
                        assertTrue(runBlocking { requireNotNull(account).start(TalkLink(SaitoBackend.OWNER, room), null, true, false) })
                        token = requireNotNull(TalkRuntime.call.value).token
                        TalkBrowserPeer(caller, owner, "#e02020", 660, send@{ envelope ->
                            val signed = SaitoEnvelope.decode(envelope); check(signed.author == caller.publicKey)
                            if (owner.publicKey !in signed.recipients) return@send
                            val value = JsonParser.parseString(envelope).asJsonObject
                            val header = java.util.Base64.getDecoder().decode(value.get("t").asString)
                            val body = java.util.Base64.getDecoder().decode(value.get("m").asString)
                            java.nio.ByteBuffer.wrap(header).putInt(8, body.size)
                            fixture.sendIncoming(header + body, sequence.incrementAndGet())
                        }).use { peer ->
                            browser.set(peer); peer.join(room)
                            await("Real call is connected", 20_000) { TalkRuntime.call.value?.phase == CallPhase.ACTIVE && peer.media[owner.publicKey]?.width?.let { it >= 320 } == true }
                            await("The peer is informed of the initially muted microphone") { peer.microphones[owner.publicKey] == false }
                            await("Muted native input produces silence at the actual remote decoder") {
                                peer.media[owner.publicKey]?.let { it.width >= 320 && it.rms < 0.003 } == true
                            }
                            val device = UiDevice.getInstance(instrumentation)
                            requireNotNull(device.wait(Until.findObject(By.text("Return to call")), 5000)).click()
                            requireNotNull(device.wait(Until.findObject(By.text("Unmute")), 5000)).click()
                            await("Native Unmute supplies real received PCM") { peer.media[owner.publicKey]?.rms?.let { it > 0.01 } == true && reads.get() > 10 }
                            hold.set(true)
                            if (roster) {
                                val newcomer = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
                                val request = TalkProtocol.enter(newcomer, room, System.currentTimeMillis()).applicationRequest(1)
                                fixture.sendIncoming(request.copyOfRange(5, request.size), sequence.incrementAndGet())
                            } else TalkRuntime.controls(requireNotNull(token), camera = false)
                            assertTrue("Owned Relay control is actually held", entered.await(5, TimeUnit.SECONDS))
                            requireNotNull(device.wait(Until.findObject(By.text("Mute")), 5000)).click()
                            await("Mute silences media without waiting for Relay", 3000) { peer.media[owner.publicKey]?.rms?.let { it < 0.003 } == true }
                            captureNativeScreen(File(evidence, "muted-with-relay-held-$roster.png"))
                            hold.set(false); release.countDown()
                            await("Muted choice remains after the request completes") { TalkRuntime.call.value?.microphone == false }
                            TalkRuntime.end(requireNotNull(token))
                            await("Call ended") { TalkRuntime.call.value?.phase == CallPhase.ENDED }
                            File(evidence, "controls-$roster.txt").writeText("initialMutedMetadata=true\nactualSilentRtp=true\nnativeUnmuteReceivedPcm=true\nrelayActuallyBlocked=true\nnativeMuteSilencesWithin3000ms=true\n")
                        }
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            hold.set(false); release.countDown(); RtcPlatform.audioInputForTest(null)
            token?.let(TalkRuntime::end); account?.detach(); TalkRuntime.visibility(false); Thread.sleep(300)
            cleanupAfterTest(primary, { wallets.replace(wallets.loadOrCreate(), previous) }, { backends.save(previousBackend) },
                { context.stopService(Intent(context, TalkService::class.java)) })
        }
    }
}
