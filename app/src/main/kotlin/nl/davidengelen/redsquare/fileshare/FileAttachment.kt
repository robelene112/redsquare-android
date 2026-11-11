package nl.davidengelen.redsquare.fileshare

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone
import java.util.Locale

/** Document picking preserves the Chat draft; transfer ownership remains outside composition. */
@Composable
internal fun FileAttachment(session: FileSession?, enabled: Boolean, beforePicker: suspend () -> Boolean,
    content: @Composable (pickFile: (() -> Unit)?, panel: @Composable () -> Unit) -> Unit) {
    if (session == null) { content(null, {}); return }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val binding = "${session.owner}:${session.backend.id}:${session.contact}"
    var pickingFor by rememberSaveable(binding) { mutableStateOf<String?>(null) }
    var pickerFailed by remember(binding) { mutableStateOf(false) }
    val currentEnabled by rememberUpdatedState(enabled)
    val ready by session.ready.collectAsState()
    val states by session.transfers.collectAsState()
    val busy = states.any { it.phase.active || it.phase == FilePhase.OFFERED }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val accepted = pickingFor == binding
        pickingFor = null
        if (accepted && uri != null && currentEnabled) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (_: SecurityException) { /* The immediately opened stream can still use the picker grant. */ }
            session.send(uri)
        }
    }
    content({
        if (enabled && ready && !busy && pickingFor == null) scope.launch {
            try {
                if (beforePicker() && currentEnabled) {
                    pickingFor = binding; pickerFailed = false; picker.launch(arrayOf("*/*"))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { pickingFor = null; pickerFailed = true }
        }
    }, {
        InlineStatus(if (pickerFailed) stringResource(R.string.fileshare_error) else null, StatusTone.ERROR)
        FileTransferPanel(session, enabled)
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileTransferPanel(session: FileSession, enabled: Boolean) {
    val transfers by session.transfers.collectAsState()
    val error by session.error.collectAsState()
    var expanded by remember(session) { mutableStateOf(false) }
    var saving by remember(session) { mutableStateOf<String?>(null) }
    var savingError by remember(session) { mutableStateOf(false) }
    var selectedSave by rememberSaveable(session.owner, session.contact, session.backend) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        val token = selectedSave; selectedSave = null
        if (uri != null && token != null) {
            saving = token; savingError = false
            scope.launch {
                try { savingError = !session.export(token, uri) }
                finally { saving = null }
            }
        }
    }
    val onSave: (FileTransfer) -> Unit = { value ->
        selectedSave = value.token; savingError = false
        try { save.launch(value.offer.name) } catch (_: Exception) { selectedSave = null; savingError = true }
    }
    Column(Modifier.animateContentSize()) {
        InlineStatus(if (error) stringResource(R.string.fileshare_error) else null, StatusTone.ERROR)
        InlineStatus(if (savingError) stringResource(R.string.fileshare_save_error) else null, StatusTone.ERROR)
        transfers.firstOrNull()?.let { latest ->
            FileTransferCard(latest, session, enabled && saving == null, saving == latest.token, onSave)
            if (transfers.size > 1) TextButton(onClick = { expanded = true }) { Text(stringResource(R.string.fileshare_all)) }
        }
    }
    if (expanded) ModalBottomSheet(onDismissRequest = { expanded = false }) {
        Text(stringResource(R.string.fileshare_all), style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = AppSpacing.screen, vertical = 12.dp))
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), contentPadding = PaddingValues(AppSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(transfers, key = { it.token }) { value ->
                FileTransferCard(value, session, enabled && saving == null, saving == value.token, onSave)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FileTransferCard(value: FileTransfer, session: FileSession, enabled: Boolean,
    saving: Boolean, onSave: (FileTransfer) -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(painterResource(R.drawable.ic_file), contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(value.offer.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    AnimatedContent(if (saving) R.string.fileshare_saving else filePhaseLabel(value.phase, value.incoming), label = "fileStatus") { label ->
                        Text(stringResource(label), style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
            }
            if (value.phase in setOf(FilePhase.SENDING, FilePhase.RECEIVING)) {
                LinearProgressIndicator(progress = { value.bytes.toFloat() / value.offer.size }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.fileshare_progress, fileSize(value.bytes), fileSize(value.offer.size)), style = MaterialTheme.typography.bodySmall)
            } else Text(fileSize(value.offer.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (value.phase in setOf(FilePhase.OFFERED, FilePhase.WAITING, FilePhase.CONNECTING))
                Text(stringResource(R.string.fileshare_live_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (value.incoming && value.phase in setOf(FilePhase.INTERRUPTED, FilePhase.FAILED))
                Text(stringResource(R.string.fileshare_incoming_interrupted), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (value.phase == FilePhase.OFFERED) {
                    FilledTonalButton(enabled = enabled, onClick = { session.accept(value.token) }) { Text(stringResource(R.string.fileshare_accept)) }
                    TextButton(enabled = enabled, onClick = { session.cancel(value.token) }) { Text(stringResource(R.string.fileshare_decline)) }
                } else if (value.phase.active) {
                    TextButton(enabled = enabled, onClick = { session.cancel(value.token) }) { Text(stringResource(R.string.fileshare_cancel)) }
                } else if (value.incoming && value.phase in setOf(FilePhase.COMPLETE, FilePhase.SAVED)) {
                    FilledTonalButton(enabled = enabled, onClick = { onSave(value) }) { Text(stringResource(R.string.fileshare_save)) }
                } else if (!value.incoming && value.source != null) {
                    TextButton(enabled = enabled, onClick = { session.retry(value.token) }) { Text(stringResource(R.string.fileshare_retry)) }
                }
            }
        }
    }
}

internal fun fileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format(Locale.getDefault(), "%.1f GB", bytes / (1024.0 * 1024 * 1024))
}
