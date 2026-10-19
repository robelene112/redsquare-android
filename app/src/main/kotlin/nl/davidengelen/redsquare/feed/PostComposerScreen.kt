package nl.davidengelen.redsquare.feed

import nl.davidengelen.redsquare.registry.registeredIdentity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import nl.davidengelen.redsquare.policy.LocalPublishingAgreement
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.FeedPost
import nl.davidengelen.redsquare.saito.RedSquareImages
import android.net.Uri
import nl.davidengelen.redsquare.media.GiphyPicker

@Composable
internal fun PostComposerScreen(wallet: SaitoWallet, endpoint: String, backend: SaitoBackend, modifier: Modifier,
    parent: FeedPost? = null, quote: FeedPost? = null, onClose: () -> Unit) {
    require(parent == null || quote == null)
    val owner = wallet.publicKey
    val activity = requireNotNull(LocalActivity.current) as ComponentActivity
    val context = activity.applicationContext
    val memory = remember(activity, owner, backend, parent?.id, quote?.id) {
        ViewModelProvider(activity)["post:$owner:${backend.id}:${parent?.id.orEmpty()}:quote:${quote?.id.orEmpty()}", PostComposerMemory::class.java]
    }
    val store = remember(owner, backend, parent?.id, quote?.id) { PostDraftStore(context, owner, backend, parent, quote) }
    val navigation = requireNotNull(LocalPostNavigation.current)
    val session = "$owner:${backend.id}:${parent?.id.orEmpty()}:quote:${quote?.id.orEmpty()}:$endpoint"
    val state = key(session) {
        val scope = rememberCoroutineScope()
        remember(store, memory) { PostComposerState(store, memory, wallet, endpoint, parent, scope, quote) }
    }
    val publishing = LocalPublishingAgreement.current
    var choosingGif by remember(state) { mutableStateOf(false) }
    var selectedImages by remember(state) { mutableStateOf<List<Uri>>(emptyList()) }
    // Only public session metadata enters saved state. Wallet material and draft
    // contents remain in their existing private stores.
    var pickingFor by rememberSaveable(session) { mutableStateOf<String?>(null) }
    val launchImagePicker: () -> Unit = key(session) {
        val singlePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
            if (pickingFor == session) {
                pickingFor = null
                selectedImages = listOfNotNull(it)
            }
        }
        val multiplePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(
            maxOf(2, RedSquareImages.MAX_COUNT - state.images.size))) {
            if (pickingFor == session) {
                pickingFor = null
                selectedImages = it
            }
        }
        val launch: () -> Unit = {
            if (pickingFor != session) state.openImagePicker {
                pickingFor = session
                try {
                    val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    if (state.images.size == RedSquareImages.MAX_COUNT - 1) singlePicker.launch(request)
                    else multiplePicker.launch(request)
                } catch (failure: Exception) {
                    pickingFor = null
                    throw failure
                }
            }
        }
        launch
    }
    LaunchedEffect(state.ready, selectedImages) {
        if (state.ready && selectedImages.isNotEmpty()) {
            state.addImages(context.contentResolver, selectedImages)
            selectedImages = emptyList()
        }
    }
    LaunchedEffect(state, state.reload) { state.load() }
    LaunchedEffect(state, state.queued, state.queueSaved, state.sending) {
        if (state.queued && state.queueSaved && !state.sending) {
            try { withContext(Dispatchers.IO) { PostQueueScheduling.enqueue(context, owner, backend, endpoint) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state.error = true }
        }
    }
    LaunchedEffect(state) {
        PostDraftStore.changes.collect {
            if (state.transaction != null) {
                snapshotFlow { !state.sending && !state.saving }.first { it }
                state.load()
            }
        }
    }
    DisposableEffect(state) {
        val save: suspend () -> Boolean = { state.persist() }
        val guarded: suspend (suspend () -> Boolean) -> Boolean = { commit ->
            if (navigation.saveCurrent !== save || !state.ready || state.saving || state.sending ||
                state.preparingImages || pickingFor != null || selectedImages.isNotEmpty()) false
            else {
                state.saving = true
                try {
                    if (state.persist() && navigation.saveCurrent === save) commit() else false
                } finally { state.saving = false }
            }
        }
        navigation.saveCurrent = save
        navigation.guardedCurrent = guarded
        onDispose {
            if (navigation.saveCurrent === save) navigation.saveCurrent = null
            if (navigation.guardedCurrent === guarded) navigation.guardedCurrent = null
        }
    }
    with(state) {
        BackHandler { if (!sending && !saving) { if (ready) saveAndClose(onClose) else onClose() } }
        Column(modifier.imePadding()) {
            Row {
                TextButton(enabled = !saving && !sending, onClick = { if (ready) saveAndClose(onClose) else onClose() }) {
                    Text(stringResource(R.string.browse_back))
                }
                Text(stringResource(if (quote != null) R.string.post_quote else if (parent == null) R.string.post_new else R.string.post_reply), Modifier.weight(1f).padding(vertical = 12.dp),
                    style = MaterialTheme.typography.titleLarge)
                TextButton(enabled = ready && !saving && !sending, onClick = { saveAndClose(onClose) }) {
                    Text(stringResource(R.string.post_save_draft))
                }
                TextButton(enabled = ready && !saving && !sending && (text.isNotBlank() || images.isNotEmpty()) && !available, onClick = {
                    send(publishing::beforePublishing)
                }) {
                    Text(stringResource(if (transaction == null) R.string.post_send else R.string.retry))
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (transaction == null) Text(stringResource(R.string.post_draft_local), style = MaterialTheme.typography.bodySmall)
                parent?.let { Text(stringResource(R.string.post_reply_to, registeredIdentity(it.author).name), style = MaterialTheme.typography.bodySmall) }
                quote?.let { QuotedPost(it, backend) }
                if (available) Text(stringResource(R.string.post_available))
                else if (queued && queueSaved) Text(stringResource(R.string.post_queued))
                else if (submitted) Text(stringResource(R.string.post_submitted))
                else if (transaction != null) Text(stringResource(R.string.post_pending))
                if (transaction != null && !available)
                    nl.davidengelen.redsquare.policy.QueuedPublishingAgreement(!saving && !sending)
                if (queued && queueSaved) TextButton(enabled = !saving && !sending, onClick = ::cancelQueued) {
                    Text(stringResource(R.string.post_cancel_queued))
                }
                if (transaction != null && !available && !queued) TextButton(enabled = !saving && !sending,
                    onClick = { confirmClear = true }) { Text(stringResource(R.string.post_start_another)) }
                if (networkError) Text(stringResource(R.string.post_network_error))
                if (validationError) Text(stringResource(R.string.post_invalid_text))
                if (error) {
                    Text(stringResource(R.string.post_draft_error))
                    if (!ready) TextButton(onClick = { reload++ }) { Text(stringResource(R.string.retry)) }
                }
                TextButton(enabled = ready && !saving && !sending && transaction == null && images.size < RedSquareImages.MAX_COUNT && pickingFor != session,
                    onClick = launchImagePicker) { Text(stringResource(R.string.image_add)) }
                TextButton(enabled = ready && !saving && !sending && transaction == null && images.size < RedSquareImages.MAX_COUNT && pickingFor == null,
                    onClick = { openImagePicker { choosingGif = true } }) { Text(stringResource(R.string.giphy_add)) }
                if (imageError) Text(stringResource(R.string.image_prepare_failed))
                if (preparingImages) Text(stringResource(R.string.image_preparing))
                OutlinedTextField(value = text, onValueChange = ::edit, enabled = ready && !saving && !sending && transaction == null,
                    placeholder = { Text(stringResource(R.string.post_write)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 280.dp))
                PostImages(images, backend, onRemove = if (transaction == null) ::removeImage else null, removalEnabled = !saving && !sending,
                    gifAttributions = gifAttributions, onOpenSource = ::openMediaSource)
            }
        }
        if (choosingGif) GiphyPicker(onClose = { choosingGif = false },
            onSelect = { item -> addGif(item) { choosingGif = false } },
            selectionEnabled = !saving && !sending, selectionError = imageError)
        if (confirmClear) AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.post_clear_title)) },
            text = { Text(stringResource(R.string.post_clear_detail)) },
            confirmButton = { TextButton(onClick = { clearAndClose(onClose) }) { Text(stringResource(R.string.post_clear_confirm)) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.post_clear_keep)) } },
        )
    }
}
