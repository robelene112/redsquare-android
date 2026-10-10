package nl.davidengelen.redsquare

import android.content.Intent
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference

class SocialPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun prepare() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        context.noBackupFilesDir.listFiles()?.filter { it.name.startsWith("social.") }?.forEach { check(it.delete()) }
    }

    @Test fun aVerifiedLikeAndServedCountStayCoherentAcrossFeedThreadAndProfile() {
        val root = InstrumentationRegistry.getInstrumentation().context.assets.open("native-feed-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow") }
        fun countedRoot(count: Int): JsonObject = root.deepCopy().apply {
            val envelope = JsonParser.parseString(get("tx").asString).asJsonObject
            envelope.addProperty("opt", Base64.getEncoder().encodeToString("{\"num_likes\":$count,\"num_retweets\":2}".toByteArray()))
            addProperty("tx", envelope.toString())
        }
        val served = AtomicReference(countedRoot(4))
        val liked = AtomicReference<JsonObject?>()
        val peer = ControlledFeedPeer("[]", onTransaction = { frame ->
            val candidate = postingTransaction(frame)
            val action = SaitoEnvelope.decode(candidate.envelope())
            assertEquals("RedSquare", action.body.get("module").asString)
            assertEquals("like tweet", action.body.get("request").asString)
            assertEquals(root.get("sig").asString, action.body.getAsJsonObject("data").get("signature").asString)
            assertEquals(listOf(action.author, root.get("field2").asString).distinct(), action.recipients)
            liked.set(JsonObject().apply {
                addProperty("sig", action.id); addProperty("field1", "RedSquareLike")
                addProperty("field2", action.author); addProperty("field4", root.get("sig").asString)
                addProperty("created_at", action.createdAt); addProperty("tx", candidate.envelope())
            })
            served.set(countedRoot(5))
        }, archiveResponse = { criteria ->
            if (criteria.get("field1").asString == "RedSquareLike") {
                liked.get()?.takeIf { !criteria.has("sig") || criteria.get("sig").asString == it.get("sig").asString }
                    ?.let { "[$it]" } ?: "[]"
            } else "[${served.get()}]"
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                try {
                    waitFor("Native Red Square reference post")
                    waitFor("4 likes")
                    ui.onNodeWithText("Like").performClick()
                    waitFor("Liked")
                    waitFor("5 likes")
                    assertEquals(1, peer.transactions.size)
                    ui.onNodeWithText("Native Red Square reference post").performClick()
                    waitFor("Conversation")
                    ui.onNodeWithText("Liked").assertIsDisplayed()
                    ui.onNodeWithText("5 likes").assertIsDisplayed()
                    ui.onNodeWithText("Back").performClick()
                    waitFor("New post")
                    ui.onNodeWithText(root.get("field2").asString).performClick()
                    waitFor("Profile")
                    waitFor("Liked")
                    ui.onNodeWithText("5 likes").assertIsDisplayed()
                    assertEquals(1, peer.transactions.size)
                    peer.assertHealthy()
                    captureFeedEvidence("social-evidence/verified-like-across-views.png")
                } catch (failure: Throwable) {
                    captureFeedEvidence("social-evidence/like-first-failure.png")
                    throw failure
                }
            }
        }
    }

    @Test fun anOrdinaryRepostPreservesItsTargetAndDoesNotInventAServerCountIncrease() {
        val root = InstrumentationRegistry.getInstrumentation().context.assets.open("native-feed-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow") }
        val envelope = JsonParser.parseString(root.get("tx").asString).asJsonObject
        envelope.addProperty("opt", Base64.getEncoder().encodeToString("{\"num_likes\":4,\"num_retweets\":2}".toByteArray()))
        root.addProperty("tx", envelope.toString())
        val reposted = AtomicReference<JsonObject?>()
        val peer = ControlledFeedPeer("[]", onTransaction = { frame ->
            val candidate = postingTransaction(frame)
            val action = SaitoEnvelope.decode(candidate.envelope())
            assertEquals("RedSquare", action.body.get("module").asString)
            assertEquals("retweet", action.body.get("request").asString)
            val data = action.body.getAsJsonObject("data")
            assertEquals(setOf("signature"), data.keySet())
            assertEquals(root.get("sig").asString, data.get("signature").asString)
            assertEquals(listOf(action.author, root.get("field2").asString).distinct(), action.recipients)
            reposted.set(JsonObject().apply {
                addProperty("sig", action.id); addProperty("field1", "RedSquareRetweet")
                addProperty("field2", action.author); addProperty("field4", root.get("sig").asString)
                addProperty("created_at", action.createdAt); addProperty("tx", candidate.envelope())
            })
        }, archiveResponse = { criteria ->
            if (criteria.get("field1").asString == "RedSquareRetweet") {
                reposted.get()?.takeIf { !criteria.has("sig") || criteria.get("sig").asString == it.get("sig").asString }
                    ?.let { "[$it]" } ?: "[]"
            } else "[$root]"
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                try {
                    waitFor("Native Red Square reference post")
                    waitFor("2 reposts")
                    ui.onNodeWithText("Repost").performClick()
                    waitFor("Reposted")
                    ui.onNodeWithText("2 reposts").assertIsDisplayed()
                    assertEquals(1, peer.transactions.size)
                    ui.onNodeWithText("Native Red Square reference post").performClick()
                    waitFor("Conversation")
                    ui.onNodeWithText("Reposted").assertIsDisplayed()
                    ui.onNodeWithText("2 reposts").assertIsDisplayed()
                    assertEquals(1, peer.transactions.size)
                    peer.assertHealthy()
                    captureFeedEvidence("social-evidence/verified-ordinary-repost-with-served-count.png")
                } catch (failure: Throwable) {
                    captureFeedEvidence("social-evidence/repost-first-failure.png")
                    throw failure
                }
            }
        }
    }

    private fun waitFor(text: String) {
        ui.waitUntil(20_000) { ui.onAllNodes(hasText(text)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
    }
}
