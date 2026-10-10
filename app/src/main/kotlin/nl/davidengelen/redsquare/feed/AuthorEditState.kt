package nl.davidengelen.redsquare.feed

import androidx.compose.runtime.*
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.davidengelen.redsquare.saito.*

/** Explicit author edits retain their signed candidate until observed or deliberately discarded. */
internal class AuthorEditState(private val store: PostDraftStore, private val memory: PostComposerMemory,
    private val wallet: SaitoWallet, private val endpoint: String, private val original: FeedPost,
    private val scope: CoroutineScope) {
    private val writes = Mutex()
    var text by mutableStateOf("")
    var ready by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var error by mutableStateOf(false)
    var networkError by mutableStateOf(false)
    var conflict by mutableStateOf(false)
    var available by mutableStateOf(false)
    var candidate by mutableStateOf<SaitoTransaction?>(null)
    var reload by mutableIntStateOf(0)

    suspend fun load() {
        try {
            check(original.author == wallet.publicKey)
            val saved = withContext(Dispatchers.IO) { store.load() }
            candidate = saved.transaction
            available = saved.available
            text = if (saved.transaction != null) saved.text else memory.text ?: saved.text.ifEmpty { original.text }
            memory.text = text
            ready = true
            error = false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
    }

    suspend fun persist(): Boolean = writes.withLock {
        if (!ready || busy) return@withLock false
        try {
            withContext(NonCancellable + Dispatchers.IO) {
                store.save(if (available) PostDraftStore.Saved("") else PostDraftStore.Saved(text, candidate))
            }
            memory.text = if (available) null else text
            true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true; false }
    }

    fun close(onClose: () -> Unit) {
        if (busy) return
        if (!ready) { onClose(); return }
        scope.launch { if (persist()) onClose() }
    }

    fun edit(value: String) { text = value; memory.text = value }

    fun send() {
        if (!ready || busy || available || conflict || text.isBlank()) return
        busy = true
        error = false
        networkError = false
        scope.launch {
            try {
                val transaction = writes.withLock {
                    val prepared = candidate ?: withContext(Dispatchers.IO) {
                        check(original.author == wallet.publicKey)
                        val body = JsonObject().apply {
                            addProperty("module", "RedSquare"); addProperty("request", "edit tweet")
                            add("data", JsonObject().apply {
                                addProperty("tweet_id", original.id); addProperty("text", text)
                            })
                        }
                        val timestamp = maxOf(System.currentTimeMillis(), Math.addExact(original.edit?.createdAt ?: 0, 1))
                        SaitoTransaction.create(wallet, body, timestamp, original.recipients.distinct())
                    }
                    withContext(NonCancellable + Dispatchers.IO) { store.save(PostDraftStore.Saved(text, prepared)) }
                    candidate = prepared
                    prepared
                }
                try {
                    withContext(Dispatchers.IO) {
                        PostDeliveryAttempts.run(transaction.id) {
                            SaitoPeer(wallet, endpoint).use { peer ->
                                suspend fun observed(): Boolean {
                                    val post = peer.authorTarget(original.id)
                                    val edit = post?.edit ?: return false
                                    if (edit.id == transaction.id && post.author == wallet.publicKey) {
                                        withContext(NonCancellable) { check(store.confirmAvailable(transaction.id)) }
                                        withContext(Dispatchers.Main.immediate) { available = true }
                                        return true
                                    }
                                    if (edit.createdAt >= SaitoEnvelope.decode(transaction.envelope()).createdAt ||
                                        (edit.id != original.edit?.id && edit.createdAt > (original.edit?.createdAt ?: 0))) {
                                        withContext(Dispatchers.Main.immediate) { conflict = true }
                                        return true
                                    }
                                    return false
                                }
                                if (observed()) return@use
                                peer.authenticate()
                                val permitted = withContext(NonCancellable) {
                                    val saved = store.load()
                                    check(saved.transaction?.id == transaction.id)
                                    store.claim(saved)
                                }
                                if (!permitted) return@use
                                peer.submit(transaction)
                                for (attempt in 0 until 6) {
                                    if (observed()) break
                                    if (attempt < 5) delay(2_000)
                                }
                            }
                        }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { networkError = true }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { busy = false }
        }
    }
}

/** Missing content is not an edit or deletion acknowledgement. */
internal suspend fun SaitoPeer.authorTarget(target: String): FeedPost? {
    val query = JsonObject().apply {
        addProperty("request", "archive")
        add("data", JsonObject().apply {
            addProperty("request", "load"); addProperty("field1", "RedSquare")
            addProperty("sig", target); addProperty("flagged_ne", 1); addProperty("limit", 1)
        })
    }
    val page = RedSquareFeed.readTarget(request(query.toString().toByteArray(Charsets.UTF_8)).toString(Charsets.UTF_8), target)
    check(page.ignored == 0)
    return page.posts.singleOrNull()
}
