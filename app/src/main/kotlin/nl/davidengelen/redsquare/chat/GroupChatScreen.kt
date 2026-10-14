package nl.davidengelen.redsquare.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    fun act(operation:suspend()->Unit) {
        if(busy)return
        busy=true;sendFailed=false
        scope.launch {
            try{operation()}
            catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){sendFailed=true}
            finally{busy=false}
        }
    }
    suspend fun sendPrepared(id:String,candidate:SaitoTransaction,updated:SaitoWallet,contact:String,originalRelay:Boolean=false,relayRecipients:List<String> = listOf(contact)) {
        onWalletMetadata(wallet,updated)
        val current=GroupChatStore(context,updated,backend)
        withContext(Dispatchers.IO){
            submitChatTransaction(updated,endpoint,contact,candidate,ChatSubmissionCallbacks(
                claim={current.claim(id,candidate.id)},assertSelected={current.assertSelected()},
                submitted={publish{current.record(id,candidate.id,GroupChatStore.Status.SUBMITTED);current.entries()}},
                forwarded={publish{current.record(id,candidate.id,GroupChatStore.Status.FORWARDED);current.entries()}}),originalRelay,relayRecipients)
        }
    }
    var creating by remember(wallet.publicKey,backend) { mutableStateOf(false) }
    var joining by remember(wallet.publicKey,backend) { mutableStateOf(false) }
    var members by remember(selected) { mutableStateOf(false) }
    var leaving by remember(selected) { mutableStateOf(false) }
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
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(enabled=loaded&&!busy,onClick={creating=true}) { Text(stringResource(R.string.group_create)) }
                OutlinedButton(enabled=loaded&&!busy,onClick={joining=true}) { Text(stringResource(R.string.group_join)) }
            }
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
            if(entry.group.confirmedInvitation!=null) Text(stringResource(R.string.group_invitation_origin),
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row {
                TextButton(onClick={members=true}) { Text(stringResource(R.string.group_members)) }
                if(entry.group.memberRoles[wallet.publicKey]?.active==true) {
                    TextButton(enabled=!busy,onClick={inviting=true}) { Text(stringResource(R.string.group_invite)) }
                    TextButton(enabled=!busy&&entry.status==GroupChatStore.Status.FORWARDED,onClick={leaving=true}) { Text(stringResource(R.string.group_leave)) }
                }
            }
            Text(stringResource(when(entry.status) {
                GroupChatStore.Status.UNKNOWN -> R.string.group_unknown
                GroupChatStore.Status.SUBMITTED -> R.string.group_submitted
                GroupChatStore.Status.FORWARDED -> R.string.group_forwarded
            }),style=MaterialTheme.typography.bodySmall)
            if(entry.status!=GroupChatStore.Status.FORWARDED) TextButton(enabled=loaded&&!busy&&!readFailed,onClick={
                act {
                    withContext(NonCancellable){publish{store.prepareRetry(entry.group.id,entry.candidate.id);store.entries()}}
                    val updated=withContext(Dispatchers.IO){wallet.withNamedChatGroup(entry.group.id,entry.group.name,entry.creationKey)}
                    val request=SaitoEnvelope.decode(entry.candidate.envelope()).body.get("request").asString
                    val recipients=if(request=="chat join") {
                        SaitoEnvelope.decode(entry.candidate.envelope()).recipients.filter{it!=wallet.publicKey&&it!=entry.group.id}
                    } else entry.group.memberRoles.filterValues{it.active}.keys.filter{it!=wallet.publicKey}
                    val targets=recipients.ifEmpty{listOf(wallet.publicKey)}
                    sendPrepared(entry.group.id,entry.candidate,updated,targets.first(),originalRelay=request=="chat group",relayRecipients=targets)
                }
            }){Text(stringResource(R.string.group_retry))}
            if(entry.group.memberRoles[wallet.publicKey]==ChatGroupRole.LEFT) Text(stringResource(R.string.group_left))
            Spacer(Modifier.weight(1f))
        }
    }
    if(creating) CreateGroupDialog(busy,onDismiss={if(!busy)creating=false}) { name,invitees ->
        act {
            val groupKey=withContext(Dispatchers.IO){SaitoWallet.create()}
            val candidate=withContext(Dispatchers.IO){GroupChat.create(wallet,groupKey.publicKey,name,invitees,System.currentTimeMillis())}
            withContext(NonCancellable){publish{store.prepareCreation(candidate,groupKey);store.entries()}}
            selected=groupKey.publicKey;creating=false
            val updated=withContext(Dispatchers.IO){wallet.withNamedChatGroup(groupKey.publicKey,name,groupKey)}
            val recipient=invitees.firstOrNull{it!=wallet.publicKey} ?: wallet.publicKey
            sendPrepared(groupKey.publicKey,candidate,updated,recipient,originalRelay=true)
        }
    }
    if(joining) JoinGroupDialog(backend,busy,onDismiss={if(!busy)joining=false}) { invitation ->
        act {
            val candidate=withContext(Dispatchers.IO){GroupChat.join(wallet,invitation,System.currentTimeMillis())}
            withContext(NonCancellable){publish{store.prepareInvitation(invitation,candidate);store.entries()}}
            selected=invitation.id;joining=false
            val updated=withContext(Dispatchers.IO){wallet.withNamedChatGroup(invitation.id,invitation.name)}
            sendPrepared(invitation.id,candidate,updated,invitation.admin)
        }
    }
    if(leaving && entry!=null) AlertDialog(onDismissRequest={if(!busy)leaving=false},
        title={Text(stringResource(R.string.group_leave))},
        text={Text(stringResource(R.string.group_leave_notice))},
        dismissButton={TextButton(enabled=!busy,onClick={leaving=false}){Text(stringResource(R.string.group_cancel))}},
        confirmButton={TextButton(enabled=!busy,onClick={
            act {
                val candidate=withContext(Dispatchers.IO){GroupChat.leave(wallet,entry.group.id,System.currentTimeMillis())}
                val recipients=entry.group.memberRoles.filterValues{it.active}.keys.filter{it!=wallet.publicKey}.ifEmpty{listOf(wallet.publicKey)}
                withContext(NonCancellable){publish{store.prepareLeave(candidate);store.entries()}}
                leaving=false
                sendPrepared(entry.group.id,candidate,wallet,recipients.first(),relayRecipients=recipients)
            }
        }){Text(stringResource(R.string.group_leave_confirm))}})
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

@Composable
private fun JoinGroupDialog(backend:SaitoBackend,busy:Boolean,onDismiss:()->Unit,onJoin:(GroupInvitation)->Unit) {
    var input by remember { mutableStateOf("") }
    var invitation by remember { mutableStateOf<GroupInvitation?>(null) }
    var invalid by remember { mutableStateOf(false) }
    val focus=androidx.compose.ui.platform.LocalFocusManager.current
    val preview=invitation
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.group_join))},
        text={Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            if(preview==null){
                OutlinedTextField(input,{input=it;invalid=false},label={Text(stringResource(R.string.group_invitation_link))},enabled=!busy)
                if(invalid)Text(stringResource(R.string.group_invitation_invalid),color=MaterialTheme.colorScheme.error)
            }else{
                Text(preview.name,style=MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.group_invitation_trust),style=MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.group_admin_label));Text(preview.admin,style=MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.group_inviter_label));Text(preview.sender,style=MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.group_id_label));Text(preview.id,style=MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.group_privacy),style=MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.group_join_backend,backend.domain),style=MaterialTheme.typography.bodySmall)
            }
        }},dismissButton={TextButton(enabled=!busy,onClick=onDismiss){Text(stringResource(R.string.group_cancel))}},
        confirmButton={TextButton(enabled=!busy&&(preview!=null||input.isNotBlank()),onClick={
            if(preview!=null)onJoin(preview) else try{invitation=GroupInvitation.parse(input);focus.clearFocus()}
            catch(_:Exception){invalid=true}
        }){Text(stringResource(if(preview==null)R.string.group_preview else R.string.group_join_confirm))}})
}
