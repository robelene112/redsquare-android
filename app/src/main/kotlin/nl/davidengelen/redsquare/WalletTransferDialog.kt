package nl.davidengelen.redsquare

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
    val hasPending = transfers.pending.isNotEmpty()
    AlertDialog(
        onDismissRequest = { transfers.cancel(); onClose() },
        title = { Text(stringResource(if (reviewed != null) R.string.transfer_confirm else R.string.transfer_send)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                transfers.message?.let { Text(stringResource(it)) }
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
                        if (inclusion == null) {
                            val previous = record.lastInclusion
                            if (previous == null) Text(stringResource(R.string.transfer_pending))
                            else Text(stringResource(R.string.transfer_inclusion_unknown, previous.height.toString()))
                        }
                        else {
                            Text(stringResource(R.string.transfer_included, inclusion.height.toString()))
                            Text(stringResource(R.string.transfer_observed))
                        }
                        Text(stringResource(R.string.transfer_amount_review, WalletTransfers.display(record.payment.amount)))
                        Text(stringResource(R.string.transfer_recipient_review, record.payment.recipient))
                        Text(stringResource(R.string.transfer_pending_backend, record.backend.domain))
                        if (inclusion == null) TextButton(enabled = !transfers.busy && record.backend.domain == transfers.backendLabel,
                            onClick = { transfers.prepareRetry(record.payment.transaction.id) }) {
                            Text(stringResource(R.string.transfer_retry))
                        }
                    }
                    if (!hasPending) {
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
            if (reviewed != null) TextButton(enabled = !transfers.busy, onClick = transfers::authenticateAndSend) {
                Text(stringResource(R.string.transfer_auth_send))
            } else if (!hasPending) TextButton(enabled = !transfers.busy,
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

