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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.ui.FrameIconButton
import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone

private data class QueuedPostRow(val backend: SaitoBackend, val store: PostDraftStore, val saved: PostDraftStore.Saved)

@Composable
internal fun QueuedPostsScreen(owner: String, selected: SaitoBackend, modifier: Modifier, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var rows by remember(owner) { mutableStateOf<List<QueuedPostRow>>(emptyList()) }
    var ready by remember(owner) { mutableStateOf(false) }
    var error by remember(owner) { mutableStateOf(false) }
    var saving by remember(owner) { mutableStateOf<String?>(null) }
    var reload by remember(owner) { mutableIntStateOf(0) }
    LaunchedEffect(owner, reload) {
        PostDraftStore.changes.collect {
            try {
                rows = withContext(Dispatchers.IO) {
                    SaitoBackend.entries.flatMap { backend ->
                        PostDraftStore.pendingDelivery(context, owner, backend).map { store -> QueuedPostRow(backend, store, store.load()) }
                    }.filter { !it.saved.available && it.saved.transaction != null }
                }
                ready = true
                error = false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
        }
    }
    BackHandler(enabled = saving == null, onBack = onBack)
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(bottom = AppSpacing.small), verticalAlignment = Alignment.CenterVertically) {
            FrameIconButton(R.drawable.ic_back, stringResource(R.string.browse_back), saving == null, onBack)
            Text(stringResource(R.string.post_queue_title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        }
        InlineStatus(if (error) stringResource(R.string.post_queue_storage_error) else null, StatusTone.ERROR,
            actionLabel = stringResource(R.string.retry), onAction = { reload++ })
        if (ready && rows.isEmpty()) Text(stringResource(R.string.post_queue_empty),
            Modifier.padding(vertical = AppSpacing.gap), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.small),
            contentPadding = PaddingValues(bottom = AppSpacing.gap)) {
            items(rows, key = { requireNotNull(it.saved.transaction).id }) { row ->
                val id = requireNotNull(row.saved.transaction).id
                PublishingWorkItem(row.backend.domain) {
                    if (row.saved.text.isNotEmpty()) Text(row.saved.text, style = MaterialTheme.typography.bodyLarge)
                    PostImages(row.saved.images, row.backend, gifAttributions = row.saved.gifAttributions, previewHeight = 120.dp)
                    InlineStatus(stringResource(if (row.saved.queued) R.string.post_queued else R.string.post_pending),
                        if (row.saved.queued) StatusTone.INFO else StatusTone.WARNING,
                        details = listOf(stringResource(R.string.status_origin_node, row.backend.domain)))
                    InlineStatus(if (row.backend != selected) stringResource(R.string.post_queue_paused) else null, StatusTone.WARNING)
                    if (row.saved.queued) OutlinedButton(enabled = saving == null, onClick = {
                        saving = id
                        scope.launch {
                            try {
                                if (!withContext(Dispatchers.IO) { row.store.cancelQueued(row.saved) }) reload++
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { error = true }
                            finally { saving = null }
                        }
                    }) { Text(stringResource(R.string.post_cancel_queued)) }
                }
            }
        }
    }
}
