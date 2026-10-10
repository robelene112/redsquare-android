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
        if (originals.size >= 200) originals.keys.firstOrNull { originals[it]?.loading != true }?.let { originals.remove(it) }
        originals[target] = QuoteOriginal(loading = true)
        try {
            val cached = withContext(Dispatchers.IO) {
                val saved = cache.load()
                val posts = listOfNotNull(embedded) + saved.values.mapNotNull { envelope ->
                    try { RedSquareFeed.decode(envelope).takeIf { it.id == target } } catch (_: Exception) { null }
                }
                revisions.apply(RedSquareFeed.newest(posts)).let { QuoteOriginal(it.posts.firstOrNull(),
                    cacheWarning = saved.unreadable || it.unreadable, deleted = it.deleted) }
            }
            if (cached.post != null || cached.deleted) { originals[target] = cached; return }
            val activeWallet = wallet ?: run { originals[target] = cached; return }
            val result = lookups.withPermit {
                withContext(Dispatchers.IO) {
                    withTimeoutOrNull(13_000) {
                        SaitoPeer(activeWallet, endpoint).use { peer ->
                            val criteria = JsonObject().apply {
                                addProperty("request", "load"); addProperty("field1", "RedSquare")
                                addProperty("sig", target); addProperty("flagged_ne", 1); addProperty("limit", 1)
                            }
                            val request = JsonObject().apply { addProperty("request", "archive"); add("data", criteria) }
                            val page = RedSquareFeed.readTarget(peer.request(request.toString().toByteArray(Charsets.UTF_8))
                                .toString(Charsets.UTF_8), target)
                            check(page.ignored == 0) { "Unreadable original" }
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
        } catch (_: TimeoutCancellationException) { originals[target] = QuoteOriginal(failed = true) }
        catch (cancelled: CancellationException) { originals.remove(target); throw cancelled }
        catch (_: Exception) { originals[target] = QuoteOriginal(failed = true) }
    }

    fun retry(target: String) {
        if (originals[target]?.loading == true) return
        originals.remove(target)
        scope.launch { load(target) }
    }
}
