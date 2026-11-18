package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class QuotePreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val originalText = "Native Red Square reference post"

    @Before fun prepare() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        context.noBackupFilesDir.listFiles()?.filter {
            it.name.startsWith("feed.") || it.name.startsWith("conversation.") ||
                it.name.startsWith("profile.") || it.name.startsWith("postdraft.")
                || it.name.startsWith("quoteoriginal.")
        }?.forEach { check(it.delete()) }
    }

    @Test fun loadedOriginalRetainsItsGeometryWhileRecycledQuotesWaitForThePublicCache() {
        val fixture = quoteFixture()
        val current = fixture.getAsJsonObject("current").deepCopy().apply {
            addProperty("field1", "RedSquareRetweet")
            addProperty("field4", fixture.getAsJsonObject("original").get("sig").asString)
        }
        val original = fixture.getAsJsonObject("original")
        val legacy = fixture.getAsJsonObject("legacy")
        val rows = InstrumentationRegistry.getInstrumentation().context.assets
            .open("native-feed-scroll-reference.json").reader().use {
                JsonParser.parseReader(it).asJsonObject.getAsJsonArray("initialRows").deepCopy()
            }.apply { add(legacy) }
        val reads = java.util.concurrent.atomic.AtomicInteger()
        val peer = ControlledFeedPeer("[]", quoteArchiveResponse = { "[$current]" }, archiveResponse = { criteria ->
            when {
                criteria.get("sig")?.asString == original.get("sig").asString -> {
                    reads.incrementAndGet(); "[$original]"
                }
                criteria.get("field1")?.asString == "RedSquare" -> rows.toString()
                else -> "[]"
            }
        })
        peer.start().use { server ->
            for (scale in listOf(1, 0)) withNativeModuleMotionScale(scale) {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { activity ->
                val folder = java.io.File(context.cacheDir, "quote-evidence").apply { mkdirs() }
                withFailureCapture({ captureNativeScreen(java.io.File(folder, "recycled-original-failure.png")) }) {
                    waitFor("Reference post 20")
                    if (ui.onNodeWithText("Later").isDisplayed()) ui.onNodeWithText("Later").performClick()
                    val list = ui.onNodeWithTag("feed-post-list")
                    val card = hasTestTag("post-card-${current.get("sig").asString.take(12)}")
                    val quoted = hasText(originalText) and hasAnyAncestor(card) and
                        hasAnyAncestor(hasContentDescription("Quoted original"))
                    list.performScrollToNode(card)
                    ui.waitUntil(20_000) { ui.onNode(quoted, useUnmergedTree = true).isDisplayed() }
                    assertTrue("The initial original came from the independent peer", reads.get() > 0)
                    val height = ui.onNode(card).fetchSemanticsNode().boundsInRoot.height
                    observeModuleWindowMotionScale(activity, scale, folder)
                    captureNativeScreen(java.io.File(folder, "scale-$scale-recycled-original-before.png"))
                    val samples = com.google.gson.JsonArray()
                    val movie = java.io.File(folder, "scale-$scale-recycled-original.mp4")
                    val recording = recordNativeTransition(movie, recordingSeconds = 5) {
                      repeat(3) { round ->
                        list.performScrollToNode(hasText("Reference post 01"))
                        ui.onNodeWithText("Reference post 01").assertIsDisplayed()
                        ui.onNode(card).assertDoesNotExist()
                        val entered = java.util.concurrent.CountDownLatch(1)
                        val release = java.util.concurrent.CountDownLatch(1)
                        val worker = kotlin.concurrent.thread(name = "owned-quote-cache-$round", isDaemon = true) {
                            synchronized(nl.davidengelen.redsquare.feed.PublicStringStore.lock) {
                                entered.countDown()
                                check(release.await(8, java.util.concurrent.TimeUnit.SECONDS))
                            }
                        }
                        try {
                            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS))
                            list.performScrollToNode(card)
                            ui.onNode(quoted, useUnmergedTree = true).assertIsDisplayed()
                            ui.onNodeWithText("Loading original.").assertDoesNotExist()
                            assertEquals("Recycled commentary retains its complete height", height,
                                ui.onNode(card).fetchSemanticsNode().boundsInRoot.height, 1f)
                            samples.add(JsonObject().apply {
                                addProperty("round", round); addProperty("originalVisibleWhileCacheHeld", true)
                                addProperty("height", ui.onNode(card).fetchSemanticsNode().boundsInRoot.height)
                            })
                        } catch (failure: Throwable) {
                            captureNativeScreen(java.io.File(folder, "scale-$scale-recycled-original-held-cache-failure.png"))
                            throw failure
                        } finally {
                            release.countDown(); worker.join(2_000); check(!worker.isAlive)
                            java.io.File(folder, "scale-$scale-recycled-original-observations.json").writeText(samples.toString())
                        }
                    }
                    }
                    val frames = retainNativeMotionFrames(movie)
                    frames.add("recording", recording); frames.add("nativeSamples", samples)
                    frames.addProperty("initialArchiveReads", reads.get())
                    frames.addProperty("publicationCount", peer.transactions.size)
                    java.io.File(folder, "scale-$scale-recycled-original-frames.json").writeText(frames.toString())
                    assertTrue(peer.transactions.isEmpty()); peer.assertHealthy()
                    captureNativeScreen(java.io.File(folder, "scale-$scale-recycled-original-after.png"))
                }
            }
            }
        }
    }


    @Test fun currentCommentaryRetweetLoadsItsVerifiedOriginalFromTheSelectedArchive() {
        val fixture = InstrumentationRegistry.getInstrumentation().context.assets.open("native-quote-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val current = fixture.getAsJsonObject("current").deepCopy().apply {
            addProperty("field1", "RedSquareRetweet")
            addProperty("field4", fixture.getAsJsonObject("original").get("sig").asString)
        }
        val original = fixture.getAsJsonObject("original")
        val peer = ControlledFeedPeer("[]", quoteArchiveResponse = { criteria ->
            if (criteria.has("field2")) assertEquals(current.get("field2").asString, criteria.get("field2").asString)
            "[$current]"
        }, archiveResponse = { criteria ->
            when {
                criteria.get("field1").asString == "RedSquareRetweet" -> "[$current]"
                criteria.get("sig")?.asString == original.get("sig").asString -> "[$original]"
                else -> "[]"
            }
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                try {
                    waitFor("Current commentary retweet")
                    val embeddedOriginal = hasText(originalText) and hasAnyAncestor(hasContentDescription("Quoted original"))
                    ui.waitUntil(20_000) { ui.onAllNodes(embeddedOriginal, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasVerticalScrollAction(), useUnmergedTree = true).performScrollToNode(embeddedOriginal)
                    ui.onNode(embeddedOriginal, useUnmergedTree = true).assertIsDisplayed()
                    captureQuoteEvidence("quote-evidence/current-commentary-retweet.png")
                    ui.clickVisiblePostProfile("Anon-${current.get("field2").asString.take(6)}")
                    waitFor("Profile")
                    ui.revealProfileHistoryItem("Current commentary retweet", timeoutMillis = 20_000)
                    ui.onNode(hasVerticalScrollAction(), useUnmergedTree = true).performScrollToNode(embeddedOriginal)
                    ui.onNode(embeddedOriginal, useUnmergedTree = true).assertIsDisplayed()
                    assertTrue(peer.transactions.isEmpty())
                    peer.assertHealthy()
                    captureQuoteEvidence("quote-evidence/current-retweet-profile.png")
                } catch (failure: Throwable) {
                    captureQuoteEvidence("quote-evidence/current-retweet-failure.png")
                    throw failure
                }
            }
        }
    }

    @Test fun publishAQuoteWithCommentaryAndAnOriginalVisibleInFeedAndConversation() {
        val original = InstrumentationRegistry.getInstrumentation().context.assets.open("native-feed-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow") }
        val quoted = AtomicReference<JsonObject?>()
        val commentary = "Native quote commentary"
        val peer = ControlledFeedPeer("[]", onTransaction = { frame ->
            val candidate = postingTransaction(frame)
            val message = SaitoEnvelope.decode(candidate.envelope())
            assertEquals("create tweet", message.body.get("request").asString)
            val data = message.body.getAsJsonObject("data")
            assertEquals(commentary, data.get("text").asString)
            assertEquals(original.get("sig").asString, data.get("signature").asString)
            val nested = SaitoEnvelope.decode(data.get("retweet_tx").asString)
            assertEquals(original.get("sig").asString, nested.id)
            assertEquals(original.get("field2").asString, nested.author)
            assertEquals(originalText, nested.body.getAsJsonObject("data").get("text").asString)
            val embedded = data.getAsJsonObject("embedded")
            assertEquals(nested.id, embedded.get("signature").asString)
            assertEquals(nested.author, embedded.get("publicKey").asString)
            assertEquals(originalText, embedded.get("text").asString)
            assertEquals(listOf(message.author, nested.author).distinct(), message.recipients)
            assertFalse(data.has("parent_id"))
            quoted.set(JsonObject().apply {
                addProperty("sig", message.id); addProperty("field1", "RedSquare")
                addProperty("field2", message.author); addProperty("field4", "")
                addProperty("field5", message.id); addProperty("created_at", message.createdAt)
                addProperty("tx", candidate.envelope())
            })
        }, archiveResponse = { criteria ->
            if (criteria.get("field1").asString != "RedSquare") "[]"
            else listOfNotNull(quoted.get(), original).filter { row ->
                (!criteria.has("sig") || criteria.get("sig").asString == row.get("sig").asString) &&
                    (!criteria.has("field2") || criteria.get("field2").asString == row.get("field2").asString) &&
                    (!criteria.has("field5") || criteria.get("field5").asString == row.get("field5")?.asString)
            }.joinToString(prefix = "[", postfix = "]")
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                try {
                    quotePhase("original loaded: start")
                    waitFor(originalText)
                    quotePhase("original loaded: done")
                    quotePhase("open quote: start")
                    ui.clickVisiblePostContent("Quote")
                    quotePhase("open quote: done")
                    waitFor("Quote post")
                    ui.onNode(hasSetTextAction()).performTextInput(commentary)
                    ui.onNodeWithText("Send").performClick()
                    quotePhase("quote available: start")
                    waitFor("Post submitted")
                    quotePhase("quote available: done")
                    assertEquals(1, peer.transactions.size)
                    quotePhase("return to feed: start")
                    ui.onNode(hasSetTextAction()).assertDoesNotExist()
                    quotePhase("return to feed: done")
                    ui.waitUntil(20_000) { ui.refreshableContentReady() }
                    quotePhase("scroll commentary: start")
                    ui.onNode(hasVerticalScrollAction()).performScrollToNode(hasText(commentary))
                    quotePhase("scroll commentary: done")
                    ui.onNodeWithText(commentary).assertIsDisplayed()
                    val embeddedOriginal = hasText(originalText) and hasAnyAncestor(hasContentDescription("Quoted original"))
                    quotePhase("scroll embedded original: start")
                    ui.onNode(hasVerticalScrollAction(), useUnmergedTree = true).performScrollToNode(embeddedOriginal)
                    quotePhase("scroll embedded original: done")
                    ui.onNode(embeddedOriginal, useUnmergedTree = true).assertIsDisplayed()
                    quotePhase("scroll commentary: start")
                    ui.onNode(hasVerticalScrollAction()).performScrollToNode(hasText(commentary))
                    quotePhase("scroll commentary: done")
                    quotePhase("open conversation: start")
                    ui.onNodeWithText(commentary).performClick()
                    quotePhase("open conversation: done")
                    waitFor("Conversation")
                    ui.onNodeWithText("Conversation").assertIsDisplayed()
                    ui.onNode(hasVerticalScrollAction()).assertIsDisplayed()
                    quotePhase("scroll commentary: start")
                    ui.onNode(hasVerticalScrollAction()).performScrollToNode(hasText(commentary))
                    quotePhase("scroll commentary: done")
                    ui.onNodeWithText(commentary).assertIsDisplayed()
                    quotePhase("scroll conversation original: start")
                    ui.onNode(hasVerticalScrollAction()).performScrollToNode(hasText(originalText))
                    quotePhase("scroll conversation original: done")
                    ui.onNodeWithText(originalText).assertIsDisplayed()
                    assertEquals(1, peer.transactions.size)
                    peer.assertHealthy()
                    quotePhase("capture conversation: start")
                    captureQuoteEvidence("quote-evidence/published-quote-conversation.png")
                    quotePhase("capture conversation: done")
                    val proof = java.io.File(context.cacheDir, "quote-evidence/native-quote-frame.base64")
                    proof.parentFile?.mkdirs()
                    proof.writeText(java.util.Base64.getEncoder().encodeToString(peer.transactions.single()))
                } catch (failure: Throwable) {
                    captureQuoteEvidence("quote-evidence/publish-quote-failure.png")
                    throw failure
                }
            }
        }
    }

    @Test fun quoteDraftSurvivesReopeningWithoutMixingWithARootDraft() {
        val fixture = quoteFixture()
        val original = fixture.getAsJsonObject("original")
        val peer = ControlledFeedPeer("[$original]")
        peer.start().use { server ->
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                waitFor(originalText)
                ui.clickVisiblePostContent("Quote")
                readyComposer()
                ui.onNode(hasSetTextAction()).performTextReplacement("Recover this quote draft")
                scenario.recreate()
                waitFor("Recover this quote draft")
                ui.onNodeWithText("Save draft").performClick()
                waitFor("New post")
                ui.onNodeWithText("New post").performClick()
                readyComposer()
                ui.onNode(hasSetTextAction()).assert(hasText(""))
                ui.onNode(hasSetTextAction()).performTextReplacement("Separate root draft")
                ui.onNodeWithText("Save draft").performClick()
                waitFor("New post")
            }
            ActivityScenario.launch<MainActivity>(intent).use {
                waitFor(originalText)
                ui.clickVisiblePostContent("Quote")
                waitFor("Recover this quote draft")
                ui.onNode(hasSetTextAction()).assertEditableTextEquals("Recover this quote draft")
                ui.onNodeWithContentDescription("Quoted original").assertIsDisplayed()
                assertTrue(peer.transactions.isEmpty())
                peer.assertHealthy()
                captureQuoteEvidence("quote-evidence/recovered-quote-draft.png")
            }
        }
    }

    @Test fun uncertainQuoteReopeningReconcilesTheOriginalCandidateWithoutSendingTwice() {
        val original = quoteFixture().getAsJsonObject("original")
        val indexed = AtomicReference<JsonObject?>()
        val peer = ControlledFeedPeer("[]", onTransaction = { frame ->
            val transaction = postingTransaction(frame)
            val signed = SaitoEnvelope.decode(transaction.envelope())
            assertEquals(original.get("sig").asString, signed.body.getAsJsonObject("data").get("signature").asString)
            indexed.set(postingArchiveRow(transaction))
        }, archiveResponse = { criteria ->
            if (criteria.has("sig")) indexed.get()?.takeIf { it.get("sig").asString == criteria.get("sig").asString }
                ?.let { "[$it]" } ?: "[]" else "[$original]"
        })
        peer.start().use { server ->
            val endpoint = "ws://127.0.0.1:${server.port}/wsopen"
            AndroidWalletStore(context).loadOrCreate()
            val candidate = legacySubmittedPost(context, endpoint, "Uncertain quote commentary",
                quote = nl.davidengelen.redsquare.saito.RedSquareFeed.decode(original.get("tx").asString))
            assertEquals(1, peer.transactions.size)
            assertEquals(candidate.id, postingTransaction(peer.transactions.single()).id)
            val intent = Intent(context, MainActivity::class.java).putExtra("feedPeerUrl", endpoint)
            ActivityScenario.launch<MainActivity>(intent).use {
                waitFor(originalText)
                ui.clickVisiblePostContent("Quote")
                // A cold legacy attempt is reconciled by its exact original,
                // without a second publication or an alarming Retry screen.
                waitFor("Post submitted")
                ui.onNode(hasSetTextAction()).assertDoesNotExist()
                assertEquals(1, peer.transactions.size)
                peer.assertHealthy()
                captureQuoteEvidence("quote-evidence/recovered-original-quote-candidate.png")
            }
        }
    }

    @Test fun failedAndUnavailableOriginalKeepCommentaryUntilRetryRecoversAVerifiedOriginal() {
        val fixture = quoteFixture()
        val quote = fixture.getAsJsonObject("embedded")
        val original = fixture.getAsJsonObject("original")
        val availability = java.util.concurrent.atomic.AtomicInteger(0)
        val peer = ControlledFeedPeer("[]", archiveResponse = { criteria ->
            if (criteria.has("sig")) when (availability.get()) { 0 -> "invalid archive response"; 1 -> "[]"; else -> "[$original]" } else "[$quote]"
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                waitFor("Embedded quote commentary")
                waitFor("Could not load the original.")
                availability.set(1)
                ui.clickVisiblePostContent("Retry")
                waitFor("Original unavailable. Commentary is still available.")
                ui.onAllNodesWithText(originalText, useUnmergedTree = true).assertCountEquals(0)
                availability.set(2)
                ui.clickVisiblePostContent("Retry")
                val embeddedOriginal = hasText(originalText) and hasAnyAncestor(hasContentDescription("Quoted original"))
                ui.waitUntil(20_000) { ui.onAllNodes(embeddedOriginal, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
                ui.onNode(hasVerticalScrollAction(), useUnmergedTree = true).performScrollToNode(embeddedOriginal)
                ui.onNode(embeddedOriginal, useUnmergedTree = true).assertIsDisplayed()
                assertTrue(peer.transactions.isEmpty())
                peer.assertHealthy()
                captureQuoteEvidence("quote-evidence/unavailable-original-recovered.png")
            }
        }
    }

    @Test fun successfulNormalProfileRefreshUpdatesCountsEvenWhenQuoteHistoryFails() {
        val original = quoteFixture().getAsJsonObject("original")
        val count = java.util.concurrent.atomic.AtomicInteger(4)
        fun countedOriginal() = original.deepCopy().apply {
            val envelope = JsonParser.parseString(get("tx").asString).asJsonObject
            envelope.addProperty("opt", java.util.Base64.getEncoder().encodeToString("{\"num_likes\":${count.get()}}".toByteArray()))
            addProperty("tx", envelope.toString())
        }
        val peer = ControlledFeedPeer("[]", quoteArchiveResponse = { criteria ->
            if (criteria.has("field2")) "invalid archive response" else "[]"
        }, archiveResponse = { criteria -> if (criteria.has("sig")) "[]" else "[${countedOriginal()}]" })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                withFailureCapture({ captureNativeScreen(java.io.File(context.cacheDir,
                    "quote-evidence/profile-refresh-counts-failure.png")) }) {
                    waitFor("4 likes")
                    count.set(9)
                    ui.clickVisiblePostProfile("Anon-${original.get("field2").asString.take(6)}")
                    ui.revealProfileHistoryItem("Profile history could not be refreshed. Available posts remain visible.", timeoutMillis = 20_000)
                    ui.revealProfileHistoryItem("9 likes", timeoutMillis = 20_000)
                    ui.onNodeWithText("9 likes").assertIsDisplayed()
                    assertTrue(peer.transactions.isEmpty())
                    peer.assertHealthy()
                    captureNativeScreen(java.io.File(context.cacheDir,
                        "quote-evidence/profile-refresh-counts.png"))
                }
            }
        }
    }

    @Test fun verifiedOriginalCacheSurvivesReopeningAndStaysWithItsBackend() {
        val fixture = quoteFixture()
        val current = fixture.getAsJsonObject("current")
        val original = fixture.getAsJsonObject("original")
        val originalAvailable = java.util.concurrent.atomic.AtomicBoolean(true)
        val owner = ControlledFeedPeer("[]", quoteArchiveResponse = { "[$current]" }, archiveResponse = { criteria ->
            if (criteria.has("sig") && originalAvailable.get()) "[$original]" else "[]"
        })
        val official = ControlledFeedPeer("[]", quoteArchiveResponse = { "[$current]" }, archiveResponse = { "[]" })
        owner.start().use { ownerServer -> official.start().use { officialServer ->
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${ownerServer.port}/wsopen")
                .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${officialServer.port}/wsopen")
            val embeddedOriginal = hasText(originalText) and hasAnyAncestor(hasContentDescription("Quoted original"))
            ActivityScenario.launch<MainActivity>(intent).use {
                ui.waitUntil(20_000) { ui.onAllNodes(embeddedOriginal, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
                ui.onNode(hasVerticalScrollAction(), useUnmergedTree = true).performScrollToNode(embeddedOriginal)
                ui.onNode(embeddedOriginal, useUnmergedTree = true).assertIsDisplayed()
            }
            originalAvailable.set(false)
            ActivityScenario.launch<MainActivity>(intent).use {
                ui.waitUntil(20_000) { ui.onAllNodes(embeddedOriginal, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
                ui.onNode(hasVerticalScrollAction(), useUnmergedTree = true).performScrollToNode(embeddedOriginal)
                ui.onNode(embeddedOriginal, useUnmergedTree = true).assertIsDisplayed()
                ui.openSettings()
                ui.onNodeWithText("Node").performScrollTo().performClick()
                waitFor("saito.io")
                ui.onNodeWithText("saito.io").performScrollTo().performClick()
                waitFor("Node: saito.io")
                waitFor("Original unavailable. Commentary is still available.")
                ui.onAllNodes(embeddedOriginal, useUnmergedTree = true).assertCountEquals(0)
                ui.openSettings()
                ui.onNodeWithText("Node").performScrollTo().performClick()
                waitFor("saito.davidengelen.nl")
                ui.onNodeWithText("saito.davidengelen.nl").performScrollTo().performClick()
                ui.waitUntil(20_000) { ui.onAllNodes(embeddedOriginal, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
                ui.onNode(hasVerticalScrollAction(), useUnmergedTree = true).performScrollToNode(embeddedOriginal)
                ui.onNode(embeddedOriginal, useUnmergedTree = true).assertIsDisplayed()
                assertTrue(owner.transactions.isEmpty() && official.transactions.isEmpty())
                owner.assertHealthy(); official.assertHealthy()
                captureQuoteEvidence("quote-evidence/backend-isolated-original-cache.png")
            }
        } }
    }

    private fun quotePhase(label: String) {
        java.io.File(context.cacheDir, "quote-evidence/publication-progress.txt").apply {
            parentFile?.mkdirs()
            appendText("$label\n")
        }
    }

    private fun readyComposer() {
        ui.waitUntil(20_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun quoteFixture() = InstrumentationRegistry.getInstrumentation().context.assets.open("native-quote-reference.json")
        .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }

    private fun waitFor(text: String) {
        ui.waitUntil(20_000) { ui.onAllNodes(hasText(text)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
    }

    private fun captureQuoteEvidence(path: String) {
        try {
            val bitmap = ui.onRoot().captureToImage().asAndroidBitmap()
            try {
                val file = java.io.File(context.cacheDir, path)
                file.parentFile?.mkdirs()
                file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            } finally { bitmap.recycle() }
        } catch (_: Exception) { captureFeedEvidence(path) }
    }
}
