package nl.davidengelen.redsquare.feed

import android.content.Context
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.FeedPost
import nl.davidengelen.redsquare.saito.RedSquareFeed

/** Public post cache. It contains no wallet or private Chat state. */
class AndroidFeedCache(context: Context, backend: SaitoBackend, private val threadId: String? = null,
    followingOwner: String? = null) {
    init { require(threadId == null || threadId.matches(Regex("[0-9a-f]{128}"))) }
    init { require(followingOwner == null ||
        (threadId == null && followingOwner.matches(Regex("[1-9A-HJ-NP-Za-km-z]{43,45}")))) }
    private val storage = PublicStringStore(context, when {
        followingOwner != null -> "followingfeed.${backend.id}.$followingOwner.v1"
        threadId != null -> "conversation.${backend.id}.$threadId.v1"
        else -> "feed.${backend.id}.v1"
    }, MAX_POSTS)
    data class SavedPosts(val posts: List<FeedPost>, val unreadable: Boolean)

    fun load(): SavedPosts {
        val saved = storage.load()
        var unreadable = saved.unreadable
        val posts = saved.values.mapNotNull { envelope ->
            try { RedSquareFeed.decode(envelope).takeIf { post ->
                if (threadId == null) post.parentId.isEmpty() else post.threadId == threadId
            } } catch (_: Exception) { unreadable = true; null }
        }
        return SavedPosts(posts.distinctBy { it.id }.sortedByDescending { it.createdAt }, unreadable)
    }

    fun save(posts: List<FeedPost>) = storage.save(
        posts.distinctBy { it.id }.sortedByDescending { it.createdAt }.map { it.envelope })

    companion object { const val MAX_POSTS = 100 }
}
