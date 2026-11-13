package nl.davidengelen.redsquare.vault

import android.content.Context
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

internal enum class VaultUploadPhase { FILES, LOADING, PREPARING, REVIEW }
internal data class VaultUploadFile(val name: String, val mime: String, val size: Int)
internal data class VaultUploadView(val phase: VaultUploadPhase = VaultUploadPhase.FILES,
    val file: VaultUploadFile? = null, val bytesRead: Long = 0, val problem: Int? = null)

/** Process-owned preparation; only explicit future submission can publish a reviewed file. */
internal class VaultUploadSession(private val context: Context, owner: String, backend: SaitoBackend) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val disk = VaultDisk(context.applicationContext, owner, backend)
    private val mutable = MutableStateFlow(VaultUploadView())
    val state: StateFlow<VaultUploadView> = mutable.asStateFlow()
    private var job: Job? = null
    @Volatile private var source: InputStream? = null
    @Volatile private var sourceCancellation: android.os.CancellationSignal? = null

    fun load() {
        if (job?.isActive == true) return
        mutable.value = mutable.value.copy(phase = VaultUploadPhase.LOADING)
        job = scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    disk.draft().also { check(it != null || !disk.hasDraftData()) }
                }
                mutable.value = reviewed(file)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = VaultUploadView(VaultUploadPhase.REVIEW, problem = R.string.vault_draft_unavailable) }
        }
    }

    fun prepare(uri: Uri) {
        if (job?.isActive == true) return
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
                        while (true) {
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
                try {
                    withContext(NonCancellable + Dispatchers.IO) {
                        if (written != null && disk.draft()?.id == written) {
                            previous?.let { disk.saveDraft(it) } ?: disk.discardDraft()
                        }
                    }
                    mutable.value = prior.copy(problem = R.string.vault_source_cancelled)
                } catch (_: Exception) {
                    mutable.value = VaultUploadView(VaultUploadPhase.REVIEW, problem = R.string.vault_draft_unavailable)
                }
            } catch (failure: Exception) {
                mutable.value = if (failure is DraftStorageFailure)
                    VaultUploadView(VaultUploadPhase.REVIEW, problem = R.string.vault_source_save_failed)
                    else prior.copy(problem = if (failure is FileTooLarge) R.string.vault_source_too_large else R.string.vault_source_unavailable)
            } finally { source = null; sourceCancellation = null; timeout.cancel() }
        }
    }

    fun cancelPreparation() {
        if (mutable.value.phase != VaultUploadPhase.PREPARING) return
        job?.cancel()
        val reading = source
        val cancellation = sourceCancellation
        scope.launch(Dispatchers.IO) {
            try { cancellation?.cancel() } catch (_: Exception) { }
            try { reading?.close() } catch (_: Exception) { }
        }
    }

    fun discard() {
        if (job?.isActive == true) return
        val prior = mutable.value
        mutable.value = prior.copy(phase = VaultUploadPhase.LOADING)
        job = scope.launch {
            try {
                withContext(Dispatchers.IO) { disk.discardDraft() }
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
    private val sessions = linkedMapOf<Pair<String, SaitoBackend>, VaultUploadSession>()
    @Synchronized fun hasPreparation(owner: String): Boolean = sessions.any {
        it.key.first == owner && it.value.state.value.phase == VaultUploadPhase.PREPARING
    }
    @Synchronized fun session(context: Context, owner: String, backend: SaitoBackend): VaultUploadSession =
        sessions.getOrPut(owner to backend) { VaultUploadSession(context.applicationContext, owner, backend) }
}
