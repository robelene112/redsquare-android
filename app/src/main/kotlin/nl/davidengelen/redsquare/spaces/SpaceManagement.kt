package nl.davidengelen.redsquare.spaces

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.feed.ProfileAvatar
import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.ui.DesignSheet

@Composable internal fun SpaceChannelManagement(account: SpacesAccount, state: SpacesAccount.State,
    channel: SpaceClient.Channel, onAccess: () -> Unit, onEdit: () -> Unit) {
    val current = state.current ?: return
    val role = state.members.firstOrNull { it.publicKey == account.ownerKey }?.role
    val editingSupported = if (channel.visibility == SpaceVisibility.PUBLIC) state.publicChannelEditSupported else state.privateChannelEditSupported
    if (role !in setOf(SpaceRole.OWNER, SpaceRole.ADMIN) ||
        !state.archiveSupported && !editingSupported &&
        !(state.privateChannelAccessSupported && channel.allowedMembers != null)) return
    val change = state.archiveChange?.takeIf { it.channelId == channel.id }
    val pendingEdit = state.channelEdit?.let { it.channelId == channel.id && it.status in setOf(
        SpaceClient.ChangeStatus.QUEUED, SpaceClient.ChangeStatus.AWAITING_CONFIRMATION) } == true
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider()
        Text(stringResource(R.string.spaces_channel_settings), style = MaterialTheme.typography.titleMedium)
        Text(channel.profile.name, style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.spaces_archive_detail), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (editingSupported) {
            OutlinedButton(onClick = onEdit, enabled = !state.working && (pendingEdit || current.canManage && channel.metadataReady),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_edit_channel)) }
        }
        if (editingSupported && !channel.metadataReady) Text(stringResource(R.string.spaces_waiting_channel_keys),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.privateChannelAccessSupported && channel.allowedMembers != null) {
            OutlinedButton(onClick = onAccess, enabled = !state.working,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_channel_access)) }
        }
        if (state.archiveSupported) {
            ManagementChangeNotice(change?.status, state.availability, account::refresh)
            val pending = change?.status in setOf(SpaceClient.ChangeStatus.QUEUED, SpaceClient.ChangeStatus.AWAITING_CONFIRMATION)
            OutlinedButton(onClick = { account.archiveChannel(current.id, channel.id, !channel.archived) },
                enabled = current.canManage && !state.working && !pending, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(when {
                    pending && change?.archived == true -> R.string.spaces_archiving_channel
                    pending -> R.string.spaces_reopening_channel
                    channel.archived -> R.string.spaces_reopen_channel
                    else -> R.string.spaces_archive_channel
                }))
            }
        }
    }
}

@Composable internal fun SpaceMemberRoleSheet(open: Boolean, account: SpacesAccount, state: SpacesAccount.State,
    member: SpaceClient.Member?, onDismiss: () -> Unit) {
    val owner = state.members.firstOrNull { it.publicKey == account.ownerKey }?.role == SpaceRole.OWNER
    val change = state.roleChange?.takeIf { it.memberKey == member?.publicKey }
    val pending = change?.status in setOf(SpaceClient.ChangeStatus.QUEUED, SpaceClient.ChangeStatus.AWAITING_CONFIRMATION)
    DesignSheet(open && member != null && state.selected != null, stringResource(R.string.spaces_manage_role), onDismiss, footer = { first ->
        SpaceManagementProblem(state.problem, account::clearProblem)
        if (member != null && owner && member.role != SpaceRole.OWNER && state.rolesSupported) {
            Button(onClick = { state.selected?.let { account.setAdministrator(it, member.publicKey, member.role != SpaceRole.ADMIN) } },
                enabled = open && state.current?.canManage == true && !state.working && !pending,
                modifier = first.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(when {
                    pending -> R.string.spaces_updating_role
                    member.role == SpaceRole.ADMIN -> R.string.spaces_remove_administrator
                    else -> R.string.spaces_make_administrator
                }))
            }
        }
    }) {
        if (member != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ProfileAvatar(member.publicKey, Modifier.size(48.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(registeredIdentity(member.publicKey).name, style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(when (member.role) {
                        SpaceRole.OWNER -> R.string.spaces_role_owner
                        SpaceRole.ADMIN -> R.string.spaces_role_admin
                        SpaceRole.MEMBER -> R.string.spaces_role_member
                    }), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(stringResource(R.string.spaces_administrator_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!owner || member.role == SpaceRole.OWNER) Text(stringResource(R.string.spaces_owner_controls_roles))
            ManagementChangeNotice(change?.status, state.availability, account::refresh)
        }
    }
}

@Composable internal fun SpaceManagementProblem(problem: SpacesAccount.Problem?, dismiss: () -> Unit, scrollIntoView: Boolean = true) {
    val transition = updateTransition(problem, label = "space-management-local-error")
    transition.AnimatedContent(transitionSpec = {
        fadeIn(tween(AppSpacing.transitionMillis)) togetherWith fadeOut(tween(AppSpacing.transitionMillis))
    }) { current ->
        if (current != null) {
            val requester = remember { BringIntoViewRequester() }
            var laidOut by remember { mutableStateOf(false) }
            val shouldScroll = scrollIntoView && problem == current && current == SpacesAccount.Problem.SAVE
            val settled = transition.currentState == transition.targetState && !transition.isRunning
            LaunchedEffect(shouldScroll, laidOut, settled) {
                if (shouldScroll && laidOut && settled) requester.bringIntoView()
            }
            Box(Modifier.fillMaxWidth().bringIntoViewRequester(requester).onGloballyPositioned { laidOut = true }) {
                ProblemNotice(current, dismiss)
            }
        }
    }
}

@Composable internal fun ManagementChangeNotice(status: SpaceClient.ChangeStatus?, availability: SpacesAccount.Availability,
    retry: () -> Unit, refreshing: Boolean = false) {
    AnimatedContent(status?.takeIf { it != SpaceClient.ChangeStatus.ACCEPTED }, label = "space-management-result",
        transitionSpec = { fadeIn(tween(AppSpacing.transitionMillis)) togetherWith fadeOut(tween(AppSpacing.transitionMillis)) }) { current ->
        if (current != null) Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(painterResource(R.drawable.ic_info), null, Modifier.size(20.dp), tint = if (current == SpaceClient.ChangeStatus.REJECTED)
                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(when {
                        current == SpaceClient.ChangeStatus.REJECTED -> R.string.spaces_management_rejected
                        current == SpaceClient.ChangeStatus.AWAITING_CONFIRMATION -> R.string.spaces_management_awaiting
                        availability == SpacesAccount.Availability.OFFLINE -> R.string.spaces_management_offline
                        else -> R.string.spaces_management_queued
                    }), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
                if (current == SpaceClient.ChangeStatus.REJECTED || current == SpaceClient.ChangeStatus.AWAITING_CONFIRMATION &&
                    availability == SpacesAccount.Availability.OFFLINE) TextButton(onClick = retry, enabled = !refreshing, modifier = Modifier.heightIn(min = 48.dp)) {
                    if (refreshing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.spaces_refresh_settings))
                }
            }
        }
    }
}
