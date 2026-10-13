package nl.davidengelen.redsquare.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.moderation.LocalPersonalControls
import nl.davidengelen.redsquare.moderation.PersonalControlsStatus
import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.saito.*

/** Private Chat has its own navigation and verified history, sharing only the active Saito foundation. */
@Composable
internal fun ChatScreen(wallet: SaitoWallet, endpoint: String, backend: SaitoBackend, modifier: Modifier, onBack: () -> Unit) {
    val personal = LocalPersonalControls.current
    var contacts by remember(wallet) { mutableStateOf<List<String>>(emptyList()) }
    var contactsFailed by remember(wallet) { mutableStateOf(false) }
    var counterparty by remember(wallet.publicKey, backend) { mutableStateOf<String?>(null) }
    var messages by remember(counterparty) { mutableStateOf<List<PrivateChatMessage>>(emptyList()) }
    var loading by remember(counterparty) { mutableStateOf(false) }
    var failed by remember(counterparty) { mutableStateOf(false) }
    var incomplete by remember(counterparty) { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(wallet, refresh) {
        try { contacts = withContext(Dispatchers.IO) { wallet.chatContacts() }; contactsFailed = false }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { contactsFailed = true }
    }
    LaunchedEffect(wallet, endpoint, counterparty, refresh) {
        val contact = counterparty ?: return@LaunchedEffect
        loading = true
        try {
            val page = withContext(Dispatchers.IO) {
                SaitoPeer(wallet, endpoint).use { peer ->
                    require("archive" in peer.services())
                    val criteria = JsonObject().apply {
                        addProperty("request", "load")
                        addProperty("field3", PrivateChat.conversationId(wallet.publicKey, contact))
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
                            PrivateChat.read(wallet, envelope.asString, contact)
                        } catch (_: Exception) { ignored++; null }
                    }.distinctBy { it.id }.sortedWith(compareBy<PrivateChatMessage> { it.createdAt }.thenBy { it.id })
                    verified to (ignored > 0)
                }
            }
            messages = page.first
            incomplete = page.second
            failed = false
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
        if (incomplete) Text(stringResource(R.string.chat_incomplete))
        val contact = counterparty
        if (contact == null) {
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
            val visible = messages.filter { message -> personal?.ready == true &&
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
        }
    }
}
