package nl.davidengelen.redsquare.fileshare

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.ui.*

/** One bounded, lazy native account task. Loading and deletion never run on the UI thread. */
@Composable
internal fun ReceivedFilesScreen(binding: ReceivedFilesScope, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val storage = remember(binding) { ReceivedFiles(context, binding) }
    val scope = rememberCoroutineScope()
    var snapshot by remember(storage) { mutableStateOf<ReceivedFilesSnapshot?>(null) }
    var failed by remember(storage) { mutableStateOf(false) }
    var removing by remember(storage) { mutableStateOf<String?>(null) }
    var confirmation by remember(storage) { mutableStateOf<ReceivedFile?>(null) }
    var refresh by remember(storage) { mutableIntStateOf(0) }
    suspend fun reload() {
        try { snapshot = withContext(Dispatchers.IO) { storage.snapshot() } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true; snapshot = null }
    }
    LaunchedEffect(storage, refresh) { reload() }
    LaunchedEffect(storage) {
        FileshareRuntime.transfers.map { rows -> rows.filter { it.owner == binding.owner && it.backend == binding.backend }
            .map { Triple(it.token, it.phase.delivered, it.localCopy) } }.distinctUntilChanged().collect { reload() }
    }
    AccountDialog(title = stringResource(R.string.received_files), closeLabel = stringResource(R.string.settings_back),
        backLabel = stringResource(R.string.settings_back), onDismissRequest = onBack, text = {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Text(stringResource(R.string.received_retention), style = MaterialTheme.typography.bodyMedium)
            snapshot?.let { current ->
                Text(stringResource(R.string.received_total, fileSize(current.bytes)),
                    style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                Text(stringResource(R.string.received_total_detail), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (current.incomplete) Text(stringResource(R.string.received_incomplete), style = MaterialTheme.typography.bodySmall)
            }
            InlineStatus(if (failed) stringResource(R.string.received_failure) else null, StatusTone.ERROR)
            TextButton(onClick = { failed = false; refresh++ }, enabled = removing == null) { Text(stringResource(R.string.feed_refresh)) }
            if (snapshot == null && !failed) LinearProgressIndicator(Modifier.fillMaxWidth())
            snapshot?.let { current ->
                if (current.files.isEmpty()) Text(stringResource(R.string.received_empty))
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(current.files, key = { it.key }) { file ->
                        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(file.transfer.offer.name, style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.received_file_size, fileSize(file.transfer.offer.size), fileSize(file.retainedBytes)),
                                    style = MaterialTheme.typography.bodySmall)
                                if (file.transfer.localCopy != LocalFileCopy.AVAILABLE)
                                    Text(stringResource(R.string.received_removal_incomplete), style = MaterialTheme.typography.bodySmall)
                                if (file.busy) Text(stringResource(R.string.received_saving), style = MaterialTheme.typography.bodySmall)
                                if (removing == file.key) LinearProgressIndicator(Modifier.fillMaxWidth())
                                TextButton(enabled = removing == null && !file.busy, onClick = { confirmation = file },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                                    Text(stringResource(R.string.received_remove))
                                }
                            }
                        }
                    }
                }
            }
        }
    })
    confirmation?.let { file ->
        AlertDialog(onDismissRequest = { confirmation = null }, title = { Text(stringResource(R.string.received_remove_title)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(file.transfer.offer.name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.received_remove_detail))
            } }, dismissButton = { TextButton(onClick = { confirmation = null }) { Text(stringResource(R.string.fileshare_cancel)) } },
            confirmButton = { TextButton(onClick = {
                confirmation = null; removing = file.key; failed = false
                scope.launch {
                    try { withContext(Dispatchers.IO) { storage.remove(file) } }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failed = true }
                    finally { removing = null }
                    reload()
                }
            }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text(stringResource(R.string.received_remove_confirm))
            } })
    }
}
