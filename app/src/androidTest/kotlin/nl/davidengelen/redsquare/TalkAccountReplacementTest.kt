package nl.davidengelen.redsquare

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.UiDevice
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.talk.*
import org.junit.Test
import java.io.File

/** An explicit actor barrier reproduces replacement acquisition before an older detach is drained. */
class TalkAccountReplacementTest {
    @Test fun replacementReceivesIncomingCallsAndStartsACallAfterTheOldDetach() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        check(TalkRuntime.call.value?.phase?.live != true)
        val wallets = AndroidWalletStore(context); val previous = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context); val previousBackend = backends.load()
        val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Public unsafe fixture only.
        val caller = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
        val files = ChatFixtureState(context, listOf("personal.${owner.publicKey}.", "chatwork.", "feed.owner."), owner.publicKey)
        val releaseOld = CompletableDeferred<Unit>(); val releaseReplacement = CompletableDeferred<Unit>()
        var old: TalkAccount? = null; var replacement: TalkAccount? = null
        var oldDetached = false
        var token: String? = null
        val ownedRooms = mutableSetOf(caller.publicKey)
        fun endOwnedCall() {
            TalkRuntime.call.value?.takeIf { it.owner == owner.publicKey && it.savedBackend == SaitoBackend.OWNER &&
                it.link.room.id in ownedRooms }?.let { TalkRuntime.end(it.token) }
        }
        val record = JsonObject()
        val folder = File(context.cacheDir, "talk-account-replacement-evidence").apply { mkdirs() }
        var primary: Throwable? = null
        try {
            files.prepare(); wallets.replace(previous, owner); backends.save(SaitoBackend.OWNER)
            val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||")
            peer.start().use { server ->
                val endpoint = "ws://127.0.0.1:${server.port}/wsopen"
                // This foreground debug host has no automatic Talk account of its own.
                var hostFailure: Throwable? = null
                try {
                    ActivityScenario.launch<CompatibilityActivity>(Intent(context, CompatibilityActivity::class.java)).use {
                        withFailureCapture({
                            File(folder, "stage.json").writeText(record.toString())
                            captureNativeScreen(File(folder, "failure.png"))
                        }) {
                            runBlocking {
                                TalkRuntime.visibility(true)
                                val original = TalkRuntime.account(context, owner, SaitoBackend.OWNER, endpoint).also { old = it; it.attach() }
                                await("Original incoming listener") { original.online.value && RealtimeIncoming.hasReceiver(owner.publicKey, endpoint) }
                                val entered = CompletableDeferred<Unit>()
                                check(original.holdActorForTest(entered, releaseOld))
                                withTimeout(5_000) { entered.await() }
                                original.detach(); oldDetached = true
                                record.addProperty("oldDetachQueuedAt", SystemClock.elapsedRealtime())
                                val next = TalkRuntime.account(context, owner, SaitoBackend.OWNER, endpoint).also { replacement = it; it.attach() }
                                record.addProperty("replacementAcquiredAt", SystemClock.elapsedRealtime())
                                val drained = CompletableDeferred<Unit>()
                                check(next.holdActorForTest(drained, releaseReplacement))
                                releaseOld.complete(Unit)
                                record.addProperty("oldDetachReleasedAt", SystemClock.elapsedRealtime())
                                withTimeout(5_000) { drained.await() }
                                releaseReplacement.complete(Unit)
                                await("Replacement incoming listener survives old disposal") {
                                    next.online.value && RealtimeIncoming.hasReceiver(owner.publicKey, endpoint)
                                }
                                val invitation = TalkProtocol.invite(caller, owner.publicKey,
                                    TalkRoom(caller.publicKey, caller.publicKey), TalkDial.REQUEST, System.currentTimeMillis())
                                    .applicationRequest(1).drop(5).toByteArray()
                                peer.sendIncoming(invitation)
                                await("Actual incoming call reaches the replacement") {
                                    TalkRuntime.call.value?.let { it.incoming && it.owner == owner.publicKey && it.direct == caller.publicKey && it.phase == CallPhase.INCOMING } == true
                                }
                                token = requireNotNull(TalkRuntime.call.value).token
                                record.addProperty("incomingReachedReplacement", true)
                                TalkRuntime.end(requireNotNull(token))
                                await("Incoming call ends") { TalkRuntime.call.value?.phase?.live == false }
                                val outgoingRoom = TalkRoom(SaitoWallet.create().publicKey, owner.publicKey)
                                ownedRooms.add(outgoingRoom.id)
                                check(next.start(TalkLink(SaitoBackend.OWNER, outgoingRoom),
                                    null, camera = false, microphone = false)) { "Replacement must start a controlled call" }
                                val call = requireNotNull(TalkRuntime.call.value).also { token = it.token }
                                check(call.owner == owner.publicKey && call.savedBackend == SaitoBackend.OWNER && call.phase.started)
                                await("Real foreground call notification") {
                                    context.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == TalkService.ID }
                                }
                                val callUri = Uri.Builder().scheme("redsquare-talk").authority("call").appendPath(call.token)
                                    .appendQueryParameter("owner", owner.publicKey).appendQueryParameter("backend", SaitoBackend.OWNER.id).build()
                                val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).setData(callUri)
                                    .putExtra("feedPeerUrl", endpoint).putExtra("feedOfficialPeerUrl", endpoint)).use { activity ->
                                    check(device.wait(Until.hasObject(By.text(context.getString(R.string.talk_end))), 5_000))
                                    activity.recreate()
                                    check(device.wait(Until.hasObject(By.text(context.getString(R.string.talk_end))), 5_000))
                                    check(TalkRuntime.call.value?.let { it.token == token && it.owner == owner.publicKey &&
                                        it.savedBackend == SaitoBackend.OWNER && it.phase.started } == true)
                                    record.addProperty("actualActivityRecreationRetainsCall", true)
                                    captureNativeScreen(File(folder, "recreated-active-call.png"))
                                }
                                TalkRuntime.visibility(false)
                                check(TalkRuntime.call.value?.let { it.token == token && it.phase.started } == true)
                                TalkRuntime.visibility(true)
                                record.addProperty("controlledCallStarted", true)
                                record.addProperty("activeCallSurvivesBackground", true)
                                check(wallets.loadExisting().exportJson() == owner.exportJson()) { "Activity replacement must preserve the wallet" }
                                check(backends.load() == SaitoBackend.OWNER)
                                peer.assertHealthy()
                                File(folder, "stage.json").writeText(record.toString())
                                captureNativeScreen(File(folder, "replacement-call.png"))
                                TalkRuntime.end(requireNotNull(token))
                                await("Controlled call releases ownership") { TalkRuntime.call.value?.phase?.live == false }
                            }
                        }
                    }
                } catch (failure: Throwable) { hostFailure = failure; throw failure }
                finally {
                    cleanupAfterTest(hostFailure,
                        { releaseOld.complete(Unit); releaseReplacement.complete(Unit) },
                        { endOwnedCall() },
                        { if (!oldDetached) { old?.detach(); oldDetached = true } },
                        { replacement?.detach(); replacement = null },
                        { TalkRuntime.visibility(false) },
                        { await("Disposed fixture listener releases its socket") {
                            endOwnedCall()
                            !RealtimeIncoming.hasReceiver(owner.publicKey, endpoint)
                        } })
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary,
                { releaseOld.complete(Unit); releaseReplacement.complete(Unit) },
                { endOwnedCall() },
                { if (!oldDetached) old?.detach() },
                { replacement?.detach() },
                { TalkRuntime.visibility(false) },
                { context.stopService(Intent(context, TalkService::class.java)) },
                { files.restoreFiles() }, { files.restorePreferences() },
                { wallets.replace(wallets.loadOrCreate(), previous) }, { backends.save(previousBackend) })
        }
    }

    private fun await(label: String, ready: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (!ready() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(25)
        check(ready()) { label }
    }
}
