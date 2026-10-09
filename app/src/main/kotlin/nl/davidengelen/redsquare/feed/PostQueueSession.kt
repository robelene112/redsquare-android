package nl.davidengelen.redsquare.feed

import android.content.Context
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import nl.davidengelen.redsquare.saito.RedSquareFeed
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoPeer
import nl.davidengelen.redsquare.saito.SaitoWallet

/** Deliver only persisted explicit intent belonging to this selected session. */
internal suspend fun resumeQueuedPosts(context: Context, wallet: SaitoWallet, backend: SaitoBackend, endpoint: String) {
    for (store in PostDraftStore.queued(context, wallet.publicKey, backend)) {
        try {
            val saved = store.load()
            if (!saved.queued) continue
            val candidate = requireNotNull(saved.transaction)
            PostDeliveryAttempts.run(candidate.id) {
            SaitoPeer(wallet, endpoint).use { peer ->
                val query = JsonObject().apply {
                    addProperty("request", "archive")
                    add("data", JsonObject().apply {
                        addProperty("request", "load"); addProperty("field1", "RedSquare")
                        addProperty("sig", candidate.id); addProperty("flagged_ne", 1)
                    })
                }
                suspend fun observed(): Boolean {
                    val response = peer.request(query.toString().toByteArray(Charsets.UTF_8)).toString(Charsets.UTF_8)
                    return RedSquareFeed.readTarget(response, candidate.id).posts
                        .any { it.author == wallet.publicKey && it.text == saved.text }
                }
                if (observed()) {
                    store.update(saved, saved.copy(queued = false, available = true))
                    return@use
                }
                // Authentication happened during the Archive request. The atomic
                // claim rechecks durable identity/selection and races with Cancel.
                if (!store.claim(saved)) return@use
                peer.submit(candidate)
                if (observed()) store.update(saved.copy(queued = false), saved.copy(queued = false, available = true))
            }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Queued or uncertain signed work remains durable. */ }
    }
}
