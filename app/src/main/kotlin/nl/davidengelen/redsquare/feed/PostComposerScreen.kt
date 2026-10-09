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
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoPeer
import com.google.gson.JsonObject

@Composable
internal fun PostComposerScreen(wallet: SaitoWallet, endpoint: String, backend: SaitoBackend, modifier: Modifier, onClose: () -> Unit) {
    val owner = wallet.publicKey
    val context = LocalContext.current.applicationContext
    val store = remember(owner, backend) { PostDraftStore(context, owner, backend) }
    val scope = rememberCoroutineScope()
    var text by remember(store) { mutableStateOf("") }
    var ready by remember(store) { mutableStateOf(false) }
    var saving by remember(store) { mutableStateOf(false) }
    var error by remember(store) { mutableStateOf(false) }
    var transaction by remember(store) { mutableStateOf<SaitoTransaction?>(null) }
    var submitted by remember(store) { mutableStateOf(false) }
    var sending by remember(store) { mutableStateOf(false) }
    var reload by remember(store) { mutableIntStateOf(0) }
    LaunchedEffect(store, reload) {
        try {
            val saved = withContext(Dispatchers.IO) { store.load() }
            text = saved.text
            transaction = saved.transaction
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
                withContext(Dispatchers.IO) { store.save(PostDraftStore.Saved(text, transaction)) }
                onClose()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { saving = false }
        }
    }
    fun send() {
        if (!ready || saving || sending || text.isBlank()) return
        sending = true
        scope.launch {
            try {
                val candidate = transaction ?: withContext(Dispatchers.IO) {
                    val body = JsonObject().apply {
                        addProperty("module", "RedSquare"); addProperty("request", "create tweet")
                        add("data", JsonObject().apply { addProperty("text", text) })
                    }
                    SaitoTransaction.create(wallet, body, System.currentTimeMillis()).also {
                        store.save(PostDraftStore.Saved(text, it))
                    }
                }.also { transaction = it }
                withContext(Dispatchers.IO) { SaitoPeer(wallet, endpoint).use { it.submit(candidate) } }
                submitted = true
                error = false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { sending = false }
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
            TextButton(enabled = ready && !saving && !sending && text.isNotBlank() && !submitted, onClick = ::send) {
                Text(stringResource(R.string.post_send))
            }
        }
        Text(stringResource(R.string.post_draft_local), style = MaterialTheme.typography.bodySmall)
        if (submitted) Text(stringResource(R.string.post_submitted))
        if (error) {
            Text(stringResource(R.string.post_draft_error))
            if (!ready) TextButton(onClick = { reload++ }) { Text(stringResource(R.string.retry)) }
        }
        OutlinedTextField(value = text, onValueChange = { text = it }, enabled = ready && !saving && !sending && transaction == null,
            placeholder = { Text(stringResource(R.string.post_write)) }, modifier = Modifier.fillMaxWidth().weight(1f))
    }
}
