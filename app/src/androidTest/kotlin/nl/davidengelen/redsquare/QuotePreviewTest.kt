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
        }?.forEach { check(it.delete()) }
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
                    waitFor(originalText)
                    ui.onNodeWithText("Quote").performClick()
                    waitFor("Quote post")
                    ui.onNode(hasSetTextAction()).performTextInput(commentary)
                    ui.onNodeWithText("Send").performClick()
                    waitFor("Available on this server.")
                    assertEquals(1, peer.transactions.size)
                    ui.onNodeWithText("Back").performClick()
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasScrollAction()).performScrollToNode(hasText(commentary))
                    ui.onNodeWithText(commentary).assertIsDisplayed()
                    ui.onNode(hasScrollAction()).performScrollToNode(hasText(originalText))
                    ui.onAllNodes(hasText(originalText))[0].assertIsDisplayed()
                    ui.onNode(hasScrollAction()).performScrollToNode(hasText(commentary))
                    ui.onNodeWithText(commentary).performClick()
                    waitFor("Conversation")
                    ui.onNode(hasScrollAction()).performScrollToNode(hasText(commentary))
                    ui.onNodeWithText(commentary).assertIsDisplayed()
                    ui.onNode(hasScrollAction()).performScrollToNode(hasText(originalText))
                    ui.onNodeWithText(originalText).assertIsDisplayed()
                    assertEquals(1, peer.transactions.size)
                    peer.assertHealthy()
                    captureFeedEvidence("quote-evidence/published-quote-conversation.png")
                    val proof = java.io.File(context.cacheDir, "quote-evidence/native-quote-frame.base64")
                    proof.parentFile?.mkdirs()
                    proof.writeText(java.util.Base64.getEncoder().encodeToString(peer.transactions.single()))
                } catch (failure: Throwable) {
                    captureFeedEvidence("quote-evidence/publish-quote-failure.png")
                    throw failure
                }
            }
        }
    }

    private fun waitFor(text: String) {
        ui.waitUntil(20_000) { ui.onAllNodes(hasText(text)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
    }
}
