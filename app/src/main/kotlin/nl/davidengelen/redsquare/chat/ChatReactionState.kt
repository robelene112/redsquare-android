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
    private var restoreFailed by mutableStateOf(false)
    private var sendFailed by mutableStateOf(false)
    val failed: Boolean get() = restoreFailed || sendFailed
    val editable: Boolean get() = ready && !busy && saved.status !in
        listOf(ChatReactionStore.Status.UNKNOWN, ChatReactionStore.Status.SUBMITTED)
    val retryable: Boolean get() = ready && !busy && saved.transaction != null && saved.status in
        listOf(ChatReactionStore.Status.UNKNOWN, ChatReactionStore.Status.SUBMITTED)

    suspend fun load() {
        try { publishSaved { store.load() }; ready = true; restoreFailed = false }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { restoreFailed = true; ready = false }
    }

    private suspend fun publishSaved(operation: () -> ChatReactionStore.Saved) = withContext(Dispatchers.Main.immediate) {
        // Order reading/writing the durable snapshot together with its UI publication.
        publication.withLock { saved = withContext(Dispatchers.IO) { operation() } }
    }

    fun toggle(target: PrivateChatMessage, beforePublishing: suspend () -> Boolean) {
        if (!editable) return
        send(beforePublishing) {
            val candidate = withContext(Dispatchers.IO) {
                PrivateChatLike.create(wallet, contact, target, System.currentTimeMillis())
            }
            withContext(NonCancellable) { publishSaved { store.prepare(candidate) } }
            candidate
        }
    }

    fun retry(beforePublishing: suspend () -> Boolean) {
        if (!retryable) return
        val original = requireNotNull(saved.transaction)
        send(beforePublishing) {
            withContext(NonCancellable) { publishSaved { store.prepareRetry(original.id) } }
            original
        }
    }

    private fun send(beforePublishing: suspend () -> Boolean, prepare: suspend () -> SaitoTransaction) {
        busy = true
        scope.launch {
            try {
                if (!beforePublishing()) return@launch
                val candidate = prepare()
                withContext(Dispatchers.IO) {
                    submitChatTransaction(wallet, endpoint, contact, candidate, ChatSubmissionCallbacks(
                        claim = { store.claim(candidate.id) },
                        assertSelected = { store.assertPublishingAllowed() },
                        submitted = {
                            publishSaved { store.record(candidate.id, ChatReactionStore.Status.SUBMITTED) }
                        },
                        forwarded = {
                            publishSaved { store.record(candidate.id, ChatReactionStore.Status.FORWARDED) }
                        }))
                }
                sendFailed = false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { sendFailed = true }
            finally { busy = false }
        }
    }
}
