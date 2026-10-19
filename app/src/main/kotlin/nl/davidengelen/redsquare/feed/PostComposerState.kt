package nl.davidengelen.redsquare.feed

import androidx.compose.runtime.*
import android.content.ContentResolver
import android.net.Uri
import nl.davidengelen.redsquare.media.GiphyAttribution
import nl.davidengelen.redsquare.media.GiphyItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
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
    private val writes = Mutex()
    var text by mutableStateOf("")
    var images by mutableStateOf<List<String>>(emptyList())
    var gifAttributions by mutableStateOf<Map<String, GiphyAttribution>>(emptyMap())
    var imageError by mutableStateOf(false)
    var preparingImages by mutableStateOf(false)
    var ready by mutableStateOf(false)
    var saving by mutableStateOf(false)
    var error by mutableStateOf(false)
    var networkError by mutableStateOf(false)
    var validationError by mutableStateOf(false)
    var transaction by mutableStateOf<SaitoTransaction?>(null)
    var queued by mutableStateOf(false)
    var queueSaved by mutableStateOf(false)
    var submitted by mutableStateOf(false)
    var available by mutableStateOf(false)
    var sending by mutableStateOf(false)
    var reload by mutableIntStateOf(0)
    var confirmClear by mutableStateOf(false)
    suspend fun load() {
        try {
            val saved = withContext(Dispatchers.IO) { store.load() }
            text = if (saved.transaction != null) saved.text else memory.text ?: saved.text
            images = if (saved.transaction != null) saved.images else memory.images ?: saved.images
            gifAttributions = (if (saved.transaction != null) saved.gifAttributions else memory.gifAttributions ?: saved.gifAttributions)
                .filterKeys { it in images }
            memory.text = text
            memory.images = images
            memory.gifAttributions = gifAttributions
            transaction = saved.transaction
            queued = saved.queued
            queueSaved = saved.queued
            available = saved.available
            ready = true
            error = false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
    }
    suspend fun persist(): Boolean = writes.withLock {
        if (!ready) return@withLock false
        try {
            val snapshot = PostDraftStore.Saved(text, transaction, queued, available, images = images, gifAttributions = gifAttributions)
            withContext(NonCancellable) {
                val persisted = withContext(Dispatchers.IO) { store.saveComposer(snapshot) }
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
        if (!ready || saving || sending || (text.isBlank() && images.isEmpty())) return
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
                    val snapshot = PostDraftStore.Saved(text, prepared, queued, images = images, gifAttributions = gifAttributions)
                    val persisted = withContext(NonCancellable + Dispatchers.IO) { store.saveComposer(snapshot) }
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
                            withContext(Dispatchers.Main.immediate) { available = true; queued = false }
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
                        withContext(Dispatchers.Main.immediate) { submitted = true }
                        for (attempt in 0 until 6) {
                            if (peer.hasPost(candidate, owner, text)) {
                                try { check(store.confirmAvailable(candidate.id)) }
                                catch (failure: Exception) { throw DraftPersistenceFailure(failure) }
                                withContext(Dispatchers.Main.immediate) { available = true }
                                break
                            }
                            if (attempt < 5) delay(2_000)
                        }
                    }
                    }
                } } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: DraftPersistenceFailure) { throw failure }
                catch (_: Exception) { networkError = true }
                error = false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: SaitoTransaction.InvalidMessage) { validationError = true }
            catch (_: Exception) { error = true }
            finally { sending = false }
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

    fun addImages(resolver: ContentResolver, uris: List<Uri>) {
        if (!ready || saving || sending || transaction != null || uris.isEmpty()) return
        if (uris.size > RedSquareImages.MAX_COUNT - images.size) { imageError = true; return }
        saving = true
        imageError = false
        preparingImages = true
        scope.launch {
            try {
                writes.withLock {
                    // Preserve current work before any provider read or pixel allocation.
                    val prior = PostDraftStore.Saved(text, images = images, gifAttributions = gifAttributions)
                    val persistedPrior = withContext(NonCancellable + Dispatchers.IO) { store.saveComposer(prior) }
                    memory.text = persistedPrior.text
                    memory.images = persistedPrior.images
                    memory.gifAttributions = persistedPrior.gifAttributions
                    val remaining = withContext(Dispatchers.IO) {
                        SaitoTransaction.MAX_MESSAGE_BYTES - RedSquarePosts.message(text, images, parent, quote)
                            .toString().toByteArray(Charsets.UTF_8).size - 8 * uris.size
                    }
                    val perImage = minOf(RedSquarePosts.MAX_IMAGE_CHARS, remaining / uris.size)
                    require(perImage >= 1024)
                    val prepared = mutableListOf<String>()
                    for (uri in uris) prepared.add(PostImagePreparation.normalize(resolver, uri, perImage))
                    val combined = images + prepared
                    val finalSize = withContext(Dispatchers.IO) { RedSquarePosts.message(text, combined, parent, quote).toString().toByteArray(Charsets.UTF_8).size }
                    require(finalSize <= SaitoTransaction.MAX_MESSAGE_BYTES)
                    val snapshot = PostDraftStore.Saved(text, images = combined, gifAttributions = gifAttributions)
                    withContext(NonCancellable) {
                        val persisted = withContext(Dispatchers.IO) { store.saveComposer(snapshot) }
                        images = persisted.images
                        gifAttributions = persisted.gifAttributions
                        memory.images = persisted.images
                        memory.gifAttributions = persisted.gifAttributions
                        memory.text = persisted.text
                    }
                    error = false
                    validationError = false
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { imageError = true }
            finally { saving = false; preparingImages = false }
        }
    }

    /** A provider selection is its unchanged original URL, never normalized raster bytes. */
    fun addGif(item: GiphyItem, onAdded: () -> Unit) {
        val source = item.originalUrl
        if (!ready || saving || sending || transaction != null || images.size >= RedSquareImages.MAX_COUNT) return
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
                        val persisted = withContext(Dispatchers.IO) { store.saveComposer(PostDraftStore.Saved(text, images = combined, gifAttributions = gifAttributions + (source to item.attribution))) }
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
        if (!ready || saving || sending || transaction != null || index !in images.indices) return
        saving = true
        scope.launch {
            try {
                writes.withLock {
                    val replacement = images.filterIndexed { position, _ -> position != index }
                    withContext(NonCancellable) {
                        val persisted = withContext(Dispatchers.IO) { store.saveComposer(PostDraftStore.Saved(text, images = replacement, gifAttributions = gifAttributions.filterKeys { it in replacement })) }
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
