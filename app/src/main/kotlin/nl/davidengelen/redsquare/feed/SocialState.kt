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

internal data class PostActionKey(val target: String, val kind: RedSquareActionKind)

internal data class ActionStatus(val candidate: SaitoTransaction? = null, val available: Boolean = false,
    val ready: Boolean = false, val busy: Boolean = false, val failed: Boolean = false)

/** One selected wallet/backend's social operations, shared across its visible post views. */
internal class SocialState(private val context: Context, private val wallet: SaitoWallet,
    private val backend: SaitoBackend, private val endpoint: String, private val scope: CoroutineScope) {
    val actions = mutableStateMapOf<PostActionKey, ActionStatus>()
    val observedPosts = mutableStateMapOf<String, FeedPost>()
    /** Called only for fresh server responses, never when reopening older cached views. */
    fun observe(posts: List<FeedPost>) {
        posts.forEach { observedPosts[it.id] = it }
    }
    private fun store(key: PostActionKey) = PublicStringStore(context, "social.${backend.id}.${wallet.publicKey}.${key.target}.${key.kind.storageId}.v1", 1)

    suspend fun load(post: FeedPost, kind: RedSquareActionKind) {
        val key = PostActionKey(post.id, kind)
        if (actions.containsKey(key)) return
        actions[key] = ActionStatus()
        try {
            val status = withContext(Dispatchers.IO) {
                val saved = store(key).load()
                require(!saved.unreadable)
                saved.values.singleOrNull()?.let { value ->
                    val record = JsonParser.parseString(value).asJsonObject
                    val candidate = SaitoTransaction.restore(record.get("transaction").asString)
                    val action = RedSquareActions.decode(candidate.envelope())
                    require(action.author == wallet.publicKey && action.target == post.id && action.kind == kind)
                    val available = record.get("available").asJsonPrimitive
                    require(available.isBoolean)
                    ActionStatus(candidate, available.asBoolean, ready = true)
                } ?: ActionStatus(ready = true)
            }
            actions[key] = status
        } catch (cancelled: CancellationException) { actions.remove(key); throw cancelled }
        catch (_: Exception) { actions[key] = ActionStatus(failed = true) }
    }

    private fun save(key: PostActionKey, candidate: SaitoTransaction, available: Boolean) {
        val record = JsonObject().apply {
            addProperty("transaction", candidate.encoded()); addProperty("available", available)
        }.toString()
        val storage = store(key)
        storage.save(listOf(record))
        check(storage.load().let { !it.unreadable && it.values == listOf(record) })
    }

    fun retryLoad(post: FeedPost, kind: RedSquareActionKind) {
        val key = PostActionKey(post.id, kind)
        actions.remove(key)
        scope.launch { load(post, kind) }
    }

    fun send(post: FeedPost, kind: RedSquareActionKind) {
        val key = PostActionKey(post.id, kind)
        val status = actions[key] ?: return
        if (!status.ready || status.busy || status.available) return
        actions[key] = status.copy(busy = true, failed = false)
        scope.launch {
            var candidate = status.candidate
            try {
                if (candidate == null) candidate = withContext(Dispatchers.IO) {
                    val body = JsonObject().apply {
                        addProperty("module", "RedSquare"); addProperty("request", kind.request)
                        add("data", JsonObject().apply { addProperty("signature", post.id) })
                    }
                    SaitoTransaction.create(wallet, body, System.currentTimeMillis(), (listOf(post.author) + post.recipients).distinct())
                }
                val original = requireNotNull(candidate)
                withContext(NonCancellable + Dispatchers.IO) { save(key, original, false) }
                actions[key] = ActionStatus(original, ready = true, busy = true)
                withContext(Dispatchers.IO) {
                    SaitoPeer(wallet, endpoint).use { peer ->
                        suspend fun observed(): Boolean {
                            val query = JsonObject().apply {
                                addProperty("request", "archive")
                                add("data", JsonObject().apply {
                                    addProperty("request", "load"); addProperty("field1", kind.archiveKind)
                                    addProperty("sig", original.id)
                                })
                            }
                            val actions = RedSquareActions.readArchive(peer.request(query.toString().toByteArray(Charsets.UTF_8))
                                .toString(Charsets.UTF_8), wallet.publicKey, kind)
                            return actions.any { it.id == original.id && it.target == post.id }
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
                        withContext(NonCancellable) { save(key, original, true) }
                        withContext(Dispatchers.Main.immediate) { actions[key] = ActionStatus(original, available = true, ready = true) }
                        val query = JsonObject().apply {
                            addProperty("request", "archive")
                            add("data", JsonObject().apply {
                                addProperty("request", "load"); addProperty("field1", "RedSquare")
                                addProperty("sig", post.id); addProperty("flagged_ne", 1)
                            })
                        }
                        val served = RedSquareFeed.readTarget(peer.request(query.toString().toByteArray(Charsets.UTF_8))
                            .toString(Charsets.UTF_8), post.id).posts.singleOrNull()
                        if (served != null) withContext(Dispatchers.Main.immediate) { observe(listOf(served)) }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                val current = actions[key] ?: status
                actions[key] = current.copy(candidate = candidate, failed = true)
            } finally {
                actions[key]?.let { actions[key] = it.copy(busy = false) }
            }
        }
    }
}
