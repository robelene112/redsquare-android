package nl.davidengelen.redsquare.store

import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.EmptyCoroutineContext
import nl.davidengelen.redsquare.saito.*

/** IO-only archive resolution. The synchronous core reader inherits this operation's cancellation. */
internal suspend fun readStoreProduct(peer: SaitoPeer, signature: String, summary: StoreSummary? = null): StoreProduct? {
    val owner = currentCoroutineContext()[Job] ?: EmptyCoroutineContext
    return SaitoStoreLineage.read(signature) { id -> runBlocking(owner) {
        peer.request(SaitoStore.listingArchiveRequest(id).toByteArray()).decodeToString()
    } }?.takeIf { summary == null || it.seller == summary.seller && it.nftId == summary.nftId }
}
