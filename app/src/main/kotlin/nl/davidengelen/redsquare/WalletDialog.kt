package nl.davidengelen.redsquare

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.saito.ImportBalance
import nl.davidengelen.redsquare.ui.AccountCard
import nl.davidengelen.redsquare.ui.AccountActionRow
import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontFamily
import nl.davidengelen.redsquare.ui.AccountDialog

internal enum class WalletDestination { RECEIVE, SEND, RECOVERY }

/** Wallet presentation delegates all signing, authentication, import and export to existing owners. */
@Composable
internal fun WalletDialog(address: String?, failed: Boolean, walletExport: WalletExport,
    walletImport: WalletImport, scrollState: ScrollState, transfersAvailable: Boolean,
    onClose: () -> Unit, onOpen: (WalletDestination) -> Unit) {
    AccountDialog(
        onDismissRequest = { if (!walletImport.activating) {
            onClose()
        } },
        title = stringResource(if (walletImport.preview != null) R.string.wallet_import_review_title else R.string.your_wallet),
        closeLabel = stringResource(R.string.wallet_close_task),
        dismissEnabled = !walletImport.activating,
        text = {
            val addressLabel = stringResource(R.string.wallet_address)
            Column(
                Modifier.fillMaxWidth().verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AccountCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.wallet_identity_heading), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
                        when {
                            address != null -> Text(address, Modifier.semantics { contentDescription = addressLabel },
                                style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                            failed -> Text(stringResource(R.string.wallet_unavailable))
                            else -> Text(stringResource(R.string.wallet_opening))
                        }
                    }
                }
                InlineStatus(if (walletExport.busy) stringResource(R.string.wallet_export_in_progress) else null, StatusTone.WORKING)
                InlineStatus(if (walletImport.busy) stringResource(R.string.wallet_import_in_progress) else null, StatusTone.WORKING)
                walletExport.message?.let { message -> InlineStatus(stringResource(message), when (message) {
                    R.string.wallet_export_saved -> StatusTone.SUCCESS
                    R.string.wallet_export_cancelled -> StatusTone.INFO
                    else -> StatusTone.ERROR
                }) }
                walletImport.message?.let { message -> InlineStatus(stringResource(message), when (message) {
                    R.string.wallet_import_saved -> StatusTone.SUCCESS
                    R.string.wallet_import_cancelled -> StatusTone.INFO
                    else -> StatusTone.ERROR
                }) }
                walletImport.preview?.let { preview ->
                    AccountCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.wallet_import_step), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary)
                            Text(stringResource(if (preview.sameIdentity) R.string.wallet_import_restore else R.string.wallet_import_replace),
                                style = MaterialTheme.typography.titleMedium)
                            val selectedAddressLabel = stringResource(R.string.wallet_import_address)
                            Text(preview.publicKey, Modifier.semantics { contentDescription = selectedAddressLabel })
                            Text(stringResource(if (preview.sameIdentity) R.string.wallet_import_restore_detail else R.string.wallet_import_replace_detail))
                            if (!preview.sameIdentity) {
                                Text(stringResource(R.string.wallet_import_local_posts))
                                if (preview.risks?.otherAssetsUnknown == true) Text(stringResource(R.string.wallet_import_other_assets))
                                preview.risks?.savedName?.let { Text(stringResource(R.string.wallet_import_saved_name, it)) }
                                if (preview.risks?.pendingWork == true) Text(stringResource(R.string.wallet_import_pending_work))
                                Text(stringResource(when (preview.balance) {
                                    null -> R.string.wallet_import_balance_checking
                                    ImportBalance.ZERO -> R.string.wallet_import_balance_zero
                                    ImportBalance.POSITIVE -> R.string.wallet_import_balance_positive
                                    ImportBalance.UNKNOWN -> R.string.wallet_import_balance_unknown
                                }))
                                Text(stringResource(when {
                                    preview.backupVerified -> R.string.wallet_import_backup_verified
                                    preview.backupSkipped -> R.string.wallet_import_backup_skipped
                                    preview.balance == ImportBalance.ZERO -> R.string.wallet_import_backup_recommended
                                    else -> R.string.wallet_import_backup_required
                                }))
                                if (preview.balance == ImportBalance.ZERO && !preview.backupVerified && !preview.backupSkipped) {
                                    TextButton(enabled = !walletExport.busy && !walletImport.busy, onClick = walletImport::skipBackup) {
                                        Text(stringResource(R.string.wallet_import_skip_backup))
                                    }
                                }
                                TextButton(enabled = !walletExport.busy && !walletImport.busy,
                                    onClick = { walletImport.backUpCurrentWallet { verified -> walletExport.start(verified) } }) {
                                    Text(stringResource(R.string.wallet_import_backup))
                                }
                            }
                            Text(stringResource(if (preview.sameIdentity) R.string.wallet_import_restore_warning else R.string.wallet_import_work_warning))
                            val backupRequirementUnmet = !preview.sameIdentity && !preview.backupVerified &&
                                !(preview.balance == ImportBalance.ZERO && preview.backupSkipped)
                            if (!preview.acknowledged || backupRequirementUnmet) {
                                Text(stringResource(when {
                                    backupRequirementUnmet -> R.string.wallet_import_continue_after_backup
                                    else -> R.string.wallet_import_continue_after_acknowledgement
                                }), style = MaterialTheme.typography.bodySmall)
                            }
                            val riskLabel = stringResource(R.string.wallet_import_ack_label)
                            Row(Modifier.heightIn(min = 48.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Checkbox(checked = preview.acknowledged, onCheckedChange = walletImport::acknowledge,
                                    enabled = !walletImport.busy && !walletExport.busy,
                                    modifier = Modifier.semantics { contentDescription = riskLabel })
                                Text(stringResource(R.string.wallet_import_acknowledge))
                            }
                            TextButton(enabled = !walletExport.busy && !walletImport.busy && preview.acknowledged &&
                                (preview.sameIdentity || preview.backupVerified ||
                                    (preview.balance == ImportBalance.ZERO && preview.backupSkipped)), onClick = walletImport::confirm) {
                                Text(stringResource(if (preview.sameIdentity) R.string.wallet_import_confirm_restore else R.string.wallet_import_confirm_replace))
                            }
                        }
                    }
                }
                if (walletImport.preview == null) AccountCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.wallet_backup_section), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.wallet_backup_section_detail), style = MaterialTheme.typography.bodySmall)
                        AccountActionRow(stringResource(R.string.wallet_export), R.drawable.ic_save,
                            enabled = address != null && !walletExport.busy && !walletImport.busy,
                            onClick = { walletExport.start() })
                        AccountActionRow(stringResource(R.string.wallet_import), R.drawable.ic_wallet,
                            enabled = address != null && !walletExport.busy && !walletImport.busy, onClick = { walletExport.clearMessage(); walletImport.start() })
                        Text(stringResource(R.string.wallet_backup_private), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (walletImport.preview == null) AccountCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.wallet_payments_section), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.wallet_payments_section_detail), style = MaterialTheme.typography.bodySmall)
                        AccountActionRow(stringResource(R.string.wallet_balance_receive), R.drawable.ic_wallet,
                            enabled = address != null && !walletExport.busy && !walletImport.busy, onClick = { onOpen(WalletDestination.RECEIVE) })
                        AccountActionRow(stringResource(R.string.transfer_send), R.drawable.ic_forward,
                            enabled = address != null && transfersAvailable && !walletExport.busy && !walletImport.busy, onClick = { onOpen(WalletDestination.SEND) })
                    }
                }
                if (walletImport.preview == null) AccountCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.wallet_recovery_section), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.wallet_recovery_section_detail), style = MaterialTheme.typography.bodySmall)
                            AccountActionRow(stringResource(R.string.wallet_recovery_guide), R.drawable.ic_info,
                            onClick = { onOpen(WalletDestination.RECOVERY) })
                    }
                }
            }
        },
        confirmButton = {
            if (walletImport.preview != null) TextButton(
                enabled = !walletImport.activating,
                onClick = walletImport::cancel,
            ) { Text(stringResource(R.string.wallet_import_back)) }
            else TextButton(enabled = !walletImport.activating,
                onClick = { onClose() }) {
                Text(stringResource(R.string.done))
            }
        },
        dismissButton = {
            if (walletImport.preview != null) TextButton(
                enabled = !walletImport.activating,
                onClick = { onClose() },
            ) { Text(stringResource(R.string.wallet_import_cancel)) }
        },
    )
}
