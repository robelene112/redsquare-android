package nl.davidengelen.redsquare

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.talk.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Owned signed peer requests exercise native decline, cancellation, busy and the real unanswered deadline. */
class TalkDialPreviewTest {
    @Test fun directCallOutcomesNeverStartCaptureWithoutAcceptanceAndIgnoreRetiredRequests() {
        val instrumentation = InstrumentationRegistry.getInstrumentation(); val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val caller = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
        val other = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
        val wallets = AndroidWalletStore(context); val previous = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context); val previousBackend = backends.load()
        val files = ChatFixtureState(context, listOf("chatwork."), owner.publicKey)
        val reads = AtomicInteger(); RtcPlatform.audioInputForTest { _, _, _, _, _ -> reads.incrementAndGet() }
        val evidence = File(context.cacheDir, "talk-dial-evidence").apply { mkdirs() }
        var account: TalkAccount? = null; var token: String? = null; var primary: Throwable? = null
        fun await(label: String, limit: Long = 5000, goal: () -> Boolean) {
            val end = SystemClock.elapsedRealtime() + limit
            while (!goal() && SystemClock.elapsedRealtime() < end) Thread.sleep(25)
            assertTrue("$label: ${TalkRuntime.call.value}", goal())
        }
        try {
            files.prepare(); backends.save(SaitoBackend.OWNER); wallets.replace(previous, owner)
            val fixture = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||")
            fixture.start().use { server ->
                val endpoint = "ws://127.0.0.1:${server.port}/wsopen"
                fun send(wallet: SaitoWallet, room: TalkRoom, command: TalkDial, at: Long = System.currentTimeMillis()) {
                    val bytes = TalkProtocol.invite(wallet, owner.publicKey, room, command, at).applicationRequest(1)
                    fixture.sendIncoming(bytes.copyOfRange(5, bytes.size))
                }
                fun sent(command: TalkDial, room: TalkRoom): Boolean = fixture.relayedMessages.any { value ->
                    val signed = SaitoEnvelope.decode(SaitoEnvelope.fromSignedJson(value))
                    signed.body.get("request")?.asString == command.wire &&
                        signed.body.getAsJsonObject("data")?.get("call_id")?.asString == room.id
                }
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", endpoint).putExtra("feedOfficialPeerUrl", endpoint)).use {
                    withFailureCapture({
                        File(evidence, "failure-state.txt").writeText("${TalkRuntime.call.value}\n${TalkRuntime.diagnostic.value}\nrecordReads=${reads.get()}")
                        captureNativeScreen(File(evidence, "failure.png"))
                    }) {
                        TalkRuntime.visibility(true)
                        account = TalkRuntime.account(context, owner, SaitoBackend.OWNER, endpoint).also { it.attach() }
                        await("Owned Relay is authenticated") { fixture.authenticatedConnectionIds.isNotEmpty() }
                        val device = UiDevice.getInstance(instrumentation)
                        val incoming = TalkRoom(SaitoWallet.create().publicKey, caller.publicKey)
                        val invitationAt = System.currentTimeMillis()
                        send(caller, incoming, TalkDial.REQUEST, invitationAt)
                        await("Signed native invitation rings") { TalkRuntime.call.value?.phase == CallPhase.INCOMING }
                        token = requireNotNull(TalkRuntime.call.value).token
                        requireNotNull(device.wait(Until.findObject(By.text("Decline")), 5000)).click()
                        await("Decline ends the incoming call") { TalkRuntime.call.value?.phase == CallPhase.DECLINED }
                        await("The caller receives rejection") { sent(TalkDial.REJECT, incoming) }
                        send(caller, incoming, TalkDial.REQUEST, invitationAt)
                        Thread.sleep(200)
                        assertEquals("A retired invitation cannot ring again", CallPhase.DECLINED, TalkRuntime.call.value?.phase)
                        val cancelled = TalkRoom(SaitoWallet.create().publicKey, caller.publicKey)
                        send(caller, cancelled, TalkDial.REQUEST)
                        await("Next incoming call rings") { TalkRuntime.call.value?.phase == CallPhase.INCOMING }
                        token = requireNotNull(TalkRuntime.call.value).token
                        send(caller, cancelled, TalkDial.CANCEL)
                        await("Caller cancellation closes ringing") { TalkRuntime.call.value?.phase == CallPhase.ENDED }
                        fun dial(): TalkRoom {
                            val room = TalkRoom(SaitoWallet.create().publicKey, owner.publicKey)
                            assertTrue(runBlocking { requireNotNull(account).start(TalkLink(SaitoBackend.OWNER, room), caller.publicKey, false, true) })
                            token = requireNotNull(TalkRuntime.call.value).token
                            assertEquals(CallPhase.DIALING, TalkRuntime.call.value?.phase)
                            return room
                        }
                        val outgoing = dial()
                        val busyRoom = TalkRoom(SaitoWallet.create().publicKey, other.publicKey)
                        send(other, busyRoom, TalkDial.REQUEST)
                        await("A busy caller receives rejection") { sent(TalkDial.REJECT, busyRoom) }
                        assertEquals(token, TalkRuntime.call.value?.token)
                        TalkRuntime.end(requireNotNull(token))
                        await("Cancelling outgoing call closes it") { TalkRuntime.call.value?.phase == CallPhase.ENDED }
                        await("Remote caller receives the exact cancellation") { sent(TalkDial.CANCEL, outgoing) }
                        val rejected = dial()
                        send(caller, rejected, TalkDial.REJECT)
                        await("Remote rejection is shown honestly") { TalkRuntime.call.value?.phase == CallPhase.DECLINED }
                        dial()
                        await("Unanswered call reaches the real deadline", 50_000) { TalkRuntime.call.value?.phase == CallPhase.NO_ANSWER }
                        assertEquals("No path captured audio without acceptance", 0, reads.get())
                        assertNull(TalkRuntime.call.value?.localVideo)
                        File(evidence, "outcomes.txt").writeText("nativeDecline=true\nretiredRequestIgnored=true\nremoteCancel=true\nbusyRejectsOtherCall=true\nlocalCancel=true\nremoteReject=true\nrealNoAnswerDeadline=true\naudioCaptureReads=0\n")
                        fixture.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            RtcPlatform.audioInputForTest(null); token?.let(TalkRuntime::end); account?.detach(); TalkRuntime.visibility(false); Thread.sleep(300)
            cleanupAfterTest(primary, { files.restoreFiles() }, { files.restorePreferences() },
                { wallets.replace(wallets.loadOrCreate(), previous) }, { backends.save(previousBackend) },
                { context.stopService(Intent(context, TalkService::class.java)) })
        }
    }
}
