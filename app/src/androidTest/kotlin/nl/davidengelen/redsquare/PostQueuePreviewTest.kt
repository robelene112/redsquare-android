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

    @Test fun queuedPostResumesOnlyWhenItsOriginalBackendIsSelected() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val port = java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val indexed = java.util.concurrent.atomic.AtomicReference<com.google.gson.JsonObject?>()
        val owner = ControlledFeedPeer("[]", onTransaction = { frame ->
            val transaction = postingTransaction(frame)
            val post = nl.davidengelen.redsquare.saito.RedSquareFeed.decode(transaction.envelope())
            org.junit.Assert.assertEquals("Send on the original server", post.text)
            indexed.set(postingArchiveRow(transaction))
        }, archiveResponse = { criteria ->
            val row = indexed.get()
            if (criteria.has("sig") && criteria.get("sig").asString != row?.get("sig")?.asString) "[]"
            else row?.let { "[$it]" } ?: "[]"
        })
        val official = ControlledFeedPeer("[]")
        official.start().use { officialServer ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:$port/wsopen")
                .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${officialServer.port}/wsopen")).use {
                ui.waitUntil(20_000) { ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText("Wallet").performClick()
                val address = displayedWalletAddress(ui)
                ui.onNodeWithText("Done").performClick()
                ui.onNodeWithText("New post").performClick()
                ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNode(hasSetTextAction()).performTextReplacement("Send on the original server")
                ui.onNodeWithText("Send").performClick()
                ui.waitUntil(20_000) { ui.onAllNodes(hasText("Cancel queued post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText("Back").performClick()
                ui.onNodeWithText("Settings").performClick()
                ui.onNodeWithText("saito.io").performClick()
                ui.waitUntil(10_000) { official.requests.get() > 0 }
                owner.start(port).use {
                    // Keep the original peer reachable while another backend is selected.
                    android.os.SystemClock.sleep(7_000)
                    assertTrue(owner.transactions.isEmpty() && official.transactions.isEmpty())
                    ui.onNodeWithText("Settings").performClick()
                    ui.onNodeWithText("saito.davidengelen.nl").performClick()
                    ui.waitUntil(30_000) { owner.transactions.isNotEmpty() }
                    val post = nl.davidengelen.redsquare.saito.RedSquareFeed.decode(postingTransaction(owner.transactions.single()).envelope())
                    org.junit.Assert.assertEquals(address, post.author)
                    assertTrue(official.transactions.isEmpty())
                    ui.onNodeWithText("Refresh").performClick()
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("Send on the original server")).fetchSemanticsNodes().isNotEmpty() }
                    owner.assertHealthy(); official.assertHealthy()
                    captureFeedEvidence("post-queue-evidence/original-backend-resumed-post.png")
                }
            }
        }
    }

    @Test fun queuedPostResumesAfterReconnectionWhileTheAppIsBackgrounded() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val port = java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val indexed = java.util.concurrent.atomic.AtomicReference<com.google.gson.JsonObject?>()
        val peer = ControlledFeedPeer("[]", onTransaction = { frame ->
            val transaction = postingTransaction(frame)
            val post = nl.davidengelen.redsquare.saito.RedSquareFeed.decode(transaction.envelope())
            org.junit.Assert.assertEquals("Resume while I am away", post.text)
            indexed.set(postingArchiveRow(transaction))
        }, archiveResponse = { criteria ->
            val row = indexed.get()
            if (criteria.has("sig") && criteria.get("sig").asString != row?.get("sig")?.asString) "[]"
            else row?.let { "[$it]" } ?: "[]"
        })
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .putExtra("feedPeerUrl", "ws://127.0.0.1:$port/wsopen")).use { scenario ->
            ui.waitUntil(20_000) { ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText("Wallet").performClick()
            val address = displayedWalletAddress(ui)
            ui.onNodeWithText("Done").performClick()
            ui.onNodeWithText("New post").performClick()
            ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            ui.onNode(hasSetTextAction()).performTextReplacement("Resume while I am away")
            ui.onNodeWithText("Send").performClick()
            ui.waitUntil(20_000) { ui.onAllNodes(hasText("Cancel queued post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            peer.start(port).use {
                ui.waitUntil(60_000) { peer.transactions.isNotEmpty() }
                val post = nl.davidengelen.redsquare.saito.RedSquareFeed.decode(postingTransaction(peer.transactions.single()).envelope())
                org.junit.Assert.assertEquals(address, post.author)
                scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                ui.waitUntil(20_000) { ui.onAllNodes(hasText("Available on this server.")).fetchSemanticsNodes().isNotEmpty() }
                peer.assertHealthy()
                captureFeedEvidence("post-queue-evidence/background-resumption-confirmed.png")
            }
        }
    }

    @Test fun failedQueueSavingRetainsWorkWithoutClaimingQueuedOrTransmitting() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val indexed = java.util.concurrent.atomic.AtomicReference<com.google.gson.JsonObject?>()
        val peer = ControlledFeedPeer("[]", onTransaction = { frame -> indexed.set(postingArchiveRow(postingTransaction(frame))) },
            archiveResponse = { criteria ->
                val row = indexed.get()
                if (criteria.has("sig") && criteria.get("sig").asString != row?.get("sig")?.asString) "[]"
                else row?.let { "[$it]" } ?: "[]"
            })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                ui.waitUntil(20_000) { ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText("Wallet").performClick()
                val address = displayedWalletAddress(ui)
                ui.onNodeWithText("Done").performClick()
                ui.onNodeWithText("New post").performClick()
                ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNode(hasSetTextAction()).performTextReplacement("Retain work until it can be saved")
                val obstacle = java.io.File(context.noBackupFilesDir, "postdraft.owner.$address.v1")
                check(obstacle.mkdir())
                val child = java.io.File(obstacle, "controlled-obstacle").apply { writeText("Synthetic write obstruction") }
                try {
                    ui.onNodeWithText("Send").performClick()
                    ui.waitUntil(10_000) { ui.onAllNodes(hasText("Could not load or confirm the saved draft. Your current text remains here. Try again.")).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Retain work until it can be saved").assertExists()
                    ui.onAllNodes(hasText("Queued")).assertCountEquals(0)
                    assertTrue("Failed persistence must prevent transmission", peer.transactions.isEmpty())
                } finally { check(child.delete()); check(obstacle.delete()) }
                ui.onNodeWithText("Retry").performClick()
                ui.waitUntil(20_000) { ui.onAllNodes(hasText("Available on this server.")).fetchSemanticsNodes().isNotEmpty() }
                val post = nl.davidengelen.redsquare.saito.RedSquareFeed.decode(postingTransaction(peer.transactions.single()).envelope())
                org.junit.Assert.assertEquals(address, post.author)
                org.junit.Assert.assertEquals("Retain work until it can be saved", post.text)
                peer.assertHealthy()
                captureFeedEvidence("post-queue-evidence/failed-save-repaired-without-losing-work.png")
            }
        }
    }
}
