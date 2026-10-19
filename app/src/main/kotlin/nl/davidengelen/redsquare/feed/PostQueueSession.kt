package nl.davidengelen.redsquare.feed

import nl.davidengelen.redsquare.saito.DeliveryAttempts
import android.content.Context
import kotlinx.coroutines.CancellationException
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoPeer
import nl.davidengelen.redsquare.saito.SaitoWallet

/** Deliver only persisted explicit intent belonging to this selected session. */
internal suspend fun resumeQueuedPosts(context: Context, wallet: SaitoWallet, backend: SaitoBackend, endpoint: String, onlyId: String? = null) {
    for (store in PostDraftStore.pendingDelivery(context, wallet.publicKey, backend)) {
        try {
            val saved = store.load()
            if (!saved.automatic || saved.available) continue
            val candidate = requireNotNull(saved.transaction)
            if (onlyId != null && candidate.id != onlyId) continue
            DeliveryAttempts.run(candidate.id) {
            SaitoPeer(wallet, endpoint).use { peer ->
                if (peer.hasPost(candidate, wallet.publicKey, saved.text)) {
                    store.update(saved, saved.copy(queued = false, available = true))
                    return@use
                }
                // Authentication happened during the Archive request. The atomic
                // claim rechecks durable identity/selection and races with Cancel.
                if (!store.claim(saved)) return@use
                peer.submit(candidate) {
                    store.assertCanPublish(candidate.id)
                }
                if (peer.hasPost(candidate, wallet.publicKey, saved.text))
                    store.update(saved.copy(queued = false), saved.copy(queued = false, available = true))
            }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Queued or uncertain signed work remains durable. */ }
    }
}
