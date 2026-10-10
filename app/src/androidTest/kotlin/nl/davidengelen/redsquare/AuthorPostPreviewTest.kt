package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference

class AuthorPostPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun prepare() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        context.noBackupFilesDir.listFiles()?.filter { it.name.startsWith("feed.") ||
            it.name.startsWith("conversation.") || it.name.startsWith("profile.") ||
            it.name.startsWith("postdraft.") || it.name.startsWith("authorposts.") ||
            it.name.startsWith("quoteoriginal.") }?.forEach { check(it.delete()) }
    }

    @Test fun authorChangesRetainOriginalIdentityRecoverDeletionAndRefreshNormalViews() {
        val reference = InstrumentationRegistry.getInstrumentation().context.assets.open("native-feed-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow") }
        val own = AtomicReference<JsonObject?>()
        val originalId = AtomicReference<String?>()
        val originalAuthor = AtomicReference<String?>()
        val deletion = AtomicReference<JsonObject?>()
        val alternateArchive = AtomicReference<JsonObject?>()
        val removeOnDelivery = java.util.concurrent.atomic.AtomicBoolean(false)
        val peer = ControlledFeedPeer("[]", onTransaction = { frame ->
            val candidate = postingTransaction(frame)
            val signed = SaitoEnvelope.decode(candidate.envelope())
            when (signed.body.get("request").asString) {
                "create tweet" -> {
                    assertEquals("Native author's original post", signed.body.getAsJsonObject("data").get("text").asString)
                    originalId.set(signed.id)
                    originalAuthor.set(signed.author)
                    own.set(postingArchiveRow(candidate))
                }
                "edit tweet" -> {
                    val old = requireNotNull(own.get())
                    assertEquals(old.get("field2").asString, signed.author)
                    assertEquals(originalId.get(), signed.body.getAsJsonObject("data").get("tweet_id").asString)
                    assertEquals("Native author's edited post", signed.body.getAsJsonObject("data").get("text").asString)
                    assertEquals(listOf(signed.author), signed.recipients)
                    own.set(old.deepCopy().apply {
                        val envelope = JsonParser.parseString(get("tx").asString).asJsonObject
                        val options = JsonObject().apply {
                            addProperty("update_tx", candidate.envelope()); addProperty("edit_ts", signed.createdAt)
                        }
                        envelope.addProperty("opt", Base64.getEncoder().encodeToString(options.toString().toByteArray()))
                        addProperty("tx", envelope.toString())
                    })
                }
                "delete tweet" -> {
                    assertEquals(originalId.get(), signed.body.getAsJsonObject("data").get("tweet_id").asString)
                    assertEquals(originalAuthor.get(), signed.author)
                    deletion.set(JsonObject().apply {
                        addProperty("sig", signed.id); addProperty("created_at", signed.createdAt)
                        addProperty("field1", "RedSquare"); addProperty("field2", signed.author)
                        addProperty("tx", candidate.envelope())
                    })
                    alternateArchive.set(if (removeOnDelivery.get()) null else own.get() ?: alternateArchive.get())
                    own.set(null)
                }
                else -> error("Unexpected author action")
            }
        }, archiveResponse = { criteria ->
            if (criteria.get("field1").asString == "RedSquareRetweet") {
                listOfNotNull(alternateArchive.get()).filter { row ->
                    criteria.has("sig") && criteria.get("sig").asString == row.get("sig").asString
                }.joinToString(prefix = "[", postfix = "]")
            } else if (criteria.get("field1").asString != "RedSquare") "[]"
            else listOfNotNull(own.get(), deletion.get(), reference).filter { row ->
                (!criteria.has("sig") || criteria.get("sig").asString == row.get("sig").asString) &&
                    (!criteria.has("field2") || criteria.get("field2").asString == row.get("field2").asString) &&
                    (!criteria.has("field5") || criteria.get("field5").asString == row.get("field5")?.asString)
            }.joinToString(prefix = "[", postfix = "]")
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                try {
                    waitFor("Native Red Square reference post")
                    ui.onAllNodesWithText("Edit").assertCountEquals(0)
                    ui.onNodeWithText("New post").performClick()
                    ui.waitUntil(20_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasSetTextAction()).performTextReplacement("Native author's original post")
                    ui.onNodeWithText("Send").performClick()
                    waitFor("Available on this server.")
                    ui.onNodeWithText("Back").performClick()
                    waitForList()
                    waitForRefresh()
                    ui.onNode(hasScrollAction()).performScrollToNode(hasText("Native author's original post"))
                    ui.onNodeWithText("Native author's original post").performClick()
                    waitFor("Conversation")
                    ui.onNodeWithText("Edit").performClick()
                    waitFor("Edit post")
                    ui.waitUntil(20_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasSetTextAction()).assertTextEquals("Native author's original post")
                    ui.onNode(hasSetTextAction()).performTextReplacement("Native author's edited post")
                    ui.onNodeWithText("Save changes").performClick()
                    waitFor("Edit available on this server.")
                    ui.onNodeWithText("Back").performClick()
                    waitForList()
                    waitFor("Native author's edited post")
                    ui.onNodeWithText("Native author's original post").assertDoesNotExist()
                    assertEquals(originalId.get(), own.get()!!.get("sig").asString)
                    ui.onNodeWithText("Back").performClick()
                    waitForList()
                    waitFor("New post")
                    waitForRefresh()
                    ui.onNode(hasScrollAction()).performScrollToNode(hasText("Native author's edited post"))
                    ui.clickVisiblePostContent(own.get()!!.get("field2").asString)
                    waitFor("Profile")
                    waitFor("Native author's edited post")
                    assertEquals(2, peer.transactions.size)
                    peer.assertHealthy()
                    java.io.File(context.cacheDir, "author-post-evidence/native-author-frames.json").apply {
                        parentFile!!.mkdirs()
                        writeText(com.google.gson.JsonArray().apply {
                            peer.transactions.forEach { add(Base64.getEncoder().encodeToString(it)) }
                        }.toString())
                    }
                    captureFeedEvidence("author-post-evidence/edited-post-profile.png")
                    ui.onNodeWithText("Native author's edited post").performClick()
                    waitFor("Conversation")
                    ui.onNodeWithText("Delete").performClick()
                    waitFor("Delete post")
                    ui.onNodeWithText("Remove this post from normal views on the selected server and this device? Historical and independent copies may remain.").assertIsDisplayed()
                    ui.onNodeWithText("Confirm delete").performClick()
                    waitFor("Removal not yet verified. Retry checks the original attempt first.")
                    ui.waitUntil(25_000) {
                        ui.onAllNodes(hasText("Retry") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
                    }
                    ui.onNodeWithText("Post removed on this server.").assertDoesNotExist()
                    val deletionId = deletion.get()!!.get("sig").asString
                    ui.onNodeWithText("Back").performClick()
                    waitFor("Conversation")
                    waitFor("Native author's edited post")
                    ui.onNodeWithText("Back").performClick()
                    waitFor("Profile")
                    ui.onNodeWithText("Back").performClick()
                    waitFor("New post")
                    ui.onNodeWithText("Saved post changes").performClick()
                    waitFor("Delete post")
                    assertEquals(3, peer.transactions.size)
                    ui.onNodeWithText("Delete post").performClick()
                    ui.waitUntil(20_000) {
                        ui.onAllNodes(hasText("Retry") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
                    }
                    removeOnDelivery.set(true)
                    ui.onNodeWithText("Retry").performClick()
                    waitFor("Post removed on this server.")
                    assertEquals(deletionId, deletion.get()!!.get("sig").asString)
                    ui.onNodeWithText("Back").performClick()
                    waitFor("No saved changes on this server.")
                    ui.onNodeWithText("Back").performClick()
                    waitFor("New post")
                    waitForRefresh()
                    ui.onNodeWithText("Native author's edited post").assertDoesNotExist()
                    assertEquals(4, peer.transactions.size)
                    peer.assertHealthy()
                    java.io.File(context.cacheDir, "author-post-evidence/native-author-delete-frames.json").writeText(
                        com.google.gson.JsonArray().apply {
                            peer.transactions.take(3).forEach { add(Base64.getEncoder().encodeToString(it)) }
                        }.toString())
                    captureFeedEvidence("author-post-evidence/deleted-post-profile.png")
                } catch (failure: Throwable) {
                    captureFeedEvidence("author-post-evidence/edit-first-failure.png")
                    throw failure
                }
            }
        }
    }

    private fun waitFor(text: String) {
        ui.waitUntil(20_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
    }

    @Test fun competingEditsRequireAnExplicitTextChoiceAndANewSend() {
        val own = AtomicReference<JsonObject?>()
        val editAttempts = java.util.concurrent.atomic.AtomicInteger()
        val peer = ControlledFeedPeer("[]", onTransaction = { frame ->
            val candidate = postingTransaction(frame)
            val signed = SaitoEnvelope.decode(candidate.envelope())
            if (signed.body.get("request").asString == "create tweet") {
                own.set(postingArchiveRow(candidate))
            } else {
                val action = RedSquareAuthorActions.decode(candidate.envelope())
                assertEquals(AuthorActionKind.EDIT, action.kind)
                assertEquals(own.get()!!.get("sig").asString, action.target)
                assertEquals(own.get()!!.get("field2").asString, action.author)
                val attempt = editAttempts.incrementAndGet()
                val observed = if (attempt <= 2) {
                    assertEquals("My retained text", action.text)
                    // A second device with this synthetic wallet signs a competing, newer edit.
                    SaitoTransaction.create(AndroidWalletStore(context).loadExisting(), JsonObject().apply {
                        addProperty("module", "RedSquare"); addProperty("request", "edit tweet")
                        add("data", JsonObject().apply {
                            addProperty("tweet_id", action.target)
                            addProperty("text", "Competing text $attempt")
                        })
                    }, action.createdAt + 1, signed.recipients)
                } else {
                    assertEquals("Competing text 2", action.text)
                    candidate
                }
                own.set(own.get()!!.deepCopy().apply {
                    val envelope = JsonParser.parseString(get("tx").asString).asJsonObject
                    val options = JsonObject().apply {
                        addProperty("update_tx", observed.envelope())
                        addProperty("edit_ts", SaitoEnvelope.decode(observed.envelope()).createdAt)
                    }
                    envelope.addProperty("opt", Base64.getEncoder().encodeToString(options.toString().toByteArray()))
                    addProperty("tx", envelope.toString())
                })
            }
        }, archiveResponse = { criteria ->
            if (criteria.get("field1").asString != "RedSquare") "[]"
            else listOfNotNull(own.get()).filter { row ->
                (!criteria.has("sig") || criteria.get("sig").asString == row.get("sig").asString) &&
                    (!criteria.has("field2") || criteria.get("field2").asString == row.get("field2").asString) &&
                    (!criteria.has("field5") || criteria.get("field5").asString == row.get("field5")?.asString)
            }.joinToString(prefix = "[", postfix = "]")
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                try {
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("New post").performClick()
                    ui.waitUntil(20_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasSetTextAction()).performTextReplacement("Original before competing edits")
                    ui.onNodeWithText("Send").performClick()
                    waitFor("Available on this server.")
                    ui.onNodeWithText("Back").performClick()
                    waitForList(); waitForRefresh()
                    ui.onNode(hasScrollAction()).performScrollToNode(hasText("Original before competing edits"))
                    ui.onNodeWithText("Original before competing edits").performClick()
                    waitFor("Edit")
                    ui.onNodeWithText("Edit").performClick()
                    ui.waitUntil(20_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasSetTextAction()).performTextReplacement("My retained text")
                    ui.onNodeWithText("Save changes").performClick()
                    waitFor("Another edit is available. Your text is saved on this device.")
                    ui.onNodeWithText("Edit available on this server.").assertDoesNotExist()
                    ui.onNodeWithText("My retained text").assertTextEquals("My retained text").assertIsNotEnabled()
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("Keep my text") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Keep my text").performClick()
                    ui.waitUntil(20_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasSetTextAction()).assertTextEquals("My retained text")
                    assertEquals(2, peer.transactions.size)
                    ui.onNodeWithText("Save changes").performClick()
                    waitFor("Another edit is available. Your text is saved on this device.")
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("Use latest text") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Use latest text").performClick()
                    ui.waitUntil(20_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasSetTextAction()).assertTextEquals("Competing text 2")
                    assertEquals(3, peer.transactions.size)
                    ui.onNodeWithText("Save changes").performClick()
                    waitFor("Edit available on this server.")
                    assertEquals(4, peer.transactions.size)
                    val sent = peer.transactions.drop(1).map { postingTransaction(it).id }
                    assertEquals(3, sent.toSet().size)
                    peer.assertHealthy()
                    captureFeedEvidence("author-post-evidence/explicit-conflict-resolution.png")
                } catch (failure: Throwable) {
                    captureFeedEvidence("author-post-evidence/conflict-failure.png")
                    throw failure
                }
            }
        }
    }

    @Test fun cachedVerifiedEditSurvivesStaleServerHistoryAndColdLaunch() {
        val fixture = InstrumentationRegistry.getInstrumentation().context.assets.open("native-author-actions-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val newer = RedSquareFeed.decode(fixture.getAsJsonObject("newerEdited").get("tx").asString)
        nl.davidengelen.redsquare.feed.AndroidFeedCache(context, SaitoBackend.OWNER).save(listOf(newer))
        ControlledFeedPeer("[${fixture.getAsJsonObject("original")}]").start().use { server ->
            repeat(2) {
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                    waitFor("A newer verified edit")
                    waitForRefresh()
                    ui.onNodeWithText("A newer verified edit").assertIsDisplayed()
                    ui.onNodeWithText("Native Red Square reference post").assertDoesNotExist()
                }
            }
        }
    }

    @Test fun invalidAuthorRecordsPreservePriorEncryptedWorkAndNeverEnterPostQueue() {
        val fixture = InstrumentationRegistry.getInstrumentation().context.assets.open("native-author-actions-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        fun candidate(name: String): SaitoTransaction {
            val outer = JsonParser.parseString(fixture.getAsJsonObject(name).get("tx").asString).asJsonObject
            val message = Base64.getDecoder().decode(outer.get("m").asString)
            val bytes = Base64.getDecoder().decode(outer.get("t").asString) + message
            java.nio.ByteBuffer.wrap(bytes).putInt(8, message.size)
            return SaitoTransaction.restore(Base64.getEncoder().encodeToString(bytes))
        }
        val original = fixture.getAsJsonObject("original")
        val owner = original.get("field2").asString
        val store = nl.davidengelen.redsquare.feed.PostDraftStore.author(context, owner, SaitoBackend.OWNER,
            original.get("sig").asString, AuthorActionKind.EDIT)
        val saved = nl.davidengelen.redsquare.feed.PostDraftStore.Saved("Independently edited original text", candidate("edit"),
            authorBaseline = original.get("tx").asString)
        store.save(saved)
        for (replacement in listOf(
            saved.copy(transaction = candidate("unrelated"), text = "An unrelated signer must not replace this text"),
            saved.copy(transaction = candidate("wrongTarget"), text = "Wrong target text"),
            saved.copy(transaction = candidate("deleted"), text = ""), saved.copy(text = "Changed after signing"),
            saved.copy(queued = true), saved.copy(automatic = true), saved.copy(authorBaseline = fixture.getAsJsonObject("deleted").get("tx").asString))) {
            assertThrows(IllegalArgumentException::class.java) { store.save(replacement) }
            assertEquals(saved.text, store.load().text)
            assertEquals(saved.transaction!!.id, store.load().transaction!!.id)
            assertEquals(saved.authorBaseline, store.load().authorBaseline)
        }
        assertTrue(nl.davidengelen.redsquare.feed.PostDraftStore.pendingDelivery(context, owner, SaitoBackend.OWNER).isEmpty())
        val other = nl.davidengelen.redsquare.feed.PostDraftStore.author(context, owner, SaitoBackend.OFFICIAL,
            original.get("sig").asString, AuthorActionKind.EDIT)
        assertEquals("", other.load().text)
        assertEquals(null, other.load().transaction)
    }

    @Test fun authorProofsPropagateAcrossIndependentCachesAndSurviveStaleResaves() {
        val fixture = InstrumentationRegistry.getInstrumentation().context.assets.open("native-author-actions-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val original = RedSquareFeed.decode(fixture.getAsJsonObject("original").get("tx").asString)
        val newer = RedSquareFeed.decode(fixture.getAsJsonObject("newerEdited").get("tx").asString)
        val feed = nl.davidengelen.redsquare.feed.AndroidFeedCache(context, SaitoBackend.OWNER)
        val thread = nl.davidengelen.redsquare.feed.AndroidFeedCache(context, SaitoBackend.OWNER, original.threadId)
        val profile = nl.davidengelen.redsquare.feed.AndroidProfileCache(context, SaitoBackend.OWNER)
        feed.save(listOf(original)); thread.save(listOf(original)); profile.save(listOf(original), emptyList())
        val revisions = nl.davidengelen.redsquare.feed.AndroidPostRevisions(context, SaitoBackend.OWNER)
        revisions.observe(newer)
        assertEquals("A newer verified edit", feed.load().posts.single().text)
        assertEquals("A newer verified edit", thread.load().posts.single().text)
        assertEquals("A newer verified edit", profile.load().posts.single().text)
        revisions.confirmDeletion(original, RedSquareAuthorActions.decode(fixture.getAsJsonObject("deleted").get("tx").asString))
        feed.save(listOf(original)); thread.save(listOf(newer)); profile.save(listOf(original), emptyList())
        assertTrue(feed.load().posts.isEmpty()); assertTrue(thread.load().posts.isEmpty()); assertTrue(profile.load().posts.isEmpty())
        assertTrue(nl.davidengelen.redsquare.feed.AndroidPostRevisions(context, SaitoBackend.OWNER)
            .apply(listOf(original)).deleted)
        assertEquals(original.id, nl.davidengelen.redsquare.feed.AndroidPostRevisions(context, SaitoBackend.OFFICIAL)
            .apply(listOf(original)).posts.single().id)
        ControlledFeedPeer("[${fixture.getAsJsonObject("original")}]").start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                waitForRefresh()
                ui.onNodeWithText("Native Red Square reference post").assertDoesNotExist()
                ui.onNodeWithText("A newer verified edit").assertDoesNotExist()
            }
        }
    }

    @Test fun mountedQuoteKeepsCommentaryWhileOriginalFollowsVerifiedEditAndDeletion() {
        fun fixture(name: String) = InstrumentationRegistry.getInstrumentation().context.assets.open(name)
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val quotes = fixture("native-quote-reference.json")
        val actions = fixture("native-author-actions-reference.json")
        val original = RedSquareFeed.decode(actions.getAsJsonObject("original").get("tx").asString)
        ControlledFeedPeer("[${quotes.getAsJsonObject("legacy")}]").start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                waitFor("Historical quote commentary")
                waitFor("Native Red Square reference post")
                val revisions = nl.davidengelen.redsquare.feed.AndroidPostRevisions(context, SaitoBackend.OWNER)
                revisions.observe(RedSquareFeed.decode(actions.getAsJsonObject("newerEdited").get("tx").asString))
                waitFor("A newer verified edit")
                ui.onNodeWithText("Native Red Square reference post").assertDoesNotExist()
                revisions.confirmDeletion(original, RedSquareAuthorActions.decode(actions.getAsJsonObject("deleted").get("tx").asString))
                waitFor("Original unavailable. Commentary is still available.")
                ui.onNodeWithText("Historical quote commentary").assertIsDisplayed()
                ui.onNodeWithText("A newer verified edit").assertDoesNotExist()
                captureFeedEvidence("author-post-evidence/quote-deleted-original.png")
                kotlinx.coroutines.runBlocking {
                    val cachedOnly = nl.davidengelen.redsquare.feed.QuoteState(context, null, SaitoBackend.OWNER,
                        "ws://127.0.0.1:1/wsopen", this)
                    cachedOnly.load(original.id, original)
                    assertTrue(cachedOnly.originals[original.id]!!.deleted)
                    assertNull(cachedOnly.originals[original.id]!!.post)
                }
            }
        }
    }
    private fun waitForList() {
        ui.waitUntil(20_000) { ui.onAllNodes(hasScrollAction()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
    }
    private fun waitForRefresh() {
        ui.waitUntil(20_000) { ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
    }
}
