package nl.davidengelen.redsquare

import android.app.NotificationManager
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.moderation.AndroidPersonalControls
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.talk.*
import org.junit.Assert.*
import org.junit.Test
import org.webrtc.VideoSink
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Actual capture and real immutable Android notification actions across successive call owners. */
class TalkOwnershipPreviewTest {
    @Test fun oldNotificationCannotEndANewerCallAndTheCurrentHangupReleasesCapture() = fixture { context, wallets, owner, endpoint ->
        val account = TalkRuntime.account(context, owner, SaitoBackend.OWNER, endpoint).also { it.attach() }
        var token: String? = null
        try {
            fun start(): TalkCall {
                assertTrue(runBlocking { account.start(TalkLink(SaitoBackend.OWNER,
                    TalkRoom(SaitoWallet.create().publicKey, owner.publicKey)), null, true, true) })
                return requireNotNull(TalkRuntime.call.value).also { call ->
                    token = call.token
                    val frames = AtomicInteger()
                    val track = requireNotNull(call.localVideo)
                    val sink = VideoSink { frames.incrementAndGet() }
                    track.addSink(sink)
                    try { await("Actual camera capture") { frames.get() >= 3 } }
                    finally { runCatching { track.removeSink(sink) } }
                }
            }
            val first = start()
            val notifications = context.getSystemService(NotificationManager::class.java)
            await("First foreground notification") { notifications.activeNotifications.any { it.id == TalkService.ID } }
            val stale = notifications.activeNotifications.single { it.id == TalkService.ID }.notification.actions.single().actionIntent
            TalkRuntime.end(first.token)
            await("First call ended") { TalkRuntime.call.value?.phase == CallPhase.ENDED }
            await("First notification removed") { notifications.activeNotifications.none { it.id == TalkService.ID } }
            val second = start()
            assertNotEquals(first.token, second.token)
            await("Second foreground notification") { notifications.activeNotifications.any { it.id == TalkService.ID } }
            stale.send()
            Thread.sleep(300)
            assertEquals(second.token, TalkRuntime.call.value?.token)
            assertTrue(requireNotNull(TalkRuntime.call.value).phase.started)
            assertTrue(requireNotNull(TalkRuntime.call.value?.localVideo).enabled())
            notifications.activeNotifications.single { it.id == TalkService.ID }.notification.actions.single().actionIntent.send()
            await("Current notification Hang up ends the exact call") { TalkRuntime.call.value?.let { it.token == second.token && it.phase == CallPhase.ENDED && it.localVideo == null } == true }
            await("Current notification removed") { notifications.activeNotifications.none { it.id == TalkService.ID } }
            File(context.cacheDir, "talk-ownership-evidence").apply { mkdirs() }.resolve("notifications.txt")
                .writeText("distinctCallTokens=true\nstaleHangupIgnored=true\ncurrentHangupReleasesCapture=true\n")
        } finally { token?.let(TalkRuntime::end); account.detach() }
    }

    @Test fun walletNodeAndBlockingChangesSilenceTheOldCaptureBeforeTheWriteReturns() = fixture { context, wallets, owner, endpoint ->
        val backendStore = AndroidBackendStore(context)
        val peer = SaitoWallet.fromPrivateKey("0".repeat(63) + "1").publicKey
        val policyStore = AndroidPersonalControls(context, owner.publicKey)
        for (change in listOf("wallet", "node", "block")) {
            backendStore.save(SaitoBackend.OWNER)
            if (wallets.loadExisting().publicKey != owner.publicKey) wallets.replace(wallets.loadExisting(), owner)
            val policy = policyStore.load()
            if (peer in policy.blocked) policyStore.save(policy, policy.copy(blocked = policy.blocked - peer))
            val account = TalkRuntime.account(context, owner, SaitoBackend.OWNER, endpoint).also { it.attach() }
            var token: String? = null
            try {
                assertTrue(runBlocking { account.start(TalkLink(SaitoBackend.OWNER,
                    TalkRoom(SaitoWallet.create().publicKey, owner.publicKey, peers = listOf(peer))), null, true, true) })
                val call = requireNotNull(TalkRuntime.call.value).also { token = it.token }
                val track = requireNotNull(call.localVideo)
                assertTrue(track.enabled())
                when (change) {
                    "wallet" -> wallets.replace(owner, SaitoWallet.fromPrivateKey("0".repeat(63) + "3"))
                    "node" -> backendStore.save(SaitoBackend.OFFICIAL)
                    else -> policyStore.load().let { policyStore.save(it, it.copy(blocked = it.blocked + peer)) }
                }
                // The old track may already be disposed. Both disposed and disabled mean it cannot transmit.
                assertFalse("$change silences capture synchronously", runCatching { track.enabled() }.getOrDefault(false))
                await("$change interrupts the obsolete call") { TalkRuntime.call.value?.let { it.token == token && !it.phase.live && it.localVideo == null } == true }
                TalkRuntime.controls(requireNotNull(token), camera = true, microphone = true)
                Thread.sleep(100)
                assertFalse(requireNotNull(TalkRuntime.call.value).phase.live)
            } finally { token?.let(TalkRuntime::end); account.detach() }
        }
        File(context.cacheDir, "talk-ownership-evidence").apply { mkdirs() }.resolve("authority.txt")
            .writeText("walletWriteSilencesCapture=true\nnodeWriteSilencesCapture=true\nblockWriteSilencesCapture=true\nobsoleteControlsIgnored=true\n")
    }

    private fun fixture(body: (android.content.Context, AndroidWalletStore, SaitoWallet, String) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallets = AndroidWalletStore(context); val previous = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context); val previousBackend = backends.load()
        val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val files = ChatFixtureState(context, listOf("personal.${owner.publicKey}.", "chatwork."), owner.publicKey)
        var primary: Throwable? = null
        try {
            files.prepare(); backends.save(SaitoBackend.OWNER); wallets.replace(previous, owner)
            ControlledFeedPeer("[]", advertisedServices = "archive||;relay||").start().use { server ->
                val endpoint = "ws://127.0.0.1:${server.port}/wsopen"
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", endpoint).putExtra("feedOfficialPeerUrl", endpoint)).use {
                    withFailureCapture({
                        val folder = File(context.cacheDir, "talk-ownership-evidence").apply { mkdirs() }
                        File(folder, "failure-state.txt").writeText("${TalkRuntime.call.value}\n${TalkRuntime.diagnostic.value}")
                        captureNativeScreen(File(folder, "failure.png"))
                    }) { TalkRuntime.visibility(true); body(context, wallets, owner, endpoint) }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            TalkRuntime.call.value?.token?.let(TalkRuntime::end); TalkRuntime.visibility(false); Thread.sleep(300)
            cleanupAfterTest(primary, { files.restoreFiles() }, { files.restorePreferences() },
                { wallets.replace(wallets.loadOrCreate(), previous) }, { backends.save(previousBackend) },
                { context.stopService(Intent(context, TalkService::class.java)) })
        }
    }
    private fun await(label: String, goal: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 5000
        while (!goal() && SystemClock.elapsedRealtime() < end) Thread.sleep(25)
        assertTrue(label, goal())
    }
}
