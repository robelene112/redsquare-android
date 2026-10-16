package nl.davidengelen.redsquare

import android.app.NotificationManager
import android.os.Build
import android.os.Process
import android.util.AtomicFile
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.notifications.SocialPush
import nl.davidengelen.redsquare.notifications.SocialPushResult
import nl.davidengelen.redsquare.notifications.SocialPushSettings
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Device-only original snapshots survive a real kill; an independent fresh reply proves the recovered path is active. */
class SocialPushProcessRecoveryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also {
        check(it.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
    }
    private val fixture = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Published unsafe fixture only. Never fund.
    private val selected = SaitoBackend.OWNER
    private val checkpoint get() = File(context.cacheDir, "social-push-process-checkpoint.json")
    private val originals get() = File(context.noBackupFilesDir, "social-push-process-originals")
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val reference get() = InstrumentationRegistry.getInstrumentation().context.assets
        .open("native-notification-reference.json").bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
    private val originalReply = "4626eafbb90d66fd650de2a943864198e3daeae0b8876b4c224625b4a438113508e44287fee31d34d117356565d67036537c65dbcc66d8589ea300e2fb414526"
    private val freshReply = "1991d496005db8cb234ddd8482f1b8cc01cb75a9d38fd3ff904f000144e7a6bd22da22068460a5b0f7d5f9d524ae3ac8e2a4a531bad7a2ce46f10149d2abc64b"
    private val parent = "c87f3ca402840cd7add095dbf1fda809f34d9cd3f7dae9af022e2a0f2a631f376beb54dabe886a5a73b02abe79afe51652efa57826ccd2591001d73f90544fd9"
    private fun tag(signature: String) = "social:${fixture.publicKey}:${selected.id}:$signature"
    private fun alerts() = manager.activeNotifications.filter { it.tag in setOf(tag(originalReply), tag(freshReply)) }

    @Test fun terminateAfterPublishingAndPhysicallyDismissingAReply() {
        check(!checkpoint.exists() && !originals.exists() && alerts().isEmpty())
        val captures = File(context.cacheDir, "social-push-process-evidence")
        check(!captures.exists() || captures.deleteRecursively())
        val wallets = AndroidWalletStore(context)
        val previous = wallets.loadOrCreate()
        val previousBackend = AndroidBackendStore(context).load()
        val row = JsonObject().apply {
            addProperty("phase", "copying"); addProperty("owner", fixture.publicKey)
            addProperty("backend", selected.id); addProperty("originalBackend", previousBackend.id)
            addProperty("pid", Process.myPid()); addProperty("walletDigest", digest(fixture.exportJson()))
            addProperty("originalWalletDigest", digest(previous.exportJson()))
        }
        writeCheckpoint(row)
        row.add("savedFiles", snapshotProcessFixtureFiles(context, originals, fileNames()))
        row.addProperty("phase", "prepared"); writeCheckpoint(row)
        synchronized(SaitoLocalState.lock) {
            fileNames().filterNot { it.startsWith("wallet.") || it.startsWith("backend.") }.forEach { name ->
                val file = File(context.noBackupFilesDir, name); check(!file.exists() || file.delete())
            }
        }
        wallets.replace(previous, fixture)
        AndroidBackendStore(context).save(selected)
        SocialPushSettings(context, fixture.publicKey).setEnabled(true)
        val peer = peer()
        peer.start().use { server ->
            withFailureCapture({ capture("before-death-failure") }) {
                assertEquals(SocialPushResult.PUBLISHED, receive(originalReply, server.port))
                assertEquals(tag(originalReply), alerts().single().tag)
                showReply()
                capture("published-before-death")
                val visible = requireNotNull(device.wait(Until.findObject(By.text("Replied to your post")), 5_000))
                val y = visible.visibleBounds.centerY()
                assertTrue(device.swipe(device.displayWidth / 4, y, device.displayWidth - 2, y, 20))
                assertTrue(device.wait(Until.gone(By.text("Replied to your post")), 5_000))
                val dismissedBy = android.os.SystemClock.elapsedRealtime() + 5_000
                while (alerts().isNotEmpty() && android.os.SystemClock.elapsedRealtime() < dismissedBy) {
                    android.os.SystemClock.sleep(25)
                }
                assertTrue("Android must finish the physical dismissal before silence is measured", alerts().isEmpty())
                assertSilent()
                capture("dismissed-before-death")
                assertFixture(row)
                assertNoWrites(peer)
                row.addProperty("phase", "published-and-dismissed"); writeCheckpoint(row)
                Process.killProcess(Process.myPid())
                error("Expected deliberate process termination")
            }
        }
    }

    @Test fun coldReplayStaysSilentWhileAnotherSignedReplyStillShows() {
        val row = readCheckpoint()
        assertEquals("published-and-dismissed", row.get("phase").asString)
        assertNotEquals(row.get("pid").asInt, Process.myPid())
        assertFixture(row)
        val peer = peer()
        peer.start().use { server ->
            withFailureCapture({ capture("cold-replay-failure") }) {
                assertEquals(SocialPushResult.SUPPRESSED, receive(originalReply, server.port))
                assertSilent()
                capture("cold-replay-stays-dismissed")
                assertEquals(SocialPushResult.PUBLISHED, receive(freshReply, server.port))
                assertEquals(tag(freshReply), alerts().single().tag)
                showReply()
                capture("fresh-reply-after-cold-replay")
                assertFixture(row)
                assertNoWrites(peer)
                row.addProperty("phase", "recovered"); row.addProperty("recoveredPid", Process.myPid())
                writeCheckpoint(row)
                device.pressBack()
            }
        }
    }

    @Test fun restoreOriginalEncryptedInstallationFiles() {
        if (!checkpoint.exists()) { check(!originals.exists()); return }
        val row = readCheckpoint()
        val allowedPhases = setOf("copying", "prepared", "published-and-dismissed", "recovered")
        require(row.get("phase").asString in allowedPhases)
        // The two fixture tags were absent before snapshot acquisition. Other alerts stay untouched.
        alerts().forEach { manager.cancel(it.tag, it.id) }
        if (row.get("phase").asString != "copying") {
            restoreProcessFixtureFiles(context, originals, fileNames(), row.getAsJsonArray("savedFiles"))
        }
        assertEquals(row.get("originalWalletDigest").asString, digest(AndroidWalletStore(context).loadExisting().exportJson()))
        assertEquals(row.get("originalBackend").asString, AndroidBackendStore(context).load().id)
        if (originals.exists()) {
            check(originals.isDirectory && originals.listFiles()!!.all { it.isFile && it.name in fileNames() })
            check(originals.deleteRecursively())
        }
        AtomicFile(checkpoint).delete()
        device.pressBack()
    }

    private fun peer(): ControlledFeedPeer {
        val rows = reference
        assertEquals(originalReply, rows.getAsJsonObject("incomingReply").get("sig").asString)
        assertEquals(freshReply, rows.getAsJsonObject("parentIndexedReply").get("sig").asString)
        assertEquals(parent, rows.getAsJsonObject("root").get("sig").asString)
        val indexed = mapOf(originalReply to rows.get("incomingReply"), freshReply to rows.get("parentIndexedReply"), parent to rows.get("root"))
        return ControlledFeedPeer("[]", archiveResponse = { criteria ->
            require(criteria.get("field1").asString == "RedSquare" && criteria.get("flagged_ne").asInt == 1 && criteria.get("limit").asInt == 1)
            "[${indexed.getValue(criteria.get("sig").asString)}]"
        })
    }

    private fun receive(signature: String, port: Int) = runBlocking(Dispatchers.IO) {
        val payload = JsonObject().apply {
            addProperty("version", 1); addProperty("wallet", fixture.publicKey)
            addProperty("backend", selected.id); addProperty("signature", signature)
        }
        SocialPush.receive(context, payload.toString(), "ws://127.0.0.1:$port/wsopen", 1700000100000)
    }

    private fun showReply() {
        assertTrue(device.openNotification())
        assertTrue(device.wait(Until.hasObject(By.text("Replied to your post")), 15_000))
    }

    private fun assertSilent() {
        val until = android.os.SystemClock.elapsedRealtime() + 2_000
        do { assertTrue("Fixture alerts must stay absent", alerts().isEmpty()); android.os.SystemClock.sleep(50) }
        while (android.os.SystemClock.elapsedRealtime() < until)
    }

    private fun assertFixture(row: JsonObject) {
        assertEquals(fixture.publicKey, row.get("owner").asString)
        assertEquals(row.get("walletDigest").asString, digest(AndroidWalletStore(context).loadExisting().exportJson()))
        assertEquals(selected, AndroidBackendStore(context).load())
    }

    private fun assertNoWrites(peer: ControlledFeedPeer) {
        assertTrue(peer.transactions.isEmpty() && peer.reports.isEmpty() && peer.relayedMessages.isEmpty())
        peer.assertHealthy()
    }

    private fun fileNames(): Set<String> = listOf("wallet.v1", "wallet.recovery.v1", "backend.v1", "backend.pending",
        "personal.${fixture.publicKey}.v1", "personal.${fixture.publicKey}.guard.v1", "personal.${fixture.publicKey}.recovery.v1",
        "socialpush.${fixture.publicKey}.settings.v1", "socialpush.${fixture.publicKey}.${selected.id}.ledger.v1")
        .flatMap { listOf(it, "$it.bak", "$it.new") }.toSet()

    private fun writeCheckpoint(row: JsonObject) {
        val file = AtomicFile(checkpoint)
        val output = file.startWrite()
        try { output.write(row.toString().toByteArray(Charsets.UTF_8)); output.fd.sync(); file.finishWrite(output) }
        catch (failure: Exception) { file.failWrite(output); throw failure }
        check(JsonParser.parseString(checkpoint.readText()).asJsonObject == row)
    }

    private fun readCheckpoint(): JsonObject = JsonParser.parseString(checkpoint.readText()).asJsonObject.also {
        require(it.get("owner").asString == fixture.publicKey && it.get("backend").asString == selected.id)
        require(it.get("originalBackend").asString in SaitoBackend.entries.map { backend -> backend.id })
        require(it.get("originalWalletDigest").asString.matches(Regex("[0-9a-f]{64}")))
    }

    private fun digest(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    private fun capture(name: String) = captureNativeScreen(File(context.cacheDir, "social-push-process-evidence/$name.png"))
}
