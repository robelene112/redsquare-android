package nl.davidengelen.redsquare.spaces

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import nl.davidengelen.redsquare.R

@Composable
internal fun RecoveredHistoryPicker(account: SpacesAccount, state: SpacesAccount.State, protectedChat: Boolean,
    dismiss: () -> Unit) {
    var archives by remember(account) { mutableStateOf<List<SpaceCrypto.Scope>?>(null) }
    var loadFailed by remember(account) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(account, state.ready, retry) {
        if (!state.ready) return@LaunchedEffect
        try { archives = account.recoveredScopes(); loadFailed = false }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { loadFailed = true }
    }
    AlertDialog(onDismissRequest = { if (!state.working) dismiss() },
        title = { Text(stringResource(R.string.recovery_archive_title)) },
        text = {
            val entries = archives
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.working || entries == null && !loadFailed) LinearProgressIndicator(Modifier.fillMaxWidth())
                when {
                    loadFailed || state.problem == SpacesAccount.Problem.SAVE -> Text(stringResource(R.string.recovery_archive_failed))
                    state.problem == SpacesAccount.Problem.RECOVERY_OTHER_MODULE ->
                        Text(stringResource(if (protectedChat) R.string.recovery_archive_in_spaces else R.string.recovery_archive_in_chat))
                    entries?.isEmpty() == true -> Text(stringResource(R.string.recovery_archive_empty))
                }
                if (!entries.isNullOrEmpty()) LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    items(entries, key = { it.groupId }) { archive ->
                        TextButton(onClick = { account.openRecoveredHistory(archive, protectedChat) },
                            enabled = !state.working && state.availability == SpacesAccount.Availability.ONLINE,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.recovery_archive_item, archive.spaceId.take(8), archive.channelId.take(8)))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss, enabled = !state.working) { Text(stringResource(android.R.string.cancel)) } },
        dismissButton = {
            if (loadFailed || archives?.isEmpty() == true) TextButton(onClick = { retry++ }, enabled = !state.working) {
                Text(stringResource(R.string.spaces_retry))
            }
        })
}

@Composable
internal fun ReplaceArchivedDevicePrompt(dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.recovery_replace_device)) },
        text = { Text(stringResource(R.string.recovery_replace_archive_device_detail)) },
        confirmButton = { Button(onClick = confirm) { Text(stringResource(R.string.recovery_replace_confirm)) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(android.R.string.cancel)) } })
}
