package nl.davidengelen.redsquare

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.RtcPlatform
import nl.davidengelen.redsquare.spaces.SpaceVoiceRoom
import nl.davidengelen.redsquare.spaces.SpaceVoiceSignalKey
import nl.davidengelen.redsquare.talk.CallPhase
import nl.davidengelen.redsquare.talk.TalkRuntime
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.webrtc.AudioTrackSink
import org.webrtc.VideoSink
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

class SpaceRoomJourneyTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext.also {
        check(it.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in setOf("ranchu", "goldfish"))
    }
    private val device get() = UiDevice.getInstance(instrumentation)
    private val evidence get() = File(context.cacheDir, "spaces-rooms-evidence").apply { mkdirs() }

    @Test fun prejoinIsQuietAndTheCallReturnsFromAnotherModule() {
        val fixture = request("room-config")
        assertPermissionAbsent(Manifest.permission.RECORD_AUDIO)
        assertPermissionAbsent(Manifest.permission.CAMERA)
        val channel = fixture.get("channel").asString
        val space = fixture.get("space").asString
        launch(fixture).use { activity ->
            withFailureCapture({ capture("room-failure") }) {
                waitText("0 people here", 25_000)
                assertTrue(observed().participants.isEmpty())
                assertTrue(TalkRuntime.call.value?.phase?.live != true)
                capture("room-prejoin")
                roomClick("Join room")
                waitText("You’re in the room", 20_000)
                val call = requireNotNull(TalkRuntime.call.value)
                assertEquals(CallPhase.ACTIVE, call.phase)
                assertEquals(channel, call.spaceRoom?.channel)
                assertEquals(space, call.spaceRoom?.space)
                assertFalse(call.microphone); assertFalse(call.camera); assertNull(call.localVideo)
                assertEquals(call.token, observed().participants.single().session)
                assertPermissionAbsent(Manifest.permission.RECORD_AUDIO)
                assertPermissionAbsent(Manifest.permission.CAMERA)
                capture("room-joined-muted")

                roomClick("Unmute")
                val deny = requireNotNull(device.wait(Until.findObject(By.res(Pattern.compile(".*:id/permission_deny_button"))), 5000))
                deny.click()
                showRoomItem(hasText("Permission wasn’t granted."))
                assertEquals(call.token, TalkRuntime.call.value?.token)
                assertFalse(requireNotNull(TalkRuntime.call.value).microphone)
                assertPermissionAbsent(Manifest.permission.RECORD_AUDIO)
                capture("room-permission-denied")

                roomClick("Browse while in the room")
                click("Return to Open lounge")
                waitText("You’re in the room", 15_000)
                assertEquals(call.token, TalkRuntime.call.value?.token)
                click("Spaces")
                click("Red Square")
                waitText("Return to Open lounge", 12_000)
                capture("room-compact-bar")
                assertEquals(call.token, observed().participants.single().session)
                click("Return to Open lounge")
                waitText("You’re in the room", 15_000)

                device.setOrientationLeft()
                waitText("You’re in the room", 15_000)
                assertEquals(call.token, TalkRuntime.call.value?.token)
                assertFalse(requireNotNull(TalkRuntime.call.value).microphone)
                capture("room-landscape")
                device.setOrientationNatural()
                activity.recreate()
                waitText("You’re in the room", 20_000)
                assertEquals(call.token, TalkRuntime.call.value?.token)
                assertEquals(call.token, observed().participants.single().session)

                assertTrue(SpaceVoiceRoom.read(request("room-remove-native")).participants.isEmpty())
                ui.waitUntil(10_000) { TalkRuntime.call.value?.phase == CallPhase.INTERRUPTED }
                assertNull(TalkRuntime.call.value?.localVideo)
                waitText("0 people here", 12_000)
                capture("room-after-removal")
                File(evidence, "room-result.json").writeText(JsonObject().apply {
                    addProperty("pid", Process.myPid())
                    addProperty("participantRemoved", observed().participants.isEmpty())
                    addProperty("nativePermissionDenied", context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED)
                    addProperty("roomSurvives", observed().status == SpaceVoiceRoom.Status.AVAILABLE)
                }.toString())
            }
        }
    }

    @Test fun threeAdmittedPeersExchangeDecodedAudioAndVideo() = runBlocking<Unit> {
        val fixture = request("room-config")
        val address = SpaceVoiceSignalKey.Address(fixture.get("authority").asString, fixture.get("space").asString,
            fixture.get("channel").asString)
        val samples = AtomicInteger()
        var sample = 0L
        RtcPlatform.audioInputForTest { buffer, channels, rate, bytes ->
            samples.incrementAndGet()
            val pcm = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            for (offset in 0 until bytes step (2 * channels)) {
                val value = (sin(2 * PI * 440 * sample++ / rate) * 7000).toInt().toShort()
                for (channel in 0 until channels) pcm.putShort(offset + 2 * channel, value)
            }
        }
        val guests = mutableListOf<SpaceRoomBrowserFixture>()
        try {
            launch(fixture).use {
                withFailureCapture({
                    File(evidence, "room-media-failure.txt").writeText(
                        "phase=${TalkRuntime.call.value?.phase} diagnostic=${TalkRuntime.diagnostic.value}\n" +
                            guests.joinToString("\n") { "errors=${it.errors} chromium=${it.browser.errors} media=${it.browser.media}" })
                    capture("room-media-failure")
                }) {
                    waitText("0 people here", 25_000)
                    roomClick("Join room")
                    waitText("You’re in the room", 20_000)
                    val initial = requireNotNull(TalkRuntime.call.value)
                    val local = observed().participants.single().contact
                    assertEquals(initial.token, local.session)
                    assertEquals(0, samples.get())
                    guests += SpaceRoomBrowserFixture(address, 1, local, ::request)
                    guests += SpaceRoomBrowserFixture(address, 2, local, ::request)
                    guests.forEach(SpaceRoomBrowserFixture::start)
                    waitMedia(guests, 25_000) { TalkRuntime.call.value?.participants?.let { it.size == 2 && it.all { p -> p.connected } } == true }
                    assertEquals(0, samples.get())
                    assertFalse(requireNotNull(TalkRuntime.call.value).microphone)
                    assertNull(requireNotNull(TalkRuntime.call.value).localVideo)

                    val excess = SpaceVoiceSignalKey.create(address, SaitoWallet.fromPrivateKey("0".repeat(63) + "7"))
                    val full = SpaceVoiceRoom.read(request("room-join", JsonObject().apply {
                        addProperty("peer", 3); add("contact", excess.contact.json())
                    }))
                    assertEquals(SpaceVoiceRoom.Status.FULL, full.status)
                    assertFalse(full.admitted)
                    assertEquals(3, full.participants.size)

                    roomClick("Unmute")
                    grantCapturePermission()
                    waitMedia(guests, 10_000) { TalkRuntime.call.value?.microphone == true }
                    roomClick("Camera on")
                    grantCapturePermission()
                    waitMedia(guests, 10_000) { TalkRuntime.call.value?.localVideo != null }
                    waitMedia(guests, 25_000) { guests.all { guest -> guest.browser.media[local.key]?.let {
                        it.width >= 320 && it.height >= 240 && it.nonBlack > 500 && it.rms > 0.01
                    } == true } }

                    val received = requireNotNull(TalkRuntime.call.value).participants
                    val tracks = received.map { participant ->
                        val video = requireNotNull(participant.video)
                        val audio = requireNotNull(participant.audio)
                        val frames = AtomicInteger()
                        val audible = AtomicBoolean()
                        val videoSink = VideoSink { frame -> if (frame.buffer.width >= 320 && frame.buffer.height >= 240) frames.incrementAndGet() }
                        val audioSink = AudioTrackSink { data, bits, _, channels, count, _ ->
                            val pcm = data.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                            var energy = 0.0
                            if (bits == 16) for (offset in 0 until min(pcm.remaining(), count * channels * 2) step 2) {
                                val value = pcm.getShort(offset).toDouble() / 32768
                                energy += value * value
                            }
                            if (energy > 0.01) audible.set(true)
                        }
                        video.addSink(videoSink); audio.addSink(audioSink)
                        Triple(frames, audible, { runCatching { video.removeSink(videoSink) }; runCatching { audio.removeSink(audioSink) }; Unit })
                    }
                    try { waitMedia(guests, 10_000) { tracks.all { it.first.get() >= 3 && it.second.get() } } }
                    finally { tracks.forEach { it.third() } }
                    capture("room-three-peer-media")
                    val browserBefore = guests.map { requireNotNull(it.browser.audioBytes[local.key]) }
                    roomClick("Browse while in the room")
                    click("Spaces")
                    click("Red Square")
                    waitText("Return to Open lounge", 12_000)
                    assertEquals(initial.token, TalkRuntime.call.value?.token)
                    waitMedia(guests, 10_000) { guests.withIndex().all { (index, guest) ->
                        (guest.browser.audioBytes[local.key] ?: 0L) > browserBefore[index]
                    } }
                    capture("room-media-across-module")

                    val muted = SpaceVoiceRoom.read(request("room-mute-native"))
                    assertFalse(muted.participants.single { it.session == initial.token }.canSpeak)
                    waitMedia(guests, 10_000) { TalkRuntime.call.value?.let { !it.microphoneAllowed && !it.microphone } == true }
                    waitMedia(guests, 10_000) { guests.all { it.browser.media[local.key]?.rms?.let { rms -> rms < 0.01 } == true } }
                    click("Return to Open lounge")
                    showRoomItem(hasText("Your microphone is paused by a moderator."))
                    capture("room-media-moderation")
                    File(evidence, "room-media-result.json").writeText(JsonObject().apply {
                        addProperty("pid", Process.myPid())
                        addProperty("admittedParticipants", full.participants.size)
                        addProperty("fourthPeerRefused", true)
                        addProperty("decodedAudioAndVideoBothWays", true)
                        addProperty("audioAcrossModules", true)
                        addProperty("moderatorStoppedDecodedAudio", true)
                        addProperty("audioInputBuffers", samples.get())
                    }.toString())
                    TalkRuntime.end(initial.token)
                }
            }
        } finally {
            TalkRuntime.call.value?.takeIf { it.spaceRoom != null }?.let { TalkRuntime.end(it.token) }
            guests.asReversed().forEach(SpaceRoomBrowserFixture::close)
            RtcPlatform.audioInputForTest(null)
        }
    }

    private fun launch(fixture: JsonObject): ActivityScenario<MainActivity> {
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
        assertEquals(fixture.get("nativeWallet").asString, wallet.publicKey)
        val store = AndroidWalletStore(context)
        store.replace(store.loadOrCreate(), wallet)
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        val peer = argument("spacesPeerPort")
        return ActivityScenario.launch(Intent(Intent.ACTION_VIEW, Uri.parse(fixture.get("link").asString), context, MainActivity::class.java)
            .putExtra("feedPeerUrl", "ws://127.0.0.1:$peer/wsopen")
            .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:$peer/wsopen")
            .putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/"))
    }

    private fun waitMedia(guests: List<SpaceRoomBrowserFixture>, timeout: Long, condition: () -> Boolean) = ui.waitUntil(timeout) {
        check(guests.all { it.errors.isEmpty() && it.browser.errors.isEmpty() }) { "A Chromium room peer reported an error" }
        condition()
    }

    private fun grantCapturePermission() {
        val allow = By.res(Pattern.compile(".*:id/permission_allow(_foreground_only)?_button"))
        requireNotNull(device.wait(Until.findObject(allow), 5000)).click()
    }

    private fun assertPermissionAbsent(permission: String) =
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(permission))

    private fun observed() = SpaceVoiceRoom.read(request("room-observe"))

    private fun waitText(label: String, timeout: Long = 10_000) = ui.waitUntil(timeout) { ui.onNodeWithText(label).isDisplayed() }

    private fun click(label: String) {
        val node = ui.onNode(hasText(label) and hasClickAction() and isEnabled())
        ui.waitUntil(12_000) { node.isDisplayed() }
        node.performTouchInput { click() }
    }

    private fun showRoomItem(matcher: SemanticsMatcher) = ui.waitUntil(10_000) {
        runCatching { ui.onNodeWithTag("spaces-room-content").performScrollToNode(matcher) }.isSuccess
    }

    private fun roomClick(label: String) {
        showRoomItem(hasText(label) and hasClickAction())
        click(label)
    }

    private fun argument(name: String) = requireNotNull(InstrumentationRegistry.getArguments().getString(name)).toInt().also { require(it in 1024..65535) }

    private fun request(action: String, body: JsonObject = JsonObject()): JsonObject {
        val port = argument("spacesControlPort")
        val connection = URL("http://127.0.0.1:$port/$action").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 5000
            connection.readTimeout = 8000
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            require(connection.responseCode == 200)
            return connection.inputStream.use { source -> JsonParser.parseString(source.bufferedReader().readText()).asJsonObject }
        } finally { connection.disconnect() }
    }

    private fun capture(name: String) = captureNativeScreen(File(evidence, "$name.png"))
}
