package nl.davidengelen.redsquare

import android.content.Intent
import android.os.Build
import android.os.Process
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference

/** Host-controlled process death. Checkpoints contain synthetic public posts only. */
class AuthorProcessRecoveryTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val delete get() = InstrumentationRegistry.getArguments().getString("authorKind") == "delete"
    private val checkpoint get() = File(context.cacheDir, "author-process-checkpoint.json")
    private val originalText = "Recover the author's original post"
    private val editedText = "Recover this exact uncertain edit"

    private fun prepare() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(Build.HARDWARE in listOf("ranchu", "goldfish"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
    }

    private fun waitFor(text: String) = ui.waitUntil(20_000) {
        ui.onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    private fun enabled(text: String) = ui.waitUntil(20_000) {
        ui.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    @Test fun terminateAfterAnUncertainAuthorChange() {
        prepare()
        context.noBackupFilesDir.listFiles()?.filter {
            it.name.startsWith("postdraft.") || it.name.startsWith("feed.") ||
                it.name.startsWith("conversation.") || it.name.startsWith("profile.") ||
                it.name.startsWith("authorposts.") || it.name.startsWith("quoteoriginal.")
        }?.forEach { check(it.delete()) }
        val original = AtomicReference<JsonObject?>()
        val action = AtomicReference<JsonObject?>()
        val peer = ControlledFeedPeer("[]", onTransaction = { frame ->
            val transaction = postingTransaction(frame)
            val signed = SaitoEnvelope.decode(transaction.envelope())
            if (signed.body.get("request").asString == "create tweet") {
                assertEquals(originalText, RedSquareFeed.decode(transaction.envelope()).text)
                original.set(postingArchiveRow(transaction))
            } else {
                val command = RedSquareAuthorActions.decode(transaction.envelope())
                assertEquals(if (delete) AuthorActionKind.DELETE else AuthorActionKind.EDIT, command.kind)
                assertEquals(original.get()!!.get("sig").asString, command.target)
                assertEquals(original.get()!!.get("field2").asString, command.author)
                assertEquals(if (delete) "" else editedText, command.text)
                action.set(JsonObject().apply {
                    addProperty("sig", command.id); addProperty("field1", "RedSquare")
                    addProperty("field2", command.author); addProperty("created_at", command.createdAt)
                    addProperty("tx", command.envelope)
                })
            }
        }, archiveResponse = { criteria ->
            if (criteria.get("field1").asString != "RedSquare") "[]"
            else listOfNotNull(original.get()).filter {
                (!criteria.has("sig") || criteria.get("sig").asString == it.get("sig").asString) &&
                    (!criteria.has("field2") || criteria.get("field2").asString == it.get("field2").asString) &&
                    (!criteria.has("field5") || criteria.get("field5").asString == it.get("field5")?.asString)
            }.joinToString(prefix = "[", postfix = "]")
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                enabled("New post")
                ui.onNodeWithText("New post").performClick()
                ui.waitUntil(20_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNode(hasSetTextAction()).performTextReplacement(originalText)
                ui.onNodeWithText("Send").performClick()
                waitFor("Available on this node.")
                enabled("Back")
                ui.onNodeWithText("Back").performClick()
                enabled("Refresh")
                ui.waitUntil(20_000) { ui.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNode(hasScrollAction()).performScrollToNode(hasText(originalText))
                ui.onNodeWithText(originalText).performClick()
                ui.clickVisiblePostMenuAction("Anon-${original.get()!!.get("field2").asString.take(6)}",
                    if (delete) "Delete" else "Edit")
                enabled(if (delete) "Confirm delete" else "Save changes")
                if (!delete) ui.onNode(hasSetTextAction()).performTextReplacement(editedText)
                ui.onNodeWithText(if (delete) "Confirm delete" else "Save changes").performClick()
                ui.waitUntil(20_000) { action.get() != null }
                assertEquals(2, peer.transactions.size)
                peer.assertHealthy()
                checkpoint.outputStream().use { output ->
                    val saved = JsonObject().apply {
                        addProperty("pid", Process.myPid()); addProperty("kind", if (delete) "delete" else "edit")
                        add("original", requireNotNull(original.get())); add("action", requireNotNull(action.get()))
                    }
                    output.write(saved.toString().toByteArray())
                    output.fd.sync()
                }
                Process.killProcess(Process.myPid())
                error("Expected process termination")
            }
        }
    }

    @Test fun coldRecoveryChecksTheOriginalAuthorChangeWithoutRebroadcasting() {
        prepare()
        val saved = JsonParser.parseString(checkpoint.readText()).asJsonObject
        assertEquals(if (delete) "delete" else "edit", saved.get("kind").asString)
        val original = saved.getAsJsonObject("original")
        val action = saved.getAsJsonObject("action")
        val served = original.deepCopy().apply {
            if (!delete) {
                val envelope = JsonParser.parseString(get("tx").asString).asJsonObject
                val options = JsonObject().apply {
                    addProperty("update_tx", action.get("tx").asString)
                    addProperty("edit_ts", action.get("created_at").asLong)
                }
                envelope.addProperty("opt", Base64.getEncoder().encodeToString(options.toString().toByteArray()))
                addProperty("tx", envelope.toString())
            }
        }
        val peer = ControlledFeedPeer("[]", archiveResponse = { criteria ->
            // No feed preload: only explicit recovery's exact target/command lookups succeed.
            if (criteria.get("field1").asString != "RedSquare" || !criteria.has("sig")) "[]"
            else when (criteria.get("sig").asString) {
                original.get("sig").asString -> if (delete) "[]" else "[$served]"
                action.get("sig").asString -> if (delete) "[$action]" else "[]"
                else -> "[]"
            }
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                enabled("New post")
                ui.onNodeWithText("Wallet").performClick()
                assertEquals(original.get("field2").asString, displayedWalletAddress(ui))
                ui.onNodeWithText("Done").performClick()
                enabled("More feed actions")
                ui.onNodeWithText("More feed actions").performClick()
                ui.onNodeWithText("Saved post changes").performClick()
                enabled(if (delete) "Delete post" else "Edit post")
                assertTrue(peer.transactions.isEmpty())
                ui.onNodeWithText(if (delete) "Delete post" else "Edit post").performClick()
                enabled("Retry")
                if (!delete) ui.onNodeWithText(editedText).assertTextEquals(editedText).assertIsNotEnabled()
                assertTrue(peer.transactions.isEmpty())
                ui.onNodeWithText("Retry").performClick()
                waitFor(if (delete) "Post removed on this node." else "Edit available on this node.")
                enabled("Back")
                assertTrue(peer.transactions.isEmpty())
                peer.assertHealthy()
                captureFeedEvidence("author-process-evidence/reconciled-without-rebroadcast.png")
                ui.onNodeWithText("Back").performClick()
                waitFor("No saved changes on this node.")
            }
        }
    }
}
