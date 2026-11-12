package nl.davidengelen.redsquare.feed

import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone

import nl.davidengelen.redsquare.registry.registeredIdentity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Alignment
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
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.ui.FrameIconButton

@Composable
internal fun PostComposerScreen(wallet: SaitoWallet, endpoint: String, backend: SaitoBackend, modifier: Modifier,
    parent: FeedPost? = null, quote: FeedPost? = null,
    onSubmitted: () -> Unit = LocalPostSubmissionNotice.current, onClose: () -> Unit) {
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
    val editorView = remember(session) { BringIntoViewRequester() }
    var editorPlaced by remember(session) { mutableStateOf(false) }
    val state = key(session) {
        val scope = rememberCoroutineScope()
        remember(store, memory) { PostComposerState(store, memory, wallet, endpoint, parent, scope, quote) }
    }
    val publishing = LocalPublishingAgreement.current
    LaunchedEffect(state, state.ready, editorPlaced) {
        if (parent != null && state.ready && editorPlaced && state.transaction == null) {
            // A long parent at large text sizes must not hide the writing field.
            // Reveal it without taking focus or opening the keyboard.
            editorView.bringIntoView()
        }
    }
    var observedTransaction by rememberSaveable(session) { mutableStateOf<String?>(null) }
    val closeEditor = { observedTransaction = null; onClose() }
    LaunchedEffect(state.transaction?.id) {
        state.transaction?.id?.let { observedTransaction = it }
    }
    LaunchedEffect(state.ready, observedTransaction) {
        val id = observedTransaction
        if (state.ready && id != null && !state.submitted &&
            withContext(Dispatchers.IO) { store.hasSubmission(id) }) state.submitted = true
    }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(state.submitted, state.sending, state.saving) {
        if (state.submitted && !state.sending && !state.saving) {
            try { withContext(Dispatchers.IO) { PostQueueScheduling.enqueue(context, owner, backend, endpoint) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                // The durable receipt is also reconciled by foreground/startup delivery.
                android.util.Log.w("PostSubmission", "Availability check scheduling deferred")
            }
            focus.clearFocus(force = true)
            keyboard?.hide()
            onSubmitted()
            closeEditor()
        }
    }
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
    // Instrumentation can hold the actual draft-loading phase on an emulator.
    // The ordinary app always uses its real storage timing.
    val fixtureLoadDelay = if (nl.davidengelen.redsquare.BuildConfig.DEBUG &&
        android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        activity.intent.getLongExtra("postDraftLoadFixtureDelayMs", 0).coerceIn(0, 1000) else 0
    LaunchedEffect(state, state.reload) {
        if (fixtureLoadDelay > 0) kotlinx.coroutines.delay(fixtureLoadDelay)
        state.load()
    }
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
            if (nl.davidengelen.redsquare.BuildConfig.DEBUG) android.util.Log.i("SocialRouteProbe",
                "[DEBUG-fs103-social] guard current=${navigation.saveCurrent === save} ready=${state.ready} saving=${state.saving} sending=${state.sending} images=${state.preparingImages || pickingFor != null || selectedImages.isNotEmpty()}")
            if (navigation.saveCurrent !== save || !state.ready || state.saving || state.sending ||
                state.preparingImages || pickingFor != null || selectedImages.isNotEmpty()) false
            else {
                state.saving = true
                try {
                    val retained = state.persist()
                    if (nl.davidengelen.redsquare.BuildConfig.DEBUG) android.util.Log.i("SocialRouteProbe",
                        "[DEBUG-fs103-social] persisted=$retained current=${navigation.saveCurrent === save}")
                    if (retained && navigation.saveCurrent === save) commit() else false
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
        BackHandler { if (!sending && !saving) { if (ready) saveAndClose(closeEditor) else closeEditor() } }
        Column(modifier.imePadding()) {
            Row(Modifier.fillMaxWidth().padding(bottom = AppSpacing.small),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FrameIconButton(R.drawable.ic_back, stringResource(R.string.browse_back), !saving && !sending) {
                    if (ready) saveAndClose(closeEditor) else closeEditor()
                }
                Text(stringResource(if (quote != null) R.string.post_quote else if (parent == null) R.string.post_new else R.string.post_reply),
                    Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)

            }
            val localStatus = if (transaction != null || !ready || error || saving || sending) null else when {
                text.isEmpty() && images.isEmpty() -> R.string.status_unsent_draft
                locallySaved -> R.string.post_saved_device
                else -> R.string.post_unsaved_changes
            }
            InlineStatus(localStatus?.let { stringResource(it) },
                details = listOf(stringResource(if (locallySaved) R.string.post_draft_local else R.string.post_unsaved_detail)),
                announce = false)
            val deliveryStatus = when {
                checking -> R.string.post_checking
                preparingImages -> R.string.image_preparing
                saving -> R.string.publishing_saving
                available -> R.string.post_available
                submitted -> R.string.post_submitted
                queued && queueSaved -> R.string.post_queued
                transaction != null -> R.string.post_pending
                sending -> R.string.publishing_preparing
                !ready && !error -> R.string.publishing_loading
                else -> null
            }
            InlineStatus(deliveryStatus?.let { stringResource(it) }, when {
                saving || sending || !ready -> StatusTone.WORKING
                available -> StatusTone.SUCCESS
                else -> StatusTone.INFO
            }, details = buildList {
                if (saving || sending) add(stringResource(R.string.publishing_busy_detail))
                if (transaction != null) {
                    add(stringResource(R.string.status_origin_node, backend.domain))
                    add(stringResource(R.string.publishing_original_attempt))
                }
            })
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val writingHeight = maxHeight
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    InlineStatus(if (networkError) stringResource(R.string.post_network_error) else null, StatusTone.ERROR)
                    InlineStatus(if (checkError) stringResource(R.string.post_check_failed) else null, StatusTone.INFO)
                    InlineStatus(if (validationError) stringResource(R.string.post_invalid_text) else null, StatusTone.ERROR)
                    InlineStatus(if (error) stringResource(R.string.post_draft_error) else null, StatusTone.ERROR)
                    if (error && !ready) TextButton(onClick = { reload++ }) { Text(stringResource(R.string.retry)) }
                    InlineStatus(if (imageError) stringResource(R.string.image_prepare_failed) else null, StatusTone.ERROR)
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProfileAvatar(owner, Modifier.size(36.dp).clip(CircleShape))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.post_writing_as), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(registeredIdentity(owner).name, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    parent?.let { target ->
                        Surface(shape = RoundedCornerShape(AppSpacing.cardRadius), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Column(Modifier.fillMaxWidth().padding(AppSpacing.small), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.post_reply_to, registeredIdentity(target.author).name),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(target.text, style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                        }
                    }
                    quote?.let { Text(stringResource(R.string.post_quoting, registeredIdentity(it.author).name),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    PublishingEditor(text, ::edit, ready && !saving && !sending && transaction == null, maximumHeight = writingHeight,
                        modifier = Modifier.bringIntoViewRequester(editorView).onGloballyPositioned {
                            editorPlaced = it.size.height > 0
                        })
                    PostImages(images, backend, onRemove = if (transaction == null) ::removeImage else null, removalEnabled = !saving && !sending,
                        previewHeight = 120.dp, gifAttributions = gifAttributions, onOpenSource = ::openMediaSource)
                    quote?.let { QuotedPost(it, backend, onOpenSource = ::openMediaSource) }
                    if (transaction != null && !available)
                        nl.davidengelen.redsquare.policy.QueuedPublishingAgreement(!saving && !sending)
                    if (queued && queueSaved) TextButton(enabled = !saving && !sending, onClick = ::cancelQueued) {
                        Text(stringResource(R.string.post_cancel_queued))
                    }
                    if (transaction != null && !available && !queued) TextButton(enabled = !saving && !sending,
                        onClick = { confirmClear = true }) { Text(stringResource(R.string.post_start_another)) }
                    if (transaction != null && !available && !queued && !submitted && !networkError && !error)
                        TextButton(enabled = !saving && !sending, onClick = { send(publishing::beforePublishing) }) {
                            Text(stringResource(R.string.post_send_saved))
                        }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                FrameIconButton(R.drawable.ic_image, stringResource(R.string.image_add),
                    ready && !saving && !sending && transaction == null && images.size < RedSquareImages.MAX_COUNT && pickingFor != session,
                    launchImagePicker)
                FrameIconButton(R.drawable.ic_gif, stringResource(R.string.giphy_add),
                    ready && !saving && !sending && transaction == null && images.size < RedSquareImages.MAX_COUNT && pickingFor == null) {
                    openImagePicker { choosingGif = true }
                }
                Spacer(Modifier.weight(1f))
                FrameIconButton(R.drawable.ic_save, stringResource(R.string.post_save_draft), ready && !saving && !sending) {
                    saveAndClose(closeEditor)
                }
            }
                Button(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(AppSpacing.controlRadius),
                    enabled = ready && !saving && !sending && (available || text.isNotBlank() || images.isNotEmpty()), onClick = {
                    if (available) saveAndClose(closeEditor)
                    else if (transaction == null || networkError || error) send(publishing::beforePublishing)
                    else checkStatus()
                }) {
                    Text(stringResource(when {
                        available -> R.string.done
                        transaction == null -> R.string.post_send
                        networkError || error -> R.string.retry
                        else -> R.string.post_check_status
                    }), Modifier.padding(vertical = 6.dp))
                }
        }
        if (choosingGif) GiphyPicker(onClose = { choosingGif = false },
            onSelect = { item -> addGif(item) { choosingGif = false } },
            selectionEnabled = !saving && !sending, selectionError = imageError)
        if (confirmClear) AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.post_clear_title)) },
            text = { Text(stringResource(R.string.post_clear_detail)) },
            confirmButton = { TextButton(onClick = { clearAndClose(closeEditor) }) { Text(stringResource(R.string.post_clear_confirm)) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.post_clear_keep)) } },
        )
    }
}
