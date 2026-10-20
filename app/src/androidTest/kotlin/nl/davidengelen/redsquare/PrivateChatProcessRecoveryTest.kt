package nl.davidengelen.redsquare

import android.content.Intent
import android.os.Build
import android.os.Process
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.chat.ChatHistoryStore
import nl.davidengelen.redsquare.chat.ChatWorkStore
import nl.davidengelen.redsquare.chat.ChatReactionStore
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Two real processes; original encrypted installation files remain on the guarded emulator only. */
class PrivateChatProcessRecoveryTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also {
        check(it.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
    }
    private val checkpoint get() = File(context.cacheDir, "private-chat-process-checkpoint.json")
    private val originals get() = File(context.noBackupFilesDir, "private-chat-process-originals")
    private val contact = SaitoWallet.fromPrivateKey("0".repeat(63) + "1").publicKey
    private val pendingContact = SaitoWallet.fromPrivateKey("0".repeat(63) + "3").publicKey
    private val fixtureWallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        .withChatContact(contact).withChatContact(pendingContact)
    private val incomingText = "Cached private message before process death"
    private val pendingText = "Uncertain private message before process death"
    private val draftText = "Unsent private draft 😀 <&>\nsecond line"

    @Test fun terminateWithCachedHistoryUnsentDraftAndUncertainOriginal() {
        check(!checkpoint.exists() && !originals.exists())
        val store = AndroidWalletStore(context)
        val original = store.loadOrCreate()
        val backend = AndroidBackendStore(context).load()
        val row = JsonObject().apply {
            addProperty("phase", "prepared"); addProperty("owner", fixtureWallet.publicKey)
            addProperty("backend", backend.id); addProperty("originalOwner", original.publicKey)
            addProperty("originalWalletDigest", digest(original.exportJson()))
            addProperty("walletDigest", digest(fixtureWallet.exportJson()))
            addProperty("pid", Process.myPid())
        }
        row.add("savedFiles", snapshotProcessFixtureFiles(context, originals, fileNames(backend)))
        val preferences = JsonArray()
        for ((name, key) in preferenceKeys()) {
            val prefs = context.getSharedPreferences(name, 0)
            preferences.add(JsonObject().apply {
                addProperty("name", name); addProperty("key", key)
                if (prefs.contains(key)) addProperty("value", prefs.getBoolean(key, false))
            })
        }
        row.add("preferences", preferences)
        writeCheckpoint(row) // Publish restoration information before any fixture mutation.
        synchronized(SaitoLocalState.lock) {
            processFixtureFiles(context,fileNames(backend)).filterNot { it.startsWith("wallet.") }.forEach { name ->
                val file = File(context.noBackupFilesDir, name); check(!file.exists() || file.delete())
            }
        }
        store.replace(original, fixtureWallet)
        for ((name, key) in preferenceKeys()) check(context.getSharedPreferences(name, 0).edit().putBoolean(key, true).commit())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val reference = instrumentation.context.assets.open("native-private-chat-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject }
        val incoming = reference.get("archiveEnvelope").asString
        val history = ChatHistoryStore(context, fixtureWallet, backend, contact)
        history.save(listOf(incoming))
        val other = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
        ChatHistoryStore(context, fixtureWallet, backend, pendingContact).save(listOf(
            PrivateChat.create(other, fixtureWallet.publicKey, incomingText, 1700000000998).envelope()))
        val candidate = PrivateChat.create(fixtureWallet, pendingContact, pendingText, 1700000000999)
        ChatWorkStore(context, fixtureWallet, backend, pendingContact).prepare(candidate)
        row.addProperty("incomingDigest", digest(incoming)); row.addProperty("candidate", candidate.id)
        row.addProperty("candidateDigest", digest(candidate.envelope()))
        val reactionReference = instrumentation.context.assets.open("native-private-chat-like-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject }
        val reaction = SaitoTransaction.restore(reactionReference.get("networkBase64").asString)
        ChatReactionStore(context, fixtureWallet, backend, contact).prepare(reaction)
        row.addProperty("reaction", reaction.id); row.addProperty("reactionDigest", digest(reaction.envelope()))
        val controlled = peer()
        controlled.start().use { server ->
            launch(server.port) {
                openChat(); openContact(contact)
                waitFor(reference.getAsJsonObject("plaintext").get("message").asString)
                ui.waitUntil(15_000) {
                    ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty()
                }
                ui.onNode(hasSetTextAction()).performTextReplacement(draftText)
                waitFor(context.getString(R.string.chat_reaction_unknown)); waitFor("1 like")
                waitForEnabled(context.getString(R.string.chat_reaction_retry))
                ui.onNodeWithText("Unlike").assertIsNotEnabled()
                capture("unknown-reaction-before-death.png", context.getString(R.string.chat_reaction_unknown))
                ui.onNodeWithText("Back").performClick(); waitFor("Public key")
                openContact(pendingContact)
                waitFor(context.getString(R.string.chat_send_unknown))
                waitForEnabled("Retry")
                showMessage(pendingText)
                capture("unknown-before-death.png", pendingText)
                assertState(row, backend, controlled)
                row.addProperty("phase", "pending"); writeCheckpoint(row)
                Process.killProcess(Process.myPid())
                error("Expected actual process termination")
            }
        }
    }

    @Test fun coldRestartRetainsHistoryDraftAndOriginalWithoutSending() {
        val row = readCheckpoint(); assertEquals("pending", row.get("phase").asString)
        assertNotEquals(row.get("pid").asInt, Process.myPid())
        val backend = SaitoBackend.entries.single { it.id == row.get("backend").asString }
        val controlled = peer()
        controlled.start().use { server ->
            launch(server.port) {
                openChat(); openContact(pendingContact)
                waitFor(context.getString(R.string.chat_load_error))
                waitFor(context.getString(R.string.chat_send_unknown)); waitForEnabled("Retry")
                showMessage(pendingText)
                capture("unknown-cold-recovered.png", pendingText)
                ui.onNodeWithText("Back").performClick(); waitFor("Public key")
                openContact(contact); waitFor(context.getString(R.string.chat_load_error))
                waitForEnabled("Send")
                ui.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(
                    SemanticsProperties.EditableText, AnnotatedString(draftText)))
                val reference = InstrumentationRegistry.getInstrumentation().context.assets
                    .open("native-private-chat-reference.json").bufferedReader().use {
                        JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject
                    }
                waitFor(reference.getAsJsonObject("plaintext").get("message").asString)
                waitFor(context.getString(R.string.chat_reaction_unknown)); waitFor("1 like")
                waitForEnabled(context.getString(R.string.chat_reaction_retry))
                ui.onNodeWithText("Unlike").assertIsNotEnabled()
                capture("unknown-reaction-cold-recovered.png", context.getString(R.string.chat_reaction_unknown))
                capture("draft-and-cache-cold-recovered.png", draftText)
                assertState(row, backend, controlled)
                val retained = ChatHistoryStore(context, fixtureWallet, backend, contact).load().entries
                assertTrue(retained.any { digest(it.envelope) == row.get("incomingDigest").asString })
                row.addProperty("phase", "recovered"); row.addProperty("recoveredPid", Process.myPid())
                writeCheckpoint(row)
            }
        }
    }

    @Test fun restoreOriginalEncryptedInstallationFiles() {
        if (!checkpoint.exists()) return
        val row = readCheckpoint()
        val backend = SaitoBackend.entries.single { it.id == row.get("backend").asString }
        restoreProcessFixtureFiles(context, originals, fileNames(backend), row.getAsJsonArray("savedFiles"))
        for (item in row.getAsJsonArray("preferences")) {
            val pref = item.asJsonObject
            val name = pref.get("name").asString; val key = pref.get("key").asString
            check(name to key in preferenceKeys())
            check(context.getSharedPreferences(name, 0).edit().apply {
                if (pref.has("value")) putBoolean(key, pref.get("value").asBoolean) else remove(key)
            }.commit())
        }
        assertEquals(row.get("originalWalletDigest").asString, digest(AndroidWalletStore(context).loadExisting().exportJson()))
        assertEquals(backend, AndroidBackendStore(context).load())
        check(originals.deleteRecursively()); check(checkpoint.delete())
    }

    private fun assertState(row: JsonObject, backend: SaitoBackend, peer: ControlledFeedPeer) {
        assertEquals(row.get("walletDigest").asString, digest(AndroidWalletStore(context).loadExisting().exportJson()))
        assertEquals(backend, AndroidBackendStore(context).load())
        assertEquals(draftText, ChatWorkStore(context, fixtureWallet, backend, contact).load().draft)
        val saved = ChatWorkStore(context, fixtureWallet, backend, pendingContact).load()
        assertEquals(ChatWorkStore.Status.UNKNOWN, saved.status)
        assertEquals(row.get("candidate").asString, saved.transaction!!.id)
        assertEquals(row.get("candidateDigest").asString, digest(saved.transaction.envelope()))
        val reaction = ChatReactionStore(context, fixtureWallet, backend, contact).load()
        assertEquals(ChatReactionStore.Status.UNKNOWN, reaction.status)
        assertEquals(row.get("reaction").asString, reaction.transaction!!.id)
        assertEquals(row.get("reactionDigest").asString, digest(reaction.transaction.envelope()))
        assertEquals(listOf(row.get("reaction").asString), reaction.events.map { it.id })
        assertTrue(peer.clientKeys.isNotEmpty() && peer.clientKeys.all { it == fixtureWallet.publicKey })
        assertTrue(peer.transactions.isEmpty()); assertTrue(peer.reports.isEmpty())
        assertTrue(peer.relayedMessages.isEmpty()); peer.assertHealthy()
    }
    private fun peer() = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = { "{invalid" })
    private fun fileNames(backend: SaitoBackend): Set<String> =
        (listOf("wallet.v1", "wallet.recovery.v1") + listOf(contact, pendingContact).flatMap { key ->
            val id = PrivateChat.conversationId(fixtureWallet.publicKey, key)
            listOf("chat.${backend.id}.${fixtureWallet.publicKey}.$id.v1", "chatwork.${backend.id}.${fixtureWallet.publicKey}.$id.v1",
                "chatreactions.${backend.id}.${fixtureWallet.publicKey}.$id.v1")
        }).flatMap { base -> listOf(base, "$base.bak", "$base.new") }.toSet()
    private fun preferenceKeys() = listOf("notification-permission" to "offered",
        "wallet-backup-reminders" to "hidden:${fixtureWallet.publicKey}")
    private fun digest(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        .joinToString("") { "%02x".format(it) }
    private fun readCheckpoint() = JsonParser.parseString(checkpoint.readText()).asJsonObject
    private fun writeCheckpoint(row: JsonObject) = checkpoint.outputStream().use { output ->
        output.write(row.toString().toByteArray()); output.fd.sync()
    }
    private fun launch(port: Int, journey: () -> Unit) {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .putExtra("feedPeerUrl", "ws://127.0.0.1:$port/wsopen")
            .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:$port/wsopen")).use {
            withFailureCapture({ capture("first-failure.png") }, journey)
        }
    }
    private fun openChat() { waitForEnabled("Refresh"); ui.onNodeWithText("Chat").performClick(); waitFor("Public key") }
    private fun openContact(key: String) { waitFor(key); ui.onNodeWithText(key).performClick(); waitFor("Message") }
    private fun capture(name: String, text: String? = null) = captureNativeScreen(File(context.cacheDir,
        "private-chat-process-evidence/$name"), text)
    private fun showMessage(text: String) {
        val message = hasText(text) and !hasSetTextAction()
        ui.waitUntil(15_000) {
            runCatching {
                val history = ui.onNodeWithContentDescription("Private messages")
                check(history.fetchSemanticsNode().boundsInRoot.height > 0f)
                history.performScrollToNode(message)
                ui.onNode(message).assertIsDisplayed()
            }.isSuccess
        }
        ui.onNode(message).assertIsDisplayed()
    }
    private fun waitFor(text: String) = ui.waitUntil(15_000) {
        ui.onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }
    private fun waitForEnabled(text: String) = ui.waitUntil(15_000) {
        ui.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }
}
