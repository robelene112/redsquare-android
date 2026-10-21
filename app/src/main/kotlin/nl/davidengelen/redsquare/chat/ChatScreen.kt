package nl.davidengelen.redsquare.chat

import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import nl.davidengelen.redsquare.feed.GeneratedProfilePicture
import nl.davidengelen.redsquare.saito.*

/** Private Chat has its own navigation and verified history, sharing only the active Saito foundation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChatScreen(wallet: SaitoWallet, endpoint: String, backend: SaitoBackend, modifier: Modifier,
    onWalletMetadata: suspend (SaitoWallet, SaitoWallet) -> Unit, initialContact: String? = null, initialGroup: String? = null, onInitialDestinationClosed: () -> Unit = {},
    onBack: () -> Unit) {
    val promptContext = LocalContext.current.applicationContext
    nl.davidengelen.redsquare.NotificationPermissionPrompt(detail = R.string.chat_background_offer_detail,
        onEnable = { withContext(Dispatchers.IO) {
            nl.davidengelen.redsquare.notifications.ChatPushSettings(promptContext, wallet.publicKey).setEnabled(true)
        } })
    var groupDestination by remember(wallet.publicKey,backend) { mutableStateOf(initialGroup) }
    var reviewingGroups by remember(wallet.publicKey,backend) { mutableStateOf(initialGroup != null) }
    if (reviewingGroups) {
        GroupChatScreen(wallet,endpoint,backend,modifier,onWalletMetadata,groupDestination, onInitialDestinationClosed) {
            reviewingGroups=false; groupDestination=null
        }
        return
    }

    val personal = LocalPersonalControls.current
    val context = LocalContext.current.applicationContext
    val publishing = nl.davidengelen.redsquare.policy.LocalPublishingAgreement.current
    val scope = rememberCoroutineScope()
    val navigation = requireNotNull(nl.davidengelen.redsquare.feed.LocalPostNavigation.current)
    var newContact by remember(wallet.publicKey) { mutableStateOf("") }
    var savingContact by remember(wallet.publicKey) { mutableStateOf(false) }
    var contactFailed by remember(wallet.publicKey) { mutableStateOf(false) }
    var contacts by remember(wallet, backend) { mutableStateOf<List<String>>(emptyList()) }
    var contactsFailed by remember(wallet) { mutableStateOf(false) }
    var counterparty by remember(wallet.publicKey, backend) { mutableStateOf(initialContact) }
    var entries by remember(wallet.publicKey, backend, counterparty) { mutableStateOf<List<ChatHistoryStore.Entry>>(emptyList()) }
    var loading by remember(counterparty) { mutableStateOf(false) }
    var failed by remember(counterparty) { mutableStateOf(false) }
    var incomplete by remember(wallet, backend, counterparty) { mutableStateOf(false) }
    var cacheFailed by remember(counterparty) { mutableStateOf(false) }
    var reactionHistoryFailed by remember(wallet, backend, counterparty) { mutableStateOf(false) }
    var groupReceiveFailed by remember(wallet.publicKey, backend) { mutableStateOf(false) }
    val groupReceiveFailures = remember(wallet.publicKey, backend) { Channel<Unit>(Channel.CONFLATED) }
    LaunchedEffect(wallet.publicKey, backend) {
        try { for (ignored in groupReceiveFailures) groupReceiveFailed = true }
        finally { groupReceiveFailures.close() }
    }
    fun retainIncoming(bytes: ByteArray): String? = try { receiveChat(context, wallet, backend, bytes, endpoint) }
        catch (failure: GroupChatStorageException) {
            groupReceiveFailures.trySend(Unit)
            throw failure // Failed retention must keep the negative acknowledgement.
        }
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
        if (closing || navigation.transitioning) return
        closing = true
        scope.launch {
            try {
                if (navigation.save()) {
                    if (counterparty == null) onBack() else {
                        if (counterparty == initialContact) onInitialDestinationClosed()
                        counterparty = null
                    }
                }
            } finally { closing = false }
        }
    }
    BackHandler { close() }

    suspend fun retainReactions(page: PrivateHistoryPage): Boolean {
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
                        retainIncoming(bytes)
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
                        val saved = retainIncoming(bytes)
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

    val keyboardVisible = WindowInsets.isImeVisible
    BoxWithConstraints(modifier.fillMaxSize()) {
    val statusMaxHeight = minOf(maxHeight / 4, 80.dp)
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = !closing && !navigation.transitioning, onClick = { close() }) {
                Text(stringResource(R.string.browse_back))
            }
            Text(stringResource(R.string.chat_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            counterparty?.let { contact ->
                ConversationMuteButton(remember(wallet.publicKey, contact) { PrivateChat.conversationId(wallet.publicKey, contact) })
            }
            TextButton(onClick = { refresh++ }, enabled = !loading) { Text(stringResource(R.string.feed_refresh)) }
        }
        Text(stringResource(R.string.chat_private), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        val statusLabel = stringResource(R.string.chat_status)
        // Keep full recovery guidance reachable without consuming the message viewport.
        Column(Modifier.heightIn(max = statusMaxHeight).verticalScroll(rememberScrollState())
            .semantics { contentDescription = statusLabel }) {
            reactionState?.let { reactions ->
                val reactionMessage = when (reactions.saved.status) {
                    ChatReactionStore.Status.UNKNOWN -> R.string.chat_reaction_unknown
                    ChatReactionStore.Status.SUBMITTED -> R.string.chat_reaction_submitted
                    ChatReactionStore.Status.FORWARDED -> R.string.chat_reaction_forwarded
                    ChatReactionStore.Status.NONE -> null
                }
                InlineStatus(reactionMessage?.let { stringResource(it) },
                    if (reactions.saved.status == ChatReactionStore.Status.UNKNOWN) StatusTone.WARNING else StatusTone.INFO)
                InlineStatus(if (reactions.failed && !reactionHistoryFailed) stringResource(R.string.chat_reaction_error) else null, StatusTone.ERROR)
            }
            PersonalControlsStatus(personal)
            InlineStatus(if (contactsFailed || failed) stringResource(R.string.chat_load_error) else null, StatusTone.ERROR)
            InlineStatus(if (cacheFailed) stringResource(R.string.chat_cache_error) else null, StatusTone.WARNING)
            InlineStatus(if (groupReceiveFailed) stringResource(R.string.chat_group_receive_error) else null, StatusTone.WARNING)
            InlineStatus(if (reactionHistoryFailed) stringResource(R.string.chat_reaction_cache_error) else null, StatusTone.WARNING)
            InlineStatus(if (incomplete) stringResource(R.string.chat_incomplete) else null, StatusTone.WARNING)
            InlineStatus(if (!keyboardVisible) stringResource(R.string.status_available_history) else null,
                details = listOf(stringResource(R.string.chat_available_history)), announce = false)
        }
        val contact = counterparty
        if (contact == null) {
            TextButton(onClick={reviewingGroups=true}) { Text(stringResource(R.string.group_title)) }
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
                    TextButton(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 12.dp), onClick = {
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
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            GeneratedProfilePicture(key, Modifier.size(48.dp).clip(CircleShape))
                            Column(Modifier.weight(1f)) {
                                Text(registeredIdentity(key).name, style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface)
                                Text(key, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        } else {
            Text(contact, Modifier.padding(vertical = 4.dp), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (loading) Text(stringResource(R.string.chat_loading))
            if (hasOlder) TextButton(enabled = !loading && !olderLoading && olderCursor != null, onClick = {
                olderLoading = true
                olderRequest++
            }) { Text(stringResource(R.string.chat_load_older)) }
            val visible = entries.map { it.message }.filter { message -> personal?.ready == true &&
                (message.author == wallet.publicKey || !personal.policy.hidesIncomingChat(message.author)) }
            if (!keyboardVisible && visible.isEmpty() && !loading && !failed) Text(stringResource(R.string.chat_no_messages))
            val replies = remember(wallet, backend, contact) { ChatReplyActions() }
            val reactions = requireNotNull(reactionState)
            ConversationMessageList(visible.map { it.id }, "${wallet.publicKey}:${backend.id}:private:$contact",
                stringResource(R.string.chat_messages), Modifier.weight(1f),
                historyHidden = personal?.ready == true && entries.isNotEmpty() && visible.isEmpty()) { showMessage ->
                items(visible, key = { it.id }) { message ->
                    val outgoing = message.author == wallet.publicKey
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start) {
                        var copyFailed by remember(message.id) { mutableStateOf(false) }
                        ChatMessageBubble(message.author, message.text, message.createdAt, outgoing, backend=backend) { reference ->
                            val index = visible.indexOfFirst { it.author + it.createdAt == reference }
                            if (index >= 0) showMessage(index)
                        }
                        val replyPrefix = remember(message) { privateReplyPrefix(message) }
                        val likedBy = reactions.saved.likedBy(message)
                        if (likedBy.isNotEmpty()) Text(pluralStringResource(R.plurals.chat_likes, likedBy.size, likedBy.size))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(enabled = personal?.ready == true && reactions.editable,
                                onClick = { reactions.toggle(message, publishing::beforePublishing) }) {
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
                }
            }
            if (reactions.saved.status in listOf(ChatReactionStore.Status.UNKNOWN, ChatReactionStore.Status.SUBMITTED)) {
                TextButton(enabled = personal?.ready == true && reactions.retryable, onClick = { reactions.retry(publishing::beforePublishing) }) {
                    Text(stringResource(R.string.chat_reaction_retry))
                }
            }
            ChatComposer(wallet, backend, endpoint, contact, personal?.ready == true, replies) { saved ->
                entries = ChatHistoryStore.merge(entries, saved)
            }
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
