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
    @Test fun aScheduledReplyRetriesAnUnavailableArchiveThenAlertsOnceWithoutAnActivity() {
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
            if (!recovering.get()) { failedReads.incrementAndGet(); "{}" }
            else when (criteria.get("sig").asString) {
                reply.id -> "[$row]"
                parent -> "[$root]"
                else -> "[]"
            }
        })
        val wallets = AndroidWalletStore(context)
        val previous = wallets.loadOrCreate()
        val previousJson = previous.exportJson()
        val backends = AndroidBackendStore(context)
        val previousBackend = backends.load()
        val retained = ChatFixtureState(context, listOf("socialpush.${wallet.publicKey}.", "personal.${wallet.publicKey}."), wallet.publicKey)
        val work = WorkManager.getInstance(context)
        val jobs = mutableListOf<UUID>()
        val manager = context.getSystemService(NotificationManager::class.java)
        val tag = "social:${wallet.publicKey}:owner:${reply.id}"
        check(manager.activeNotifications.none { it.tag == tag })
        val device = UiDevice.getInstance(instrumentation)
        fun capture(name: String) = captureNativeScreen(File(context.cacheDir, "social-push-work-evidence/$name.png"))
        fun info(id: UUID) = work.getWorkInfoById(id).get(5, TimeUnit.SECONDS)
        fun awaitState(label: String, predicate: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + 60_000
            while (!predicate() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
            assertTrue(label, predicate())
        }
        var primary: Throwable? = null
        try {
            retained.prepare()
            wallets.replace(previous, wallet)
            backends.save(SaitoBackend.OWNER)
            SocialPushSettings(context, wallet.publicKey).setEnabled(true)
            peer.start().use { server ->
                withFailureCapture({ capture("scheduled-reply-failure") }) {
                    val payload = JsonObject().apply {
                        addProperty("version", 1); addProperty("wallet", wallet.publicKey)
                        addProperty("backend", "owner"); addProperty("signature", reply.id)
                    }.toString()
                    fun schedule(): UUID = runBlocking(Dispatchers.IO) {
                        SocialPushScheduling.enqueue(context, payload, "ws://127.0.0.1:${server.port}/wsopen")
                    }.also(jobs::add)
                    val first = schedule()
                    awaitState("Unavailable Archive must leave a scheduled retry, not a successful empty receipt") {
                        info(first)?.let {
                            assertFalse("Unavailable Archive receipt finished as ${it.state}", it.state.isFinished)
                            it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount >= 1
                        } == true
                    }
                    assertTrue(failedReads.get() > 0)
                    assertTrue(manager.activeNotifications.none { it.tag == tag })
                    assertNoActivity()
                    capture("archive-retry-pending")
                    recovering.set(true)
                    awaitState("Recovered Archive must finish the scheduled receipt") { info(first)?.state == WorkInfo.State.SUCCEEDED }
                    assertEquals(tag, manager.activeNotifications.single { it.tag == tag }.tag)
                    assertTrue(device.openNotification())
                    assertTrue(device.wait(Until.hasObject(By.text("Replied to your post")), 15_000))
                    capture("scheduled-reply-alert")
                    assertNoActivity()
                    val duplicate = schedule()
                    awaitState("Completed receipt redelivery must finish") { info(duplicate)?.state == WorkInfo.State.SUCCEEDED }
                    assertEquals(1, manager.activeNotifications.count { it.tag == tag })
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
            val cleanup = listOf<() -> Unit>(
                { jobs.forEach { work.cancelWorkById(it).result.get(5, TimeUnit.SECONDS) } },
                { manager.cancel(tag, 0) },
                { device.pressBack() },
                { retained.restoreFiles() },
                { retained.restorePreferences() },
                { wallets.replace(wallets.loadExisting(), previous); check(wallets.loadExisting().exportJson() == previousJson) },
                { backends.save(previousBackend); check(backends.load() == previousBackend) })
            var cleanupFailure: Throwable? = null
            cleanup.forEach { action -> try { action() } catch (failure: Throwable) {
                if (cleanupFailure == null) cleanupFailure = failure else cleanupFailure!!.addSuppressed(failure)
            } }
            cleanupFailure?.let { if (primary != null) primary!!.addSuppressed(it) else throw it }
        }
    }
}
