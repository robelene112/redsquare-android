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
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** The host interrupts the real process; the checkpoint contains public test data only. */
class PostQueueProcessRecoveryTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val checkpoint get() = File(context.cacheDir, "post-queue-process-checkpoint.json")
    private val body = "Resume the queued post after process death"

    private fun prepare() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(Build.HARDWARE in listOf("ranchu", "goldfish"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
    }

    @Test fun terminateAfterDurableOfflineQueueing() {
        prepare()
        context.noBackupFilesDir.listFiles()?.filter { it.name.startsWith("postdraft.") }?.forEach { check(it.delete()) }
        val port = java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { it.localPort }
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .putExtra("feedPeerUrl", "ws://127.0.0.1:$port/wsopen")).use {
            ui.waitUntil(20_000) { ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText("Wallet").performClick()
            val address = displayedWalletAddress(ui)
            ui.onNodeWithText("Done").performClick()
            val started = System.currentTimeMillis()
            ui.onNodeWithText("New post").performClick()
            ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            ui.onNode(hasSetTextAction()).performTextReplacement(body)
            ui.onNodeWithText("Send").performClick()
            ui.waitUntil(20_000) { ui.onAllNodes(hasText("Cancel queued post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            val publicState = JsonObject().apply {
                addProperty("owner", address); addProperty("text", body); addProperty("port", port)
                addProperty("started", started); addProperty("queued_at", System.currentTimeMillis()); addProperty("pid", Process.myPid())
            }
            checkpoint.outputStream().use { output -> output.write(publicState.toString().toByteArray()); output.fd.sync() }
            Process.killProcess(Process.myPid())
            error("Expected real process termination")
        }
    }

    @Test fun coldStartupDeliversThePreviouslySignedQueuedPost() {
        prepare()
        val expected = JsonParser.parseString(checkpoint.readText()).asJsonObject
        val indexed = AtomicReference<JsonObject?>()
        val peer = ControlledFeedPeer("[]", onTransaction = { frame -> indexed.set(postingArchiveRow(postingTransaction(frame))) },
            archiveResponse = { criteria ->
                val row = indexed.get()
                if (criteria.has("sig") && criteria.get("sig").asString != row?.get("sig")?.asString) "[]"
                else row?.let { "[$it]" } ?: "[]"
            })
        peer.start(expected.get("port").asInt).use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                ui.waitUntil(60_000) { peer.transactions.isNotEmpty() }
                val candidate = postingTransaction(peer.transactions.single())
                val post = RedSquareFeed.decode(candidate.envelope())
                assertEquals(expected.get("owner").asString, post.author)
                assertEquals(body, post.text)
                assertTrue("Recovery must retain the pre-interruption signed operation", post.createdAt in
                    expected.get("started").asLong..expected.get("queued_at").asLong)
                ui.waitUntil(20_000) { ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText("Wallet").performClick()
                assertEquals(expected.get("owner").asString, displayedWalletAddress(ui))
                ui.onNodeWithText("Done").performClick()
                ui.waitUntil(10_000) { ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText("Refresh").performClick()
                ui.waitUntil(20_000) {
                    try {
                        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(body))
                        ui.onNodeWithText(body).assertIsDisplayed(); true
                    } catch (_: AssertionError) { false }
                }
                assertEquals(1, peer.transactions.size)
                peer.assertHealthy()
                captureFeedEvidence("post-queue-process-evidence/cold-delivery.png")
                File(context.cacheDir, "post-queue-process-result.json").writeText(JsonObject().apply {
                    addProperty("owner", post.author); addProperty("signature", candidate.id)
                    addProperty("created_at", post.createdAt)
                }.toString())
            }
        }
    }
}
