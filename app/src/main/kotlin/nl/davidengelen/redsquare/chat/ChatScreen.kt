package nl.davidengelen.redsquare.chat

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.moderation.LocalPersonalControls
import nl.davidengelen.redsquare.moderation.PersonalControlsStatus
import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.saito.*

/** Private Chat has its own navigation and verified history, sharing only the active Saito foundation. */
@Composable
internal fun ChatScreen(wallet: SaitoWallet, endpoint: String, backend: SaitoBackend, modifier: Modifier,
    onWalletMetadata: suspend (SaitoWallet, SaitoWallet) -> Unit, onBack: () -> Unit) {
    nl.davidengelen.redsquare.NotificationPermissionPrompt()
    val personal = LocalPersonalControls.current
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val navigation = requireNotNull(nl.davidengelen.redsquare.feed.LocalPostNavigation.current)
    var newContact by remember(wallet.publicKey) { mutableStateOf("") }
    var savingContact by remember(wallet.publicKey) { mutableStateOf(false) }
    var contactFailed by remember(wallet.publicKey) { mutableStateOf(false) }
    var contacts by remember(wallet, backend) { mutableStateOf<List<String>>(emptyList()) }
    var contactsFailed by remember(wallet) { mutableStateOf(false) }
    var counterparty by remember(wallet.publicKey, backend) { mutableStateOf<String?>(null) }
    var entries by remember(wallet.publicKey, backend, counterparty) { mutableStateOf<List<ChatHistoryStore.Entry>>(emptyList()) }
    var loading by remember(counterparty) { mutableStateOf(false) }
    var failed by remember(counterparty) { mutableStateOf(false) }
    var incomplete by remember(wallet, backend, counterparty) { mutableStateOf(false) }
    var cacheFailed by remember(counterparty) { mutableStateOf(false) }
    var reactionHistoryFailed by remember(wallet, backend, counterparty) { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var hasOlder by remember(wallet, backend, counterparty) { mutableStateOf(false) }
    var olderLoading by remember(wallet, backend, counterparty) { mutableStateOf(false) }
    var olderRequest by remember(wallet, backend, counterparty) { mutableIntStateOf(0) }
    var olderCursor by remember(wallet, backend, counterparty) { mutableStateOf<Long?>(null) }
    var closing by remember(wallet.publicKey, backend, counterparty) { mutableStateOf(false) }
    val reactionState = remember(wallet, backend, counterparty, endpoint) {
        counterparty?.let { ChatReactionState(context, wallet, backend, it, endpoint, scope) }
    }
    LaunchedEffect(reactionState) { reactionState?.load() }
    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            try {
                if (navigation.save()) {
                    if (counterparty == null) onBack() else counterparty = null
                }
            } finally { closing = false }
        }
    }
    BackHandler { close() }

    suspend fun retainReactions(page: PrivateHistoryPage): Boolean {
        if (page.reactions.isEmpty()) return true
        val retained = try {
            withContext(Dispatchers.IO) {
                page.reactions.forEach { (contact, events) ->
                    val store = ChatReactionStore(context, wallet, backend, contact)
                    events.forEach { store.receive(it.envelope) }
                }
            }
            true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
        reactionState?.load()
        return retained
    }

    suspend fun retainHistory(cache: ChatHistoryStore): ChatHistoryStore.Read? {
        val originals = entries.map { it.envelope }
        return try {
            val saved = withContext(Dispatchers.IO) { cache.save(originals) }
            entries = ChatHistoryStore.merge(entries, saved.entries)
            cacheFailed = false
            saved
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { cacheFailed = true; null }
    }

    LaunchedEffect(wallet, backend, endpoint, counterparty, refresh) {
        try {
            val retained = withContext(Dispatchers.IO) { ChatHistoryStore.contacts(context, wallet, backend) }
            var discoveryFailed = retained.unavailable
            var reactionRetentionFailed = false
            if (retained.unreadable) incomplete = true
            val portable = withContext(Dispatchers.IO) { wallet.chatContacts() }
            contacts = (contacts + portable + retained.accounts).distinct()
            val known = portable.map { PrivateChat.conversationId(wallet.publicKey, it) }.toSet()
            val missing = withContext(Dispatchers.IO) { wallet.chatHistoryIds().filter { it !in known } }
            if (missing.isNotEmpty()) {
                var updated = wallet
                SaitoPeer(wallet, endpoint).use { peer ->
                    require("archive" in peer.services())
                    for (group in missing) {
                        try {
                            val recovered = withContext(Dispatchers.IO) { loadPrivateHistory(peer, wallet, group) }
                            incomplete = incomplete || recovered.incomplete
                            if (!retainReactions(recovered)) reactionRetentionFailed = true
                            for ((address, messages) in recovered.messages.groupBy { it.counterparty }) {
                                withContext(Dispatchers.IO) {
                                    ChatHistoryStore(context, wallet, backend, address).save(messages.map { it.envelope })
                                }
                                updated = updated.withChatContact(address)
                            }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { discoveryFailed = true }
                    }
                }
                if (updated !== wallet) onWalletMetadata(wallet, updated)
            }
            contactsFailed = discoveryFailed
            if (counterparty == null) reactionHistoryFailed = reactionRetentionFailed
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { contactsFailed = true }
    }
    LaunchedEffect(wallet, backend, endpoint, counterparty, refresh) {
        if (counterparty != null) return@LaunchedEffect
        try {
            coroutineScope {
                val updates = Channel<Unit>(Channel.CONFLATED)
                val receiver = launch {
                    for (ignored in updates) {
                        val retained = withContext(Dispatchers.IO) { ChatHistoryStore.contacts(context, wallet, backend) }
                        contacts = (contacts + retained.accounts).distinct()
                        if (retained.unavailable) contactsFailed = true
                        if (retained.unreadable) incomplete = true
                    }
                }
                try {
                    SaitoPeer(wallet, endpoint, onIncoming = { bytes ->
                        receivePrivateChat(context, wallet, backend, bytes)
                        updates.trySend(Unit)
                        true // Even if the UI closes, original authenticated history discovers the contact on reopening.
                    }).use { peer ->
                        require("relay" in peer.services())
                        peer.awaitDisconnection()
                        contactsFailed = true
                    }
                } finally { updates.close(); receiver.cancelAndJoin() }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { contactsFailed = true }
    }
    LaunchedEffect(wallet, endpoint, counterparty, refresh) {
        val contact = counterparty ?: return@LaunchedEffect
        loading = true
        val cache = ChatHistoryStore(context, wallet, backend, contact)
        try {
            val cached = withContext(Dispatchers.IO) { cache.load() }
            val savedIds = cached.entries.map { it.message.id }.toSet()
            cacheFailed = entries.any { it.message.id !in savedIds }
            entries = ChatHistoryStore.merge(entries, cached.entries)
            incomplete = incomplete || cached.incomplete
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { cacheFailed = true }
        try {
            coroutineScope {
                val updates = Channel<Unit>(Channel.CONFLATED)
                val receiver = launch {
                    for (ignored in updates) {
                        try {
                            val history = withContext(Dispatchers.IO) { cache.load() }
                            entries = ChatHistoryStore.merge(entries, history.entries)
                            if (history.incomplete) incomplete = true
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { cacheFailed = true }
                        reactionState?.load()
                    }
                }
                try {
                    SaitoPeer(wallet, endpoint, onIncoming = { bytes ->
                        val saved = receivePrivateChat(context, wallet, backend, bytes)
                        if (saved == contact) {
                            updates.trySend(Unit)
                        }
                        true // Original authenticated message or metadata is durably saved before acknowledgement.
                    }).use { peer ->
                        require(setOf("archive", "relay").all { it in peer.services() })
                        try {
                            val page = withContext(Dispatchers.IO) {
                                loadPrivateHistory(peer, wallet, PrivateChat.conversationId(wallet.publicKey, contact))
                            }
                            reactionHistoryFailed = !retainReactions(page)
                            hasOlder = page.hasOlder
                            olderCursor = page.oldestTimestamp
                            entries = ChatHistoryStore.merge(entries, page.messages.map { ChatHistoryStore.Entry(it.envelope, it.message) })
                            incomplete = incomplete || page.incomplete
                            retainHistory(cache)?.let { saved ->
                                incomplete = page.incomplete || saved.incomplete
                            }
                            failed = false
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { failed = true }
                        finally { loading = false }
                        // Keep the authenticated selected connection alive after loading its history.
                        peer.awaitDisconnection()
                        failed = true
                    }
                } finally { updates.close(); receiver.cancelAndJoin() }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }

    LaunchedEffect(wallet, backend, endpoint, counterparty, olderRequest) {
        val contact = counterparty ?: return@LaunchedEffect
        if (olderRequest == 0) return@LaunchedEffect
        val cutoff = olderCursor ?: return@LaunchedEffect
        val cache = ChatHistoryStore(context, wallet, backend, contact)
        try {
            val page = withContext(Dispatchers.IO) {
                SaitoPeer(wallet, endpoint).use { peer ->
                    require("archive" in peer.services())
                    loadPrivateHistory(peer, wallet, PrivateChat.conversationId(wallet.publicKey, contact), cutoff)
                }
            }
            require(page.messages.all { it.message.createdAt < cutoff })
            reactionHistoryFailed = !retainReactions(page) || reactionHistoryFailed
            hasOlder = page.hasOlder
            olderCursor = page.oldestTimestamp
            entries = ChatHistoryStore.merge(entries, page.messages.map { ChatHistoryStore.Entry(it.envelope, it.message) })
            incomplete = incomplete || page.incomplete
            retainHistory(cache)?.let { saved ->
                incomplete = incomplete || saved.incomplete
            }
            failed = false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { olderLoading = false }
    }

    Column(modifier.fillMaxSize()) {
        Row {
            TextButton(enabled = !closing, onClick = { close() }) {
                Text(stringResource(R.string.browse_back))
            }
            Text(stringResource(R.string.chat_title), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = { refresh++ }, enabled = !loading) { Text(stringResource(R.string.feed_refresh)) }
        }
        Text(stringResource(R.string.chat_private), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.chat_available_history), style = MaterialTheme.typography.bodySmall)
        PersonalControlsStatus(personal)
        if (contactsFailed || failed) Text(stringResource(R.string.chat_load_error))
        if (cacheFailed) Text(stringResource(R.string.chat_cache_error))
        if (reactionHistoryFailed) Text(stringResource(R.string.chat_reaction_cache_error))
        if (incomplete) Text(stringResource(R.string.chat_incomplete))
        val contact = counterparty
        if (contact == null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(value = newContact, onValueChange = { newContact = it; contactFailed = false },
                    label = { Text(stringResource(R.string.chat_public_key)) }, singleLine = true,
                    enabled = !savingContact, modifier = Modifier.weight(1f))
                TextButton(enabled = !savingContact && personal?.ready == true && newContact.isNotBlank(),
                    onClick = {
                        val address = newContact.trim()
                        savingContact = true
                        contactFailed = false
                        scope.launch {
                            try {
                                val updated = withContext(Dispatchers.IO) { wallet.withChatContact(address) }
                                onWalletMetadata(wallet, updated)
                                counterparty = address
                                newContact = ""
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { contactFailed = true }
                            finally { savingContact = false }
                        }
                    }) { Text(stringResource(R.string.chat_open)) }
            }
            if (contactFailed) Text(stringResource(R.string.chat_contact_error))
            if (contacts.isEmpty() && !contactsFailed) Text(stringResource(R.string.chat_empty))
            LazyColumn(Modifier.weight(1f)) {
                items(contacts, key = { it }) { key ->
                    TextButton(onClick = {
                        savingContact = true
                        contactFailed = false
                        scope.launch {
                            try {
                                if (key !in wallet.chatContacts()) {
                                    val updated = withContext(Dispatchers.IO) { wallet.withChatContact(key) }
                                    onWalletMetadata(wallet, updated)
                                }
                                counterparty = key
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { contactFailed = true }
                            finally { savingContact = false }
                        }
                    }, enabled = !savingContact && personal?.ready == true) {
                        Column {
                            Text(registeredIdentity(key).name)
                            Text(key, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        } else {
            Text(contact, style = MaterialTheme.typography.labelSmall)
            if (loading) Text(stringResource(R.string.chat_loading))
            if (hasOlder) TextButton(enabled = !loading && !olderLoading && olderCursor != null, onClick = {
                olderLoading = true
                olderRequest++
            }) { Text(stringResource(R.string.chat_load_older)) }
            val visible = entries.map { it.message }.filter { message -> personal?.ready == true &&
                (message.author == wallet.publicKey || !personal.policy.hidesIncomingChat(message.author)) }
            if (visible.isEmpty() && !loading && !failed) Text(stringResource(R.string.chat_no_messages))
            val replies = remember(wallet, backend, contact) { ChatReplyActions() }
            val reactions = requireNotNull(reactionState)
            val messageList = rememberLazyListState()
            LazyColumn(Modifier.weight(1f), state = messageList) {
                items(visible, key = { it.id }) { message ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        var copyFailed by remember(message.id) { mutableStateOf(false) }
                        Text(registeredIdentity(message.author).name, style = MaterialTheme.typography.labelSmall)
                        PrivateMessageText(message.text) { reference ->
                            val index = visible.indexOfFirst { it.author + it.createdAt == reference }
                            if (index >= 0) scope.launch { messageList.animateScrollToItem(index) }
                        }
                        val replyPrefix = remember(message) { privateReplyPrefix(message) }
                        val likedBy = reactions.saved.likedBy(message)
                        if (likedBy.isNotEmpty()) Text(pluralStringResource(R.plurals.chat_likes, likedBy.size, likedBy.size))
                        Row {
                            TextButton(enabled = personal?.ready == true && reactions.editable,
                                onClick = { reactions.toggle(message) }) {
                                Text(stringResource(if (wallet.publicKey in likedBy) R.string.chat_unlike else R.string.chat_like))
                            }
                            TextButton(enabled = replies.replyTo != null && replyPrefix != null, onClick = { replies.replyTo?.invoke(message) }) {
                                Text(stringResource(R.string.post_reply))
                            }
                            TextButton(onClick = { copyFailed = !copyPrivateMessage(context, message.text) }) {
                                Text(stringResource(R.string.chat_copy))
                            }
                        }
                        if (copyFailed) Text(stringResource(R.string.chat_copy_error))
                    }
                    HorizontalDivider()
                }
            }
            when (reactions.saved.status) {
                ChatReactionStore.Status.UNKNOWN -> Text(stringResource(R.string.chat_reaction_unknown))
                ChatReactionStore.Status.SUBMITTED -> Text(stringResource(R.string.chat_reaction_submitted))
                ChatReactionStore.Status.FORWARDED -> Text(stringResource(R.string.chat_reaction_forwarded))
                ChatReactionStore.Status.NONE -> Unit
            }
            if (reactions.failed && !reactionHistoryFailed) Text(stringResource(R.string.chat_reaction_error))
            if (reactions.saved.status in listOf(ChatReactionStore.Status.UNKNOWN, ChatReactionStore.Status.SUBMITTED)) {
                TextButton(enabled = personal?.ready == true && reactions.retryable, onClick = { reactions.retry() }) {
                    Text(stringResource(R.string.chat_reaction_retry))
                }
            }
            ChatComposer(wallet, backend, endpoint, contact, personal?.ready == true, replies) { saved ->
                entries = ChatHistoryStore.merge(entries, saved)
            }
        }
    }
}

private data class PrivateHistoryPage(val messages: List<ReceivedPrivateChat>,
    val reactions: Map<String, List<PrivateChatLikeEvent>>, val incomplete: Boolean,
    val hasOlder: Boolean, val oldestTimestamp: Long?)

private suspend fun loadPrivateHistory(peer: SaitoPeer, wallet: SaitoWallet,
    group: String, before: Long = System.currentTimeMillis()): PrivateHistoryPage {
    val criteria = JsonObject().apply {
        addProperty("request", "load")
        addProperty("field3", group)
        addProperty("limit", 50)
        addProperty("created_earlier_than", before)
    }
    val request = JsonObject().apply { addProperty("request", "archive"); add("data", criteria) }
    val rows = JsonParser.parseString(peer.request(request.toString().toByteArray(Charsets.UTF_8))
        .toString(Charsets.UTF_8)).asJsonArray
    require(rows.size() <= 50)
    var ignored = 0
    val reactions = mutableMapOf<String, MutableList<PrivateChatLikeEvent>>()
    var oldestTimestamp: Long? = null
    val verified = rows.mapNotNull { row ->
        try {
            val envelope = row.asJsonObject.get("tx")
            require(envelope.isJsonPrimitive && envelope.asJsonPrimitive.isString)
            // Pagination can advance without a missing decryption key, but never from unsigned
            // archive timestamps or an original belonging to another private conversation.
            val signed = SaitoEnvelope.decodePayload(envelope.asString)
            require(signed.createdAt < before)
            val candidates = if (signed.author == wallet.publicKey)
                signed.recipients.distinct().filter { it != wallet.publicKey } else listOf(signed.author)
            val counterparty = candidates.single { PrivateChat.conversationId(wallet.publicKey, it) == group }
            require(signed.recipients.containsAll(setOf(wallet.publicKey, counterparty)))
            if (signed.body.isJsonObject) {
                val event = PrivateChatLike.read(wallet, counterparty, envelope.asString)
                reactions.getOrPut(counterparty) { mutableListOf() }.add(event)
                oldestTimestamp = oldestTimestamp?.let { minOf(it, signed.createdAt) } ?: signed.createdAt
                null // Authenticated reaction metadata is retained separately from encrypted message text.
            } else {
                require(signed.body.isJsonPrimitive && signed.body.asJsonPrimitive.isString)
                oldestTimestamp = oldestTimestamp?.let { minOf(it, signed.createdAt) } ?: signed.createdAt
                PrivateChat.recover(wallet, envelope.asString, group)
            }
        } catch (_: Exception) { ignored++; null }
    }.distinctBy { it.message.id }
    return PrivateHistoryPage(verified, reactions, ignored > 0, rows.size() == 50, oldestTimestamp)
}
