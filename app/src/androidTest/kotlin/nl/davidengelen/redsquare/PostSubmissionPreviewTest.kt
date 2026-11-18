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

    @Test fun uncertainOriginalDoesNotBlockTheNewDraftActionWhileArchiveIsHeld() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallets = AndroidWalletStore(context)
        val wallet = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context)
        val originalBackend = backends.load()
        val retained = ChatFixtureState(context, listOf("postdraft.", "feed."), wallet.publicKey)
        val store = nl.davidengelen.redsquare.feed.PostDraftStore(context, wallet.publicKey, SaitoBackend.OWNER)
        val body = "Retain this exact older uncertain attempt"
        val original = nl.davidengelen.redsquare.saito.SaitoTransaction.create(wallet,
            nl.davidengelen.redsquare.saito.RedSquarePosts.message(body), 1_700_000_000_000L)
        val root = InstrumentationRegistry.getInstrumentation().context.assets
            .open("native-following-reference.json").reader().use { JsonParser.parseReader(it).asJsonObject.get("root") }
        val heldOriginal = java.util.concurrent.atomic.AtomicInteger()
        val exposeOriginal = java.util.concurrent.atomic.AtomicBoolean(false)
        val peer = ControlledFeedPeer("[$root]", archiveResponse = { query ->
            if (query.has("sig")) {
                if (exposeOriginal.get() && query.get("sig").asString == original.id) "[${postingArchiveRow(original)}]" else "[]"
            } else "[$root]"
        }, onArchiveResponse = { query, _, queued ->
            if (!queued && query.has("sig") && query.get("sig").asString == original.id) heldOriginal.incrementAndGet()
        })
        val folder = File(context.cacheDir, "post-submission-evidence").apply { mkdirs() }
        var primary: Throwable? = null
        try {
            retained.prepare()
            backends.save(SaitoBackend.OWNER)
            store.save(nl.davidengelen.redsquare.feed.PostDraftStore.Saved(body, original))
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { activity ->
                    withFailureCapture({ captureNativeScreen(File(folder, "fresh-held-original-failure.png")) }) {
                        ui.waitUntil(15_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
                        peer.withholdArchiveReply = true
                        ui.onNode(hasText("New post") and hasClickAction()).performClick()
                        ui.waitUntil(5_000) { peer.assertHealthy(); heldOriginal.get() > 0 }
                        val heartbeat = java.util.concurrent.CountDownLatch(1)
                        android.os.Handler(android.os.Looper.getMainLooper()).post { heartbeat.countDown() }
                        check(heartbeat.await(1, java.util.concurrent.TimeUnit.SECONDS)) { "The Android UI thread must remain responsive" }
                        File(folder, "fresh-held-original-observation.txt").writeText(
                            "heldOriginalQueries=${heldOriginal.get()}\nmainThreadHeartbeat=true\ntransmitted=${peer.transactions.size}\n")
                        // The peer's actual Archive deadline is twelve seconds. New writing
                        // must remain reachable before that independent deadline expires.
                        ui.waitUntil(2_000) {
                            ui.onAllNodes(hasText("Start another draft") and isEnabled())
                                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
                        }
                        ui.onNodeWithText("Start another draft").performScrollTo().assertIsEnabled()
                        assertEquals(original.encoded(), store.load().transaction?.encoded())
                        assertEquals(0, peer.transactions.size)
                        captureNativeScreen(File(folder, "fresh-held-original-ready.png"))
                        ui.onNodeWithText("Start another draft").performClick()
                        ui.waitUntil(2_000) { ui.onNode(hasSetTextAction() and isEnabled()).isDisplayed() }
                        ui.onNode(hasSetTextAction()).assertEditableTextEquals("")
                        val savedOriginal = nl.davidengelen.redsquare.feed.PostDraftStore
                            .pendingDelivery(context, wallet.publicKey, SaitoBackend.OWNER)
                            .single { it.load().transaction?.id == original.id }
                        assertEquals(original.encoded(), savedOriginal.load().transaction?.encoded())
                        org.junit.Assert.assertFalse(store.hasSubmission(original.id))
                        org.junit.Assert.assertFalse(savedOriginal.load().available)
                        ui.onNode(hasSetTextAction()).performTextReplacement("Keep the independent next draft")
                        ui.attachSyntheticImage()
                        ui.onNode(hasSetTextAction()).performScrollTo().performClick().assertIsFocused()
                        peer.withholdArchiveReply = false
                        exposeOriginal.set(true)
                        ui.waitForPostAvailability(wallet.publicKey, original.id)
                        ui.onNode(hasSetTextAction()).assertEditableTextEquals("Keep the independent next draft").assertIsEnabled().assertIsFocused()
                        ui.onNode(hasSetTextAction()).performTextInput(" while the old one finishes")
                        ui.assertSyntheticRedImage()
                        activity.recreate()
                        ui.waitUntil(15_000) { ui.onNode(hasSetTextAction() and isEnabled()).isDisplayed() }
                        ui.onNode(hasSetTextAction()).assertEditableTextEquals("Keep the independent next draft while the old one finishes")
                        ui.assertSyntheticRedImage()
                        ui.onNodeWithText("Save draft").performClick()
                        ui.waitUntil(10_000) { ui.onNode(hasText("New post") and hasClickAction()).isDisplayed() }
                        ui.onNode(hasText("New post") and hasClickAction()).performClick()
                        ui.waitUntil(10_000) { ui.onNode(hasSetTextAction() and isEnabled()).isDisplayed() }
                        ui.onNode(hasSetTextAction()).assertEditableTextEquals("Keep the independent next draft while the old one finishes")
                        ui.assertSyntheticRedImage()
                        assertEquals(original.encoded(), savedOriginal.load().transaction?.encoded())
                        assertEquals(0, peer.transactions.size)
                        captureNativeScreen(File(folder, "fresh-independent-text-and-image.png"))
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            peer.withholdArchiveReply = false
            cleanupAfterTest(primary, { retained.restoreFiles() }, { retained.restorePreferences() }, {
                backends.save(originalBackend)
                check(wallets.loadExisting().exportJson() == wallet.exportJson())
            })
        }
    }

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
                        ui.waitForPostAvailability(wallet.publicKey, original.id)
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
        val store = nl.davidengelen.redsquare.feed.PostDraftStore(context, wallet.publicKey, SaitoBackend.OWNER)
        val body = "Recover the original after a transport timeout"
        val original = nl.davidengelen.redsquare.saito.SaitoTransaction.create(wallet,
            nl.davidengelen.redsquare.saito.RedSquarePosts.message(body), System.currentTimeMillis())
        var obstacle: PostReceiptObstacle? = null
        val indexed = java.util.concurrent.atomic.AtomicReference<com.google.gson.JsonObject?>()
        val expose = java.util.concurrent.atomic.AtomicBoolean(false)
        val peer = ControlledFeedPeer("[]", archiveResponse = { query ->
            if (expose.get() && query.has("sig")) indexed.get()?.takeIf { it.get("sig").asString == query.get("sig").asString }
                ?.let { "[$it]" } ?: "[]" else "[]"
        }, onTransaction = { indexed.set(postingArchiveRow(postingTransaction(it))) })

        var primary: Throwable? = null
        try {
            retained.prepare()
            backends.save(SaitoBackend.OWNER)
            store.save(nl.davidengelen.redsquare.feed.PostDraftStore.Saved(body, original))
            obstacle = PostReceiptObstacle.create(context, wallet.publicKey, original.id)
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                    withFailureCapture({ captureNativeScreen(File(context.cacheDir, "post-submission-evidence/timeout-failure.png")) }) {
                        ui.waitUntil(15_000) { ui.onNode(hasText("New post") and hasClickAction()).isDisplayed() }
                        ui.onNode(hasText("New post") and hasClickAction()).performClick()
                        ui.waitUntil(10_000) { ui.onNodeWithText(body).isDisplayed() &&
                            ui.onAllNodes(hasText(context.getString(R.string.post_send_saved)) and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                        ui.onNodeWithText(body).assertIsDisplayed()
                        ui.onNodeWithText(context.getString(R.string.post_send_saved)).performScrollTo().assertIsDisplayed().performClick()
                        ui.waitUntil(10_000) { peer.transactions.size == 1 }
                        val candidate = postingTransaction(peer.transactions.single())
                        assertEquals(original.encoded(), candidate.encoded())
                        val failedBy = android.os.SystemClock.elapsedRealtime() + 20_000
                        ui.waitUntil(20_000) { ui.onNode(hasText("Retry") and isEnabled()).isDisplayed() &&
                            ui.onAllNodesWithText(context.getString(R.string.post_draft_error))
                                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                        ui.onNodeWithText(context.getString(R.string.post_draft_error)).performScrollTo().assertIsDisplayed()
                        ui.onNodeWithText(context.getString(R.string.post_network_error)).assertDoesNotExist()
                        check(android.os.SystemClock.elapsedRealtime() <= failedBy)
                        val saved = nl.davidengelen.redsquare.feed.PostDraftStore(context, wallet.publicKey, SaitoBackend.OWNER)
                        assertEquals(candidate.encoded(), saved.load().transaction?.encoded())
                        org.junit.Assert.assertFalse(saved.hasSubmission(candidate.id))
                        // Shared local drain already completed. This first failure is the real
                        // receipt-write obstacle, not a nonexistent per-feature Close ACK.
                        obstacle!!.close(); obstacle = null
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
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally { cleanupAfterTest(primary, { obstacle?.close() }, {
            peer.transactions.firstOrNull()?.let { androidx.work.WorkManager.getInstance(context)
                .cancelUniqueWork("post:${postingTransaction(it).id}").result.get() }
        }, { retained.restoreFiles() }, { retained.restorePreferences() }, { backends.save(originalBackend) }) }
    }

    @Test fun failedSeparationRetainsTheOriginalAndStaleSavingCannotReplaceTheIndependentDraft() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallets = AndroidWalletStore(context)
        val wallet = wallets.loadOrCreate()
        val retained = ChatFixtureState(context, listOf("postdraft."), wallet.publicKey)
        val store = nl.davidengelen.redsquare.feed.PostDraftStore(context, wallet.publicKey, SaitoBackend.OWNER)
        val image = InstrumentationRegistry.getInstrumentation().context.assets.open("native-images-reference.json")
            .reader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("images").get("red").asString }
        val candidate = nl.davidengelen.redsquare.saito.SaitoTransaction.create(wallet,
            nl.davidengelen.redsquare.saito.RedSquarePosts.message("Retain the original media", listOf(image)), 1_700_000_000_000L)
        val original = nl.davidengelen.redsquare.feed.PostDraftStore.Saved("Retain the original media", candidate, images = listOf(image))
        val obstacle = File(context.noBackupFilesDir, "postdraft.owner.${wallet.publicKey}.recovery.${candidate.id}.v1")
        var primary: Throwable? = null
        try {
            retained.prepare()
            store.save(original)
            check(obstacle.mkdir())
            File(obstacle, "owned-test-obstacle").writeText("Prevent separation replacement")
            var failed = false
            try { store.retainAttempt(candidate.id) } catch (_: Exception) { failed = true }
            org.junit.Assert.assertTrue("An unreadable separate record must not release the only original", failed)
            assertEquals(candidate.encoded(), store.load().transaction?.encoded())
            assertEquals(listOf(image), store.load().images)
            check(File(obstacle, "owned-test-obstacle").delete() && obstacle.delete())
            val separate = store.retainAttempt(candidate.id)
            assertEquals(candidate.encoded(), separate.load().transaction?.encoded())
            org.junit.Assert.assertFalse(store.hasSubmission(candidate.id))
            assertEquals("", store.load().text)
            store.saveComposer(nl.davidengelen.redsquare.feed.PostDraftStore.Saved("Independent unsent draft"))
            store.saveComposer(original)
            val reopened = nl.davidengelen.redsquare.feed.PostDraftStore.recovered(context, wallet.publicKey, SaitoBackend.OWNER, candidate.id)
            assertEquals(candidate.encoded(), reopened.load().transaction?.encoded())
            assertEquals(listOf(image), reopened.load().images)
            org.junit.Assert.assertTrue(reopened.confirmAvailable(candidate.id))
            assertEquals("Independent unsent draft", store.load().text)
            assertEquals(emptyList<String>(), store.load().images)
            assertEquals(null, store.load().transaction)
            assertEquals(null, nl.davidengelen.redsquare.feed.PostDraftStore.recovered(context,
                wallet.publicKey, SaitoBackend.OFFICIAL, candidate.id).load().transaction)
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally { cleanupAfterTest(primary, {
            if (obstacle.isDirectory) {
                check(File(obstacle, "owned-test-obstacle").delete())
                check(obstacle.delete())
            }
        }, { retained.restoreFiles() }, { retained.restorePreferences() }, {
            check(wallets.loadExisting().exportJson() == wallet.exportJson())
        }) }
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
