package nl.davidengelen.redsquare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.saito.SaitoWallet

/** Debug-only, entirely local proof. No fixture key or private material is rendered. */
class CompatibilityActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val scope = rememberCoroutineScope()
            var running by remember { mutableStateOf(false) }
            var result by remember { mutableStateOf<Boolean?>(null) }
            var address by remember { mutableStateOf<String?>(null) }
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.compatibility_title), style = MaterialTheme.typography.headlineMedium)
                        Text(stringResource(R.string.compatibility_description))
                        Button(enabled = !running, onClick = {
                            running = true
                            scope.launch {
                                val checked = withContext(Dispatchers.Default) {
                                    runCatching {
                                        val wallet = SaitoWallet.create()
                                        val restored = SaitoWallet.fromBackup(wallet.exportJson())
                                        check(restored.publicKey == wallet.publicKey)
                                        val message = "Red Square local compatibility check".toByteArray(Charsets.UTF_8)
                                        val verified = SaitoWallet.verify(message, restored.sign(message), wallet.publicKey)
                                        wallet.publicKey to verified
                                    }.getOrNull()
                                }
                                address = checked?.first
                                result = checked?.second ?: false
                                running = false
                            }
                        }) {
                            Text(stringResource(if (running) R.string.compatibility_running else R.string.compatibility_action))
                        }
                        result?.let { Text(stringResource(if (it) R.string.compatibility_pass else R.string.compatibility_fail)) }
                        address?.let { Text(stringResource(R.string.compatibility_address, it)) }
                    }
                }
            }
        }
    }
}
