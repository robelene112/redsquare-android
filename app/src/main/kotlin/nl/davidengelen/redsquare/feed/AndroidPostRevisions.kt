package nl.davidengelen.redsquare.feed

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import nl.davidengelen.redsquare.saito.*

/** Backend-scoped public author proofs. Per-target records survive eviction from individual view caches. */
internal class AndroidPostRevisions(private val context: Context, private val backend: SaitoBackend) {
    data class Applied(val posts: List<FeedPost>, val unreadable: Boolean, val deleted: Boolean = false)

    private fun store(target: String): PublicStringStore {
        require(target.matches(Regex("[0-9a-f]{128}")))
        return PublicStringStore(context, "authorposts.${backend.id}.$target.v1", 1)
    }

    fun apply(posts: List<FeedPost>): Applied {
        var unreadable = false
        var deleted = false
        val visible = posts.mapNotNull { post ->
            val saved = store(post.id).load()
            if (saved.unreadable) { unreadable = true; return@mapNotNull null }
            try {
                val revision = saved.values.singleOrNull()?.let(PostRevision::restore)
                if (revision == null) post else {
                    require(revision.post.id == post.id)
                    if (revision.deletion != null) deleted = true
                    revision.apply(listOf(post)).singleOrNull()
                }
            } catch (_: Exception) { unreadable = true; null }
        }
        return Applied(visible, unreadable, deleted)
    }

    fun observe(post: FeedPost) {
        if (post.edit == null) return
        retain(post)
    }

    fun retain(post: FeedPost) {
        var changed = false
        store(post.id).update { saved ->
            check(!saved.unreadable)
            val previous = saved.values.singleOrNull()?.let(PostRevision::restore)
            require(previous == null || previous.post.id == post.id)
            val retained = if (previous?.deletion != null) previous else
                PostRevision.observed(RedSquareFeed.newest(listOfNotNull(post, previous?.post)).single().envelope)
            listOf(retained.encode()).also { changed = it != saved.values }
        }
        if (changed) changes.update { it + 1 }
    }

    fun original(target: String): FeedPost? {
        val saved = store(target).load()
        check(!saved.unreadable)
        return saved.values.singleOrNull()?.let(PostRevision::restore)?.post?.also { require(it.id == target) }
    }

    /** Called only after observing the exact command and successfully querying the original as absent. */
    fun confirmDeletion(original: FeedPost, command: AuthorAction) {
        val deleted = PostRevision.deleted(original.envelope, command.envelope)
        var changed = false
        store(original.id).update { saved ->
            check(!saved.unreadable)
            val previous = saved.values.singleOrNull()?.let(PostRevision::restore)
            require(previous == null || previous.post.id == original.id)
            listOf((previous?.takeIf { it.deletion != null } ?: deleted).encode()).also { changed = it != saved.values }
        }
        if (changed) changes.update { it + 1 }
    }

    companion object { val changes = MutableStateFlow(0L) }
}
