package nl.davidengelen.redsquare.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
    var newContact by remember(wallet.publicKey) { mutableStateOf("") }
    var savingContact by remember(wallet.publicKey) { mutableStateOf(false) }
    var contactFailed by remember(wallet.publicKey) { mutableStateOf(false) }
    var contacts by remember(wallet) { mutableStateOf<List<String>>(emptyList()) }
    var contactsFailed by remember(wallet) { mutableStateOf(false) }
    var counterparty by remember(wallet.publicKey, backend) { mutableStateOf<String?>(null) }
    var entries by remember(wallet.publicKey, backend, counterparty) { mutableStateOf<List<ChatHistoryStore.Entry>>(emptyList()) }
    var loading by remember(counterparty) { mutableStateOf(false) }
    var failed by remember(counterparty) { mutableStateOf(false) }
    var incomplete by remember(counterparty) { mutableStateOf(false) }
    var cacheFailed by remember(counterparty) { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(wallet, endpoint, refresh) {
        try {
            var discoveryFailed = false
            contacts = withContext(Dispatchers.IO) { wallet.chatContacts() }
            val known = contacts.map { PrivateChat.conversationId(wallet.publicKey, it) }.toSet()
            val missing = withContext(Dispatchers.IO) { wallet.chatHistoryIds().filter { it !in known } }
            if (missing.isNotEmpty()) {
                var updated = wallet
                SaitoPeer(wallet, endpoint).use { peer ->
                    require("archive" in peer.services())
                    for (group in missing) {
                        try {
                            val recovered = withContext(Dispatchers.IO) { loadPrivateHistory(peer, wallet, group) }
                            incomplete = incomplete || recovered.second
                            for ((address, messages) in recovered.first.groupBy { it.counterparty }) {
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
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { contactsFailed = true }
    }
    LaunchedEffect(wallet, endpoint, counterparty, refresh) {
        val contact = counterparty ?: return@LaunchedEffect
        loading = true
        val cache = ChatHistoryStore(context, wallet, backend, contact)
        try {
            val cached = withContext(Dispatchers.IO) { cache.load() }
            val savedIds = cached.map { it.message.id }.toSet()
            cacheFailed = entries.any { it.message.id !in savedIds }
            entries = ChatHistoryStore.merge(entries, cached)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { cacheFailed = true }
        try {
            coroutineScope {
                val updates = Channel<List<ChatHistoryStore.Entry>>(Channel.CONFLATED)
                val receiver = launch {
                    for (saved in updates) {
                        entries = ChatHistoryStore.merge(entries, saved)
                    }
                }
                try {
                    SaitoPeer(wallet, endpoint, onIncoming = { bytes ->
                        val received = PrivateChat.receive(wallet, bytes)
                        if (received.counterparty != contact) false
                        else {
                            val saved = cache.save(listOf(received.envelope))
                            updates.trySend(saved)
                            true // Original authenticated ciphertext is durably saved before acknowledgement.
                        }
                    }).use { peer ->
                        require(setOf("archive", "relay").all { it in peer.services() })
                        try {
                            val page = withContext(Dispatchers.IO) {
                                loadPrivateHistory(peer, wallet, PrivateChat.conversationId(wallet.publicKey, contact))
                            }
                            entries = ChatHistoryStore.merge(entries, page.first.map { ChatHistoryStore.Entry(it.envelope, it.message) })
                            try {
                                val saved = withContext(Dispatchers.IO) { cache.save(entries.map { it.envelope }) }
                                entries = ChatHistoryStore.merge(entries, saved)
                                cacheFailed = false
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { cacheFailed = true }
                            incomplete = page.second
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

    Column(modifier.fillMaxSize()) {
        Row {
            TextButton(onClick = { if (counterparty == null) onBack() else counterparty = null }) {
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
                    TextButton(onClick = { counterparty = key }, enabled = personal?.ready == true) {
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
            val visible = entries.map { it.message }.filter { message -> personal?.ready == true &&
                (message.author == wallet.publicKey || !personal.policy.hidesIncomingChat(message.author)) }
            if (visible.isEmpty() && !loading && !failed) Text(stringResource(R.string.chat_no_messages))
            LazyColumn(Modifier.weight(1f)) {
                items(visible, key = { it.id }) { message ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(registeredIdentity(message.author).name, style = MaterialTheme.typography.labelSmall)
                        Text(message.text, style = MaterialTheme.typography.bodyLarge)
                    }
                    HorizontalDivider()
                }
            }
            ChatComposer(wallet, backend, endpoint, contact, personal?.ready == true) { saved ->
                entries = ChatHistoryStore.merge(entries, saved)
            }
        }
    }
}

private suspend fun loadPrivateHistory(peer: SaitoPeer, wallet: SaitoWallet,
    group: String): Pair<List<ReceivedPrivateChat>, Boolean> {
    val criteria = JsonObject().apply {
        addProperty("request", "load")
        addProperty("field3", group)
        addProperty("limit", 50)
        addProperty("created_earlier_than", System.currentTimeMillis())
    }
    val request = JsonObject().apply { addProperty("request", "archive"); add("data", criteria) }
    val rows = JsonParser.parseString(peer.request(request.toString().toByteArray(Charsets.UTF_8))
        .toString(Charsets.UTF_8)).asJsonArray
    require(rows.size() <= 50)
    var ignored = 0
    val verified = rows.mapNotNull { row ->
        try {
            val envelope = row.asJsonObject.get("tx")
            require(envelope.isJsonPrimitive && envelope.asJsonPrimitive.isString)
            PrivateChat.recover(wallet, envelope.asString, group)
        } catch (_: Exception) { ignored++; null }
    }.distinctBy { it.message.id }
    return verified to (ignored > 0)
}
