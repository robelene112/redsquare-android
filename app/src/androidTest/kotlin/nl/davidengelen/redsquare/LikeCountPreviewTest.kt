package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/** Actual numeric paint and independently received signed Likes, including absent node totals. */
class LikeCountPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun missingFeedTotalAdvancesBeforeAuthenticationAndSurvivesLateAndStaleTotals() {
        withLikeCounts(reply = false, initialCount = null) { node ->
            ui.waitUntil(20_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
            ui.clickVisiblePostBody("Native Red Square reference post")
            ui.waitUntil(15_000) { ui.onNodeWithText("Conversation").isDisplayed() }
            ui.onNodeWithText("Back").performClick()
            ui.onNode(hasVerticalScrollAction()).performScrollToNode(node.action)
            ui.onNode(node.action).assertIsDisplayed().assertIsEnabled()
            ui.onNode(hasText("0 likes") and hasAnyAncestor(node.card), useUnmergedTree = true).assertDoesNotExist()
            holdAuthentication(node) {
                for (count in 1..3) {
                    ui.onNode(node.action).performClick()
                    assertPaintedCount(node, count.toLong(), "held-$count", 2_000)
                }
                assertEquals("Local feedback does not claim peer receipt", 0, node.peer.transactions.size)
                node.total.set(7L)
            }
            assertReceivedLikes(node, 3)
            assertPaintedCount(node, 7, "hydrated", 20_000)
            // A distinct fresh repost total proves this stale Like response was applied.
            node.total.set(4L); node.reposts.set(9L)
            ui.waitForRefreshableContent(15_000)
            ui.refreshVisibleView()
            ui.waitUntil(20_000) {
                ui.onNode(hasText("9 reposts") and hasAnyAncestor(node.card), useUnmergedTree = true).isDisplayed()
            }
            assertPaintedCount(node, 7, "stale-total", 2_000)
            ui.onNode(node.action).performClick()
            assertPaintedCount(node, 8, "next-tap", 2_000)
            assertReceivedLikes(node, 4)
        }
    }

    private fun holdAuthentication(node: LikeCountPeer, block: () -> Unit) {
        val peer = node.peer
        val previous = peer.authenticatedConnectionIds
        assertTrue("The loaded post came from an authenticated owned peer", previous.isNotEmpty())
        peer.withholdHandshakeReplies = true
        try {
            peer.goOffline()
            ui.waitUntil(5_000) { previous.intersect(peer.authenticatedConnectionIds).isEmpty() }
            val held = peer.heldHandshakeEntries.get()
            peer.rejectNewConnections = false
            block()
            ui.waitUntil(2_000) { peer.heldHandshakeEntries.get() > held }
            assertEquals(0, peer.transactions.size)
        } finally {
            peer.rejectNewConnections = false
            peer.releaseWithheldHandshakeReplies()
        }
    }

    private fun assertPaintedCount(node: LikeCountPeer, count: Long, stage: String, budget: Long) {
        val label = "$count ${if (count == 1L) "like" else "likes"}"
        val selector = hasText(label) and hasAnyAncestor(node.card)
        ui.waitUntil(budget) { ui.onNode(selector, useUnmergedTree = true).isDisplayed() }
        val layouts = mutableListOf<TextLayoutResult>()
        ui.onNode(selector, useUnmergedTree = true).assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { check(it(layouts)) }
        assertEquals(1, layouts.size)
        val layout = layouts.single()
        assertEquals("The visible numeric glyphs must advance, not only the heart or semantics", count.toString(), layout.layoutInput.text.text)
        assertFalse(layout.didOverflowWidth); assertFalse(layout.didOverflowHeight)
        val folder = File(context.cacheDir, "like-count-evidence").apply { mkdirs() }
        File(folder, "${node.journey}-$stage-layout.json").writeText(JsonObject().apply {
            addProperty("text", layout.layoutInput.text.text); addProperty("target", node.targetId)
            addProperty("received", node.peer.transactions.size)
            addProperty("heldHandshakes", node.peer.heldHandshakeEntries.get())
            addProperty("size", layout.size.toString())
        }.toString())
        captureFeedEvidence("like-count-evidence/${node.journey}-$stage.png")
    }

    private fun assertReceivedLikes(node: LikeCountPeer, count: Int) {
        ui.waitUntil(20_000) { node.peer.transactions.size == count }
        val signed = node.peer.transactions.map(::postingTransaction).map { SaitoEnvelope.decode(it.envelope()) }
        assertEquals(count, signed.map { it.id }.toSet().size)
        assertEquals(setOf(AndroidWalletStore(context).loadExisting().publicKey), signed.map { it.author }.toSet())
        assertTrue(signed.all { it.body.get("request").asString == "like tweet" &&
            it.body.getAsJsonObject("data").get("signature").asString == node.targetId })
        node.peer.transactions.forEachIndexed { index, bytes ->
            File(context.cacheDir, "like-count-evidence/${node.journey}-signed-$index.base64")
                .writeText(Base64.getEncoder().encodeToString(bytes))
        }
        node.peer.assertHealthy()
    }

    private fun withLikeCounts(reply: Boolean, initialCount: Long?, block: (LikeCountPeer) -> Unit) {
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallets = AndroidWalletStore(context)
        val wallet = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context)
        val previous = backends.load()
        val retained = ChatFixtureState(context, listOf("social.", "socialrate.", "feed.", "conversation.",
            "profile.", "profilemeta.", "authorposts.", "following.", "namemap."), wallet.publicKey)
        var primary: Throwable? = null
        try {
            retained.prepare(); backends.save(SaitoBackend.OWNER)
            val node = LikeCountPeer(reply, initialCount)
            node.peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                    withFailureCapture({ captureFeedEvidence("like-count-evidence/${node.journey}-failure.png") }) { block(node) }
                    assertTrue(node.peer.reports.isEmpty()); node.peer.assertHealthy()
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally { cleanupAfterTest(primary, { retained.restoreFiles() }, { retained.restorePreferences() },
            { backends.save(previous); check(backends.load() == previous) },
            { assertTrue("Wallet state is preserved", wallet.exportJson() == wallets.loadExisting().exportJson()) }) }
    }
}

/** Unsigned node totals vary independently of signed content and exact received Like frames. */
private class LikeCountPeer(reply: Boolean, initialCount: Long?) {
    private val fixture = InstrumentationRegistry.getInstrumentation().context.assets
        .open("native-thread-reference.json").reader().use { JsonParser.parseReader(it).asJsonObject }
    private val root = fixture.getAsJsonObject("root")
    private val target = if (reply) fixture.getAsJsonObject("firstReply") else root
    val targetId: String = target.get("sig").asString
    val journey = "${if (reply) "reply" else "feed"}-${initialCount ?: "missing"}"
    val total = AtomicReference<Long?>(initialCount)
    val reposts = AtomicReference(2L)
    val card = hasTestTag("post-card-${targetId.take(12)}")
    val action = likePostAction() and hasAnyAncestor(card)
    private val indexed = ConcurrentHashMap<String, JsonObject>()
    private fun counted(row: JsonObject): JsonObject = row.deepCopy().apply {
        if (get("sig").asString == targetId) {
            val envelope = JsonParser.parseString(get("tx").asString).asJsonObject
            val options = JsonObject().apply {
                total.get()?.let { addProperty("num_likes", it) }
                addProperty("num_retweets", reposts.get())
            }
            envelope.addProperty("opt", Base64.getEncoder().encodeToString(options.toString().toByteArray()))
            addProperty("tx", envelope.toString())
        }
    }
    val peer = ControlledFeedPeer("[${counted(root)}]", onTransaction = { frame ->
        val tx = postingTransaction(frame)
        val action = SaitoEnvelope.decode(tx.envelope())
        assertEquals("like tweet", action.body.get("request").asString)
        assertEquals(setOf("signature"), action.body.getAsJsonObject("data").keySet())
        assertEquals(targetId, action.body.getAsJsonObject("data").get("signature").asString)
        indexed.putIfAbsent(action.id, JsonObject().apply {
            addProperty("sig", action.id); addProperty("field1", "RedSquareLike")
            addProperty("field2", action.author); addProperty("field4", targetId)
            addProperty("created_at", action.createdAt); addProperty("tx", tx.envelope())
        })
    }, archiveResponse = { criteria ->
        val sig = criteria.get("sig")?.asString
        when {
            criteria.get("field1").asString == "RedSquareLike" -> indexed[sig]?.let { "[$it]" } ?: "[]"
            sig == targetId -> "[${counted(target)}]"
            sig == root.get("sig").asString -> "[${counted(root)}]"
            criteria.has("field5") -> "[${counted(root)},${counted(fixture.getAsJsonObject("firstReply"))}]"
            else -> "[${counted(root)}]"
        }
    })
}
