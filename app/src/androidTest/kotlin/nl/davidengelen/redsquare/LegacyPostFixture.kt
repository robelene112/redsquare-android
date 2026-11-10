package nl.davidengelen.redsquare

import android.content.Context
import android.os.Build
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.feed.PostDraftStore
import nl.davidengelen.redsquare.saito.FeedPost
import nl.davidengelen.redsquare.saito.RedSquarePosts
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoPeer
import nl.davidengelen.redsquare.saito.SaitoTransaction

/** Actual loopback transmission with the persisted editor format from beta102. */
internal fun legacySubmittedPost(context: Context, endpoint: String, text: String,
    images: List<String> = emptyList(), parent: FeedPost? = null, quote: FeedPost? = null): SaitoTransaction {
    check(context.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
    require(endpoint.matches(Regex("ws://127\\.0\\.0\\.1:[0-9]+/wsopen")))
    val wallet = AndroidWalletStore(context).loadExisting()
    val recipients = (parent ?: quote)?.let { (listOf(it.author) + it.recipients).distinct() }.orEmpty()
    val candidate = SaitoTransaction.create(wallet, RedSquarePosts.message(text, images, parent, quote),
        System.currentTimeMillis(), recipients)
    val store = PostDraftStore(context, wallet.publicKey, SaitoBackend.OWNER, parent, quote)
    store.save(PostDraftStore.Saved(text, candidate, images = images))
    runBlocking(Dispatchers.IO) {
        SaitoPeer(wallet, endpoint).use { peer ->
            peer.submit(candidate) { store.assertCanPublish(candidate.id) }
            peer.finishBroadcast()
        }
    }
    return candidate
}

/** Validate durable state on changes, without redoing signature/Keystore work
 * on every test frame and starving the Compose clock used by the observer. */
internal fun postAvailabilityProbe(context: Context, owner: String, id: String): () -> Boolean {
    check(context.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
    var observedVersion = Long.MIN_VALUE
    var available = false
    return {
        val version = PostDraftStore.changes.value
        if (version != observedVersion) {
            available = PostDraftStore(context, owner, SaitoBackend.OWNER).hasSubmission(id) &&
                PostDraftStore.pendingDelivery(context, owner, SaitoBackend.OWNER).none { it.load().transaction?.id == id }
            // A write during the probe remains visible on the next iteration.
            observedVersion = version
        }
        available
    }
}

/** A validated retained receipt and no pending observation prove node reconciliation. */
internal fun ComposeTestRule.waitForPostAvailability(owner: String, id: String, timeoutMillis: Long = 20_000,
    destinationReady: () -> Boolean = { true }) {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val available = postAvailabilityProbe(context, owner, id)
    waitUntil(timeoutMillis) { available() && destinationReady() }
}
