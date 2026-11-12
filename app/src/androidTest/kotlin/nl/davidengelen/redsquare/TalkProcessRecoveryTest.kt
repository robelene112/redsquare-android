package nl.davidengelen.redsquare

import android.content.Intent
import android.os.Build
import android.os.Process
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.chat.ChatWorkStore
import nl.davidengelen.redsquare.talk.*
import kotlinx.coroutines.runBlocking
import org.webrtc.VideoSink
import java.util.concurrent.atomic.AtomicInteger
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Real camera capture and foreground ownership followed by actual process death and a stale return intent. */
class TalkProcessRecoveryTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also {
        check(it.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
    }
    private val contact = SaitoWallet.fromPrivateKey("0".repeat(63) + "1").publicKey
    private val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2").withChatContact(contact)
        private val draft = "Retain this unsent message after an interrupted call"
    private val checkpoint get() = File(context.cacheDir, "talk-process-checkpoint.json")
    private val originals get() = File(context.noBackupFilesDir, "talk-process-originals")
    private val prefixes get() = listOf("chatwork.", "chatpush.${wallet.publicKey}.",
        "groupchatpush.${wallet.publicKey}.", "avatarprofiles.", "displaynames.")
    private fun names(): Set<String> = (listOf("wallet.v1", "wallet.v1.bak", "wallet.v1.new",
        "wallet.recovery.v1", "wallet.recovery.v1.bak", "wallet.recovery.v1.new") +
        context.noBackupFilesDir.listFiles().orEmpty().filter { it.isFile && prefixes.any(it.name::startsWith) }.map { it.name }).toSet()
    private fun preferences() = listOf("name-directory" to "enabled", "notification-permission" to "offered",
        "notification-permission" to "social-offered:${wallet.publicKey}", "wallet-backup-reminders" to "hidden:${wallet.publicKey}")

    @Test fun terminateDuringActualCameraCaptureAndForegroundCall() {
        check(!checkpoint.exists() && !originals.exists())
        val wallets = AndroidWalletStore(context)
        val previous = wallets.loadOrCreate()
        val backend = AndroidBackendStore(context).load()
        val row = JsonObject().apply {
            addProperty("phase", "prepared"); addProperty("pid", Process.myPid()); addProperty("backend", backend.id)
            addProperty("originalWalletDigest", digest(previous.exportJson())); addProperty("walletDigest", digest(wallet.exportJson()))
            add("savedFiles", snapshotProcessFixtureFiles(context, originals, names()))
            add("preferences", JsonArray().apply { preferences().forEach { (name, key) ->
                val pref = context.getSharedPreferences(name, 0)
                add(JsonObject().apply {
                    addProperty("name", name); addProperty("key", key)
                    if (pref.contains(key)) addProperty("value", pref.getBoolean(key, false))
                })
            } })
        }
        write(row) // Restoration checkpoint precedes every fixture mutation.
        synchronized(SaitoLocalState.lock) {
            names().filterNot { it.startsWith("wallet.") }.forEach { name ->
                val file = File(context.noBackupFilesDir, name); check(!file.exists() || file.delete())
            }
        }
        wallets.replace(previous, wallet)
        preferences().forEach { (name, key) -> check(context.getSharedPreferences(name, 0).edit().putBoolean(key, true).commit()) }
        ChatWorkStore(context, wallet, backend, contact).saveDraft(draft)
        ControlledFeedPeer("[]", advertisedServices = "archive||;relay||").start().use { server ->
            val endpoint = "ws://127.0.0.1:${server.port}/wsopen"
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", endpoint).putExtra("feedOfficialPeerUrl", endpoint)).use {
                TalkRuntime.visibility(true)
                val account = TalkRuntime.account(context, wallet, backend, endpoint).also { it.attach() }
                val room = TalkRoom(SaitoWallet.create().publicKey, wallet.publicKey)
                assertTrue(runBlocking { account.start(TalkLink(backend, room), null, true, true) })
                val call = requireNotNull(TalkRuntime.call.value)
                val track = requireNotNull(call.localVideo)
                val frames = AtomicInteger()
                track.addSink(VideoSink { if (it.buffer.width >= 320) frames.incrementAndGet() })
                ui.waitUntil(10_000) { frames.get() >= 3 }
                val alerts = context.getSystemService(android.app.NotificationManager::class.java).activeNotifications
                assertTrue(alerts.any { it.id == TalkService.ID })
                row.addProperty("token", call.token); row.addProperty("cameraFrames", frames.get())
                row.addProperty("phase", "pending"); write(row)
                Process.killProcess(Process.myPid())
            }
        }
        error("Expected actual process termination")
    }

    @Test fun coldRestartRejectsTheOldCallReturnWithoutCaptureAndRetainsTheDraft() {
        val row = read(); assertEquals("pending", row.get("phase").asString)
        assertNotEquals(row.get("pid").asInt, Process.myPid())
        val backend = SaitoBackend.entries.single { it.id == row.get("backend").asString }
        val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = { "[]" })
        peer.start().use { server ->
            val endpoint = "ws://127.0.0.1:${server.port}/wsopen"
            val oldReturn = android.net.Uri.Builder().scheme("redsquare-talk").authority("call")
                .appendPath(row.get("token").asString).appendQueryParameter("owner", wallet.publicKey)
                .appendQueryParameter("backend", backend.id).build()
            assertNull(TalkRuntime.call.value)
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).setData(oldReturn)
                .putExtra("feedPeerUrl", endpoint).putExtra("feedOfficialPeerUrl", endpoint)).use {
                withFailureCapture({ capture("failure.png") }) {
                    val label = context.getString(R.string.talk_return_expired)
                    ui.waitUntil(15_000) { ui.onNodeWithText(label).isDisplayed() }
                    assertNull(TalkRuntime.call.value)
                    assertTrue(context.getSystemService(android.app.NotificationManager::class.java)
                        .activeNotifications.none { it.id == TalkService.ID })
                    capture("expired-after-cold-start.png", label)
                    ui.onNodeWithText("Chat").performClick()
                    ui.waitUntil(15_000) { ui.onNodeWithText(contact).isDisplayed() }
                    ui.onNodeWithText(contact).performClick()
                    ui.waitUntil(15_000) { ui.onNode(hasText(draft) and hasSetTextAction()).isDisplayed() }
                    assertEquals(row.get("walletDigest").asString, digest(AndroidWalletStore(context).loadExisting().exportJson()))
                    assertEquals(backend, AndroidBackendStore(context).load())
                    assertTrue(peer.relayedMessages.isEmpty() && peer.transactions.isEmpty()); peer.assertHealthy()
                    capture("draft-after-cold-start.png", draft)
                    row.addProperty("phase", "recovered"); row.addProperty("recoveredPid", Process.myPid()); write(row)
                }
            }
        }
    }

    @Test fun restoreOriginalEncryptedInstallationFiles() {
        if (!checkpoint.exists()) return
        val row = read()
        restoreProcessFixtureFiles(context, originals, names() + row.getAsJsonArray("savedFiles").map { it.asString }, row.getAsJsonArray("savedFiles"))
        for (entry in row.getAsJsonArray("preferences")) {
            val pref = entry.asJsonObject; val name = pref.get("name").asString; val key = pref.get("key").asString
            check(name to key in preferences())
            check(context.getSharedPreferences(name, 0).edit().apply {
                if (pref.has("value")) putBoolean(key, pref.get("value").asBoolean) else remove(key)
            }.commit())
        }
        assertEquals(row.get("originalWalletDigest").asString, digest(AndroidWalletStore(context).loadExisting().exportJson()))
        check(originals.deleteRecursively()); check(checkpoint.delete())
    }
    private fun read() = JsonParser.parseString(checkpoint.readText()).asJsonObject
    private fun write(row: JsonObject) = checkpoint.outputStream().use { it.write(row.toString().toByteArray()); it.fd.sync() }
    private fun capture(name: String, text: String? = null) = captureNativeScreen(File(context.cacheDir, "talk-process-evidence/$name"), text)
    private fun digest(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
