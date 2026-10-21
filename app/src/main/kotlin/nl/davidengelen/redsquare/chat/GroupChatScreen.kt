package nl.davidengelen.redsquare.chat

import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone

import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.lifecycle.ViewModelProvider
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.moderation.LocalPersonalControls
import nl.davidengelen.redsquare.moderation.PersonalControlsStatus
import nl.davidengelen.redsquare.registry.registeredIdentity

private enum class GroupStatusSection { HISTORY, MANAGEMENT }

/** Named-group UI is separate from private Chat and shares the selected wallet/backend. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GroupChatScreen(wallet: SaitoWallet, endpoint: String, backend: SaitoBackend, modifier: Modifier,
    onWalletMetadata: suspend (SaitoWallet,SaitoWallet) -> Unit, initialGroup: String? = null, onInitialDestinationClosed: () -> Unit = {}, onBack: () -> Unit) {
    val gifCredits = nl.davidengelen.redsquare.media.LocalGiphyAttributions.current
    val context = LocalContext.current.applicationContext
    val publishing = nl.davidengelen.redsquare.policy.LocalPublishingAgreement.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val personal = LocalPersonalControls.current
    val store = remember(wallet,backend) { GroupChatStore(context,wallet,backend) }
    var entries by remember(wallet.publicKey,backend) { mutableStateOf<List<GroupChatStore.Entry>>(emptyList()) }
    var selected by remember(wallet.publicKey,backend) { mutableStateOf(initialGroup) }
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
    val activity=requireNotNull(LocalActivity.current) as ComponentActivity
    val draftMemory=remember(activity,wallet.publicKey,backend,selected){
        ViewModelProvider(activity)["group-chat:${wallet.publicKey}:${backend.id}:${selected.orEmpty()}",ChatComposerMemory::class.java]
    }
    var message by remember(wallet.publicKey,backend,selected){mutableStateOf("")}
    fun changeMessage(value:String){message=value;draftMemory.text=value}
    var draftReady by remember(store,selected){mutableStateOf(false)}
    var draftFailed by remember(wallet.publicKey,backend,selected){mutableStateOf(false)}
    var preparingPhoto by remember(wallet.publicKey,backend,selected){mutableStateOf(false)}
    var savingForNavigation by remember(wallet.publicKey,backend,selected){mutableStateOf(false)}
    val draftWrites=draftMemory.writes
    val navigation=requireNotNull(nl.davidengelen.redsquare.feed.LocalPostNavigation.current)
    suspend fun persistDraft(allowBusy:Boolean=false):Boolean = withContext(Dispatchers.Main.immediate) {
        draftWrites.withLock {
            if(preparingPhoto || (busy&&!allowBusy))return@withLock false
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
    LaunchedEffect(store,selected,refresh) {
        if(draftReady)return@LaunchedEffect // Refresh must not replace unsaved edited text.
        val id=selected ?: return@LaunchedEffect
        try {
            draftWrites.withLock {
                val saved=withContext(Dispatchers.IO){requireNotNull(store.load(id))}
                changeMessage(if(saved.hasPendingAction)saved.draft else draftMemory.text ?: saved.draft)
                draftReady=true;draftFailed=false
            }
        } catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){draftFailed=true}
    }
    LaunchedEffect(draftReady,message,preparingPhoto) {
        if(!draftReady||busy||preparingPhoto)return@LaunchedEffect
        delay(300)
        persistDraft()
    }
    fun act(requiresAgreement: Boolean = true, operation:suspend()->Unit) {
        if(busy||savingForNavigation||preparingPhoto||navigation.transitioning)return
        busy=true;sendFailed=false
        scope.launch {
            try{
                if (requiresAgreement && !publishing.beforePublishing()) return@launch
                if(persistDraft(allowBusy=true))operation()
            }
            catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){
                sendFailed=true
                try { withContext(NonCancellable+Dispatchers.IO) {
                    if(store.entries().any{it.queuedMessage!=null})ChatQueueScheduling.enqueue(context,wallet,backend,endpoint)
                }}catch(_:Exception){/* The queued original remains protected for the next session. */}
            }
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
    var stopping by remember(selected) { mutableStateOf(false) }
    var removing by remember(selected) { mutableStateOf<String?>(null) }
    var leaving by remember(selected) { mutableStateOf(false) }
    var inviting by remember(selected) { mutableStateOf(false) }
    DisposableEffect(store,selected,navigation) {
        val save:suspend()->Boolean={
            savingForNavigation=true
            try{persistDraft()}finally{savingForNavigation=false}
        }
        val guarded:suspend(suspend()->Boolean)->Boolean={commit->
            if(navigation.saveCurrent!==save || !loaded || readFailed || busy || preparingPhoto ||
                savingForNavigation || (selected!=null&&!draftReady) || creating || joining || members ||
                promoting!=null || stopping || removing!=null || leaving || inviting) false
            else {
                savingForNavigation=true
                try{persistDraft() && navigation.saveCurrent===save && commit()}
                finally{savingForNavigation=false}
            }
        }
        navigation.saveCurrent=save
        navigation.guardedCurrent=guarded
        onDispose{
            if(navigation.saveCurrent===save)navigation.saveCurrent=null
            if(navigation.guardedCurrent===guarded)navigation.guardedCurrent=null
        }
    }
    fun back() {
        if(busy||savingForNavigation||navigation.transitioning)return
        savingForNavigation=true
        scope.launch{
            try{if(persistDraft()){if(selected==null)onBack() else {
                if(selected==initialGroup)onInitialDestinationClosed()
                selected=null
            }}}
            finally{savingForNavigation=false}
        }
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
    LaunchedEffect(store,loaded,busy,selected) {
        if(!loaded||busy)return@LaunchedEffect
        GroupChatStore.changes.collect {
            try {
                val previous=entries.firstOrNull{it.group.id==selected}?.pendingMessage?.id
                publish{store.entries()}
                val current=entries.firstOrNull{it.group.id==selected}
                if(previous!=null&&current?.candidate?.id==previous&&
                    current.status in listOf(GroupChatStore.Status.FORWARDED,GroupChatStore.Status.AVAILABLE))sendFailed=false
            }catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){readFailed=true}
        }
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
                            try{receiveChat(context,wallet,backend,bytes,endpoint)}
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
    val keyboardVisible = WindowInsets.isImeVisible
    var details by remember(wallet.publicKey, backend, selected) { mutableStateOf(false) }
    val entry = entries.firstOrNull { it.group.id == selected }
    val staleAction = entry?.let(store::isStaleAdminAction) == true
    // Queued controls sit below this viewport. Keep management at its scroll end
    // there, while ordinary conversations retain their direct management access.
    val statusSections = if (entry?.queuedMessage != null)
        listOf(GroupStatusSection.HISTORY, GroupStatusSection.MANAGEMENT)
    else listOf(GroupStatusSection.MANAGEMENT, GroupStatusSection.HISTORY)
    BoxWithConstraints(modifier.fillMaxSize()) {
    val statusMaxHeight=minOf(maxHeight/3,80.dp)
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            TextButton(enabled=!busy&&!savingForNavigation&&!navigation.transitioning,onClick={back()}) { Text(stringResource(R.string.browse_back)) }
            Column(Modifier.weight(1f)) {
                Text(entry?.group?.name ?: stringResource(R.string.group_title), style=MaterialTheme.typography.titleMedium,
                    maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(stringResource(R.string.group_unencrypted),style=MaterialTheme.typography.labelSmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            entry?.let { ConversationMuteButton(it.group.id) }
            val detailsLabel = stringResource(R.string.chat_conversation_details)
            if (entry != null) TextButton(onClick = { details = true },
                modifier = Modifier.semantics { contentDescription = detailsLabel }) { Text(stringResource(R.string.status_details)) }
            else TextButton(enabled=!busy&&!paging&&!navigation.transitioning,onClick={if(!navigation.transitioning)refresh++}) { Text(stringResource(R.string.feed_refresh)) }
        }
        if (entry == null) Text(stringResource(R.string.group_privacy),style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        // Full status text stays scrollable while queued controls keep their space.
        val statusLabel=stringResource(R.string.group_status_actions)
        val statusContent: @Composable (Boolean) -> Unit = { expanded ->
        Column(
            verticalArrangement=Arrangement.spacedBy(4.dp)) {
            InlineStatus(if (hintsFailed) stringResource(R.string.group_hints_error) else null, StatusTone.WARNING)
            PersonalControlsStatus(personal)
            InlineStatus(if (readFailed || draftFailed) stringResource(R.string.group_error) else null, StatusTone.ERROR)
            InlineStatus(if (sendFailed) stringResource(if(entry?.automaticMessage==true&&entry.pendingMessage!=null)
                R.string.chat_automatic_send_error else R.string.group_error) else null, StatusTone.ERROR)
            InlineStatus(if (historyFailed) stringResource(R.string.group_history_error) else null, StatusTone.WARNING)
            InlineStatus(if (liveFailed) stringResource(R.string.group_live_error) else null, StatusTone.WARNING)
            if(entry!=null) {
                InlineStatus(if (entry.queuedMessage==null && (expanded || entry.status in listOf(
                    GroupChatStore.Status.UNKNOWN, GroupChatStore.Status.SUBMITTED,
                    GroupChatStore.Status.FORWARDED, GroupChatStore.Status.STOPPED))) stringResource(when(entry.status) {
                    GroupChatStore.Status.UNKNOWN -> R.string.group_unknown
                    GroupChatStore.Status.SUBMITTED -> R.string.group_submitted
                    GroupChatStore.Status.FORWARDED -> R.string.group_forwarded
                    GroupChatStore.Status.STOPPED -> R.string.group_stopped
                    GroupChatStore.Status.RECEIVED -> if(entry.ownCreation)R.string.group_recovered else R.string.group_received
                    GroupChatStore.Status.AVAILABLE -> R.string.chat_send_available
                }) else null, if (entry.status == GroupChatStore.Status.UNKNOWN) StatusTone.WARNING else StatusTone.INFO,
                    actionLabel = if(entry.hasPendingAction&&entry.queuedMessage==null) stringResource(R.string.group_retry) else null,
                    actionEnabled = loaded&&!busy&&!readFailed&&!staleAction&&!navigation.transitioning,
                    onAction = {
                        act(requiresAgreement = false) {
                            val pending=requireNotNull(entry.candidate)
                            if (groupActionRequiresAgreement(pending) && !publishing.beforePublishing()) return@act
                            withContext(NonCancellable){publish{store.prepareRetry(entry.group.id,pending.id);store.entries()}}
                            val updated=withContext(Dispatchers.IO){wallet.withNamedChatGroup(entry.group.id,entry.group.name,entry.creationKey)}
                            val creation=withContext(Dispatchers.IO){SaitoEnvelope.decode(pending.envelope()).body.get("request").asString=="chat group"}
                            if(entry.automaticMessage) {
                                withContext(Dispatchers.IO){submitGroupMessage(context,wallet,backend,endpoint,entry.group.id,pending)}
                                publish{store.entries()}
                            } else sendPrepared(entry.group.id,pending,updated,originalRelay=creation)
                        }
                    })
            }
            if (!expanded && entry?.group?.memberRoles?.get(wallet.publicKey) == ChatGroupRole.LEFT)
                Text(stringResource(R.string.group_left))
            statusSections.forEach { section ->
                key(section) {
                    when (section) {
                        GroupStatusSection.HISTORY -> InlineStatus(
                            if (expanded || entry == null && !keyboardVisible) stringResource(R.string.status_available_history) else null,
                            details = listOf(stringResource(R.string.chat_available_history)), announce = false)
                        GroupStatusSection.MANAGEMENT -> if(expanded && entry!=null) {
                            if(entry.group.confirmedInvitation!=null) Text(stringResource(R.string.group_invitation_origin),
                                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow {
                                TextButton(enabled=!navigation.transitioning,onClick={if(!navigation.transitioning){details=false;members=true}}) { Text(stringResource(R.string.group_members)) }
                                if(entry.group.memberRoles[wallet.publicKey]?.active==true) {
                                    TextButton(enabled=!busy&&!navigation.transitioning&&entry.group.memberRoles.values.any{it==ChatGroupRole.ADMIN},onClick={if(!navigation.transitioning){details=false;inviting=true}}) { Text(stringResource(R.string.group_invite)) }
                                    TextButton(enabled=!busy&&!navigation.transitioning&&!entry.hasPendingAction,onClick={if(!navigation.transitioning){details=false;leaving=true}}) { Text(stringResource(R.string.group_leave)) }
                                }
                            }

                            if(staleAction) {
                                Text(stringResource(R.string.group_stale_action),color=MaterialTheme.colorScheme.error)
                                TextButton(enabled=loaded&&!busy&&!readFailed&&!navigation.transitioning,onClick={if(!navigation.transitioning){details=false;stopping=true}}){Text(stringResource(R.string.group_stop_action))}
                            }
                            if(entry.stoppedActions.isNotEmpty()&&entry.status!=GroupChatStore.Status.STOPPED)
                                Text(stringResource(R.string.group_stopped_history),style=MaterialTheme.typography.bodySmall)
                            if(entry.group.memberRoles[wallet.publicKey]==ChatGroupRole.LEFT) Text(stringResource(R.string.group_left))
                        }
                    }
                }
            }
        }
        }
        Column(Modifier.heightIn(max=statusMaxHeight).verticalScroll(rememberScrollState())
            .semantics{contentDescription=statusLabel}) { statusContent(false) }
        if (details && entry != null) ConversationDetails(onDismiss = { details = false }) {
            Text(entry.group.name, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.group_privacy), style = MaterialTheme.typography.bodyMedium)
            androidx.compose.foundation.text.selection.SelectionContainer { Text(entry.group.id) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled=!busy&&!paging&&!navigation.transitioning,
                    onClick={if(!navigation.transitioning)refresh++}) { Text(stringResource(R.string.feed_refresh)) }
            }
            statusContent(true)
        }
        if (entry != null && hasOlder && olderCursor != null) TextButton(
            enabled=!busy&&!paging&&!navigation.transitioning,
            onClick={if(!initialLoading&&!olderLoading&&!busy&&!navigation.transitioning){olderLoading=true;olderRequest++}}) {
            Text(stringResource(R.string.chat_load_older))
        }
        if(entry==null) {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(enabled=loaded&&!busy&&!navigation.transitioning,onClick={if(!navigation.transitioning)creating=true}) { Text(stringResource(R.string.group_create)) }
                OutlinedButton(enabled=loaded&&!busy&&!navigation.transitioning,onClick={if(!navigation.transitioning)joining=true}) { Text(stringResource(R.string.group_join)) }
            }
            if(discoveryHasOlder&&discoveryCursor!=null)TextButton(enabled=!busy&&!discoveryLoading&&!navigation.transitioning,onClick={if(!navigation.transitioning)discoveryRequest++}){
                Text(stringResource(R.string.group_discovery_older))
            }
            LazyColumn(Modifier.weight(1f)) {
                items(entries,key={it.group.id}) { row ->
                    TextButton(enabled=!busy&&!navigation.transitioning,onClick={if(!navigation.transitioning)selected=row.group.id},modifier=Modifier.fillMaxWidth()) {
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
            if (entry.automaticMessage && entry.pendingMessage != null)
                nl.davidengelen.redsquare.policy.QueuedPublishingAgreement(loaded && !busy && !readFailed && !navigation.transitioning)
            val queuedMessage = entry.queuedMessage
            InlineStatus(if (queuedMessage != null) stringResource(R.string.chat_queued) else null,
                actionLabel = stringResource(R.string.chat_cancel_queued),
                actionEnabled = loaded&&!busy&&!readFailed&&!navigation.transitioning,
                onAction = {
                    if (queuedMessage != null) act(requiresAgreement = false) {
                        draftWrites.withLock {
                            withContext(NonCancellable){
                                publish{
                                    store.cancelQueued(entry.group.id,queuedMessage.candidate.id);store.entries()
                                }
                                changeMessage(withContext(Dispatchers.IO){requireNotNull(store.load(entry.group.id)).draft})
                            }
                        }
                    }
                })
            val messagesLabel=stringResource(R.string.group_messages)
            var latestRequest by remember(wallet.publicKey, backend, entry.group.id) { mutableIntStateOf(0) }
            val visible=entry.messages.filter{personal?.ready==true &&
                (it.author==wallet.publicKey || !personal.policy.hidesIncomingChat(it.author))}
            val editable=personal?.ready==true&&draftReady&&!busy&&!savingForNavigation&&!navigation.transitioning&&
                !entry.hasPendingAction&&entry.group.memberRoles[wallet.publicKey]?.active==true
            val attachmentPermitted=editable&&loaded&&!readFailed
            val permitted=attachmentPermitted&&!preparingPhoto
            val messageKeys = visible.map { it.id } + entry.queuedMessage?.let { listOf("queued:${it.candidate.id}") }.orEmpty()
            ConversationMessageList(messageKeys, "${wallet.publicKey}:${backend.id}:group:${entry.group.id}",
                messagesLabel, Modifier.weight(1f),
                historyHidden = personal?.ready == true && entry.messages.isNotEmpty() && visible.isEmpty(),
                requestLatest = latestRequest, historyMessageKeys = visible.map { it.id },
                verticalArrangement = Arrangement.spacedBy(8.dp)) { showMessage ->
                items(visible,key={it.id}){item->
                    val outgoing=item.author==wallet.publicKey
                    Column(Modifier.fillMaxWidth().padding(vertical=6.dp),
                        horizontalAlignment=if(outgoing)Alignment.End else Alignment.Start){
                        var copyFailed by remember(item.id){mutableStateOf(false)}
                        val replyPrefix=remember(item){chatReplyPrefix(item.author,item.createdAt,item.text)}
                        val likedBy=entry.likedBy(item)
                        ChatMessageBubble(item.author,item.text,item.createdAt,outgoing,backend=backend,selectable=true,
                            actions = {
                            ChatMessageActions(wallet.publicKey in likedBy, permitted, permitted && replyPrefix != null,
                                onLike = {
                            act {
                                val candidate=withContext(Dispatchers.IO){GroupChat.like(wallet,entry.group,item,System.currentTimeMillis())}
                                withContext(NonCancellable){publish{store.prepareReaction(candidate);store.entries()}}
                                sendPrepared(entry.group.id,candidate,wallet)
                            }
                        }, onReply = {
                            if(!navigation.transitioning)replyPrefix?.let{changeMessage(it+privateMessageBody(message))}
                        }, onCopy = {copyFailed=!copyPrivateMessage(context,item.text)})
                        }) { reference ->
                            val index=visible.indexOfFirst{it.author+it.createdAt==reference}
                            if(index>=0)showMessage(index)
                        }
                        if(likedBy.isNotEmpty())Text(pluralStringResource(R.plurals.chat_likes,likedBy.size,likedBy.size))
                        if(copyFailed)Text(stringResource(R.string.chat_copy_error))
                    }
                }
                entry.queuedMessage?.let { queued -> item(key="queued:${queued.candidate.id}") {
                    val pending=queued.message
                    ChatMessageBubble(pending.author,pending.text,pending.createdAt,true,backend=backend,onQuote={})
                } }
            }
            LaunchedEffect(entry.group.id,entry.queuedMessage?.candidate?.id) {
                if(entry.queuedMessage!=null)latestRequest++
            }
            if(entry.queuedMessage==null) {
                val quote=remember(message){privateReply(message)}
                val body=quote?.body ?: message
                val photo=remember(body){chatPhoto(body)}
                val typed=photo?.text ?: body
                BoxWithConstraints {
                    val previewMaxHeight=maxHeight/3
                    val previewScroll=rememberScrollState()
                    val previewLabel=stringResource(R.string.chat_composition_preview)
                    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Column((if(quote!=null||photo!=null)Modifier.heightIn(max=previewMaxHeight).verticalScroll(previewScroll) else Modifier)
                            .semantics{contentDescription=previewLabel},verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            if(quote!=null){
                                Text(quote.quote,style=MaterialTheme.typography.bodyMedium)
                                TextButton(enabled=editable,onClick={if(!navigation.transitioning)changeMessage(body)}){Text(stringResource(R.string.chat_remove_reply))}
                            }
                            ChatPhotoAttachment("group:${wallet.publicKey}:${backend.id}:${entry.group.id}",body,backend,attachmentPermitted,
                                beforePicker={!navigation.transitioning&&persistDraft()&&!navigation.transitioning},onPreparing={preparingPhoto=it},
                                onChange={if(!navigation.transitioning)changeMessage((quote?.prefix ?: "")+it)},
                                gifAttribution=entry.gifAttribution,
                                showPickerActions=!entry.hasPendingAction,
                                onSelectGif={item->
                                    draftWrites.withLock {
                                        if(!attachmentPermitted||selected!=entry.group.id||navigation.transitioning)false
                                        else {
                                            val next=composeChatGif(message,item.originalUrl)
                                            if(next==null)false else {
                                                withContext(NonCancellable){
                                                    withContext(Dispatchers.IO){store.assertSelected()}
                                                    publish{store.saveDraft(entry.group.id,next,item.attribution);store.entries()}
                                                    changeMessage(next)
                                                }
                                                draftFailed=false
                                                true
                                            }
                                        }
                                    }
                                })
                        }
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            OutlinedTextField(typed,{value->if(!navigation.transitioning)changeMessage((quote?.prefix ?: "")+(photo?.let{composeChatPhoto(it.source,value)} ?: value))},Modifier.weight(1f),
                                label={Text(stringResource(R.string.chat_message))},enabled=editable,maxLines=4)
                            Button(enabled=permitted&&(typed.isNotBlank()||photo!=null),onClick={
                                focus.clearFocus()
                                keyboard?.hide()
                                act {
                                    val mentioned=quote?.author?.let{listOf(it)}.orEmpty()
                                    val candidate=withContext(Dispatchers.IO){
                                        entry.gifAttribution?.let{credit->
                                            val source=chatPhoto(privateMessageBody(message))?.source
                                            if(source!=null&&chatPhoto(privateMessageBody(entry.draft))?.source==source)
                                                requireNotNull(gifCredits).retain(mapOf(source to credit))
                                        }
                                        GroupChat.message(wallet,entry.group,message,System.currentTimeMillis(),mentioned)
                                    }
                                    draftWrites.withLock {
                                        withContext(NonCancellable){
                                            publish{store.prepareMessage(candidate,queued=true);store.entries()}
                                            changeMessage("")
                                        }
                                    }
                                    withContext(Dispatchers.IO){submitGroupMessage(context,wallet,backend,endpoint,entry.group.id,candidate)}
                                    publish{store.entries()}
                                }
                            }){Text(stringResource(R.string.chat_send))}
                        }
                    }
                }
            }
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
    if(stopping && entry!=null) AlertDialog(onDismissRequest={if(!busy)stopping=false},
        title={Text(stringResource(R.string.group_stop_action))},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(stringResource(R.string.group_stop_notice))
            InlineStatus(if (sendFailed||draftFailed) stringResource(R.string.group_error) else null, StatusTone.ERROR)
        }},
        dismissButton={TextButton(enabled=!busy,onClick={stopping=false}){Text(stringResource(R.string.group_cancel))}},
        confirmButton={TextButton(enabled=loaded&&!busy&&!readFailed&&store.isStaleAdminAction(entry),onClick={
            act(requiresAgreement = false) {
                val pending=requireNotNull(entry.candidate)
                withContext(NonCancellable){publish{store.stopStaleAction(entry.group.id,pending.id);store.entries()}}
                stopping=false
            }
        }){Text(stringResource(R.string.group_stop_confirm))}})
    if(leaving && entry!=null) AlertDialog(onDismissRequest={if(!busy)leaving=false},
        title={Text(stringResource(R.string.group_leave))},
        text={Text(stringResource(R.string.group_leave_notice))},
        dismissButton={TextButton(enabled=!busy,onClick={leaving=false}){Text(stringResource(R.string.group_cancel))}},
        confirmButton={TextButton(enabled=!busy,onClick={
            act(requiresAgreement = false) {
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
                    val removeDescription=stringResource(R.string.group_remove_member_accessibility,key)
                    TextButton(enabled=loaded&&!busy&&!readFailed&&!entry.hasPendingAction,
                        modifier=Modifier.semantics{contentDescription=removeDescription},onClick={removing=key;members=false}) {
                        Text(stringResource(R.string.group_remove))
                    }
                }
            }
        }}},confirmButton={TextButton(onClick={members=false}){Text(stringResource(R.string.group_done))}})
    if(promoting!=null && entry!=null) MemberManagementDialog(requireNotNull(promoting),
        R.string.group_promote_title,R.string.group_promote_notice,R.string.group_promote_confirm,
        entry.group,wallet.publicKey,busy,loaded&&!busy&&!readFailed&&!entry.hasPendingAction,
        sendFailed||draftFailed,onDismiss={promoting=null}) {
        val member=requireNotNull(promoting)
        act {
            val candidate=withContext(Dispatchers.IO){GroupChat.promote(wallet,entry.group,member,System.currentTimeMillis())}
            withContext(NonCancellable){publish{store.preparePromotion(candidate,member);store.entries()}}
            promoting=null
            sendPrepared(entry.group.id,candidate,wallet)
        }
    }
    if(removing!=null && entry!=null) MemberManagementDialog(requireNotNull(removing),
        R.string.group_remove_title,R.string.group_remove_notice,R.string.group_remove_confirm,
        entry.group,wallet.publicKey,busy,loaded&&!busy&&!readFailed&&!entry.hasPendingAction,
        sendFailed||draftFailed,onDismiss={removing=null}) {
        val member=requireNotNull(removing)
        act(requiresAgreement = false) {
            val candidate=withContext(Dispatchers.IO){GroupChat.removeMember(wallet,entry.group,member,System.currentTimeMillis())}
            withContext(NonCancellable){publish{store.prepareRemoval(candidate,member);store.entries()}}
            removing=null
            sendPrepared(entry.group.id,candidate,wallet)
        }
    }
    if(inviting && entry!=null && entry.group.memberRoles[wallet.publicKey]?.active==true && entry.group.memberRoles.values.any{it==ChatGroupRole.ADMIN}) {
        val invitation = remember(entry.group,backend) { GroupInvitation.fromGroup(entry.group,wallet.publicKey).link(backend) }
        AlertDialog(onDismissRequest={inviting=false},title={Text(stringResource(R.string.group_invitation_link))},
            text={SelectionContainer{Text(invitation,Modifier.semantics{contentDescription="Group invitation link"},style=MaterialTheme.typography.bodySmall)}},
            confirmButton={TextButton(onClick={inviting=false}){Text(stringResource(R.string.group_done))}})
    }
}

@Composable
private fun MemberManagementDialog(member:String,title:Int,notice:Int,confirm:Int,group:NamedChatGroup,owner:String,
    busy:Boolean,enabled:Boolean,failed:Boolean,
    onDismiss:()->Unit,onConfirm:()->Unit) {
    val permitted=group.memberRoles[owner]==ChatGroupRole.ADMIN&&group.memberRoles[member]==ChatGroupRole.MEMBER
    AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text(stringResource(title))},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(registeredIdentity(member).name)
            Text(member,style=MaterialTheme.typography.bodySmall)
            Text(stringResource(notice))
            if(!permitted)Text(stringResource(R.string.group_management_changed),color=MaterialTheme.colorScheme.error)
            InlineStatus(if (failed) stringResource(R.string.group_error) else null, StatusTone.ERROR)
        }},dismissButton={TextButton(enabled=!busy,onClick=onDismiss){Text(stringResource(R.string.group_cancel))}},
        confirmButton={TextButton(enabled=enabled&&permitted,onClick=onConfirm){Text(stringResource(confirm))}})
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
