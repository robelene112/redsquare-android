package nl.davidengelen.redsquare.feed

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.SaitoBackend

@Composable
internal fun PostComposerScreen(owner: String, backend: SaitoBackend, modifier: Modifier, onClose: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val store = remember(owner, backend) { PostDraftStore(context, owner, backend) }
    val scope = rememberCoroutineScope()
    var text by remember(store) { mutableStateOf("") }
    var ready by remember(store) { mutableStateOf(false) }
    var saving by remember(store) { mutableStateOf(false) }
    var error by remember(store) { mutableStateOf(false) }
    var reload by remember(store) { mutableIntStateOf(0) }
    LaunchedEffect(store, reload) {
        try {
            text = withContext(Dispatchers.IO) { store.load() }
            ready = true
            error = false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
    }
    fun saveAndClose() {
        if (!ready || saving) return
        saving = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { store.save(text) }
                onClose()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { saving = false }
        }
    }
    BackHandler { if (ready) saveAndClose() else onClose() }
    Column(modifier.imePadding()) {
        Row {
            TextButton(enabled = !saving, onClick = { if (ready) saveAndClose() else onClose() }) {
                Text(stringResource(R.string.browse_back))
            }
            Text(stringResource(R.string.post_new), Modifier.weight(1f).padding(vertical = 12.dp),
                style = MaterialTheme.typography.titleLarge)
            TextButton(enabled = ready && !saving, onClick = ::saveAndClose) {
                Text(stringResource(R.string.post_save_draft))
            }
        }
        Text(stringResource(R.string.post_draft_local), style = MaterialTheme.typography.bodySmall)
        if (error) {
            Text(stringResource(R.string.post_draft_error))
            if (!ready) TextButton(onClick = { reload++ }) { Text(stringResource(R.string.retry)) }
        }
        OutlinedTextField(value = text, onValueChange = { text = it }, enabled = ready && !saving,
            placeholder = { Text(stringResource(R.string.post_write)) }, modifier = Modifier.fillMaxWidth().weight(1f))
    }
}
