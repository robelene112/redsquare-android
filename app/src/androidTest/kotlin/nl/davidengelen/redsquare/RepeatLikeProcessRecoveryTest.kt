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
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Base64
import java.util.concurrent.ConcurrentLinkedQueue

/** The host proves actual process death between these phases. Captures contain public data only. */
class RepeatLikeProcessRecoveryTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val checkpoint get() = File(context.cacheDir, "repeat-social-process-checkpoint.json")
    private val evidence get() = File(context.cacheDir, "repeat-social-process-evidence")

    private fun prepare() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(Build.HARDWARE in listOf("ranchu", "goldfish"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
    }

    @Test fun terminateAfterASettledThenUncertainSecondLike() {
        prepare()
        context.noBackupFilesDir.listFiles()?.filter {
            it.name.startsWith("social.") || it.name.startsWith("socialrate.") ||
                it.name.startsWith("feed.") || it.name.startsWith("conversation.") || it.name.startsWith("profile.")
        }?.forEach { check(it.delete()) }
        lateinit var node: PostLikePeer
        node = PostLikePeer(afterLike = { count -> if (count == 2) node.peer.disconnectAfterTransaction = true })
        node.peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(intent(server.port)).use {
                withFailureCapture({ captureFeedEvidence("repeat-social-process-evidence/prepare-failure.png") }) {
                    waitFor("4 likes")
                    ui.clickVisiblePostContent("Like")
                    waitFor("5 likes")
                    ui.clickVisiblePostContent("Liked")
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("Retry like") and isEnabled())
                        .fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasScrollAction()).performScrollToNode(hasText("Retry like"))
                    ui.onNodeWithText("Retry like").assertIsDisplayed()
                    assertEquals(2, node.peer.transactions.size)
                    val first = postingTransaction(node.peer.transactions[0])
                    val second = postingTransaction(node.peer.transactions[1])
                    assertNotEquals(first.id, second.id)
                    val action = SaitoEnvelope.decode(second.envelope())
                    assertEquals(SaitoEnvelope.decode(first.envelope()).author, action.author)
                    node.peer.assertHealthy()
                    captureFeedEvidence("repeat-social-process-evidence/second-like-uncertain.png")
                    evidence.mkdirs()
                    node.peer.transactions.forEachIndexed { index, frame ->
                        File(evidence, "native-like-$index.base64").writeText(Base64.getEncoder().encodeToString(frame))
                    }
                    checkpoint.outputStream().use { output ->
                        val row = JsonObject().apply {
                            addProperty("sig", action.id); addProperty("field1", "RedSquareLike")
                            addProperty("field2", action.author)
                            addProperty("field4", action.body.getAsJsonObject("data").get("signature").asString)
                            addProperty("created_at", action.createdAt); addProperty("tx", second.envelope())
                            addProperty("previousSig", first.id); addProperty("pid", Process.myPid())
                        }
                        output.write(row.toString().toByteArray())
                        output.fd.sync()
                    }
                    Process.killProcess(Process.myPid())
                    error("Expected process termination")
                }
            }
        }
    }

    @Test fun coldStartupReconcilesSecondLikeWithoutCreatingThird() {
        prepare()
        val row = JsonParser.parseString(checkpoint.readText()).asJsonObject
        val root = InstrumentationRegistry.getInstrumentation().context.assets.open("native-feed-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow") }
        val served = root.deepCopy().apply {
            val envelope = JsonParser.parseString(get("tx").asString).asJsonObject
            envelope.addProperty("opt", Base64.getEncoder().encodeToString(
                "{\"num_likes\":6,\"num_retweets\":2}".toByteArray()))
            addProperty("tx", envelope.toString())
        }
        val queries = ConcurrentLinkedQueue<String>()
        val peer = ControlledFeedPeer("[$served]", archiveResponse = { criteria ->
            if (criteria.get("field1").asString == "RedSquareLike") {
                val sig = criteria.get("sig").asString
                queries.add(sig)
                if (sig == row.get("sig").asString) "[$row]" else "[]"
            } else "[$served]"
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(intent(server.port)).use {
                withFailureCapture({ captureFeedEvidence("repeat-social-process-evidence/recovery-failure.png") }) {
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("New post") and isEnabled())
                        .fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Wallet").performClick()
                    assertEquals(row.get("field2").asString, displayedWalletAddress(ui))
                    ui.onNodeWithText("Done").performClick()
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("Retry like") and isEnabled())
                        .fetchSemanticsNodes().isNotEmpty() }
                    assertTrue(peer.transactions.isEmpty())
                    ui.clickVisiblePostContent("Retry like")
                    waitFor("6 likes")
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("Liked") and isEnabled())
                        .fetchSemanticsNodes().isNotEmpty() }
                    assertTrue("The exact second original must be checked before and after reconciliation",
                        queries.count { it == row.get("sig").asString } >= 2)
                    assertTrue(peer.transactions.isEmpty())
                    ui.clickVisiblePostContent("Liked")
                    waitFor("Two likes per second. Try again in a moment.")
                    assertTrue("Cold restart must not open a new wallet rate window", peer.transactions.isEmpty())
                    ui.onNodeWithText("6 likes").assertExists()
                    peer.assertHealthy()
                    captureFeedEvidence("repeat-social-process-evidence/reconciled-without-rebroadcast.png")
                    File(evidence, "recovery-pid.txt").writeText(Process.myPid().toString())
                }
            }
        }
    }

    private fun intent(port: Int) = Intent(context, MainActivity::class.java)
        .putExtra("feedPeerUrl", "ws://127.0.0.1:$port/wsopen")
        .putExtra("socialLikeElapsedMillis", 10_000L)

    private fun waitFor(text: String) = ui.waitUntil(20_000) {
        ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
}
