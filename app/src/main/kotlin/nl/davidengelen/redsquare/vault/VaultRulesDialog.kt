package nl.davidengelen.redsquare.vault

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.ui.*

internal enum class VaultRuleMode(val label: Int, val explanation: Int) {
    STANDARD(R.string.vault_standard_key, R.string.vault_standard_key_description),
    RENTAL(R.string.vault_rental_key, R.string.vault_rental_key_description),
    CUSTOM(R.string.vault_advanced, R.string.vault_advanced_description)
}
internal data class VaultDraftRules(val mode: VaultRuleMode = VaultRuleMode.STANDARD, val source: String = "") {
    init { require(source.toByteArray(Charsets.UTF_8).size <= 64 * 1024) }
    fun script(creator: String): SaitoAccessScript? = when (mode) {
        VaultRuleMode.STANDARD -> null
        VaultRuleMode.RENTAL -> SaitoVaultRules.rental(creator)
        VaultRuleMode.CUSTOM -> SaitoAccessScript.parse(source).withoutWitnesses()
    }
    companion object {
        fun from(candidate: SaitoVaultMint.Candidate) = VaultDraftRules(
            if (candidate.keyType == "vault-nft-rental") VaultRuleMode.RENTAL
            else if (candidate.accessScript != null) VaultRuleMode.CUSTOM else VaultRuleMode.STANDARD,
            candidate.accessScript?.source.orEmpty())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VaultRulesDialog(creator: String, initial: VaultDraftRules, saving: Boolean, problem: Int?,
    onDismiss: () -> Unit, onApply: (VaultDraftRules, () -> Unit) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(initial.mode) }
    var source by rememberSaveable { mutableStateOf(initial.source.ifBlank { SaitoVaultRules.custom().source }) }
    var validation by remember { mutableStateOf(false) }
    var compiling by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val busy = saving || compiling
    AccountDialog(title = stringResource(R.string.vault_file_rules), closeLabel = stringResource(R.string.vault_close_rules),
        onDismissRequest = onDismiss, dismissEnabled = !busy,
        text = {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VaultRuleMode.entries.forEach { choice ->
                        FilterChip(mode == choice, {
                            if (choice == VaultRuleMode.CUSTOM && mode == VaultRuleMode.RENTAL)
                                source = SaitoVaultRules.rental(creator).source
                            mode = choice; validation = false
                        }, { Text(stringResource(choice.label)) }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
                Text(stringResource(mode.explanation), style = MaterialTheme.typography.bodyLarge)
                InlineStatus(if (validation) stringResource(R.string.vault_rules_invalid)
                    else problem?.let { stringResource(it) }, StatusTone.ERROR)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (mode == VaultRuleMode.CUSTOM) {
                    OutlinedTextField(source, { if (it.length <= 64 * 1024) { source = it; validation = false } },
                        label = { Text(stringResource(R.string.vault_access_script)) }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth(), minLines = 5, maxLines = 12,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                    Text(stringResource(R.string.vault_rules_public), style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(R.string.vault_copies_cannot_recall), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }, confirmButton = {
            Button(enabled = !busy, modifier = Modifier.heightIn(min = 48.dp), onClick = {
                if (!saving && !compiling) {
                    compiling = true; validation = false
                    scope.launch {
                        try {
                            val rules = withContext(Dispatchers.IO) {
                                val selected = VaultDraftRules(mode, if (mode == VaultRuleMode.CUSTOM) source else "")
                                selected.script(creator)
                                selected
                            }
                            onApply(rules, onDismiss)
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { validation = true }
                        finally { compiling = false }
                    }
                }
            }) { Text(stringResource(R.string.vault_use_rules)) }
        })
}
