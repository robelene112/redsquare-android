package nl.davidengelen.redsquare.vault

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.ui.*

@Composable
internal fun VaultUploadReview(view: VaultUploadView, modifier: Modifier, onDiscard: () -> Unit, onCancel: () -> Unit,
    onSubmit: () -> Unit, onStop: () -> Unit, onCheck: () -> Unit, onBack: () -> Unit, onLibrary: () -> Unit, creator: String, onRules: (VaultDraftRules, () -> Unit) -> Unit) {
    var rulesOpen by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val working = view.phase in setOf(VaultUploadPhase.LOADING, VaultUploadPhase.PREPARING, VaultUploadPhase.CHECKING,
        VaultUploadPhase.AUTHORIZING, VaultUploadPhase.SENDING)
    val title = when (view.phase) {
        VaultUploadPhase.COMPLETE -> R.string.vault_upload_complete
        VaultUploadPhase.WAITING -> R.string.vault_upload_waiting
        VaultUploadPhase.SENDING -> when (view.step) {
            VaultSendStep.KEY -> R.string.vault_sending_key
            VaultSendStep.FILE -> R.string.vault_sending_file
            VaultSendStep.OWNERSHIP -> R.string.vault_checking_key
            null -> R.string.vault_upload_sending
        }
        VaultUploadPhase.CHECKING -> R.string.vault_checking_upload
        VaultUploadPhase.AUTHORIZING -> R.string.vault_authorize_upload
        VaultUploadPhase.PREPARING, VaultUploadPhase.LOADING -> R.string.vault_preparing_file
        else -> R.string.vault_review_upload
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.padding(horizontal = AppSpacing.screen), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FrameIconButton(R.drawable.ic_back, stringResource(R.string.vault_show_files), onClick = onLibrary)
                Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
            }
            InlineStatus(view.problem?.let { stringResource(it) }, StatusTone.ERROR)
            if (working) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                if (view.phase == VaultUploadPhase.PREPARING) {
                    Text(Formatter.formatShortFileSize(context, view.bytesRead), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.vault_cancel_reading))
                    }
                }
                if (view.phase == VaultUploadPhase.SENDING) TextButton(onClick = onStop, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.vault_stop_upload))
                }
            }
            (view.file ?: view.record?.let { VaultUploadFile(it.candidate.name, it.mime, it.size) })?.let { file ->
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(file.name, style = MaterialTheme.typography.titleLarge)
                        Text("${Formatter.formatShortFileSize(context, file.size.toLong())} · ${file.mime}",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (view.phase == VaultUploadPhase.REVIEW && view.file != null) {
                    Text(stringResource(view.rules.mode.label), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(view.rules.mode.explanation), style = MaterialTheme.typography.bodyMedium)
                    if (view.record == null) OutlinedButton(onClick = { rulesOpen = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.vault_change_rules))
                    }
                    Text(stringResource(R.string.vault_key_deposit), style = MaterialTheme.typography.labelLarge)
                    Text("1 SAITO", style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.vault_no_network_fee), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(R.string.vault_deposit_description), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.vault_upload_privacy), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.vault_create_upload))
                    }
                }
            }
            if (view.phase in setOf(VaultUploadPhase.WAITING, VaultUploadPhase.COMPLETE)) view.record?.let { record ->
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(if (record.submitted) R.string.vault_key_submitted else if (record.mayHaveSent)
                            R.string.vault_key_submission_unverified else R.string.vault_key_not_submitted))
                        Text(stringResource(if (record.uploaded) R.string.vault_file_accepted else if (record.rejected)
                            R.string.vault_file_rejected else R.string.vault_file_unverified))
                        Text(stringResource(if (record.owned) R.string.vault_key_owned else R.string.vault_key_not_observed))
                    }
                }
            }
            if (view.phase == VaultUploadPhase.COMPLETE) {
                Text(stringResource(R.string.vault_upload_complete_detail), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.vault_back_to_files))
                }
            }
            if (view.phase == VaultUploadPhase.WAITING) {
                Text(stringResource(R.string.vault_upload_waiting_detail), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onCheck, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = view.record != null) {
                    Text(stringResource(R.string.vault_check_upload))
                }
                if (view.file != null) OutlinedButton(onClick = onSubmit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.vault_retry_upload))
                }
            }
            if (view.phase == VaultUploadPhase.REVIEW && view.record?.mayHaveSent != true && view.record?.uploaded != true) {
                OutlinedButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.vault_discard_file))
                }
            }
        }
    }
    if (rulesOpen) VaultRulesDialog(creator, view.rules, working, view.problem,
        onDismiss = { rulesOpen = false }, onApply = onRules)

}
