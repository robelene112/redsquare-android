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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.SaitoBackend

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
        Row {
            TextButton(enabled = saving == null, onClick = onBack) { Text(stringResource(R.string.browse_back)) }
            Text(stringResource(R.string.post_queue_title), Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.titleLarge)
        }
        if (error) {
            Text(stringResource(R.string.post_queue_storage_error))
            TextButton(onClick = { reload++ }) { Text(stringResource(R.string.retry)) }
        }
        if (ready && rows.isEmpty()) Text(stringResource(R.string.post_queue_empty))
        LazyColumn {
            items(rows, key = { requireNotNull(it.saved.transaction).id }) { row ->
                val id = requireNotNull(row.saved.transaction).id
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Text(row.backend.domain, style = MaterialTheme.typography.labelMedium)
                    Text(row.saved.text, Modifier.padding(vertical = 8.dp))
                    PostImages(row.saved.images, row.backend)
                    Text(stringResource(if (row.saved.queued) R.string.post_queued else R.string.post_pending))
                    if (row.backend != selected) Text(stringResource(R.string.post_queue_paused))
                    if (row.saved.queued) TextButton(enabled = saving == null, onClick = {
                        saving = id
                        scope.launch {
                            try {
                                if (!withContext(Dispatchers.IO) { row.store.cancelQueued(row.saved) }) reload++
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { error = true }
                            finally { saving = null }
                        }
                    }) { Text(stringResource(R.string.post_cancel_queued)) }
                    HorizontalDivider()
                }
            }
        }
    }
}
