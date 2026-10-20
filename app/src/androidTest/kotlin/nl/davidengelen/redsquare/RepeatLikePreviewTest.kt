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
import java.io.File
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/** Deliberate repeat input, observed through native controls and compatible peer frames. */
class RepeatLikePreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun prepare() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        context.noBackupFilesDir.listFiles()?.filter { it.name.startsWith("social.") ||
            it.name.startsWith("feed.") || it.name.startsWith("conversation.") ||
            it.name.startsWith("profile.") }?.forEach { check(it.delete()) }
    }

    @Test fun laterDeliberateLikeSignsADistinctActionAndShowsOnlyServedCounts() {
        val root = InstrumentationRegistry.getInstrumentation().context.assets.open("native-feed-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow") }
        val indexed = ConcurrentHashMap<String, JsonObject>()
        fun countedRoot(): JsonObject = root.deepCopy().apply {
            val envelope = JsonParser.parseString(get("tx").asString).asJsonObject
            val count = 4 + indexed.size
            envelope.addProperty("opt", Base64.getEncoder().encodeToString(
                "{\"num_likes\":$count,\"num_retweets\":2}".toByteArray()))
            addProperty("tx", envelope.toString())
        }
        val peer = ControlledFeedPeer("[]", onTransaction = { frame ->
            val tx = postingTransaction(frame)
            val action = SaitoEnvelope.decode(tx.envelope())
            assertEquals("like tweet", action.body.get("request").asString)
            assertEquals(setOf("signature"), action.body.getAsJsonObject("data").keySet())
            assertEquals(root.get("sig").asString, action.body.getAsJsonObject("data").get("signature").asString)
            indexed.putIfAbsent(action.id, JsonObject().apply {
                addProperty("sig", action.id); addProperty("field1", "RedSquareLike")
                addProperty("field2", action.author); addProperty("field4", root.get("sig").asString)
                addProperty("created_at", action.createdAt); addProperty("tx", tx.envelope())
            })
        }, archiveResponse = { criteria ->
            if (criteria.get("field1").asString == "RedSquareLike")
                indexed[criteria.get("sig").asString]?.let { "[$it]" } ?: "[]"
            else "[${countedRoot()}]"
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                withFailureCapture({ captureFeedEvidence("repeat-like-evidence/repeat-failure.png") }) {
                    waitFor("4 likes")
                    ui.clickVisiblePostContent("Like")
                    waitFor("5 likes")
                    ui.onNodeWithText("Liked").assertIsEnabled()
                    captureFeedEvidence("repeat-like-evidence/settled-like-ready-again.png")
                    ui.clickVisiblePostContent("Liked")
                    waitFor("6 likes")
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("Liked") and isEnabled())
                        .fetchSemanticsNodes().isNotEmpty() }
                    assertEquals(2, peer.transactions.size)
                    assertEquals(2, indexed.size)
                    val originals = peer.transactions.map(::postingTransaction)
                    assertNotEquals(originals[0].id, originals[1].id)
                    assertEquals(SaitoEnvelope.decode(originals[0].envelope()).author,
                        SaitoEnvelope.decode(originals[1].envelope()).author)
                    ui.onNodeWithText("Unlike").assertDoesNotExist()
                    peer.assertHealthy()
                    captureFeedEvidence("repeat-like-evidence/two-distinct-served-likes.png")
                    peer.transactions.forEachIndexed { index, frame ->
                        File(context.cacheDir, "repeat-like-evidence/native-like-$index.base64")
                            .writeText(Base64.getEncoder().encodeToString(frame))
                    }
                }
            }
        }
    }

    private fun waitFor(text: String) {
        ui.waitUntil(20_000) { ui.onAllNodes(hasText(text))
            .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
    }
}
