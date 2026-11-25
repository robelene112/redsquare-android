package nl.davidengelen.redsquare.spaces

import android.content.Intent
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.policy.LocalPublishingAgreement
import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone
import java.text.DateFormat
import java.util.Date

@Composable
internal fun SpaceFileChannel(account: SpacesAccount, state: SpacesAccount.State, channel: SpaceClient.Channel,
    modifier: Modifier) {
    val target = SpacesAccount.Target(requireNotNull(state.selected), channel.id)
    val context = LocalContext.current
    val publishing = LocalPublishingAgreement.current
    val online = state.availability == SpacesAccount.Availability.ONLINE
    val joined = state.current?.joined == true
    val records = remember(state.messages) { state.messages.filter { it.isFile }.asReversed() }
    val descriptors by produceState<Map<String, SpaceFileDescriptor>>(emptyMap(), target, records) {
        value = withContext(Dispatchers.Default) {
            records.mapNotNull { record ->
                val file = record.text?.let { runCatching { SpaceFileDescriptor.read(it) }.getOrNull() }
                file?.let { record.id to it }
            }.toMap()
        }
    }
    var revision by remember(target) { mutableIntStateOf(0) }
    var busy by remember(target) { mutableStateOf(false) }
    var problem by remember(target) { mutableIntStateOf(0) }
    var selected by rememberSaveable(target.space, target.channel) { mutableStateOf<String?>(null) }
    var settings by rememberSaveable(target.space, target.channel) { mutableStateOf(false) }
    var providerOrigin by rememberSaveable(target.space, target.channel) { mutableStateOf("") }
    var terms by remember(target) { mutableStateOf<SpaceStorageOffer?>(null) }
    var removeShared by remember(target) { mutableStateOf<SpaceClient.Message?>(null) }
    var removeLocal by remember(target) { mutableStateOf<String?>(null) }
    val library by produceState<SpacesAccount.FileLibrary?>(null, target, revision, online, joined, state.fileLeaseId, records, state.contentLoaded) {
        if (state.contentLoaded || records.isNotEmpty() || online) {
            try {
                val local = account.fileLibrary(target, records, observeProvider = false)
                val previous = value
                value = if (previous?.lease?.id == local.lease?.id && online && previous?.usage != null && !local.expired)
                    local.copy(usage = previous.usage, checkedProvider = true) else local
                if (online && joined && local.lease != null && !local.expired)
                    value = account.fileLibrary(target, records, observeProvider = true)
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { value = null }
        }
    }
    val lease = library?.lease
    LaunchedEffect(settings, lease?.id) {
        if (settings && providerOrigin.isBlank()) lease?.offer?.origin?.let { providerOrigin = it }
    }
    LaunchedEffect(target, lease?.id, online, joined) {
        val end = lease?.offer?.expiresAt ?: return@LaunchedEffect
        while (true) {
            val remaining = end - System.currentTimeMillis()
            if (remaining <= 0) break
            val warning = remaining - FILE_STORAGE_WARNING_MILLIS
            val poll = if (online && joined) 60_000L else Long.MAX_VALUE
            delay(minOf(remaining, poll, warning.takeIf { it > 0 } ?: Long.MAX_VALUE))
            revision++
        }
    }

    fun runFileAction(failure: Int, action: suspend () -> Unit) {
        if (busy) return
        problem = 0
        busy = true
        account.launchFileOperation(target) {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { problem = failure }
            finally { busy = false; revision++ }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { document ->
        if (document != null) {
            runFileAction(R.string.spaces_files_upload_problem) {
                if (publishing.beforePublishing()) {
                    account.prepareFileUpload(target, document)
                    account.sendPreparedFile(target)
                    account.refresh()
                }
            }
        }
    }
    val selectedRecord = selected?.let { id -> records.firstOrNull { it.id == id } }
    val selection = selectedRecord?.let { record -> descriptors[record.id] ?: library?.offline?.get(record.id) }
    val rowHasOffline = selectedRecord?.id?.let { it in library?.offline.orEmpty() } == true

    BackHandler(selected != null) {
        selected = null
        problem = 0
    }
    val displayRows = records.filter { !it.pending && !it.rejected && (!it.deleted || it.id in library?.offline.orEmpty()) }
    val anchor = state.anchor
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = anchor?.let { saved ->
            displayRows.indexOfFirst { it.id == saved.messageId }
                .takeIf { it >= 0 }?.plus(1)
        } ?: 0,
        initialFirstVisibleItemScrollOffset = anchor?.offset ?: 0,
    )
    LaunchedEffect(target, listState, selected, records, state.windowFrom) {
        if (selected != null) return@LaunchedEffect
        snapshotFlow {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == listState.firstVisibleItemIndex }
            first?.key?.toString() to listState.firstVisibleItemScrollOffset
        }.collect { (id, offset) ->
            if (id == "library") account.readingPosition(target.space, target.channel, true)
            else if (id != null) {
                val record = records.firstOrNull { it.id == id && it.position != null }
                record?.position?.let { position ->
                    account.readingPosition(target.space, target.channel, false,
                        SpaceClient.ReadingAnchor(id, position, offset, state.windowFrom))
                }
            }
        }
    }

    LaunchedEffect(target, state.scrollToMessage, displayRows, selected) {
        val requested = state.scrollToMessage ?: return@LaunchedEffect
        if (selected != null) return@LaunchedEffect
        val index = displayRows.indexOfFirst { it.id == requested.id }
        val slot = if (index >= 0) index + 1 else if (state.hasLater) displayRows.size else 0
        listState.scrollToItem(slot)
        account.consumeScrollTarget(requested)
    }

    val canConfigure = online && library?.supported == true && account.mayManageFileStorage(target)
    val canDownload = online && joined && library?.usage != null && library?.expired == false
    val canUpload = canDownload && library?.usage?.available?.let { it >= SpaceFileCipher.MIN_CIPHERTEXT_BYTES } == true &&
        state.current?.canSend == true && library?.upload == null
    if (selectedRecord != null && selection != null) {
        val row = selectedRecord
        LazyColumn(modifier.fillMaxWidth().testTag("spaces-file-detail"), contentPadding = PaddingValues(AppSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                TextButton(onClick = { selected = null; problem = 0 }) { Text(stringResource(R.string.spaces_files_back_to_library)) }
            }
            item { FilePanel(selection.name, FileAttribution(row, selection.sealed.size), R.drawable.ic_file) }
            if (problem != 0) item { InlineStatus(stringResource(problem), StatusTone.WARNING) }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (row.deleted) item { InlineStatus(stringResource(R.string.spaces_files_offline_only), StatusTone.INFO) }
            item {
                Button(onClick = {
                    runFileAction(R.string.spaces_files_open_problem) {
                        val opened = requireNotNull(account.openSpaceFile(target, row.id, keep = false))
                        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(opened.uri, opened.mime)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(intent, context.getString(R.string.spaces_files_open)))
                    }
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    enabled = !busy && (rowHasOffline || !row.deleted && canDownload)) {
                    Text(stringResource(R.string.spaces_files_open))
                }
            }
            item {
                if (rowHasOffline) {
                    OutlinedButton(onClick = { removeLocal = row.id }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        enabled = !busy) { Text(stringResource(R.string.spaces_files_remove_offline)) }
                } else if (!row.deleted) {
                    OutlinedButton(onClick = {
                        runFileAction(R.string.spaces_files_open_problem) { account.openSpaceFile(target, row.id, keep = true) }
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy && canDownload) {
                        Text(stringResource(R.string.spaces_files_keep_offline))
                    }
                }
            }
            if (account.mayRemoveFile(target, row)) item {
                TextButton(onClick = { removeShared = row }, enabled = !busy) {
                    Text(stringResource(R.string.spaces_files_remove_shared), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    } else {
        LazyColumn(modifier.fillMaxWidth().testTag("spaces-file-list"), state = listState, contentPadding = PaddingValues(AppSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item("library") {
                FilePanel(stringResource(R.string.spaces_files_library), storageLabel(library, online, joined), R.drawable.ic_vault,
                    onClick = { settings = true; providerOrigin = lease?.offer?.origin.orEmpty(); problem = 0 })
            }
            items(displayRows, key = { it.id }) { row ->
                val file = descriptors[row.id] ?: library?.offline?.get(row.id)
                if (file != null && !row.pending && !row.rejected) {
                    FilePanel(file.name, FileAttribution(row, file.sealed.size), R.drawable.ic_file,
                        onClick = { selected = row.id; problem = 0 })
                } else if (!row.pending && !row.rejected && row.missingKeys) {
                    FilePanel(stringResource(R.string.spaces_files_hidden), FileAttribution(row, null), R.drawable.ic_file)
                }
            }
            if (problem != 0 && !settings) item("file-problem") { InlineStatus(stringResource(problem), StatusTone.WARNING) }
            if (busy && !settings) item("file-progress") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            library?.awaitingRemoval?.let {
                item("file-removal") {
                    OutlinedButton(onClick = { runFileAction(R.string.spaces_files_remove_problem) {
                        account.resumeFileRemoval(target); account.refresh()
                    } }, enabled = !busy && canDownload, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.spaces_files_finish_removal))
                    }
                }
            }
            library?.upload?.let { upload ->
                item("file-retry") {
                    FilePanel(upload.file.name, stringResource(R.string.spaces_files_upload_pending), R.drawable.ic_file)
                    OutlinedButton(onClick = { runFileAction(R.string.spaces_files_upload_problem) {
                        account.sendPreparedFile(target); account.refresh()
                    } }, enabled = !busy && online, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.spaces_files_retry_upload))
                    }
                    TextButton(onClick = { runFileAction(R.string.spaces_files_upload_problem) {
                        account.discardFileUpload(target)
                    } }, enabled = !busy && online) { Text(stringResource(R.string.spaces_files_discard_upload)) }
                }
            }
            if (displayRows.isEmpty() && state.contentLoaded) item("empty") {
                Text(stringResource(R.string.spaces_files_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.hasLater) item("latest") {
                TextButton(onClick = account::latestMessages, enabled = !state.refreshing && !busy) {
                    Text(stringResource(R.string.spaces_files_latest))
                }
            }
            if (state.hasEarlier) item("older") {
                TextButton(onClick = account::olderMessages, enabled = !state.refreshing && !busy) {
                    Text(stringResource(R.string.spaces_files_older))
                }
            }
            if (state.current?.canSend == true) item("upload") {
                Button(onClick = { picker.launch(arrayOf("*/*")) },
                    enabled = canUpload && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.spaces_files_upload))
                }
            }
        }
    }

    if (settings) AlertDialog(
        onDismissRequest = { if (!busy) { settings = false; terms = null; problem = 0 } },
        title = { Text(stringResource(R.string.spaces_files_storage)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(storageLabel(library, online, joined))
                if (online && joined && lease != null && library?.expired == false && terms == null) {
                    TextButton(onClick = { revision++ }, enabled = !busy) {
                        Text(stringResource(R.string.spaces_files_refresh_storage))
                    }
                }
                lease?.offer?.let {
                    Text(it.origin)
                    Text(stringResource(R.string.spaces_files_expiry, fileDate(it.expiresAt)))
                }
                val approved = terms
                if (approved != null) {
                    Text(approved.origin, style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.spaces_files_review_capacity,
                        Formatter.formatShortFileSize(context, approved.capacityBytes)))
                    Text(stringResource(R.string.spaces_files_expiry, fileDate(approved.expiresAt)))
                    Text(if (approved.costNolan.signum() == 0) stringResource(R.string.spaces_files_price_free)
                        else stringResource(R.string.spaces_files_price_unsupported, approved.costNolan.toString()))
                    Text(stringResource(R.string.spaces_files_review_deadline, fileDate(approved.confirmBy)))
                } else if (canConfigure && library?.setup == null) {
                    OutlinedTextField(providerOrigin, { providerOrigin = it.take(512) }, singleLine = true,
                        enabled = !busy, label = { Text(stringResource(R.string.spaces_files_provider_url)) })
                }
                if (library?.setup != null) {
                    Text(stringResource(R.string.spaces_files_terms_pending))
                    TextButton(onClick = { runFileAction(R.string.spaces_files_storage_problem) {
                        account.discardFileStorageReview(target)
                    } }, enabled = !busy && canConfigure) { Text(stringResource(R.string.spaces_files_discard_terms)) }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (problem != 0) InlineStatus(stringResource(problem), StatusTone.WARNING)
            }
        },
        confirmButton = {
            val approved = terms
            when {
                approved != null -> TextButton(onClick = {
                    runFileAction(R.string.spaces_files_storage_problem) {
                        account.confirmFileStorage(target, approved)
                        terms = null
                        settings = false
                        account.refresh()
                    }
                }, enabled = canConfigure && !busy && approved.costNolan.signum() == 0) {
                    Text(stringResource(R.string.spaces_files_confirm_terms))
                }
                canConfigure && library?.setup != null -> TextButton(onClick = {
                    runFileAction(R.string.spaces_files_storage_problem) {
                        account.resumeFileStorage(target)
                        settings = false
                        account.refresh()
                    }
                }, enabled = !busy) { Text(stringResource(R.string.spaces_files_finish_terms)) }
                canConfigure -> TextButton(onClick = {
                    runFileAction(R.string.spaces_files_storage_problem) {
                        terms = account.reviewFileStorage(target, providerOrigin.trim())
                    }
                }, enabled = !busy && providerOrigin.isNotBlank()) {
                    Text(stringResource(R.string.spaces_files_review_terms))
                }
            }
        },
        dismissButton = { TextButton(onClick = { settings = false; terms = null; problem = 0 }, enabled = !busy) {
            Text(stringResource(android.R.string.cancel))
        } },
    )
    removeLocal?.let { id ->
        FileRemovalDialog(R.string.spaces_files_remove_offline, R.string.spaces_files_remove_offline_description,
            { removeLocal = null }) {
            removeLocal = null
            runFileAction(R.string.spaces_files_remove_problem) { account.removeFileOffline(target, id) }
        }
    }
    removeShared?.let { row ->
        FileRemovalDialog(R.string.spaces_files_remove_shared, R.string.spaces_files_remove_shared_description,
            { removeShared = null }) {
            removeShared = null
            runFileAction(R.string.spaces_files_remove_problem) {
                account.removeFileFromSpace(target, row); account.refresh()
                selected = null
            }
        }
    }
}

@Composable
private fun FileRemovalDialog(title: Int, description: Int, close: () -> Unit, remove: () -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text(stringResource(title)) },
        text = { Text(stringResource(description)) },
        confirmButton = { TextButton(onClick = remove) { Text(stringResource(title), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = close) { Text(stringResource(android.R.string.cancel)) } })
}

@Composable
private fun FilePanel(title: String, description: String, icon: Int, onClick: (() -> Unit)? = null) {
    val content: @Composable ColumnScope.() -> Unit = {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icon(painterResource(icon), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (onClick == null) Card(Modifier.fillMaxWidth(), content = content)
    else Card(onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp), content = content)
}

@Composable
private fun FileAttribution(row: SpaceClient.Message, bytes: Long?): String {
    val sender = registeredIdentity(row.author).name
    val date = fileDate(row.createdAt)
    return if (bytes == null) stringResource(R.string.spaces_files_attribution_unknown, sender, date)
    else stringResource(R.string.spaces_files_attribution, sender, Formatter.formatShortFileSize(LocalContext.current, bytes), date)
}

@Composable
private fun storageLabel(library: SpacesAccount.FileLibrary?, online: Boolean, joined: Boolean): String {
    val context = LocalContext.current
    val used = library?.usage
    return when {
        library == null -> stringResource(R.string.spaces_files_checking)
        library.expired -> stringResource(R.string.spaces_files_expired)
        !online -> stringResource(R.string.spaces_files_offline)
        !library.supported -> stringResource(R.string.spaces_files_node_unavailable)
        library.lease == null -> stringResource(R.string.spaces_files_unconfigured)
        !joined -> stringResource(R.string.spaces_files_join_for_download)
        !library.checkedProvider -> stringResource(R.string.spaces_files_checking)
        used == null -> stringResource(R.string.spaces_files_provider_unavailable)
        used.available < SpaceFileCipher.MIN_CIPHERTEXT_BYTES -> stringResource(R.string.spaces_files_full,
            Formatter.formatShortFileSize(context, used.used), Formatter.formatShortFileSize(context, used.capacity))
        else -> stringResource(R.string.spaces_files_usage_and_expiry,
            Formatter.formatShortFileSize(context, used.used), Formatter.formatShortFileSize(context, used.capacity),
            if (used.expiresAt - System.currentTimeMillis() <= FILE_STORAGE_WARNING_MILLIS)
                stringResource(R.string.spaces_files_expiry_soon, fileDate(used.expiresAt))
            else stringResource(R.string.spaces_files_expiry, fileDate(used.expiresAt)))
    }
}

@Composable
private fun fileDate(time: Long): String = remember(time) {
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))
}

private const val FILE_STORAGE_WARNING_MILLIS = 24 * 60 * 60_000L
