package nl.davidengelen.redsquare

import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
fun WalletTransferDialog(transfers: WalletTransfers, onClose: () -> Unit) {
    var recipient by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("0") }
    var memo by remember { mutableStateOf("") }
    LaunchedEffect(transfers) { transfers.load() }
    DisposableEffect(transfers) { onDispose { transfers.cancel() } }
    val reviewed = transfers.review?.payment
    val recordsReadable = transfers.message != R.string.transfer_storage_unavailable
    val transferActionsEnabled = !transfers.busy && recordsReadable
    val canPrepare = recordsReadable &&
        (transfers.pending.isEmpty() || transfers.available?.toBigDecimalOrNull()?.signum() == 1)
    AlertDialog(
        onDismissRequest = { transfers.cancel(); onClose() },
        title = { Text(stringResource(if (reviewed != null) R.string.transfer_confirm else R.string.transfer_send)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                InlineStatus(transfers.message?.let { stringResource(it) }
                    ?: if (transfers.busy) stringResource(R.string.status_transfer_processing) else null,
                    if (transfers.busy) StatusTone.WORKING else if (transfers.message == R.string.transfer_cancelled)
                        StatusTone.INFO else StatusTone.ERROR)
                if (reviewed != null) {
                    Text(stringResource(R.string.transfer_recipient_review, reviewed.recipient))
                    Text(stringResource(R.string.transfer_amount_review, WalletTransfers.display(reviewed.amount)))
                    Text(stringResource(R.string.transfer_fee_review, WalletTransfers.display(reviewed.fee)))
                    Text(stringResource(R.string.transfer_backend_review, transfers.backendLabel))
                    if (reviewed.memo.isNotEmpty()) Text(stringResource(R.string.transfer_memo_review, reviewed.memo))
                    Text(stringResource(R.string.transfer_public_warning))
                } else {
                    transfers.available?.let { Text(stringResource(R.string.transfer_available, it)) }
                    transfers.pending.forEach { record ->
                        val inclusion = transfers.inclusions[record.payment.transaction.id]
                        val previous = record.lastInclusion
                        val reorganized = record.payment.transaction.id in transfers.reorganized
                        InlineStatus(when {
                            inclusion != null -> stringResource(R.string.transfer_included, inclusion.height.toString())
                            previous == null -> stringResource(R.string.transfer_pending)
                            reorganized -> stringResource(R.string.transfer_reorganized, previous.height.toString())
                            else -> stringResource(R.string.transfer_inclusion_unknown, previous.height.toString())
                        }, if (inclusion != null) StatusTone.SUCCESS else StatusTone.WARNING)
                        if (inclusion != null || (previous != null && reorganized))
                            Text(stringResource(R.string.transfer_observed))
                        Text(stringResource(R.string.transfer_amount_review, WalletTransfers.display(record.payment.amount)))
                        Text(stringResource(R.string.transfer_recipient_review, record.payment.recipient))
                        Text(stringResource(R.string.transfer_pending_backend, record.backend.domain))
                        if (inclusion == null) TextButton(enabled = transferActionsEnabled && record.backend.domain == transfers.backendLabel,
                            onClick = { transfers.prepareRetry(record.payment.transaction.id) }) {
                            Text(stringResource(R.string.transfer_retry))
                        }
                    }
                    if (canPrepare) {
                        TransferField(R.string.transfer_recipient, recipient) { recipient = it }
                        TransferField(R.string.transfer_amount, amount) { amount = it }
                        TransferField(R.string.transfer_fee, fee) { fee = it }
                        TransferField(R.string.transfer_memo, memo) { memo = it }
                        Text(stringResource(R.string.transfer_public_warning))
                    }
                }
            }
        },
        confirmButton = {
            if (reviewed != null) TextButton(enabled = transferActionsEnabled, onClick = transfers::authenticateAndSend) {
                Text(stringResource(R.string.transfer_auth_send))
            } else if (canPrepare) TextButton(enabled = transferActionsEnabled,
                onClick = { transfers.prepare(recipient, amount, fee, memo) }) {
                Text(stringResource(R.string.transfer_review))
            }
        },
        dismissButton = { TextButton(onClick = { transfers.cancel(); onClose() }) { Text(stringResource(R.string.done)) } },
    )
}

@Composable
private fun TransferField(labelId: Int, value: String, update: (String) -> Unit) {
    val label = stringResource(labelId)
    OutlinedTextField(value = value, onValueChange = update, singleLine = true,
        label = { Text(label) }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = label })
}

