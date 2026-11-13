package nl.davidengelen.redsquare.vault

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.ui.*
import java.math.BigDecimal

@Composable
internal fun VaultKeyTransferDialog(model: VaultKeyTransfers, backend: SaitoBackend, key: VaultKey?,
    record: TransferStore.PendingKey?, recipient: String, onRecipient: (String) -> Unit,
    onPrepared: (String) -> Unit, onDismiss: () -> Unit, onNodeSettings: () -> Unit) {
    val transfer = record?.transfer
    val included = transfer?.let { model.inclusions[it.transaction.id] }
    val correctNode = record == null || record.backend == backend
    AccountDialog(title = stringResource(R.string.vault_key_transfer_title), closeLabel = stringResource(R.string.vault_key_transfer_close),
        onDismissRequest = onDismiss,
        text = {
            SingleOwnerReveal(transfer?.transaction?.id) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(transfer?.key?.name ?: key?.name ?: stringResource(R.string.vault_details_unavailable),
                        style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.vault_key_transfer_node, (record?.backend ?: backend).domain),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    InlineStatus(model.problem?.let { stringResource(it) },
                        if (model.problem == R.string.vault_key_waiting_detail) StatusTone.NEUTRAL else StatusTone.ERROR)
                    if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (transfer == null) {
                        Text(stringResource(R.string.vault_key_transfer_explanation), style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(recipient, { if (it.length <= 128) onRecipient(it) },
                            enabled = !model.busy, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.vault_key_recipient)) })
                    } else {
                        Text(stringResource(R.string.vault_key_whole_quantity, transfer.holding.quantity.toString()),
                            style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.vault_key_locked_deposit,
                            BigDecimal(transfer.holding.deposit, 8).stripTrailingZeros().toPlainString()))
                        Text(stringResource(R.string.vault_no_network_fee))
                        Text(stringResource(R.string.vault_key_recipient), style = MaterialTheme.typography.labelLarge)
                        Text(transfer.recipient, style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.vault_key_transfer_explanation), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(if (included != null) R.string.vault_key_included else if (record.mayHaveSent)
                            R.string.vault_key_waiting else R.string.vault_key_not_sent), style = MaterialTheme.typography.titleSmall)
                        included?.let { Text(stringResource(R.string.vault_key_inclusion_height, it.height.toString()),
                            style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }, confirmButton = {
            if (!correctNode) Button(onClick = onNodeSettings, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.vault_key_choose_transfer_node))
            } else if (record != null && included != null) {
                Button(onClick = { model.check(record) }, enabled = !model.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.vault_key_check_transfer))
                }
            } else if (record != null) {
                Button(onClick = { model.authorize(record) }, enabled = !model.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(if (record.mayHaveSent) R.string.vault_key_retry_original else R.string.vault_key_confirm_transfer))
                }
            } else Button(onClick = { key?.let { model.prepare(it, recipient.trim(), onPrepared) } },
                enabled = !model.busy && key != null && recipient.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.vault_key_review_transfer))
            }
        }, dismissButton = {
            if (record != null && correctNode) {
                if (!record.mayHaveSent) OutlinedButton(onClick = { model.discard(record, onDismiss) },
                    enabled = !model.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.vault_key_discard_transfer))
                } else if (included == null) OutlinedButton(onClick = { model.check(record) },
                    enabled = !model.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.vault_key_check_transfer))
                }
            }
        })
}

@Composable
internal fun VaultKeyTransferCard(record: TransferStore.PendingKey, model: VaultKeyTransfers,
    modifier: Modifier = Modifier, onReview: () -> Unit) {
    val included = model.inclusions[record.transfer.transaction.id] != null
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(record.transfer.key.name, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(if (included) R.string.vault_key_included else if (record.mayHaveSent)
                R.string.vault_key_waiting else R.string.vault_key_not_sent))
            Text(stringResource(R.string.vault_key_transfer_node, record.backend.domain),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(onClick = onReview, enabled = !model.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.vault_key_review_transfer))
            }
        }
    }
}
