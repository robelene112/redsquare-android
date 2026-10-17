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
import nl.davidengelen.redsquare.chat.GroupChatStore
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

/** Real worker, signed original, retained known group and Android shade; owned opaque injection only. */
class GroupPushWorkPreviewTest {
    @Test fun aKnownGroupReceivesAnOpaqueWakeWithoutAnActivityAndDoesNotReAlertAfterDismissal() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
        check(!BuildConfig.PUSH_CONFIGURED)
        fun activities(): List<MainActivity> {
            var result = emptyList<MainActivity>()
            instrumentation.runOnMainSync {
                val monitor = ActivityLifecycleMonitorRegistry.getInstance()
                result = Stage.values().filter { it != Stage.DESTROYED }
                    .flatMap { monitor.getActivitiesInStage(it) }.filterIsInstance<MainActivity>().distinct()
            }
            return result
        }
        fun assertNoActivity() = assertTrue("Group wake receipt must not launch an Activity", activities().isEmpty())
        fun finishActivities() {
            val active = activities()
            instrumentation.runOnMainSync { active.forEach { it.finish() } }
            instrumentation.waitForIdleSync()
            val deadline = SystemClock.elapsedRealtime() + 5_000
            while (activities().isNotEmpty() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(25)
            assertNoActivity()
        }
        assertNoActivity()
        // Published unsafe fixture identities; this transport story adds no independent interoperability claim.
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
        val author = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val groupKey = SaitoWallet.fromPrivateKey("0".repeat(63) + "9").publicKey
        val backend = SaitoBackend.OWNER
        val now = System.currentTimeMillis()
        val creation = GroupChat.create(author, groupKey, "A known background group", listOf(wallet.publicKey), now - 1)
        val group = GroupChat.readCreation(creation.envelope())
        val text = "A group message retained in the local conversation"
        val original = GroupChat.message(author, group, text, now)
        assertFalse(wallet.publicKey in SaitoEnvelope.decode(original.envelope()).recipients)
        val wake = "71".repeat(32)
        val wallets = AndroidWalletStore(context)
        val previous = wallets.loadOrCreate()
        val previousJson = previous.exportJson()
        val backends = AndroidBackendStore(context)
        val previousBackend = backends.load()
        val retained = ChatFixtureState(context, listOf("groupchat.${backend.id}.${wallet.publicKey}.",
            "groupchatpush.${wallet.publicKey}.", "socialpush.${wallet.publicKey}.",
            "chat-alerts.${wallet.publicKey}.", "personal.${wallet.publicKey}."), wallet.publicKey)
        val failure = AtomicReference<Throwable?>()
        val resolves = AtomicInteger()
        val originals = AtomicInteger()
        val gateway = mockwebserver3.MockWebServer().apply {
            dispatcher = object : mockwebserver3.Dispatcher() {
                override fun dispatch(request: mockwebserver3.RecordedRequest): mockwebserver3.MockResponse = try {
                    require(request.method == "POST" && request.headers["X-Saito-Owner"] == wallet.publicKey)
                    val body = JsonParser.parseString(requireNotNull(request.body).utf8()).asJsonObject
                    require(body == JsonParser.parseString("{\"version\":1,\"wake\":\"$wake\"}"))
                    val operation = when (request.url.encodedPath) {
                        "/v1/wakes/resolve" -> "resolve"
                        "/v1/wakes/original" -> "original"
                        else -> error("Unexpected owned group wake route")
                    }
                    require(SaitoWallet.verify("Saito push $operation v1\n${request.url}\n$wake".toByteArray(),
                        requireNotNull(request.headers["X-Saito-Signature"]), wallet.publicKey))
                    mockwebserver3.MockResponse.Builder().body(JsonObject().apply {
                        addProperty("version", 1)
                        if (operation == "resolve") {
                            resolves.incrementAndGet()
                            addProperty("wallet", wallet.publicKey); addProperty("backend", backend.id)
                            addProperty("signature", original.id); addProperty("kind", "group-chat")
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
        val tag = "chat:${wallet.publicKey}:${backend.id}:${original.id}"
        check(manager.activeNotifications.none { it.tag == tag })
        val device = UiDevice.getInstance(instrumentation)
        fun capture(name: String) = captureNativeScreen(File(context.cacheDir, "group-push-evidence/group-worker-$name.png"))
        fun await(id: UUID) {
            val deadline = SystemClock.elapsedRealtime() + 40_000
            while (SystemClock.elapsedRealtime() < deadline) {
                failure.get()?.let { throw AssertionError("Owned group gateway rejected receipt", it) }
                val info = work.getWorkInfoById(id).get(5, TimeUnit.SECONDS)
                if (info?.state?.isFinished == true) {
                    assertEquals("Group receipt must complete successfully", WorkInfo.State.SUCCEEDED, info.state)
                    return
                }
                SystemClock.sleep(50)
            }
            fail("Group receipt did not complete within40seconds")
        }
        var primary: Throwable? = null
        var stopped = false
        fun requireStopped() { check(stopped); assertNoActivity() }
        try {
            retained.prepare()
            wallets.replace(previous, wallet); backends.save(backend)
            GroupChatStore(context, wallet, backend).receive(creation.envelope())
            ChatPushSettings(context, wallet.publicKey).setEnabled(true)
            gateway.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
            withFailureCapture({ capture("failure") }) {
                fun schedule() = runBlocking(Dispatchers.IO) { SocialPushWakeScheduling.enqueue(context, wake,
                    "http://127.0.0.1:${gateway.port}/v1/wakes/resolve") }
                await(schedule())
                assertNoActivity()
                val publishedBy = SystemClock.elapsedRealtime() + 5_000
                while (manager.activeNotifications.none { it.tag == tag } && SystemClock.elapsedRealtime() < publishedBy)
                    SystemClock.sleep(25)
                val alert = manager.activeNotifications.singleOrNull { it.tag == tag }
                assertNotNull("A known group must retain its signed original and display a generic alert", alert)
                assertTrue(resolves.get() > 0 && originals.get() > 0)
                assertEquals("group-chats", alert!!.notification.channelId)
                assertEquals("New message", alert.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
                val saved = GroupChatStore(context, wallet, backend).load(group.id)!!
                assertEquals(1, saved.messages.size)
                assertEquals(original.id, saved.messages.single().id)
                assertEquals(author.publicKey, saved.messages.single().author)
                assertEquals(text, saved.messages.single().text)
                assertTrue(saved.group.memberRoles.getValue(wallet.publicKey).active)
                assertTrue(device.openNotification())
                assertNotNull(device.wait(Until.findObject(By.text("New message")), 10_000))
                capture("generic-alert")
                val card = requireNotNull(device.wait(Until.findObject(
                    By.res("android", "status_bar_latest_event_content").hasDescendant(By.text("New message"))), 5_000))
                val bounds = card.visibleBounds
                assertTrue("The group card must be fully visible before dismissal", bounds.left >= 0 &&
                    bounds.right <= device.displayWidth && bounds.top >= 0 && bounds.bottom <= device.displayHeight &&
                    bounds.width() > device.displayWidth / 2 && bounds.height() > 16)
                card.setGestureMargin(8); card.swipe(Direction.RIGHT, 1.0f)
                assertTrue(device.wait(Until.gone(By.text("New message")), 5_000))
                val dismissedBy = SystemClock.elapsedRealtime() + 5_000
                while (manager.activeNotifications.any { it.tag == tag } && SystemClock.elapsedRealtime() < dismissedBy)
                    SystemClock.sleep(25)
                assertTrue(manager.activeNotifications.none { it.tag == tag })
                device.pressBack()
                await(schedule())
                val silentUntil = SystemClock.elapsedRealtime() + 2_000
                do {
                    assertTrue("Redelivery must not re-alert a dismissed group message", manager.activeNotifications.none { it.tag == tag })
                    SystemClock.sleep(50)
                } while (SystemClock.elapsedRealtime() < silentUntil)
                assertEquals(1, GroupChatStore(context, wallet, backend).load(group.id)!!.messages.size)
                assertEquals(wallet.exportJson(), wallets.loadExisting().exportJson())
                assertEquals(backend, backends.load())
                assertNoActivity()
                capture("deduplicated")
            }
        } catch (caught: Throwable) { primary = caught; throw caught }
        finally {
            cleanupAfterTest(primary,
                { runBlocking(Dispatchers.IO) { SocialPushWakeScheduling.cancelAndAwait(context, wake) }; finishActivities(); stopped = true },
                { gateway.close() }, { manager.cancel(tag, 0) }, { device.pressBack() },
                { requireStopped(); retained.restoreFiles() }, { requireStopped(); retained.restorePreferences() },
                { requireStopped(); wallets.replace(wallets.loadExisting(), previous); check(wallets.loadExisting().exportJson() == previousJson) },
                { requireStopped(); backends.save(previousBackend); check(backends.load() == previousBackend) })
        }
    }
}
