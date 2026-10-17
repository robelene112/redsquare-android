package nl.davidengelen.redsquare

import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.AtomicFile
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.gson.JsonArray
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
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.net.ServerSocketFactory

/** Host-initiated cold recovery of the real worker, with original installation snapshots kept device-only. */
class PrivatePushProcessTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext.also {
        check(it.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
        check(!BuildConfig.PUSH_CONFIGURED)
    }
    private val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe synthetic identities only.
    private val sender = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
    private val wake = "49".repeat(32)
    private val text = "Recover this retained private message after an actual process interruption"
    private val legacyText = "Keep this older cached message through the private receipt update"
    private val checkpoint get() = File(context.cacheDir, "private-push-process-checkpoint.json")
    private val originals get() = File(context.noBackupFilesDir, "private-push-process-originals")
    private val obstructionName get() = "chatpush.${wallet.publicKey}.owner.ledger.v1.new"
    private val obstruction get() = File(context.noBackupFilesDir, obstructionName)
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private val work get() = WorkManager.getInstance(context)
    private val device get() = UiDevice.getInstance(instrumentation)
    private val gatewayFailure = AtomicReference<Throwable?>()

    @Test fun terminateWithAnActuallyRetainedPendingWorkerReceipt() {
        check(!checkpoint.exists() && !originals.exists())
        assertNoActivity()
        val previous = AndroidWalletStore(context).loadOrCreate()
        val transaction = PrivateChat.create(sender, wallet.publicKey, text, System.currentTimeMillis())
        val legacy = PrivateChat.create(sender, wallet.publicKey, legacyText, System.currentTimeMillis() - 1_000)
        val row = JsonObject().apply {
            addProperty("phase", "copying"); addProperty("pid", Process.myPid())
            addProperty("owner", wallet.publicKey); addProperty("backend", "owner"); addProperty("wake", wake)
            addProperty("signature", transaction.id); addProperty("envelope", transaction.envelope())
            addProperty("legacySignature", legacy.id)
            addProperty("obstruction", obstructionName)
            addProperty("originalWalletDigest", digest(previous.exportJson()))
            addProperty("originalBackend", AndroidBackendStore(context).load().id)
            addProperty("walletDigest", digest(wallet.exportJson()))
        }
        write(row)
        row.add("savedFiles", snapshotProcessFixtureFiles(context, originals, names()))
        row.addProperty("phase", "prepared"); write(row)
        synchronized(SaitoLocalState.lock) {
            processFixtureFiles(context, names()).filterNot {
                it.startsWith("wallet.") || it.startsWith("backend.")
            }.forEach { name -> val file = File(context.noBackupFilesDir, name); check(!file.exists() || file.delete()) }
        }
        AndroidWalletStore(context).replace(previous, wallet)
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        ChatPushSettings(context, wallet.publicKey).setEnabled(true)
        // Seed the exact pre-pending-metadata array format (1f6406e7), rather than writing with the new store.
        EncryptedLocalFile(context, historyName, "redsquare.chat.v1", "chat.").save(
            EncryptedLocalFile.Contents(1, JsonArray().apply { add(legacy.envelope()) }.toString()))
        assertEquals(legacyText, ChatHistoryStore(context, wallet, SaitoBackend.OWNER, sender.publicKey)
            .load().entries.single().message.text)
        assertTrue(sender.publicKey in ChatHistoryStore.contacts(context, wallet, SaitoBackend.OWNER).accounts)
        check(obstruction.mkdir())
        gateway(row).use { gateway ->
            gateway.start(InetAddress.getByName("127.0.0.1"), 0)
            row.addProperty("port", gateway.port); write(row)
            val id = enqueue(gateway.port)
            row.addProperty("workId", id.toString()); write(row)
            await("persisted worker retry", 40_000) {
                val info = requireNotNull(work.getWorkInfoById(id).get(5, TimeUnit.SECONDS))
                assertFalse("Unavailable bookkeeping must retain a worker retry", info.state.isFinished)
                info.state == WorkInfo.State.ENQUEUED && info.runAttemptCount >= 1
            }
            assertHistory(row); assertSilent(row); assertIdentity(row); assertNoActivity()
            capture("retained-before-death")
            row.addProperty("phase", "pending"); write(row)
            Process.killProcess(Process.myPid())
            error("Expected deliberate process termination")
        }
    }

    @Test fun recoverTheSameWorkerDismissItsAlertAndTerminateAgain() {
        val row = read()
        assertEquals("pending", row.get("phase").asString)
        assertNotEquals(row.get("pid").asInt, Process.myPid())
        assertIdentity(row); assertHistory(row); assertNoActivity()
        gateway(row).use { gateway ->
            val port = row.get("port").asInt
            gateway.start(InetAddress.getByName("127.0.0.1"), port)
            assertEquals(port, gateway.port)
            check(obstruction.isDirectory && obstruction.listFiles()!!.isEmpty() && obstruction.delete())
            // Observe the original persisted UUID; no replacement task or forced job is used here.
            val id = UUID.fromString(row.get("workId").asString)
            await("same pending worker recovery", 60_000) {
                val info = requireNotNull(work.getWorkInfoById(id).get(5, TimeUnit.SECONDS))
                if (!info.state.isFinished) false else {
                    assertEquals(WorkInfo.State.SUCCEEDED, info.state)
                    true
                }
            }
            await("recovered private Android alert", 5_000) { manager.activeNotifications.any { it.tag == tag(row) } }
            val alert = manager.activeNotifications.single { it.tag == tag(row) }.notification
            assertEquals("private-chats", alert.channelId)
            assertEquals("New message", alert.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            assertTrue(device.openNotification())
            val card = requireNotNull(device.wait(Until.findObject(By.res("android", "status_bar_latest_event_content")
                .hasDescendant(By.text("New message"))), 10_000))
            val bounds = card.visibleBounds
            assertTrue(bounds.left >= 0 && bounds.right <= device.displayWidth && bounds.top >= 0 &&
                bounds.bottom <= device.displayHeight && bounds.width() > device.displayWidth / 2 && bounds.height() > 16)
            capture("recovered-alert")
            card.setGestureMargin(8); card.swipe(Direction.RIGHT, 1.0f)
            assertTrue(device.wait(Until.gone(By.text("New message")), 5_000))
            await("physical dismissal", 5_000) { manager.activeNotifications.none { it.tag == tag(row) } }
            device.pressBack()
            assertHistory(row); assertSilent(row); assertIdentity(row); assertNoActivity()
            row.addProperty("recoveredPid", Process.myPid()); row.addProperty("phase", "dismissed"); write(row)
            Process.killProcess(Process.myPid())
            error("Expected second deliberate process termination")
        }
    }

    @Test fun aDismissedReceiptRemainsSilentAfterAnotherColdRecovery() {
        val row = read()
        assertEquals("dismissed", row.get("phase").asString)
        assertNotEquals(row.get("pid").asInt, Process.myPid())
        assertNotEquals(row.get("recoveredPid").asInt, Process.myPid())
        assertIdentity(row); assertHistory(row); assertNoActivity()
        gateway(row).use { gateway ->
            gateway.start(InetAddress.getByName("127.0.0.1"), row.get("port").asInt)
            val id = enqueue(gateway.port)
            assertNotEquals(UUID.fromString(row.get("workId").asString), id)
            await("cold duplicate completion", 40_000) {
                val state = work.getWorkInfoById(id).get(5, TimeUnit.SECONDS)?.state
                if (state?.isFinished != true) false else { assertEquals(WorkInfo.State.SUCCEEDED, state); true }
            }
            assertSilent(row); assertHistory(row); assertIdentity(row); assertNoActivity()
            capture("cold-duplicate-silent")
            row.addProperty("finalPid", Process.myPid()); row.addProperty("phase", "verified"); write(row)
        }
    }

    @Test fun restoreOriginalEncryptedInstallationFiles() {
        if (!checkpoint.exists()) { check(!originals.exists()); return }
        val row = read()
        runBlocking(Dispatchers.IO) { SocialPushWakeScheduling.cancelAndAwait(context, wake) }
        assertNoActivity()
        manager.cancel(tag(row), 0)
        if (row.get("phase").asString != "copying") {
            if (obstruction.isDirectory) { check(obstruction.listFiles()!!.isEmpty()); check(obstruction.delete()) }
            restoreProcessFixtureFiles(context, originals, names(), row.getAsJsonArray("savedFiles"))
        }
        assertEquals(row.get("originalWalletDigest").asString, digest(AndroidWalletStore(context).loadExisting().exportJson()))
        assertEquals(row.get("originalBackend").asString, AndroidBackendStore(context).load().id)
        if (originals.exists()) {
            check(originals.isDirectory && originals.listFiles()!!.all { it.isFile && it.name in processFixtureFiles(context, names()) })
            check(originals.deleteRecursively())
        }
        AtomicFile(checkpoint).delete()
        device.pressBack(); assertNoActivity()
    }

    private fun gateway(row: JsonObject) = mockwebserver3.MockWebServer().apply {
        // Both generations opt in before bind, so accepted connections from the killed
        // process cannot prevent the same owned endpoint from recovering immediately.
        serverSocketFactory = object : ServerSocketFactory() {
            override fun createServerSocket() = ServerSocket().apply { reuseAddress = true }
            override fun createServerSocket(port: Int) = createServerSocket(port, 50)
            override fun createServerSocket(port: Int, backlog: Int) = createServerSocket(port, backlog, null)
            override fun createServerSocket(port: Int, backlog: Int, address: InetAddress?) =
                createServerSocket().apply { bind(InetSocketAddress(address, port), backlog) }
        }
        dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest): mockwebserver3.MockResponse = try {
                require(request.method == "POST" && request.headers["X-Saito-Owner"] == wallet.publicKey)
                require(JsonParser.parseString(requireNotNull(request.body).utf8()) == JsonObject().apply {
                    addProperty("version", 1); addProperty("wake", wake)
                })
                val operation = when (request.url.encodedPath) {
                    "/v1/wakes/resolve" -> "resolve"
                    "/v1/wakes/original" -> "original"
                    else -> error("Unexpected owned private receipt route")
                }
                require(SaitoWallet.verify("Saito push $operation v1\n${request.url}\n$wake".toByteArray(),
                    requireNotNull(request.headers["X-Saito-Signature"]), wallet.publicKey))
                mockwebserver3.MockResponse.Builder().body(JsonObject().apply {
                    addProperty("version", 1)
                    if (operation == "resolve") {
                        addProperty("wallet", wallet.publicKey); addProperty("backend", "owner")
                        addProperty("signature", row.get("signature").asString); addProperty("kind", "private-chat")
                    } else addProperty("envelope", row.get("envelope").asString)
                }.toString()).build()
            } catch (failure: Throwable) {
                gatewayFailure.set(failure); mockwebserver3.MockResponse.Builder().code(500).build()
            }
        }
    }

    private fun enqueue(port: Int) = runBlocking(Dispatchers.IO) {
        SocialPushWakeScheduling.enqueue(context, wake, "http://127.0.0.1:$port/v1/wakes/resolve")
    }
    private fun tag(row: JsonObject) = "chat:${wallet.publicKey}:owner:${row.get("signature").asString}"
    private fun assertHistory(row: JsonObject) {
        val entries = ChatHistoryStore(context, wallet, SaitoBackend.OWNER, sender.publicKey).load().entries
        assertEquals(2, entries.size)
        val older = entries.single { it.message.id == row.get("legacySignature").asString }
        assertEquals(legacyText, older.message.text); assertEquals(sender.publicKey, older.message.author)
        val entry = entries.single { it.message.id == row.get("signature").asString }
        assertEquals(row.get("signature").asString, entry.message.id)
        assertEquals(text, entry.message.text); assertEquals(sender.publicKey, entry.message.author)
        assertTrue(sender.publicKey in ChatHistoryStore.contacts(context, wallet, SaitoBackend.OWNER).accounts)
    }
    private fun assertIdentity(row: JsonObject) {
        assertEquals(row.get("walletDigest").asString, digest(AndroidWalletStore(context).loadExisting().exportJson()))
        assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
    }
    private fun assertSilent(row: JsonObject) {
        val deadline = SystemClock.elapsedRealtime() + 2_000
        do { assertTrue(manager.activeNotifications.none { it.tag == tag(row) }); SystemClock.sleep(50) }
        while (SystemClock.elapsedRealtime() < deadline)
    }
    private fun assertNoActivity() = instrumentation.runOnMainSync {
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        assertTrue(Stage.values().filter { it != Stage.DESTROYED }
            .flatMap { monitor.getActivitiesInStage(it) }.none { it is MainActivity })
    }
    private fun await(label: String, milliseconds: Long, ready: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + milliseconds
        do {
            gatewayFailure.get()?.let { throw AssertionError("Owned gateway rejected the request", it) }
            if (ready()) return
            SystemClock.sleep(50)
        } while (SystemClock.elapsedRealtime() < deadline)
        error("No $label before its deadline")
    }
    private val historyName get() = "chat.owner.${wallet.publicKey}.${PrivateChat.conversationId(wallet.publicKey, sender.publicKey)}.v1"
    private fun names() = listOf("wallet.v1", "wallet.recovery.v1", "backend.v1", "backend.pending",
        "personal.${wallet.publicKey}.v1", "personal.${wallet.publicKey}.guard.v1", "personal.${wallet.publicKey}.recovery.v1",
        "socialpush.${wallet.publicKey}.settings.v1", "chatpush.${wallet.publicKey}.settings.v1",
        "chatpush.${wallet.publicKey}.owner.ledger.v1", "chat-alerts.${wallet.publicKey}.settings.v1",
        historyName)
        .flatMap { listOf(it, "$it.bak", "$it.new") }.toSet()
    private fun write(row: JsonObject) {
        val file = AtomicFile(checkpoint); val output = file.startWrite()
        try { output.write(row.toString().toByteArray()); output.fd.sync(); file.finishWrite(output) }
        catch (failure: Exception) { file.failWrite(output); throw failure }
        assertEquals(row, read())
    }
    private fun read() = JsonParser.parseString(checkpoint.readText()).asJsonObject.also {
        require(it.get("owner").asString == wallet.publicKey && it.get("wake").asString == wake &&
            it.get("backend").asString == "owner" && it.get("obstruction").asString == obstructionName)
    }
    private fun capture(name: String) = captureNativeScreen(File(context.cacheDir, "private-push-process-evidence/$name.png"))
    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
