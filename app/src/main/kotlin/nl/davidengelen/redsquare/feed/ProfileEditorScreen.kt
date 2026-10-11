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
    private val scope: CoroutineScope, private val wallet: SaitoWallet, private val endpoint: String,
    private val cache: AndroidProfileDetailsCache) {
    var changes by mutableStateOf(JsonObject())
    var ready by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var error by mutableStateOf(false)
    var reload by mutableIntStateOf(0)
    var candidate by mutableStateOf<SaitoTransaction?>(null)
    var available by mutableStateOf(false)
    var superseded by mutableStateOf(false)
    var networkError by mutableStateOf(false)

    suspend fun load() {
        try {
            val saved = withContext(Dispatchers.IO) { store.load() }
            changes = (if (saved.transaction != null) saved.changes else memory.changes ?: saved.changes).deepCopy()
            memory.changes = changes.deepCopy()
            candidate = saved.transaction
            available = saved.available
            ready = true
            error = false
            // Reopening only checks a retained candidate. It never signs or submits.
            saved.transaction?.let { transaction ->
                busy = true
                try {
                    withContext(Dispatchers.IO) { SaitoPeer(wallet, endpoint).use { observe(it, transaction) } }
                } catch (_: TimeoutCancellationException) {
                    currentCoroutineContext().ensureActive(); networkError = true
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { networkError = true }
                finally { busy = false }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
    }

    fun editBio(text: String) {
        if (!ready || busy || candidate != null) return
        changes = changes.deepCopy().apply { addProperty("description", text) }
        memory.changes = changes.deepCopy()
    }

    suspend fun persist(): Boolean {
        if (!ready || busy) return false
        busy = true
        return try {
            val saved = if (available && !networkError && !superseded) ProfileDraftStore.Saved()
                else ProfileDraftStore.Saved(changes, candidate, available)
            withContext(NonCancellable + Dispatchers.IO) { store.save(saved) }
            memory.changes = if (saved.changes.size() == 0) null else saved.changes.deepCopy()
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

    fun send(known: SaitoProfile?) {
        if (!ready || busy || available || superseded || changes.size() == 0) return
        busy = true
        error = false
        networkError = false
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Save local edits even when the required fresh baseline lookup fails.
                    withContext(NonCancellable) { store.save(ProfileDraftStore.Saved(changes, candidate)) }
                    SaitoPeer(wallet, endpoint).use { peer ->
                        val transaction = candidate ?: run {
                            val fresh = peer.profile(wallet.publicKey)
                            check(fresh.ignored == 0)
                            val cached = cache.load()
                            check(!cached.unreadable)
                            val baseline = SaitoProfiles.merge(known?.let { SaitoProfiles.merge(cached.profile, it) }
                                ?: cached.profile, fresh)
                            val timestamp = maxOf(System.currentTimeMillis(), Math.addExact(
                                baseline.updates.maxOfOrNull { it.createdAt } ?: 0, 1))
                            val prepared = SaitoTransaction.create(wallet, SaitoProfiles.message(baseline, changes), timestamp)
                            withContext(NonCancellable) { store.save(ProfileDraftStore.Saved(changes, prepared)) }
                            withContext(Dispatchers.Main.immediate) { candidate = prepared }
                            prepared
                        }
                        PostDeliveryAttempts.run(transaction.id) {
                            if (observe(peer, transaction)) return@run
                            peer.authenticate()
                            if (!withContext(NonCancellable) { store.claim(transaction.id) }) return@run
                            peer.submit(transaction)
                            for (attempt in 0 until 6) {
                                if (observe(peer, transaction)) break
                                if (attempt < 5) delay(2_000)
                            }
                        }
                    }
                }
            } catch (_: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive(); networkError = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { networkError = true }
            finally { busy = false }
        }
    }

    private suspend fun observe(peer: SaitoPeer, transaction: SaitoTransaction): Boolean {
        val exact = peer.profile(wallet.publicKey, transaction.id)
        check(exact.ignored == 0 && exact.updates.size <= 1 && exact.updates.all { it.id == transaction.id })
        val remote = peer.profile(wallet.publicKey)
        check(remote.ignored == 0)
        val cached = cache.load()
        check(!cached.unreadable)
        val latest = SaitoProfiles.merge(cached.profile, SaitoProfiles.merge(remote, exact))
        val newer = latest.updates.any { it.snapshot && it.id != transaction.id &&
            it.createdAt >= SaitoEnvelope.decode(transaction.envelope()).createdAt }
        if (newer) {
            withContext(Dispatchers.Main.immediate) { superseded = true; available = false }
            // Preserve the verified newer snapshot before declaring reconciliation settled.
            // A failed write keeps retry blocked in memory and surfaces a recovery error.
            withContext(NonCancellable) { cache.save(latest) }
            return true // Never rebroadcast an older update over a newer verified snapshot.
        }
        if (exact.updates.none { it.id == transaction.id }) return false
        val observed = SaitoEnvelope.decode(exact.updates.single().envelope)
        val expected = SaitoEnvelope.decode(transaction.envelope())
        check(observed.body == expected.body && observed.createdAt == expected.createdAt &&
            observed.recipients == expected.recipients)
        withContext(NonCancellable) {
            cache.save(latest)
            check(store.confirmAvailable(transaction.id))
        }
        withContext(Dispatchers.Main.immediate) { available = true; networkError = false }
        return true
    }
}

private suspend fun SaitoPeer.profile(author: String, id: String? = null): SaitoProfile {
    val query = JsonObject().apply {
        addProperty("request", "archive")
        add("data", JsonObject().apply {
            addProperty("request", "load"); addProperty("field1", "Profile")
            addProperty("field2", author); addProperty("limit", if (id == null) 100 else 1)
            id?.let { addProperty("sig", it) }
        })
    }
    return SaitoProfiles.readArchive(request(query.toString().toByteArray(Charsets.UTF_8)).toString(Charsets.UTF_8), author)
}

@Composable
internal fun ProfileEditorScreen(wallet: SaitoWallet, backend: SaitoBackend, endpoint: String, profile: SaitoProfile?,
    modifier: Modifier, onClose: () -> Unit) {
    val activity = requireNotNull(LocalActivity.current) as ComponentActivity
    val owner = wallet.publicKey
    val memory = remember(activity, owner, backend) {
        ViewModelProvider(activity)["profile-draft:$owner:${backend.id}", ProfileDraftMemory::class.java]
    }
    val store = remember(owner, backend) { ProfileDraftStore(activity.applicationContext, owner, backend) }
    val cache = remember(owner, backend) { AndroidProfileDetailsCache(activity.applicationContext, backend, owner) }
    val scope = rememberCoroutineScope()
    val state = remember(store, memory, wallet, endpoint, cache) { ProfileEditorState(store, memory, scope, wallet, endpoint, cache) }
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
            Button(enabled = ready && !busy && !available && !superseded && changes.size() > 0,
                onClick = { send(profile) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (candidate == null) R.string.profile_save else R.string.profile_retry))
            }
            if (candidate != null) Text(stringResource(when {
                superseded -> R.string.profile_superseded
                networkError && available -> R.string.profile_previous_available
                available -> R.string.profile_available
                else -> R.string.profile_pending
            }))
            if (networkError) Text(stringResource(R.string.profile_publish_error))
            if (error) {
                Text(stringResource(R.string.profile_draft_error))
                if (!ready) TextButton(onClick = { reload++ }) { Text(stringResource(R.string.retry)) }
            }
            OutlinedTextField(value = changes.get("description")?.asString ?: profile?.description.orEmpty(),
                onValueChange = ::editBio, enabled = ready && !busy && candidate == null,
                label = { Text(stringResource(R.string.profile_bio)) }, modifier = Modifier.weight(1f).fillMaxWidth())
        }
    }
}
