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
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppSpacing.screen), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.store_proceeds_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.store_proceeds_explanation), color = MaterialTheme.colorScheme.onSurfaceVariant)
        InlineStatus(proceeds.problem?.let { stringResource(it) }, StatusTone.NEUTRAL)
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
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = proceeds::load, enabled = !proceeds.loading, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.store_refresh)) }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.done)) }
    }
}
