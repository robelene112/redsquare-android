package nl.davidengelen.redsquare.vault

import android.content.Context
import android.content.Intent
import nl.davidengelen.redsquare.WalletBalances
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.*
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale

internal enum class VaultUploadPhase { FILES, LOADING, PREPARING, REVIEW, CHECKING, AUTHORIZING, SENDING, WAITING, COMPLETE }
internal data class VaultUploadFile(val name: String, val mime: String, val size: Int)
internal data class VaultUploadView(val phase: VaultUploadPhase = VaultUploadPhase.FILES,
    val file: VaultUploadFile? = null, val bytesRead: Long = 0, val problem: Int? = null,
    val step: VaultSendStep? = null, val record: VaultUploadRecord? = null)

/** Process-owned preparation; only explicit future submission can publish a reviewed file. */
internal class VaultUploadSession(private val context: Context, private val wallet: SaitoWallet, private val backend: SaitoBackend,
    private val endpoint: String, balances: WalletBalances) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val disk = VaultDisk(context.applicationContext, wallet.publicKey, backend)
    private val store = VaultUploadStore(context, wallet.publicKey, backend)
    private val transfer = VaultUploadTransfer(context, wallet, backend, endpoint, balances)
    private val epoch = java.util.concurrent.atomic.AtomicLong()
    private val mutable = MutableStateFlow(VaultUploadView())
    val state: StateFlow<VaultUploadView> = mutable.asStateFlow()
    private var job: Job? = null
    @Volatile private var source: InputStream? = null
    @Volatile private var sourceCancellation: android.os.CancellationSignal? = null

    fun load() {
        if (job?.isCompleted == false || mutable.value.phase != VaultUploadPhase.FILES) return
        mutable.value = mutable.value.copy(phase = VaultUploadPhase.LOADING)
        job = scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    disk.draft().also { check(it != null || !disk.hasDraftData()) }
                }
                val record = withContext(Dispatchers.IO) { store.read() }
                mutable.value = reviewed(file).copy(record = record,
                    phase = when { record?.complete == true -> VaultUploadPhase.COMPLETE
                        record != null && (record.mayHaveSent || record.uploaded) -> VaultUploadPhase.WAITING
                        else -> if (file == null) VaultUploadPhase.FILES else VaultUploadPhase.REVIEW })
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = VaultUploadView(VaultUploadPhase.REVIEW, problem = R.string.vault_draft_unavailable) }
        }
    }

    suspend fun acceptShared(uri: Uri) {
        if (state.value.phase in setOf(VaultUploadPhase.LOADING, VaultUploadPhase.FILES)) job?.join()
        if (job?.isCompleted == false || mutable.value.phase != VaultUploadPhase.FILES || mutable.value.record != null)
            mutable.value = mutable.value.copy(problem = R.string.vault_share_existing_work)
        else prepare(uri)
    }

    fun prepare(uri: Uri) {
        if (job?.isCompleted == false || mutable.value.record != null) return
        val prior = mutable.value
        val readingSignal = android.os.CancellationSignal().also { sourceCancellation = it }
        mutable.value = VaultUploadView(VaultUploadPhase.PREPARING)
        job = scope.launch {
            var previous: VaultFile? = null
            var written: String? = null
            val preparation = currentCoroutineContext().job
            val timeout = scope.launch { delay(90_000); if (preparation.isActive) cancelPreparation() }
            try {
                val file = withContext(Dispatchers.IO) {
                    require(uri.scheme == "content")
                    previous = disk.draft()
                    val resolver = context.contentResolver
                    var name = "Shared file"
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null, readingSignal)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex) && cursor.getLong(sizeIndex) > SaitoVault.MAX_FILE_BYTES) throw FileTooLarge()
                        }
                    }
                    name = name.substringAfterLast('/').substringAfterLast('\\').trim().also { require(it.isNotEmpty() && it.length <= 1024) }
                    val mime = (resolver.getType(uri) ?: "application/octet-stream").substringBefore(';').lowercase(Locale.ROOT)
                        .also { require(it.matches(Regex("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+"))) }
                    val output = ByteArrayOutputStream()
                    var lastProgress = 0L
                    requireNotNull(resolver.openAssetFileDescriptor(uri, "r", readingSignal)).use { descriptor ->
                    descriptor.createInputStream().also { source = it }.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        val readiness = android.system.StructPollfd().apply {
                            fd = descriptor.parcelFileDescriptor.fileDescriptor
                            events = (android.system.OsConstants.POLLIN or android.system.OsConstants.POLLHUP or
                                android.system.OsConstants.POLLERR).toShort()
                        }
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            // Provider pipes may stay open indefinitely without another byte. Poll
                            // outside InputStream's read/close monitor so a stop stays cooperative.
                            if (android.system.Os.poll(arrayOf(readiness), 100) == 0) continue
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            check(count > 0)
                            if (output.size() + count > SaitoVault.MAX_FILE_BYTES) throw FileTooLarge()
                            output.write(buffer, 0, count)
                            val now = android.os.SystemClock.elapsedRealtime()
                            if (now - lastProgress >= 100) {
                                lastProgress = now
                                withContext(Dispatchers.Main.immediate) { mutable.value = mutable.value.copy(bytesRead = output.size().toLong()) }
                            }
                        }
                    }
                    }
                    source = null
                    currentCoroutineContext().ensureActive()
                    val bytes = output.toByteArray()
                    val localDigest = MessageDigest.getInstance("SHA-512").digest(bytes).joinToString("") { "%02x".format(it) }
                    VaultFile(localDigest, name, mime, bytes).also {
                        written = localDigest
                        try { disk.saveDraft(it) } catch (failure: Exception) { throw DraftStorageFailure(failure) }
                        currentCoroutineContext().ensureActive()
                    }
                }
                mutable.value = reviewed(file)
            } catch (cancelled: CancellationException) {
                restoreCancelledPreparation(previous, written, prior)
            } catch (failure: Exception) {
                // Closing a provider pipe may report IOException or OperationCanceledException
                // before withContext propagates coroutine cancellation. It is still a user stop.
                android.util.Log.w("VaultUpload", "[DEBUG-vault107] source failure active=${currentCoroutineContext().isActive} child=${System.identityHashCode(currentCoroutineContext().job)} field=${System.identityHashCode(job)} type=${failure.javaClass.name}")
                if (!currentCoroutineContext().isActive) restoreCancelledPreparation(previous, written, prior)
                else mutable.value = if (failure is DraftStorageFailure)
                    VaultUploadView(VaultUploadPhase.REVIEW, problem = R.string.vault_source_save_failed)
                    else prior.copy(problem = if (failure is FileTooLarge) R.string.vault_source_too_large else R.string.vault_source_unavailable)
            } finally { source = null; sourceCancellation = null; timeout.cancel() }
        }
    }

    private suspend fun restoreCancelledPreparation(previous: VaultFile?, written: String?, prior: VaultUploadView) {
        try {
            withContext(NonCancellable + Dispatchers.IO) {
                if (written != null && disk.draft()?.id == written)
                    previous?.let { disk.saveDraft(it) } ?: disk.discardDraft()
            }
            mutable.value = prior.copy(problem = R.string.vault_source_cancelled)
        } catch (_: Exception) {
            mutable.value = VaultUploadView(VaultUploadPhase.REVIEW, problem = R.string.vault_draft_unavailable)
        }
    }

    fun cancelPreparation() {
        if (mutable.value.phase != VaultUploadPhase.PREPARING) return
        android.util.Log.w("VaultUpload", "[DEBUG-vault107] cancel phase=${mutable.value.phase} active=${job?.isActive} completed=${job?.isCompleted} field=${System.identityHashCode(job)}")
        job?.cancel()
        val reading = source
        val cancellation = sourceCancellation
        scope.launch(Dispatchers.IO) {
            try { cancellation?.cancel() } catch (_: Exception) { }
            try { reading?.close() } catch (_: Exception) { }
        }
    }

    /** Preflight creates and retains one exact candidate before asking the system to authorize it. */
    fun requestAuthorization(ready: (VaultUploadRecord, Long) -> Unit) {
        if (job?.isCompleted == false) return
        val token = epoch.incrementAndGet()
        val prior = mutable.value
        mutable.value = prior.copy(phase = VaultUploadPhase.CHECKING, problem = null)
        job = scope.launch {
            try {
                val file = withContext(Dispatchers.IO) { requireNotNull(disk.draft()) }
                val record = transfer.prepare(file) { require(epoch.get() == token) }
                mutable.value = prior.copy(phase = VaultUploadPhase.AUTHORIZING, record = record, problem = null)
                ready(record, token)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.value = prior.copy(problem = problem(failure)) }
        }
    }

    fun authorizationCancelled(token: Long) {
        if (epoch.compareAndSet(token, token + 1) && mutable.value.phase == VaultUploadPhase.AUTHORIZING)
            mutable.value = mutable.value.copy(phase = if (mutable.value.record?.mayHaveSent == true || mutable.value.record?.uploaded == true)
                VaultUploadPhase.WAITING else VaultUploadPhase.REVIEW, problem = R.string.vault_authorization_cancelled)
    }

    fun send(record: VaultUploadRecord, token: Long) {
        if (epoch.get() != token || mutable.value.phase != VaultUploadPhase.AUTHORIZING) return
        try { context.startForegroundService(Intent(context, VaultUploadService::class.java)) }
        catch (_: Exception) {
            mutable.value = mutable.value.copy(phase = VaultUploadPhase.REVIEW, problem = R.string.vault_background_unavailable)
            return
        }
        mutable.value = mutable.value.copy(phase = VaultUploadPhase.SENDING, problem = null)
        job = scope.launch {
            try {
                val file = withContext(Dispatchers.IO) { requireNotNull(disk.draft()) }
                val result = transfer.send(record, file, { require(epoch.get() == token) }) { step ->
                    withContext(Dispatchers.Main.immediate) { mutable.value = mutable.value.copy(step = step) }
                }
                mutable.value = mutable.value.copy(phase = if (result.complete) VaultUploadPhase.COMPLETE else VaultUploadPhase.WAITING,
                    record = result, step = null)
            } catch (_: CancellationException) { interrupted() }
            catch (failure: Exception) { interrupted(problem(failure)) }
        }
    }

    private suspend fun interrupted(problem: Int = R.string.vault_upload_interrupted) {
        val record = try { withContext(NonCancellable + Dispatchers.IO) { store.read() } } catch (_: Exception) { null }
        mutable.value = mutable.value.copy(phase = VaultUploadPhase.WAITING, record = record ?: mutable.value.record,
            step = null, problem = problem)
    }

    fun cancelSending() {
        if (mutable.value.phase != VaultUploadPhase.SENDING) return
        epoch.incrementAndGet(); job?.cancel(); transfer.cancel()
    }

    fun checkOwnership() {
        if (job?.isCompleted == false) return
        val record = mutable.value.record ?: return
        val token = epoch.incrementAndGet()
        mutable.value = mutable.value.copy(phase = VaultUploadPhase.CHECKING, problem = null)
        job = scope.launch {
            try {
                val result = transfer.check(record) { require(epoch.get() == token) }
                mutable.value = mutable.value.copy(record = result,
                    phase = if (result.complete) VaultUploadPhase.COMPLETE else VaultUploadPhase.WAITING)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.value = mutable.value.copy(phase = VaultUploadPhase.WAITING, problem = problem(failure)) }
        }
    }

    fun backToFiles() {
        if (job?.isCompleted == false || mutable.value.phase != VaultUploadPhase.COMPLETE) return
        job = scope.launch {
            try { withContext(Dispatchers.IO) { store.remove() }; mutable.value = VaultUploadView() }
            catch (_: Exception) { mutable.value = mutable.value.copy(problem = R.string.vault_draft_remove_failed) }
        }
    }

    private fun problem(failure: Exception) = when (failure) {
        is VaultInsufficientFunds -> R.string.vault_insufficient_funds
        is VaultFundingChanged -> R.string.vault_funding_changed
        is VaultServiceMissing -> R.string.vault_service_unavailable
        else -> R.string.vault_upload_interrupted
    }

    fun discard() {
        if (job?.isCompleted == false) return
        val prior = mutable.value
        mutable.value = prior.copy(phase = VaultUploadPhase.LOADING)
        job = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val record = store.read()
                    require(record?.mayHaveSent != true && record?.uploaded != true)
                    disk.discardDraft(); store.remove()
                }
                mutable.value = VaultUploadView()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = prior.copy(problem = R.string.vault_draft_remove_failed) }
        }
    }
    private fun reviewed(file: VaultFile?) = if (file == null) VaultUploadView()
        else VaultUploadView(VaultUploadPhase.REVIEW, VaultUploadFile(file.name, file.mime, file.size))
    private class FileTooLarge : IllegalArgumentException()
    private class DraftStorageFailure(cause: Exception) : java.io.IOException(cause)
}

/** Models are shared across module switches. Files remain encrypted and scoped on disk. */
internal object VaultUploads {
    private data class Identity(val owner: String, val backend: SaitoBackend, val endpoint: String)
    private val sessions = linkedMapOf<Identity, VaultUploadSession>()
    @Synchronized fun hasPreparation(owner: String): Boolean = sessions.any {
        it.key.owner == owner && it.value.state.value.phase in setOf(VaultUploadPhase.PREPARING, VaultUploadPhase.SENDING,
            VaultUploadPhase.CHECKING, VaultUploadPhase.AUTHORIZING)
    }
    @Synchronized fun active(): List<VaultUploadSession> = sessions.values.filter { it.state.value.phase == VaultUploadPhase.SENDING }
    @Synchronized fun session(context: Context, wallet: SaitoWallet, backend: SaitoBackend, endpoint: String,
        balances: WalletBalances): VaultUploadSession = sessions.getOrPut(Identity(wallet.publicKey, backend, endpoint)) {
        VaultUploadSession(context.applicationContext, wallet, backend, endpoint, balances)
    }
}
