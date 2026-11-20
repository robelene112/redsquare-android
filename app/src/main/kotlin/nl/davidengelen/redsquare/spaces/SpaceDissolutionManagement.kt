package nl.davidengelen.redsquare.spaces

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R

@Composable internal fun SpaceDissolutionControls(account: SpacesAccount, state: SpacesAccount.State) {
    val current = state.current ?: return
    val profile = state.profiles[current.id] ?: return
    val owner = state.members.any { it.publicKey == account.ownerKey && it.role == SpaceRole.OWNER }
    if (!current.joined || !owner || !state.dissolutionSupported) return
    var confirming by rememberSaveable(current.id, account.ownerKey, account.backend.webUrl) { mutableStateOf(false) }
    LaunchedEffect(current.dissolved) { if (current.dissolved) confirming = false }
    val enabled = state.ready && !state.loading && !state.loadingChannel && current.canManage && !state.working && !state.savingNavigation
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (current.dissolved) R.string.spaces_dissolved else R.string.spaces_close_community),
                style = MaterialTheme.typography.titleMedium)
            if (current.dissolved) Text(stringResource(R.string.spaces_dissolved_detail),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                ManagementChangeNotice(state.dissolutionChange?.status, state.availability, account::refresh, state.refreshing)
                OutlinedButton(onClick = { confirming = true }, enabled = enabled,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_dissolve)) }
            }
        }
    }
    if (confirming && !current.dissolved) AlertDialog(onDismissRequest = { confirming = false },
        title = { Text(stringResource(R.string.spaces_dissolve_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.spaces_dissolve_detail))
                if (!enabled) Text(stringResource(R.string.spaces_visibility_changed), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = {
            confirming = false
            account.dissolve(current.id)
        }, enabled = enabled, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_dissolve)) } },
        dismissButton = { TextButton(onClick = { confirming = false }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.spaces_cancel))
        } })
}
