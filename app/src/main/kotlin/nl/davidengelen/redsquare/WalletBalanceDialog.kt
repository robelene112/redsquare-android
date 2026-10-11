package nl.davidengelen.redsquare

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoBalanceSnapshot

@Composable
fun WalletBalanceDialog(publicKey: String, backend: SaitoBackend?, balances: WalletBalances, onClose: () -> Unit) {
    var observation by remember(publicKey, backend) { mutableStateOf<SaitoBalanceSnapshot?>(null) }
    var loading by remember(publicKey, backend) { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    LaunchedEffect(publicKey, backend, refresh) {
        loading = true
        observation = null
        observation = backend?.let { withContext(Dispatchers.IO) { balances.read(it, publicKey) } }
        loading = false
    }
    AlertDialog(onDismissRequest = onClose,
        title = { Text(stringResource(R.string.wallet_balance_receive)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.wallet_balance_reported), style = MaterialTheme.typography.labelLarge)
                Text(when {
                    loading -> stringResource(R.string.wallet_balance_loading)
                    observation != null -> stringResource(R.string.wallet_balance_amount, observation!!.saito)
                    else -> stringResource(R.string.wallet_balance_unavailable)
                }, style = MaterialTheme.typography.headlineSmall)
                backend?.let { Text(stringResource(R.string.wallet_balance_source, it.domain)) }
                Text(stringResource(R.string.wallet_balance_observation), style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = !loading, onClick = { refresh++ }) { Text(stringResource(R.string.feed_refresh)) }
                val label = stringResource(R.string.wallet_receiving_address)
                Text(label, style = MaterialTheme.typography.labelLarge)
                Text(publicKey, Modifier.semantics { contentDescription = label })
                TextButton(onClick = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText(label, publicKey))
                }) { Text(stringResource(R.string.wallet_copy_address)) }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.done)) } })
}
