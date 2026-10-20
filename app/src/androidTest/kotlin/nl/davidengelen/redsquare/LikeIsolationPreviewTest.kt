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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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

    @Test fun capCoversDifferentPostsButEachWalletKeepsItsOwnWindow() {
        val extra = InstrumentationRegistry.getInstrumentation().context.assets.open("native-feed-scroll-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonArray("initialRows")[0].asJsonObject }
        val extraText = RedSquareFeed.readTarget("[$extra]", extra.get("sig").asString).posts.single().text
        val node = PostLikePeer(additionalPost = extra)
        node.peer.start().use { server ->
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                .putExtra("socialLikeElapsedMillis", 10_000L)
            withActivity(intent, "wallet-window-initial") {
                ui.clickVisiblePostContent("Native Red Square reference post")
                waitFor("Conversation")
                ui.clickVisiblePostContent("Like")
                waitFor("5 likes")
                ui.clickVisiblePostContent("Liked")
                waitFor("6 likes")
                ui.onNodeWithText("Back").performClick()
                waitFor("New post")
                ui.clickVisiblePostContent(extraText)
                waitFor("Conversation")
                ui.clickVisiblePostContent("Like")
                waitFor("Two likes per second. Try again in a moment.")
                assertEquals(2, node.peer.transactions.size)
                captureFeedEvidence("like-isolation-evidence/another-post-shares-cap.png")
            }
            val wallets = AndroidWalletStore(context)
            val original = wallets.loadExisting()
            val other = SaitoWallet.create()
            var primary: Throwable? = null
            try {
                wallets.replace(original, other)
                withActivity(intent, "other-wallet-has-own-window") {
                    ui.clickVisiblePostContent("Native Red Square reference post")
                    waitFor("Conversation")
                    ui.clickVisiblePostContent("Like")
                    waitFor("7 likes")
                    assertEquals(3, node.peer.transactions.size)
                    captureFeedEvidence("like-isolation-evidence/other-wallet-new-like.png")
                }
                wallets.replace(wallets.loadExisting(), original)
                withActivity(intent, "original-window-retained") {
                    ui.clickVisiblePostContent("Native Red Square reference post")
                    waitFor("Conversation")
                    ui.clickVisiblePostContent("Liked")
                    waitFor("Two likes per second. Try again in a moment.")
                    assertEquals(3, node.peer.transactions.size)
                    val actions = node.peer.transactions.map(::postingTransaction).map { SaitoEnvelope.decode(it.envelope()) }
                    assertEquals(listOf(original.publicKey, original.publicKey, other.publicKey), actions.map { it.author })
                    assertEquals(3, actions.map { it.id }.toSet().size)
                    ui.onNodeWithText("Wallet").performClick()
                    assertEquals(original.publicKey, displayedWalletAddress(ui))
                    ui.onNodeWithText("Done").performClick()
                    node.peer.assertHealthy()
                    captureFeedEvidence("like-isolation-evidence/original-wallet-still-capped.png")
                    retainFrames(node.peer, "wallet-window")
                }
            } catch (failure: Throwable) { primary = failure; throw failure }
            finally {
                cleanupAfterTest(primary, {
                    val current = wallets.loadExisting()
                    check(current.publicKey in setOf(original.publicKey, other.publicKey))
                    if (current.exportJson() != original.exportJson()) wallets.replace(current, original)
                    assertTrue("Like rate fixture must restore its captured wallet",
                        wallets.loadExisting().exportJson() == original.exportJson())
                })
            }
        }
    }

    @Test fun olderCountFailureCannotReplaceTheNewerUncertainCandidate() {
        val firstCount = AtomicBoolean(true)
        val secondSeen = CountDownLatch(1)
        lateinit var node: PostLikePeer
        node = PostLikePeer(afterLike = { count ->
            if (count == 2) { node.peer.disconnectAfterTransaction = true; secondSeen.countDown() }
        }, countsResponse = { response ->
            if (firstCount.compareAndSet(true, false)) {
                check(secondSeen.await(5, TimeUnit.SECONDS)) { "Second deliberate like did not arrive" }
                "not an archive response"
            } else response
        })
        node.peer.start().use { server ->
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                .putExtra("socialLikeElapsedMillis", 10_000L)
            withActivity(intent, "late-count-failure") {
                waitFor("4 likes")
                ui.clickVisiblePostContent("Like")
                ui.waitUntil(20_000) { ui.onAllNodes(hasText("Liked") and isEnabled())
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                ui.clickVisiblePostContent("Liked")
                ui.waitUntil(20_000) { ui.onAllNodes(hasText("Retry like") and isEnabled())
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                assertEquals(2, node.peer.transactions.size)
                val second = postingTransaction(node.peer.transactions[1]).id
                captureFeedEvidence("like-isolation-evidence/newer-pending-after-old-count-failure.png")
                node.peer.disconnectAfterTransaction = false
                ui.clickVisiblePostContent("Retry like")
                waitFor("6 likes")
                ui.onNodeWithText("Retry like").assertDoesNotExist()
                assertEquals(2, node.peer.transactions.size)
                assertTrue("Retry must check the newer original candidate", node.likeQueries.count { it == second } >= 2)
                node.peer.assertHealthy()
                captureFeedEvidence("like-isolation-evidence/newer-original-reconciled-without-rebroadcast.png")
                retainFrames(node.peer, "late-count")
            }
        }
    }

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
