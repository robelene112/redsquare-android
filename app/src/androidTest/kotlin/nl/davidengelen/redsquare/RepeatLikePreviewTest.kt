package nl.davidengelen.redsquare

import android.content.Intent
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Base64

/** Unthrottled Like taps are observed as distinct signed frames by a compatible peer. */
class RepeatLikePreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun prepare() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        context.noBackupFilesDir.listFiles()?.filter { it.name.startsWith("social.") || it.name.startsWith("socialrate.") ||
            it.name.startsWith("feed.") || it.name.startsWith("conversation.") ||
            it.name.startsWith("profile.") }?.forEach { check(it.delete()) }
    }

    @Test fun rapidLikesDispatchIndependentlyAndUpdateTheCountImmediately() {
        val likeNode = PostLikePeer()
        withLikes(likeNode) { peer, intent ->
            withLikeActivity(intent, "deliberate") {
                waitFor("4 likes")
                ui.onNodeWithText("Like", useUnmergedTree = true).assertDoesNotExist()
                ui.clickVisiblePostContent("Like")
                ui.clickVisiblePostContent("Like")
                ui.clickVisiblePostContent("Like")
                ui.waitUntil(20_000) { peer.transactions.size == 3 }
                assertEquals(3, peer.transactions.size)
                val originals = peer.transactions.map(::postingTransaction)
                assertEquals(3, originals.map { it.id }.distinct().size)
                assertEquals(1, originals.map { SaitoEnvelope.decode(it.envelope()).author }.distinct().size)
                ui.waitUntil(20_000) { likeNode.likeQueries.size >= 3 }
                assertEquals("The backend fixture observes each exact Like signature",
                    originals.map { it.id }.toSet(), likeNode.likeQueries.toSet())
                ui.onNodeWithText("Unlike").assertDoesNotExist()
                ui.onNodeWithText("Liked").assertDoesNotExist()
                ui.onNode(likePostAction()).assertIsEnabled()
                waitFor("7 likes")
                ui.onNodeWithText("7 likes").assertIsDisplayed()
                peer.assertHealthy()
                captureFeedEvidence("repeat-like-evidence/three-sent-likes-no-confirmation.png")
                retainFrames(peer, "deliberate")
            }
        }
    }

    @Test fun rapidLikesAdvanceTheCountWhilePeerAuthenticationIsDelayed() {
        val likeNode = PostLikePeer()
        withLikes(likeNode) { peer, intent ->
            withLikeActivity(intent, "slow-peer") {
                waitFor("4 likes")
                peer.withholdHandshakeReplies = true
                try {
                    val startedAt = SystemClock.elapsedRealtime()
                    ui.onNode(likePostAction()).performTouchInput {
                        repeat(3) {
                            click()
                            advanceEventTime(40)
                        }
                    }
                    ui.waitUntil(500) { ui.onNodeWithText("7 likes").isDisplayed() }
                    assertTrue("Three rapid Likes update the visible count within 500 ms",
                        SystemClock.elapsedRealtime() - startedAt <= 500)
                    ui.onNodeWithText("7 likes").assertIsDisplayed()
                    ui.onNode(likePostAction()).assertIsEnabled()
                    assertTrue("A slow peer handshake has not received Likes yet", peer.transactions.isEmpty())
                } finally {
                    peer.releaseWithheldHandshakeReplies()
                }
                ui.waitUntil(20_000) { peer.transactions.size == 3 }
                assertEquals(3, peer.transactions.size)
            }
        }
    }

    private fun retainFrames(peer: ControlledFeedPeer, journey: String) {
        peer.transactions.forEachIndexed { index, frame ->
            File(context.cacheDir, "repeat-like-evidence/$journey/native-like-$index.base64").apply {
                parentFile?.mkdirs()
                writeText(Base64.getEncoder().encodeToString(frame))
            }
        }
    }

    private fun withLikes(likeNode: PostLikePeer = PostLikePeer(), block: (ControlledFeedPeer, Intent) -> Unit) {
        val peer = likeNode.peer
        peer.start().use { server ->
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
            block(peer, intent)
        }
    }

    private fun withLikeActivity(intent: Intent, journey: String, block: (ActivityScenario<MainActivity>) -> Unit) {
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            withFailureCapture({ captureFeedEvidence("repeat-like-evidence/$journey-failure.png") }) {
                block(scenario)
            }
        }
    }

    private fun waitFor(text: String) {
        ui.waitUntil(20_000) { ui.onAllNodes(hasText(text))
            .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
    }

}
