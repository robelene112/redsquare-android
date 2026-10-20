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
import nl.davidengelen.redsquare.saito.SaitoBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** The host runs these phases in separate processes. All captured data is public. */
class SocialProcessRecoveryTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val checkpoint get() = File(context.cacheDir, "social-process-checkpoint.json")

    private fun prepare() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(Build.HARDWARE in listOf("ranchu", "goldfish"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
    }

    private fun root(): JsonObject = InstrumentationRegistry.getInstrumentation().context.assets
        .open("native-feed-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow")
        }

    private fun waitFor(text: String) = ui.waitUntil(20_000) {
        ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun terminateAfterAnUncertainLike() {
        prepare()
        context.noBackupFilesDir.listFiles()?.filter {
            it.name.startsWith("social.") || it.name.startsWith("socialrate.") || it.name.startsWith("feed.") ||
                it.name.startsWith("conversation.") || it.name.startsWith("profile.")
        }
            ?.forEach { check(it.delete()) }
        val observed = AtomicReference<JsonObject?>()
        val peer = ControlledFeedPeer("[${root()}]", onTransaction = { frame ->
            val candidate = postingTransaction(frame)
            val action = nl.davidengelen.redsquare.saito.RedSquareActions.decode(candidate.envelope())
            assertEquals(nl.davidengelen.redsquare.saito.RedSquareActionKind.LIKE, action.kind)
            assertEquals(root().get("sig").asString, action.target)
            observed.set(JsonObject().apply {
                addProperty("sig", action.id); addProperty("field1", "RedSquareLike")
                addProperty("field2", action.author); addProperty("field4", action.target)
                addProperty("created_at", nl.davidengelen.redsquare.saito.SaitoEnvelope.decode(candidate.envelope()).createdAt); addProperty("tx", candidate.envelope())
            })
        }, archiveResponse = { criteria ->
            if (criteria.get("field1").asString == "RedSquareLike") "[]" else "[${root()}]"
        }).apply { disconnectAfterTransaction = true }
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                withFailureCapture({
                    captureNativeScreen(File(context.cacheDir, "social-process-evidence/prepare-first-failure.png"))
                    File(context.cacheDir, "social-process-evidence/prepare-counts.json").writeText(JsonObject().apply {
                        addProperty("archiveRequests", peer.requests.get())
                        addProperty("broadcasts", peer.transactions.size)
                        addProperty("observedLike", observed.get() != null)
                    }.toString())
                }) {
                waitFor("Native Red Square reference post")
                ui.onNode(hasScrollAction()).performScrollToNode(hasText("Like"))
                ui.waitUntil(20_000) { ui.onAllNodes(hasText("Like") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText("Like").assertIsDisplayed().performClick()
                ui.waitUntil(20_000) { ui.onAllNodes(hasText("Retry like") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNode(hasScrollAction()).performScrollToNode(hasText("Retry like"))
                ui.onNodeWithText("Retry like").assertIsDisplayed()
                assertEquals(1, peer.transactions.size)
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
    }

    @Test fun coldStartupReconcilesTheOriginalLikeWithoutRebroadcasting() {
        prepare()
        val row = JsonParser.parseString(checkpoint.readText()).asJsonObject
        val peer = ControlledFeedPeer("[${root()}]", archiveResponse = { criteria ->
            if (criteria.get("field1").asString == "RedSquareLike") {
                if (criteria.get("sig").asString == row.get("sig").asString) "[$row]" else "[]"
            } else "[${root()}]"
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                ui.waitUntil(20_000) { ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText("Wallet").performClick()
                assertEquals(row.get("field2").asString, displayedWalletAddress(ui))
                ui.onNodeWithText("Done").performClick()
                waitFor("Native Red Square reference post")
                ui.waitUntil(20_000) { ui.onAllNodes(hasText("Retry like") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                assertTrue(peer.transactions.isEmpty())
                ui.onNode(hasScrollAction()).performScrollToNode(hasText("Retry like"))
                ui.onNodeWithText("Retry like").assertIsDisplayed().performClick()
                waitFor("Liked")
                assertTrue(peer.transactions.isEmpty())
                peer.assertHealthy()
                captureFeedEvidence("social-process-evidence/reconciled-without-rebroadcast.png")
            }
        }
    }
}
