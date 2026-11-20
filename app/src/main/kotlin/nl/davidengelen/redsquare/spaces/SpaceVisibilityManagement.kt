package nl.davidengelen.redsquare.spaces

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R

@Composable internal fun SpaceVisibilityControls(account: SpacesAccount, state: SpacesAccount.State) {
    val current = state.current ?: return
    val profile = state.profiles[current.id] ?: return
    val owner = state.members.any { it.publicKey == account.ownerKey && it.role == SpaceRole.OWNER }
    if (!owner || !current.joined || current.dissolved || !state.visibilitySupported) return
    var confirming by rememberSaveable(current.id, account.ownerKey, account.backend.webUrl) { mutableStateOf<String?>(null) }
    var listed by rememberSaveable(current.id, account.ownerKey, account.backend.webUrl) { mutableStateOf(true) }
    val change = state.visibilityChange
    val ready = state.ready && !state.loading && !state.loadingChannel
    val enabled = ready && current.canManage && !state.working && !state.savingNavigation
    val opening = profile.visibility == SpaceVisibility.PRIVATE
    val discoverableLabel = stringResource(R.string.spaces_discoverable)
    val action = if (opening) R.string.spaces_make_public else R.string.spaces_make_private
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(if (opening) R.drawable.ic_lock else R.drawable.ic_globe), null, Modifier.size(24.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.spaces_visibility_settings), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() })
                    Text(stringResource(if (opening) R.string.spaces_private else R.string.spaces_public),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (change != null) {
                Text(stringResource(if (change.visibility == SpaceVisibility.PUBLIC) R.string.spaces_requested_public else R.string.spaces_requested_private),
                    style = MaterialTheme.typography.bodyMedium)
                val desiredListing = change.listed
                if (change.visibility == SpaceVisibility.PUBLIC && desiredListing != null) Text(stringResource(
                    if (desiredListing) R.string.spaces_requested_listed else R.string.spaces_requested_unlisted),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ManagementChangeNotice(change.status, state.availability, account::refresh, state.refreshing)
                if (change.status == SpaceClient.ChangeStatus.ACCEPTED) Text(stringResource(R.string.spaces_visibility_confirmed),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = {
                listed = true
                confirming = (if (opening) SpaceVisibility.PUBLIC else SpaceVisibility.PRIVATE).name
            }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(action)) }
        }
    }
    val target = confirming?.let(SpaceVisibility::valueOf)
    if (target != null) {
        val toPublic = target == SpaceVisibility.PUBLIC
        val stillCurrent = enabled && profile.visibility != target
        AlertDialog(onDismissRequest = { confirming = null },
            title = { Text(stringResource(if (toPublic) R.string.spaces_make_public else R.string.spaces_make_private)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SpaceAvatar(profile, current.id, account.avatarImages, Modifier.size(48.dp))
                        Text(profile.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    }
                    Text(stringResource(if (toPublic) R.string.spaces_open_visibility_detail else R.string.spaces_close_visibility_detail))
                    if (toPublic) {
                        Text(stringResource(R.string.spaces_public_profile_review), style = MaterialTheme.typography.titleSmall)
                        if (profile.description.isNotBlank()) Text(profile.description)
                        if (profile.houseRules.isNotBlank()) Text(profile.houseRules)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.spaces_discoverable))
                                Text(stringResource(R.string.spaces_discoverable_detail), style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(listed, { listed = it }, enabled = stillCurrent,
                                modifier = Modifier.semantics { contentDescription = discoverableLabel })
                        }
                    }
                    if (!stillCurrent) Text(stringResource(R.string.spaces_visibility_changed), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = {
                confirming = null
                account.changeVisibility(current.id, target, listed)
            }, enabled = stillCurrent, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (toPublic) R.string.spaces_make_public else R.string.spaces_make_private))
            } },
            dismissButton = { TextButton(onClick = { confirming = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.spaces_cancel))
            } })
    }
}
