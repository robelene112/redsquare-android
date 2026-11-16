package nl.davidengelen.redsquare

import android.content.Intent
import android.os.SystemClock
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

class TalkIdleRecoveryTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun visibleIdleTalkRecoversWithoutNavigationAndReceivesAFreshIncomingCall() = exercise(false)
    @Test fun launchDuringOutageShowsTruthfulFailureAndManualRetryRecoversIncomingCalls() = exercise(true)

    private fun exercise(initialOutage: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        check(TalkRuntime.call.value?.phase?.live != true)
        val wallets = AndroidWalletStore(context); val previous = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context); val previousBackend = backends.load()
        val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Public unsafe fixture only.
        val caller = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
        val files = ChatFixtureState(context, listOf("personal.${owner.publicKey}.", "chatwork.", "feed.owner."), owner.publicKey)
        val mode = if (initialOutage) "initial-retry" else "automatic"
        val folder = File(context.cacheDir, "talk-idle-evidence/$mode").apply { mkdirs() }
        val record = JsonObject()
        var observer: TalkAccount? = null
        fun endOwnedInvitation() {
            TalkRuntime.call.value?.takeIf { it.owner == owner.publicKey && it.savedBackend == SaitoBackend.OWNER &&
                it.link.room.id == caller.publicKey }?.let { TalkRuntime.end(it.token) }
        }
        var primary: Throwable? = null
        try {
            files.prepare(); wallets.replace(previous, owner); backends.save(SaitoBackend.OWNER)
            val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||").apply { rejectNewConnections = initialOutage }
            peer.start().use { server ->
                val endpoint = "ws://127.0.0.1:${server.port}/wsopen"
                var hostFailure: Throwable? = null
                try {
                    ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                        .putExtra("feedPeerUrl", endpoint).putExtra("feedOfficialPeerUrl", endpoint)).use {
                        withFailureCapture({
                            File(folder, "stage.json").writeText(record.toString())
                            captureNativeScreen(File(folder, "failure.png"))
                        }) {
                            ui.waitUntil(20_000) { ui.onNodeWithContentDescription("Switch app: Red Square").isDisplayed() }
                            ui.switchStackModule("Red Square", "Saito Talk", scrollTarget = true)
                            val account = TalkRuntime.account(context, owner, SaitoBackend.OWNER, endpoint).also { observer = it; it.attach() }
                            if (initialOutage) {
                                val unavailable = "Call connection unavailable."
                                ui.waitUntil(20_000) {
                                    runCatching {
                                        ui.onNode(hasVerticalScrollAction()).performScrollToNode(hasText(unavailable))
                                        ui.onNodeWithText(unavailable).isDisplayed()
                                    }.getOrDefault(false)
                                }
                                ui.onNodeWithText("Retry").assertIsDisplayed().assertIsEnabled()
                                check(!account.online.value)
                                captureNativeScreen(File(folder, "initial-outage.png"))
                                val attempts = peer.connectionAttempts.get()
                                peer.rejectNewConnections = false
                                record.addProperty("networkRestoredAt", SystemClock.elapsedRealtime())
                                ui.onNodeWithText("Retry").performClick()
                                ui.waitUntil(20_000) { account.online.value && peer.connectionAttempts.get() > attempts }
                                ui.onNode(hasVerticalScrollAction()).performScrollToNode(hasText(context.getString(R.string.talk_available)))
                                ui.onNodeWithText(context.getString(R.string.talk_available)).assertIsDisplayed()
                                ui.onAllNodesWithText(unavailable).assertCountEquals(0)
                            } else {
                                ui.waitUntil(15_000) { account.online.value }
                                val initialConnections = peer.authenticatedConnectionIds.size
                                check(initialConnections > 0)
                                peer.goOffline()
                                ui.waitUntil(10_000) { !account.online.value && peer.authenticatedConnectionIds.isEmpty() }
                                record.addProperty("allRoutesLostAt", SystemClock.elapsedRealtime())
                                captureNativeScreen(File(folder, "all-routes-lost.png"))
                                peer.rejectNewConnections = false
                                record.addProperty("networkRestoredAt", SystemClock.elapsedRealtime())
                                // No Retry, Activity replacement, module switch or lifecycle transition.
                                ui.waitUntil(20_000) { account.online.value && peer.authenticatedConnectionIds.isNotEmpty() }
                                check(peer.authenticatedConnectionIds.size <= initialConnections) { "Recovery must not duplicate authenticated connections" }
                            }
                            record.addProperty("recoveredAt", SystemClock.elapsedRealtime())
                            val invitation = TalkProtocol.invite(caller, owner.publicKey,
                                TalkRoom(caller.publicKey, caller.publicKey), TalkDial.REQUEST, System.currentTimeMillis())
                                .applicationRequest(1).drop(5).toByteArray()
                            peer.sendIncoming(invitation)
                            ui.waitUntil(10_000) { ui.onNodeWithText(context.getString(R.string.talk_incoming)).isDisplayed() }
                            check(TalkRuntime.call.value?.let { it.owner == owner.publicKey && it.direct == caller.publicKey &&
                                it.savedBackend == SaitoBackend.OWNER && it.phase == CallPhase.INCOMING } == true)
                            record.addProperty("incomingInvitationReceived", true)
                            record.addProperty("retryTapped", initialOutage); record.addProperty("lifecycleRestarted", false)
                            record.addProperty("activityRecreated", false); record.addProperty("moduleChangedDuringRecovery", false)
                            File(folder, "stage.json").writeText(record.toString())
                            captureNativeScreen(File(folder, "incoming-after-recovery.png"))
                            ui.onNodeWithText(context.getString(R.string.talk_decline)).performClick()
                            ui.waitUntil(10_000) { TalkRuntime.call.value?.phase?.live == false }
                            check(wallets.loadExisting().exportJson() == owner.exportJson()) { "Connection recovery must preserve the wallet" }
                            check(backends.load() == SaitoBackend.OWNER)
                            peer.assertHealthy()
                        }
                    }
                } catch (failure: Throwable) { hostFailure = failure; throw failure }
                finally {
                    cleanupAfterTest(hostFailure, { peer.rejectNewConnections = false },
                        { TalkRuntime.visibility(false) }, { endOwnedInvitation() },
                        { observer?.detach(); observer = null }, {
                            ui.waitUntil(10_000) { endOwnedInvitation(); !RealtimeIncoming.hasReceiver(owner.publicKey, endpoint) }
                        })
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally { cleanupAfterTest(primary, { TalkRuntime.visibility(false) }, { endOwnedInvitation() },
            { observer?.detach() }, { files.restoreFiles() }, { files.restorePreferences() },
            { wallets.replace(wallets.loadOrCreate(), previous) }, { backends.save(previousBackend) }) }
    }
}
