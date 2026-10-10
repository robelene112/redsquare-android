package nl.davidengelen.redsquare.feed

import android.content.Context
import nl.davidengelen.redsquare.saito.*

/** Bounded public posts and signed like records. Never stores wallet or private Chat data. */
internal class AndroidProfileCache(context: Context, backend: SaitoBackend) {
    private val storage = PublicStringStore(context, "profile.${backend.id}.v1", 200)
    data class SavedProfile(val posts: List<FeedPost>, val likes: List<ProfileLike>, val unreadable: Boolean)

    fun load(): SavedProfile = decode(storage.load())

    private fun decode(saved: PublicStringStore.Loaded): SavedProfile {
        val posts = mutableListOf<FeedPost>()
        val likes = mutableListOf<ProfileLike>()
        var unreadable = saved.unreadable
        for (envelope in saved.values) {
            try { posts.add(RedSquareFeed.decode(envelope)) }
            catch (_: Exception) {
                try { likes.add(RedSquareLikes.decode(envelope)) }
                catch (_: Exception) { unreadable = true }
            }
        }
        return SavedProfile(RedSquareFeed.newest(posts), likes.distinctBy { it.id }, unreadable)
    }

    fun save(posts: List<FeedPost>, likes: List<ProfileLike>) = storage.update { saved ->
        (RedSquareFeed.newest(posts + decode(saved).posts).map { it.createdAt to it.envelope } +
            likes.distinctBy { it.id }.map { it.createdAt to it.envelope })
            .sortedByDescending { it.first }.map { it.second }
    }
}
