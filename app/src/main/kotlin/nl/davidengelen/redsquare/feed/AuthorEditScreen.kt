package nl.davidengelen.redsquare.feed

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.*

@Composable
internal fun AuthorEditScreen(wallet: SaitoWallet, endpoint: String, backend: SaitoBackend, post: FeedPost,
    modifier: Modifier, kind: AuthorActionKind = AuthorActionKind.EDIT, onClose: () -> Unit) {
    require(wallet.publicKey == post.author)
    val activity = requireNotNull(LocalActivity.current) as ComponentActivity
    val memory = remember(activity, wallet.publicKey, backend, post.id, kind) {
        ViewModelProvider(activity)["${kind.storageId}:${wallet.publicKey}:${backend.id}:${post.id}", PostComposerMemory::class.java]
    }
    val store = remember(wallet.publicKey, backend, post.id, kind) {
        PostDraftStore.author(activity.applicationContext, wallet.publicKey, backend, post.id, kind)
    }
    val scope = rememberCoroutineScope()
    val state = remember(store, memory, endpoint) {
        AuthorEditState(store, memory, wallet, endpoint, post, scope, AndroidPostRevisions(activity.applicationContext, backend), kind)
    }
    val navigation = requireNotNull(LocalPostNavigation.current)
    LaunchedEffect(state, state.reload) { state.load() }
    DisposableEffect(state) {
        val save: suspend () -> Boolean = { state.persist() }
        navigation.saveCurrent = save
        onDispose { if (navigation.saveCurrent === save) navigation.saveCurrent = null }
    }
    BackHandler { state.close(onClose) }
    with(state) {
        Column(modifier.imePadding()) {
            Row {
                TextButton(enabled = !busy && !saving, onClick = { close(onClose) }) { Text(stringResource(R.string.browse_back)) }
                Text(stringResource(if (kind == AuthorActionKind.EDIT) R.string.author_edit_title else R.string.author_delete_title), Modifier.weight(1f).padding(vertical = 12.dp),
                    style = MaterialTheme.typography.titleLarge)
                TextButton(enabled = ready && !busy && !saving && !available && !conflict &&
                    (kind == AuthorActionKind.DELETE || text.isNotBlank()), onClick = ::send) {
                    Text(stringResource(if (candidate != null) R.string.retry else
                        if (kind == AuthorActionKind.EDIT) R.string.author_edit_save else R.string.author_delete_confirm))
                }
            }
            if (available) Text(stringResource(if (kind == AuthorActionKind.EDIT) R.string.author_edit_available else R.string.author_delete_available))
            else if (conflict) Text(stringResource(R.string.author_edit_conflict))
            else if (candidate != null) Text(stringResource(if (kind == AuthorActionKind.EDIT) R.string.author_edit_pending else R.string.author_delete_pending))
            if (conflict) {
                Text(stringResource(R.string.author_edit_conflict_resolution))
                Row {
                    TextButton(enabled = !busy && !saving, onClick = { resolveConflict(false) }) {
                        Text(stringResource(R.string.author_edit_keep_text))
                    }
                    TextButton(enabled = !busy && !saving, onClick = { resolveConflict(true) }) {
                        Text(stringResource(R.string.author_edit_use_latest))
                    }
                }
            }
            if (networkError) Text(stringResource(R.string.author_action_network_error))
            if (error) Text(stringResource(R.string.post_draft_error))
            if (!ready && error) TextButton(onClick = { reload++ }) { Text(stringResource(R.string.retry)) }
            if (kind == AuthorActionKind.EDIT) {
                OutlinedTextField(value = text, onValueChange = ::edit,
                    enabled = ready && !busy && !saving && candidate == null, modifier = Modifier.fillMaxWidth().weight(1f))
            } else {
                Text(stringResource(R.string.author_delete_explanation))
                Text(post.text, Modifier.padding(vertical = 16.dp))
            }
        }
    }
}
