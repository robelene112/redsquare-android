package nl.davidengelen.redsquare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var address by remember { mutableStateOf<String?>(null) }
            var failed by remember { mutableStateOf(false) }
            var retry by remember { mutableIntStateOf(0) }
            var walletOpen by remember { mutableStateOf(false) }
            LaunchedEffect(retry) {
                failed = false
                address = withContext(Dispatchers.IO) {
                    try { AndroidWalletStore(applicationContext).loadOrCreate().publicKey }
                    catch (_: Exception) { null }
                }
                failed = address == null
            }
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                        TextButton(onClick = { walletOpen = true }) { Text(stringResource(R.string.wallet)) }
                        if (failed) {
                            Text(stringResource(R.string.wallet_recovery_error))
                            Button(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
                        }
                    }
                    if (walletOpen) AlertDialog(
                        onDismissRequest = { walletOpen = false },
                        title = { Text(stringResource(R.string.your_wallet)) },
                        text = {
                            val addressLabel = stringResource(R.string.wallet_address)
                            when {
                                address != null -> Text(address!!, Modifier.semantics { contentDescription = addressLabel })
                                failed -> Text(stringResource(R.string.wallet_unavailable))
                                else -> Text(stringResource(R.string.wallet_opening))
                            }
                        },
                        confirmButton = { TextButton(onClick = { walletOpen = false }) { Text(stringResource(R.string.done)) } }
                    )
                }
            }
        }
    }
}
