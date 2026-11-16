package nl.davidengelen.redsquare

import android.app.NotificationManager
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.talk.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class TalkRoutesRecoveryTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun oneHealthyRouteStaysAvailableAndDeliversAnInvitationWhileTheOtherFails() = exercise(false)
    @Test fun losingAllRoutesInterruptsTheExactStartedCallAndRecoveryDoesNotRestartIt() = exercise(true)

    private fun exercise(interruptActiveCall: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        check(TalkRuntime.call.value?.phase?.live != true)
        val wallets = AndroidWalletStore(context); val previous = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context); val previousBackend = backends.load()
        val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Public unsafe fixture only.
        val caller = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
        val files = ChatFixtureState(context, listOf("personal.${owner.publicKey}.", "chatwork.", "feed.owner."), owner.publicKey)
        val mode = if (interruptActiveCall) "active-interruption" else "mixed-routes"
        val folder = File(context.cacheDir, "talk-routes-evidence/$mode").apply { mkdirs() }
        val record = JsonObject()
        fun endOwnedInvitation() {
            TalkRuntime.call.value?.takeIf { it.owner == owner.publicKey && it.savedBackend == SaitoBackend.OWNER &&
                it.link.room.id == caller.publicKey }?.let { TalkRuntime.end(it.token) }
        }
        var primary: Throwable? = null
        try {
            files.prepare(); wallets.replace(previous, owner); backends.save(SaitoBackend.OWNER)
            val first = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||").apply { rejectNewConnections = !interruptActiveCall }
            val second = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||")
            first.start().use { firstServer -> second.start().use { secondServer ->
                val primaryEndpoint = "ws://127.0.0.1:${firstServer.port}/wsopen"
                val fallbackEndpoint = "ws://127.0.0.1:${secondServer.port}/wsopen"
                var hostFailure: Throwable? = null
                try {
                    ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                        .putExtra("feedPeerUrl", primaryEndpoint).putExtra("feedOfficialPeerUrl", primaryEndpoint)
                        .putExtra("talkFallbackPeerUrl", fallbackEndpoint)).use {
                        withFailureCapture({
                            File(folder, "stage.json").writeText(record.toString())
                            captureNativeScreen(File(folder, "failure.png"))
                        }) {
                            ui.waitUntil(20_000) { ui.onNodeWithContentDescription("Switch app: Red Square").isDisplayed() }
                            ui.switchStackModule("Red Square", "Saito Talk", scrollTarget = true)
                            val ready = context.getString(R.string.talk_available)
                            ui.waitUntil(20_000) {
                                runCatching {
                                    ui.onNode(hasVerticalScrollAction()).performScrollToNode(hasText(ready))
                                    ui.onNodeWithText(ready).isDisplayed()
                                }.getOrDefault(false)
                            }
                            check(second.authenticatedConnectionIds.size == 1) { "One owned fallback connection must be authenticated" }
                            check(first.connectionAttempts.get() > 0)
                            if (!interruptActiveCall) check(first.authenticatedConnectionIds.isEmpty())
                            record.addProperty("healthyFallbackAuthenticated", true)
                            record.addProperty("primaryFailing", !interruptActiveCall)
                            captureNativeScreen(File(folder, "ready-with-fallback.png"))
                            second.sendIncoming(TalkProtocol.invite(caller, owner.publicKey,
                                TalkRoom(caller.publicKey, caller.publicKey), TalkDial.REQUEST, System.currentTimeMillis())
                                .applicationRequest(1).drop(5).toByteArray())
                            ui.waitUntil(10_000) { ui.onNodeWithText(context.getString(R.string.talk_incoming)).isDisplayed() }
                            val call = requireNotNull(TalkRuntime.call.value)
                            check(call.owner == owner.publicKey && call.savedBackend == SaitoBackend.OWNER && call.direct == caller.publicKey)
                            record.addProperty("incomingFromHealthyFallback", true)
                            if (interruptActiveCall) {
                                ui.onNodeWithText(context.getString(R.string.talk_audio_only)).performClick()
                                ui.waitUntil(15_000) { TalkRuntime.call.value?.let { it.token == call.token && it.phase.started } == true &&
                                    context.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == TalkService.ID } }
                                first.goOffline(); second.goOffline()
                                ui.waitUntil(15_000) { TalkRuntime.call.value?.let { it.token == call.token && it.phase == CallPhase.INTERRUPTED } == true }
                                ui.waitUntil(10_000) { context.getSystemService(NotificationManager::class.java).activeNotifications.none { it.id == TalkService.ID } }
                                record.addProperty("exactStartedCallInterrupted", true)
                                captureNativeScreen(File(folder, "all-routes-call-interrupted.png"))
                                first.rejectNewConnections = false; second.rejectNewConnections = false
                                ui.waitUntil(20_000) {
                                    runCatching {
                                        ui.onNode(hasVerticalScrollAction()).performScrollToNode(hasText(ready))
                                        ui.onNodeWithText(ready).isDisplayed()
                                    }.getOrDefault(false)
                                }
                                check(TalkRuntime.call.value?.let { it.token == call.token && it.phase == CallPhase.INTERRUPTED } == true)
                                check(context.getSystemService(NotificationManager::class.java).activeNotifications.none { it.id == TalkService.ID })
                                record.addProperty("networkRecoveryDoesNotRestartCall", true)
                            } else {
                                captureNativeScreen(File(folder, "incoming-on-healthy-route.png"))
                                ui.onNodeWithText(context.getString(R.string.talk_decline)).performClick()
                                ui.waitUntil(10_000) { TalkRuntime.call.value?.phase?.live == false }
                            }
                            check(wallets.loadExisting().exportJson() == owner.exportJson()) { "Route recovery must preserve the wallet" }
                            check(backends.load() == SaitoBackend.OWNER)
                            first.assertHealthy(); second.assertHealthy()
                            File(folder, "stage.json").writeText(record.toString())
                        }
                    }
                } catch (failure: Throwable) { hostFailure = failure; throw failure }
                finally {
                    cleanupAfterTest(hostFailure, { first.rejectNewConnections = false; second.rejectNewConnections = false },
                        { TalkRuntime.visibility(false) }, { endOwnedInvitation() }, {
                            ui.waitUntil(10_000) {
                                endOwnedInvitation()
                                !RealtimeIncoming.hasReceiver(owner.publicKey, primaryEndpoint) &&
                                    !RealtimeIncoming.hasReceiver(owner.publicKey, fallbackEndpoint) &&
                                    first.authenticatedConnectionIds.isEmpty() && second.authenticatedConnectionIds.isEmpty()
                            }
                            record.addProperty("ownedListenersAndConnectionsReleased", true)
                            File(folder, "stage.json").writeText(record.toString())
                        })
                }
            } }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally { cleanupAfterTest(primary, { TalkRuntime.visibility(false) }, { endOwnedInvitation() },
            { files.restoreFiles() }, { files.restorePreferences() },
            { wallets.replace(wallets.loadOrCreate(), previous) }, { backends.save(previousBackend) }) }
    }
}
