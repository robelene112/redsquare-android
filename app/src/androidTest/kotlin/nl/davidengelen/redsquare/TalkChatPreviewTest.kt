package nl.davidengelen.redsquare

import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.gson.*
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.talk.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.ByteOrder
import java.util.concurrent.atomic.*
import kotlin.math.*

class TalkChatPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun aPrivateChatAudioCallUsesActualMediaAndPreservesTheUnsentDraft() = call(false)
    @Test fun aPrivateChatVideoCallUsesActualMediaAndPreservesTheUnsentDraft() = call(true)
    private fun call(video: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val caller = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
        val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "2").withChatContact(caller.publicKey)
        val wallets = AndroidWalletStore(context); val previous = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context); val previousBackend = backends.load()
        val files = ChatFixtureState(context, listOf("chatwork.", "chat.owner.${owner.publicKey}.", "displaynames.", "namemap."), owner.publicKey)
        val evidence = File(context.cacheDir, "talk-chat-evidence").apply { mkdirs() }
        val browser = AtomicReference<TalkBrowserPeer>(); val sequence = AtomicInteger(8000)
        val fixture = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = { "[]" }, relayResponse = { inner ->
            val envelope = SaitoEnvelope.fromSignedJson(inner); val signed = SaitoEnvelope.decode(envelope)
            if (caller.publicKey in signed.recipients) browser.get().receive(envelope)
            "{\"success\":1}"
        })
        val draft = "Keep this unsent chat text through the ${if (video) "video" else "audio"} call"
        var token: String? = null; var primary: Throwable? = null; var sample = 0L
        val device = UiDevice.getInstance(instrumentation)
        RtcPlatform.audioInputForTest { buffer, channels, rate, bytes ->
            val pcm = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            for (offset in 0 until bytes step (2 * channels)) {
                val value = (sin(2 * PI * 440 * sample++ / rate) * 7000).toInt().toShort()
                for (channel in 0 until channels) pcm.putShort(offset + 2 * channel, value)
            }
        }
        fun await(label: String, timeout: Long = 5000, goal: () -> Boolean) = ui.waitUntil(timeout) {
            browser.get()?.errors?.firstOrNull()?.let { throw AssertionError("$label: $it") }
            goal()
        }
        try {
            files.prepare(); backends.save(SaitoBackend.OWNER); wallets.replace(previous, owner)
            fixture.start().use { server ->
                val endpoint = "ws://127.0.0.1:${server.port}/wsopen"
                TalkBrowserPeer(caller, owner, "#e02020", 660, send@{ envelope ->
                    val signed = runCatching { SaitoEnvelope.decode(envelope) }.getOrNull()
                    if (signed != null) {
                        check(signed.author == caller.publicKey)
                        if (owner.publicKey !in signed.recipients) return@send
                    } else check(requireNotNull(TalkProtocol.read(envelope, owner.publicKey)).sender == caller.publicKey)
                    val value = JsonParser.parseString(envelope).asJsonObject
                    val header = java.util.Base64.getDecoder().decode(value.get("t").asString)
                    val body = java.util.Base64.getDecoder().decode(value.get("m").asString)
                    java.nio.ByteBuffer.wrap(header).putInt(8, body.size)
                    fixture.sendIncoming(header + body, sequence.incrementAndGet())
                }).use { peer ->
                    browser.set(peer)
                    ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                        .putExtra("feedPeerUrl", endpoint).putExtra("feedOfficialPeerUrl", endpoint)).use { activity ->
                        withFailureCapture({
                            File(evidence, "failure-$video.txt").writeText("${TalkRuntime.call.value}\n${peer.errors}\nmedia=${peer.media}\ndiagnostics=${peer.diagnostics}\n")
                            captureNativeScreen(File(evidence, "failure-$video.png"))
                        }) {
                            await("Chat navigation ready", 15_000) { ui.onNodeWithText("Chat").isDisplayed() }
                            ui.onNodeWithText("Chat").performClick()
                            await("Exact private contact ready", 15_000) { ui.onNodeWithText(caller.publicKey).isDisplayed() }
                            ui.onNodeWithText(caller.publicKey).performClick()
                            await("Private composer ready", 15_000) { ui.onNode(hasText("Message") and hasSetTextAction() and isEnabled()).isDisplayed() }
                            ui.onNode(hasText("Message") and hasSetTextAction()).performClick().performTextInput(draft)
                            await("Actual message keyboard is shown") { focusedWindowImeBottom() > 0 }
                            device.pressBack()
                            await("Actual message keyboard has closed") { focusedWindowImeBottom() == 0 }
                            ui.onNodeWithText("Call options").assertIsDisplayed().performClick()
                            ui.onNodeWithText(if (video) "Video call" else "Audio call").assertIsDisplayed().performClick()
                            await("Native direct prejoin") { ui.onNodeWithText("Ready to call?").isDisplayed() }
                            assertTrue(TalkRuntime.call.value?.phase?.live != true)
                            assertEquals(0, peer.captures.get())
                            ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Start call") and hasClickAction())
                            ui.onNode(hasText("Start call") and hasClickAction()).performClick()
                            await("The actual web dialer rings") { peer.ringing.get() }
                            token = requireNotNull(TalkRuntime.call.value).token
                            peer.answer()
                            await("The accepted call decodes real native PCM at the peer", 25_000) {
                                TalkRuntime.call.value?.phase == CallPhase.ACTIVE &&
                                    peer.media[owner.publicKey]?.let { it.rms > 0.01 && (!video || it.width >= 320 && it.nonBlack > 500) } == true
                            }
                            assertEquals(video, TalkRuntime.call.value?.camera)
                            assertEquals(!video, TalkRuntime.call.value?.localVideo == null)
                            ui.onNodeWithText("Audio output").assertIsDisplayed().performClick()
                            await("Speaker choice available without unrelated permissions") { ui.onNode(hasText("Speaker") and hasClickAction()).isDisplayed() }
                            ui.onNode(hasText("Speaker") and hasClickAction()).performClick()
                            val audio = context.getSystemService(AudioManager::class.java)
                            await("Actual speaker route selected") {
                                if (android.os.Build.VERSION.SDK_INT >= 31) audio.communicationDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                                else audio.isSpeakerphoneOn
                            }
                            captureNativeScreen(File(evidence, "connected-$video.png"))
                            ui.onNode(hasText("End call") and hasClickAction()).performClick()
                            await("Native End action releases the call") { TalkRuntime.call.value?.phase == CallPhase.ENDED }
                            ui.onNodeWithContentDescription("Switch app: Saito Talk").performClick()
                            ui.onNode(hasText("Red Square") and hasClickAction()).performClick()
                            await("Exact unsent private draft restored", 15_000) { ui.onNode(hasText(draft) and hasSetTextAction()).isDisplayed() }
                            assertEquals(owner.exportJson(), wallets.loadExisting().exportJson())
                            assertEquals(SaitoBackend.OWNER, backends.load())
                            captureNativeScreen(File(evidence, "draft-return-$video.png"))
                            File(evidence, "chat-$video.json").writeText("{\"video\":$video,\"realRemotePcm\":true,\"speakerSelected\":true,\"nativeStartEnd\":true,\"draftRetained\":true}")
                            fixture.assertHealthy()
                        }
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            RtcPlatform.audioInputForTest(null); token?.let(TalkRuntime::end); TalkRuntime.visibility(false); Thread.sleep(300)
            cleanupAfterTest(primary, { files.restoreFiles() }, { files.restorePreferences() }, { wallets.replace(wallets.loadOrCreate(), previous) },
                { backends.save(previousBackend) }, { context.stopService(Intent(context, TalkService::class.java)) })
        }
    }
}
