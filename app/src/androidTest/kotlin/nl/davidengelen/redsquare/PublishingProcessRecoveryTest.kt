package nl.davidengelen.redsquare

import android.content.Intent
import android.os.Build
import android.os.Process
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.chat.ChatQueueScheduling
import nl.davidengelen.redsquare.feed.PostDraftStore
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit

/** The host proves actual process death; installation snapshots remain on this disposable device. */
class PublishingProcessRecoveryTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also {
        check(it.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
    }
    private val checkpoint get() = File(context.cacheDir, "publishing-process-checkpoint.json")
    private val originals get() = File(context.noBackupFilesDir, "publishing-process-originals")

    @Test fun terminateAfterExplicitAgreementAndSavingTheUnsentDraft() {
        check(!checkpoint.exists() && !originals.exists())
        val wallet = AndroidWalletStore(context).loadOrCreate()
        val backend = AndroidBackendStore(context).load()
        assertTrue("Preserve existing queued Posts", PostDraftStore.pendingDelivery(context, wallet.publicKey, SaitoBackend.OWNER).isEmpty())
        assertTrue("Preserve existing queued Chat", ChatQueueScheduling.pendingIds(context, wallet, SaitoBackend.OWNER).isEmpty())
        val row = JsonObject().apply {
            addProperty("phase", "preparing"); addProperty("owner", wallet.publicKey)
            addProperty("originalBackend", backend.id); addProperty("backend", "owner")
            addProperty("walletDigest", digest(wallet.exportJson())); addProperty("pid", Process.myPid())
            addProperty("revision", "owned-process-${UUID.randomUUID()}")
            addProperty("text", "Keep this unsent draft after actual process death 😀 ${UUID.randomUUID()}")
            add("savedFiles", snapshotProcessFixtureFiles(context, originals, fileNames(wallet.publicKey)))
            add("preferences", JsonArray().apply { preferenceKeys(wallet.publicKey).forEach { (name, key) ->
                val prefs = context.getSharedPreferences(name, 0)
                add(JsonObject().apply {
                    addProperty("name", name); addProperty("key", key)
                    if (prefs.contains(key)) addProperty("value", prefs.getBoolean(key, false))
                })
            } })
        }
        writeCheckpoint(row)
        synchronized(SaitoLocalState.lock) {
            fileNames(wallet.publicKey).filterNot { it.startsWith("backend.") }.forEach { name ->
                val file = File(context.noBackupFilesDir, name); check(!file.exists() || file.delete())
            }
        }
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        preferenceKeys(wallet.publicKey).forEach { (name, key) ->
            check(context.getSharedPreferences(name, 0).edit().putBoolean(key, true).commit())
        }
        File(context.noBackupFilesDir, "publishing-policy-fixture.json").writeText(JsonObject().apply {
            addProperty("enforce", true); addProperty("ready", true); addProperty("revision", row.get("revision").asString)
            addProperty("terms", "Controlled process terms. No real publisher approval.")
            addProperty("community", "Controlled process rules. New drafts remain unsent.")
        }.toString())
        val peer = ControlledFeedPeer("[]", archiveResponse = { "[]" })
        launch(peer, "accepted-before-death-failure.png") {
            click("New post"); waitForEditor()
            ui.onNode(hasSetTextAction()).performTextReplacement(row.get("text").asString)
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            click("Send"); waitFor("Review publishing terms"); click("Accept")
            waitFor("Agreement saved. New content stays unsent. Queued messages may resume.")
            assertTrue("Agreement does not publish", peer.transactions.isEmpty())
            click("Save draft"); waitFor("New post")
            val draft = PostDraftStore(context, wallet.publicKey, SaitoBackend.OWNER).load()
            assertEquals(row.get("text").asString, draft.text); assertNull(draft.transaction)
            assertSelection(row)
            assertTrue(peer.transactions.isEmpty()); peer.assertHealthy()
            capture("accepted-draft-before-process-death.png")
            row.addProperty("phase", "accepted"); writeCheckpoint(row)
            Process.killProcess(Process.myPid())
            error("Expected actual process termination")
        }
    }

    @Test fun coldProcessKeepsAgreementButRequiresDeliberateSend() {
        val row = readCheckpoint()
        assertEquals("accepted", row.get("phase").asString)
        assertNotEquals(row.get("pid").asInt, Process.myPid())
        assertSelection(row) // No wallet/backend reset on recovery.
        val indexed = AtomicReference<JsonObject?>()
        val peer = ControlledFeedPeer("[]", onTransaction = { frame ->
            val transaction = postingTransaction(frame)
            val post = RedSquareFeed.decode(transaction.envelope())
            assertEquals(row.get("text").asString, post.text)
            assertEquals(row.get("owner").asString, post.author)
            indexed.set(postingArchiveRow(transaction))
        }, archiveResponse = { criteria ->
            indexed.get()?.takeIf { record -> criteria.get("field1")?.asString == "RedSquare" &&
                (!criteria.has("sig") || criteria.get("sig").asString == record.get("sig").asString) }
                ?.let { "[$it]" } ?: "[]"
        })
        launch(peer, "cold-agreement-failure.png") {
            click("New post"); waitForEditor()
            ui.onNode(hasSetTextAction()).assertTextEquals(row.get("text").asString).assertIsEnabled()
            assertTrue("A cold unsent draft must not automatically publish", peer.transactions.isEmpty())
            capture("cold-agreement-keeps-unsent-draft.png")
            click("Send"); waitFor("Available on this node.")
            ui.onAllNodesWithText("Review publishing terms").assertCountEquals(0)
            ui.onAllNodesWithText("Accept").assertCountEquals(0)
            assertEquals(1, peer.transactions.size)
            assertSelection(row); peer.assertHealthy()
            capture("cold-agreement-deliberate-send.png")
            row.addProperty("phase", "verified"); row.addProperty("recoveredPid", Process.myPid())
            row.addProperty("signature", requireNotNull(indexed.get()).get("sig").asString)
            writeCheckpoint(row)
        }
    }

    @Test fun restoreOriginalEncryptedInstallationFiles() {
        if (!checkpoint.exists()) { check(!originals.exists()); return }
        val row = readCheckpoint()
        val owner = row.get("owner").asString
        cleanupAfterTest(null,
            { val saved = PostDraftStore(context, owner, SaitoBackend.OWNER).load()
                saved.transaction?.takeIf { saved.text == row.get("text").asString }?.let { candidate ->
                    assertEquals(owner, RedSquareFeed.decode(candidate.envelope()).author)
                    WorkManager.getInstance(context).cancelUniqueWork("post:${candidate.id}").result.get(5, TimeUnit.SECONDS)
                }
            },
            { restoreProcessFixtureFiles(context, originals, fileNames(owner), row.getAsJsonArray("savedFiles")) },
            { row.getAsJsonArray("preferences").forEach { value ->
                val pref = value.asJsonObject
                val pair = pref.get("name").asString to pref.get("key").asString
                check(pair in preferenceKeys(owner))
                check(context.getSharedPreferences(pair.first, 0).edit().apply {
                    if (pref.has("value")) putBoolean(pair.second, pref.get("value").asBoolean) else remove(pair.second)
                }.commit())
            } })
        assertEquals(row.get("walletDigest").asString, digest(AndroidWalletStore(context).loadExisting().exportJson()))
        assertEquals(row.get("originalBackend").asString, AndroidBackendStore(context).load().id)
        check(originals.deleteRecursively()); check(checkpoint.delete())
    }

    private fun launch(peer: ControlledFeedPeer, failure: String, action: () -> Unit) {
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                withFailureCapture({ capture(failure) }, action)
            }
        }
    }
    private fun fileNames(owner: String): Set<String> {
        require(owner.matches(Regex("[1-9A-HJ-NP-Za-km-z]{43,45}")))
        return listOf("feed.owner.v1", "postdraft.owner.$owner.v1", "publishing-policy-fixture.json",
            "publishing-agreement.v1", "publishing-agreement.pending", "backend.v1", "backend.pending")
            .flatMap { listOf(it, "$it.bak", "$it.new") }.toSet()
    }
    private fun preferenceKeys(owner: String) = listOf("notification-permission" to "offered",
        "wallet-backup-reminders" to "hidden:$owner")
    private fun assertSelection(row: JsonObject) {
        assertEquals(row.get("owner").asString, AndroidWalletStore(context).loadExisting().publicKey)
        assertEquals(row.get("walletDigest").asString, digest(AndroidWalletStore(context).loadExisting().exportJson()))
        assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
    }
    private fun readCheckpoint() = JsonParser.parseString(checkpoint.readText()).asJsonObject.also {
        check(it.get("phase").asString in listOf("preparing", "accepted", "verified"))
    }
    private fun writeCheckpoint(row: JsonObject) = checkpoint.outputStream().use { output ->
        output.write(row.toString().toByteArray(Charsets.UTF_8)); output.fd.sync()
    }
    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    private fun click(text: String) {
        ui.waitUntil(20_000) { ui.onAllNodes(hasText(text) and hasClickAction() and isEnabled()).fetchSemanticsNodes().size == 1 }
        ui.onNodeWithText(text).performClick()
    }
    private fun waitFor(text: String) = ui.waitUntil(20_000) { ui.onNodeWithText(text).isDisplayed() }
    private fun waitForEditor() = ui.waitUntil(20_000) { ui.onNode(hasSetTextAction() and isEnabled()).isDisplayed() }
    private fun capture(name: String) = captureNativeScreen(File(context.cacheDir, "publishing-process-evidence/$name"))
}
