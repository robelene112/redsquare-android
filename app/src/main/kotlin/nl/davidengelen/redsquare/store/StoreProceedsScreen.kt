package nl.davidengelen.redsquare.store

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.ui.*

@Composable
internal fun StoreProceedsScreen(wallet: SaitoWallet, backend: SaitoBackend, selected: SaitoBackend,
    endpoint: String, modifier: Modifier, onBack: () -> Unit) {
    val activity = androidx.compose.ui.platform.LocalContext.current as Activity
    val scope = rememberCoroutineScope()
    val proceeds = remember(wallet.publicKey, backend, selected, endpoint) { StoreProceeds(activity, wallet, backend, selected, endpoint, scope) }
    DisposableEffect(proceeds) { onDispose { proceeds.dispose() } }
    LaunchedEffect(proceeds) { proceeds.load() }
    var choosing by remember { mutableStateOf<String?>(null) }
    var address by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("0") }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val record = proceeds.focused
    SingleOwnerReveal(record?.candidate?.transaction?.id ?: choosing ?: "proceeds", modifier.fillMaxSize()) {
    if (record != null) StoreWithdrawalReview(proceeds, record)
    else if (choosing != null) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppSpacing.screen), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.store_withdrawal_action, requireNotNull(choosing)), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.store_withdrawal_destination_explanation))
        OutlinedTextField(address, { if (it.length <= 2048) address = it }, label = { Text(stringResource(R.string.store_withdrawal_destination)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(fee, { if (it.length <= 24) fee = it }, label = { Text(stringResource(R.string.store_withdrawal_fee_field)) }, modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.store_withdrawal_manual), color = MaterialTheme.colorScheme.onSurfaceVariant)
        InlineStatus(proceeds.problem?.let { stringResource(it) }, StatusTone.NEUTRAL)
        if (proceeds.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Button(onClick = { keyboard?.hide(); proceeds.prepare(requireNotNull(choosing), address.trim(), fee) { choosing = null } },
            enabled = !proceeds.busy && address.isNotBlank() && fee.toBigDecimalOrNull()?.signum()?.let { it >= 0 } == true,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.store_withdrawal_review)) }
        TextButton(onClick = { choosing = null }, enabled = !proceeds.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.done)) }
    }
    else {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppSpacing.screen), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.store_proceeds_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.store_proceeds_explanation), color = MaterialTheme.colorScheme.onSurfaceVariant)
        InlineStatus(proceeds.problem?.let { stringResource(it) }, StatusTone.NEUTRAL)
        if (proceeds.report != null && proceeds.methods == null) Text(stringResource(R.string.store_proceeds_provider_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (proceeds.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        SingleOwnerReveal(proceeds.report) {
            val report = proceeds.report
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (report != null) {
                    val tickers = (report.eligible.keys + report.pending.keys).sortedDescending()
                    if (tickers.isEmpty()) Text(stringResource(R.string.store_proceeds_empty))
                    for (ticker in tickers) Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(ticker, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.store_proceeds_eligible, (report.eligible[ticker] ?: java.math.BigDecimal.ZERO).stripTrailingZeros().toPlainString(), ticker))
                            Text(stringResource(R.string.store_proceeds_pending, (report.pending[ticker] ?: java.math.BigDecimal.ZERO).stripTrailingZeros().toPlainString(), ticker), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if ((report.eligible[ticker]?.signum() ?: 0) > 0) OutlinedButton(onClick = { choosing = ticker; address = ""; fee = "0" },
                                enabled = !proceeds.loading && proceeds.methods?.currencies?.any { it.ticker == ticker } == true,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.store_withdrawal_action, ticker)) }
                        }
                    }
                }
            }
        }
        for (saved in proceeds.records.filter { it.backend == backend }) OutlinedCard(onClick = { proceeds.open(saved) }) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.store_withdrawal_saved, saved.candidate.quote.amount.stripTrailingZeros().toPlainString(), saved.candidate.quote.ticker), style = MaterialTheme.typography.titleMedium)
                Text(withdrawalStatus(saved), style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedButton(onClick = proceeds::load, enabled = !proceeds.loading, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.store_refresh)) }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.done)) }
    }
    }
    }
}
