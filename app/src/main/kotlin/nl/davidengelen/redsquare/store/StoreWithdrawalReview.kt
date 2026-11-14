package nl.davidengelen.redsquare.store

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.ui.*
import java.math.BigDecimal

@Composable
internal fun withdrawalStatus(record: StoreWithdrawalStore.Record): String = stringResource(when (record.result?.state) {
    SaitoStoreWithdrawal.State.SUBMITTED -> R.string.store_withdrawal_submitted
    SaitoStoreWithdrawal.State.ACCEPTED -> R.string.store_withdrawal_accepted
    SaitoStoreWithdrawal.State.FORWARD_FAILED -> R.string.store_withdrawal_forward_failed
    null -> if (record.mayHaveSent) R.string.store_withdrawal_waiting else R.string.store_withdrawal_unsent
})

@Composable
internal fun StoreWithdrawalReview(proceeds: StoreProceeds, record: StoreWithdrawalStore.Record) {
    val candidate = record.candidate; val quote = candidate.quote
    var now by remember(candidate.transaction.id) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(candidate.transaction.id) { while (true) { delay(1_000); now = System.currentTimeMillis() } }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(AppSpacing.screen), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.store_withdrawal_review), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.store_withdrawal_amount, quote.amount.stripTrailingZeros().toPlainString(), quote.ticker), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.store_crypto_network, record.network))
            Text(quote.address, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.store_withdrawal_service_fee, BigDecimal(quote.serviceFee, 8).stripTrailingZeros().toPlainString()))
            Text(stringResource(R.string.store_withdrawal_network_fee, BigDecimal(candidate.fee, 8).stripTrailingZeros().toPlainString()))
            Text(stringResource(R.string.store_withdrawal_net_unknown), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.store_withdrawal_manual), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(record.backend.webUrl, style = MaterialTheme.typography.bodySmall)
            SelectionContainer { Text(candidate.transaction.id, style = MaterialTheme.typography.bodySmall) }
        }
        HorizontalDivider()
        SingleOwnerReveal(Triple(record.mayHaveSent, record.result, quote.validAt(now))) {
            Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InlineStatus(proceeds.problem?.let { stringResource(it) }, StatusTone.NEUTRAL)
                Text(withdrawalStatus(record), style = MaterialTheme.typography.bodySmall)
                if (!quote.validAt(now) && !record.mayHaveSent) Text(stringResource(R.string.store_withdrawal_expired), style = MaterialTheme.typography.bodySmall)
                if (record.result?.state != SaitoStoreWithdrawal.State.SUBMITTED) Button(
                    onClick = { proceeds.authorize(record) }, enabled = !proceeds.busy && (record.mayHaveSent || quote.validAt(now)),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(if (record.mayHaveSent) R.string.store_withdrawal_retry else R.string.store_withdrawal_authorize))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = proceeds::closeReview, enabled = !proceeds.busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.done)) }
                    if (!record.mayHaveSent) TextButton(onClick = { proceeds.discard(record) }, enabled = !proceeds.busy,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.store_withdrawal_discard)) }
                }
            }
        }
    }
}
