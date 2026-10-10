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
    modifier: Modifier, onClose: () -> Unit) {
    require(wallet.publicKey == post.author)
    val activity = requireNotNull(LocalActivity.current) as ComponentActivity
    val memory = remember(activity, wallet.publicKey, backend, post.id) {
        ViewModelProvider(activity)["edit:${wallet.publicKey}:${backend.id}:${post.id}", PostComposerMemory::class.java]
    }
    val store = remember(wallet.publicKey, backend, post.id) {
        PostDraftStore.author(activity.applicationContext, wallet.publicKey, backend, post.id, AuthorActionKind.EDIT)
    }
    val scope = rememberCoroutineScope()
    val state = remember(store, memory, endpoint) { AuthorEditState(store, memory, wallet, endpoint, post, scope) }
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
                TextButton(enabled = !busy, onClick = { close(onClose) }) { Text(stringResource(R.string.browse_back)) }
                Text(stringResource(R.string.author_edit_title), Modifier.weight(1f).padding(vertical = 12.dp),
                    style = MaterialTheme.typography.titleLarge)
                TextButton(enabled = ready && !busy && !available && !conflict && text.isNotBlank(), onClick = ::send) {
                    Text(stringResource(if (candidate == null) R.string.author_edit_save else R.string.retry))
                }
            }
            if (available) Text(stringResource(R.string.author_edit_available))
            else if (conflict) Text(stringResource(R.string.author_edit_conflict))
            else if (candidate != null) Text(stringResource(R.string.author_edit_pending))
            if (networkError) Text(stringResource(R.string.author_action_network_error))
            if (error) Text(stringResource(R.string.post_draft_error))
            if (!ready && error) TextButton(onClick = { reload++ }) { Text(stringResource(R.string.retry)) }
            OutlinedTextField(value = text, onValueChange = ::edit,
                enabled = ready && !busy && candidate == null, modifier = Modifier.fillMaxWidth().weight(1f))
        }
    }
}
