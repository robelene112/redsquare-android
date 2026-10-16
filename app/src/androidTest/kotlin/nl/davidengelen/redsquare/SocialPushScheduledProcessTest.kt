package nl.davidengelen.redsquare

import android.app.NotificationManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.AtomicFile
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.UiDevice
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
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Originals remain device-only; the host observes OS recovery before running any new instrumentation. */
class SocialPushScheduledProcessTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext.also {
        check(it.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
    }
    private val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixture. Never fund.
    private val checkpoint get() = File(context.cacheDir, "social-push-scheduled-checkpoint.json")
    private val originals get() = File(context.noBackupFilesDir, "social-push-scheduled-originals")
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    @Test fun holdPendingReceiptUntilTheHostTerminatesThisProcess() {
        check(!checkpoint.exists() && !originals.exists())
        val port = requireNotNull(InstrumentationRegistry.getArguments().getString("hostPeerPort")).toInt()
        require(port in 1024..65535)
        assertNoActivity()
        val root = instrumentation.context.assets.open("native-notification-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("root") }
        val parent = "c87f3ca402840cd7add095dbf1fda809f34d9cd3f7dae9af022e2a0f2a631f376beb54dabe886a5a73b02abe79afe51652efa57826ccd2591001d73f90544fd9"
        assertEquals(parent, root.get("sig").asString)
        val createdAt = System.currentTimeMillis()
        val reply = SaitoTransaction.create(SaitoWallet.fromPrivateKey("0".repeat(63) + "2"), JsonObject().apply {
            addProperty("module", "RedSquare"); addProperty("request", "create tweet")
            add("data", JsonObject().apply {
                addProperty("text", "Scheduled process recovery reference")
                addProperty("parent_id", parent); addProperty("thread_id", parent)
            })
        }, createdAt, listOf(wallet.publicKey))
        val payload = JsonObject().apply {
            addProperty("version", 1); addProperty("wallet", wallet.publicKey)
            addProperty("backend", "owner"); addProperty("signature", reply.id)
        }.toString()
        val tag = "social:${wallet.publicKey}:owner:${reply.id}"
        check(manager.activeNotifications.none { it.tag == tag })
        val previous = AndroidWalletStore(context).loadOrCreate()
        val previousBackend = AndroidBackendStore(context).load()
        val row = JsonObject().apply {
            addProperty("phase", "copying"); addProperty("pid", Process.myPid()); addProperty("port", port)
            addProperty("owner", wallet.publicKey); addProperty("backend", "owner")
            addProperty("payload", payload); addProperty("tag", tag)
            addProperty("originalBackend", previousBackend.id)
            addProperty("originalWalletDigest", digest(previous.exportJson()))
            addProperty("walletDigest", digest(wallet.exportJson()))
            add("reply", JsonObject().apply {
                addProperty("sig", reply.id); addProperty("created_at", createdAt); addProperty("tx", reply.envelope())
            })
        }
        write(row)
        row.add("savedFiles", snapshotProcessFixtureFiles(context, originals, names()))
        row.addProperty("phase", "prepared"); write(row)
        synchronized(SaitoLocalState.lock) {
            names().filterNot { it.startsWith("wallet.") || it.startsWith("backend.") }.forEach {
                val file = File(context.noBackupFilesDir, it); check(!file.exists() || file.delete())
            }
        }
        AndroidWalletStore(context).replace(previous, wallet)
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        SocialPushSettings(context, wallet.publicKey).setEnabled(true)
        val id = runBlocking(Dispatchers.IO) {
            SocialPushScheduling.enqueue(context, payload, "ws://127.0.0.1:$port/wsopen")
        }
        val work = WorkManager.getInstance(context)
        val deadline = SystemClock.elapsedRealtime() + 60_000
        while (true) {
            val info = requireNotNull(work.getWorkInfoById(id).get(5, TimeUnit.SECONDS))
            assertFalse("Unavailable Archive must leave a retry: ${info.state}", info.state.isFinished)
            if (info.state == WorkInfo.State.ENQUEUED && info.runAttemptCount >= 1) break
            check(SystemClock.elapsedRealtime() < deadline) { "No persisted retry before process stop" }
            SystemClock.sleep(50)
        }
        assertTrue(manager.activeNotifications.none { it.tag == tag })
        assertNoActivity()
        assertEquals(row.get("walletDigest").asString, digest(AndroidWalletStore(context).loadExisting().exportJson()))
        assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
        captureNativeScreen(File(context.cacheDir, "social-push-scheduled-evidence/pending-before-death.png"))
        row.addProperty("workId", id.toString()); row.addProperty("phase", "pending"); write(row)
        // The supervisor must kill this verified-live process. Completing this test would weaken the proof.
        SystemClock.sleep(90_000)
        error("The host did not terminate the pending receipt process")
    }

    @Test fun restoreOriginalEncryptedInstallationFiles() {
        if (!checkpoint.exists()) { check(!originals.exists()); return }
        val row = JsonParser.parseString(checkpoint.readText()).asJsonObject
        require(row.get("owner").asString == wallet.publicKey && row.get("backend").asString == "owner")
        require(row.get("phase").asString in setOf("copying", "prepared", "pending"))
        // Even enqueue acknowledgement failure retains payload ownership in the public checkpoint.
        runBlocking(Dispatchers.IO) { SocialPushScheduling.cancelAndAwait(context, row.get("payload").asString) }
        manager.cancel(row.get("tag").asString, 0)
        val pendingIdentityPreserved = row.get("phase").asString != "pending" ||
            (row.get("walletDigest").asString == digest(AndroidWalletStore(context).loadExisting().exportJson()) &&
                AndroidBackendStore(context).load() == SaitoBackend.OWNER)
        if (row.get("phase").asString != "copying") {
            restoreProcessFixtureFiles(context, originals, names(), row.getAsJsonArray("savedFiles"))
        }
        assertEquals(row.get("originalWalletDigest").asString, digest(AndroidWalletStore(context).loadExisting().exportJson()))
        assertEquals(row.get("originalBackend").asString, AndroidBackendStore(context).load().id)
        if (originals.exists()) check(originals.deleteRecursively())
        AtomicFile(checkpoint).delete()
        UiDevice.getInstance(instrumentation).pressBack()
        assertTrue("Scheduled recovery must preserve the full fixture wallet and selected backend", pendingIdentityPreserved)
    }

    private fun names() = listOf("wallet.v1", "wallet.recovery.v1", "backend.v1", "backend.pending",
        "personal.${wallet.publicKey}.v1", "personal.${wallet.publicKey}.guard.v1", "personal.${wallet.publicKey}.recovery.v1",
        "socialpush.${wallet.publicKey}.settings.v1", "socialpush.${wallet.publicKey}.owner.ledger.v1")
        .flatMap { listOf(it, "$it.bak", "$it.new") }.toSet()

    private fun write(row: JsonObject) {
        val file = AtomicFile(checkpoint)
        val output = file.startWrite()
        try { output.write(row.toString().toByteArray()); output.fd.sync(); file.finishWrite(output) }
        catch (failure: Exception) { file.failWrite(output); throw failure }
        check(JsonParser.parseString(checkpoint.readText()).asJsonObject == row)
    }

    private fun assertNoActivity() = instrumentation.runOnMainSync {
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        assertTrue(Stage.values().filter { it != Stage.DESTROYED }
            .flatMap { monitor.getActivitiesInStage(it) }.none { it is MainActivity })
    }
    private fun digest(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
