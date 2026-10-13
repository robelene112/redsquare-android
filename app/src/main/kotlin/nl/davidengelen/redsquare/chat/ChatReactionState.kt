package nl.davidengelen.redsquare.chat

import android.content.Context
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.davidengelen.redsquare.saito.*

internal class ChatReactionState(context: Context, private val wallet: SaitoWallet,
    backend: SaitoBackend, private val contact: String, private val endpoint: String,
    private val scope: CoroutineScope) {
    private val store = ChatReactionStore(context, wallet, backend, contact)
    private val publication = Mutex()
    var saved by mutableStateOf(ChatReactionStore.Saved()); private set
    var ready by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var failed by mutableStateOf(false); private set
    val editable: Boolean get() = ready && !busy && saved.status !in
        listOf(ChatReactionStore.Status.UNKNOWN, ChatReactionStore.Status.SUBMITTED)
    val retryable: Boolean get() = ready && !busy && saved.transaction != null && saved.status in
        listOf(ChatReactionStore.Status.UNKNOWN, ChatReactionStore.Status.SUBMITTED)

    suspend fun load() {
        try { publishSaved { store.load() }; ready = true }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }

    private suspend fun publishSaved(operation: () -> ChatReactionStore.Saved) = withContext(Dispatchers.Main.immediate) {
        // Order reading/writing the durable snapshot together with its UI publication.
        publication.withLock { saved = withContext(Dispatchers.IO) { operation() } }
    }

    fun toggle(target: PrivateChatMessage) {
        if (!editable) return
        send {
            val candidate = withContext(Dispatchers.IO) {
                PrivateChatLike.create(wallet, contact, target, System.currentTimeMillis())
            }
            withContext(NonCancellable) { publishSaved { store.prepare(candidate) } }
            candidate
        }
    }

    fun retry() {
        if (!retryable) return
        val original = requireNotNull(saved.transaction)
        send {
            withContext(NonCancellable) { publishSaved { store.prepareRetry(original.id) } }
            original
        }
    }

    private fun send(prepare: suspend () -> SaitoTransaction) {
        busy = true
        scope.launch {
            try {
                val candidate = prepare()
                withContext(Dispatchers.IO) {
                    submitChatTransaction(wallet, endpoint, contact, candidate, ChatSubmissionCallbacks(
                        claim = { store.claim(candidate.id) },
                        assertSelected = { store.assertSelected() },
                        submitted = {
                            publishSaved { store.record(candidate.id, ChatReactionStore.Status.SUBMITTED) }
                        },
                        forwarded = {
                            publishSaved { store.record(candidate.id, ChatReactionStore.Status.FORWARDED) }
                        }))
                }
                failed = false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
}
