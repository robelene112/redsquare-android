package nl.davidengelen.redsquare

import android.app.NotificationManager
import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.notifications.SocialPushScheduling
import nl.davidengelen.redsquare.notifications.SocialPushWakeScheduling
import nl.davidengelen.redsquare.notifications.SocialPushSettings
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Real scheduler, selected Archive and Android alert. The external transport alone is injected. */
class SocialPushWorkPreviewTest {
    @Test fun aScheduledReplyRetriesAnUnavailableArchiveThenAlertsOnceWithoutAnActivity() = scheduledReplyJourney()

    @Test fun aScheduledReplyRetriesWhenTheArchiveDoesNotAnswerAndAlertsAfterRecovery() = scheduledReplyJourney(withhold = true)

    @Test fun anOpaqueWakeRetriesTheGatewayThenVerifiesTheReplyAndAlertsOnce() = scheduledReplyJourney(wake = true)

    private fun scheduledReplyJourney(withhold: Boolean = false, wake: Boolean = false) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
        fun assertNoActivity() = instrumentation.runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            assertTrue(Stage.values().filter { it != Stage.DESTROYED }
                .flatMap { monitor.getActivitiesInStage(it) }.none { it is MainActivity })
        }
        assertNoActivity()
        val root = instrumentation.context.assets.open("native-notification-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("root") }
        val parent = "c87f3ca402840cd7add095dbf1fda809f34d9cd3f7dae9af022e2a0f2a631f376beb54dabe886a5a73b02abe79afe51652efa57826ccd2591001d73f90544fd9"
        assertEquals(parent, root.get("sig").asString)
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Published unsafe fixture. Never fund.
        val author = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Published unsafe fixture. Never fund.
        // Fresh transport fixture, not an independent protocol interoperability claim.
        // The parent remains the independently signed web reference used by existing receipt cases.
        val createdAt = System.currentTimeMillis()
        val reply = SaitoTransaction.create(author, JsonObject().apply {
            addProperty("module", "RedSquare"); addProperty("request", "create tweet")
            add("data", JsonObject().apply {
                addProperty("text", "Background reply recovery reference")
                addProperty("parent_id", parent); addProperty("thread_id", parent)
            })
        }, createdAt, listOf(wallet.publicKey))
        val row = JsonObject().apply {
            addProperty("sig", reply.id); addProperty("created_at", createdAt); addProperty("tx", reply.envelope())
        }
        val recovering = AtomicBoolean(false)
        val failedReads = AtomicInteger()
        val peer = ControlledFeedPeer("[]", archiveResponse = { criteria ->
            require(criteria.get("field1").asString == "RedSquare" && criteria.get("flagged_ne").asInt == 1)
            if (!recovering.get()) {
                failedReads.incrementAndGet()
                "{}"
            }
            else when (criteria.get("sig").asString) {
                reply.id -> "[$row]"
                parent -> "[$root]"
                else -> "[]"
            }
        }).apply { withholdArchiveReply = withhold }
        val wallets = AndroidWalletStore(context)
        val previous = wallets.loadOrCreate()
        val previousJson = previous.exportJson()
        val backends = AndroidBackendStore(context)
        val previousBackend = backends.load()
        val retained = ChatFixtureState(context, listOf("socialpush.${wallet.publicKey}.", "personal.${wallet.publicKey}."), wallet.publicKey)
        val work = WorkManager.getInstance(context)
        // Retain ownership before enqueue acknowledgement, including its timeout/failure path.
        val payload = JsonObject().apply {
            addProperty("version", 1); addProperty("wallet", wallet.publicKey)
            addProperty("backend", "owner"); addProperty("signature", reply.id)
        }.toString()
        val opaque = "ab".repeat(32) // Public transport fixture, never a real installation/event capability.
        val gatewayReads = AtomicInteger()
        val gatewayFailure = java.util.concurrent.atomic.AtomicReference<Throwable?>()
        val gateway = if (wake) mockwebserver3.MockWebServer().apply {
            dispatcher = object : mockwebserver3.Dispatcher() {
                override fun dispatch(request: mockwebserver3.RecordedRequest): mockwebserver3.MockResponse = try {
                    require(request.method == "POST" && request.url.encodedPath == "/v1/wakes/resolve")
                    val body = JsonParser.parseString(requireNotNull(request.body).utf8()).asJsonObject
                    require(body == JsonParser.parseString("{\"version\":1,\"wake\":\"$opaque\"}"))
                    gatewayReads.incrementAndGet()
                    if (!recovering.get()) mockwebserver3.MockResponse.Builder().code(503).build()
                    else mockwebserver3.MockResponse.Builder().code(200).body(payload).build()
                } catch (failure: Throwable) {
                    gatewayFailure.set(failure)
                    mockwebserver3.MockResponse.Builder().code(500).build()
                }
            }
        } else null
        val manager = context.getSystemService(NotificationManager::class.java)
        val tag = "social:${wallet.publicKey}:owner:${reply.id}"
        check(manager.activeNotifications.none { it.tag == tag })
        val device = UiDevice.getInstance(instrumentation)
        val captureDirectory = if (wake) "opaque" else if (withhold) "timeout" else "malformed"
        fun capture(name: String) = captureNativeScreen(File(context.cacheDir, "social-push-work-evidence/$captureDirectory/$name.png"))
        fun info(id: UUID) = work.getWorkInfoById(id).get(5, TimeUnit.SECONDS)
        fun awaitState(label: String, predicate: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + 60_000
            while (!predicate() && SystemClock.elapsedRealtime() < deadline) {
                gatewayFailure.get()?.let { throw AssertionError("Controlled gateway rejected the request", it) }
                SystemClock.sleep(50)
            }
            gatewayFailure.get()?.let { throw AssertionError("Controlled gateway rejected the request", it) }
            assertTrue(label, predicate())
        }
        var primary: Throwable? = null
        try {
            retained.prepare()
            wallets.replace(previous, wallet)
            backends.save(SaitoBackend.OWNER)
            SocialPushSettings(context, wallet.publicKey).setEnabled(true)
            peer.start().use { server ->
                gateway?.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
                withFailureCapture({ capture("scheduled-reply-failure") }) {
                    fun schedule(): UUID = runBlocking(Dispatchers.IO) {
                        if (wake) SocialPushWakeScheduling.enqueue(context, opaque,
                            "http://127.0.0.1:${requireNotNull(gateway).port}/v1/wakes/resolve",
                            "ws://127.0.0.1:${server.port}/wsopen")
                        else SocialPushScheduling.enqueue(context, payload, "ws://127.0.0.1:${server.port}/wsopen")
                    }
                    val submittedAt = SystemClock.elapsedRealtime()
                    val first = schedule()
                    awaitState("Unavailable notification source must leave a scheduled retry, not a successful empty receipt") {
                        info(first)?.let {
                            assertFalse("Unavailable ${if (wake) "gateway" else "Archive"} receipt finished as ${it.state}", it.state.isFinished)
                            it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount >= 1
                        } == true
                    }
                    assertTrue(if (wake) gatewayReads.get() > 0 else failedReads.get() > 0)
                    if (withhold) assertTrue("A withheld response must reach the real request deadline before retry",
                        SystemClock.elapsedRealtime() - submittedAt >= 10_000)
                    assertEquals("A duplicate during retry must return the persisted existing task", first, schedule())
                    assertTrue(manager.activeNotifications.none { it.tag == tag })
                    assertNoActivity()
                    capture(if (wake) "gateway-retry-pending" else "archive-retry-pending")
                    recovering.set(true)
                    peer.withholdArchiveReply = false
                    awaitState("Recovered Archive must finish the scheduled receipt") { info(first)?.state == WorkInfo.State.SUCCEEDED }
                    assertEquals(tag, manager.activeNotifications.single { it.tag == tag }.tag)
                    assertTrue(device.openNotification())
                    assertTrue(device.wait(Until.hasObject(By.text("Replied to your post")), 15_000))
                    capture("scheduled-reply-alert")
                    assertNoActivity()
                    val firstPostTime = manager.activeNotifications.single { it.tag == tag }.postTime
                    awaitState("Separate the completed redelivery from the first OS publication time") {
                        System.currentTimeMillis() > firstPostTime
                    }
                    val duplicate = schedule()
                    awaitState("Completed receipt redelivery must finish") { info(duplicate)?.state == WorkInfo.State.SUCCEEDED }
                    assertEquals(1, manager.activeNotifications.count { it.tag == tag })
                    assertEquals("Redelivery must not replace or re-alert the OS notification", firstPostTime,
                        manager.activeNotifications.single { it.tag == tag }.postTime)
                    assertEquals(wallet.exportJson(), wallets.loadExisting().exportJson())
                    assertEquals(SaitoBackend.OWNER, backends.load())
                    assertTrue(peer.transactions.isEmpty() && peer.reports.isEmpty() && peer.relayedMessages.isEmpty())
                    peer.assertHealthy()
                    assertNoActivity()
                    capture("scheduled-reply-deduplicated")
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            var stopped = false
            val cleanup = listOf<() -> Unit>(
                { runBlocking(Dispatchers.IO) {
                    if (wake) SocialPushWakeScheduling.cancelAndAwait(context, opaque)
                    SocialPushScheduling.cancelAndAwait(context, payload)
                }; stopped = true },
                { gateway?.close() },
                { manager.cancel(tag, 0) },
                { device.pressBack() },
                { if (stopped) retained.restoreFiles() },
                { if (stopped) retained.restorePreferences() },
                { if (stopped) { wallets.replace(wallets.loadExisting(), previous); check(wallets.loadExisting().exportJson() == previousJson) } },
                { if (stopped) { backends.save(previousBackend); check(backends.load() == previousBackend) } })
            var cleanupFailure: Throwable? = null
            cleanup.forEach { action -> try { action() } catch (failure: Throwable) {
                if (cleanupFailure == null) cleanupFailure = failure else cleanupFailure!!.addSuppressed(failure)
            } }
            cleanupFailure?.let { if (primary != null) primary!!.addSuppressed(it) else throw it }
        }
    }
}
