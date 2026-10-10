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
            it.name.startsWith("postdraft.") || it.name.startsWith("authorposts.") }?.forEach { check(it.delete()) }
    }

    @Test fun authorEditsPreserveOriginalIdentityAndRefreshFeedThreadAndProfile() {
        val reference = InstrumentationRegistry.getInstrumentation().context.assets.open("native-feed-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow") }
        val own = AtomicReference<JsonObject?>()
        val originalId = AtomicReference<String?>()
        val peer = ControlledFeedPeer("[]", onTransaction = { frame ->
            val candidate = postingTransaction(frame)
            val signed = SaitoEnvelope.decode(candidate.envelope())
            when (signed.body.get("request").asString) {
                "create tweet" -> {
                    assertEquals("Native author's original post", signed.body.getAsJsonObject("data").get("text").asString)
                    originalId.set(signed.id)
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
                else -> error("Unexpected author action")
            }
        }, archiveResponse = { criteria ->
            if (criteria.get("field1").asString != "RedSquare") "[]"
            else listOfNotNull(own.get(), reference).filter { row ->
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
                    ui.onNodeWithText(own.get()!!.get("field2").asString).performClick()
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
        val saved = nl.davidengelen.redsquare.feed.PostDraftStore.Saved("Independently edited original text", candidate("edit"))
        store.save(saved)
        for (replacement in listOf(
            saved.copy(transaction = candidate("unrelated"), text = "An unrelated signer must not replace this text"),
            saved.copy(transaction = candidate("wrongTarget"), text = "Wrong target text"),
            saved.copy(transaction = candidate("deleted"), text = ""), saved.copy(text = "Changed after signing"),
            saved.copy(queued = true), saved.copy(automatic = true))) {
            assertThrows(IllegalArgumentException::class.java) { store.save(replacement) }
            assertEquals(saved.text, store.load().text)
            assertEquals(saved.transaction!!.id, store.load().transaction!!.id)
        }
        assertTrue(nl.davidengelen.redsquare.feed.PostDraftStore.pendingDelivery(context, owner, SaitoBackend.OWNER).isEmpty())
        val other = nl.davidengelen.redsquare.feed.PostDraftStore.author(context, owner, SaitoBackend.OFFICIAL,
            original.get("sig").asString, AuthorActionKind.EDIT)
        assertEquals("", other.load().text)
        assertEquals(null, other.load().transaction)
    }
    private fun waitForList() {
        ui.waitUntil(20_000) { ui.onAllNodes(hasScrollAction()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
    }
    private fun waitForRefresh() {
        ui.waitUntil(20_000) { ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
    }
}
