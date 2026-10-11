package nl.davidengelen.redsquare.registry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.SaitoPeer
import nl.davidengelen.redsquare.saito.SaitoRegistry
import nl.davidengelen.redsquare.saito.SaitoWallet

/** Optional Registry UI. Opening it never signs or sends a registration. */
@Composable
internal fun UsernameScreen(wallet: SaitoWallet, endpoint: String, modifier: Modifier, onClose: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onClose)
    Column(modifier.imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onClose) { Text(stringResource(R.string.browse_back)) }
        Text(stringResource(R.string.username_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.username_optional))
        OutlinedTextField(value = name, enabled = !busy, singleLine = true,
            onValueChange = { name = it; status = null },
            label = { Text(stringResource(R.string.username_label)) },
            supportingText = { Text(stringResource(R.string.username_rules)) }, modifier = Modifier.fillMaxWidth())
        Button(enabled = !busy, onClick = {
            if (!name.matches(Regex("[A-Za-z0-9]{1,45}"))) {
                status = R.string.username_invalid
            } else {
                val identifier = "$name@saito"
                busy = true
                status = null
                scope.launch {
                    try {
                        val registered = withContext(Dispatchers.IO) {
                            SaitoPeer(wallet, endpoint).use { peer ->
                                val query = JsonObject().apply {
                                    addProperty("request", "registry query")
                                    add("data", JsonObject().apply {
                                        addProperty("request", "registry namecheck")
                                        addProperty("identifier", identifier)
                                    })
                                }
                                val response = peer.request(query.toString().toByteArray(Charsets.UTF_8))
                                SaitoRegistry.readNamecheck(response.toString(Charsets.UTF_8), identifier)
                            }
                        }
                        status = when {
                            registered == null -> R.string.username_available
                            registered.publicKey != wallet.publicKey -> R.string.username_taken
                            !registered.active -> R.string.username_inactive
                            else -> R.string.username_owned
                        }
                    } catch (_: TimeoutCancellationException) {
                        currentCoroutineContext().ensureActive()
                        status = R.string.username_unavailable
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { status = R.string.username_unavailable }
                    finally { busy = false }
                }
            }
        }) { Text(stringResource(if (busy) R.string.username_checking else R.string.username_check)) }
        status?.let { Text(stringResource(it)) }
    }
}
