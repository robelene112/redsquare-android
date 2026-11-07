package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Base64

class LikeIsolationPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun prepare() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        context.noBackupFilesDir.listFiles()?.filter { it.name.startsWith("social.") ||
            it.name.startsWith("socialrate.") || it.name.startsWith("feed.") ||
            it.name.startsWith("conversation.") || it.name.startsWith("profile.") }?.forEach { check(it.delete()) }
    }

    @Test fun repeatedTapsAcrossPostsAndWalletsEachSendToTheSelectedNode() {
        val extra = InstrumentationRegistry.getInstrumentation().context.assets.open("native-feed-scroll-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonArray("initialRows")[0].asJsonObject }
        val extraText = RedSquareFeed.readTarget("[$extra]", extra.get("sig").asString).posts.single().text
        val node = PostLikePeer(additionalPost = extra)
        node.peer.start().use { server ->
            val intent = Intent(context, MainActivity::class.java).putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/")
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
            withActivity(intent, "repeat-posts-and-wallets") {
                ui.clickVisiblePostContent("Native Red Square reference post")
                waitFor("Conversation")
                ui.clickVisiblePostContent("Like")
                ui.clickVisiblePostContent("Like")
                ui.waitUntil(20_000) { node.peer.transactions.size == 2 }
                ui.onNode(likePostAction()).assertIsEnabled()
                waitFor("6 likes")
                ui.onNodeWithText("6 likes").assertIsDisplayed()
                ui.onNodeWithText("Back").performClick()
                waitFor("New post")
                ui.clickVisiblePostContent(extraText)
                waitFor("Conversation")
                ui.clickVisiblePostContent("Like")
                ui.waitUntil(20_000) { node.peer.transactions.size == 3 }
                captureFeedEvidence("like-isolation-evidence/repeated-post-actions.png")
            }
            val wallets = AndroidWalletStore(context)
            val original = wallets.loadExisting()
            val other = SaitoWallet.create()
            var primary: Throwable? = null
            try {
                wallets.replace(original, other)
                withActivity(intent, "other-wallet-like") {
                    ui.clickVisiblePostContent("Native Red Square reference post")
                    waitFor("Conversation")
                    ui.clickVisiblePostContent("Like")
                    ui.waitUntil(20_000) { node.peer.transactions.size == 4 }
                    captureFeedEvidence("like-isolation-evidence/other-wallet-new-like.png")
                }
                wallets.replace(wallets.loadExisting(), original)
                withActivity(intent, "original-wallet-like") {
                    ui.clickVisiblePostContent("Native Red Square reference post")
                    waitFor("Conversation")
                    ui.clickVisiblePostContent("Like")
                    ui.waitUntil(20_000) { node.peer.transactions.size == 5 }
                    val actions = node.peer.transactions.map(::postingTransaction).map { SaitoEnvelope.decode(it.envelope()) }
                    assertEquals(listOf(original.publicKey, original.publicKey, original.publicKey,
                        other.publicKey, original.publicKey), actions.map { it.author })
                    assertEquals(5, actions.map { it.id }.toSet().size)
                    assertEquals(listOf(rootSignature(), rootSignature(), extra.get("sig").asString,
                        rootSignature(), rootSignature()), actions.map { it.body.getAsJsonObject("data").get("signature").asString })
                    ui.openWallet()
                    assertEquals(original.publicKey, displayedWalletAddress(ui))
                    ui.onNodeWithText("Done").performClick()
                    node.peer.assertHealthy()
                    captureFeedEvidence("like-isolation-evidence/original-wallet-remains-unlimited.png")
                    retainFrames(node.peer, "repeated-wallet-posts")
                }
            } catch (failure: Throwable) { primary = failure; throw failure }
            finally {
                cleanupAfterTest(primary, {
                    val current = wallets.loadExisting()
                    check(current.publicKey in setOf(original.publicKey, other.publicKey))
                    if (current.exportJson() != original.exportJson()) wallets.replace(current, original)
                    assertTrue("Like fixture must restore its captured wallet",
                        wallets.loadExisting().exportJson() == original.exportJson())
                })
            }
        }
    }

    @Test fun aNewTapSendsAnotherDistinctLikeWithoutWaitingForTheFirstReceipt() {
        val node = PostLikePeer()
        node.peer.start().use { server ->
            val intent = Intent(context, MainActivity::class.java).putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/")
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
            withActivity(intent, "repeat-with-unavailable-confirmation") {
                waitFor("4 likes")
                ui.clickVisiblePostContent("Like")
                ui.waitUntil(20_000) { node.peer.transactions.size == 1 }
                ui.clickVisiblePostContent("Like")
                ui.waitUntil(20_000) { node.peer.transactions.size == 2 }
                val ids = node.peer.transactions.map(::postingTransaction).map { it.id }
                assertEquals(2, ids.distinct().size)
                ui.onNode(likePostAction()).assertIsEnabled()
                waitFor("6 likes")
                ui.onNodeWithText("6 likes").assertIsDisplayed()
                ui.onNodeWithText("Liked").assertDoesNotExist()
                ui.onNodeWithText("Like pending").assertDoesNotExist()
                assertEquals(2, node.peer.transactions.size)
                node.peer.assertHealthy()
                captureFeedEvidence("like-isolation-evidence/repeated-like-before-confirmation.png")
                retainFrames(node.peer, "repeated-before-confirmation")
            }
        }
    }

    private fun rootSignature() = InstrumentationRegistry.getInstrumentation().context.assets.open("native-feed-reference.json")
        .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow").get("sig").asString }

    private fun withActivity(intent: Intent, journey: String, block: () -> Unit) {
        ActivityScenario.launch<MainActivity>(intent).use {
            withFailureCapture({ captureFeedEvidence("like-isolation-evidence/$journey-failure.png") }, block)
        }
    }
    private fun retainFrames(peer: ControlledFeedPeer, journey: String) {
        peer.transactions.forEachIndexed { index, frame ->
            File(context.cacheDir, "like-isolation-evidence/$journey/native-like-$index.base64").apply {
                parentFile?.mkdirs(); writeText(Base64.getEncoder().encodeToString(frame))
            }
        }
    }
    private fun waitFor(text: String) {
        ui.waitUntil(20_000) { ui.onAllNodes(hasText(text))
            .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
    }
}
