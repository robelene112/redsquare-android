package nl.davidengelen.redsquare.feed

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ContentResolver
import android.net.Uri
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.gson.JsonNull
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
    private var durable = ProfileDraftStore.Saved()
    var changes by mutableStateOf(JsonObject())
    var ready by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var error by mutableStateOf(false)
    var reload by mutableIntStateOf(0)
    var candidate by mutableStateOf<SaitoTransaction?>(null)
    var available by mutableStateOf(false)
    var previouslyAvailable by mutableStateOf(false)
    var superseding by mutableStateOf<SaitoProfile?>(null)
    val superseded: Boolean get() = superseding != null
    var networkError by mutableStateOf(false)
    var imageError by mutableStateOf(false)
    var preparingImage by mutableStateOf(false)

    suspend fun load() {
        try {
            val saved = withContext(Dispatchers.IO) { store.load() }
            durable = saved
            changes = (if (saved.transaction != null) saved.changes else memory.changes ?: saved.changes).deepCopy()
            memory.changes = changes.deepCopy()
            candidate = saved.transaction
            // Durable confirmation is historical until this session verifies exact server availability.
            previouslyAvailable = saved.available
            available = false
            superseding = saved.superseding
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

    private suspend fun save(saved: ProfileDraftStore.Saved): ProfileDraftStore.Saved =
        store.save(saved, durable).also { durable = it }

    private fun retain(saved: ProfileDraftStore.Saved) {
        if (saved.transaction == null || saved.transaction.id != candidate?.id || saved.superseding != null) available = false
        durable = saved
        changes = saved.changes.deepCopy()
        memory.changes = changes.takeIf { it.size() > 0 }?.deepCopy()
        candidate = saved.transaction
        previouslyAvailable = saved.available
        superseding = saved.superseding
    }

    fun editBio(text: String) {
        if (!ready || busy || candidate != null) return
        changes = changes.deepCopy().apply { addProperty("description", text) }
        memory.changes = changes.deepCopy()
    }

    fun image(kind: ProfileImageKind, profile: SaitoProfile?): String? {
        val changed = changes.get(kind.field)
        return if (changed != null) changed.takeUnless { it.isJsonNull }?.asString
        else if (kind == ProfileImageKind.PICTURE) profile?.image else profile?.banner
    }

    fun removeImage(kind: ProfileImageKind) {
        if (!ready || busy || candidate != null) return
        changes = changes.deepCopy().apply { add(kind.field, JsonNull.INSTANCE) }
        memory.changes = changes.deepCopy()
    }

    fun openImagePicker(onLaunch: () -> Unit) {
        if (!ready || busy || candidate != null) return
        scope.launch {
            try {
                if (!persist()) return@launch
                ensureActive()
                onLaunch()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { imageError = true }
        }
    }

    fun addImage(resolver: ContentResolver, uri: Uri, kind: ProfileImageKind, known: SaitoProfile?) {
        if (!ready || busy || candidate != null) return
        busy = true
        preparingImage = true
        imageError = false
        scope.launch {
            try {
                val prior = changes.deepCopy()
                withContext(Dispatchers.IO) {
                    // Preserve all prior work before provider reads or pixel allocation.
                    withContext(NonCancellable) { save(ProfileDraftStore.Saved(prior)) }
                    val cached = cache.load()
                    check(!cached.unreadable)
                    val baseline = known?.let { SaitoProfiles.merge(cached.profile, it) } ?: cached.profile
                    // An empty placeholder includes the exact JSON field overhead and replaces old media.
                    val placeholder = prior.deepCopy().apply { addProperty(kind.field, "") }
                    val remaining = SaitoTransaction.MAX_MESSAGE_BYTES -
                        SaitoProfiles.message(baseline, placeholder).toString().toByteArray(Charsets.UTF_8).size
                    val source = PostImagePreparation.normalize(resolver, uri, minOf(512 * 1024, remaining), kind.ratio)
                    val prepared = prior.deepCopy().apply { addProperty(kind.field, source) }
                    require(SaitoProfiles.message(baseline, prepared).toString().toByteArray(Charsets.UTF_8).size <=
                        SaitoTransaction.MAX_MESSAGE_BYTES)
                    withContext(NonCancellable) {
                        save(ProfileDraftStore.Saved(prepared))
                        withContext(Dispatchers.Main.immediate) {
                            changes = prepared; memory.changes = prepared.deepCopy(); error = false
                        }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { imageError = true }
            finally { busy = false; preparingImage = false }
        }
    }

    suspend fun persist(): Boolean {
        if (!ready || busy) return false
        busy = true
        return try {
            val saved = withContext(NonCancellable + Dispatchers.IO) {
                if (available && !networkError && !superseded) store.clearAvailable(requireNotNull(candidate).id, durable)
                else save(ProfileDraftStore.Saved(changes, candidate, previouslyAvailable, superseding))
            }
            retain(saved)
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
                    withContext(NonCancellable) {
                        val saved = save(ProfileDraftStore.Saved(changes, candidate, previouslyAvailable, superseding))
                        withContext(Dispatchers.Main.immediate) { retain(saved) }
                    }
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
                            // A late persistence error must not cause a retry to sign new bytes.
                            withContext(Dispatchers.Main.immediate) { candidate = prepared }
                            withContext(NonCancellable) { save(ProfileDraftStore.Saved(changes, prepared)) }
                            prepared
                        }
                        PostDeliveryAttempts.run(transaction.id) {
                            if (observe(peer, transaction)) return@run
                            // A previously confirmed candidate is checked without publishing it again.
                            if (previouslyAvailable) return@run
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
        val retained = superseding?.let { SaitoProfiles.merge(cached.profile, it) } ?: cached.profile
        val latest = SaitoProfiles.merge(retained, SaitoProfiles.merge(remote, exact))
        val newer = latest.updates.any { it.snapshot && it.id != transaction.id &&
            it.createdAt >= SaitoEnvelope.decode(transaction.envelope()).createdAt }
        if (newer) {
            withContext(Dispatchers.Main.immediate) {
                superseding = latest; available = false; previouslyAvailable = false
            }
            // Navigation must retain this verified guard even if public-cache saving fails.
            withContext(NonCancellable) {
                val saved = save(ProfileDraftStore.Saved(changes, transaction, superseding = latest))
                withContext(Dispatchers.Main.immediate) { retain(saved) }
                cache.save(requireNotNull(saved.superseding))
            }
            return true // Never rebroadcast an older update over a newer verified snapshot.
        }
        if (exact.updates.none { it.id == transaction.id }) {
            withContext(Dispatchers.Main.immediate) { available = false }
            return false
        }
        val observed = SaitoEnvelope.decode(exact.updates.single().envelope)
        val expected = SaitoEnvelope.decode(transaction.envelope())
        check(observed.body == expected.body && observed.createdAt == expected.createdAt &&
            observed.recipients == expected.recipients)
        withContext(NonCancellable) {
            cache.save(latest)
            val saved = checkNotNull(store.confirmAvailable(transaction.id))
            withContext(Dispatchers.Main.immediate) { retain(saved) }
        }
        withContext(Dispatchers.Main.immediate) { available = true; previouslyAvailable = true; networkError = false }
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
    val session = "$owner:${backend.id}:$endpoint"
    var picking by rememberSaveable(session) { mutableStateOf<String?>(null) }
    var selected by remember(session) { mutableStateOf<Pair<ProfileImageKind, Uri>?>(null) }
    val chooseImage: (ProfileImageKind) -> Unit = key(session) {
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            val kind = ProfileImageKind.entries.firstOrNull { "$session:${it.name}" == picking }
            picking = null
            if (kind != null && uri != null) selected = kind to uri
        }
        val choose: (ProfileImageKind) -> Unit = { kind ->
            if (picking == null) state.openImagePicker {
                picking = "$session:${kind.name}"
                try { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                catch (failure: Exception) { picking = null; throw failure }
            }
        }
        choose
    }
    val navigation = requireNotNull(LocalPostNavigation.current)
    LaunchedEffect(state, state.reload) { state.load() }
    LaunchedEffect(state.ready, selected) {
        if (state.ready) selected?.let { (kind, uri) ->
            state.addImage(activity.contentResolver, uri, kind, profile)
            selected = null
        }
    }
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
                Text(stringResource(when {
                    candidate == null -> R.string.profile_save
                    previouslyAvailable -> R.string.profile_check
                    else -> R.string.profile_retry
                }))
            }
            if (candidate != null) Text(stringResource(when {
                superseded -> R.string.profile_superseded
                available -> R.string.profile_available
                previouslyAvailable -> R.string.profile_previous_available
                else -> R.string.profile_pending
            }), Modifier.zIndex(1f))
            if (networkError) Text(stringResource(R.string.profile_publish_error))
            if (error) {
                Text(stringResource(R.string.profile_draft_error))
                if (!ready) TextButton(onClick = { reload++ }) { Text(stringResource(R.string.retry)) }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (imageError) Text(stringResource(R.string.image_prepare_failed))
                if (preparingImage) Text(stringResource(R.string.image_preparing))
                OutlinedTextField(value = changes.get("description")?.asString ?: profile?.description.orEmpty(),
                    onValueChange = ::editBio, enabled = ready && !busy && candidate == null,
                    label = { Text(stringResource(R.string.profile_bio)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 280.dp))
                for (kind in ProfileImageKind.entries) {
                    TextButton(enabled = ready && !busy && candidate == null && picking == null,
                        onClick = { chooseImage(kind) }) { Text(stringResource(kind.changeLabel)) }
                    val source = image(kind, profile)
                    if (source != null) TextButton(enabled = ready && !busy && candidate == null && picking == null,
                        onClick = { removeImage(kind) }) { Text(stringResource(kind.removeLabel)) }
                    if (kind == ProfileImageKind.PICTURE) ProfileImage(source, kind, owner, backend, Modifier.size(96.dp))
                    else if (source != null) ProfileImage(source, kind, owner, backend, Modifier.fillMaxWidth().aspectRatio(3f))
                }
            }
        }
    }
}
