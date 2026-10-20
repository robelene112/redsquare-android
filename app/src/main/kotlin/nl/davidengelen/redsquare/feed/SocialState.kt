package nl.davidengelen.redsquare.feed

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.policy.PublishingPolicies

internal data class PostActionKey(val target: String, val kind: RedSquareActionKind)

internal data class ActionStatus(val candidate: SaitoTransaction? = null, val available: Boolean = false,
    val ready: Boolean = false, val busy: Boolean = false, val failed: Boolean = false,
    val countsUnavailable: Boolean = false, val blockedBy: SaitoBackend? = null,
    val rateFeedback: Long = 0)

/** One selected wallet/backend's social operations, shared across its visible post views. */
internal class SocialState(private val context: Context, private val wallet: SaitoWallet,
    private val backend: SaitoBackend, private val endpoint: String, private val scope: CoroutineScope,
    inputClock: () -> Long = android.os.SystemClock::elapsedRealtime) {
    val reports = ReportState(context, wallet, backend, endpoint, scope)
    val actions = mutableStateMapOf<PostActionKey, ActionStatus>()
    val observedPosts = mutableStateMapOf<String, FeedPost>()
    private val generations = mutableMapOf<PostActionKey, Any>()
    private val inputRate = PostLikeInputRate(context, wallet.publicKey, inputClock)
    private var feedbackSequence = 0L
    /** Called only for fresh node responses, never when reopening older cached views. */
    fun observe(posts: List<FeedPost>) {
        posts.forEach { post ->
            observedPosts[post.id] = post
            actions.keys.filter { it.target == post.id }.forEach { key ->
                actions[key]?.let { actions[key] = it.copy(countsUnavailable = false) }
            }
        }
    }
    private fun journal(key: PostActionKey, node: SaitoBackend = backend) =
        SocialActionJournal(context, wallet.publicKey, node, key)

    fun busy(post: FeedPost, kind: RedSquareActionKind) =
        SocialActionOperations.busy(wallet.publicKey, PostActionKey(post.id, kind))

    private fun snapshot(key: PostActionKey): Pair<SocialActionJournal.Saved?, SaitoBackend?> {
        val current = journal(key).load()
        val foreign = if (key.kind == RedSquareActionKind.LIKE && current?.available != false)
            SaitoBackend.entries.filter { it != backend }.firstOrNull { journal(key, it).load()?.available == false }
        else null
        return current to foreign
    }

    suspend fun load(post: FeedPost, kind: RedSquareActionKind, refresh: Boolean = false) {
        val key = PostActionKey(post.id, kind)
        // A mounted operation owns its volatile save-failure candidate and all later UI writes.
        if (actions.containsKey(key) && (!refresh || generations.containsKey(key))) return
        actions[key] = ActionStatus()
        try {
            val (saved, foreign) = withContext(Dispatchers.IO) { snapshot(key) }
            actions[key] = ActionStatus(saved?.candidate, saved?.available == true, ready = true, blockedBy = foreign)
        } catch (cancelled: CancellationException) { actions.remove(key); throw cancelled }
        catch (_: Exception) { actions[key] = ActionStatus(failed = true) }
    }

    fun retryLoad(post: FeedPost, kind: RedSquareActionKind) {
        val key = PostActionKey(post.id, kind)
        actions.remove(key)
        generations.remove(key)
        scope.launch { load(post, kind) }
    }

    fun send(post: FeedPost, kind: RedSquareActionKind, beforePublishing: suspend () -> Boolean) {
        val key = PostActionKey(post.id, kind)
        val status = actions[key] ?: return
        if (!status.ready || status.busy || status.blockedBy != null ||
            status.available && kind != RedSquareActionKind.LIKE) return
        val token = Any()
        if (!SocialActionOperations.acquire(wallet.publicKey, key, token)) return
        val previousGeneration = generations.put(key, token)
        actions[key] = status.copy(busy = true, failed = false)
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var candidate: SaitoTransaction? = null
            var durableRead = false
            try {
                val (saved, foreign) = withContext(Dispatchers.IO) { snapshot(key) }
                durableRead = true
                if (foreign != null) {
                    actions[key] = ActionStatus(saved?.candidate, saved?.available == true, ready = true, blockedBy = foreign)
                    return@launch
                }
                // An interrupted save may have settled even though this mounted view still says Retry.
                val retry = status.candidate?.takeIf { !status.available }
                if (saved?.available == true && (kind != RedSquareActionKind.LIKE ||
                    retry != null && SaitoEnvelope.decode(retry.envelope()).createdAt <=
                        SaitoEnvelope.decode(saved.candidate.envelope()).createdAt)) {
                    actions[key] = ActionStatus(saved.candidate, available = true, ready = true)
                    return@launch
                }
                candidate = saved?.candidate?.takeIf { !saved.available } ?: retry
                if (candidate == null && !beforePublishing()) return@launch
                if (candidate == null) {
                    candidate = withContext(Dispatchers.IO) {
                        synchronized(SaitoLocalState.lock) {
                            check(nl.davidengelen.redsquare.AndroidWalletStore(context).loadExisting().publicKey == wallet.publicKey &&
                                AndroidBackendStore(context).load() == backend)
                            if (kind == RedSquareActionKind.LIKE && !inputRate.reserve()) return@synchronized null
                            val nodes = if (kind == RedSquareActionKind.LIKE) SaitoBackend.entries else listOf(backend)
                            val latest = nodes.mapNotNull { journal(key, it).load()?.candidate }
                                .maxOfOrNull { SaitoEnvelope.decode(it.envelope()).createdAt } ?: -1L
                            val timestamp = maxOf(System.currentTimeMillis(), Math.addExact(latest, 1))
                            val body = JsonObject().apply {
                                addProperty("module", "RedSquare"); addProperty("request", kind.request)
                                add("data", JsonObject().apply { addProperty("signature", post.id) })
                            }
                            SaitoTransaction.create(wallet, body, timestamp,
                                (listOf(post.author) + post.recipients).distinct())
                        }
                    }
                    if (candidate == null) {
                        // Rejected input must not discard the previous operation's count observation.
                        if (previousGeneration != null) generations[key] = previousGeneration
                        actions[key] = ActionStatus(saved?.candidate, saved?.available == true, ready = true,
                            countsUnavailable = status.countsUnavailable, rateFeedback = ++feedbackSequence)
                        return@launch
                    }
                }
                val original = requireNotNull(candidate)
                actions[key] = ActionStatus(original, ready = true, busy = true)
                val prepared = withContext(NonCancellable + Dispatchers.IO) { journal(key).prepare(original, saved) }
                actions[key] = ActionStatus(original, available = prepared.available, ready = true, busy = true)
                withContext(Dispatchers.IO) {
                    SaitoPeer(wallet, endpoint).use { peer ->
                        suspend fun observed(): Boolean {
                            val query = archiveQuery(kind.archiveKind, original.id)
                            val found = RedSquareActions.readArchive(peer.request(query.toString().toByteArray(Charsets.UTF_8))
                                .toString(Charsets.UTF_8), wallet.publicKey, kind)
                            return found.any { it.id == original.id && it.target == post.id }
                        }
                        if (!observed()) {
                            if (!withContext(Dispatchers.Main.immediate) { beforePublishing() }) return@use
                            peer.submit(original) {
                                PublishingPolicies.requireAllowed(context)
                                check(AndroidBackendStore(context).load() == backend &&
                                    nl.davidengelen.redsquare.AndroidWalletStore(context).loadExisting().publicKey == wallet.publicKey)
                            }
                        }
                        if (!observed()) error("Interaction availability unknown")
                        withContext(NonCancellable) { journal(key).settle(original) }
                        withContext(Dispatchers.Main.immediate) {
                            if (generations[key] === token) actions[key] = ActionStatus(original, available = true, ready = true)
                            // A later deliberate intent need not wait for an independent count refresh.
                            SocialActionOperations.release(wallet.publicKey, key, token)
                        }
                        val query = archiveQuery("RedSquare", post.id).apply {
                            getAsJsonObject("data").addProperty("flagged_ne", 1)
                        }
                        val served = requireNotNull(RedSquareFeed.readTarget(peer.request(query.toString().toByteArray(Charsets.UTF_8))
                            .toString(Charsets.UTF_8), post.id).posts.singleOrNull())
                        withContext(Dispatchers.Main.immediate) {
                            if (generations[key] === token) observe(listOf(served))
                        }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (generations[key] === token) {
                    val current = actions[key] ?: status
                    actions[key] = if (!durableRead || candidate == null)
                        ActionStatus(current.candidate, current.available, failed = true)
                    else current.copy(candidate = candidate ?: current.candidate, failed = !current.available,
                        countsUnavailable = current.available)
                }
            } finally {
                SocialActionOperations.release(wallet.publicKey, key, token)
                if (generations[key] === token) actions[key]?.let { actions[key] = it.copy(busy = false) }
            }
        }
    }

    fun clearRateFeedback(post: FeedPost, kind: RedSquareActionKind, sequence: Long) {
        val key = PostActionKey(post.id, kind)
        actions[key]?.takeIf { it.rateFeedback == sequence }?.let {
            actions[key] = it.copy(rateFeedback = 0)
        }
    }

    private fun archiveQuery(kind: String, signature: String) = JsonObject().apply {
        addProperty("request", "archive")
        add("data", JsonObject().apply {
            addProperty("request", "load"); addProperty("field1", kind); addProperty("sig", signature)
        })
    }
}
