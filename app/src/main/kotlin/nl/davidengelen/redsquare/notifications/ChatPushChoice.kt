package nl.davidengelen.redsquare.notifications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.chat.GroupChatStore
import nl.davidengelen.redsquare.saito.MAX_PUSH_GROUP_INTERESTS
import nl.davidengelen.redsquare.saito.SaitoBackend

@Composable
internal fun ChatPushChoice(owner: String, backend: SaitoBackend?) {
    val context = LocalContext.current.applicationContext
    val settings = remember(context, owner) { ChatPushSettings(context, owner) }
    val scope = rememberCoroutineScope()
    var choice by remember(owner) { mutableStateOf<Boolean?>(null) }
    var saving by remember(owner) { mutableStateOf(false) }
    var failed by remember(owner) { mutableStateOf(false) }
    var retry by remember(owner) { mutableIntStateOf(0) }
    val groupChanges by GroupChatStore.changes.collectAsState()
    var groupCapacityExceeded by remember(owner, backend) { mutableStateOf(false) }
    var groupStatusFailed by remember(owner, backend) { mutableStateOf(false) }
    LaunchedEffect(settings, retry) {
        choice = null
        try {
            choice = withContext(Dispatchers.IO) { settings.enabled() }
            failed = false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    LaunchedEffect(owner, backend, choice, groupChanges, retry) {
        groupCapacityExceeded = false
        groupStatusFailed = false
        if (choice == true && backend != null) {
            try {
                groupCapacityExceeded = withContext(Dispatchers.IO) {
                    pushRegistrationSelection(context)?.takeIf {
                        it.identity.wallet.publicKey == owner && it.identity.backend == backend
                    }?.groupCapacityExceeded == true
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { groupStatusFailed = true }
        }
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
        value = choice == true, enabled = choice != null && !saving, role = Role.Switch,
        onValueChange = { requested ->
            saving = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { settings.setEnabled(requested) }
                    choice = requested
                    failed = false
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { choice = null; failed = true }
                finally { saving = false }
            }
        }), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.chat_background_alerts), Modifier.weight(1f))
        Switch(checked = choice == true, onCheckedChange = null, enabled = choice != null && !saving)
    }
    Text(stringResource(R.string.chat_background_alerts_detail), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (groupCapacityExceeded) {
        Text(stringResource(R.string.chat_group_alert_capacity, MAX_PUSH_GROUP_INTERESTS),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (groupStatusFailed) {
        Text(stringResource(R.string.chat_group_alert_availability_error))
        TextButton(enabled = !saving, onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
    }
    if (failed) {
        Text(stringResource(R.string.chat_background_alerts_error))
        TextButton(enabled = !saving, onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
    }
}
