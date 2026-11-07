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

/** The host proves immediate repeated Like dispatch and in-place behavior after process death. */
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

    @Test fun terminateAfterTwoSentLikes() {
        prepare()
        context.noBackupFilesDir.listFiles()?.filter {
            it.name.startsWith("social.") ||
                it.name.startsWith("feed.") || it.name.startsWith("conversation.") || it.name.startsWith("profile.")
        }?.forEach { check(it.delete()) }
        val node = PostLikePeer()
        node.peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(intent(server.port)).use {
                withFailureCapture({ captureFeedEvidence("repeat-social-process-evidence/prepare-failure.png") }) {
                    waitFor("4 likes")
                    ui.clickVisiblePostContent("Like")
                    ui.waitUntil(20_000) { node.peer.transactions.size == 1 }
                    ui.clickVisiblePostContent("Like")
                    ui.waitUntil(20_000) { node.peer.transactions.size == 2 }
                    ui.onNode(likePostAction()).assertIsEnabled()
                    ui.onNodeWithText("Liked").assertDoesNotExist()
                    assertEquals(2, node.peer.transactions.size)
                    val first = postingTransaction(node.peer.transactions[0])
                    val second = postingTransaction(node.peer.transactions[1])
                    assertNotEquals(first.id, second.id)
                    val action = SaitoEnvelope.decode(second.envelope())
                    assertEquals(SaitoEnvelope.decode(first.envelope()).author, action.author)
                    node.peer.assertHealthy()
                    captureFeedEvidence("repeat-social-process-evidence/second-like-sent.png")
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

    @Test fun coldStartupAllowsAnotherLikeWithoutWaitingForTheEarlierConfirmation() {
        prepare()
        val row = JsonParser.parseString(checkpoint.readText()).asJsonObject
        val node = PostLikePeer()
        val peer = node.peer
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(intent(server.port)).use {
                withFailureCapture({ captureFeedEvidence("repeat-social-process-evidence/recovery-failure.png") }) {
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("New post") and isEnabled())
                        .fetchSemanticsNodes().isNotEmpty() }
                    ui.openWallet()
                    assertEquals(row.get("field2").asString, displayedWalletAddress(ui))
                    ui.closeWalletOverview()
                    ui.waitUntil(20_000) { ui.onAllNodes(likePostAction() and isEnabled())
                        .fetchSemanticsNodes().isNotEmpty() }
                    assertTrue(peer.transactions.isEmpty())
                    ui.clickVisiblePostContent("Like")
                    ui.waitUntil(20_000) { peer.transactions.size == 1 }
                    val recovered = postingTransaction(peer.transactions.single()).id
                    assertNotEquals("A new deliberate tap creates a fresh transaction", row.get("sig").asString, recovered)
                    assertNotEquals("A new tap does not replay the earlier Like", row.get("previousSig").asString, recovered)
                    ui.onNode(likePostAction()).assertIsEnabled()
                    ui.onNodeWithText("Like pending").assertDoesNotExist()
                    ui.onNodeWithText("Retry like").assertDoesNotExist()
                    peer.assertHealthy()
                    captureFeedEvidence("repeat-social-process-evidence/new-like-after-restart.png")
                    File(evidence, "recovery-like-id.txt").writeText(recovered)
                    File(evidence, "recovery-pid.txt").writeText(Process.myPid().toString())
                }
            }
        }
    }

    private fun intent(port: Int) = Intent(context, MainActivity::class.java).putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/")
        .putExtra("feedPeerUrl", "ws://127.0.0.1:$port/wsopen")

    private fun waitFor(text: String) = ui.waitUntil(20_000) {
        ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
}
