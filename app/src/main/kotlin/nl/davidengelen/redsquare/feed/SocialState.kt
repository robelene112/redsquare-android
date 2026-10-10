package nl.davidengelen.redsquare.feed

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.saito.*

internal data class LikeStatus(val candidate: SaitoTransaction? = null, val available: Boolean = false,
    val ready: Boolean = false, val busy: Boolean = false, val failed: Boolean = false)

/** One selected wallet/backend's social operations, shared across its visible post views. */
internal class SocialState(private val context: Context, private val wallet: SaitoWallet,
    private val backend: SaitoBackend, private val endpoint: String, private val scope: CoroutineScope) {
    val likes = mutableStateMapOf<String, LikeStatus>()
    val observedPosts = mutableStateMapOf<String, FeedPost>()
    private fun store(target: String) = PublicStringStore(context, "social.${backend.id}.${wallet.publicKey}.$target.like.v1", 1)

    suspend fun load(post: FeedPost) {
        if (likes.containsKey(post.id)) return
        likes[post.id] = LikeStatus()
        try {
            val status = withContext(Dispatchers.IO) {
                val saved = store(post.id).load()
                require(!saved.unreadable)
                saved.values.singleOrNull()?.let { value ->
                    val record = JsonParser.parseString(value).asJsonObject
                    val candidate = SaitoTransaction.restore(record.get("transaction").asString)
                    val like = RedSquareLikes.decode(candidate.envelope())
                    require(like.author == wallet.publicKey && like.target == post.id)
                    val available = record.get("available").asJsonPrimitive
                    require(available.isBoolean)
                    LikeStatus(candidate, available.asBoolean, ready = true)
                } ?: LikeStatus(ready = true)
            }
            likes[post.id] = status
        } catch (cancelled: CancellationException) { likes.remove(post.id); throw cancelled }
        catch (_: Exception) { likes[post.id] = LikeStatus(failed = true) }
    }

    private fun save(target: String, candidate: SaitoTransaction, available: Boolean) {
        val record = JsonObject().apply {
            addProperty("transaction", candidate.encoded()); addProperty("available", available)
        }.toString()
        val storage = store(target)
        storage.save(listOf(record))
        check(storage.load().let { !it.unreadable && it.values == listOf(record) })
    }

    fun retryLoad(post: FeedPost) {
        likes.remove(post.id)
        scope.launch { load(post) }
    }

    fun like(post: FeedPost) {
        val status = likes[post.id] ?: return
        if (!status.ready || status.busy || status.available) return
        likes[post.id] = status.copy(busy = true, failed = false)
        scope.launch {
            var candidate = status.candidate
            try {
                if (candidate == null) candidate = withContext(Dispatchers.IO) {
                    val body = JsonObject().apply {
                        addProperty("module", "RedSquare"); addProperty("request", "like tweet")
                        add("data", JsonObject().apply { addProperty("signature", post.id) })
                    }
                    SaitoTransaction.create(wallet, body, System.currentTimeMillis(), (listOf(post.author) + post.recipients).distinct())
                }
                val original = requireNotNull(candidate)
                withContext(NonCancellable + Dispatchers.IO) { save(post.id, original, false) }
                likes[post.id] = LikeStatus(original, ready = true, busy = true)
                withContext(Dispatchers.IO) {
                    SaitoPeer(wallet, endpoint).use { peer ->
                        suspend fun observed(): Boolean {
                            val query = JsonObject().apply {
                                addProperty("request", "archive")
                                add("data", JsonObject().apply {
                                    addProperty("request", "load"); addProperty("field1", "RedSquareLike")
                                    addProperty("sig", original.id)
                                })
                            }
                            val page = RedSquareLikes.readArchive(peer.request(query.toString().toByteArray(Charsets.UTF_8))
                                .toString(Charsets.UTF_8), wallet.publicKey)
                            return page.likes.any { it.id == original.id && it.target == post.id }
                        }
                        if (!observed()) {
                            val permitted = synchronized(SaitoLocalState.lock) {
                                AndroidBackendStore(context).load() == backend &&
                                    nl.davidengelen.redsquare.AndroidWalletStore(context).loadExisting().publicKey == wallet.publicKey
                            }
                            check(permitted)
                            peer.submit(original)
                        }
                        if (!observed()) throw IllegalStateException("Interaction availability unknown")
                        withContext(NonCancellable) { save(post.id, original, true) }
                        withContext(Dispatchers.Main.immediate) { likes[post.id] = LikeStatus(original, available = true, ready = true) }
                        val query = JsonObject().apply {
                            addProperty("request", "archive")
                            add("data", JsonObject().apply {
                                addProperty("request", "load"); addProperty("field1", "RedSquare")
                                addProperty("sig", post.id); addProperty("flagged_ne", 1)
                            })
                        }
                        val served = RedSquareFeed.readTarget(peer.request(query.toString().toByteArray(Charsets.UTF_8))
                            .toString(Charsets.UTF_8), post.id).posts.singleOrNull()
                        if (served != null) withContext(Dispatchers.Main.immediate) { observedPosts[post.id] = served }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                val current = likes[post.id] ?: status
                likes[post.id] = current.copy(candidate = candidate, failed = true)
            } finally {
                likes[post.id]?.let { likes[post.id] = it.copy(busy = false) }
            }
        }
    }
}
