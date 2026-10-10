package nl.davidengelen.redsquare.feed

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.*

private data class SavedAuthorRow(val work: PostDraftStore.AuthorWork, val original: FeedPost)

/** Explicit recovery of saved changes. Opening the list never submits an author action. */
@Composable
internal fun SavedAuthorActionsScreen(wallet: SaitoWallet, endpoint: String, backend: SaitoBackend,
    modifier: Modifier, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    var rows by remember(wallet.publicKey, backend) { mutableStateOf<List<SavedAuthorRow>>(emptyList()) }
    var selected by remember(wallet.publicKey, backend) { mutableStateOf<SavedAuthorRow?>(null) }
    var failed by remember(wallet.publicKey, backend) { mutableStateOf(false) }
    var ready by remember(wallet.publicKey, backend) { mutableStateOf(false) }
    var reload by remember(wallet.publicKey, backend) { mutableIntStateOf(0) }
    LaunchedEffect(wallet.publicKey, backend, reload) {
        try {
            rows = withContext(Dispatchers.IO) {
                val revisions = AndroidPostRevisions(context, backend)
                PostDraftStore.authorWork(context, wallet.publicKey, backend).map { work ->
                    val original = requireNotNull(revisions.original(work.target))
                    check(original.author == wallet.publicKey)
                    SavedAuthorRow(work, original)
                }
            }
            ready = true; failed = false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    selected?.let { row ->
        AuthorEditScreen(wallet, endpoint, backend, row.original, modifier, row.work.kind) {
            selected = null; reload++
        }
        return
    }
    BackHandler(onBack = onBack)
    Column(modifier) {
        Row {
            TextButton(onClick = onBack) { Text(stringResource(R.string.browse_back)) }
            Text(stringResource(R.string.author_saved_changes), Modifier.padding(vertical = 12.dp),
                style = MaterialTheme.typography.titleLarge)
        }
        Text(backend.domain, style = MaterialTheme.typography.labelMedium)
        Text(stringResource(R.string.author_saved_manual))
        if (failed) {
            Text(stringResource(R.string.post_draft_error))
            TextButton(onClick = { reload++ }) { Text(stringResource(R.string.retry)) }
        }
        if (ready && rows.isEmpty()) Text(stringResource(R.string.author_saved_empty))
        LazyColumn {
            items(rows, key = { "${it.work.kind.storageId}:${it.work.target}" }) { row ->
                Column(Modifier.padding(vertical = 12.dp)) {
                    Text(if (row.work.kind == AuthorActionKind.EDIT) row.work.saved.text else row.original.text)
                    TextButton(onClick = { selected = row }) {
                        Text(stringResource(if (row.work.kind == AuthorActionKind.EDIT) R.string.author_edit_title else R.string.author_delete_title))
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
