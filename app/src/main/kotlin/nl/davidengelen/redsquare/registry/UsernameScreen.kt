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
import nl.davidengelen.redsquare.policy.LocalPublishingAgreement

/** Optional Registry UI. Opening it only loads and reconciles existing work. */
@Composable
internal fun UsernameScreen(wallet: SaitoWallet, backend: SaitoBackend, endpoint: String, registrar: String,
    modifier: Modifier, onClose: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val publishing = LocalPublishingAgreement.current
    val state = remember(wallet, backend, endpoint, registrar) {
        UsernameState(wallet, endpoint, backend, registrar, UsernameRequestStore(context, wallet.publicKey, backend, registrar),
            RegisteredNameStore(context, wallet.publicKey, registrar), scope)
    }
    val navigation = requireNotNull(LocalPostNavigation.current)
    DisposableEffect(state) {
        val save: suspend () -> Boolean = { state.persist() }
        navigation.saveCurrent = save
        onDispose { if (navigation.saveCurrent === save) navigation.saveCurrent = null }
    }
    LaunchedEffect(state) { state.load() }
    var choosingAnother by remember(state) { mutableStateOf(false) }
    if (choosingAnother) AlertDialog(onDismissRequest = { choosingAnother = false },
        title = { Text(stringResource(R.string.username_new_title)) },
        text = { Text(stringResource(R.string.username_new_notice)) },
        confirmButton = { TextButton(enabled = state.canChooseAnother, onClick = {
            choosingAnother = false
            state.chooseAnother()
        }) { Text(stringResource(R.string.username_new_confirm)) } },
        dismissButton = { TextButton(onClick = { choosingAnother = false }) { Text(stringResource(R.string.username_new_cancel)) } })
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
            if (currentName?.publicKey == wallet.publicKey && currentName?.active == true) {
                Text(currentName!!.identifier)
            } else if (savedName != null) Text(stringResource(R.string.username_cached, savedName!!.name.identifier))
            else verifiedOwnName?.let { Text(stringResource(R.string.username_previous, it.identifier)) }
            if (cacheError) Text(stringResource(R.string.username_cache_error))
            OutlinedTextField(value = name, enabled = ready && !busy && candidate == null && verifiedOwnName == null, singleLine = true,
                onValueChange = ::edit, label = { Text(stringResource(R.string.username_label)) },
                supportingText = { Text(stringResource(R.string.username_rules)) }, modifier = Modifier.fillMaxWidth())
            if (candidate == null) {
                Button(enabled = ready && !busy, onClick = ::check) {
                    Text(stringResource(if (busy) R.string.username_checking else R.string.username_check))
                }
                if (available && verifiedOwnName == null) Button(enabled = ready && !busy,
                    onClick = { register(publishing::beforePublishing) }) {
                    Text(stringResource(R.string.username_register))
                }
            } else if (verifiedOwnName == null) {
                Button(enabled = ready && !busy && currentName == null,
                    onClick = { register(publishing::beforePublishing) }) { Text(stringResource(R.string.username_retry)) }
            } else {
                Button(enabled = ready && !busy, onClick = ::check) { Text(stringResource(R.string.username_check)) }
            }
            if (canChooseAnother) Button(onClick = { choosingAnother = true }) { Text(stringResource(R.string.username_new)) }
            status?.let { Text(stringResource(it)) }
        }
    }
}
