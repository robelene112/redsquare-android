package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PostPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun savedDraftSurvivesReopeningAndRemainsUnsent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".dev"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        val root = instrumentation.context.assets.open("native-following-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("root")
        }
        val peer = ControlledFeedPeer("[$root]")
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { scenario ->
                try {
                    waitFor("Native Red Square reference post")
                    ui.onNodeWithText("New post").performClick()
                    ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasSetTextAction()).performTextReplacement("Draft kept until Send")
                    ui.onNodeWithText("Save draft").performClick()
                    waitFor("Native Red Square reference post")
                    scenario.recreate()
                    waitFor("Native Red Square reference post")
                    ui.onNodeWithText("New post").performClick()
                    ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.waitUntil(10_000) {
                        ui.onAllNodes(hasSetTextAction() and hasText("Draft kept until Send")).fetchSemanticsNodes().isNotEmpty()
                    }
                    ui.onNode(hasSetTextAction()).assertTextEquals("Draft kept until Send")
                    peer.assertHealthy()
                    assertTrue("Saving or reopening a draft must not publish it", peer.transactions.isEmpty())
                    captureFeedEvidence("posting-evidence/recovered-unsent-draft.png")
                } catch (failure: Throwable) {
                    captureFeedEvidence("posting-evidence/draft-first-failure.png")
                    throw failure
                }
            }
        }
    }

    private fun waitFor(text: String) = ui.waitUntil(15_000) {
        ui.onAllNodes(hasText(text)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    @Test fun explicitSendBroadcastsAZeroPaymentPostWithoutInventingAcknowledgement() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".dev"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        val root = instrumentation.context.assets.open("native-following-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("root")
        }
        val peer = ControlledFeedPeer("[$root]") { criteria ->
            if (criteria.has("sig")) "[]" else "[$root]"
        }
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                try {
                    waitFor("Native Red Square reference post")
                    ui.onNodeWithText("Wallet").performClick()
                    val author = displayedWalletAddress(ui)
                    ui.onNodeWithText("Done").performClick()
                    ui.onNodeWithText("New post").performClick()
                    ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasSetTextAction()).performTextReplacement("Explicit unfunded native post")
                    ui.onNodeWithText("Send").performClick()
                    waitFor("Submitted. Waiting for this server.")
                    ui.waitUntil(10_000) { peer.transactions.isNotEmpty() }
                    val frame = peer.transactions.single()
                    check(frame[0] == 4.toByte())
                    val transaction = frame.copyOfRange(1, frame.size)
                    val header = java.nio.ByteBuffer.wrap(transaction)
                    val inputs = header.int
                    val outputs = header.int
                    val length = header.int
                    val hops = header.int
                    check(inputs == 1 && outputs == 1 && hops == 1)
                    check(transaction.size == 93 + 59 * (inputs + outputs) + length + 130)
                    val messageOffset = 93 + 59 * (inputs + outputs)
                    val message = transaction.copyOfRange(messageOffset, messageOffset + length)
                    val detached = transaction.copyOfRange(0, messageOffset) + transaction.copyOfRange(messageOffset + length, transaction.size)
                    java.nio.ByteBuffer.wrap(detached).putInt(8, 0)
                    val encoder = java.util.Base64.getEncoder()
                    val envelope = com.google.gson.JsonObject().apply {
                        addProperty("t", encoder.encodeToString(detached))
                        addProperty("m", encoder.encodeToString(message))
                        addProperty("opt", encoder.encodeToString("{}".toByteArray()))
                    }.toString()
                    val post = nl.davidengelen.redsquare.saito.RedSquareFeed.decode(envelope)
                    org.junit.Assert.assertEquals(author, post.author)
                    org.junit.Assert.assertEquals("Explicit unfunded native post", post.text)
                    check(post.parentId.isEmpty() && post.threadId == post.id)
                    repeat(inputs + outputs) { index ->
                        check(java.nio.ByteBuffer.wrap(transaction).getLong(93 + index * 59 + 33) == 0L)
                    }
                    peer.assertHealthy()
                    captureFeedEvidence("posting-evidence/submitted-awaiting-server.png")
                } catch (failure: Throwable) {
                    captureFeedEvidence("posting-evidence/send-first-failure.png")
                    throw failure
                }
            }
        }
    }
}
