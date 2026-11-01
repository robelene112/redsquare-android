package nl.davidengelen.redsquare.feed

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.policy.PublishingPolicies

internal data class PostActionKey(val target: String, val kind: RedSquareActionKind)

internal data class ActionStatus(val candidate: SaitoTransaction? = null, val available: Boolean = false,
    val ready: Boolean = false, val busy: Boolean = false, val failed: Boolean = false,
    val countsUnavailable: Boolean = false,
    val tapFeedback: Long = 0)

/** Selected-wallet social operations, shared across views and isolated by backend. */
internal class SocialState(private val context: Context, private val wallet: SaitoWallet,
    private val backend: SaitoBackend, private val endpoint: String, private val scope: CoroutineScope) {
    val reports = ReportState(context, wallet, backend, endpoint, scope)
    val actions = mutableStateMapOf<PostActionKey, ActionStatus>()
    val observedPosts = mutableStateMapOf<String, FeedPost>()
    private val generations = mutableMapOf<PostActionKey, Any>()
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
        SocialActionOperations.busy(wallet.publicKey, backend, PostActionKey(post.id, kind))

    private fun snapshot(key: PostActionKey) = journal(key).load()

    suspend fun load(post: FeedPost, kind: RedSquareActionKind, refresh: Boolean = false) {
        val key = PostActionKey(post.id, kind)
        // A mounted operation owns its volatile save-failure candidate and all later UI writes.
        if (actions.containsKey(key) && (!refresh || generations.containsKey(key))) return
        actions[key] = ActionStatus()
        try {
            val saved = withContext(Dispatchers.IO) { snapshot(key) }
            actions[key] = ActionStatus(saved?.candidate, saved?.available == true, ready = true)
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
        if (kind == RedSquareActionKind.LIKE) {
            enqueueLike(post, beforePublishing)
            return
        }
        val status = actions[key] ?: return
        if (!status.ready || status.busy || status.available) return
        val token = Any()
        if (!SocialActionOperations.acquire(wallet.publicKey, backend, key, token)) return
        generations.put(key, token)
        actions[key] = status.copy(busy = true, failed = false)
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var candidate: SaitoTransaction? = null
            var durableRead = false
            try {
                val saved = withContext(Dispatchers.IO) { snapshot(key) }
                durableRead = true
                // An interrupted save may have settled even though this mounted view still says Retry.
                val retry = status.candidate?.takeIf { !status.available }
                if (saved?.available == true) {
                    actions[key] = ActionStatus(saved.candidate, available = true, ready = true)
                    return@launch
                }
                candidate = saved?.candidate?.takeIf { !saved.available } ?: retry
                val retryingPendingCandidate = candidate != null
                if (candidate == null && !beforePublishing()) return@launch
                if (candidate == null) {
                    candidate = withContext(Dispatchers.IO) {
                        synchronized(SaitoLocalState.lock) {
                            requireSelectedWalletAndBackend()
                            val latest = journal(key).load()?.candidate
                                ?.let { SaitoEnvelope.decode(it.envelope()).createdAt } ?: -1L
                            val timestamp = maxOf(System.currentTimeMillis(), Math.addExact(latest, 1))
                            val body = JsonObject().apply {
                                addProperty("module", "RedSquare"); addProperty("request", kind.request)
                                add("data", JsonObject().apply { addProperty("signature", post.id) })
                            }
                            SaitoTransaction.create(wallet, body, timestamp,
                                (listOf(post.author) + post.recipients).distinct())
                        }
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
                        val alreadyAvailable = observed()
                        if (!alreadyAvailable) {
                            if (!withContext(Dispatchers.Main.immediate) { beforePublishing() }) return@use
                            peer.submit(original) {
                                PublishingPolicies.requireAllowed(context)
                                check(AndroidBackendStore(context).load() == backend &&
                                    nl.davidengelen.redsquare.AndroidWalletStore(context).loadExisting().publicKey == wallet.publicKey)
                            }
                            val settled = waitForExactReadback(
                                timeoutMillis = if (kind == RedSquareActionKind.LIKE || retryingPendingCandidate)
                                    45_000L else 10_000L,
                                observed = { observed() })
                            if (!settled) error("Interaction availability unknown")
                        }
                        withContext(NonCancellable) { journal(key).settle(original) }
                        withContext(Dispatchers.Main.immediate) {
                            if (generations[key] === token) actions[key] = ActionStatus(original, available = true, ready = true)
                            // A later deliberate intent need not wait for an independent count refresh.
                            SocialActionOperations.release(wallet.publicKey, backend, key, token)
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
                SocialActionOperations.release(wallet.publicKey, backend, key, token)
                if (generations[key] === token) actions[key]?.let { actions[key] = it.copy(busy = false) }
            }
        }
    }

    private fun enqueueLike(post: FeedPost, beforePublishing: suspend () -> Boolean) {
        val key = PostActionKey(post.id, RedSquareActionKind.LIKE)
        val token = Any()
        val shouldStart = SocialActionOperations.enqueueLike(wallet.publicKey, backend, key, token)
        val previous = actions[key] ?: ActionStatus()
        val sequence = ++feedbackSequence
        actions[key] = previous.copy(ready = true, busy = true, failed = false, tapFeedback = sequence)
        if (!shouldStart) return
        generations[key] = token
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var peer: SaitoPeer? = null
            try {
                while (SocialActionOperations.takeLike(wallet.publicKey, backend, key, token)) {
                    if (!beforePublishing()) break
                    try {
                        if (peer == null) peer = withContext(Dispatchers.IO) { SaitoPeer(wallet, endpoint) }
                        submitLike(post, key, requireNotNull(peer))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        peer?.close()
                        peer = null
                        Log.w(LIKE_LOG_TAG, "socket_enqueue_failed node=${backend.domain} error=${failure.javaClass.simpleName}")
                    }
                }
            } finally {
                peer?.close()
                SocialActionOperations.release(wallet.publicKey, backend, key, token)
                if (generations[key] === token) generations.remove(key)
                actions[key]?.let { actions[key] = it.copy(busy = false) }
            }
        }
    }

    /** A tap completes at local peer-socket enqueue. Archive visibility is informational only. */
    private suspend fun submitLike(post: FeedPost, key: PostActionKey, peer: SaitoPeer) {
        val savedAtStart = withContext(Dispatchers.IO) { snapshot(key) }
        var saved = savedAtStart
        var candidate: SaitoTransaction? = null
        try {
            withContext(Dispatchers.IO) {
                    val pending = saved?.takeIf { !it.submitted }?.candidate
                    if (pending != null) {
                        peer.submit(pending) { requireSelectedWalletAndBackend() }
                        Log.i(LIKE_LOG_TAG, "socket_enqueued node=${backend.domain} signature=${pending.id}")
                        saved = withContext(NonCancellable) { journal(key).markSubmitted(pending) }
                    }
                    candidate = synchronized(SaitoLocalState.lock) {
                        requireSelectedWalletAndBackend()
                        val latest = SaitoBackend.entries.mapNotNull { journal(key, it).load()?.candidate }
                            .maxOfOrNull { SaitoEnvelope.decode(it.envelope()).createdAt } ?: -1L
                        val timestamp = maxOf(System.currentTimeMillis(), Math.addExact(latest, 1))
                        val body = JsonObject().apply {
                            addProperty("module", "RedSquare")
                            addProperty("request", RedSquareActionKind.LIKE.request)
                            add("data", JsonObject().apply { addProperty("signature", post.id) })
                        }
                        SaitoTransaction.create(wallet, body, timestamp,
                            (listOf(post.author) + post.recipients).distinct())
                    }
                    val fresh = requireNotNull(candidate)
                    val prepared = withContext(NonCancellable + Dispatchers.IO) {
                        journal(key).prepare(fresh, saved)
                    }
                    saved = prepared
                    peer.submit(fresh) { requireSelectedWalletAndBackend() }
                    Log.i(LIKE_LOG_TAG, "socket_enqueued node=${backend.domain} signature=${fresh.id}")
                    saved = withContext(NonCancellable) { journal(key).markSubmitted(fresh) }
            }
            withContext(Dispatchers.Main.immediate) {
                if (generations.containsKey(key)) actions[key]?.let { actions[key] = it.copy(candidate = candidate ?: it.candidate,
                    available = false, ready = true, failed = false, countsUnavailable = false) }
            }
        } catch (failure: Exception) {
            val failedCandidate = candidate ?: saved?.candidate
            failedCandidate?.let {
                Log.w(LIKE_LOG_TAG, "socket_enqueue_failed node=${backend.domain} signature=${it.id} error=${failure.javaClass.simpleName}")
            }
            throw failure
        }
    }

    private fun requireSelectedWalletAndBackend() {
        PublishingPolicies.requireAllowed(context)
        check(AndroidBackendStore(context).load() == backend &&
            nl.davidengelen.redsquare.AndroidWalletStore(context).loadExisting().publicKey == wallet.publicKey)
    }

    private fun archiveQuery(kind: String, signature: String) = JsonObject().apply {
        addProperty("request", "archive")
        add("data", JsonObject().apply {
            addProperty("request", "load"); addProperty("field1", kind); addProperty("sig", signature)
        })
    }

    /** A signed action may reach the node before its archive has indexed the candidate. */
    private suspend fun waitForExactReadback(timeoutMillis: Long, observed: suspend () -> Boolean): Boolean =
        withTimeoutOrNull(timeoutMillis) {
            var pauseMillis = 250L
            while (true) {
                delay(pauseMillis)
                if (observed()) return@withTimeoutOrNull true
                val maxPause = if (timeoutMillis > 10_000) 5_000L else 2_000L
                pauseMillis = (pauseMillis * 2).coerceAtMost(maxPause)
            }
            false
        } ?: false

    private companion object { const val LIKE_LOG_TAG = "RedSquareLike" }
}
