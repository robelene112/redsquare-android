package nl.davidengelen.redsquare.feed

import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone

import nl.davidengelen.redsquare.registry.registeredIdentity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import nl.davidengelen.redsquare.policy.LocalPublishingAgreement
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import nl.davidengelen.redsquare.MainActivity
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.FeedPost
import nl.davidengelen.redsquare.saito.RedSquareImages
import nl.davidengelen.redsquare.media.GiphyPicker
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.ui.FrameIconButton
import nl.davidengelen.redsquare.ui.keyboardOccupiesWindow

@Composable
internal fun compactPostWindow(): Boolean = LocalConfiguration.current.screenHeightDp < 400

/** Android can resize the activity before publishing the keyboard's visibility. */
internal fun compactTypingViewport(height: Dp, keyboardVisible: Boolean): Boolean = keyboardVisible || height < 160.dp

internal val LocalPostComposerActive = staticCompositionLocalOf<MutableState<Boolean>?> { null }

@Composable
internal fun PostComposerScreen(wallet: SaitoWallet, endpoint: String, backend: SaitoBackend, modifier: Modifier,
    parent: FeedPost? = null, quote: FeedPost? = null, recovery: PostDraftStore? = null,
    onSubmitted: () -> Unit = LocalPostSubmissionNotice.current, onClose: () -> Unit) {
    require(parent == null || quote == null)
    val owner = wallet.publicKey
    val activity = requireNotNull(LocalActivity.current) as MainActivity
    val context = activity.applicationContext
    val memory = remember(activity, owner, backend, parent?.id, quote?.id, recovery?.recoveryId) {
        ViewModelProvider(activity)["post:$owner:${backend.id}:${parent?.id.orEmpty()}:quote:${quote?.id.orEmpty()}:recovery:${recovery?.recoveryId.orEmpty()}", PostComposerMemory::class.java]
    }
    val store = remember(owner, backend, parent?.id, quote?.id, recovery) { recovery ?: PostDraftStore(context, owner, backend, parent, quote) }
    val navigation = requireNotNull(LocalPostNavigation.current)
    val session = "$owner:${backend.id}:${parent?.id.orEmpty()}:quote:${quote?.id.orEmpty()}:$endpoint:recovery:${recovery?.recoveryId.orEmpty()}"
    val editorView = remember(session) { BringIntoViewRequester() }
    var editorPlaced by remember(session) { mutableStateOf(false) }
    val state = key(session) {
        val scope = rememberCoroutineScope()
        remember(store, memory) { PostComposerState(store, memory, wallet, endpoint, parent, scope, quote) }
    }
    val publishing = LocalPublishingAgreement.current
    val composerActive = LocalPostComposerActive.current
    DisposableEffect(composerActive) {
        composerActive?.value = true
        onDispose { composerActive?.value = false }
    }
    val compactHeight = compactPostWindow()
    val keyboardVisible = keyboardOccupiesWindow()
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
    val selection = memory.imagePreparation.pending
    val picker = activity.postImagePicker
    val pickingFor by picker.pickingFor.collectAsState()
    val returned = picker.selection
    val choosingImages = pickingFor != null || returned?.session == session
    val launchImagePicker: () -> Unit = {
        if (picker.pickingFor.value == null) state.openImagePicker {
            activity.launchPostImagePicker(session, RedSquareImages.MAX_COUNT - state.images.size)
        }
    }
    LaunchedEffect(session, returned) {
        if (returned?.session == session && picker.consume(returned)) memory.imagePreparation.select(returned.uris)
    }
    LaunchedEffect(state, selection) {
        if (selection != null && !memory.imagePreparation.failed) {
            // A picker result can arrive while an earlier draft write is busy.
            // Keep this waiter alive until admission, without keying on the busy
            // flags that preparation changes during its own atomic commit.
            snapshotFlow { state.ready && !state.saving && !state.sending }.first { it }
            if (memory.imagePreparation.pending === selection && !memory.imagePreparation.failed)
                state.addImages(context.contentResolver, selection)
        }
    }
    LaunchedEffect(state, memory.imagePreparation.completed) {
        if (memory.imagePreparation.completed > 0) state.load()
    }
    // Exercise real loading-state recovery without changing ordinary app timing.
    val fixtureLoadDelay = if (nl.davidengelen.redsquare.BuildConfig.DEBUG &&
        android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        activity.intent.getLongExtra("postDraftLoadFixtureDelayMs", 0).coerceIn(0, 5000) else 0
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
                state.observeSavedWork()
            }
        }
    }
    DisposableEffect(state) {
        val save: suspend () -> Boolean = { state.persist() }
        val loading: () -> Boolean = { !state.ready && !state.error }
        val guarded: suspend (suspend () -> Boolean) -> Boolean = { commit ->
            if (navigation.saveCurrent !== save || !state.ready || state.saving || state.sending ||
                state.preparingImages || picker.pickingFor.value == session || picker.selection?.session == session) false
            else {
                state.saving = true
                try {
                    if (state.persist() && navigation.saveCurrent === save) commit() else false
                } finally { state.saving = false }
            }
        }
        navigation.saveCurrent = save
        navigation.guardedCurrent = guarded
        navigation.loadingCurrent = loading
        onDispose {
            if (navigation.saveCurrent === save) navigation.saveCurrent = null
            if (navigation.guardedCurrent === guarded) navigation.guardedCurrent = null
            if (navigation.loadingCurrent === loading) navigation.loadingCurrent = null
        }
    }
    with(state) {
        val closeButton: @Composable () -> Unit = {
            FrameIconButton(R.drawable.ic_back, stringResource(R.string.browse_back), !saving && !sending && !preparingImages) {
                if (ready) saveAndClose(closeEditor) else closeEditor()
            }
        }
        val submitEnabled = ready && !saving && !sending && !preparingImages && (available || text.isNotBlank() || images.isNotEmpty())
        val submitLabel = stringResource(when {
            available -> R.string.done
            transaction == null -> R.string.post_send
            networkError || error -> R.string.retry
            else -> R.string.post_check_status
        })
        val submitPost: () -> Unit = {
            if (available) saveAndClose(closeEditor)
            else if (transaction == null || networkError || error) send(publishing::beforePublishing)
            else checkStatus()
        }
        BackHandler { if (!sending && !saving) { if (ready) saveAndClose(closeEditor) else closeEditor() } }
        BoxWithConstraints(modifier.imePadding()) {
            val compactTyping = compactHeight && compactTypingViewport(maxHeight, keyboardVisible)
            LaunchedEffect(state, state.ready, editorPlaced, compactTyping) {
                if ((parent != null || compactTyping) && state.ready && editorPlaced && state.transaction == null) {
                    editorView.bringIntoView()
                }
            }
            // Give the focused editor the remaining height when a call and
            // the keyboard leave too little room for the secondary toolbar.
            val showActions = !compactTyping || maxHeight >= 120.dp
            Column(Modifier.fillMaxSize()) {
                if (!compactTyping) Row(Modifier.fillMaxWidth().padding(bottom = AppSpacing.small),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    closeButton()
                    Text(stringResource(if (recovery != null) R.string.post_saved_title else if (quote != null) R.string.post_quote else if (parent == null) R.string.post_new else R.string.post_reply),
                        Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                }
                val localStatus = if (transaction != null || !ready || error || saving || sending) null else when {
                    recoveryError -> R.string.post_unsaved_changes
                    text.isEmpty() && images.isEmpty() -> R.string.status_unsent_draft
                    locallySaved -> R.string.post_saved_device
                    else -> R.string.post_unsaved_changes
                }
                val draftNotices: @Composable () -> Unit = {
                    InlineStatus(localStatus?.let { stringResource(it) }, StatusTone.INFO,
                        details = listOf(stringResource(if (locallySaved && !recoveryError) R.string.post_draft_local else R.string.post_unsaved_detail)),
                        announce = false)
                    InlineStatus(if (localStatus != null && recoveryError) stringResource(R.string.unfinished_text_recovery_failed) else null,
                        StatusTone.WARNING, actionLabel = stringResource(R.string.retry), onAction = ::retryRecovery)
                }
                if (!compactHeight) draftNotices()
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
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                            InlineStatus(if (networkError) stringResource(R.string.post_network_error) else null, StatusTone.ERROR)
                            InlineStatus(if (checkError) stringResource(R.string.post_check_failed) else null, StatusTone.INFO)
                            InlineStatus(if (validationError) stringResource(R.string.post_invalid_text) else null, StatusTone.ERROR)
                            InlineStatus(if (error) stringResource(R.string.post_draft_error) else null, StatusTone.ERROR)
                            if (error && !ready) TextButton(onClick = { reload++ }) { Text(stringResource(R.string.retry)) }
                            InlineStatus(if (imageError) stringResource(R.string.image_prepare_failed) else null, StatusTone.ERROR)
                            if (!compactTyping) Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
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
                                compact = compactHeight,
                                modifier = Modifier.bringIntoViewRequester(editorView).onGloballyPositioned {
                                    editorPlaced = it.size.height > 0
                                })
                            PostImages(images, backend, onRemove = if (transaction == null) ::removeImage else null, removalEnabled = !saving && !sending && !preparingImages,
                                previewHeight = 120.dp, gifAttributions = gifAttributions, onOpenSource = ::openMediaSource)
                            if (compactHeight) draftNotices()
                            quote?.let { QuotedPost(it, backend, onOpenSource = ::openMediaSource) }
                            if (transaction != null && !available)
                                nl.davidengelen.redsquare.policy.QueuedPublishingAgreement(!saving && !sending)
                            if (queued && queueSaved) TextButton(enabled = !saving && !sending, onClick = ::cancelQueued) {
                                Text(stringResource(R.string.post_cancel_queued))
                            }
                            if (transaction != null && !available && !queued && recovery == null) {
                                TextButton(enabled = !saving && !sending,
                                    onClick = { startAnotherDraft {
                                        observedTransaction = null
                                        try { withContext(Dispatchers.IO) { PostQueueScheduling.enqueue(context, owner, backend, endpoint) } }
                                        catch (cancelled: CancellationException) { throw cancelled }
                                        catch (_: Exception) { android.util.Log.w("PostRecovery", "Availability check scheduling deferred") }
                                    } }) {
                                    Text(stringResource(R.string.post_start_another))
                                }
                                Text(stringResource(R.string.post_retained_detail), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(enabled = !saving && !sending, onClick = { confirmClear = true }) {
                                    Text(stringResource(R.string.post_remove_record))
                                }
                            }
                            if (transaction != null && !available && !queued && !submitted && !networkError && !error)
                                TextButton(enabled = !saving && !sending, onClick = { send(publishing::beforePublishing) }) {
                                    Text(stringResource(R.string.post_send_saved))
                                }
                        }
                        if (!showActions) FilledIconButton(onClick = submitPost, enabled = submitEnabled,
                            modifier = Modifier.padding(start = AppSpacing.small).size(48.dp)) {
                            Icon(painterResource(R.drawable.ic_send), submitLabel, Modifier.size(22.dp))
                        }
                    }
                }
                val submit: @Composable (Modifier) -> Unit = { placement ->
                    Button(modifier = placement.heightIn(min = 48.dp),
                        shape = RoundedCornerShape(AppSpacing.controlRadius),
                        enabled = submitEnabled, onClick = submitPost) {
                        Text(submitLabel, Modifier.padding(vertical = 6.dp))
                    }
                }
                if (showActions) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (showActions) Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (compactTyping) closeButton()
                    FrameIconButton(R.drawable.ic_image, stringResource(R.string.image_add),
                        ready && !saving && !sending && !preparingImages && transaction == null && images.size < RedSquareImages.MAX_COUNT && !choosingImages,
                        launchImagePicker)
                    FrameIconButton(R.drawable.ic_gif, stringResource(R.string.giphy_add),
                        ready && !saving && !sending && !preparingImages && transaction == null && images.size < RedSquareImages.MAX_COUNT && !choosingImages) {
                        openImagePicker { choosingGif = true }
                    }
                    Spacer(Modifier.weight(1f))
                    FrameIconButton(R.drawable.ic_save, stringResource(R.string.post_save_draft), ready && !saving && !sending && !preparingImages) {
                        saveAndClose(closeEditor)
                    }
                    if (compactHeight) submit(Modifier.padding(start = AppSpacing.small))
                }
                if (!compactHeight) submit(Modifier.fillMaxWidth())
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
