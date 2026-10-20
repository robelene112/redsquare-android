package nl.davidengelen.redsquare

import android.content.Intent
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

/** Deliberate repeat input, observed through native controls and compatible peer frames. */
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

    @Test fun laterDeliberateLikeSignsADistinctActionAndShowsOnlyServedCounts() {
        withLikes { peer, intent ->
            withLikeActivity(intent, "deliberate") {
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
                val originals = peer.transactions.map(::postingTransaction)
                assertNotEquals(originals[0].id, originals[1].id)
                assertEquals(SaitoEnvelope.decode(originals[0].envelope()).author,
                    SaitoEnvelope.decode(originals[1].envelope()).author)
                ui.onNodeWithText("Unlike").assertDoesNotExist()
                peer.assertHealthy()
                captureFeedEvidence("repeat-like-evidence/two-distinct-served-likes.png")
                retainFrames(peer, "deliberate")
            }
        }
    }

    @Test fun rateLimitSurvivesRecreationAndExpiresAtTheRollingBoundary() {
        withLikes { peer, intent ->
            intent.putExtra("socialLikeElapsedMillis", 10_000L)
            withLikeActivity(intent, "rate-recreation") { scenario ->
                waitFor("4 likes")
                ui.clickVisiblePostContent("Like")
                waitFor("5 likes")
                ui.clickVisiblePostContent("Liked")
                waitFor("6 likes")
                ui.clickVisiblePostContent("Liked")
                assertRateFeedback()
                assertEquals(2, peer.transactions.size)
                captureFeedEvidence("repeat-like-evidence/rate-limit-feedback.png")
                scenario.recreate()
                waitFor("6 likes")
                ui.clickVisiblePostContent("Liked")
                assertRateFeedback()
                assertEquals(2, peer.transactions.size)
                ui.onNodeWithText("Retry like").assertDoesNotExist()
                ui.onNodeWithText("Queued").assertDoesNotExist()
                captureFeedEvidence("repeat-like-evidence/recreated-rate-limit.png")
            }
            intent.putExtra("socialLikeElapsedMillis", 11_000L)
            withLikeActivity(intent, "rate-expired") {
                waitFor("6 likes")
                ui.clickVisiblePostContent("Liked")
                waitFor("7 likes")
                assertEquals(3, peer.transactions.size)
                assertEquals(3, peer.transactions.map(::postingTransaction).map { it.id }.toSet().size)
                assertEquals(1, peer.transactions.map(::postingTransaction)
                    .map { SaitoEnvelope.decode(it.envelope()).author }.toSet().size)
                peer.assertHealthy()
                captureFeedEvidence("repeat-like-evidence/rolling-window-expired.png")
                retainFrames(peer, "rate-recreation")
            }
        }
    }

    @Test fun oneWalletSharesItsRateAcrossBothNodesThreadAndProfile() {
        val owner = PostLikePeer()
        val official = PostLikePeer()
        owner.peer.start().use { ownerServer -> official.peer.start().use { officialServer ->
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${ownerServer.port}/wsopen")
                .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${officialServer.port}/wsopen")
                .putExtra("socialLikeElapsedMillis", 10_000L)
            withLikeActivity(intent, "rate-across-nodes") {
                waitFor("4 likes")
                ui.clickVisiblePostContent("Like")
                waitFor("5 likes")
                ui.onNodeWithText("Settings").performClick()
                waitFor("saito.io")
                ui.onNodeWithText("saito.io").performScrollTo().performClick()
                waitFor("Node: saito.io")
                waitFor("4 likes")
                ui.clickVisiblePostContent("Like")
                waitFor("5 likes")
                ui.clickVisiblePostContent("Native Red Square reference post")
                waitFor("Conversation")
                ui.clickVisiblePostContent("Liked")
                assertRateFeedback()
                assertEquals(1, owner.peer.transactions.size)
                assertEquals(1, official.peer.transactions.size)
                captureFeedEvidence("repeat-like-evidence/thread-shares-both-node-rate.png")
                ui.onNodeWithText("Back").performClick()
                waitFor("New post")
                ui.onNodeWithText("Settings").performClick()
                waitFor("saito.davidengelen.nl")
                ui.onNodeWithText("saito.davidengelen.nl").performScrollTo().performClick()
                waitFor("Node: saito.davidengelen.nl")
                waitFor("5 likes")
                ui.clickVisiblePostContent(owner.postAuthor)
                waitFor("Profile")
                ui.clickVisiblePostContent("Liked")
                assertRateFeedback()
                assertEquals(1, owner.peer.transactions.size)
                assertEquals(1, official.peer.transactions.size)
                val first = postingTransaction(owner.peer.transactions.single())
                val second = postingTransaction(official.peer.transactions.single())
                assertNotEquals(first.id, second.id)
                assertEquals(SaitoEnvelope.decode(first.envelope()).author,
                    SaitoEnvelope.decode(second.envelope()).author)
                owner.peer.assertHealthy(); official.peer.assertHealthy()
                captureFeedEvidence("repeat-like-evidence/profile-shares-both-node-rate.png")
                retainFrames(owner.peer, "rate-nodes-owner")
                retainFrames(official.peer, "rate-nodes-official")
            }
        } }
    }

    private fun assertRateFeedback() {
        val message = "Two likes per second. Try again in a moment."
        waitFor(message)
        ui.onNode(hasText(message) and SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.LiveRegion,
            androidx.compose.ui.semantics.LiveRegionMode.Polite)).assertIsDisplayed()
    }

    private fun retainFrames(peer: ControlledFeedPeer, journey: String) {
        peer.transactions.forEachIndexed { index, frame ->
            File(context.cacheDir, "repeat-like-evidence/$journey/native-like-$index.base64").apply {
                parentFile?.mkdirs()
                writeText(Base64.getEncoder().encodeToString(frame))
            }
        }
    }

    private fun withLikes(block: (ControlledFeedPeer, Intent) -> Unit) {
        val peer = PostLikePeer().peer
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
