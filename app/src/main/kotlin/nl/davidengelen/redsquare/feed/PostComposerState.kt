package nl.davidengelen.redsquare.feed

import androidx.compose.runtime.*
import android.content.ContentResolver
import android.net.Uri
import nl.davidengelen.redsquare.media.GiphyAttribution
import nl.davidengelen.redsquare.media.GiphyItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.saito.FeedPost
import nl.davidengelen.redsquare.saito.DeliveryAttempts
import nl.davidengelen.redsquare.saito.SaitoPeer
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.RedSquarePosts
import nl.davidengelen.redsquare.saito.RedSquareImages

/** Composer operations live for this visible wallet/backend session. */
internal class PostComposerState(
    private val store: PostDraftStore,
    private val memory: PostComposerMemory,
    private val wallet: SaitoWallet,
    private val endpoint: String,
    private val parent: FeedPost?,
    private val scope: CoroutineScope,
    private val quote: FeedPost? = null,
) {
    private val owner = wallet.publicKey
    private val writes = memory.writes
    // A write can replace the file before confirmation fails. Trust only its successful return.
    private var savedContent by mutableStateOf<PostDraftStore.Saved?>(null)
    var text by mutableStateOf("")
    var images by mutableStateOf<List<String>>(emptyList())
    var gifAttributions by mutableStateOf<Map<String, GiphyAttribution>>(emptyMap())
    private var localImageError by mutableStateOf(false)
    var imageError: Boolean
        get() = localImageError || memory.imagePreparation.failed
        set(value) { localImageError = value }
    val preparingImages: Boolean get() = memory.imagePreparation.preparing
    var ready by mutableStateOf(false)
    var saving by mutableStateOf(false)
    var error by mutableStateOf(false)
    var networkError by mutableStateOf(false)
    private var checkingId by mutableStateOf<String?>(null)
    val checking: Boolean get() = checkingId == transaction?.id && checkingId != null
    var checkError by mutableStateOf(false)
    var validationError by mutableStateOf(false)
    var transaction by mutableStateOf<SaitoTransaction?>(null)
    var queued by mutableStateOf(false)
    var queueSaved by mutableStateOf(false)
    var submitted by mutableStateOf(false)
    var available by mutableStateOf(false)
    var sending by mutableStateOf(false)
    var reload by mutableIntStateOf(0)
    var confirmClear by mutableStateOf(false)
    val recoveryError: Boolean get() = memory.unfinished?.failed == true
    fun retryRecovery() { memory.unfinished?.retry() }
    val locallySaved: Boolean get() = savedContent?.let {
        it.text == text && it.images == images && it.gifAttributions == gifAttributions
    } == true
    /** Initial entry or an explicit retry may resolve a previous loading failure. */
    suspend fun load() {
        val firstEntry = !ready
        writes.withLock { readSavedWork(passive = false) }
        // Local recovery must be usable before an independent Archive deadline.
        if (firstEntry && transaction != null && !queued && !available) checkStatus()
    }

    /** A storage notification is not a successful retry of a failed operation. */
    suspend fun observeSavedWork() = writes.withLock {
        if (!sending && !saving) readSavedWork(passive = true)
    }

    private suspend fun readSavedWork(passive: Boolean) {
        try {
            var saved = withContext(Dispatchers.IO) { store.load() }
            var reconciled = false
            // A durable availability result can heal its receipt locally.
            // Network observation starts only after the editor has been hydrated.
            if (saved.transaction != null && saved.available) {
                try { withContext(NonCancellable + Dispatchers.IO) { store.markSubmitted(requireNotNull(saved.transaction).id) } }
                catch (failure: Exception) { throw DraftPersistenceFailure(failure) }
                reconciled = true
            }
            val handedOff = (transaction?.id ?: memory.transactionId)?.let { id ->
                withContext(Dispatchers.IO) { store.hasSubmission(id) }
            } == true
            if (reconciled || handedOff) saved = withContext(Dispatchers.IO) { store.load() }
            // A user operation can set its busy flag while this read is suspended.
            // Its write waits for this mutex, so discard the observation before it
            // can overwrite the new operation's state or consume its error.
            if (passive && (sending || saving)) return
            if (reconciled || handedOff) {
                if (reconciled || (ready && transaction != null)) submitted = true
                memory.clearSubmitted()
            }
            savedContent = saved
            if (saved.transaction == null && memory.unfinished == null) {
                memory.unfinished = withContext(Dispatchers.IO) {
                    store.unfinishedText().also { memory.addCloseable("unfinished-text", it) }
                }
            }
            text = if (saved.transaction != null) saved.text else memory.text ?: memory.unfinished?.restored ?: saved.text
            images = if (saved.transaction != null) saved.images else memory.images ?: saved.images
            gifAttributions = (if (saved.transaction != null) saved.gifAttributions else memory.gifAttributions ?: saved.gifAttributions)
                .filterKeys { it in images }
            memory.text = text
            memory.images = images
            memory.gifAttributions = gifAttributions
            transaction = saved.transaction
            memory.transactionId = saved.transaction?.id
            queued = saved.queued
            queueSaved = saved.queued
            available = saved.available
            ready = true
            if (!passive || reconciled || handedOff) error = false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (!passive || (!sending && !saving)) error = true }
    }
    private suspend fun reconcile(candidate: SaitoTransaction, content: String): Boolean = withContext(Dispatchers.IO) {
        var confirmed = false
        DeliveryAttempts.run(candidate.id) {
            SaitoPeer(wallet, endpoint).use { peer ->
                if (peer.hasPost(candidate, owner, content)) {
                    try {
                        check(store.confirmAvailable(candidate.id))
                        store.markSubmitted(candidate.id)
                    } catch (failure: Exception) { throw DraftPersistenceFailure(failure) }
                    confirmed = true
                }
            }
        }
        confirmed
    }

    fun checkStatus() {
        val candidate = transaction ?: return
        if (!ready || saving || sending || checking) return
        val content = text
        checkingId = candidate.id
        checkError = false
        scope.launch {
            try {
                val reconciled = reconcile(candidate, content)
                writes.withLock {
                    if (transaction?.id == candidate.id && reconciled) {
                        memory.clearSubmitted()
                        submitted = true
                    }
                }
            } catch (timeout: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                if (transaction?.id == candidate.id) checkError = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: DraftPersistenceFailure) { if (transaction?.id == candidate.id) error = true }
            catch (_: Exception) { if (transaction?.id == candidate.id) checkError = true }
            finally { if (checkingId == candidate.id) checkingId = null }
        }
    }
    suspend fun persist(): Boolean = writes.withLock {
        if (!ready || preparingImages) return@withLock false
        try {
            val snapshot = PostDraftStore.Saved(text, transaction, queued, available, images = images, gifAttributions = gifAttributions)
            withContext(NonCancellable) {
                savedContent = null
                val persisted = withContext(Dispatchers.IO) { store.saveComposer(snapshot) }
                savedContent = persisted
                memory.text = persisted.text
                memory.images = persisted.images
                memory.gifAttributions = persisted.gifAttributions
                queueSaved = persisted.queued
            }
            error = false
            true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true; false }
    }
    fun saveAndClose(onClose: () -> Unit) {
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
    fun send(beforePublishing: suspend () -> Boolean) {
        if (!ready || saving || sending || preparingImages || (text.isBlank() && images.isEmpty())) return
        val retrying = transaction != null
        networkError = false
        validationError = false
        sending = true
        scope.launch {
            try {
                if (!retrying && !beforePublishing()) return@launch
                val candidate = writes.withLock {
                    val prepared = transaction ?: withContext(Dispatchers.IO) {
                        val body = RedSquarePosts.message(text, images, parent, quote)
                        val recipients = (parent ?: quote)?.let { (listOf(it.author) + it.recipients).distinct() }.orEmpty()
                        SaitoTransaction.create(wallet, body, System.currentTimeMillis(), recipients)
                    }
                    if (transaction == null) queued = true
                    transaction = prepared
                    memory.transactionId = prepared.id
                    val snapshot = PostDraftStore.Saved(text, prepared, queued, images = images, gifAttributions = gifAttributions)
                    savedContent = null
                    val persisted = withContext(NonCancellable + Dispatchers.IO) { store.saveComposer(snapshot) }
                    savedContent = persisted
                    queued = persisted.queued
                    queueSaved = persisted.queued
                    available = persisted.available
                    prepared
                }
                try { withContext(Dispatchers.IO) {
                    DeliveryAttempts.run(candidate.id) {
                    SaitoPeer(wallet, endpoint).use { peer ->
                        if (retrying && peer.hasPost(candidate, owner, text)) {
                            try { check(store.confirmAvailable(candidate.id)) }
                            catch (failure: Exception) { throw DraftPersistenceFailure(failure) }
                            finishSubmission(candidate.id)
                            return@use
                        }
                        peer.authenticate()
                        if (retrying && !withContext(Dispatchers.Main.immediate) { beforePublishing() }) return@use
                        // A crash after this write may have transmitted. It is no longer cancellable queued work.
                        val permitted = withContext(Dispatchers.Main.immediate) {
                            writes.withLock {
                                val claimed = withContext(NonCancellable + Dispatchers.IO) {
                                    try {
                                        val saved = store.load()
                                        check(saved.transaction?.id == candidate.id)
                                        if (saved.available) false
                                        else store.claim(saved)
                                    }
                                    catch (failure: Exception) { throw DraftPersistenceFailure(failure) }
                                }
                                if (claimed) queued = false
                                claimed
                            }
                        }
                        if (!permitted) return@use
                        peer.submit(candidate) {
                            store.assertCanPublish(candidate.id)
                        }
                        peer.finishBroadcast()
                        finishSubmission(candidate.id)
                    }
                    }
                } } catch (timeout: TimeoutCancellationException) {
                    currentCoroutineContext().ensureActive()
                    networkError = true
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: DraftPersistenceFailure) { throw failure }
                catch (_: Exception) { networkError = true }
                error = false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: SaitoTransaction.InvalidMessage) { validationError = true }
            catch (_: Exception) { error = true }
            finally { sending = false }
        }
    }
    private suspend fun finishSubmission(id: String) {
        try { withContext(NonCancellable + Dispatchers.IO) { store.markSubmitted(id) } }
        catch (failure: Exception) { throw DraftPersistenceFailure(failure) }
        withContext(Dispatchers.Main.immediate) {
            memory.clearSubmitted()
            queued = false
            submitted = true
        }
    }
    fun cancelQueued() {
        if (!ready || saving || sending || !queued || !queueSaved) return
        saving = true
        scope.launch {
            try {
                writes.withLock {
                    withContext(NonCancellable) {
                        withContext(Dispatchers.IO) {
                            val saved = store.load()
                            check(saved.queued && saved.transaction?.id == transaction?.id)
                            check(store.update(saved, PostDraftStore.Saved(text, images = images, gifAttributions = gifAttributions)))
                        }
                        transaction = null
                        queued = false
                        queueSaved = false
                        networkError = false
                        memory.text = text
                        memory.images = images
                        memory.gifAttributions = gifAttributions
                        if (memory.unfinished == null) memory.unfinished = withContext(Dispatchers.IO) {
                            store.unfinishedText().also { memory.addCloseable("unfinished-text", it) }
                        }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { saving = false }
        }
    }
    fun startAnotherDraft(onStarted: () -> Unit) {
        val candidate = transaction ?: return
        if (!ready || saving || sending || available || queued || store.observesOnly) return
        saving = true
        scope.launch {
            try {
                writes.withLock {
                    withContext(NonCancellable) {
                        withContext(Dispatchers.IO) { store.retainAttempt(candidate.id) }
                        memory.clearSubmitted()
                        onStarted()
                        transaction = null
                        text = ""
                        images = emptyList()
                        gifAttributions = emptyMap()
                        queued = false
                        queueSaved = false
                        submitted = false
                        available = false
                        networkError = false
                        checkError = false
                        error = false
                        savedContent = PostDraftStore.Saved("")
                        memory.text = ""
                        memory.images = emptyList()
                        memory.gifAttributions = emptyMap()
                        memory.unfinished = withContext(Dispatchers.IO) {
                            store.unfinishedText().also { memory.addCloseable("unfinished-text", it) }
                        }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { saving = false }
        }
    }
    fun clearAndClose(onClose: () -> Unit) {
        if (!ready || saving || sending || transaction == null || available) return
        confirmClear = false
        saving = true
        scope.launch {
            try {
                writes.withLock {
                    withContext(NonCancellable) {
                        withContext(Dispatchers.IO) { store.save(PostDraftStore.Saved("")) }
                        memory.text = ""
                        memory.images = emptyList()
                        memory.gifAttributions = emptyMap()
                        text = ""
                        images = emptyList()
                        gifAttributions = emptyMap()
                        transaction = null
                    }
                }
                onClose()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { saving = false }
        }
    }
    fun edit(value: String) {
        if (!ready || saving || sending || transaction != null) return
        text = value
        memory.text = value
        memory.unfinished?.update(value)
        validationError = false
    }

    fun openImagePicker(onLaunch: () -> Unit) {
        if (transaction != null || images.size >= RedSquareImages.MAX_COUNT) return
        openMediaSource(onLaunch)
    }

    /** Source links also persist the latest authored work before leaving the app. */
    fun openMediaSource(onLaunch: () -> Unit) {
        if (!ready || saving || sending) return
        saving = true
        scope.launch {
            try {
                if (!persist()) return@launch
                // Persistence finishes even during disposal. External navigation must not.
                ensureActive()
                onLaunch()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { imageError = true }
            finally { saving = false }
        }
    }

    suspend fun addImages(resolver: ContentResolver, selection: SelectedImagePreparation.Selection) {
        if (!ready || saving || sending || transaction != null) return
        val operation = memory.imagePreparation
        imageError = false
        try {
            val perImage = writes.withLock {
                if (operation.pending !== selection) return
                require(selection.uris.size <= RedSquareImages.MAX_COUNT - images.size)
                // Keep prior content recoverable before starting a provider read. Do
                // not hold the draft mutex, or disable input, while reading pixels.
                saving = true
                try {
                    val prior = PostDraftStore.Saved(text, images = images, gifAttributions = gifAttributions)
                    withContext(NonCancellable) {
                        savedContent = null
                        savedContent = withContext(Dispatchers.IO) { store.saveComposer(prior) }
                        memory.unfinished?.update(memory.text ?: text)
                    }
                    val remaining = withContext(Dispatchers.IO) {
                        SaitoTransaction.MAX_MESSAGE_BYTES - RedSquarePosts.message(text, images, parent, quote)
                            .toString().toByteArray(Charsets.UTF_8).size - 8 * selection.uris.size
                    }
                    minOf(RedSquarePosts.MAX_IMAGE_CHARS, remaining / selection.uris.size).also { require(it >= 1024) }
                } finally { saving = false }
            }
            val prepared = operation.prepared(selection, resolver, perImage)
            writes.withLock {
                if (operation.pending !== selection) return@withLock
                saving = true
                try {
                    // Activity replacement can leave an older waiter finishing a
                    // non-cancellable commit. The retained memory is authoritative.
                    val latest = memory.text ?: text
                    val combined = (memory.images ?: images) + prepared
                    val credits = memory.gifAttributions ?: gifAttributions
                    require(withContext(Dispatchers.IO) {
                        RedSquarePosts.message(latest, combined, parent, quote).toString().toByteArray(Charsets.UTF_8).size
                    } <= SaitoTransaction.MAX_MESSAGE_BYTES)
                    withContext(NonCancellable) {
                        savedContent = null
                        val persisted = withContext(Dispatchers.IO) {
                            store.saveComposer(PostDraftStore.Saved(latest, images = combined, gifAttributions = credits))
                        }
                        savedContent = persisted
                        images = persisted.images
                        gifAttributions = persisted.gifAttributions
                        memory.images = persisted.images
                        memory.gifAttributions = persisted.gifAttributions
                        text = memory.text ?: latest
                        memory.unfinished?.update(text)
                        operation.complete(selection)
                    }
                    error = false
                    validationError = false
                } finally { saving = false }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { operation.fail(selection); imageError = true }
    }

    /** A provider selection is its unchanged original URL, never normalized raster bytes. */
    fun addGif(item: GiphyItem, onAdded: () -> Unit) {
        val source = item.originalUrl
        if (!ready || saving || sending || preparingImages || transaction != null || images.size >= RedSquareImages.MAX_COUNT) return
        saving = true
        imageError = false
        scope.launch {
            try {
                writes.withLock {
                    require(source.length <= 8192 && RedSquareImages.safeSource(source) == source)
                    val combined = images + source
                    val size = withContext(Dispatchers.IO) {
                        RedSquarePosts.message(text, combined, parent, quote).toString().toByteArray(Charsets.UTF_8).size
                    }
                    require(size <= SaitoTransaction.MAX_MESSAGE_BYTES)
                    withContext(NonCancellable) {
                        savedContent = null
                        val persisted = withContext(Dispatchers.IO) { store.saveComposer(PostDraftStore.Saved(text, images = combined, gifAttributions = gifAttributions + (source to item.attribution))) }
                        savedContent = persisted
                        images = persisted.images
                        gifAttributions = persisted.gifAttributions
                        memory.images = persisted.images
                        memory.gifAttributions = persisted.gifAttributions
                        memory.text = persisted.text
                    }
                    imageError = false
                    error = false
                    validationError = false
                }
                ensureActive()
                onAdded()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { imageError = true }
            finally { saving = false }
        }
    }

    fun removeImage(index: Int) {
        if (!ready || saving || sending || preparingImages || transaction != null || index !in images.indices) return
        saving = true
        scope.launch {
            try {
                writes.withLock {
                    val replacement = images.filterIndexed { position, _ -> position != index }
                    withContext(NonCancellable) {
                        savedContent = null
                        val persisted = withContext(Dispatchers.IO) { store.saveComposer(PostDraftStore.Saved(text, images = replacement, gifAttributions = gifAttributions.filterKeys { it in replacement })) }
                        savedContent = persisted
                        images = persisted.images
                        gifAttributions = persisted.gifAttributions
                        memory.images = persisted.images
                        memory.gifAttributions = persisted.gifAttributions
                        memory.text = persisted.text
                    }
                    imageError = false
                    error = false
                    validationError = false
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { imageError = true }
            finally { saving = false }
        }
    }
    private class DraftPersistenceFailure(cause: Exception) : RuntimeException(cause)
}
