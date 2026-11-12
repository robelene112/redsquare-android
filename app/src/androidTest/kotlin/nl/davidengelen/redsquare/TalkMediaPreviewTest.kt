package nl.davidengelen.redsquare

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.talk.*
import org.junit.Assert.*
import org.junit.Test
import org.webrtc.*
import java.io.File
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.*
import kotlin.math.*

/** Real native capture/codecs/DTLS/RTP and original browser Talk/Stun, on an owned Relay only. */
class TalkMediaPreviewTest {
    @Test fun aNativeRoomTransportsDecodedAudioAndVideoToTwoOfficialBrowserPeers() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallets = AndroidWalletStore(context); val previous = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context); val previousBackend = backends.load()
        val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val browsers = ConcurrentHashMap<String, TalkBrowserPeer>()
        val sequence = AtomicInteger(5000)
        val evidence = File(context.cacheDir, "talk-media-evidence").apply { mkdirs() }
        var account: TalkAccount? = null
        var primary: Throwable? = null
        var token: String? = null
        val localAudioReads = AtomicInteger()
        var sample = 0L
        RtcPlatform.audioInputForTest { buffer, channels, rate, bytes ->
            localAudioReads.incrementAndGet()
            val pcm = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            for (offset in 0 until bytes step (2 * channels)) {
                val value = (sin(2 * PI * 440 * sample++ / rate) * 7000).toInt().toShort()
                for (channel in 0 until channels) pcm.putShort(offset + 2 * channel, value)
            }
        }
        lateinit var fixture: ControlledFeedPeer
        fun route(envelope: String) {
            val signed = SaitoEnvelope.decode(envelope)
            browsers.values.filter { it.wallet.publicKey in signed.recipients && it.wallet.publicKey != signed.author }.forEach { it.receive(envelope) }
            if (owner.publicKey in signed.recipients && signed.author != owner.publicKey) {
                val detached = com.google.gson.JsonParser.parseString(envelope).asJsonObject
                val header = java.util.Base64.getDecoder().decode(detached.get("t").asString)
                val body = java.util.Base64.getDecoder().decode(detached.get("m").asString)
                java.nio.ByteBuffer.wrap(header).putInt(8, body.size)
                val binary = header + body
                fixture.sendIncoming(binary, sequence.incrementAndGet())
            }
        }
        fixture = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", relayResponse = { inner ->
            route(SaitoEnvelope.fromSignedJson(inner)); "{\"success\":1}"
        })
        fun await(label: String, timeout: Long = 20_000, check: () -> Boolean) {
            val end = SystemClock.elapsedRealtime() + timeout
            while (!check() && SystemClock.elapsedRealtime() < end) {
                browsers.values.forEach { if (it.errors.isNotEmpty()) throw AssertionError("$label: ${it.errors}") }
                Thread.sleep(50)
            }
            assertTrue("$label; call=${TalkRuntime.call.value?.phase}; diagnostic=${TalkRuntime.diagnostic.value}", check())
        }
        try {
            backends.save(SaitoBackend.OWNER); wallets.replace(previous, owner)
            fixture.start().use { server ->
                val endpoint = "ws://127.0.0.1:${server.port}/wsopen"
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", endpoint).putExtra("feedOfficialPeerUrl", endpoint)).use { activity ->
                    withFailureCapture({
                        File(evidence, "failure-state.txt").writeText("call=${TalkRuntime.call.value}\ndiagnostic=${TalkRuntime.diagnostic.value}\n" +
                            browsers.values.joinToString("\n") { "${it.wallet.publicKey} errors=${it.errors} media=${it.media} captures=${it.captures}" })
                        captureNativeScreen(File(evidence, "failure.png"))
                    }) {
                        TalkRuntime.visibility(true)
                        account = TalkRuntime.account(context, owner, SaitoBackend.OWNER, endpoint).also { it.attach() }
                        val room = TalkRoom(SaitoWallet.create().publicKey, owner.publicKey)
                        assertTrue(runBlocking { requireNotNull(account).start(TalkLink(SaitoBackend.OWNER, room), null, true, true) })
                        token = requireNotNull(TalkRuntime.call.value).token
                        await("Owned Relay authenticated", 5000) { fixture.authenticatedConnectionIds.isNotEmpty() }
                        TalkBrowserPeer(SaitoWallet.fromPrivateKey("0".repeat(63) + "1"), owner, "#e02020", 660, ::route).use { alice ->
                            browsers[alice.wallet.publicKey] = alice
                            assertEquals("Opening a browser fixture does not capture", 0, alice.captures.get())
                            alice.join(room)
                            await("Native and official browser exchange media tracks") {
                                TalkRuntime.call.value?.participants?.singleOrNull()?.let { it.connected && it.video != null && it.audio != null } == true
                            }
                            val frames = AtomicInteger(); val colour = AtomicBoolean(); val audio = AtomicBoolean()
                            val participant = requireNotNull(TalkRuntime.call.value).participants.single()
                            val sink = VideoSink { frame ->
                                val i420 = frame.buffer.toI420() ?: return@VideoSink
                                try {
                                    if (i420.width >= 320 && i420.height >= 240) {
                                        frames.incrementAndGet()
                                        val u = i420.dataU.get(i420.strideU * 30 + 30).toInt() and 255
                                        val v = i420.dataV.get(i420.strideV * 30 + 30).toInt() and 255
                                        if (u < 125 && v > 160) colour.set(true)
                                    }
                                } finally { i420.release() }
                            }
                            val audioSink = AudioTrackSink { data, bits, rate, channels, count, timestamp ->
                                val pcm = data.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                                var energy = 0.0
                                if (bits == 16) for (offset in 0 until min(pcm.remaining(), count * channels * 2) step 2) {
                                    val value = pcm.getShort(offset).toDouble() / 32768; energy += value * value
                                }
                                if (energy > 0.01) audio.set(true)
                            }
                            participant.video!!.addSink(sink); participant.audio!!.addSink(audioSink)
                            try {
                                await("Actual red browser frames decoded natively") { frames.get() >= 5 && colour.get() }
                                await("Actual browser PCM decoded natively") { audio.get() }
                                await("Actual native camera and PCM decoded in browser") { alice.media[owner.publicKey]?.let { it.width >= 320 && it.nonBlack > 500 && it.rms > 0.01 } == true }
                                TalkBrowserPeer(SaitoWallet.fromPrivateKey("0".repeat(63) + "3"), owner, "#20d030", 880, ::route).use { carol ->
                                    browsers[carol.wallet.publicKey] = carol
                                    carol.join(room)
                                    await("Three actual native participants connected", 30_000) {
                                        TalkRuntime.call.value?.participants?.count { it.connected && it.video != null && it.audio != null } == 2
                                    }
                                    await("Official browser peers complete the three-party mesh", 30_000) {
                                        carol.media[owner.publicKey]?.rms?.let { it > 0.01 } == true &&
                                            carol.media[alice.wallet.publicKey]?.rms?.let { it > 0.01 } == true &&
                                            alice.media[carol.wallet.publicKey]?.rms?.let { it > 0.01 } == true
                                    }
                                    carol.leave()
                                    await("A leaving participant is removed", 5000) { TalkRuntime.call.value?.participants?.size == 1 }
                                    browsers.remove(carol.wallet.publicKey)
                                }
                                assertTrue(localAudioReads.get() > 10)
                                File(evidence, "media.txt").writeText("realNativeVideoFrames=${frames.get()}\nredPeerPixels=${colour.get()}\nremotePcm=${audio.get()}\nactualAudioRecordReads=${localAudioReads.get()}\nthreePartyDecodedMesh=true\nsyntheticBrowserDeviceInputs=true\ncontrolledNativePcmAfterAudioRecord=true\npublicWrites=0\n")
                            } finally {
                                runCatching { participant.video.removeSink(sink) }; runCatching { participant.audio.removeSink(audioSink) }
                            }
                            TalkRuntime.end(requireNotNull(token))
                            await("End releases native media", 5000) { TalkRuntime.call.value?.phase == CallPhase.ENDED && TalkRuntime.call.value?.localVideo == null }
                            browsers.remove(alice.wallet.publicKey)
                        }
                        fixture.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            RtcPlatform.audioInputForTest(null); token?.let(TalkRuntime::end); account?.detach(); TalkRuntime.visibility(false)
            Thread.sleep(300)
            cleanupAfterTest(primary,
                { wallets.replace(wallets.loadOrCreate(), previous) }, { backends.save(previousBackend) },
                { context.stopService(Intent(context, TalkService::class.java)) })
        }
    }
}
