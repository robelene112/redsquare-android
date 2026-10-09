package nl.davidengelen.redsquare.feed

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoPeer
import nl.davidengelen.redsquare.saito.RedSquareFeed
import nl.davidengelen.redsquare.saito.FeedPost
import com.google.gson.JsonObject

@Composable
internal fun PostComposerScreen(wallet: SaitoWallet, endpoint: String, backend: SaitoBackend, modifier: Modifier,
    parent: FeedPost? = null, onClose: () -> Unit) {
    val owner = wallet.publicKey
    val activity = requireNotNull(LocalActivity.current) as ComponentActivity
    val context = activity.applicationContext
    val memory = remember(activity, owner, backend, parent?.id) {
        ViewModelProvider(activity)["post:$owner:${backend.id}:${parent?.id.orEmpty()}", PostComposerMemory::class.java]
    }
    val store = remember(owner, backend, parent?.id) { PostDraftStore(context, owner, backend, parent) }
    val scope = rememberCoroutineScope()
    val writes = remember(store) { Mutex() }
    val navigation = requireNotNull(LocalPostNavigation.current)
    var text by remember(store) { mutableStateOf("") }
    var ready by remember(store) { mutableStateOf(false) }
    var saving by remember(store) { mutableStateOf(false) }
    var error by remember(store) { mutableStateOf(false) }
    var networkError by remember(store) { mutableStateOf(false) }
    var validationError by remember(store) { mutableStateOf(false) }
    var transaction by remember(store) { mutableStateOf<SaitoTransaction?>(null) }
    var submitted by remember(store) { mutableStateOf(false) }
    var available by remember(store) { mutableStateOf(false) }
    var sending by remember(store) { mutableStateOf(false) }
    var reload by remember(store) { mutableIntStateOf(0) }
    var confirmClear by remember(store) { mutableStateOf(false) }
    LaunchedEffect(store, reload) {
        try {
            val saved = withContext(Dispatchers.IO) { store.load() }
            text = if (saved.transaction != null) saved.text else memory.text ?: saved.text
            memory.text = text
            transaction = saved.transaction
            ready = true
            error = false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
    }
    suspend fun persist(): Boolean = writes.withLock {
        if (!ready) return@withLock false
        try {
            val snapshot = if (available) PostDraftStore.Saved("") else PostDraftStore.Saved(text, transaction)
            withContext(NonCancellable) {
                withContext(Dispatchers.IO) { store.save(snapshot) }
                memory.text = snapshot.text
            }
            error = false
            true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true; false }
    }
    DisposableEffect(store) {
        val save: suspend () -> Boolean = { persist() }
        navigation.saveCurrent = save
        onDispose { if (navigation.saveCurrent === save) navigation.saveCurrent = null }
    }
    fun saveAndClose() {
        if (!ready || saving || sending) return
        saving = true
        scope.launch {
            try {
                if (persist()) onClose()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { saving = false }
        }
    }
    fun send() {
        if (!ready || saving || sending || text.isBlank()) return
        val retrying = transaction != null
        networkError = false
        validationError = false
        sending = true
        scope.launch {
            try {
                val candidate = writes.withLock {
                    val prepared = transaction ?: withContext(Dispatchers.IO) {
                    val body = JsonObject().apply {
                        addProperty("module", "RedSquare"); addProperty("request", "create tweet")
                        add("data", JsonObject().apply {
                            addProperty("text", text)
                            parent?.let { addProperty("parent_id", it.id); addProperty("thread_id", it.threadId) }
                        })
                    }
                    val recipients = parent?.let { (listOf(it.author) + it.recipients).distinct() }.orEmpty()
                        SaitoTransaction.create(wallet, body, System.currentTimeMillis(), recipients)
                    }
                    transaction = prepared
                    val snapshot = PostDraftStore.Saved(text, prepared)
                    withContext(NonCancellable + Dispatchers.IO) { store.save(snapshot) }
                    prepared
                }
                try { withContext(Dispatchers.IO) {
                    SaitoPeer(wallet, endpoint).use { peer ->
                        val criteria = JsonObject().apply {
                            addProperty("request", "load"); addProperty("field1", "RedSquare")
                            addProperty("sig", candidate.id); addProperty("flagged_ne", 1)
                        }
                        val query = JsonObject().apply { addProperty("request", "archive"); add("data", criteria) }
                        suspend fun observed(): Boolean {
                            val response = peer.request(query.toString().toByteArray(Charsets.UTF_8)).toString(Charsets.UTF_8)
                            return RedSquareFeed.readTarget(response, candidate.id).posts
                                .any { it.author == owner && it.text == text }
                        }
                        if (retrying && observed()) {
                            withContext(Dispatchers.Main.immediate) { available = true }
                            return@use
                        }
                        peer.submit(candidate)
                        withContext(Dispatchers.Main.immediate) { submitted = true }
                        for (attempt in 0 until 6) {
                            if (observed()) {
                                withContext(Dispatchers.Main.immediate) { available = true }
                                break
                            }
                            if (attempt < 5) delay(2_000)
                        }
                    }
                } } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { networkError = true }
                error = false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: SaitoTransaction.InvalidMessage) { validationError = true }
            catch (_: Exception) { error = true }
            finally { sending = false }
        }
    }
    fun clearAndClose() {
        if (!ready || saving || sending || transaction == null || available) return
        confirmClear = false
        saving = true
        scope.launch {
            try {
                writes.withLock {
                    withContext(NonCancellable) {
                        withContext(Dispatchers.IO) { store.save(PostDraftStore.Saved("")) }
                        memory.text = ""
                        text = ""
                        transaction = null
                    }
                }
                onClose()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { saving = false }
        }
    }
    BackHandler { if (!sending) { if (ready) saveAndClose() else onClose() } }
    Column(modifier.imePadding()) {
        Row {
            TextButton(enabled = !saving && !sending, onClick = { if (ready) saveAndClose() else onClose() }) {
                Text(stringResource(R.string.browse_back))
            }
            Text(stringResource(if (parent == null) R.string.post_new else R.string.post_reply), Modifier.weight(1f).padding(vertical = 12.dp),
                style = MaterialTheme.typography.titleLarge)
            TextButton(enabled = ready && !saving && !sending, onClick = ::saveAndClose) {
                Text(stringResource(R.string.post_save_draft))
            }
            TextButton(enabled = ready && !saving && !sending && text.isNotBlank() && !available, onClick = ::send) {
                Text(stringResource(if (transaction == null) R.string.post_send else R.string.retry))
            }
        }
        if (transaction == null) Text(stringResource(R.string.post_draft_local), style = MaterialTheme.typography.bodySmall)
        parent?.let { Text(stringResource(R.string.post_reply_to, "Anon-${it.author.take(6)}"), style = MaterialTheme.typography.bodySmall) }
        if (available) Text(stringResource(R.string.post_available))
        else if (submitted) Text(stringResource(R.string.post_submitted))
        else if (transaction != null) Text(stringResource(R.string.post_pending))
        if (transaction != null && !available) TextButton(enabled = !saving && !sending,
            onClick = { confirmClear = true }) { Text(stringResource(R.string.post_start_another)) }
        if (networkError) Text(stringResource(R.string.post_network_error))
        if (validationError) Text(stringResource(R.string.post_invalid_text))
        if (error) {
            Text(stringResource(R.string.post_draft_error))
            if (!ready) TextButton(onClick = { reload++ }) { Text(stringResource(R.string.retry)) }
        }
        OutlinedTextField(value = text, onValueChange = { text = it; memory.text = it; validationError = false }, enabled = ready && !saving && !sending && transaction == null,
            placeholder = { Text(stringResource(R.string.post_write)) }, modifier = Modifier.fillMaxWidth().weight(1f))
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text(stringResource(R.string.post_clear_title)) },
        text = { Text(stringResource(R.string.post_clear_detail)) },
        confirmButton = { TextButton(onClick = ::clearAndClose) { Text(stringResource(R.string.post_clear_confirm)) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.post_clear_keep)) } },
    )
}
