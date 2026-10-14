package nl.davidengelen.redsquare.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.*

/** Named-group UI is separate from private Chat and shares the selected wallet/backend. */
@Composable
internal fun GroupChatScreen(wallet: SaitoWallet, endpoint: String, backend: SaitoBackend, modifier: Modifier,
    onWalletMetadata: suspend (SaitoWallet,SaitoWallet) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val store = remember(wallet,backend) { GroupChatStore(context,wallet,backend) }
    var entries by remember(wallet.publicKey,backend) { mutableStateOf<List<GroupChatStore.Entry>>(emptyList()) }
    var selected by remember(wallet.publicKey,backend) { mutableStateOf<String?>(null) }
    var loaded by remember(store) { mutableStateOf(false) }
    val publication = remember(wallet.publicKey,backend) { Mutex() }
    var readFailed by remember(wallet.publicKey,backend) { mutableStateOf(false) }
    var sendFailed by remember(wallet.publicKey,backend) { mutableStateOf(false) }
    suspend fun publish(operation: () -> List<GroupChatStore.Entry>) = withContext(Dispatchers.Main.immediate) {
        publication.withLock { entries=withContext(Dispatchers.IO) { operation() } }
    }
    var busy by remember(wallet.publicKey,backend) { mutableStateOf(false) }
    var creating by remember(wallet.publicKey,backend) { mutableStateOf(false) }
    var members by remember(selected) { mutableStateOf(false) }
    var inviting by remember(selected) { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    fun back() { if(!busy) { if(selected==null) onBack() else selected=null } }
    BackHandler { back() }
    LaunchedEffect(store,refresh) {
        loaded=false
        try { publish { store.entries() };loaded=true;readFailed=false }
        catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){readFailed=true}
    }
    val entry = entries.firstOrNull { it.group.id == selected }
    Column(modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            TextButton(enabled=!busy,onClick={back()}) { Text(stringResource(R.string.browse_back)) }
            Text(entry?.group?.name ?: stringResource(R.string.group_title),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            TextButton(enabled=!busy,onClick={refresh++}) { Text(stringResource(R.string.feed_refresh)) }
        }
        Text(stringResource(R.string.group_privacy),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.chat_available_history),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(readFailed || sendFailed) Text(stringResource(R.string.group_error),color=MaterialTheme.colorScheme.error)
        if(entry==null) {
            Button(enabled=loaded&&!busy,onClick={creating=true}) { Text(stringResource(R.string.group_create)) }
            LazyColumn(Modifier.weight(1f)) {
                items(entries,key={it.group.id}) { row ->
                    TextButton(enabled=!busy,onClick={selected=row.group.id},modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(vertical=8.dp)) {
                            Text(row.group.name,style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onSurface)
                            Text(stringResource(R.string.group_unencrypted),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider()
                }
            }
        } else {
            Row {
                TextButton(onClick={members=true}) { Text(stringResource(R.string.group_members)) }
                TextButton(onClick={inviting=true}) { Text(stringResource(R.string.group_invite)) }
            }
            Text(stringResource(when(entry.status) {
                GroupChatStore.Status.UNKNOWN -> R.string.group_unknown
                GroupChatStore.Status.SUBMITTED -> R.string.group_submitted
                GroupChatStore.Status.FORWARDED -> R.string.group_forwarded
            }),style=MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
        }
    }
    if(creating) CreateGroupDialog(busy,onDismiss={if(!busy)creating=false}) { name,invitees ->
        busy=true;sendFailed=false
        scope.launch {
            try {
                val groupKey = withContext(Dispatchers.IO) { SaitoWallet.create() }
                val candidate = withContext(Dispatchers.IO) { GroupChat.create(wallet,groupKey.publicKey,name,invitees,System.currentTimeMillis()) }
                // Protect original intent first; metadata and sending can fail without losing it.
                withContext(NonCancellable) {
                    publish { store.prepareCreation(candidate);store.entries() }
                }
                val groupId=groupKey.publicKey
                selected=groupId;creating=false
                val updated = withContext(Dispatchers.IO) { wallet.withNamedChatGroup(groupKey.publicKey,name,groupKey) }
                onWalletMetadata(wallet,updated)
                val current = GroupChatStore(context,updated,backend)
                val recipient = invitees.firstOrNull { it != wallet.publicKey } ?: wallet.publicKey
                withContext(Dispatchers.IO) {
                    submitChatTransaction(updated,endpoint,recipient,candidate,ChatSubmissionCallbacks(
                        claim={current.claim(groupId,candidate.id)},assertSelected={current.assertSelected()},
                        submitted={publish { current.record(groupId,candidate.id,GroupChatStore.Status.SUBMITTED);current.entries() }},
                        forwarded={publish { current.record(groupId,candidate.id,GroupChatStore.Status.FORWARDED);current.entries() }}), originalRelay=true)
                }
            } catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){sendFailed=true}
            finally{busy=false}
        }
    }
    if(members && entry!=null) AlertDialog(onDismissRequest={members=false},title={Text(stringResource(R.string.group_members))},
        text={LazyColumn(Modifier.heightIn(max=320.dp)){items(entry.group.memberRoles.toList(),key={it.first}){(key,role)->
            Column(Modifier.padding(vertical=8.dp)){Text(key,style=MaterialTheme.typography.bodySmall);Text(role.name.lowercase())}
        }}},confirmButton={TextButton(onClick={members=false}){Text(stringResource(R.string.group_done))}})
    if(inviting && entry!=null) {
        val invitation = remember(entry.group,backend) { GroupInvitation.fromGroup(entry.group,wallet.publicKey).link(backend) }
        AlertDialog(onDismissRequest={inviting=false},title={Text(stringResource(R.string.group_invitation_link))},
            text={SelectionContainer{Text(invitation,Modifier.semantics{contentDescription="Group invitation link"},style=MaterialTheme.typography.bodySmall)}},
            confirmButton={TextButton(onClick={inviting=false}){Text(stringResource(R.string.group_done))}})
    }
}

@Composable
private fun CreateGroupDialog(busy:Boolean,onDismiss:()->Unit,onCreate:(String,List<String>)->Unit) {
    var name by remember { mutableStateOf("") }
    var invitees by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.group_create))},text={Column {
        OutlinedTextField(name,{name=it;invalid=false},label={Text(stringResource(R.string.group_name))},enabled=!busy)
        OutlinedTextField(invitees,{invitees=it;invalid=false},label={Text(stringResource(R.string.group_invitees))},enabled=!busy)
        if(invalid)Text(stringResource(R.string.group_invalid_invitees))
    }},dismissButton={TextButton(enabled=!busy,onClick=onDismiss){Text(stringResource(R.string.group_cancel))}},
        confirmButton={TextButton(enabled=!busy&&name.isNotBlank(),onClick={
            val keys=invitees.split(Regex("[\\s,;]+")).filter(String::isNotBlank).distinct()
            if(keys.size>254 || keys.any{!SaitoWallet.isPublicKey(it)}) invalid=true else onCreate(name.trim(),keys)
        }){Text(stringResource(R.string.group_create_confirm))}})
}
