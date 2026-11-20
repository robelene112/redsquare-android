package nl.davidengelen.redsquare.spaces

import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.feed.ProfileAvatar
import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.ui.FrameIconButton

/** A channel-scoped roster. An accepted policy change is distinct from key delivery. */
@Composable internal fun SpaceChannelAccessPage(account: SpacesAccount, state: SpacesAccount.State, onBack: () -> Unit) {
    val channel = state.channels.firstOrNull { it.id == state.selectedChannel }
    val scope = "${account.ownerKey}:${account.backend.webUrl}:${state.selected}:${state.selectedChannel}"
    var removing by rememberSaveable(scope) { mutableStateOf<String?>(null) }
    var reviewedChange by rememberSaveable(scope) { mutableStateOf<String?>(null) }
    val administrator = state.members.any { it.publicKey == account.ownerKey && it.role in setOf(SpaceRole.OWNER, SpaceRole.ADMIN) }
    val change = state.channelAccessChange?.takeIf { it.channelId == channel?.id }
    val pending = change?.status in setOf(SpaceClient.ChangeStatus.QUEUED, SpaceClient.ChangeStatus.AWAITING_CONFIRMATION)
    val restoring = state.loading || state.loadingChannel || state.current == null || state.availability == SpacesAccount.Availability.CHECKING
    val available = !restoring && state.current?.dissolved != true && administrator && state.privateChannelAccessSupported && channel?.allowedMembers != null && state.current?.joined == true
    val enabled = available && !state.savingNavigation && state.current?.canManage == true && !state.working && !pending
    val noticeStatus = change?.status?.takeUnless { it == SpaceClient.ChangeStatus.REJECTED && change?.id == reviewedChange }
    LaunchedEffect(available) { if (!available) removing = null }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.small, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            FrameIconButton(R.drawable.ic_back, stringResource(R.string.spaces_back), onClick = onBack)
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(channel?.profile?.name.orEmpty(), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.spaces_channel_access), style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() })
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(AppSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.spaces_channel_access_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (available && channel?.ready != true) Text(stringResource(R.string.spaces_waiting_channel_keys), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (restoring) Text(stringResource(R.string.spaces_loading_channel_settings), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else if (!available) Text(stringResource(if (state.current?.dissolved == true) R.string.spaces_dissolved_detail else if (!administrator || state.current?.joined != true)
                        R.string.spaces_edit_access_changed else R.string.spaces_channel_settings_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(state.members, key = { it.publicKey }) { member ->
                val name = registeredIdentity(member.publicKey).name
                val always = member.role in setOf(SpaceRole.OWNER, SpaceRole.ADMIN)
                val granted = always || channel?.allowedMembers?.contains(member.publicKey) == true
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).toggleable(granted, enabled = enabled && !always && (!granted || channel?.ready == true),
                        role = Role.Checkbox, onValueChange = { allowed ->
                            if (!allowed) removing = member.publicKey
                            else state.selected?.let { space -> channel?.let { account.setChannelAccess(space, it.id, member.publicKey, true) } }
                        }).padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProfileAvatar(member.publicKey, Modifier.size(40.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(if (always) R.string.spaces_channel_access_always else if (granted)
                                R.string.spaces_channel_access_granted else R.string.spaces_channel_access_not_granted),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Checkbox(granted, null, enabled = enabled && !always && (!granted || channel?.ready == true))
                    }
                }
            }
        }
        // Keep the result reachable even after acting near the end of a long roster.
        AnimatedVisibility(state.problem != null || noticeStatus in setOf(SpaceClient.ChangeStatus.QUEUED,
                SpaceClient.ChangeStatus.AWAITING_CONFIRMATION, SpaceClient.ChangeStatus.REJECTED),
            enter = fadeIn(tween(AppSpacing.transitionMillis)) + expandVertically(tween(AppSpacing.transitionMillis)),
            exit = fadeOut(tween(AppSpacing.transitionMillis)) + shrinkVertically(tween(AppSpacing.transitionMillis))) {
          Column {
            HorizontalDivider()
            Column(Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.screen, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SpaceManagementProblem(state.problem, account::clearProblem)
                ManagementChangeNotice(noticeStatus, state.availability, retry = {
                    val selected = state.selected
                    val selectedChannel = channel?.id
                    val rejected = change?.id
                    if (selected != null && selectedChannel != null) account.refreshChannelSettings(selected, selectedChannel) {
                        reviewedChange = rejected
                    }
                }, refreshing = state.working)
            }
          }
        }
    }
    val member = state.members.firstOrNull { it.publicKey == removing && it.role == SpaceRole.MEMBER }
    if (member != null && available) AlertDialog(onDismissRequest = { removing = null },
        title = { Text(stringResource(R.string.spaces_remove_channel_access)) },
        text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.spaces_remove_channel_access_detail, registeredIdentity(member.publicKey).name))
            Text(stringResource(R.string.spaces_remove_channel_access_membership))
            Text(stringResource(R.string.spaces_remove_channel_access_history))
            Text(stringResource(R.string.spaces_remove_channel_access_future))
        } },
        confirmButton = { TextButton(onClick = {
            state.selected?.let { space -> channel?.let { account.setChannelAccess(space, it.id, member.publicKey, false) } }
            removing = null
        }, enabled = enabled && channel?.ready == true, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_remove_channel_access)) } },
        dismissButton = { TextButton(onClick = { removing = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_close)) } })
}
