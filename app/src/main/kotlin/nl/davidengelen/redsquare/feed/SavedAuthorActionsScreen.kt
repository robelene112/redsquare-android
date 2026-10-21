package nl.davidengelen.redsquare.feed

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.ui.FrameIconButton
import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone

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
        Row(Modifier.fillMaxWidth().padding(bottom = AppSpacing.small), verticalAlignment = Alignment.CenterVertically) {
            FrameIconButton(R.drawable.ic_back, stringResource(R.string.browse_back), onClick = onBack)
            Text(stringResource(R.string.author_saved_changes), Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium)
        }
        InlineStatus(if (ready && !failed && rows.isNotEmpty()) stringResource(R.string.post_saved_device) else null,
            details = listOf(stringResource(R.string.author_saved_manual), stringResource(R.string.status_origin_node, backend.domain)),
            announce = false)
        InlineStatus(if (failed) stringResource(R.string.post_draft_error) else null, StatusTone.ERROR,
            actionLabel = stringResource(R.string.retry), onAction = { reload++ })
        if (ready && rows.isEmpty()) Text(stringResource(R.string.author_saved_empty),
            Modifier.padding(vertical = AppSpacing.gap), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.small),
            contentPadding = PaddingValues(bottom = AppSpacing.gap)) {
            items(rows, key = { "${it.work.kind.storageId}:${it.work.target}" }) { row ->
                PublishingWorkItem(backend.domain) {
                    Text(if (row.work.kind == AuthorActionKind.EDIT) row.work.saved.text else row.original.text,
                        style = MaterialTheme.typography.bodyLarge)
                    if (row.work.saved.transaction != null) InlineStatus(stringResource(
                        if (row.work.kind == AuthorActionKind.EDIT) R.string.author_edit_pending else R.string.author_delete_pending),
                        StatusTone.WARNING)
                    OutlinedButton(onClick = { selected = row }) {
                        Text(stringResource(if (row.work.kind == AuthorActionKind.EDIT) R.string.author_edit_title else R.string.author_delete_title))
                    }
                }
            }
        }
    }
}
