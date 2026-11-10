package nl.davidengelen.redsquare

import android.content.Intent
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import java.io.File
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PostSubmissionPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun submittedPostReturnsToReadingAndOpensAFreshDraftWhileArchiveIsDelayed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallets = AndroidWalletStore(context)
        val wallet = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context)
        val originalBackend = backends.load()
        val retained = ChatFixtureState(context, listOf("postdraft.", "feed."), wallet.publicKey)
        val root = InstrumentationRegistry.getInstrumentation().context.assets
            .open("native-following-reference.json").reader().use { JsonParser.parseReader(it).asJsonObject.get("root") }
        val lookups = java.util.concurrent.atomic.AtomicInteger()
        val submittedId = java.util.concurrent.atomic.AtomicReference<String?>()
        val available = java.util.concurrent.atomic.AtomicReference<com.google.gson.JsonObject?>()
        val peer = ControlledFeedPeer("[$root]", archiveResponse = { query ->
            if (query.has("sig")) {
                if (query.get("sig").asString == submittedId.get()) lookups.incrementAndGet()
                available.get()?.takeIf { it.get("sig").asString == query.get("sig").asString }?.let { "[$it]" } ?: "[]"
            } else "[$root]"
        }, onTransaction = { frame -> submittedId.set(postingTransaction(frame).id) })
        var primary: Throwable? = null
        try {
            retained.prepare()
            backends.save(SaitoBackend.OWNER)
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { activity ->
                    withFailureCapture({ captureNativeScreen(File(context.cacheDir, "post-submission-evidence/delayed-failure.png")) }) {
                        ui.waitUntil(15_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
                        ui.onNode(hasText("New post") and hasClickAction()).performClick()
                        ui.waitUntil(10_000) { ui.onNode(hasSetTextAction()).isDisplayed() }
                        ui.onNode(hasSetTextAction()).performTextReplacement("Submitted once while the archive catches up")
                        ui.onNodeWithText("Send").performClick()
                        ui.waitUntil(10_000) { peer.transactions.size == 1 }
                        ui.waitUntil(15_000) { ui.onNodeWithText("Post submitted").isDisplayed() }
                        ui.onNodeWithText("Retry").assertDoesNotExist()
                        ui.onNode(hasSetTextAction()).assertDoesNotExist()
                        captureNativeScreen(File(context.cacheDir, "post-submission-evidence/submitted-reading.png"))
                        ui.waitUntil(15_000) { lookups.get() >= 2 }
                        assertEquals(1, peer.transactions.size)
                        ui.onNode(hasText("New post") and hasClickAction()).performClick()
                        ui.waitUntil(10_000) { ui.onNode(hasSetTextAction() and isEnabled()).isDisplayed() }
                        ui.onNode(hasSetTextAction()).assertEditableTextEquals("")
                        activity.recreate()
                        ui.waitUntil(15_000) { ui.onNode(hasSetTextAction() and isEnabled()).isDisplayed() }
                        ui.onNode(hasSetTextAction()).assertEditableTextEquals("")
                        ui.onNode(hasSetTextAction()).performTextReplacement("Keep this next draft")
                        val original = postingTransaction(peer.transactions.single())
                        available.set(postingArchiveRow(original))
                        ui.waitUntil(20_000) {
                            nl.davidengelen.redsquare.feed.PostDraftStore.pendingDelivery(context, wallet.publicKey, SaitoBackend.OWNER)
                                .none { it.load().transaction?.id == original.id }
                        }
                        ui.onNode(hasSetTextAction()).assertEditableTextEquals("Keep this next draft")
                        activity.recreate()
                        ui.waitUntil(15_000) { ui.onNode(hasSetTextAction() and isEnabled()).isDisplayed() }
                        ui.onNode(hasSetTextAction()).assertEditableTextEquals("Keep this next draft")
                        assertEquals(1, peer.transactions.size)
                        org.junit.Assert.assertTrue("Wallet and portable options remain unchanged",
                            wallet.exportJson() == wallets.loadExisting().exportJson())
                        peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, {
                peer.transactions.firstOrNull()?.let { frame ->
                    androidx.work.WorkManager.getInstance(context)
                        .cancelUniqueWork("post:${postingTransaction(frame).id}").result.get()
                }
            }, { retained.restoreFiles() }, { retained.restorePreferences() }, {
                backends.save(originalBackend)
                check(wallets.loadExisting().exportJson() == wallet.exportJson())
            })
        }
    }

    @Test fun timedOutSubmissionKeepsTheOriginalAndRetryReconcilesWithoutAnotherSend() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallet = AndroidWalletStore(context).loadOrCreate()
        val backends = AndroidBackendStore(context)
        val originalBackend = backends.load()
        val retained = ChatFixtureState(context, listOf("postdraft.", "feed."), wallet.publicKey)
        val indexed = java.util.concurrent.atomic.AtomicReference<com.google.gson.JsonObject?>()
        val expose = java.util.concurrent.atomic.AtomicBoolean(false)
        val peer = ControlledFeedPeer("[]", archiveResponse = { query ->
            if (expose.get() && query.has("sig")) indexed.get()?.takeIf { it.get("sig").asString == query.get("sig").asString }
                ?.let { "[$it]" } ?: "[]" else "[]"
        }, onTransaction = { indexed.set(postingArchiveRow(postingTransaction(it))) })
            .apply { withholdCloseReplies = true }
        var primary: Throwable? = null
        try {
            retained.prepare()
            backends.save(SaitoBackend.OWNER)
            peer.start().use { server ->
                try {
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                    withFailureCapture({ captureNativeScreen(File(context.cacheDir, "post-submission-evidence/timeout-failure.png")) }) {
                        ui.waitUntil(15_000) { ui.onNode(hasText("New post") and hasClickAction()).isDisplayed() }
                        ui.onNode(hasText("New post") and hasClickAction()).performClick()
                        ui.waitUntil(10_000) { ui.onNode(hasSetTextAction() and isEnabled()).isDisplayed() }
                        ui.onNode(hasSetTextAction()).performTextReplacement("Recover the original after a transport timeout")
                        ui.onNodeWithText("Send").performClick()
                        ui.waitUntil(10_000) { peer.transactions.size == 1 }
                        val candidate = postingTransaction(peer.transactions.single())
                        ui.waitUntil(20_000) { ui.onNode(hasText("Retry") and isEnabled()).isDisplayed() }
                        val saved = nl.davidengelen.redsquare.feed.PostDraftStore(context, wallet.publicKey, SaitoBackend.OWNER)
                        assertEquals(candidate.encoded(), saved.load().transaction?.encoded())
                        org.junit.Assert.assertFalse(saved.hasSubmission(candidate.id))
                        peer.withholdCloseReplies = false
                        peer.releaseWithheldCloseReplies()
                        // A withheld Archive reply deterministically reaches
                        // SaitoPeer's coroutine deadline rather than a socket
                        // close/ping race. It must leave Retry usable too.
                        peer.withholdArchiveReply = true
                        val checkingAt = android.os.SystemClock.elapsedRealtime()
                        ui.onNodeWithText("Retry").performClick()
                        ui.waitUntil(20_000) { ui.onNode(hasText("Retry") and isEnabled()).isDisplayed() }
                        org.junit.Assert.assertTrue("Observe the actual twelve-second Archive deadline",
                            android.os.SystemClock.elapsedRealtime() - checkingAt >= 11_000)
                        assertEquals(candidate.encoded(), saved.load().transaction?.encoded())
                        org.junit.Assert.assertFalse(saved.hasSubmission(candidate.id))
                        assertEquals(1, peer.transactions.size)
                        peer.withholdArchiveReply = false
                        expose.set(true)
                        ui.onNodeWithText("Retry").performClick()
                        ui.waitUntil(15_000) { ui.onNodeWithText("Post submitted").isDisplayed() }
                        org.junit.Assert.assertTrue(saved.hasSubmission(candidate.id))
                        assertEquals(1, peer.transactions.size)
                        ui.onNode(hasSetTextAction()).assertDoesNotExist()
                        peer.assertHealthy()
                    }
                }
                } finally { peer.releaseWithheldCloseReplies() }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally { cleanupAfterTest(primary, {
            peer.transactions.firstOrNull()?.let { androidx.work.WorkManager.getInstance(context)
                .cancelUniqueWork("post:${postingTransaction(it).id}").result.get() }
        }, { retained.restoreFiles() }, { retained.restorePreferences() }, { backends.save(originalBackend) }) }
    }

    @Test fun failedReceiptKeepsTheOriginalAndStaleSavingCannotReplaceTheNextDraft() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallet = AndroidWalletStore(context).loadOrCreate()
        val retained = ChatFixtureState(context, listOf("postdraft."), wallet.publicKey)
        val store = nl.davidengelen.redsquare.feed.PostDraftStore(context, wallet.publicKey, SaitoBackend.OWNER)
        val candidate = nl.davidengelen.redsquare.saito.SaitoTransaction.create(wallet,
            nl.davidengelen.redsquare.saito.RedSquarePosts.message("Keep this original"), 1_700_000_000_000L)
        val original = nl.davidengelen.redsquare.feed.PostDraftStore.Saved("Keep this original", candidate)
        val obstacle = File(context.noBackupFilesDir, "postdraft.owner.${wallet.publicKey}.submitted.${candidate.id}.v1")
        var primary: Throwable? = null
        try {
            retained.prepare()
            store.save(original)
            check(obstacle.mkdir())
            File(obstacle, "owned-test-obstacle").writeText("Prevent receipt replacement")
            var failed = false
            try { store.markSubmitted(candidate.id) } catch (_: Exception) { failed = true }
            org.junit.Assert.assertTrue("A failed receipt must not release the signed original", failed)
            assertEquals(candidate.encoded(), store.load().transaction?.encoded())
            assertEquals("Keep this original", store.load().text)
            org.junit.Assert.assertTrue(store.confirmAvailable(candidate.id))
            failed = false
            try { store.markSubmitted(candidate.id) } catch (_: Exception) { failed = true }
            org.junit.Assert.assertTrue(failed)
            store.saveComposer(original)
            val reopened = nl.davidengelen.redsquare.feed.PostDraftStore(context, wallet.publicKey, SaitoBackend.OWNER)
            assertEquals(candidate.encoded(), reopened.load().transaction?.encoded())
            org.junit.Assert.assertTrue(reopened.load().available)
            check(File(obstacle, "owned-test-obstacle").delete() && obstacle.delete())
            val receipt = store.markSubmitted(candidate.id)
            assertEquals(candidate.encoded(), receipt.load().transaction?.encoded())
            assertEquals("", store.load().text)
            store.saveComposer(nl.davidengelen.redsquare.feed.PostDraftStore.Saved("A new unsent draft"))
            store.saveComposer(original)
            org.junit.Assert.assertTrue(receipt.confirmAvailable(candidate.id))
            assertEquals("A new unsent draft", store.load().text)
            assertEquals(null, store.load().transaction)
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally { cleanupAfterTest(primary, {
            if (obstacle.isDirectory) {
                check(File(obstacle, "owned-test-obstacle").delete())
                check(obstacle.delete())
            }
        }, { retained.restoreFiles() }, { retained.restorePreferences() }, {
            check(AndroidWalletStore(context).loadExisting().exportJson() == wallet.exportJson())
        }) }
    }
}
