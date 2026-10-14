package nl.davidengelen.redsquare.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.moderation.LocalPersonalControls
import nl.davidengelen.redsquare.moderation.PersonalControlsStatus
import nl.davidengelen.redsquare.registry.registeredIdentity

/** Named-group UI is separate from private Chat and shares the selected wallet/backend. */
@Composable
internal fun GroupChatScreen(wallet: SaitoWallet, endpoint: String, backend: SaitoBackend, modifier: Modifier,
    onWalletMetadata: suspend (SaitoWallet,SaitoWallet) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val personal = LocalPersonalControls.current
    val store = remember(wallet,backend) { GroupChatStore(context,wallet,backend) }
    var entries by remember(wallet.publicKey,backend) { mutableStateOf<List<GroupChatStore.Entry>>(emptyList()) }
    var selected by remember(wallet.publicKey,backend) { mutableStateOf<String?>(null) }
    var loaded by remember(store) { mutableStateOf(false) }
    val publication = remember(wallet.publicKey,backend) { Mutex() }
    var readFailed by remember(wallet.publicKey,backend) { mutableStateOf(false) }
    var historyFailed by remember(store,selected) { mutableStateOf(false) }
    var liveFailed by remember(store,endpoint){mutableStateOf(false)}
    var sendFailed by remember(wallet.publicKey,backend) { mutableStateOf(false) }
    suspend fun publish(onResult:(Boolean)->Unit={},operation: () -> List<GroupChatStore.Entry>) = withContext(Dispatchers.Main.immediate) {
        publication.withLock {
            try{entries=withContext(Dispatchers.IO){operation()};onResult(true)}
            catch(cancelled:CancellationException){throw cancelled}
            catch(failure:Exception){onResult(false);throw failure}
        }
    }
    var busy by remember(wallet.publicKey,backend) { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var lastRefreshAttempt by remember(wallet.publicKey,backend){mutableIntStateOf(0)}
    var initialLoading by remember(store,selected){mutableStateOf(false)}
    var olderLoading by remember(store,selected){mutableStateOf(false)}
    val paging=initialLoading||olderLoading
    var hasOlder by remember(store,selected){mutableStateOf(false)}
    var olderCursor by remember(store,selected){mutableStateOf<Long?>(null)}
    suspend fun publishPage(page:GroupHistoryPage) {
        publish{store.entries()}
        historyFailed=page.incomplete
        hasOlder=page.hasOlder;olderCursor=page.cursor
    }
    var olderRequest by remember(store,selected){mutableIntStateOf(0)}
    var importedIds by remember(wallet){mutableStateOf<List<String>>(emptyList())}
    var hintsFailed by remember(wallet){mutableStateOf(false)}
    LaunchedEffect(wallet){
        try{importedIds=withContext(Dispatchers.IO){wallet.namedChatGroupIds()};hintsFailed=false}
        catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){hintsFailed=true}
    }
    var discoveryCursor by remember(store,endpoint,refresh){mutableStateOf<Long?>(null)}
    var discoveryHasOlder by remember(store,endpoint,refresh){mutableStateOf(false)}
    var discoveryLoading by remember(store,endpoint){mutableStateOf(false)}
    var discoveryChecked by remember(store,endpoint,refresh){mutableStateOf(false)}
    var discoveryRequest by remember(store,endpoint,refresh){mutableIntStateOf(0)}
    var message by remember(wallet.publicKey,backend,selected){mutableStateOf("")}
    var draftReady by remember(store,selected){mutableStateOf(false)}
    var draftFailed by remember(wallet.publicKey,backend,selected){mutableStateOf(false)}
    var savingForNavigation by remember(wallet.publicKey,backend,selected){mutableStateOf(false)}
    val draftWrites=remember(wallet.publicKey,backend,selected){Mutex()}
    val navigation=requireNotNull(nl.davidengelen.redsquare.feed.LocalPostNavigation.current)
    suspend fun persistDraft(allowBusy:Boolean=false):Boolean = withContext(Dispatchers.Main.immediate) {
        draftWrites.withLock {
            if(busy&&!allowBusy)return@withLock false
            val id=selected ?: return@withLock true
            if(!draftReady)return@withLock true
            if(entries.firstOrNull{it.group.id==id}?.hasPendingAction!=false)return@withLock true
            try {
                val typed=message
                withContext(NonCancellable){publish{store.saveDraft(id,typed);store.entries()}}
                draftFailed=false
                true
            } catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){draftFailed=true;false}
        }
    }
    DisposableEffect(store,selected,navigation) {
        val save:suspend()->Boolean={
            savingForNavigation=true
            try{persistDraft()}finally{savingForNavigation=false}
        }
        navigation.saveCurrent=save
        onDispose{if(navigation.saveCurrent===save)navigation.saveCurrent=null}
    }
    LaunchedEffect(store,selected,refresh) {
        if(draftReady)return@LaunchedEffect // Refresh must not replace unsaved edited text.
        val id=selected ?: return@LaunchedEffect
        try {
            message=withContext(Dispatchers.IO){requireNotNull(store.load(id)).draft}
            draftReady=true;draftFailed=false
        } catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){draftFailed=true}
    }
    LaunchedEffect(draftReady,message) {
        if(!draftReady||busy)return@LaunchedEffect
        delay(300)
        persistDraft()
    }
    fun act(operation:suspend()->Unit) {
        if(busy||savingForNavigation)return
        busy=true;sendFailed=false
        scope.launch {
            try{if(persistDraft(allowBusy=true))operation()}
            catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){sendFailed=true}
            finally{busy=false}
        }
    }
    suspend fun sendPrepared(id:String,candidate:SaitoTransaction,updated:SaitoWallet,originalRelay:Boolean=false) {
        onWalletMetadata(wallet,updated)
        val current=GroupChatStore(context,updated,backend)
        withContext(Dispatchers.IO){
            submitChatTransaction(updated,endpoint,updated.publicKey,candidate,ChatSubmissionCallbacks(
                claim={current.claim(id,candidate.id)},assertSelected={current.assertSelected()},
                submitted={publish{current.record(id,candidate.id,GroupChatStore.Status.SUBMITTED);current.entries()}},
                forwarded={publish{current.record(id,candidate.id,GroupChatStore.Status.FORWARDED);current.entries()}},
                relayRecipients={current.relayRecipients(id,candidate.id)}),originalRelay)
        }
    }
    var creating by remember(wallet.publicKey,backend) { mutableStateOf(false) }
    var joining by remember(wallet.publicKey,backend) { mutableStateOf(false) }
    var members by remember(selected) { mutableStateOf(false) }
    var promoting by remember(selected) { mutableStateOf<String?>(null) }
    var leaving by remember(selected) { mutableStateOf(false) }
    var inviting by remember(selected) { mutableStateOf(false) }
    fun back() {
        if(busy||savingForNavigation)return
        scope.launch{if(navigation.save()){if(selected==null)onBack() else selected=null}}
    }
    BackHandler { back() }
    LaunchedEffect(store,refresh) {
        loaded=false
        val explicitRefresh=refresh>lastRefreshAttempt
        lastRefreshAttempt=refresh // Failed/cancelled attempts cannot become ordinary reload authority.
        try { publish(onResult={success->
            loaded=success
            if(!success)readFailed=true
            else if(explicitRefresh)readFailed=false
        }){store.entries()} }
        catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){/* Failure state was published under the same lock. */}
    }
    // Keep compatible watch metadata in the protected backup. Its names never supply authority.
    val receivedEntries=entries.filter{it.candidate==null&&it.status==GroupChatStore.Status.RECEIVED}
    val watchMetadata=receivedEntries.map{it.group.id to it.group.name}
    LaunchedEffect(store,watchMetadata,loaded,busy,readFailed) {
        if(!loaded||busy||savingForNavigation||readFailed||receivedEntries.isEmpty())return@LaunchedEffect
        savingForNavigation=true
        try {
            val updated=withContext(Dispatchers.IO){
                receivedEntries.fold(wallet){saved,row->saved.withNamedChatGroup(row.group.id,row.group.name)}
            }
            if(updated.exportJson()!=wallet.exportJson() && persistDraft() && !readFailed)onWalletMetadata(wallet,updated)
        }catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){readFailed=true}
        finally{savingForNavigation=false}
    }
    LaunchedEffect(store,endpoint,loaded,importedIds,refresh,discoveryRequest) {
        if(!loaded||importedIds.isEmpty())return@LaunchedEffect
        val missing=importedIds.filter{id->entries.none{it.group.id==id}}.toSet()
        if(missing.isEmpty())return@LaunchedEffect
        discoveryLoading=true
        try {
            val before=if(discoveryRequest==0)System.currentTimeMillis() else discoveryCursor ?: return@LaunchedEffect
            SaitoPeer(wallet,endpoint).use{peer->
                require("archive" in peer.services())
                val page=withContext(Dispatchers.IO){loadGroupDiscoveryPage(peer,store,wallet.publicKey,missing,before)}
                publish{store.entries()}
                discoveryHasOlder=page.hasOlder;discoveryCursor=page.cursor
            }
        }catch(cancelled:CancellationException){throw cancelled}
        catch(_:GroupChatStorageException){readFailed=true}
        catch(_:Exception){/* Missing originals remain public ID hints with recovery guidance. */}
        finally{discoveryLoading=false;discoveryChecked=true}
    }
    // Retain private and group originals even while Groups is shown.
    // Receiving never creates an outgoing attempt.
    val receiveUpdates=remember(wallet.publicKey,backend){Channel<Unit>(Channel.CONFLATED)}
    val receiveStorageFailed=remember(wallet.publicKey,backend){java.util.concurrent.atomic.AtomicBoolean(false)}
    val receiveStore by rememberUpdatedState(store)
    LaunchedEffect(wallet.publicKey,backend){
        try {
            for(ignored in receiveUpdates){
                try{publish(onResult={success->
                    val failed=receiveStorageFailed.getAndSet(false)
                    if(!success||failed)readFailed=true
                }){receiveStore.entries()}}
                catch(cancelled:CancellationException){throw cancelled}
                catch(_:Exception){/* Ordered publication reported the local failure. */}
            }
        }finally{receiveUpdates.close()}
    }
    // Metadata replacement can restart the socket, but cannot discard queued storage failures.
    LaunchedEffect(store,endpoint,refresh) {
                while(isActive){
                    try {
                        SaitoPeer(wallet,endpoint,onIncoming={bytes->
                            try{receiveChat(context,wallet,backend,bytes)}
                            catch(failure:GroupChatStorageException){
                                receiveStorageFailed.set(true)
                                receiveUpdates.trySend(Unit)
                                throw failure // SaitoPeer sends a negative acknowledgement.
                            }
                            receiveUpdates.trySend(Unit)
                            true // Protect the authenticated original before positive acknowledgement.
                        }).use{peer->
                            peer.services()
                            liveFailed=false
                            peer.awaitDisconnection()
                            liveFailed=true
                        }
                    }catch(cancelled:CancellationException){throw cancelled}
                    catch(_:Exception){liveFailed=true}
                    delay(2_000) // Reconnect passive reception without transmitting retained attempts.
                }
    }
    LaunchedEffect(store,selected,endpoint,refresh) {
        val id=selected ?: return@LaunchedEffect
        initialLoading=true
        try {
            SaitoPeer(wallet,endpoint).use{peer->
                require("archive" in peer.services())
                val page=withContext(Dispatchers.IO){loadGroupHistoryPage(peer,store,id,System.currentTimeMillis())}
                publishPage(page)
            }
        }catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){historyFailed=true}
        finally{initialLoading=false}
    }
    LaunchedEffect(store,selected,endpoint,olderRequest) {
        if(olderRequest==0)return@LaunchedEffect
        val id=selected ?: return@LaunchedEffect
        val before=olderCursor ?: return@LaunchedEffect
        olderLoading=true
        try {
            SaitoPeer(wallet,endpoint).use{peer->
                require("archive" in peer.services())
                val page=withContext(Dispatchers.IO){loadGroupHistoryPage(peer,store,id,before)}
                publishPage(page)
            }
        } catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){historyFailed=true}
        finally{olderLoading=false}
    }
    val entry = entries.firstOrNull { it.group.id == selected }
    Column(modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            TextButton(enabled=!busy,onClick={back()}) { Text(stringResource(R.string.browse_back)) }
            Text(entry?.group?.name ?: stringResource(R.string.group_title),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            TextButton(enabled=!busy&&!paging,onClick={refresh++}) { Text(stringResource(R.string.feed_refresh)) }
        }
        Text(stringResource(R.string.group_privacy),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.chat_available_history),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(hintsFailed)Text(stringResource(R.string.group_hints_error),color=MaterialTheme.colorScheme.onSurfaceVariant)
        PersonalControlsStatus(personal)
        if(readFailed || sendFailed || draftFailed) Text(stringResource(R.string.group_error),color=MaterialTheme.colorScheme.error)
        if(historyFailed) Text(stringResource(R.string.group_history_error),color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(liveFailed) Text(stringResource(R.string.group_live_error),color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(entry==null) {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(enabled=loaded&&!busy,onClick={creating=true}) { Text(stringResource(R.string.group_create)) }
                OutlinedButton(enabled=loaded&&!busy,onClick={joining=true}) { Text(stringResource(R.string.group_join)) }
            }
            if(discoveryHasOlder&&discoveryCursor!=null)TextButton(enabled=!busy&&!discoveryLoading,onClick={discoveryRequest++}){
                Text(stringResource(R.string.group_discovery_older))
            }
            LazyColumn(Modifier.weight(1f)) {
                items(entries,key={it.group.id}) { row ->
                    TextButton(enabled=!busy,onClick={selected=row.group.id},modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(vertical=8.dp)) {
                            ChatIdentityRow(row.group.id,row.group.name,stringResource(R.string.group_unencrypted))
                        }
                    }
                    HorizontalDivider()
                }
                items(importedIds.filter{id->entries.none{it.group.id==id}},key={it}){id->
                    Column(Modifier.fillMaxWidth().padding(vertical=8.dp)) {
                        Text(id,style=MaterialTheme.typography.bodySmall)
                        Text(stringResource(if(!discoveryChecked||discoveryLoading)R.string.group_checking
                            else if(discoveryHasOlder)R.string.group_not_recovered else R.string.group_unavailable),style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                }
            }
        } else {
            if(entry.group.confirmedInvitation!=null) Text(stringResource(R.string.group_invitation_origin),
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow {
                TextButton(onClick={members=true}) { Text(stringResource(R.string.group_members)) }
                if(entry.group.memberRoles[wallet.publicKey]?.active==true) {
                    TextButton(enabled=!busy&&entry.group.memberRoles.values.any{it==ChatGroupRole.ADMIN},onClick={inviting=true}) { Text(stringResource(R.string.group_invite)) }
                    TextButton(enabled=!busy&&!entry.hasPendingAction,onClick={leaving=true}) { Text(stringResource(R.string.group_leave)) }
                }
            }
            Text(stringResource(when(entry.status) {
                GroupChatStore.Status.UNKNOWN -> R.string.group_unknown
                GroupChatStore.Status.SUBMITTED -> R.string.group_submitted
                GroupChatStore.Status.FORWARDED -> R.string.group_forwarded
                GroupChatStore.Status.RECEIVED -> if(entry.ownCreation)R.string.group_recovered else R.string.group_received
            }),style=MaterialTheme.typography.bodySmall)
            if(entry.hasPendingAction) TextButton(enabled=loaded&&!busy&&!readFailed,onClick={
                act {
                    val pending=requireNotNull(entry.candidate)
                    withContext(NonCancellable){publish{store.prepareRetry(entry.group.id,pending.id);store.entries()}}
                    val updated=withContext(Dispatchers.IO){wallet.withNamedChatGroup(entry.group.id,entry.group.name,entry.creationKey)}
                    val creation=withContext(Dispatchers.IO){SaitoEnvelope.decode(pending.envelope()).body.get("request").asString=="chat group"}
                    sendPrepared(entry.group.id,pending,updated,originalRelay=creation)
                }
            }){Text(stringResource(R.string.group_retry))}
            if(entry.group.memberRoles[wallet.publicKey]==ChatGroupRole.LEFT) Text(stringResource(R.string.group_left))
            if(hasOlder&&olderCursor!=null) TextButton(enabled=!busy&&!paging,onClick={if(!initialLoading&&!olderLoading&&!busy){olderLoading=true;olderRequest++}}){
                Text(stringResource(R.string.chat_load_older))
            }
            val messagesLabel=stringResource(R.string.group_messages)
            val messageList=rememberLazyListState()
            val visible=entry.messages.filter{personal?.ready==true &&
                (it.author==wallet.publicKey || !personal.policy.hidesIncomingChat(it.author))}
            val editable=personal?.ready==true&&draftReady&&!busy&&!savingForNavigation&&
                !entry.hasPendingAction&&entry.group.memberRoles[wallet.publicKey]?.active==true
            val permitted=editable&&loaded&&!readFailed
            LazyColumn(Modifier.weight(1f).semantics{contentDescription=messagesLabel},
                state=messageList,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(visible,key={it.id}){item->
                    val outgoing=item.author==wallet.publicKey
                    Column(Modifier.fillMaxWidth().padding(vertical=6.dp),
                        horizontalAlignment=if(outgoing)Alignment.End else Alignment.Start){
                        var copyFailed by remember(item.id){mutableStateOf(false)}
                        val replyPrefix=remember(item){chatReplyPrefix(item.author,item.createdAt,item.text)}
                        ChatMessageBubble(item.author,item.text,item.createdAt,outgoing,selectable=true){reference->
                            val index=visible.indexOfFirst{it.author+it.createdAt==reference}
                            if(index>=0)scope.launch{messageList.animateScrollToItem(index)}
                        }
                        val likedBy=entry.likedBy(item)
                        if(likedBy.isNotEmpty())Text(pluralStringResource(R.plurals.chat_likes,likedBy.size,likedBy.size))
                        FlowRow{
                        TextButton(enabled=permitted,onClick={
                            act {
                                val candidate=withContext(Dispatchers.IO){GroupChat.like(wallet,entry.group,item,System.currentTimeMillis())}
                                withContext(NonCancellable){publish{store.prepareReaction(candidate);store.entries()}}
                                sendPrepared(entry.group.id,candidate,wallet)
                            }
                        }){Text(stringResource(if(wallet.publicKey in likedBy)R.string.chat_unlike else R.string.chat_like))}
                        TextButton(enabled=permitted&&replyPrefix!=null,onClick={
                            replyPrefix?.let{message=it+privateMessageBody(message)}
                        }){Text(stringResource(R.string.post_reply))}
                        TextButton(onClick={copyFailed=!copyPrivateMessage(context,item.text)}){Text(stringResource(R.string.chat_copy))}
                        }
                        if(copyFailed)Text(stringResource(R.string.chat_copy_error))
                    }
                }
            }
            val quote=remember(message){privateReply(message)}
            val typed=quote?.body ?: message
            if(quote!=null){
                Text(quote.quote,style=MaterialTheme.typography.bodyMedium)
                TextButton(enabled=editable,onClick={message=typed}){Text(stringResource(R.string.chat_remove_reply))}
            }
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedTextField(typed,{message=(quote?.prefix ?: "")+it},Modifier.weight(1f),
                    label={Text(stringResource(R.string.chat_message))},enabled=editable,maxLines=4)
                Button(enabled=permitted&&typed.isNotBlank(),onClick={
                    act {
                        val mentioned=quote?.author?.let{listOf(it)}.orEmpty()
                        val candidate=withContext(Dispatchers.IO){GroupChat.message(wallet,entry.group,message,System.currentTimeMillis(),mentioned)}
                        withContext(NonCancellable){publish{store.prepareMessage(candidate);store.entries()}}
                        message=""
                        sendPrepared(entry.group.id,candidate,wallet)
                    }
                }){Text(stringResource(R.string.chat_send))}
            }
        }
    }
    if(creating) CreateGroupDialog(busy,onDismiss={if(!busy)creating=false}) { name,invitees ->
        act {
            val groupKey=withContext(Dispatchers.IO){SaitoWallet.create()}
            val candidate=withContext(Dispatchers.IO){GroupChat.create(wallet,groupKey.publicKey,name,invitees,System.currentTimeMillis())}
            withContext(NonCancellable){publish{store.prepareCreation(candidate,groupKey);store.entries()}}
            selected=groupKey.publicKey;creating=false
            val updated=withContext(Dispatchers.IO){wallet.withNamedChatGroup(groupKey.publicKey,name,groupKey)}
            sendPrepared(groupKey.publicKey,candidate,updated,originalRelay=true)
        }
    }
    if(joining) JoinGroupDialog(backend,busy,entries.map{it.group},onDismiss={if(!busy)joining=false}) { invitation ->
        act {
            val candidate=withContext(Dispatchers.IO){GroupChat.join(wallet,invitation,System.currentTimeMillis())}
            withContext(NonCancellable){publish{store.prepareInvitation(invitation,candidate);store.entries()}}
            selected=invitation.id;joining=false
            val updated=withContext(Dispatchers.IO){val retained=requireNotNull(store.load(invitation.id));wallet.withNamedChatGroup(retained.group.id,retained.group.name,retained.creationKey)}
            sendPrepared(invitation.id,candidate,updated)
        }
    }
    if(leaving && entry!=null) AlertDialog(onDismissRequest={if(!busy)leaving=false},
        title={Text(stringResource(R.string.group_leave))},
        text={Text(stringResource(R.string.group_leave_notice))},
        dismissButton={TextButton(enabled=!busy,onClick={leaving=false}){Text(stringResource(R.string.group_cancel))}},
        confirmButton={TextButton(enabled=!busy,onClick={
            act {
                val candidate=withContext(Dispatchers.IO){GroupChat.leave(wallet,entry.group.id,System.currentTimeMillis())}
                withContext(NonCancellable){publish{store.prepareLeave(candidate);store.entries()}}
                leaving=false
                sendPrepared(entry.group.id,candidate,wallet)
            }
        }){Text(stringResource(R.string.group_leave_confirm))}})
    if(members && entry!=null) AlertDialog(onDismissRequest={members=false},title={Text(stringResource(R.string.group_members))},
        text={LazyColumn(Modifier.heightIn(max=320.dp)){items(entry.group.memberRoles.toList(),key={it.first}){(key,role)->
            Column(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                ChatIdentityRow(key,registeredIdentity(key).name,stringResource(when(role){
                ChatGroupRole.ADMIN -> R.string.group_role_admin
                ChatGroupRole.MEMBER -> R.string.group_role_member
                ChatGroupRole.LEFT -> R.string.group_role_left
                ChatGroupRole.REMOVED -> R.string.group_role_removed
                }))
                Text(key,Modifier.padding(start=52.dp),style=MaterialTheme.typography.labelSmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(role==ChatGroupRole.MEMBER && entry.group.memberRoles[wallet.publicKey]==ChatGroupRole.ADMIN) {
                    val promoteDescription=stringResource(R.string.group_promote_member_accessibility,key)
                    TextButton(enabled=loaded&&!busy&&!readFailed&&!entry.hasPendingAction,
                        modifier=Modifier.semantics{contentDescription=promoteDescription},onClick={promoting=key;members=false}) {
                        Text(stringResource(R.string.group_promote))
                    }
                }
            }
        }}},confirmButton={TextButton(onClick={members=false}){Text(stringResource(R.string.group_done))}})
    if(promoting!=null && entry!=null) AlertDialog(onDismissRequest={if(!busy)promoting=null},
        title={Text(stringResource(R.string.group_promote_title))},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(registeredIdentity(requireNotNull(promoting)).name)
            Text(requireNotNull(promoting),style=MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.group_promote_notice))
        }},
        dismissButton={TextButton(enabled=!busy,onClick={promoting=null}){Text(stringResource(R.string.group_cancel))}},
        confirmButton={TextButton(enabled=loaded&&!busy&&!readFailed&&!entry.hasPendingAction,onClick={
            val member=requireNotNull(promoting)
            act {
                val candidate=withContext(Dispatchers.IO){GroupChat.promote(wallet,entry.group,member,System.currentTimeMillis())}
                withContext(NonCancellable){publish{store.preparePromotion(candidate,member);store.entries()}}
                promoting=null
                sendPrepared(entry.group.id,candidate,wallet)
            }
        }){Text(stringResource(R.string.group_promote_confirm))}})
    if(inviting && entry!=null && entry.group.memberRoles[wallet.publicKey]?.active==true && entry.group.memberRoles.values.any{it==ChatGroupRole.ADMIN}) {
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
private fun JoinGroupDialog(backend:SaitoBackend,busy:Boolean,knownGroups:List<NamedChatGroup>,onDismiss:()->Unit,onJoin:(GroupInvitation)->Unit) {
    var input by remember { mutableStateOf("") }
    var invitation by remember { mutableStateOf<GroupInvitation?>(null) }
    var invalid by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }
    var savedDetails by remember { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    val focus=androidx.compose.ui.platform.LocalFocusManager.current
    val preview=invitation
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.group_join))},
        text={Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            if(preview==null){
                OutlinedTextField(input,{input=it;invalid=false},label={Text(stringResource(R.string.group_invitation_link))},enabled=!busy&&!resolving)
                if(invalid)Text(stringResource(R.string.group_invitation_invalid),color=MaterialTheme.colorScheme.error)
            }else{
                Text(preview.name,style=MaterialTheme.typography.titleMedium)
                Text(stringResource(if(savedDetails)R.string.group_invitation_saved else R.string.group_invitation_trust),style=MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.group_admin_label));Text(preview.admin,style=MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.group_inviter_label));Text(preview.sender,style=MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.group_id_label));Text(preview.id,style=MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.group_privacy),style=MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.group_join_backend,backend.domain),style=MaterialTheme.typography.bodySmall)
            }
        }},dismissButton={TextButton(enabled=!busy&&!resolving,onClick=onDismiss){Text(stringResource(R.string.group_cancel))}},
        confirmButton={TextButton(enabled=!busy&&!resolving&&(preview!=null||input.isNotBlank()),onClick={
            if(preview!=null)onJoin(preview) else {
                resolving=true
                scope.launch {
                    try {
                        val resolved=withContext(Dispatchers.Default){
                            val parsed=GroupInvitation.parse(input)
                            val known=knownGroups.firstOrNull{it.id==parsed.id}
                            (known?.let{parsed.usingKnownGroup(it)} ?: parsed) to (known!=null)
                        }
                        invitation=resolved.first;savedDetails=resolved.second;focus.clearFocus()
                    } catch(cancelled:CancellationException){throw cancelled}
                    catch(_:Exception){invalid=true}
                    finally{resolving=false}
                }
            }
        }){Text(stringResource(if(preview==null)R.string.group_preview else R.string.group_join_confirm))}})
}
