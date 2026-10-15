package nl.davidengelen.redsquare.chat

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.*

/** Retains private edits through activity recreation without storing plaintext in a Bundle. */
internal class ChatComposerMemory : ViewModel() {
    var text: String? = null
}

internal class ChatReplyActions {
    var replyTo by mutableStateOf<((PrivateChatMessage) -> Unit)?>(null)
}

@Composable
internal fun ChatComposer(wallet: SaitoWallet, backend: SaitoBackend, endpoint: String, contact: String,
    permitted: Boolean, replies: ChatReplyActions, onHistory: (List<ChatHistoryStore.Entry>) -> Unit) {
    val context = LocalContext.current.applicationContext
    val activity = requireNotNull(LocalActivity.current) as ComponentActivity
    val memory = remember(activity, wallet.publicKey, backend, contact) {
        ViewModelProvider(activity)["chat:${wallet.publicKey}:${backend.id}:$contact", ChatComposerMemory::class.java]
    }
    val work = remember(wallet, backend, contact) { ChatWorkStore(context, wallet, backend, contact) }
    val history = remember(wallet, backend, contact) { ChatHistoryStore(context, wallet, backend, contact) }
    val scope = rememberCoroutineScope()
    val writes = remember(work) { Mutex() }
    var saved by remember(work) { mutableStateOf(ChatWorkStore.Saved()) }
    var text by remember(work) { mutableStateOf("") }
    fun changeText(value: String) { text = value; memory.text = value }
    var ready by remember(work) { mutableStateOf(false) }
    var sending by remember(work) { mutableStateOf(false) }
    var failed by remember(work) { mutableStateOf(false) }
    val navigation = requireNotNull(nl.davidengelen.redsquare.feed.LocalPostNavigation.current)
    suspend fun persist(): Boolean = writes.withLock {
        if (sending) return@withLock false
        // An unreadable composer cannot edit or replace the retained work.
        if (!ready || saved.frozen) return@withLock true
        try {
            saved = withContext(NonCancellable + Dispatchers.IO) { work.saveDraft(text) }
            failed = false
            true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true; false }
    }
    DisposableEffect(work, navigation) {
        val save: suspend () -> Boolean = { persist() }
        navigation.saveCurrent = save
        onDispose { if (navigation.saveCurrent === save) navigation.saveCurrent = null }
    }
    LaunchedEffect(work) {
        try {
            saved = withContext(Dispatchers.IO) { work.load() }
            changeText(if (saved.frozen) saved.draft else memory.text ?: saved.draft)
            saved.transaction?.takeIf{saved.status!=ChatWorkStore.Status.QUEUED}?.let { candidate ->
                onHistory(withContext(Dispatchers.IO) { history.save(listOf(candidate.envelope())).entries })
            }
            ready = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    LaunchedEffect(work,ready,sending) {
        ChatWorkStore.changes.collect {
            if(ready&&!sending)writes.withLock {
                try {
                    val previous=saved
                    saved=withContext(Dispatchers.IO){work.load()}
                    if(saved.transaction!=null&&saved.status!=ChatWorkStore.Status.QUEUED)
                        onHistory(withContext(Dispatchers.IO){history.load().entries})
                    if(saved.deliveryComplete&&!previous.deliveryComplete&&saved.transaction?.id==previous.transaction?.id)
                        failed=false
                }catch(cancelled:CancellationException){throw cancelled}
                catch(_:Exception){ready=false;failed=true}
            }
        }
    }
    LaunchedEffect(ready, text) {
        if (!ready || sending || saved.frozen) return@LaunchedEffect
        delay(300)
        persist()
    }
    val editable = ready && !sending && !saved.frozen
    val replyAction = remember(work) {
        { message: PrivateChatMessage ->
            if (ready && !sending && !saved.frozen &&
                message.author in setOf(wallet.publicKey, contact)) {
                privateReplyPrefix(message)?.let { prefix -> changeText(prefix + privateMessageBody(text)) }
            }
        }
    }
    SideEffect { replies.replyTo = if (permitted && editable) replyAction else null }
    DisposableEffect(replies) { onDispose { replies.replyTo = null } }
    val quote = remember(text) { privateReply(text) }
    val typed = quote?.body ?: text
    val retrying = saved.transaction != null && (saved.status == ChatWorkStore.Status.UNKNOWN ||
        (saved.status == ChatWorkStore.Status.SUBMITTED && typed.isBlank() && quote == null))
    Column {
        when (saved.status) {
            ChatWorkStore.Status.QUEUED -> {
                val pending=remember(saved.transaction){PrivateChat.read(wallet,requireNotNull(saved.transaction).envelope(),contact)}
                ChatMessageBubble(pending.author,pending.text,pending.createdAt,outgoing=true,onQuote={})
                Text(stringResource(R.string.chat_queued))
                TextButton(enabled=ready&&!sending,onClick={
                    sending=true
                    scope.launch {
                        try {
                            writes.withLock {
                                withContext(NonCancellable) {
                                    saved=withContext(Dispatchers.IO){work.cancelQueued(requireNotNull(saved.transaction).id)}
                                    changeText(saved.draft)
                                }
                            }
                            failed=false
                        }catch(cancelled:CancellationException){throw cancelled}
                        catch(_:Exception){failed=true}
                        finally{sending=false}
                    }
                }){Text(stringResource(R.string.chat_cancel_queued))}
            }
            ChatWorkStore.Status.UNKNOWN -> Text(stringResource(R.string.chat_send_unknown))
            ChatWorkStore.Status.SUBMITTED -> Text(stringResource(R.string.chat_send_submitted))
            ChatWorkStore.Status.FORWARDED -> Text(stringResource(R.string.chat_send_forwarded))
            ChatWorkStore.Status.AVAILABLE -> Text(stringResource(R.string.chat_send_available))
            ChatWorkStore.Status.NONE -> Unit
        }
        if (failed) Text(stringResource(when {
            !ready -> R.string.chat_work_read_error
            saved.automatic&&!saved.deliveryComplete -> R.string.chat_automatic_send_error
            else -> R.string.chat_send_error
        }))
        if (quote != null) {
            Text(quote.quote, style = MaterialTheme.typography.bodyMedium)
            TextButton(enabled = editable, onClick = { changeText(typed) }) {
                Text(stringResource(R.string.chat_remove_reply))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = typed, onValueChange = { changeText((quote?.prefix ?: "") + it) }, label = { Text(stringResource(R.string.chat_message)) },
                modifier = Modifier.weight(1f), enabled = editable)
            Button(enabled = permitted && ready && !sending && saved.status!=ChatWorkStore.Status.QUEUED && (retrying || typed.isNotBlank()),
                onClick = {
                    sending = true
                    val outgoing = text
                    val original = if (retrying) saved.transaction else null
                    scope.launch {
                        try {
                            val candidate = writes.withLock {
                                val prepared = original ?: withContext(Dispatchers.IO) {
                                    val mentioned = privateReply(outgoing)?.author?.let { author ->
                                        require(author in setOf(wallet.publicKey, contact))
                                        listOf(author)
                                    } ?: emptyList()
                                    PrivateChat.create(wallet, contact, outgoing, System.currentTimeMillis(), mentioned = mentioned)
                                }
                                withContext(NonCancellable) {
                                    saved = withContext(Dispatchers.IO) {
                                        if (original == null) work.prepare(prepared,queued=true) else work.prepareRetry(prepared.id)
                                    }
                                    if (original == null) changeText("")
                                }
                                if(saved.status!=ChatWorkStore.Status.QUEUED)
                                    onHistory(withContext(Dispatchers.IO) { history.save(listOf(prepared.envelope())).entries })
                                prepared
                            }
                            withContext(Dispatchers.IO) {
                                submitPrivateMessage(context,wallet,backend,endpoint,contact,candidate){progress,retained->
                                    withContext(Dispatchers.Main.immediate){saved=progress;onHistory(retained)}
                                }
                            }
                            failed = false
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) {
                            try {
                                saved=withContext(Dispatchers.IO){work.load()}
                                if(saved.status==ChatWorkStore.Status.QUEUED)
                                    withContext(NonCancellable+Dispatchers.IO){ChatQueueScheduling.enqueue(context,wallet,backend,endpoint)}
                                failed=saved.status!=ChatWorkStore.Status.QUEUED&&
                                    !(original!=null&&saved.transaction?.id==original.id&&saved.deliveryComplete)
                            }
                            catch(_:Exception){ready=false;failed=true}
                        }
                        finally { sending = false }
                    }
                }) { Text(stringResource(if (retrying) R.string.retry else R.string.chat_send)) }
        }
    }
}
