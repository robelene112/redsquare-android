package nl.davidengelen.redsquare

import android.content.Intent
import android.media.*
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.talk.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TalkLifecyclePreviewTest {
    @Test fun losingAndroidAudioFocusInterruptsTheExactCallAndReleasesItsForegroundOwnership() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallets = AndroidWalletStore(context); val previous = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context); val previousBackend = backends.load()
        val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val audio = context.getSystemService(AudioManager::class.java)
        val mode = audio.mode
        val evidence = File(context.cacheDir, "talk-lifecycle-evidence").apply { mkdirs() }
        val competingFocus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .setOnAudioFocusChangeListener { }.build()
        val fixture = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||")
        var account: TalkAccount? = null; var token: String? = null; var primary: Throwable? = null
        fun await(label: String, goal: () -> Boolean) {
            val end = SystemClock.elapsedRealtime() + 5000
            while (!goal() && SystemClock.elapsedRealtime() < end) Thread.sleep(25)
            assertTrue("$label: ${TalkRuntime.call.value?.phase}", goal())
        }
        try {
            backends.save(SaitoBackend.OWNER); wallets.replace(previous, owner)
            fixture.start().use { server ->
                val endpoint = "ws://127.0.0.1:${server.port}/wsopen"
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", endpoint).putExtra("feedOfficialPeerUrl", endpoint)).use { activity ->
                    withFailureCapture({
                        File(evidence, "focus-failure.txt").writeText("phase=${TalkRuntime.call.value?.phase} mode=${audio.mode} diagnostic=${TalkRuntime.diagnostic.value}")
                        captureNativeScreen(File(evidence, "focus-failure.png"))
                    }) {
                        TalkRuntime.visibility(true)
                        account = TalkRuntime.account(context, owner, SaitoBackend.OWNER, endpoint).also { it.attach() }
                        val room = TalkRoom(SaitoWallet.create().publicKey, owner.publicKey, mode = TalkMode.VOICE)
                        assertTrue(runBlocking { requireNotNull(account).start(TalkLink(SaitoBackend.OWNER, room), null, false, true) })
                        token = requireNotNull(TalkRuntime.call.value).token
                        assertEquals(AudioManager.MODE_IN_COMMUNICATION, audio.mode)
                        await("Android has posted the foreground call controls") {
                            context.getSystemService(android.app.NotificationManager::class.java).activeNotifications.any { it.id == TalkService.ID }
                        }
                        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, audio.requestAudioFocus(competingFocus))
                        await("Audio focus loss is reported as an interruption") { TalkRuntime.call.value?.phase == CallPhase.INTERRUPTED }
                        await("Native audio mode restored") { audio.mode == mode }
                        await("Call foreground notification removed") {
                            context.getSystemService(android.app.NotificationManager::class.java).activeNotifications.none { it.id == TalkService.ID }
                        }
                        assertNull(TalkRuntime.call.value?.localVideo)
                        File(evidence, "focus.txt").writeText("actualFocusTakeover=true\nphase=INTERRUPTED\nmodeRestored=true\nforegroundRemoved=true\n")
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            audio.abandonAudioFocusRequest(competingFocus)
            token?.let(TalkRuntime::end); account?.detach(); TalkRuntime.visibility(false); Thread.sleep(300)
            cleanupAfterTest(primary, { wallets.replace(wallets.loadOrCreate(), previous) }, { backends.save(previousBackend) },
                { context.stopService(Intent(context, TalkService::class.java)) })
        }
    }
}
