package nl.davidengelen.redsquare.registry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.feed.LocalPostNavigation
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet

/** Optional Registry UI. Opening it only loads and reconciles existing work. */
@Composable
internal fun UsernameScreen(wallet: SaitoWallet, backend: SaitoBackend, endpoint: String,
    modifier: Modifier, onClose: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val state = remember(wallet, backend, endpoint) {
        UsernameState(wallet, endpoint, UsernameRequestStore(context, wallet.publicKey, backend), scope)
    }
    val navigation = requireNotNull(LocalPostNavigation.current)
    DisposableEffect(state) {
        val save: suspend () -> Boolean = { state.persist() }
        navigation.saveCurrent = save
        onDispose { if (navigation.saveCurrent === save) navigation.saveCurrent = null }
    }
    LaunchedEffect(state) { state.load() }
    BackHandler(onBack = onClose)
    with(state) {
        Column(modifier.imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onClose) { Text(stringResource(R.string.browse_back)) }
            Text(stringResource(R.string.username_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.username_optional))
            if (loadError) {
                Text(stringResource(R.string.username_load_error))
                Button(onClick = { scope.launch { load() } }) { Text(stringResource(R.string.retry)) }
            }
            OutlinedTextField(value = name, enabled = ready && !busy && candidate == null, singleLine = true,
                onValueChange = ::edit, label = { Text(stringResource(R.string.username_label)) },
                supportingText = { Text(stringResource(R.string.username_rules)) }, modifier = Modifier.fillMaxWidth())
            if (candidate == null) {
                Button(enabled = ready && !busy, onClick = ::check) {
                    Text(stringResource(if (busy) R.string.username_checking else R.string.username_check))
                }
                if (available) Button(enabled = ready && !busy, onClick = ::register) {
                    Text(stringResource(R.string.username_register))
                }
            } else {
                Button(enabled = ready && !busy, onClick = ::register) { Text(stringResource(R.string.username_retry)) }
            }
            status?.let { Text(stringResource(it)) }
        }
    }
}
