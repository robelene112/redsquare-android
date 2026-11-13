package nl.davidengelen.redsquare.vault

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.SaitoAccessScript
import nl.davidengelen.redsquare.ui.*

/** A focused read task. The parent retains the editable draft and owns any file operation. */
@Composable
internal fun VaultAccessDialog(entry: VaultEntry, node: String, source: String, inputs: String,
    busy: Boolean, problem: Int?, onSource: (String) -> Unit, onInputs: (String) -> Unit,
    onEdited: () -> Unit, onDismiss: () -> Unit,
    onKeep: (SaitoAccessScript) -> Unit, onSave: (SaitoAccessScript) -> Unit) {
    val scope = rememberCoroutineScope()
    var compiling by remember { mutableStateOf(false) }
    var validation by remember { mutableStateOf<Int?>(null) }
    val original = requireNotNull(entry.key?.customScript)
    val working = busy || compiling
    fun request(action: (SaitoAccessScript) -> Unit) {
        if (busy || compiling) return
        compiling = true; validation = null; onEdited()
        scope.launch {
            try {
                val (script, originalHash) = withContext(Dispatchers.IO) {
                    SaitoAccessScript.parse(source).withWitnesses(inputs) to SaitoAccessScript.parse(original).hash
                }
                if (script.hash != originalHash) validation = R.string.vault_access_rules_changed
                else action(script)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { validation = R.string.vault_access_json_invalid }
            finally { compiling = false }
        }
    }
    AccountDialog(title = stringResource(R.string.vault_file_access), closeLabel = stringResource(R.string.vault_close_access),
        onDismissRequest = onDismiss,
        text = {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(entry.name, style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.vault_access_node, node), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                InlineStatus((validation ?: problem)?.let { stringResource(it) }, StatusTone.ERROR)
                if (working) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.vault_checking_access), style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedTextField(source, { if (it.length <= 64 * 1024) { onSource(it); validation = null; onEdited() } },
                    label = { Text(stringResource(R.string.vault_access_script)) }, enabled = !working,
                    modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 7,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    supportingText = { Text(stringResource(R.string.vault_access_script_help)) })
                TextButton(onClick = { onSource(original); validation = null; onEdited() }, enabled = !working,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.vault_restore_access_script)) }
                OutlinedTextField(inputs, { if (it.length <= 64 * 1024) { onInputs(it); validation = null; onEdited() } },
                    label = { Text(stringResource(R.string.vault_unlock_inputs)) }, enabled = !working,
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    supportingText = { Text(stringResource(R.string.vault_unlock_inputs_help)) })
            }
        },
        confirmButton = {
            Button(onClick = { request(onKeep) }, enabled = !working, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.vault_keep_offline))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = { request(onSave) }, enabled = !working, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.vault_save_as))
            }
        })
}
