package nl.davidengelen.redsquare.chat

import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.*

/** Retains private edits through activity recreation without storing plaintext in a Bundle. */
internal class ChatComposerMemory : ViewModel() {
    var text: String? = null
    val writes = Mutex()
}

internal class ChatReplyActions {
    var replyTo by mutableStateOf<((PrivateChatMessage) -> Unit)?>(null)
}

@Composable
internal fun ChatComposer(wallet: SaitoWallet, backend: SaitoBackend, endpoint: String, contact: String,
    permitted: Boolean, replies: ChatReplyActions, onHistory: (List<ChatHistoryStore.Entry>) -> Unit) {
    val gifCredits = nl.davidengelen.redsquare.media.LocalGiphyAttributions.current
    val context = LocalContext.current.applicationContext
    val publishing = nl.davidengelen.redsquare.policy.LocalPublishingAgreement.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val activity = requireNotNull(LocalActivity.current) as ComponentActivity
    val memory = remember(activity, wallet.publicKey, backend, contact) {
        ViewModelProvider(activity)["chat:${wallet.publicKey}:${backend.id}:$contact", ChatComposerMemory::class.java]
    }
    val work = remember(wallet, backend, contact) { ChatWorkStore(context, wallet, backend, contact) }
    val history = remember(wallet, backend, contact) { ChatHistoryStore(context, wallet, backend, contact) }
    val scope = rememberCoroutineScope()
    val writes = memory.writes
    var saved by remember(work) { mutableStateOf(ChatWorkStore.Saved()) }
    var text by remember(work) { mutableStateOf("") }
    fun changeText(value: String) { text = value; memory.text = value }
    var ready by remember(work) { mutableStateOf(false) }
    var sending by remember(work) { mutableStateOf(false) }
    var preparingPhoto by remember(work) { mutableStateOf(false) }
    var failed by remember(work) { mutableStateOf(false) }
    val navigation = requireNotNull(nl.davidengelen.redsquare.feed.LocalPostNavigation.current)
    suspend fun persist(): Boolean = writes.withLock {
        if (sending || preparingPhoto) return@withLock false
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
        val guarded: suspend (suspend () -> Boolean) -> Boolean = { commit ->
            if (navigation.saveCurrent !== save || !ready || sending || preparingPhoto) false
            else if (persist() && navigation.saveCurrent === save) commit() else false
        }
        navigation.saveCurrent = save
        navigation.guardedCurrent = guarded
        onDispose {
            if (navigation.saveCurrent === save) navigation.saveCurrent = null
            if (navigation.guardedCurrent === guarded) navigation.guardedCurrent = null
        }
    }
    LaunchedEffect(work) {
        try {
            writes.withLock {
                saved = withContext(Dispatchers.IO) { work.load() }
                changeText(if (saved.frozen) saved.draft else memory.text ?: saved.draft)
                saved.transaction?.takeIf{saved.status!=ChatWorkStore.Status.QUEUED}?.let { candidate ->
                    onHistory(withContext(Dispatchers.IO) { history.save(listOf(candidate.envelope())).entries })
                }
                ready = true
            }
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
    LaunchedEffect(ready, text, preparingPhoto) {
        if (!ready || sending || preparingPhoto || saved.frozen) return@LaunchedEffect
        delay(300)
        persist()
    }
    val editable = ready && !sending && !saved.frozen && !navigation.transitioning
    val replyAction = remember(work) {
        { message: PrivateChatMessage ->
            if (ready && !sending && !saved.frozen && !navigation.transitioning &&
                message.author in setOf(wallet.publicKey, contact)) {
                privateReplyPrefix(message)?.let { prefix -> changeText(prefix + privateMessageBody(text)) }
            }
        }
    }
    SideEffect { replies.replyTo = if (permitted && editable) replyAction else null }
    DisposableEffect(replies) { onDispose { replies.replyTo = null } }
    val quote = remember(text) { privateReply(text) }
    val body = quote?.body ?: text
    val photo = remember(body) { chatPhoto(body) }
    val typed = photo?.text ?: body
    val retrying = saved.transaction != null && (saved.status == ChatWorkStore.Status.UNKNOWN ||
        (saved.status == ChatWorkStore.Status.SUBMITTED && typed.isBlank() && quote == null && photo == null))
    Column {
        if (saved.automatic && !saved.deliveryComplete)
            nl.davidengelen.redsquare.policy.QueuedPublishingAgreement(ready && !sending && !navigation.transitioning)
        when (saved.status) {
            ChatWorkStore.Status.QUEUED -> {
                val pending=remember(saved.transaction){PrivateChat.read(wallet,requireNotNull(saved.transaction).envelope(),contact)}
                val previewLabel=stringResource(R.string.chat_queued_preview)
                BoxWithConstraints {
                    val previewMaxHeight=maxHeight/2
                    Column(Modifier.heightIn(max=previewMaxHeight).verticalScroll(rememberScrollState())
                        .semantics{contentDescription=previewLabel}) {
                        ChatMessageBubble(pending.author,pending.text,pending.createdAt,outgoing=true,backend=backend,onQuote={})
                    }
                }
            }
            else -> Unit
        }
        InlineStatus(if (saved.status == ChatWorkStore.Status.QUEUED) stringResource(R.string.chat_queued) else null,
            actionLabel = stringResource(R.string.chat_cancel_queued),
            actionEnabled = ready&&!sending&&!navigation.transitioning,
            onAction = cancel@{
                if (saved.status != ChatWorkStore.Status.QUEUED || navigation.transitioning || sending) return@cancel
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
            })
        val deliverySummary = when (saved.status) {
            ChatWorkStore.Status.UNKNOWN -> stringResource(R.string.chat_send_unknown)
            ChatWorkStore.Status.SUBMITTED -> stringResource(R.string.chat_send_submitted)
            ChatWorkStore.Status.FORWARDED -> stringResource(R.string.chat_send_forwarded)
            ChatWorkStore.Status.AVAILABLE -> stringResource(R.string.chat_send_available)
            ChatWorkStore.Status.NONE -> if (ready && !failed && text.isNotBlank() && text == saved.draft)
                stringResource(R.string.status_saved_locally) else null
            ChatWorkStore.Status.QUEUED -> null
        }
        InlineStatus(deliverySummary, when (saved.status) {
            ChatWorkStore.Status.UNKNOWN -> StatusTone.WARNING
            ChatWorkStore.Status.AVAILABLE -> StatusTone.SUCCESS
            else -> StatusTone.INFO
        }, details = if (saved.transaction != null) listOf(stringResource(R.string.status_origin_node, backend.domain))
            else emptyList(), announce = saved.status != ChatWorkStore.Status.NONE)
        InlineStatus(if (failed) stringResource(when {
            !ready -> R.string.chat_work_read_error
            saved.automatic&&!saved.deliveryComplete -> R.string.chat_automatic_send_error
            else -> R.string.chat_send_error
        }) else null, StatusTone.ERROR)
        if (saved.status != ChatWorkStore.Status.QUEUED) {
            BoxWithConstraints {
                val previewMaxHeight=maxHeight/3
                val previewScroll=rememberScrollState()
                val previewLabel=stringResource(R.string.chat_composition_preview)
                Column {
                    Column((if(quote!=null || photo!=null)Modifier.heightIn(max=previewMaxHeight).verticalScroll(previewScroll) else Modifier)
                        .semantics{contentDescription=previewLabel}) {
                        if (quote != null) {
                            Text(quote.quote, style = MaterialTheme.typography.bodyMedium)
                            TextButton(enabled = editable, onClick = { if (!navigation.transitioning) changeText(body) }) {
                                Text(stringResource(R.string.chat_remove_reply))
                            }
                        }
                        ChatPhotoAttachment("${wallet.publicKey}:${backend.id}:$contact", body, backend, editable && permitted,
                            beforePicker = { !navigation.transitioning && persist() && !navigation.transitioning },
                            onPreparing = { preparingPhoto = it },
                            onChange = { if (!navigation.transitioning) changeText((quote?.prefix ?: "") + it) },
                            gifAttribution = saved.gifAttribution,
                            onSelectGif = { item ->
                                writes.withLock {
                                    if (!ready || sending || saved.frozen || !permitted || navigation.transitioning) false
                                    else {
                                        val next = composeChatGif(text, item.originalUrl)
                                        if (next == null) false
                                        else {
                                            withContext(NonCancellable) {
                                                saved = withContext(Dispatchers.IO) { work.assertSelected(); work.saveDraft(next, item.attribution) }
                                                changeText(next)
                                            }
                                            failed = false
                                            true
                                        }
                                    }
                                }
                            })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = typed, onValueChange = { value ->
                            if (!navigation.transitioning)
                                changeText((quote?.prefix ?: "") + (photo?.let { composeChatPhoto(it.source, value) } ?: value))
                        }, label = { Text(stringResource(R.string.chat_message)) },
                            modifier = Modifier.weight(1f), enabled = editable, maxLines = 4)
                        Button(enabled = permitted && ready && !sending && !preparingPhoto && !navigation.transitioning && saved.status!=ChatWorkStore.Status.QUEUED && (retrying || typed.isNotBlank() || photo != null),
                            onClick = send@ {
                                if (navigation.transitioning || sending || preparingPhoto || !ready) return@send
                                focus.clearFocus()
                                keyboard?.hide()
                                sending = true
                                val outgoing = text
                                val original = if (retrying) saved.transaction else null
                                scope.launch {
                                    try {
                                        if (!publishing.beforePublishing()) return@launch
                                        val candidate = writes.withLock {
                                            saved.gifAttribution?.let { credit ->
                                                chatPhoto(privateMessageBody(outgoing))?.source?.let { source ->
                                                    withContext(Dispatchers.IO) { requireNotNull(gifCredits).retain(mapOf(source to credit)) }
                                                }
                                            }
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
        }
    }
}
