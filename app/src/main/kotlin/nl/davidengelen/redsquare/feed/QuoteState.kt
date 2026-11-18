package nl.davidengelen.redsquare.feed

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.TimeoutCancellationException
import nl.davidengelen.redsquare.saito.*

internal data class QuoteOriginal(val post: FeedPost? = null, val loading: Boolean = false, val failed: Boolean = false,
    val cacheWarning: Boolean = false, val deleted: Boolean = false)

/** Visible originals only: verified public cache, distinct targets and three bounded concurrent lookups. */
internal class QuoteState(context: Context, private val wallet: SaitoWallet?, backend: SaitoBackend,
    private val endpoint: String, private val scope: CoroutineScope) {
    val originals = mutableStateMapOf<String, QuoteOriginal>()
    private val cache = PublicStringStore(context, "quoteoriginal.${backend.id}.v1", 100)
    private val revisions = AndroidPostRevisions(context, backend)
    private val writes = Mutex()
    private val lookups = Semaphore(3)

    suspend fun load(target: String, embedded: FeedPost? = null, refresh: Boolean = false) {
        if (target in originals && !refresh) return
        require(target.matches(Regex("[0-9a-f]{128}")))
        if (target !in originals && originals.size >= 200) originals.keys.firstOrNull { originals[it]?.loading != true }?.let { originals.remove(it) }
        // Re-entering a lazy row re-observes its original. Keep verified content
        // throughout that read, including cancellation when the row leaves again.
        val previous = originals[target]
        val pending = (previous ?: QuoteOriginal()).copy(loading = true, failed = false)
        originals[target] = pending
        try {
            val cached = withContext(Dispatchers.IO) {
                // The session already owns these verified bytes. Recheck author
                // revisions without decoding the entire public cache on every scroll.
                val saved = if (previous?.post != null) PublicStringStore.Loaded(emptyList(), false) else cache.load()
                val posts = listOfNotNull(embedded, previous?.post) + saved.values.mapNotNull { envelope ->
                    try { RedSquareFeed.decode(envelope).takeIf { it.id == target } } catch (_: Exception) { null }
                }
                revisions.apply(RedSquareFeed.newest(posts)).let { applied ->
                    QuoteOriginal(applied.posts.firstOrNull() ?: previous?.post?.takeUnless { applied.deleted },
                        cacheWarning = saved.unreadable || applied.unreadable, deleted = applied.deleted)
                }
            }
            if (cached.post != null || cached.deleted) { originals[target] = cached; return }
            val activeWallet = wallet ?: run { originals[target] = cached; return }
            val result = lookups.withPermit {
                withContext(Dispatchers.IO) {
                    withTimeoutOrNull(13_000) {
                        SaitoPeer(activeWallet, endpoint).use { peer ->
                            suspend fun read(kind: String): ArchiveFeedPage {
                                val criteria = JsonObject().apply {
                                    addProperty("request", "load"); addProperty("field1", kind)
                                    addProperty("sig", target); addProperty("flagged_ne", 1); addProperty("limit", 1)
                                }
                                val request = JsonObject().apply { addProperty("request", "archive"); add("data", criteria) }
                                val page = RedSquareFeed.readTarget(peer.request(request.toString().toByteArray(Charsets.UTF_8))
                                    .toString(Charsets.UTF_8), target)
                                check(page.ignored == 0) { "Unreadable original" }
                                return page
                            }
                            val ordinary = read("RedSquare")
                            val page = if (ordinary.posts.isEmpty() && ordinary.deletions.isEmpty())
                                read("RedSquareRetweet") else ordinary
                            page.posts.forEach(revisions::observe)
                            revisions.apply(page.posts).let { QuoteOriginal(it.posts.firstOrNull(),
                                cacheWarning = cached.cacheWarning || it.unreadable, deleted = it.deleted) }
                        }
                    } ?: QuoteOriginal(failed = true)
                }
            }
            originals[target] = result
            result.post?.let { post ->
                try { writes.withLock {
                    withContext(Dispatchers.IO) { cache.update { saved ->
                        val posts = listOf(post) + saved.values.mapNotNull {
                            try { RedSquareFeed.decode(it) } catch (_: Exception) { null }
                        }
                        RedSquareFeed.newest(posts).map { it.envelope }
                    } }
                } } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { originals[target] = result.copy(cacheWarning = true) }
            }
        } catch (_: TimeoutCancellationException) {
            if (originals[target] === pending) originals[target] = pending.copy(loading = false, failed = true)
        } catch (cancelled: CancellationException) {
            if (originals[target] === pending) {
                if (previous == null) originals.remove(target)
                else originals[target] = previous.copy(loading = false)
            }
            throw cancelled
        } catch (_: Exception) {
            if (originals[target] === pending) originals[target] = pending.copy(loading = false, failed = true)
        }
    }

    fun retry(target: String) {
        if (originals[target]?.loading == true) return
        originals.remove(target)
        scope.launch { load(target) }
    }
}
