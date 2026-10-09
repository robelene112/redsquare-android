package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PostQueuePreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Before fun prepareIsolatedDrafts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".dev"))
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        context.noBackupFilesDir.listFiles()?.filter { it.name.startsWith("postdraft.") }
            ?.forEach { check(it.delete()) }
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
    }

    @Test fun explicitOfflineSendIsDurableAndCanBeCancelledBeforeTransmission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val port = java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val intent = Intent(context, MainActivity::class.java)
            .putExtra("feedPeerUrl", "ws://127.0.0.1:$port/wsopen")
        ActivityScenario.launch<MainActivity>(intent).use {
            ui.waitUntil(20_000) { ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText("New post").performClick()
            ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            ui.onNode(hasSetTextAction()).performTextReplacement("Keep my explicitly queued post")
            ui.onNodeWithText("Send").performClick()
            ui.waitUntil(20_000) { ui.onAllNodes(hasText("Queued")).fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText("Keep my explicitly queued post").assertIsNotEnabled()
        }
        ActivityScenario.launch<MainActivity>(intent).use {
            ui.waitUntil(20_000) { ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText("New post").performClick()
            ui.waitUntil(10_000) { ui.onAllNodes(hasText("Queued")).fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText("Cancel queued post").performClick()
            ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            ui.onNode(hasSetTextAction()).assertTextEquals("Keep my explicitly queued post")
            val peer = ControlledFeedPeer("[]")
            peer.start(port).use {
                ui.onNodeWithText("Save draft").performClick()
                ui.waitUntil(20_000) { peer.requests.get() > 0 }
                assertTrue("Cancelled work must remain an unsent draft", peer.transactions.isEmpty())
                peer.assertHealthy()
                captureFeedEvidence("post-queue-evidence/cancelled-queued-post-remains-draft.png")
            }
        }
    }
}
