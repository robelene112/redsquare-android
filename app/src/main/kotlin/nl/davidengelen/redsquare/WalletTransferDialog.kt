package nl.davidengelen.redsquare

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.tween
import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone

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
    val reviewStep = reviewed != null
    AlertDialog(
        onDismissRequest = { transfers.cancel(); onClose() },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(if (reviewStep) R.string.transfer_confirm else R.string.transfer_send))
                Text(
                    stringResource(if (reviewStep) R.string.transfer_step_review else R.string.transfer_step_details),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                LinearProgressIndicator(
                    progress = { if (reviewStep) 1f else 0.5f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InlineStatus(
                    transfers.message?.let { stringResource(it) }
                        ?: if (transfers.busy) stringResource(R.string.status_transfer_processing) else null,
                    if (transfers.busy) StatusTone.WORKING
                    else if (transfers.message == R.string.transfer_cancelled) StatusTone.INFO
                    else StatusTone.ERROR,
                )
                AnimatedContent(
                    targetState = reviewStep,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                    label = "payment-review-step",
                ) { showReview ->
                if (showReview) {
                    val payment = requireNotNull(reviewed)
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            TransferReviewValue(R.string.transfer_recipient_review, payment.recipient)
                            TransferReviewValue(R.string.transfer_amount_review, WalletTransfers.display(payment.amount))
                            TransferReviewValue(R.string.transfer_fee_review, WalletTransfers.display(payment.fee))
                            TransferReviewValue(R.string.transfer_backend_review, transfers.backendLabel)
                            if (payment.memo.isNotEmpty()) TransferReviewValue(R.string.transfer_memo_review, payment.memo)
                        }
                    }
                    Text(stringResource(R.string.transfer_public_warning), style = MaterialTheme.typography.bodySmall)
                } else {
                    transfers.available?.let {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.transfer_available, it), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
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
                        if (inclusion == null) TextButton(
                            enabled = transferActionsEnabled && record.backend.domain == transfers.backendLabel,
                            onClick = { transfers.prepareRetry(record.payment.transaction.id) },
                        ) { Text(stringResource(R.string.transfer_retry)) }
                    }
                    if (canPrepare) {
                        TransferField(R.string.transfer_recipient, recipient, KeyboardType.Text, ImeAction.Next) { recipient = it }
                        TransferField(R.string.transfer_amount, amount, KeyboardType.Decimal, ImeAction.Next) { amount = it }
                        TransferField(R.string.transfer_fee, fee, KeyboardType.Decimal, ImeAction.Next) { fee = it }
                        TransferField(R.string.transfer_memo, memo, KeyboardType.Text, ImeAction.Done) { memo = it }
                        Text(stringResource(R.string.transfer_public_warning), style = MaterialTheme.typography.bodySmall)
                    }
                }
                }
            }
        },
        confirmButton = {
            if (reviewed != null) TextButton(enabled = transferActionsEnabled, onClick = transfers::authenticateAndSend) {
                Text(stringResource(R.string.transfer_auth_send))
            } else if (canPrepare) TextButton(
                enabled = transferActionsEnabled,
                onClick = { transfers.prepare(recipient, amount, fee, memo) },
            ) { Text(stringResource(R.string.transfer_review)) }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (reviewStep) TextButton(onClick = transfers::returnToDetails) {
                    Text(stringResource(R.string.transfer_back))
                }
                TextButton(onClick = { transfers.cancel(); onClose() }) {
                    Text(stringResource(if (transfers.pending.isEmpty()) R.string.transfer_cancel else R.string.transfer_close))
                }
            }
        },
    )
}

@Composable
private fun TransferReviewValue(labelId: Int, value: String) {
    val formatted = stringResource(labelId, value)
    val valueStart = formatted.indexOf(value)
    check(valueStart >= 0)
    val prefix = formatted.substring(0, valueStart)
    val suffix = formatted.substring(valueStart + value.length)
    val valueStyle = if (labelId == R.string.transfer_amount_review || labelId == R.string.transfer_fee_review)
        MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge
    Text(buildAnnotatedString {
        withStyle(MaterialTheme.typography.labelLarge.toSpanStyle()) { append(prefix) }
        withStyle(valueStyle.toSpanStyle()) { append(value) }
        withStyle(MaterialTheme.typography.labelLarge.toSpanStyle()) { append(suffix) }
    })
}

@Composable
private fun TransferField(
    labelId: Int,
    value: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    update: (String) -> Unit,
) {
    val label = stringResource(labelId)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = value,
        onValueChange = { entered -> update(if (keyboardType == KeyboardType.Decimal) entered.replace(',', '.') else entered) },
        singleLine = true,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
            onDone = { keyboardController?.hide() },
        ),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
    )
}
