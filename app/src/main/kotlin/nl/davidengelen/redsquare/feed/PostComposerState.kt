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
import nl.davidengelen.redsquare.saito.RedSquareFeed
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
            ready = true
            error = false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
    }
    suspend fun persist(): Boolean = writes.withLock {
        if (!ready) return@withLock false
        try {
            val snapshot = if (available) PostDraftStore.Saved("") else PostDraftStore.Saved(text, transaction, queued)
            withContext(NonCancellable) {
                withContext(Dispatchers.IO) { store.save(snapshot) }
                memory.text = snapshot.text
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
                    withContext(NonCancellable + Dispatchers.IO) { store.save(snapshot) }
                    prepared
                }
                try { withContext(Dispatchers.IO) {
                    SaitoPeer(wallet, endpoint).use { peer ->
                        val criteria = JsonObject().apply {
                            addProperty("request", "load"); addProperty("field1", "RedSquare")
                            addProperty("sig", candidate.id); addProperty("flagged_ne", 1)
                        }
                        val query = JsonObject().apply { addProperty("request", "archive"); add("data", criteria) }
                        suspend fun observed(): Boolean {
                            val response = peer.request(query.toString().toByteArray(Charsets.UTF_8)).toString(Charsets.UTF_8)
                            return RedSquareFeed.readTarget(response, candidate.id).posts
                                .any { it.author == owner && it.text == text }
                        }
                        if (retrying && observed()) {
                            withContext(Dispatchers.Main.immediate) { available = true; queued = false }
                            return@use
                        }
                        peer.authenticate()
                        // A crash after this write may have transmitted. It is no longer cancellable queued work.
                        withContext(Dispatchers.Main.immediate) {
                            writes.withLock {
                                withContext(NonCancellable + Dispatchers.IO) {
                                    try { store.save(PostDraftStore.Saved(text, candidate, false)) }
                                    catch (failure: Exception) { throw DraftPersistenceFailure(failure) }
                                }
                                queued = false
                            }
                        }
                        peer.submit(candidate)
                        withContext(Dispatchers.Main.immediate) { submitted = true }
                        for (attempt in 0 until 6) {
                            if (observed()) {
                                withContext(Dispatchers.Main.immediate) { available = true }
                                break
                            }
                            if (attempt < 5) delay(2_000)
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
        if (!ready || saving || sending || !queued) return
        saving = true
        scope.launch {
            try {
                writes.withLock {
                    withContext(NonCancellable) {
                        withContext(Dispatchers.IO) { store.save(PostDraftStore.Saved(text)) }
                        transaction = null
                        queued = false
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
