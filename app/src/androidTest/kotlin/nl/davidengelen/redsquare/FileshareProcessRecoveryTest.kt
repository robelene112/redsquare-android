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
import nl.davidengelen.redsquare.fileshare.*
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Actual cold-process recovery from synthetic encrypted in-progress storage, not a live-peer proof. */
class FileshareProcessRecoveryTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also {
        check(it.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
    }
    private val contact = SaitoWallet.fromPrivateKey("0".repeat(63) + "1").publicKey
    private val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2").withChatContact(contact)
    private val token = "10300000-0000-4000-8000-000000000001"
    private val draft = "Retain this unsent message after an interrupted file"
    private val checkpoint get() = File(context.cacheDir, "fileshare-process-checkpoint.json")
    private val originals get() = File(context.noBackupFilesDir, "fileshare-process-originals")
    private val prefixes get() = listOf("fileshare.", "chatwork.", "chatpush.${wallet.publicKey}.",
        "groupchatpush.${wallet.publicKey}.", "avatarprofiles.", "displaynames.")
    private fun names(): Set<String> = (listOf("wallet.v1", "wallet.v1.bak", "wallet.v1.new",
        "wallet.recovery.v1", "wallet.recovery.v1.bak", "wallet.recovery.v1.new") +
        context.noBackupFilesDir.listFiles().orEmpty().filter { it.isFile && prefixes.any(it.name::startsWith) }.map { it.name }).toSet()
    private fun preferences() = listOf("name-directory" to "enabled", "notification-permission" to "offered",
        "notification-permission" to "social-offered:${wallet.publicKey}", "wallet-backup-reminders" to "hidden:${wallet.publicKey}")

    @Test fun terminateWithAnEncryptedPartialFileAndUnsentChatDraft() {
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
        val store = FileTransferStore(context, wallet.publicKey, backend, contact)
        val value = FileTransfer(token, wallet.publicKey, backend, contact, "ws://127.0.0.1:1/wsopen",
            Fileshare.Offer("process103", "interrupted-note.bin", 1024, "application/octet-stream"), true, FilePhase.RECEIVING, 32)
        store.save(value)
        store.begin(value).append(ByteArray(32) { 7 }) // Deliberately unclosed when Android kills this process.
        store.inbox(value).append("interrupted encrypted receive frame")
        check(context.noBackupFilesDir.listFiles().orEmpty().any { it.name.startsWith("fileshare.") && it.name.endsWith(".part") })
        row.addProperty("phase", "pending"); write(row)
        Process.killProcess(Process.myPid())
        error("Expected actual process termination")
    }

    @Test fun coldRestartShowsInterruptedWithoutSendingAndRetainsTheDraft() {
        val row = read(); assertEquals("pending", row.get("phase").asString)
        assertNotEquals(row.get("pid").asInt, Process.myPid())
        val backend = SaitoBackend.entries.single { it.id == row.get("backend").asString }
        val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = { "[]" })
        peer.start().use { server ->
            val endpoint = "ws://127.0.0.1:${server.port}/wsopen"
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", endpoint).putExtra("feedOfficialPeerUrl", endpoint)).use {
                withFailureCapture({ capture("failure.png") }) {
                    ui.waitUntil(30_000) { ui.onAllNodesWithText("Chat").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.onNodeWithText("Chat").performClick()
                    ui.waitUntil(30_000) { ui.onAllNodesWithText(contact).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.onNodeWithText(contact).performClick()
                    val label = context.getString(R.string.fileshare_interrupted)
                    ui.waitUntil(30_000) { ui.onAllNodesWithText(label).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.onNodeWithText(draft).assertExists()
                    ui.onAllNodesWithText("Save as…").assertCountEquals(0)
                    val session = FileshareRuntime.session(context, wallet, backend, endpoint, contact)
                    assertEquals(FilePhase.INTERRUPTED, session.transfers.value.single { value -> value.token == token }.phase)
                    assertFalse(FileshareRuntime.hasLiveWork())
                    assertTrue(context.noBackupFilesDir.listFiles().orEmpty().none { file ->
                        file.name.startsWith("fileshare.") && (file.name.endsWith(".part") || file.name.endsWith(".data") || file.name.endsWith(".inbox"))
                    })
                    assertEquals(row.get("walletDigest").asString, digest(AndroidWalletStore(context).loadExisting().exportJson()))
                    assertEquals(backend, AndroidBackendStore(context).load())
                    assertTrue(peer.relayedMessages.isEmpty() && peer.transactions.isEmpty()); peer.assertHealthy()
                    capture("interrupted-after-cold-start.png", label)
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
    private fun capture(name: String, text: String? = null) = captureNativeScreen(File(context.cacheDir, "fileshare-process-evidence/$name"), text)
    private fun digest(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
