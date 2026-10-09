package nl.davidengelen.redsquare.feed

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.FeedPost

@Composable
internal fun PostComposerScreen(wallet: SaitoWallet, endpoint: String, backend: SaitoBackend, modifier: Modifier,
    parent: FeedPost? = null, onClose: () -> Unit) {
    val owner = wallet.publicKey
    val activity = requireNotNull(LocalActivity.current) as ComponentActivity
    val context = activity.applicationContext
    val memory = remember(activity, owner, backend, parent?.id) {
        ViewModelProvider(activity)["post:$owner:${backend.id}:${parent?.id.orEmpty()}", PostComposerMemory::class.java]
    }
    val store = remember(owner, backend, parent?.id) { PostDraftStore(context, owner, backend, parent) }
    val scope = rememberCoroutineScope()
    val navigation = requireNotNull(LocalPostNavigation.current)
    val state = remember(store, memory, endpoint) { PostComposerState(store, memory, wallet, endpoint, parent, scope) }
    LaunchedEffect(state, state.reload) { state.load() }
    LaunchedEffect(state) {
        PostDraftStore.changes.collect {
            if (state.transaction != null) {
                snapshotFlow { !state.sending && !state.saving }.first { it }
                state.load()
            }
        }
    }
    DisposableEffect(state) {
        val save: suspend () -> Boolean = { state.persist() }
        navigation.saveCurrent = save
        onDispose { if (navigation.saveCurrent === save) navigation.saveCurrent = null }
    }
    with(state) {
        BackHandler { if (!sending) { if (ready) saveAndClose(onClose) else onClose() } }
        Column(modifier.imePadding()) {
            Row {
                TextButton(enabled = !saving && !sending, onClick = { if (ready) saveAndClose(onClose) else onClose() }) {
                    Text(stringResource(R.string.browse_back))
                }
                Text(stringResource(if (parent == null) R.string.post_new else R.string.post_reply), Modifier.weight(1f).padding(vertical = 12.dp),
                    style = MaterialTheme.typography.titleLarge)
                TextButton(enabled = ready && !saving && !sending, onClick = { saveAndClose(onClose) }) {
                    Text(stringResource(R.string.post_save_draft))
                }
                TextButton(enabled = ready && !saving && !sending && text.isNotBlank() && !available, onClick = ::send) {
                    Text(stringResource(if (transaction == null) R.string.post_send else R.string.retry))
                }
            }
            if (transaction == null) Text(stringResource(R.string.post_draft_local), style = MaterialTheme.typography.bodySmall)
            parent?.let { Text(stringResource(R.string.post_reply_to, "Anon-${it.author.take(6)}"), style = MaterialTheme.typography.bodySmall) }
            if (available) Text(stringResource(R.string.post_available))
            else if (queued) Text(stringResource(R.string.post_queued))
            else if (submitted) Text(stringResource(R.string.post_submitted))
            else if (transaction != null) Text(stringResource(R.string.post_pending))
            if (queued) TextButton(enabled = !saving && !sending, onClick = ::cancelQueued) {
                Text(stringResource(R.string.post_cancel_queued))
            }
            if (transaction != null && !available && !queued) TextButton(enabled = !saving && !sending,
                onClick = { confirmClear = true }) { Text(stringResource(R.string.post_start_another)) }
            if (networkError) Text(stringResource(R.string.post_network_error))
            if (validationError) Text(stringResource(R.string.post_invalid_text))
            if (error) {
                Text(stringResource(R.string.post_draft_error))
                if (!ready) TextButton(onClick = { reload++ }) { Text(stringResource(R.string.retry)) }
            }
            OutlinedTextField(value = text, onValueChange = ::edit, enabled = ready && !saving && !sending && transaction == null,
                placeholder = { Text(stringResource(R.string.post_write)) }, modifier = Modifier.fillMaxWidth().weight(1f))
        }
        if (confirmClear) AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.post_clear_title)) },
            text = { Text(stringResource(R.string.post_clear_detail)) },
            confirmButton = { TextButton(onClick = { clearAndClose(onClose) }) { Text(stringResource(R.string.post_clear_confirm)) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.post_clear_keep)) } },
        )
    }
}
