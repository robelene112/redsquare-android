package nl.davidengelen.redsquare.chat

import android.content.Context
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import nl.davidengelen.redsquare.saito.*

internal class ChatReactionState(context: Context, private val wallet: SaitoWallet,
    backend: SaitoBackend, private val contact: String, private val endpoint: String,
    private val scope: CoroutineScope) {
    private val store = ChatReactionStore(context, wallet, backend, contact)
    var saved by mutableStateOf(ChatReactionStore.Saved()); private set
    var ready by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var failed by mutableStateOf(false); private set
    val editable: Boolean get() = ready && !busy && saved.status !in
        listOf(ChatReactionStore.Status.UNKNOWN, ChatReactionStore.Status.SUBMITTED)

    suspend fun load() {
        try { saved = withContext(Dispatchers.IO) { store.load() }; ready = true }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }

    fun toggle(target: PrivateChatMessage) {
        if (!editable) return
        busy = true
        scope.launch {
            try {
                val candidate = withContext(Dispatchers.IO) {
                    PrivateChatLike.create(wallet, contact, target, System.currentTimeMillis())
                }
                saved = withContext(NonCancellable + Dispatchers.IO) { store.prepare(candidate) }
                withContext(Dispatchers.IO) {
                    submitChatTransaction(wallet, endpoint, contact, candidate, ChatSubmissionCallbacks(
                        claim = { store.claim(candidate.id) },
                        assertSelected = { store.assertSelected() },
                        submitted = {
                            val submitted = store.record(candidate.id, ChatReactionStore.Status.SUBMITTED)
                            withContext(Dispatchers.Main.immediate) { saved = submitted }
                        },
                        forwarded = {
                            val forwarded = store.record(candidate.id, ChatReactionStore.Status.FORWARDED)
                            withContext(Dispatchers.Main.immediate) { saved = forwarded }
                        }))
                }
                failed = false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
}
