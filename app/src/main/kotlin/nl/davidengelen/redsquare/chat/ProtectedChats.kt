package nl.davidengelen.redsquare.chat

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.feed.LocalPostNavigation
import nl.davidengelen.redsquare.policy.LocalPublishingAgreement
import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceClient
import nl.davidengelen.redsquare.spaces.SpacesAccount
import nl.davidengelen.redsquare.spaces.RecoveredHistoryPicker
import nl.davidengelen.redsquare.spaces.ReplaceArchivedDevicePrompt
import nl.davidengelen.redsquare.spaces.SpacesLaunchRoutes
import nl.davidengelen.redsquare.ui.FrameIconButton
import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone

internal class ProtectedChatAccess(val account: SpacesAccount?, val activate: () -> Unit)
internal val LocalProtectedChats = staticCompositionLocalOf<ProtectedChatAccess?> { null }

@Composable
internal fun ProtectedChats(access: ProtectedChatAccess, owner: String, startingName: String?,
    startingPeer: String?, legacyGroup: String?, legacyMembers: List<String>, modifier: Modifier,
    initialInvitation: String?, onInvitationConsumed: () -> Unit,
    onBack: () -> Unit) {
    LaunchedEffect(Unit) { access.activate() }
    val account = access.account
    if (account == null) {
        BackHandler(onBack = onBack)
        Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val state by account.state.collectAsState()
    val navigation = LocalPostNavigation.current
    val publishing = LocalPublishingAgreement.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(account, lifecycle, navigation) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) account.visible(true)
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) account.visible(false)
        }
        lifecycle.addObserver(observer)
        val save: suspend () -> Boolean = { account.guardNavigation { true } }
        val guard: suspend (suspend () -> Boolean) -> Boolean = { account.guardNavigation(it) }
        navigation?.saveCurrent = save
        navigation?.guardedCurrent = guard
        onDispose {
            lifecycle.removeObserver(observer)
            if (navigation?.saveCurrent === save) navigation.saveCurrent = null
            if (navigation?.guardedCurrent === guard) navigation.guardedCurrent = null
            account.select(null)
            account.visible(false)
        }
    }
    var selected by rememberSaveable(owner, account.backend) { mutableStateOf<String?>(null) }
    var dialog by rememberSaveable(owner, account.backend) { mutableStateOf<String?>(null) }
    val legacyId = legacyGroup ?: startingPeer
    val legacyChat = if (legacyGroup != null) "group:$legacyGroup" else startingPeer?.let { "direct:$it" }
    var openingLegacy by rememberSaveable(owner, account.backend, legacyId) { mutableStateOf(legacyId != null) }
    var migrating by rememberSaveable(owner, account.backend) { mutableStateOf(false) }
    var name by rememberSaveable(owner, account.backend) { mutableStateOf(startingName.orEmpty()) }
    var peer by rememberSaveable(owner, account.backend) { mutableStateOf(startingPeer.orEmpty()) }
    var group by rememberSaveable(owner, account.backend) { mutableStateOf(startingPeer == null && startingName != null) }
    var link by remember { mutableStateOf("") }
    var invitee by rememberSaveable(owner, account.backend) { mutableStateOf("") }
    val current = state.current?.takeIf { it.protectedChat != null && selected == it.id }
    val conversations = remember(state.communities) { state.communities.filter { it.protectedChat != null && !it.dissolved } }
    val online = state.availability == SpacesAccount.Availability.ONLINE
    val enabled = !state.loading && !state.working && !state.savingNavigation && navigation?.transitioning != true
    LaunchedEffect(account, state.loading) {
        if (!state.loading) {
            account.dismissPreview()
            account.select(selected)
        }
    }
    LaunchedEffect(state.loading, conversations, openingLegacy) {
        if (!state.loading && openingLegacy) {
            openingLegacy = false
            val existing = conversations.firstOrNull { it.legacyChat == legacyChat }
            if (existing == null) {
                migrating = true
                name = startingName.orEmpty()
                peer = startingPeer.orEmpty()
                group = legacyGroup != null
                dialog = "create"
            } else {
                selected = existing.id
                account.select(existing.id)
            }
        }
    }
    LaunchedEffect(initialInvitation, state.loading, state.working) {
        if (initialInvitation != null && !state.loading && !state.working) {
            link = initialInvitation
            dialog = "join"
            account.preview(initialInvitation)
            onInvitationConsumed()
        }
    }
    LaunchedEffect(state.created, state.selected, state.communities) {
        val created = state.created ?: return@LaunchedEffect
        if (state.selected == created && state.communities.any { it.id == created && it.protectedChat != null }) {
            selected = created
            account.consumeCreated(created)
            dialog = null
        }
    }
    LaunchedEffect(state.joinedInvitation, state.selected) {
        val joined = state.joinedInvitation ?: return@LaunchedEffect
        if (joined == link) {
            selected = state.selected
            account.consumeJoinedInvitation(joined)
            link = ""
            dialog = null
        }
    }
    LaunchedEffect(state.openedRecovery, dialog) {
        val archive = state.openedRecovery ?: return@LaunchedEffect
        if (dialog == "archive") {
            selected = archive.spaceId
            dialog = null
            account.consumeRecoveredHistory(archive)
        }
    }
    fun back() {
        scope.launch {
            account.guardNavigation {
                val wasInChat = selected != null
                selected = null
                account.select(null)
                if (!wasInChat) onBack()
                true
            }
        }
    }
    BackHandler(dialog == null) { back() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FrameIconButton(R.drawable.ic_back, stringResource(R.string.browse_back), enabled = enabled, onClick = { back() })
            Column(Modifier.weight(1f)) {
                Text(current?.name ?: stringResource(R.string.protected_chat_title),
                    style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.chat_encrypted), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = account::refresh, enabled = enabled && !state.refreshing) {
                Text(stringResource(R.string.feed_refresh))
            }
        }
        InlineStatus(
            when {
                state.problem == SpacesAccount.Problem.PROTECTED_CHAT_UNSUPPORTED ||
                    state.availability == SpacesAccount.Availability.UNSUPPORTED -> stringResource(R.string.protected_chat_unsupported)
                state.availability == SpacesAccount.Availability.CHANGED_IDENTITY -> stringResource(R.string.protected_chat_changed_node)
                state.availability == SpacesAccount.Availability.STORAGE_UNAVAILABLE -> stringResource(R.string.protected_chat_storage_failed)
                state.availability == SpacesAccount.Availability.OFFLINE -> stringResource(R.string.protected_chat_offline)
                state.problem != null && dialog == null -> stringResource(R.string.protected_chat_action_failed)
                else -> null
            }, StatusTone.WARNING
        )
        if (selected == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    name = ""; peer = ""; group = false; migrating = false; dialog = "create"
                }, enabled = enabled && online, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.protected_chat_new))
                }
                OutlinedButton(onClick = {
                    account.dismissPreview(); link = ""; dialog = "join"
                }, enabled = enabled && online, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.protected_chat_invitation))
                }
            }
            TextButton(enabled = enabled && online, onClick = { account.clearProblem(); dialog = "archive" }) {
                Text(stringResource(R.string.recovery_archive_title))
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                if (conversations.isEmpty() && !state.loading) {
                    item {
                        Text(stringResource(R.string.protected_chat_empty), Modifier.padding(vertical = 16.dp),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(conversations, key = { it.id }) { chat ->
                    Surface(onClick = { selected = chat.id; account.select(chat.id) }, enabled = enabled,
                        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                        ListItem(headlineContent = { Text(chat.name) },
                            supportingContent = {
                                Text(stringResource(when {
                                    chat.deviceReplaced -> R.string.recovery_device_replaced
                                    chat.replacingDevice -> R.string.recovery_device_waiting
                                    chat.replacementRejected -> R.string.recovery_device_rejected
                                    chat.archiveScope != null -> R.string.recovery_archive_read_only
                                    chat.hasUnread == true -> R.string.protected_chat_unread
                                    !chat.joined -> R.string.protected_chat_waiting
                                    chat.protectedChat?.directPeer == null -> R.string.protected_chat_group
                                    else -> R.string.protected_chat_direct
                                }))
                            })
                    }
                }
            }
        } else if (current == null) {
            if (state.loading || state.loadingChannel) LinearProgressIndicator(Modifier.fillMaxWidth())
            else Text(stringResource(R.string.protected_chat_unavailable))
        } else {
            if (current.archiveScope != null && !current.replacingDevice) {
                OutlinedButton(onClick = { dialog = "activate-archive" }, enabled = enabled && online) {
                    Text(stringResource(R.string.recovery_replace_device))
                }
            }
            val directPeer = current.protectedChat?.directPeer
            if (current.canManage && (directPeer == null || state.members.none { it.publicKey == directPeer })) {
                val invitation = state.invitation?.takeIf { it.confirmed && !it.expired && !it.revoked && !it.revocationPending }
                val shareText = stringResource(R.string.protected_chat_share_invite)
                val shareBody = invitation?.let { stringResource(R.string.protected_chat_share_body, it.link) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        invitee = directPeer.orEmpty(); dialog = "invite"
                    }, enabled = enabled && online && !current.pending) {
                        Text(stringResource(R.string.protected_chat_invite))
                    }
                    if (shareBody != null) TextButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareBody)
                        }
                        context.startActivity(Intent.createChooser(send, shareText))
                    }) { Text(shareText) }
                }
            }
            ProtectedConversation(account, state, current, enabled && current.canSend, Modifier.weight(1f))
        }
    }
    val preview = state.preview?.takeIf { it.protectedChat != null }
    val operationError = state.problem != null || state.previewIssue != null || state.requiredNode != null ||
        state.preview != null && preview == null
    when (dialog) {
        "archive" -> RecoveredHistoryPicker(account, state, protectedChat = true) { dialog = null }
        "activate-archive" -> current?.archiveScope?.let { archive ->
            ReplaceArchivedDevicePrompt(dismiss = { dialog = null }, confirm = {
                scope.launch {
                    if (publishing.beforePublishing()) {
                        dialog = null
                        account.replaceDevice(archive, expectProtectedChat = true)
                    }
                }
            })
        }
        "create" -> AlertDialog(onDismissRequest = { if (!state.working) { account.dismissCreation(); dialog = null } },
            title = { Text(stringResource(R.string.protected_chat_new)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(when {
                        migrating && group -> R.string.protected_chat_migrate_group
                        migrating -> R.string.protected_chat_migrate_direct
                        else -> R.string.protected_chat_create_detail
                    }))
                    if (!migrating) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !group, onClick = { group = false }, label = { Text(stringResource(R.string.protected_chat_direct)) })
                        FilterChip(selected = group, onClick = { group = true }, label = { Text(stringResource(R.string.protected_chat_group)) })
                    }
                    OutlinedTextField(name, { name = it.take(100) }, singleLine = true,
                        label = { Text(stringResource(R.string.protected_chat_name)) })
                    if (!group) OutlinedTextField(peer, { peer = it.take(128) }, singleLine = true,
                        readOnly = migrating,
                        label = { Text(stringResource(R.string.protected_chat_peer)) })
                    if (operationError) ProtectedChatError(state)
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        if (publishing.beforePublishing()) {
                            account.createProtectedChat(name.trim(), peer.trim().takeUnless { group }, legacyId.takeIf { migrating })
                        }
                    }
                }, enabled = enabled && online && name.isNotBlank() &&
                    (group || (peer.trim() != owner && SaitoWallet.isPublicKey(peer.trim())))) {
                    Text(stringResource(R.string.protected_chat_create))
                }
            },
            dismissButton = { TextButton(onClick = { account.dismissCreation(); dialog = null }, enabled = !state.working) {
                Text(stringResource(android.R.string.cancel))
            } })
        "join", "restore" -> AlertDialog(onDismissRequest = { if (!state.working) { account.dismissPreview(); dialog = null } },
            title = { Text(stringResource(if (dialog == "restore") R.string.recovery_replace_device else R.string.protected_chat_invitation)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(link, { link = it.take(8192); account.dismissPreview() },
                        maxLines = 3, label = { Text(stringResource(R.string.protected_chat_invitation_link)) }, enabled = !state.working)
                    if (dialog == "restore") {
                        Text(stringResource(R.string.recovery_replace_device_detail))
                        if (operationError) ProtectedChatError(state)
                    } else if (preview != null) {
                        Text(preview.name, style = MaterialTheme.typography.titleMedium)
                        preview.owner?.let { Text(stringResource(R.string.protected_chat_invited_by, registeredIdentity(it).name)) }
                    } else {
                        if (operationError) ProtectedChatError(state)
                        TextButton(onClick = { account.dismissPreview(); dialog = "restore" }, enabled = !state.working) {
                            Text(stringResource(R.string.recovery_replace_device))
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val invitation = link.lineSequence().map(String::trim)
                        .singleOrNull { SpacesLaunchRoutes.isProtectedChat(Uri.parse(it)) } ?: link.trim()
                    if (dialog == "restore") scope.launch {
                        if (publishing.beforePublishing()) {
                            link = invitation
                            account.replaceDevice(invitation, expectProtectedChat = true)
                        }
                    }
                    else if (preview == null) account.preview(invitation)
                    else scope.launch {
                        if (publishing.beforePublishing()) {
                            link = invitation
                            account.joinProtectedChat(invitation)
                        }
                    }
                }, enabled = enabled && online && link.isNotBlank()) {
                    Text(stringResource(when {
                        dialog == "restore" -> R.string.recovery_replace_confirm
                        preview == null -> R.string.protected_chat_review
                        else -> R.string.protected_chat_join
                    }))
                }
            },
            dismissButton = { TextButton(onClick = { account.dismissPreview(); dialog = null }, enabled = !state.working) {
                Text(stringResource(android.R.string.cancel))
            } })
        "invite" -> if (current != null) AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.protected_chat_invite)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(invitee, { invitee = it.take(128) }, singleLine = true,
                        readOnly = current.protectedChat?.directPeer != null,
                        label = { Text(stringResource(R.string.protected_chat_peer)) })
                    if (legacyGroup != null && current.legacyChat == legacyChat) {
                        val remaining = legacyMembers.filter { key -> state.members.none { it.publicKey == key } }
                        if (remaining.isNotEmpty()) LazyColumn(Modifier.heightIn(max = 192.dp)) {
                            items(remaining, key = { it }) { key ->
                                FilterChip(selected = invitee == key, onClick = { invitee = key },
                                    label = { Text(registeredIdentity(key).name) })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(enabled = enabled && online && SaitoWallet.isPublicKey(invitee.trim()) &&
                    state.members.none { it.publicKey == invitee.trim() }, onClick = {
                    scope.launch {
                        if (publishing.beforePublishing()) {
                            account.inviteProtectedChat(current.id, invitee.trim())
                            dialog = null
                        }
                    }
                }) { Text(stringResource(R.string.protected_chat_create_invite)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(android.R.string.cancel)) } })
    }
}

@Composable
private fun ProtectedChatError(state: SpacesAccount.State) {
    Text(stringResource(when {
        state.problem == SpacesAccount.Problem.PROTECTED_CHAT_UNSUPPORTED -> R.string.protected_chat_unsupported
        state.problem == SpacesAccount.Problem.DEVICE_RECOVERY_UNSUPPORTED -> R.string.recovery_device_unsupported
        state.problem == SpacesAccount.Problem.DEVICE_RECOVERY_NO_APPROVER -> R.string.recovery_device_no_approver
        state.problem == SpacesAccount.Problem.DEVICE_ALREADY_ACTIVE -> R.string.recovery_device_already_active
        state.requiredNode != null -> R.string.protected_chat_invite_node
        state.previewIssue != null || state.preview != null -> R.string.protected_chat_invite_denied
        else -> R.string.protected_chat_action_failed
    }), color = MaterialTheme.colorScheme.error)
}

@Composable
private fun ProtectedConversation(account: SpacesAccount, state: SpacesAccount.State, current: SpaceClient.Community,
    canSend: Boolean, modifier: Modifier) {
    val list = rememberLazyListState()
    val publishing = LocalPublishingAgreement.current
    val scope = rememberCoroutineScope()
    val editable = current.joined && !state.savingNavigation && !state.loadingChannel
    val messages by rememberUpdatedState(state.messages)
    val canMarkRead by rememberUpdatedState(current.joined && state.followingLatest && !state.hasLater)
    LaunchedEffect(account, current.id, current.channel, list) {
        snapshotFlow {
            val latest = messages.lastOrNull { it.position != null }
            latest?.position?.takeIf { canMarkRead && list.layoutInfo.visibleItemsInfo.any { item -> item.key == latest.id } }
        }.distinctUntilChanged().collect { position ->
            if (position != null) account.markRead(current.id, current.channel, position)
        }
    }
    LaunchedEffect(state.messages.lastOrNull()?.id, state.followingLatest) {
        if (state.followingLatest && state.messages.isNotEmpty()) list.scrollToItem(state.messages.lastIndex + if (state.hasEarlier) 1 else 0)
    }
    LaunchedEffect(state.scrollToMessage) {
        val target = state.scrollToMessage ?: return@LaunchedEffect
        val index = state.messages.indexOfFirst { it.id == target.id }
        if (index >= 0) list.scrollToItem(index + if (state.hasEarlier) 1 else 0)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!current.canSend) Text(
            stringResource(when {
                current.deviceReplaced -> R.string.recovery_device_replaced
                current.replacingDevice -> R.string.recovery_device_waiting
                current.replacementRejected -> R.string.recovery_device_rejected
                current.archiveScope != null -> R.string.recovery_archive_read_only
                state.members.size < 2 -> R.string.protected_chat_waiting_peer
                current.joined && !current.pending -> R.string.spaces_missing_keys
                else -> R.string.protected_chat_waiting
            }),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = list, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.hasEarlier) item {
                TextButton(onClick = account::olderMessages, enabled = !state.working) {
                    Text(stringResource(R.string.protected_chat_older))
                }
            }
            items(state.messages, key = { it.id }) { message ->
                val own = message.author == account.ownerKey
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (own) Alignment.End else Alignment.Start) {
                    if (!own) Text(registeredIdentity(message.author).name, style = MaterialTheme.typography.labelMedium)
                    Surface(shape = MaterialTheme.shapes.medium, color = if (own)
                        MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
                        val body = when {
                            message.deleted -> stringResource(R.string.protected_chat_deleted)
                            message.missingKeys -> stringResource(R.string.spaces_missing_keys)
                            else -> message.text.orEmpty()
                        }
                        Text(body, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (message.pending || message.rejected || message.cancelled) {
                        Text(stringResource(when {
                            message.rejected -> R.string.protected_chat_send_rejected
                            message.cancelled -> R.string.spaces_send_cancelled
                            message.attempted -> R.string.protected_chat_send_waiting
                            else -> R.string.protected_chat_send_queued
                        }), style = MaterialTheme.typography.labelSmall)
                        if ((message.rejected || message.cancelled) && !message.text.isNullOrBlank()) {
                            TextButton(onClick = { account.restoreUnsentDraft(message.id) },
                                enabled = editable && state.draft.isBlank()) {
                                Text(stringResource(R.string.spaces_restore_draft))
                            }
                        }
                    }
                }
            }
        }
        if (state.hasLater) TextButton(onClick = account::latestMessages) { Text(stringResource(R.string.protected_chat_latest)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(state.draft, account::editDraft, enabled = editable, maxLines = 4,
                modifier = Modifier.weight(1f), label = { Text(stringResource(R.string.protected_chat_message)) })
            val draft = state.draft
            Button(enabled = canSend && draft.isNotBlank(), onClick = {
                val requested = account.state.value.takeIf { it.selected == current.id }?.draft ?: return@Button
                scope.launch {
                    if (publishing.beforePublishing()) account.sendProtected(current.id, requested)
                }
            }) { Text(stringResource(R.string.protected_chat_send)) }
        }
    }
}
