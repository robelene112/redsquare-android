package nl.davidengelen.redsquare

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import nl.davidengelen.redsquare.saito.SharedRtcPeers
import nl.davidengelen.redsquare.spaces.SpaceVoiceRoom
import nl.davidengelen.redsquare.spaces.SpaceVoiceSignalKey
import nl.davidengelen.redsquare.talk.CallPhase
import nl.davidengelen.redsquare.talk.TalkRuntime
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.webrtc.AudioTrack
import org.webrtc.AudioTrackSink
import org.webrtc.Logging
import org.webrtc.VideoSink
import org.webrtc.VideoTrack
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

    @Test fun anAttachedDraftAndTheKeyboardStayClearOfTheLiveCallBar() {
        val fixture = request("room-config")
        val draft = "Writing with an image while the room stays connected"
        launch(fixture).use { activity ->
            withFailureCapture({ capture("room-draft-attachment-failure") }) {
                waitText("0 people here", 25_000)
                activity.onActivity { assertEquals(Configuration.ORIENTATION_LANDSCAPE, it.resources.configuration.orientation) }
                roomClick("Join room")
                waitText("You’re in the room", 20_000)
                val token = requireNotNull(TalkRuntime.call.value).token
                assertEquals(token, observed().participants.single().session)

                switchToRedSquare()
                val createPost = ui.onNode(hasText("New post") and hasClickAction() and isEnabled())
                ui.waitUntil(12_000) { createPost.isDisplayed() }
                val touch = createPost.fetchSemanticsNode().touchBoundsInRoot
                val minimum = context.resources.displayMetrics.density * 48f
                assertTrue("New post must keep its full target with a call active",
                    touch.height >= minimum && touch.width >= minimum)
                createPost.performTouchInput { click() }
                val editor = ui.onNode(hasSetTextAction() and isEnabled())
                ui.waitUntil(12_000) { editor.isDisplayed() }
                editor.performTextReplacement(draft)
                ui.attachSyntheticImage()
                editor.performScrollTo().performTouchInput { click() }
                ui.waitUntil(10_000) {
                    var visible = false
                    activity.onActivity { native ->
                        visible = ViewCompat.getRootWindowInsets(native.window.decorView)
                            ?.isVisible(WindowInsetsCompat.Type.ime()) == true
                    }
                    visible && editor.isDisplayed()
                }
                editor.assertEditableTextEquals(draft)
                val returnAction = ui.onNode(hasText("Call") and hasClickAction())
                returnAction.assertIsDisplayed().assertIsEnabled()
                ui.onNode(hasText("End call") and hasClickAction()).assertIsDisplayed().assertIsEnabled()
                assertTrue("The call bar must not overlap the focused post editor",
                    returnAction.getUnclippedBoundsInRoot().bottom <= editor.getUnclippedBoundsInRoot().top)
                assertEquals(token, observed().participants.single().session)
                capture("room-draft-attachment-ime")

                androidx.test.espresso.Espresso.closeSoftKeyboard()
                ui.assertSyntheticRedImage()
                click("Return to Open lounge")
                waitText("You’re in the room", 15_000)
                assertEquals(token, observed().participants.single().session)
                switchToRedSquare()
                if (!editor.isDisplayed()) click("New post")
                ui.waitUntil(12_000) { editor.isDisplayed() }
                editor.assertEditableTextEquals(draft)
                ui.assertSyntheticRedImage()
                capture("room-draft-attachment-retained")
                click("Return to Open lounge")
                waitText("You’re in the room", 15_000)
                roomClick("Leave room")
                waitText("0 people here", 20_000)
                assertTrue(observed().participants.isEmpty())
                File(evidence, "room-draft-attachment.json").writeText(JsonObject().apply {
                    addProperty("pid", Process.myPid())
                    addProperty("roomSession", token)
                    addProperty("actualIme", true)
                    addProperty("exactDraftAndVisibleAttachment", true)
                }.toString())
            }
        }
    }

    @Test fun talkBackReadsTheQuietJoinAndActiveCallControls() {
        val fixture = request("room-config")
        val folder = evidence.resolve("talkback").apply { mkdir() }
        NativeTalkBackObservation(folder).observe {
            launch(fixture).use {
                waitText("0 people here", 25_000)
                assertTrue(observed().participants.isEmpty())
                focusAndHear("join-room", "Join room", folder, textLabel = true)
                click(automation, "Join room")
                waitText("You’re in the room", 20_000)
                val active = requireNotNull(TalkRuntime.call.value)
                assertEquals(active.token, observed().participants.single().session)
                assertFalse(active.microphone)
                assertFalse(active.camera)
                focusAndHear("microphone", "Unmute", folder, textLabel = true)
                focusAndHear("camera", "Camera on", folder, textLabel = true)
                focusAndHear("leave-room", "Leave room", folder, textLabel = true)
                click(automation, "Leave room")
                waitText("0 people here", 20_000)
                assertTrue(observed().participants.isEmpty())
            }
        }
    }

    @Test fun prejoinIsQuietAndTheCallReturnsFromAnotherModule() {
        val fixture = request("room-config")
        assertPermissionAbsent(Manifest.permission.RECORD_AUDIO)
        assertPermissionAbsent(Manifest.permission.CAMERA)
        val channel = fixture.get("channel").asString
        val space = fixture.get("space").asString
        launch(fixture).use { activity ->
            withFailureCapture({
                File(evidence, "room-ui-failure.txt").writeText("phase=${TalkRuntime.call.value?.phase} diagnostic=${TalkRuntime.diagnostic.value}")
                capture("room-failure")
            }) {
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

                ui.onNodeWithContentDescription("Switch Space or channel").performClick()
                ui.onNodeWithText("General").performScrollTo().performClick()
                val draft = ui.onNode(hasSetTextAction())
                ui.waitUntil(15_000) { draft.isDisplayed() }
                draft.performTouchInput { click() }
                ui.waitUntil(10_000) {
                    var visible = false
                    activity.onActivity { screen ->
                        visible = ViewCompat.getRootWindowInsets(screen.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
                    }
                    visible
                }
                draft.performTextInput("Room call and a local draft")
                waitText("Call")
                ui.onNode(hasText("Unmute") and hasClickAction()).assertIsDisplayed()
                ui.onNode(hasText("End call") and hasClickAction()).assertIsDisplayed()
                capture("room-bar-with-keyboard")
                click("Call")
                waitText("You’re in the room", 15_000)
                assertEquals(call.token, TalkRuntime.call.value?.token)

                roomClick("Browse while in the room")
                click("Return to Open lounge")
                waitText("You’re in the room", 15_000)
                assertEquals(call.token, TalkRuntime.call.value?.token)
                switchToRedSquare()
                waitText("Return to Open lounge", 12_000)
                capture("room-compact-bar")
                assertEquals(call.token, observed().participants.single().session)
                click("Return to Open lounge")
                waitText("You’re in the room", 15_000)

                try {
                    device.setOrientationLeft()
                    waitText("You’re in the room", 15_000)
                    assertEquals(call.token, TalkRuntime.call.value?.token)
                    assertFalse(requireNotNull(TalkRuntime.call.value).microphone)
                    capture("room-landscape")
                } finally {
                    device.setOrientationNatural()
                    device.unfreezeRotation()
                }
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
                    addProperty("roomControlsWithRealKeyboard", true)
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
        var receiveEvidence: List<String> = emptyList()
        var primary: Throwable? = null
        try {
            launch(fixture).use {
                withFailureCapture({
                    val call = TalkRuntime.call.value
                    val nativeVideo = call?.let { runCatching { runBlocking { SharedRtcPeers.roomVideoStatsForTest(it.token) } }.getOrNull() }
                    File(evidence, "room-media-failure.txt").writeText(
                        "phase=${call?.phase} diagnostic=${TalkRuntime.diagnostic.value}\n" +
                            receiveEvidence.joinToString("\n", postfix = "\n") +
                            "nativeVideo=$nativeVideo\n" + guests.joinToString("\n") {
                                "errors=${it.errors} chromium=${it.browser.errors} media=${it.browser.media}\n" +
                                    it.browser.diagnostics.takeLast(8).joinToString("\n")
                            })
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
                    Logging.enableLogToDebugOutput(Logging.Severity.LS_INFO)
                    waitMedia(guests, 25_000) { guests.all { guest ->
                        val other = guests.single { it !== guest }
                        guest.browser.media[other.contact.key]?.let { it.width >= 320 && it.nonBlack > 500 && it.rms > 0.01 } == true
                    } }
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
                        IncomingRoomProbe(participant.key, requireNotNull(participant.video), requireNotNull(participant.audio))
                    }
                    try {
                        waitMedia(guests, 10_000) { tracks.all(IncomingRoomProbe::decoded) }
                    } finally {
                        val current = TalkRuntime.call.value?.participants.orEmpty()
                        receiveEvidence = tracks.map { probe ->
                            val participant = current.firstOrNull { it.key == probe.key }
                            probe.report() + " camera=${participant?.camera} microphone=${participant?.microphone}" +
                                " videoStillCurrent=${participant?.video === probe.video} audioStillCurrent=${participant?.audio === probe.audio}"
                        }
                        File(evidence, "room-decoded-receive.txt").writeText(receiveEvidence.joinToString("\n", postfix = "\n"))
                        tracks.forEach(IncomingRoomProbe::close)
                    }
                    capture("room-three-peer-media")
                    val beforePauseAudio = guests.map { requireNotNull(it.browser.audioBytes[local.key]) }
                    roomClick("Camera off")
                    waitMedia(guests, 10_000) {
                        TalkRuntime.call.value?.localVideo == null && guests.withIndex().all { (index, guest) ->
                            guest.remoteCapture[local.session]?.camera == false &&
                                (guest.browser.audioBytes[local.key] ?: 0L) > beforePauseAudio[index] &&
                                (guest.browser.media[local.key]?.rms ?: 0.0) > 0.01
                        }
                    }
                    val beforeResume = guests.map { requireNotNull(it.browser.videoFrames[local.key]) }
                    roomClick("Camera on")
                    waitMedia(guests, 10_000) {
                        TalkRuntime.call.value?.localVideo != null && guests.withIndex().all { (index, guest) ->
                            guest.remoteCapture[local.session]?.camera == true &&
                                (guest.browser.videoFrames[local.key] ?: 0L) > beforeResume[index] + 2
                        }
                    }
                    val firstGuest = guests.first()
                    val secondGuest = guests.last()
                    fun receivedFrom(guest: SpaceRoomBrowserFixture) =
                        TalkRuntime.call.value?.participants?.firstOrNull { it.roomSession == guest.contact.session }
                    val incoming = requireNotNull(receivedFrom(firstGuest))
                    IncomingRoomProbe(incoming.key, requireNotNull(incoming.video), requireNotNull(incoming.audio)).use { probe ->
                        firstGuest.setCamera(false)
                        waitMedia(guests, 10_000) { receivedFrom(firstGuest)?.camera == false && receivedFrom(secondGuest)?.camera == true }
                        val beforeGuestResume = probe.visibleFrames()
                        firstGuest.setCamera(true)
                        waitMedia(guests, 10_000) {
                            receivedFrom(firstGuest)?.let { it.camera && it.video === probe.video } == true &&
                                probe.visibleFrames() > beforeGuestResume + 2
                        }
                    }
                    val guestModeration = JsonObject().apply { addProperty("peer", 1); addProperty("remove", false) }
                    val mutedGuest = SpaceVoiceRoom.read(request("room-moderate-guest", guestModeration))
                    assertFalse(mutedGuest.participants.single { it.session == firstGuest.contact.session }.canSpeak)
                    waitMedia(guests, 10_000) {
                        receivedFrom(firstGuest)?.audio?.enabled() == false && receivedFrom(secondGuest)?.audio?.enabled() == true &&
                            (secondGuest.browser.media[firstGuest.contact.key]?.rms ?: 0.0) > 0.01
                    }
                    val browserBefore = guests.map { requireNotNull(it.browser.audioBytes[local.key]) }
                    roomClick("Browse while in the room")
                    switchToRedSquare()
                    waitText("Return to Open lounge", 12_000)
                    assertEquals(initial.token, TalkRuntime.call.value?.token)
                    waitMedia(guests, 10_000) { guests.withIndex().all { (index, guest) ->
                        (guest.browser.audioBytes[local.key] ?: 0L) > browserBefore[index]
                    } }
                    capture("room-media-across-module")

                    val beforeSleep = guests.map { requireNotNull(it.browser.audioBytes[local.key]) }
                    device.sleep()
                    try {
                        assertFalse(device.isScreenOn)
                        val end = SystemClock.elapsedRealtime() + 8000
                        fun stillAudible() = guests.withIndex().all { (index, guest) ->
                            (guest.browser.audioBytes[local.key] ?: 0L) > beforeSleep[index] &&
                                (guest.browser.media[local.key]?.rms ?: 0.0) > 0.01
                        }
                        while (!stillAudible() && SystemClock.elapsedRealtime() < end) Thread.sleep(80)
                        assertTrue("Independent peers continued to decode audio with the screen off", stillAudible())
                        assertEquals(initial.token, TalkRuntime.call.value?.token)
                    } finally {
                        device.wakeUp()
                        device.executeShellCommand("wm dismiss-keyguard")
                    }
                    waitText("Return to Open lounge", 12_000)

                    fun pauseWriting(paused: Boolean) = SpaceVoiceRoom.read(request("room-pause-native-writing",
                        JsonObject().apply { addProperty("paused", paused) }))
                    val timedPause = pauseWriting(true)
                    assertFalse(timedPause.participants.single { it.session == initial.token }.canSpeak)
                    waitMedia(guests, 10_000) {
                        TalkRuntime.call.value?.let { it.token == initial.token && !it.microphoneAllowed && !it.microphone } == true &&
                            guests.all { (it.browser.media[local.key]?.rms ?: 1.0) < 0.01 }
                    }
                    click("Return to Open lounge")
                    showRoomItem(hasText("Your microphone is paused by a moderator."))
                    capture("room-media-writing-pause")
                    assertTrue(pauseWriting(false).participants.single { it.session == initial.token }.canSpeak)
                    waitMedia(guests, 10_000) { TalkRuntime.call.value?.let { it.microphoneAllowed && !it.microphone } == true }
                    roomClick("Unmute")
                    waitMedia(guests, 10_000) {
                        TalkRuntime.call.value?.let { it.token == initial.token && it.microphone } == true &&
                            guests.all { (it.browser.media[local.key]?.rms ?: 0.0) > 0.01 }
                    }

                    val muted = SpaceVoiceRoom.read(request("room-mute-native"))
                    assertFalse(muted.participants.single { it.session == initial.token }.canSpeak)
                    waitMedia(guests, 10_000) { TalkRuntime.call.value?.let { !it.microphoneAllowed && !it.microphone } == true }
                    waitMedia(guests, 10_000) { guests.all { it.browser.media[local.key]?.rms?.let { rms -> rms < 0.01 } == true } }
                    showRoomItem(hasText("Your microphone is paused by a moderator."))
                    capture("room-media-moderation")
                    firstGuest.expectRemoval = true
                    guestModeration.addProperty("remove", true)
                    assertEquals(2, SpaceVoiceRoom.read(request("room-moderate-guest", guestModeration)).participants.size)
                    firstGuest.assertRelayRejected(secondGuest.contact, "sender-retired")
                    secondGuest.assertRelayRejected(firstGuest.contact, "recipient-retired")
                    waitMedia(guests, 12_000) {
                        firstGuest.removed && receivedFrom(firstGuest) == null && receivedFrom(secondGuest)?.connected == true &&
                            TalkRuntime.call.value?.let { it.token == initial.token && it.phase == CallPhase.ACTIVE } == true
                    }
                    File(evidence, "room-media-result.json").writeText(JsonObject().apply {
                        addProperty("pid", Process.myPid())
                        addProperty("admittedParticipants", full.participants.size)
                        addProperty("fourthPeerRefused", true)
                        addProperty("decodedAudioAndVideoBothWays", true)
                        addProperty("audioAcrossModules", true)
                        addProperty("decodedAudioWithScreenOff", true)
                        addProperty("guestsDecodedEachOther", true)
                        addProperty("remoteCameraToggleObserved", true)
                        addProperty("nativeCameraResumedToBothGuests", true)
                        addProperty("remoteCameraResumedToNative", true)
                        addProperty("receiverBlockedModeratedGuest", true)
                        addProperty("remainingPeerSurvivedRemoval", true)
                        addProperty("revokedSignalDenied", true)
                        addProperty("moderatorStoppedDecodedAudio", true)
                        addProperty("timedPauseStoppedDecodedAudio", true)
                        addProperty("explicitUnmuteRestoredDecodedAudio", true)
                        addProperty("audioInputBuffers", samples.get())
                    }.toString())
                    TalkRuntime.end(initial.token)
                }
            }
        } catch (failure: Throwable) {
            primary = failure
            throw failure
        } finally {
            cleanupAfterTest(primary,
                { TalkRuntime.call.value?.takeIf { it.spaceRoom != null }?.let { TalkRuntime.end(it.token) } },
                *guests.asReversed().map { guest -> { guest.close() } }.toTypedArray(),
                { RtcPlatform.audioInputForTest(null) })
        }
    }

    private class IncomingRoomProbe(val key: String, val video: VideoTrack, val audio: AudioTrack) : AutoCloseable {
        private val frames = AtomicInteger()
        private val audioBuffers = AtomicInteger()
        private val audible = AtomicBoolean()
        @Volatile private var videoSize = "none"
        @Volatile private var audioFormat = "none"
        private val videoSink = VideoSink { frame ->
            videoSize = "${frame.buffer.width}x${frame.buffer.height}"
            if (frame.buffer.width >= 320 && frame.buffer.height >= 240) {
                val pixels = frame.buffer.toI420() ?: return@VideoSink
                try {
                    val center = pixels.height / 2 * pixels.strideY + pixels.width / 2
                    if ((pixels.dataY.get(center).toInt() and 255) > 25) frames.incrementAndGet()
                } finally { pixels.release() }
            }
        }
        private val audioSink = AudioTrackSink { data, bits, rate, channels, count, _ ->
            audioBuffers.incrementAndGet()
            audioFormat = "$bits bits, $rate Hz, $channels channels, $count samples, ${data.remaining()} bytes"
            val pcm = data.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            var energy = 0.0
            if (bits == 16) for (offset in 0 until min(pcm.remaining(), count * channels * 2) step 2) {
                val value = pcm.getShort(offset).toDouble() / 32768
                energy += value * value
            }
            if (energy > 0.01) audible.set(true)
        }

        init {
            video.addSink(videoSink)
            audio.addSink(audioSink)
        }

        fun decoded() = frames.get() >= 3 && audible.get()
        fun visibleFrames() = frames.get()

        fun report() = "$key qualifyingFrames=${frames.get()} lastFrame=$videoSize audioBuffers=${audioBuffers.get()}" +
            " audioFormat=$audioFormat audible=${audible.get()} videoEnabled=${runCatching { video.enabled() }.getOrNull()}" +
            " audioEnabled=${runCatching { audio.enabled() }.getOrNull()}"

        override fun close() {
            runCatching { video.removeSink(videoSink) }
            runCatching { audio.removeSink(audioSink) }
        }
    }

    @Test fun recordAnActiveCameraForTheOperatingSystemRevocation() {
        assertPermissionAbsent(Manifest.permission.RECORD_AUDIO)
        assertPermissionAbsent(Manifest.permission.CAMERA)
        launch(request("room-config")).use {
            withFailureCapture({ capture("room-camera-checkpoint-failure") }) {
                waitText("0 people here", 25_000)
                roomClick("Join room")
                waitText("You’re in the room", 20_000)
                val token = requireNotNull(TalkRuntime.call.value).token
                roomClick("Camera on")
                grantCapturePermission()
                val frames = waitCameraFrames()
                assertPermissionAbsent(Manifest.permission.RECORD_AUDIO)
                assertFalse(requireNotNull(TalkRuntime.call.value).microphone)
                capture("room-camera-without-microphone")

                device.sleep()
                val sleepingBy = SystemClock.elapsedRealtime() + 5000
                while (TalkRuntime.call.value?.camera == true && SystemClock.elapsedRealtime() < sleepingBy) Thread.sleep(60)
                assertFalse("The owned Android screen must be off", device.isScreenOn)
                val sleeping = requireNotNull(TalkRuntime.call.value)
                assertEquals(token, sleeping.token)
                assertEquals(CallPhase.ACTIVE, sleeping.phase)
                assertFalse(sleeping.camera)
                assertNull(sleeping.localVideo)
                assertEquals(token, observed().participants.single().session)
                device.wakeUp()
                device.executeShellCommand("wm dismiss-keyguard")
                roomClick("Camera on")
                val resumedFrames = waitCameraFrames()
                assertEquals(token, TalkRuntime.call.value?.token)
                capture("room-camera-before-revoke")
                val checkpoint = File(evidence, "room-camera-checkpoint.json")
                val written = File(evidence, "room-camera-checkpoint.pending")
                written.writeText(JsonObject().apply {
                    addProperty("pid", Process.myPid())
                    addProperty("session", token)
                    addProperty("cameraFrames", frames)
                    addProperty("resumedCameraFrames", resumedFrames)
                    addProperty("screenOffPausedVideo", true)
                }.toString())
                check(written.renameTo(checkpoint))
                // Let the host revoke the actual Android permission while the camera is sending.
                val deadline = SystemClock.elapsedRealtime() + 45_000
                while (SystemClock.elapsedRealtime() < deadline) {
                    if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED &&
                        TalkRuntime.call.value?.let { !it.camera && it.localVideo == null } == true) {
                        File(evidence, "room-camera-revoked-live.txt").writeText("Camera turned off in the existing process\n")
                    }
                    Thread.sleep(100)
                }
                error("The native revocation harness did not finish the checkpoint")
            }
        }
    }

    @Test fun aNewProcessCanExplicitlyRejoinAfterTheCameraPermissionWasRevoked() {
        val before = JsonParser.parseString(File(evidence, "room-camera-checkpoint.json").readText()).asJsonObject
        assertNotEquals(before.get("pid").asInt, Process.myPid())
        assertPermissionAbsent(Manifest.permission.CAMERA)
        assertPermissionAbsent(Manifest.permission.RECORD_AUDIO)
        launch(request("room-config")).use { activity ->
            withFailureCapture({ capture("room-camera-recovery-failure") }) {
                waitText("0 people here", 25_000)
                assertTrue(observed().participants.isEmpty())
                assertTrue(TalkRuntime.call.value?.phase?.live != true)
                capture("room-recovered-quiet")
                roomClick("Join room")
                waitText("You’re in the room", 20_000)
                val call = requireNotNull(TalkRuntime.call.value)
                assertNotEquals(before.get("session").asString, call.token)
                assertEquals(call.token, observed().participants.single().session)
                assertFalse(call.microphone); assertFalse(call.camera); assertNull(call.localVideo)
                capture("room-rejoined-quiet")
                roomClick("Leave room")
                ui.waitUntil(10_000) { TalkRuntime.call.value?.phase == CallPhase.ENDED }
                waitText("0 people here", 12_000)
                assertTrue(observed().participants.isEmpty())

                roomClick("Join room")
                waitText("You’re in the room", 20_000)
                val backendStore = AndroidBackendStore(context)
                try {
                    backendStore.save(SaitoBackend.OFFICIAL)
                    ui.waitUntil(10_000) { TalkRuntime.call.value?.phase == CallPhase.INTERRUPTED }
                    assertNull(TalkRuntime.call.value?.localVideo)
                    waitText("0 people here", 15_000)
                } finally { backendStore.save(SaitoBackend.OWNER) }
                activity.recreate()
                waitText("0 people here", 20_000)
                roomClick("Join room")
                waitText("You’re in the room", 20_000)
                val wallets = AndroidWalletStore(context)
                val wallet = wallets.loadExisting()
                val replacement = SaitoWallet.fromPrivateKey("0".repeat(63) + "7")
                try {
                    wallets.replace(wallet, replacement)
                    ui.waitUntil(10_000) { TalkRuntime.call.value?.phase == CallPhase.INTERRUPTED }
                    assertNull(TalkRuntime.call.value?.localVideo)
                    waitText("0 people here", 15_000)
                } finally { wallets.replace(replacement, wallet) }
                File(evidence, "room-camera-recovery.json").writeText(JsonObject().apply {
                    addProperty("pid", Process.myPid())
                    addProperty("explicitFreshSession", true)
                    addProperty("cameraPermissionStillRevoked", context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED)
                    addProperty("nodeChangeEndedOriginalScope", true)
                    addProperty("walletChangeEndedOriginalScope", true)
                    addProperty("roomSurvived", observed().status == SpaceVoiceRoom.Status.AVAILABLE)
                }.toString())
            }
        }
    }

    private fun waitCameraFrames(): Int {
        ui.waitUntil(10_000) { TalkRuntime.call.value?.camera == true && TalkRuntime.call.value?.localVideo != null }
        val track = requireNotNull(TalkRuntime.call.value?.localVideo)
        val frames = AtomicInteger()
        val sink = VideoSink { frame -> if (frame.buffer.width > 0 && frame.buffer.height > 0) frames.incrementAndGet() }
        track.addSink(sink)
        try { ui.waitUntil(10_000) { frames.get() >= 3 } } finally { runCatching { track.removeSink(sink) } }
        return frames.get()
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

    private fun switchToRedSquare() {
        val selector = ui.onNodeWithContentDescription("Switch app: Spaces")
        ui.waitUntil(10_000) { selector.isDisplayed() }
        selector.performClick()
        ui.onNodeWithText("Red Square").performScrollTo().performClick()
        ui.waitUntil(12_000) { ui.onNodeWithContentDescription("Switch app: Red Square").isDisplayed() }
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
            require(connection.responseCode == 200) { "Owned fixture $action returned HTTP ${connection.responseCode}" }
            return connection.inputStream.use { source -> JsonParser.parseString(source.bufferedReader().readText()).asJsonObject }
        } finally { connection.disconnect() }
    }

    private fun capture(name: String) = captureNativeScreen(File(evidence, "$name.png"))
}
