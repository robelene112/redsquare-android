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
    private val committedReceipt get() = InstrumentationRegistry.getArguments().getString("handoff") == "true"
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

    @Test fun terminateAfterOriginalPostTransmission() {
        prepare()
        context.noBackupFilesDir.listFiles()?.filter { it.name.startsWith("postdraft.") || it.name.startsWith("feed.") }
            ?.forEach { check(it.delete()) }
        val wallet = AndroidWalletStore(context).loadOrCreate()
        val quoted = if (quote) RedSquareFeed.readArchive("[${root()}]").posts.single() else null
        val seeded = if (!committedReceipt) nl.davidengelen.redsquare.saito.SaitoTransaction.create(wallet,
            nl.davidengelen.redsquare.saito.RedSquarePosts.message(body, quote = quoted), System.currentTimeMillis(),
            quoted?.let { (listOf(it.author) + it.recipients).distinct() }.orEmpty()) else null
        val obstacle = seeded?.let {
            nl.davidengelen.redsquare.feed.PostDraftStore(context, wallet.publicKey, SaitoBackend.OWNER, quote = quoted)
                .save(nl.davidengelen.redsquare.feed.PostDraftStore.Saved(body, it))
            PostReceiptObstacle.create(context, wallet.publicKey, it.id)
        }
        val observed = AtomicReference<JsonObject?>()
        val peer = ControlledFeedPeer("[${root()}]", onTransaction = { frame ->
            val candidate = postingTransaction(frame)
            val post = RedSquareFeed.decode(candidate.envelope())
            assertEquals(body, post.text)
            if (quote) assertEquals(root().get("sig").asString, post.quoted!!.id)
            if (seeded != null) assertEquals(seeded.encoded(), candidate.encoded())
            observed.set(postingArchiveRow(candidate))
        }, archiveResponse = { criteria -> if (criteria.has("sig")) "[]" else "[${root()}]" })
        var primary: Throwable? = null
        try {
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/")
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                    withFailureCapture({
                        // Native capture precedes any storage/Compose diagnostic reads.
                        val folder = File(context.cacheDir, "post-process-evidence")
                        captureNativeScreen(File(folder, "original-window-failure.png"))
                        val native = File(folder, "original-window-failure.xml").readText()
                        val retained = nl.davidengelen.redsquare.feed.PostDraftStore(context, wallet.publicKey,
                            SaitoBackend.OWNER, quote = quoted).load()
                        val details = JsonObject().apply {
                            addProperty("candidate", seeded?.id)
                            addProperty("retained", retained.transaction?.id)
                            addProperty("hasReceipt", seeded?.let { nl.davidengelen.redsquare.feed.PostDraftStore(context,
                                wallet.publicKey, SaitoBackend.OWNER).hasSubmission(it.id) })
                            addProperty("broadcasts", peer.transactions.size)
                            addProperty("connections", peer.authenticatedConnectionIds.size)
                            addProperty("elapsedMillis", android.os.SystemClock.elapsedRealtime())
                        }
                        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
                            putString("stream", "\nPOST_WINDOW_ORIGINAL_NATIVE_XML\n$native\nPOST_WINDOW_AFTER_CAPTURE_STATE\n$details\n")
                        })
                    }) {
                    waitFor("New post")
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    openComposer()
                    if (seeded == null) {
                        ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                        ui.onNode(hasSetTextAction()).performTextReplacement(body)
                    } else {
                        ui.waitUntil(10_000) { ui.onNodeWithText(body).isDisplayed() &&
                            ui.onAllNodes(hasText(context.getString(R.string.post_send_saved)) and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                        ui.onNodeWithText(body).assertIsDisplayed()
                    }
                    val transmittedBy = android.os.SystemClock.elapsedRealtime() + 10_000
                    if (seeded == null) ui.onNodeWithText("Send").performClick()
                    else ui.onNodeWithText(context.getString(R.string.post_send_saved)).performScrollTo().assertIsDisplayed().performClick()
                    ui.waitUntil(10_000) { observed.get() != null }
                    if (committedReceipt) waitFor("Post submitted")
                    else {
                        val remaining = transmittedBy - android.os.SystemClock.elapsedRealtime()
                        check(remaining > 0)
                        ui.waitUntil(remaining) { ui.onNode(hasText("Retry") and isEnabled()).isDisplayed() &&
                            ui.onAllNodesWithText(context.getString(R.string.post_draft_error))
                                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                        ui.onNodeWithText(context.getString(R.string.post_draft_error)).performScrollTo().assertIsDisplayed()
                        ui.onNodeWithText(context.getString(R.string.post_network_error)).assertDoesNotExist()
                        check(android.os.SystemClock.elapsedRealtime() <= transmittedBy)
                    }
                    peer.assertHealthy()
                    checkpoint.outputStream().use { output ->
                        val row = requireNotNull(observed.get()).deepCopy().apply {
                            addProperty("pid", Process.myPid())
                            addProperty("receipt_committed", committedReceipt)
                            addProperty("window_driver", if (seeded == null) "normal-ui-submission" else "owned-receipt-write-obstacle")
                        }
                        val hasReceipt = nl.davidengelen.redsquare.feed.PostDraftStore(context,
                            row.get("field2").asString, SaitoBackend.OWNER).hasSubmission(row.get("sig").asString)
                        assertEquals("The deliberate crash must hit its declared persistence window", committedReceipt, hasReceipt)
                        output.write(row.toString().toByteArray()); output.fd.sync()
                    }
                    Process.killProcess(Process.myPid())
                    error("Expected process termination")
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally { cleanupAfterTest(primary, { obstacle?.close() }) }
    }

    @Test fun coldStartupReconcilesTheOriginalPostWithoutRebroadcasting() {
        prepare()
        val row = JsonParser.parseString(checkpoint.readText()).asJsonObject
        if (!committedReceipt) {
            assertEquals("owned-receipt-write-obstacle", row.get("window_driver").asString)
            PostReceiptObstacle.removeOwned(context, row.get("field2").asString, row.get("sig").asString)
        }
        val peer = ControlledFeedPeer("[${root()}]", archiveResponse = { criteria ->
            if (criteria.has("sig") && criteria.get("sig").asString == row.get("sig").asString) "[$row]"
            else if (criteria.has("sig")) "[]" else "[${root()}]"
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/")
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                waitFor("New post")
                ui.openWallet()
                assertEquals(row.get("field2").asString, displayedWalletAddress(ui))
                ui.closeWalletOverview()
                waitFor("New post")
                if (!committedReceipt) {
                    openComposer()
                    waitFor("Post submitted")
                }
                assertTrue(nl.davidengelen.redsquare.feed.PostDraftStore(context,
                    row.get("field2").asString, SaitoBackend.OWNER).hasSubmission(row.get("sig").asString))
                ui.waitForPostAvailability(row.get("field2").asString, row.get("sig").asString)
                assertTrue(peer.transactions.isEmpty())
                peer.assertHealthy()
                captureFeedEvidence("post-process-evidence/reconciled-without-rebroadcast.png")
                openComposer()
                ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNode(hasSetTextAction()).assertEditableTextEquals("")
            }
        }
    }
}
