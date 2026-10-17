package nl.davidengelen.redsquare

import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.chat.ChatHistoryStore
import nl.davidengelen.redsquare.notifications.ChatPushSettings
import nl.davidengelen.redsquare.notifications.SocialPushWakeScheduling
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Actual worker, recipient-proof HTTP, encrypted history and Android shade; opaque wake injection only. */
class PrivatePushWorkPreviewTest {
    @Test fun chatOnlyConsentReceivesAndDecryptsAnOpaqueWakeWithoutAnActivityAndDoesNotReAlertAfterDismissal() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
        check(!BuildConfig.PUSH_CONFIGURED)
        fun assertNoActivity() = instrumentation.runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            assertTrue(Stage.values().filter { it != Stage.DESTROYED }
                .flatMap { monitor.getActivitiesInStage(it) }.none { it is MainActivity })
        }
        assertNoActivity()
        // Published unsafe fixture identities; fresh transport fixture, not independent encryption interoperability.
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
        val sender = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val text = "Keep this private text on the receiving device"
        val original = PrivateChat.create(sender, wallet.publicKey, text, System.currentTimeMillis())
        val opaque = "46".repeat(32)
        val wallets = AndroidWalletStore(context)
        val previous = wallets.loadOrCreate()
        val previousJson = previous.exportJson()
        val backends = AndroidBackendStore(context)
        val previousBackend = backends.load()
        val retained = ChatFixtureState(context, listOf("chatpush.${wallet.publicKey}.", "socialpush.${wallet.publicKey}.",
            "chat.owner.${wallet.publicKey}.", "chat-alerts.${wallet.publicKey}.", "personal.${wallet.publicKey}."), wallet.publicKey)
        val failure = AtomicReference<Throwable?>()
        val resolves = AtomicInteger()
        val originals = AtomicInteger()
        val gateway = mockwebserver3.MockWebServer().apply {
            dispatcher = object : mockwebserver3.Dispatcher() {
                override fun dispatch(request: mockwebserver3.RecordedRequest): mockwebserver3.MockResponse = try {
                    require(request.method == "POST" && request.headers["X-Saito-Owner"] == wallet.publicKey)
                    val body = JsonParser.parseString(requireNotNull(request.body).utf8()).asJsonObject
                    require(body == JsonParser.parseString("{\"version\":1,\"wake\":\"$opaque\"}"))
                    val operation = when (request.url.encodedPath) {
                        "/v1/wakes/resolve" -> "resolve"
                        "/v1/wakes/original" -> "original"
                        else -> error("Unexpected owned private wake route")
                    }
                    require(SaitoWallet.verify("Saito push $operation v1\n${request.url}\n$opaque".toByteArray(),
                        requireNotNull(request.headers["X-Saito-Signature"]), wallet.publicKey))
                    mockwebserver3.MockResponse.Builder().body(JsonObject().apply {
                        addProperty("version", 1)
                        if (operation == "resolve") {
                            resolves.incrementAndGet()
                            addProperty("wallet", wallet.publicKey); addProperty("backend", "owner")
                            addProperty("signature", original.id); addProperty("kind", "private-chat")
                        } else {
                            originals.incrementAndGet(); addProperty("envelope", original.envelope())
                        }
                    }.toString()).build()
                } catch (caught: Throwable) {
                    failure.set(caught); mockwebserver3.MockResponse.Builder().code(500).build()
                }
            }
        }
        val work = WorkManager.getInstance(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val tag = "chat:${wallet.publicKey}:owner:${original.id}"
        check(manager.activeNotifications.none { it.tag == tag })
        val device = UiDevice.getInstance(instrumentation)
        fun capture(name: String) = captureNativeScreen(File(context.cacheDir, "chat-alert-evidence/private-worker-$name.png"))
        fun await(id: UUID) {
            val deadline = SystemClock.elapsedRealtime() + 40_000
            while (SystemClock.elapsedRealtime() < deadline) {
                failure.get()?.let { throw AssertionError("Owned private gateway rejected receipt", it) }
                val info = work.getWorkInfoById(id).get(5, TimeUnit.SECONDS)
                if (info?.state?.isFinished == true) {
                    assertEquals("Private receipt must complete successfully", WorkInfo.State.SUCCEEDED, info.state)
                    return
                }
                SystemClock.sleep(50)
            }
            fail("Private receipt did not complete within40seconds")
        }
        var primary: Throwable? = null
        var stopped = false
        fun requireStopped() { check(stopped); assertNoActivity() }
        try {
            retained.prepare()
            wallets.replace(previous, wallet); backends.save(SaitoBackend.OWNER)
            ChatPushSettings(context, wallet.publicKey).setEnabled(true)
            gateway.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
            withFailureCapture({ capture("failure") }) {
                fun schedule() = runBlocking(Dispatchers.IO) { SocialPushWakeScheduling.enqueue(context, opaque,
                    "http://127.0.0.1:${gateway.port}/v1/wakes/resolve") }
                await(schedule())
                assertNoActivity()
                val publishedBy = SystemClock.elapsedRealtime() + 5_000
                while (manager.activeNotifications.none { it.tag == tag } && SystemClock.elapsedRealtime() < publishedBy)
                    SystemClock.sleep(25)
                val alert = manager.activeNotifications.singleOrNull { it.tag == tag }
                assertNotNull("Chat-only consent must resolve, decrypt, retain and display its private alert", alert)
                assertTrue(resolves.get() > 0 && originals.get() > 0)
                assertEquals("private-chats", alert!!.notification.channelId)
                assertEquals("New message", alert.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
                val saved = ChatHistoryStore(context, wallet, SaitoBackend.OWNER, sender.publicKey).load().entries
                    .single { it.message.id == original.id }
                assertEquals(sender.publicKey, saved.message.author); assertEquals(text, saved.message.text)
                assertTrue(device.openNotification())
                assertNotNull(device.wait(Until.findObject(By.text("New message")), 10_000))
                capture("generic-alert")
                val card = requireNotNull(device.wait(Until.findObject(
                    By.res("android", "status_bar_latest_event_content").hasDescendant(By.text("New message"))), 5_000))
                val bounds = card.visibleBounds
                assertTrue("The actual private card must be fully visible before swiping",
                    bounds.left >= 0 && bounds.right <= device.displayWidth && bounds.top >= 0 &&
                        bounds.bottom <= device.displayHeight && bounds.width() > device.displayWidth / 2 && bounds.height() > 16)
                card.setGestureMargin(8)
                card.swipe(Direction.RIGHT, 1.0f)
                assertTrue(device.wait(Until.gone(By.text("New message")), 5_000))
                val dismissedBy = SystemClock.elapsedRealtime() + 5_000
                while (manager.activeNotifications.any { it.tag == tag } && SystemClock.elapsedRealtime() < dismissedBy)
                    SystemClock.sleep(25)
                assertTrue("Android must observe the physical dismissal before redelivery", manager.activeNotifications.none { it.tag == tag })
                device.pressBack()
                await(schedule())
                val silentUntil = SystemClock.elapsedRealtime() + 2_000
                do {
                    assertTrue("Redelivery must not re-alert a dismissed private message", manager.activeNotifications.none { it.tag == tag })
                    SystemClock.sleep(50)
                } while (SystemClock.elapsedRealtime() < silentUntil)
                assertEquals(1, ChatHistoryStore(context, wallet, SaitoBackend.OWNER, sender.publicKey).load().entries.size)
                assertEquals(wallet.exportJson(), wallets.loadExisting().exportJson())
                assertEquals(SaitoBackend.OWNER, backends.load())
                assertNoActivity()
                capture("deduplicated")
            }
        } catch (caught: Throwable) { primary = caught; throw caught }
        finally {
            cleanupAfterTest(primary,
                { runBlocking(Dispatchers.IO) { SocialPushWakeScheduling.cancelAndAwait(context, opaque) }; assertNoActivity(); stopped = true },
                { gateway.close() }, { manager.cancel(tag, 0) }, { device.pressBack() },
                { requireStopped(); retained.restoreFiles() }, { requireStopped(); retained.restorePreferences() },
                { requireStopped(); wallets.replace(wallets.loadExisting(), previous); check(wallets.loadExisting().exportJson() == previousJson) },
                { requireStopped(); backends.save(previousBackend); check(backends.load() == previousBackend) })
        }
    }
}
