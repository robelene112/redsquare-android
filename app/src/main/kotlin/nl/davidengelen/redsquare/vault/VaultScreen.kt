package nl.davidengelen.redsquare.vault

import android.app.Activity
import android.content.Intent
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.WalletBalances
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.ui.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VaultScreen(wallet: SaitoWallet, backend: SaitoBackend, endpoint: String, balances: WalletBalances,
    modifier: Modifier, chrome: @Composable (Boolean) -> Unit, onNodeSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val library = remember(wallet.publicKey, backend, endpoint, balances) { VaultLibrary(context, wallet, backend, endpoint, balances) }
    var page by rememberSaveable(wallet.publicKey, backend.id) { mutableIntStateOf(0) }
    var offlineOnly by rememberSaveable { mutableStateOf(false) }
    var privacyOpen by rememberSaveable { mutableStateOf(false) }
    var exporting by rememberSaveable(wallet.publicKey, backend.id) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<Int?>(null) }
    var warning by remember { mutableStateOf<Int?>(null) }
    val list = rememberLazyListState()
    val header = rememberReadingHeaderState(true, list)
    val anchor = rememberReadingAnchorModifier(list, !header.inMotion, relativeToRoot = true)

    val exportDocument = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        scope.launch {
            busy = true; error = null; feedback = null
            try {
                val destination = result.data?.data.takeIf { result.resultCode == Activity.RESULT_OK }
                if (destination != null) {
                    withContext(Dispatchers.IO) {
                        val file = requireNotNull(library.disk.export())
                        currentCoroutineContext().ensureActive()
                        requireNotNull(context.contentResolver.openOutputStream(destination, "wt")).use { output ->
                            output.write(file.bytes); output.flush()
                        }
                    }
                    feedback = R.string.vault_file_saved
                }
                try { withContext(Dispatchers.IO) { library.disk.finishExport() } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { warning = R.string.vault_export_cleanup }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = R.string.vault_save_failed }
            finally { exporting = false; busy = false; library.refreshLocal() }
        }
    }
    LaunchedEffect(library) { library.load(page); if (library.problem == null) page = library.page }
    fun loadPage(target: Int) {
        scope.launch {
            library.load(target)
            if (library.problem == null) { page = library.page; list.scrollToItem(0) }
        }
    }
    fun keep(entry: VaultEntry) {
        if (busy || exporting) return
        busy = true; feedback = null; error = null
        scope.launch {
            try { library.keep(entry); feedback = R.string.vault_copy_saved }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: VaultLocalFailure) { error = R.string.vault_offline_save_failed }
            catch (_: Exception) { error = R.string.vault_access_failed }
            finally { busy = false }
        }
    }
    fun remove(copy: VaultCopy) {
        if (busy || exporting) return
        busy = true; feedback = null; error = null
        scope.launch {
            try { library.remove(copy); feedback = R.string.vault_copy_removed }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = R.string.vault_remove_failed }
            finally { busy = false }
        }
    }
    fun saveAs(entry: VaultEntry) {
        if (busy || exporting) return
        busy = true; feedback = null; error = null
        scope.launch {
            try {
                val file = library.download(entry)
                withContext(Dispatchers.IO) { currentCoroutineContext().ensureActive(); library.disk.prepareExport(file) }
                library.refreshLocal()
                exporting = true
                exportDocument.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType(file.mime).putExtra(Intent.EXTRA_TITLE,
                        file.name.substringAfterLast('/').substringAfterLast('\\').ifBlank { "Vault file" }))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: VaultLocalFailure) { exporting = false; error = R.string.vault_local_file_failed }
            catch (_: Exception) { exporting = false; error = R.string.vault_access_failed }
            finally { busy = false }
        }
    }
    val enabled = !busy && !exporting
    val visible = library.entries.filter { !offlineOnly || it.copy != null }
    Column(modifier.fillMaxSize().nestedScroll(header.connection).readingNavigation(header)) {
        ReadingHeader(header) { chrome(false) }
        Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.vault_files), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            FrameIconButton(R.drawable.ic_info, stringResource(R.string.vault_about)) { privacyOpen = true }
            FrameIconButton(R.drawable.ic_refresh, stringResource(R.string.vault_refresh), enabled && !library.loading) {
                scope.launch { feedback = null; error = null; library.load(page); if (library.problem == null) page = library.page }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen)) {
            InlineStatus(if (library.loading) stringResource(R.string.vault_loading) else null, StatusTone.WORKING)
            InlineStatus(if (busy || exporting) stringResource(R.string.vault_working) else null, StatusTone.WORKING)
            InlineStatus(feedback?.let { stringResource(it) }, StatusTone.SUCCESS)
            InlineStatus(error?.let { stringResource(it) }, StatusTone.ERROR)
            InlineStatus(library.problem?.let { stringResource(if (it == VaultProblem.SERVICE_UNAVAILABLE)
                R.string.vault_service_unavailable else R.string.vault_connection_failed) },
                StatusTone.NEUTRAL, actionLabel = stringResource(R.string.vault_choose_node), onAction = onNodeSettings)
            InlineStatus(if (library.storageWarning) stringResource(R.string.vault_storage_warning)
                else warning?.let { stringResource(it) }, StatusTone.WARNING)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(offlineOnly.not(), { offlineOnly = false }, { Text(stringResource(R.string.vault_all_files)) },
                    modifier = Modifier.heightIn(min = 48.dp))
                FilterChip(offlineOnly, { offlineOnly = true }, { Text(stringResource(R.string.vault_offline)) },
                    modifier = Modifier.heightIn(min = 48.dp))
            }
            Text(stringResource(R.string.vault_storage_used, Formatter.formatShortFileSize(context, library.storedBytes)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().then(anchor).testTag("vault-files"),
            state = list, contentPadding = PaddingValues(horizontal = AppSpacing.screen, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (visible.isEmpty() && !library.loading) item("empty") {
                Text(stringResource(if (library.problem != null) R.string.vault_saved_only
                    else if (offlineOnly) R.string.vault_no_offline else R.string.vault_empty),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(visible, key = { it.token }) { entry ->
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().animateContentSize()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Icon(painterResource(R.drawable.ic_file), null, Modifier.padding(12.dp).size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(entry.name, style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(if (entry.copy != null) R.string.vault_available_offline
                                    else if (entry.key != null) R.string.vault_access_key else R.string.vault_details_unavailable),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                entry.copy?.let { Text(Formatter.formatShortFileSize(context, it.size.toLong()),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                        if (entry.key?.customScript != null && entry.copy == null) Text(stringResource(R.string.vault_custom_key),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val readable = entry.copy != null || entry.key?.let { it.customScript == null } == true
                            OutlinedButton(onClick = { saveAs(entry) }, enabled = enabled && readable,
                                modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.vault_save_as)) }
                            if (entry.copy == null) FilledTonalButton(onClick = { keep(entry) }, enabled = enabled && readable,
                                modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.vault_keep_offline)) }
                            else TextButton(onClick = { remove(entry.copy) }, enabled = enabled,
                                modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.vault_remove_copy)) }
                        }
                    }
                }
            }
            if (!offlineOnly && (library.more || library.page > 0)) item("pages") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (library.page > 0) OutlinedButton(onClick = { loadPage(library.page - 1) }, enabled = enabled && !library.loading,
                        modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.vault_previous_files)) }
                    if (library.more) OutlinedButton(onClick = { loadPage(library.page + 1) }, enabled = enabled && !library.loading,
                        modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.vault_load_more)) }
                }
            }
        }
    }
    if (privacyOpen) AlertDialog(onDismissRequest = { privacyOpen = false }, title = { Text(stringResource(R.string.vault_about)) },
        text = { Text(stringResource(R.string.vault_privacy)) },
        confirmButton = { TextButton(onClick = { privacyOpen = false }) { Text(stringResource(R.string.done)) } })
}
