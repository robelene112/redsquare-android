package nl.davidengelen.redsquare.feed

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.*

internal class ProfileDraftMemory : ViewModel() {
    var changes: JsonObject? = null
}

private class ProfileEditorState(private val store: ProfileDraftStore, private val memory: ProfileDraftMemory,
    private val scope: CoroutineScope) {
    var changes by mutableStateOf(JsonObject())
    var ready by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var error by mutableStateOf(false)
    var reload by mutableIntStateOf(0)

    suspend fun load() {
        try {
            val saved = withContext(Dispatchers.IO) { store.load() }
            changes = (memory.changes ?: saved).deepCopy()
            memory.changes = changes.deepCopy()
            ready = true
            error = false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
    }

    fun editBio(text: String) {
        if (!ready || busy) return
        changes = changes.deepCopy().apply { addProperty("description", text) }
        memory.changes = changes.deepCopy()
    }

    suspend fun persist(): Boolean {
        if (!ready || busy) return false
        busy = true
        return try {
            withContext(NonCancellable + Dispatchers.IO) { store.save(changes) }
            error = false
            true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true; false }
        finally { busy = false }
    }

    fun close(onClose: () -> Unit) {
        if (busy) return
        if (!ready) { onClose(); return }
        scope.launch { if (persist()) onClose() }
    }
}

@Composable
internal fun ProfileEditorScreen(wallet: SaitoWallet, backend: SaitoBackend, profile: SaitoProfile?,
    modifier: Modifier, onClose: () -> Unit) {
    val activity = requireNotNull(LocalActivity.current) as ComponentActivity
    val owner = wallet.publicKey
    val memory = remember(activity, owner, backend) {
        ViewModelProvider(activity)["profile-draft:$owner:${backend.id}", ProfileDraftMemory::class.java]
    }
    val store = remember(owner, backend) { ProfileDraftStore(activity.applicationContext, owner, backend) }
    val scope = rememberCoroutineScope()
    val state = remember(store, memory) { ProfileEditorState(store, memory, scope) }
    val navigation = requireNotNull(LocalPostNavigation.current)
    LaunchedEffect(state, state.reload) { state.load() }
    DisposableEffect(state) {
        val save: suspend () -> Boolean = { state.persist() }
        navigation.saveCurrent = save
        onDispose { if (navigation.saveCurrent === save) navigation.saveCurrent = null }
    }
    BackHandler { state.close(onClose) }
    with(state) {
        Column(modifier.imePadding()) {
            Row {
                TextButton(enabled = !busy, onClick = { close(onClose) }) { Text(stringResource(R.string.browse_back)) }
                Text(stringResource(R.string.profile_edit), Modifier.weight(1f).padding(vertical = 12.dp),
                    style = MaterialTheme.typography.titleLarge)
                TextButton(enabled = ready && !busy, onClick = { close(onClose) }) { Text(stringResource(R.string.post_save_draft)) }
            }
            Text(stringResource(R.string.profile_draft_local), style = MaterialTheme.typography.bodySmall)
            if (error) {
                Text(stringResource(R.string.profile_draft_error))
                if (!ready) TextButton(onClick = { reload++ }) { Text(stringResource(R.string.retry)) }
            }
            OutlinedTextField(value = changes.get("description")?.asString ?: profile?.description.orEmpty(),
                onValueChange = ::editBio, enabled = ready && !busy,
                label = { Text(stringResource(R.string.profile_bio)) }, modifier = Modifier.weight(1f).fillMaxWidth())
        }
    }
}
