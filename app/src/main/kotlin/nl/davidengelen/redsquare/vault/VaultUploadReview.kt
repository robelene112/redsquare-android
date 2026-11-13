package nl.davidengelen.redsquare.vault

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.ui.*

@Composable
internal fun VaultUploadReview(view: VaultUploadView, modifier: Modifier, onDiscard: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.padding(horizontal = AppSpacing.screen), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(if (view.phase == VaultUploadPhase.REVIEW) R.string.vault_review_upload else R.string.vault_preparing_file),
                style = MaterialTheme.typography.headlineSmall)
            InlineStatus(view.problem?.let { stringResource(it) }, StatusTone.ERROR)
            if (view.phase != VaultUploadPhase.REVIEW) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(Formatter.formatShortFileSize(context, view.bytesRead), style = MaterialTheme.typography.bodyMedium)
                if (view.phase == VaultUploadPhase.PREPARING) TextButton(onClick = onCancel,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.vault_cancel_reading)) }
            } else {
                view.file?.let { file ->
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(file.name, style = MaterialTheme.typography.titleLarge)
                            Text("${Formatter.formatShortFileSize(context, file.size.toLong())} · ${file.mime}",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(stringResource(R.string.vault_standard_key), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.vault_standard_key_description), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.vault_key_deposit), style = MaterialTheme.typography.labelLarge)
                    Text("1 SAITO", style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.vault_deposit_description), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.vault_upload_privacy), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.vault_discard_file))
                }
            }
        }
    }
}
