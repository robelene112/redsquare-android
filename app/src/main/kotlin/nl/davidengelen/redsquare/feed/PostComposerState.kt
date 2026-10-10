package nl.davidengelen.redsquare.feed

import androidx.compose.runtime.*
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.saito.FeedPost
import nl.davidengelen.redsquare.saito.SaitoPeer
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet

/** Composer operations live for this visible wallet/backend session. */
internal class PostComposerState(
    private val store: PostDraftStore,
    private val memory: PostComposerMemory,
    private val wallet: SaitoWallet,
    private val endpoint: String,
    private val parent: FeedPost?,
    private val scope: CoroutineScope,
) {
    private val owner = wallet.publicKey
    private val writes = Mutex()
    var text by mutableStateOf("")
    var ready by mutableStateOf(false)
    var saving by mutableStateOf(false)
    var error by mutableStateOf(false)
    var networkError by mutableStateOf(false)
    var validationError by mutableStateOf(false)
    var transaction by mutableStateOf<SaitoTransaction?>(null)
    var queued by mutableStateOf(false)
    var queueSaved by mutableStateOf(false)
    var submitted by mutableStateOf(false)
    var available by mutableStateOf(false)
    var sending by mutableStateOf(false)
    var reload by mutableIntStateOf(0)
    var confirmClear by mutableStateOf(false)
    suspend fun load() {
        try {
            val saved = withContext(Dispatchers.IO) { store.load() }
            text = if (saved.transaction != null) saved.text else memory.text ?: saved.text
            memory.text = text
            transaction = saved.transaction
            queued = saved.queued
            queueSaved = saved.queued
            available = saved.available
            ready = true
            error = false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
    }
    suspend fun persist(): Boolean = writes.withLock {
        if (!ready) return@withLock false
        try {
            val snapshot = PostDraftStore.Saved(text, transaction, queued, available)
            withContext(NonCancellable) {
                val persisted = withContext(Dispatchers.IO) { store.saveComposer(snapshot) }
                memory.text = persisted.text
                queueSaved = persisted.queued
            }
            error = false
            true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true; false }
    }
    fun saveAndClose(onClose: () -> Unit) {
        if (!ready || saving || sending) return
        saving = true
        scope.launch {
            try {
                if (persist()) onClose()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { saving = false }
        }
    }
    fun send() {
        if (!ready || saving || sending || text.isBlank()) return
        val retrying = transaction != null
        networkError = false
        validationError = false
        sending = true
        scope.launch {
            try {
                val candidate = writes.withLock {
                    val prepared = transaction ?: withContext(Dispatchers.IO) {
                        val body = JsonObject().apply {
                            addProperty("module", "RedSquare"); addProperty("request", "create tweet")
                            add("data", JsonObject().apply {
                                addProperty("text", text)
                                parent?.let { addProperty("parent_id", it.id); addProperty("thread_id", it.threadId) }
                            })
                        }
                        val recipients = parent?.let { (listOf(it.author) + it.recipients).distinct() }.orEmpty()
                        SaitoTransaction.create(wallet, body, System.currentTimeMillis(), recipients)
                    }
                    if (transaction == null) queued = true
                    transaction = prepared
                    val snapshot = PostDraftStore.Saved(text, prepared, queued)
                    val persisted = withContext(NonCancellable + Dispatchers.IO) { store.saveComposer(snapshot) }
                    queued = persisted.queued
                    queueSaved = persisted.queued
                    available = persisted.available
                    prepared
                }
                try { withContext(Dispatchers.IO) {
                    PostDeliveryAttempts.run(candidate.id) {
                    SaitoPeer(wallet, endpoint).use { peer ->
                        if (retrying && peer.hasPost(candidate, owner, text)) {
                            try { check(store.confirmAvailable(candidate.id)) }
                            catch (failure: Exception) { throw DraftPersistenceFailure(failure) }
                            withContext(Dispatchers.Main.immediate) { available = true; queued = false }
                            return@use
                        }
                        peer.authenticate()
                        // A crash after this write may have transmitted. It is no longer cancellable queued work.
                        val permitted = withContext(Dispatchers.Main.immediate) {
                            writes.withLock {
                                val claimed = withContext(NonCancellable + Dispatchers.IO) {
                                    try {
                                        val saved = store.load()
                                        check(saved.transaction?.id == candidate.id)
                                        if (saved.available) false
                                        else if (saved.queued) store.claim(saved)
                                        else true
                                    }
                                    catch (failure: Exception) { throw DraftPersistenceFailure(failure) }
                                }
                                if (claimed) queued = false
                                claimed
                            }
                        }
                        if (!permitted) return@use
                        peer.submit(candidate)
                        withContext(Dispatchers.Main.immediate) { submitted = true }
                        for (attempt in 0 until 6) {
                            if (peer.hasPost(candidate, owner, text)) {
                                try { check(store.confirmAvailable(candidate.id)) }
                                catch (failure: Exception) { throw DraftPersistenceFailure(failure) }
                                withContext(Dispatchers.Main.immediate) { available = true }
                                break
                            }
                            if (attempt < 5) delay(2_000)
                        }
                    }
                    }
                } } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: DraftPersistenceFailure) { throw failure }
                catch (_: Exception) { networkError = true }
                error = false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: SaitoTransaction.InvalidMessage) { validationError = true }
            catch (_: Exception) { error = true }
            finally { sending = false }
        }
    }
    fun cancelQueued() {
        if (!ready || saving || sending || !queued || !queueSaved) return
        saving = true
        scope.launch {
            try {
                writes.withLock {
                    withContext(NonCancellable) {
                        withContext(Dispatchers.IO) {
                            val saved = store.load()
                            check(saved.queued && saved.transaction?.id == transaction?.id)
                            check(store.update(saved, PostDraftStore.Saved(text)))
                        }
                        transaction = null
                        queued = false
                        queueSaved = false
                        networkError = false
                        memory.text = text
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { saving = false }
        }
    }
    fun clearAndClose(onClose: () -> Unit) {
        if (!ready || saving || sending || transaction == null || available) return
        confirmClear = false
        saving = true
        scope.launch {
            try {
                writes.withLock {
                    withContext(NonCancellable) {
                        withContext(Dispatchers.IO) { store.save(PostDraftStore.Saved("")) }
                        memory.text = ""
                        text = ""
                        transaction = null
                    }
                }
                onClose()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { saving = false }
        }
    }
    fun edit(value: String) {
        text = value
        memory.text = value
        validationError = false
    }
    private class DraftPersistenceFailure(cause: Exception) : RuntimeException(cause)
}
