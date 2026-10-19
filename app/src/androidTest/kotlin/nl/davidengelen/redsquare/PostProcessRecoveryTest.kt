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
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.RedSquareFeed
import nl.davidengelen.redsquare.saito.SaitoBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** The host runs these phases in separate processes. All captured data is public. */
class PostProcessRecoveryTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val quote get() = InstrumentationRegistry.getArguments().getString("quote") == "true"
    private val checkpoint get() = File(context.cacheDir, if (quote) "quote-process-checkpoint.json" else "post-process-checkpoint.json")
    private val body get() = if (quote) "Recover this uncertain quote after process death" else "Recover this uncertain post after process death"

    private fun prepare() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(Build.HARDWARE in listOf("ranchu", "goldfish"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
    }

    private fun root(): JsonObject = InstrumentationRegistry.getInstrumentation().context.assets
        .open("native-following-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("root")
        }

    private fun openComposer() {
        if (quote) {
            waitFor("Native Red Square reference post")
            ui.clickVisiblePostContent("Quote")
            waitFor("Quote post")
        } else ui.onNodeWithText("New post").performClick()
    }

    private fun waitFor(text: String) = ui.waitUntil(20_000) {
        ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun terminateAfterAnUncertainSendBeforeLeavingTheComposer() {
        prepare()
        context.noBackupFilesDir.listFiles()?.filter { it.name.startsWith("postdraft.") || it.name.startsWith("feed.") }
            ?.forEach { check(it.delete()) }
        val observed = AtomicReference<JsonObject?>()
        val peer = ControlledFeedPeer("[${root()}]", onTransaction = { frame ->
            val candidate = postingTransaction(frame)
            val envelope = candidate.envelope()
            val post = RedSquareFeed.decode(envelope)
            assertEquals(body, post.text)
            if (quote) assertEquals(root().get("sig").asString, post.quoted!!.id)
            observed.set(postingArchiveRow(candidate))
        }, archiveResponse = { criteria -> if (criteria.has("sig")) "[]" else "[${root()}]" })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                waitFor("New post")
                ui.waitUntil(20_000) {
                    ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
                }
                openComposer()
                ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNode(hasSetTextAction()).performTextReplacement(body)
                ui.onNodeWithText("Send").performClick()
                waitFor("Submitted. Waiting for this node.")
                ui.waitUntil(10_000) { observed.get() != null }
                peer.assertHealthy()
                checkpoint.outputStream().use { output ->
                    val row = requireNotNull(observed.get()).deepCopy().apply { addProperty("pid", Process.myPid()) }
                    output.write(row.toString().toByteArray())
                    output.fd.sync()
                }
                Process.killProcess(Process.myPid())
                error("Expected process termination")
            }
        }
    }

    @Test fun coldStartupReconcilesTheOriginalPostWithoutRebroadcasting() {
        prepare()
        val row = JsonParser.parseString(checkpoint.readText()).asJsonObject
        val peer = ControlledFeedPeer("[${root()}]", archiveResponse = { criteria ->
            if (criteria.has("sig") && criteria.get("sig").asString == row.get("sig").asString) "[$row]"
            else if (criteria.has("sig")) "[]" else "[${root()}]"
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                waitFor("New post")
                ui.onNodeWithText("Wallet").performClick()
                assertEquals(row.get("field2").asString, displayedWalletAddress(ui))
                ui.onNodeWithText("Done").performClick()
                waitFor("New post")
                openComposer()
                waitFor("Send requested. Availability on this node is not confirmed.")
                ui.onNodeWithText(body).assertTextEquals(body).assertIsNotEnabled()
                assertTrue(peer.transactions.isEmpty())
                ui.onNodeWithText("Retry").performClick()
                waitFor("Available on this node.")
                ui.waitUntil(10_000) { ui.onAllNodes(hasText("Back") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                assertTrue(peer.transactions.isEmpty())
                peer.assertHealthy()
                captureFeedEvidence("post-process-evidence/reconciled-without-rebroadcast.png")
                ui.onNodeWithText("Back").performClick()
                ui.waitUntil(10_000) { ui.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty() }
                openComposer()
                ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNode(hasSetTextAction()).assert(hasText(""))
            }
        }
    }
}
