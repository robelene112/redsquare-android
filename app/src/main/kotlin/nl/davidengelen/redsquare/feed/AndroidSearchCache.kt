package nl.davidengelen.redsquare.feed

import android.content.Context
import nl.davidengelen.redsquare.saito.*

/** Verified public history for one server. Never stores queries, wallet or private Chat data. */
internal class AndroidSearchCache(context: Context, backend: SaitoBackend) {
    private val storage = PublicStringStore(context, "search.${backend.id}.v1", MAX_POSTS)
    private val feed = AndroidFeedCache(context, backend)
    private val profiles = AndroidProfileCache(context, backend)
    private val revisions = AndroidPostRevisions(context, backend)
    data class History(val posts: List<FeedPost>, val unreadable: Boolean, val limited: Boolean = false)

    fun load(): History {
        val saved = decode(storage.load())
        val roots = feed.load()
        val people = profiles.load()
        return History(RedSquareFeed.newest(saved.posts + roots.posts + people.posts),
            saved.unreadable || roots.unreadable || people.unreadable)
    }

    /** Keep discovered matches first, within both the entry and encoded public-store budget. */
    fun retain(posts: List<FeedPost>, query: String): History {
        val applied = revisions.apply(RedSquareFeed.newest(posts))
        val ordered = applied.posts.sortedWith(compareByDescending<FeedPost> {
            it.text.contains(query, ignoreCase = true)
        }.thenByDescending { it.createdAt })
        val retained = ordered.take(storage.fittingEntryCount(ordered.asSequence().map { it.envelope }))
        return History(retained, applied.unreadable,
            retained.size == MAX_POSTS || retained.size < ordered.size)
    }

    fun save(posts: List<FeedPost>, query: String) {
        storage.update { previous ->
            val retained = retain(posts + decode(previous).posts, query)
            check(!retained.unreadable)
            retained.posts.map { it.envelope }
        }
    }

    private fun decode(saved: PublicStringStore.Loaded): History {
        var unreadable = saved.unreadable
        val posts = saved.values.mapNotNull { envelope ->
            try { RedSquareFeed.decode(envelope) }
            catch (_: Exception) { unreadable = true; null }
        }
        val applied = revisions.apply(RedSquareFeed.newest(posts))
        return History(applied.posts, unreadable || applied.unreadable)
    }

    companion object { private const val MAX_POSTS = 1000 }
}
