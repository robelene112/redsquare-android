package nl.davidengelen.redsquare

import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.ImportBalance
import nl.davidengelen.redsquare.saito.SaitoRegistry
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.feed.FeedEndpoints
import nl.davidengelen.redsquare.registry.RegisteredNameStore
import nl.davidengelen.redsquare.registry.RegistryAuthority
import nl.davidengelen.redsquare.registry.UsernameRequestStore
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** Selection validates a backup without publishing a replacement identity. */
class WalletImport(
    private val activity: ComponentActivity,
    private val currentWallet: () -> SaitoWallet?,
    private val currentBackend: () -> SaitoBackend?,
    private val balances: WalletBalances,
    private val beforeSelection: suspend () -> Boolean,
    private val activate: suspend (SaitoWallet, SaitoWallet) -> Activation,
    private val chooseDocument: () -> Unit,
) {
    data class Preview(val publicKey: String, val sameIdentity: Boolean, val backupVerified: Boolean = false,
        val balance: ImportBalance? = null, val backupSkipped: Boolean = false, val acknowledged: Boolean = false,
        val risks: SaitoWallet.ReplacementRisks? = null)
    class Activation(val wallet: SaitoWallet?, val successful: Boolean)

    var preview by mutableStateOf<Preview?>(null)
        private set
    var message by mutableStateOf<Int?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var activating by mutableStateOf(false)
        private set
    private val documents = activity.applicationContext.contentResolver
    @Volatile private var readingAttempt: ReadAttempt? = null
    private var candidate: Candidate? = null
    private var readJob: Job? = null
    private var balanceJob: Job? = null

    fun clearMessage() { if (!busy) message = null }

    fun start() {
        if (busy) return
        balanceJob?.cancel()
        readJob?.cancel()
        val original = currentWallet() ?: return
        val backend = currentBackend()
        candidate = null
        preview = null
        message = null
        busy = true
        readJob = activity.lifecycleScope.launch {
            try {
                // Preview must assess durable outgoing work before the user confirms replacement.
                val saved = beforeSelection()
                currentCoroutineContext().ensureActive()
                if (!saved || currentWallet() !== original || currentBackend() != backend) {
                    busy = false
                    message = R.string.wallet_import_work_save_failed
                    return@launch
                }
                val operation = ReadAttempt(original, backend)
                readingAttempt = operation
                try { chooseDocument() }
                catch (_: Exception) { finish(operation, R.string.wallet_import_open_failed) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                busy = false
                message = R.string.wallet_import_work_save_failed
            }
        }
    }

    fun documentChosen(uri: Uri?) {
        val operation = readingAttempt ?: return
        if (operation.reading) return
        if (uri == null) { finish(operation, R.string.wallet_import_cancelled); return }
        operation.reading = true
        operation.deadline = activity.lifecycleScope.launch {
            delay(READ_TIMEOUT_MILLIS)
            if (readingAttempt === operation) finish(operation, R.string.wallet_import_open_failed)
        }
        readJob = activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val buffer = ByteArray(SaitoWallet.MAX_BACKUP_BYTES + 1)
                try {
                    require(uri.scheme == "content")
                    check(isCurrent(operation))
                    val descriptor = documents.openFileDescriptor(uri, "r", operation.cancellation)
                        ?: throw IOException("Wallet document unavailable")
                    operation.descriptor = descriptor
                    val size = ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                        check(isCurrent(operation))
                        var count = 0
                        while (count < buffer.size) {
                            val read = input.read(buffer, count, buffer.size - count)
                            if (read < 0) break
                            check(read > 0)
                            count += read
                        }
                        descriptor.checkError()
                        count
                    }
                    operation.descriptor = null
                    check(isCurrent(operation))
                    require(size in 1..SaitoWallet.MAX_BACKUP_BYTES)
                    val json = Charsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(buffer, 0, size)).toString()
                    var nativeWork = try {
                        SaitoBackend.entries.any { backend ->
                            nl.davidengelen.redsquare.feed.PostDraftStore.authorWork(activity.applicationContext,
                                operation.original.publicKey, backend).isNotEmpty() ||
                                nl.davidengelen.redsquare.feed.ProfileDraftStore(activity.applicationContext,
                                    operation.original.publicKey, backend).load().hasWork
                        }
                    } catch (_: Exception) { true }
                    val registrar = operation.backend?.let {
                        RegistryAuthority.fromIntent(activity.intent, FeedEndpoints.fromIntent(activity.intent, it))
                    } ?: SaitoRegistry.REGISTRAR
                    val nativeName = try {
                        RegisteredNameStore(activity.applicationContext, operation.original.publicKey, registrar)
                            .load()?.name?.identifier
                    } catch (_: Exception) { nativeWork = true; null }
                    val registryWork = try {
                        SaitoBackend.entries.any { backend ->
                            val authority = RegistryAuthority.fromIntent(activity.intent,
                                FeedEndpoints.fromIntent(activity.intent, backend))
                            UsernameRequestStore(activity.applicationContext, operation.original.publicKey, backend, authority)
                                .load()?.let { request ->
                                    SaitoEnvelope.decode(request.envelope()).body.get("identifier").asString != nativeName
                                } ?: false
                        }
                    } catch (_: Exception) { true }
                    ReadResult(Candidate(SaitoWallet.fromBackup(json), operation.original, operation.backend,
                        nativeWork || registryWork, nativeName), null)
                } catch (_: CharacterCodingException) {
                    ReadResult(null, R.string.wallet_import_invalid)
                } catch (_: IllegalArgumentException) {
                    // Provider and parser messages can include private file values.
                    ReadResult(null, R.string.wallet_import_invalid)
                } catch (_: Exception) {
                    ReadResult(null, R.string.wallet_import_open_failed)
                } finally { operation.descriptor = null; buffer.fill(0) }
            }
            if (isCurrent(operation)) {
                candidate = result.candidate
                preview = result.candidate?.wallet?.let { Preview(it.publicKey, it.publicKey == operation.original.publicKey,
                    risks = result.candidate.risks) }
                finish(operation, result.failure)
                result.candidate?.let { selected ->
                    if (preview?.sameIdentity == false) assessBalance(selected)
                }
            }
        }
    }

    private fun assessBalance(selected: Candidate) {
        balanceJob = activity.lifecycleScope.launch {
            val balance = selected.backend?.let { backend ->
                withContext(Dispatchers.IO) { balances.assess(backend, selected.original.publicKey) }
            } ?: ImportBalance.UNKNOWN
            if (candidate === selected && currentWallet() === selected.original && currentBackend() == selected.backend) {
                preview = preview?.copy(balance = if (selected.risks.otherAssetsUnknown)
                    ImportBalance.UNKNOWN else balance, backupSkipped = false)
            }
        }
    }

    fun skipBackup() {
        val selected = candidate ?: return
        if (!busy && currentWallet() === selected.original && currentBackend() == selected.backend &&
            preview?.balance == ImportBalance.ZERO) preview = preview?.copy(backupSkipped = true)
    }

    fun acknowledge(checked: Boolean) { if (!busy) preview = preview?.copy(acknowledged = checked) }

    fun confirm() {
        val selected = candidate ?: return
        val view = preview ?: return
        if (busy || !view.acknowledged || currentWallet() !== selected.original || currentBackend() != selected.backend ||
            (!view.sameIdentity && !view.backupVerified && !(view.balance == ImportBalance.ZERO && view.backupSkipped))) return
        busy = true
        activating = true
        balanceJob?.cancel()
        activity.lifecycleScope.launch {
            if (!view.sameIdentity && !view.backupVerified) {
                val balance = selected.backend?.let {
                    withContext(Dispatchers.IO) { balances.assess(it, selected.original.publicKey) }
                } ?: ImportBalance.UNKNOWN
                if (candidate !== selected || currentWallet() !== selected.original || currentBackend() != selected.backend) {
                    cancel(); activating = false; return@launch
                }
                if (balance != ImportBalance.ZERO || selected.risks.otherAssetsUnknown) {
                    preview = preview?.copy(balance = balance, backupSkipped = false)
                    message = R.string.wallet_import_balance_changed
                    busy = false
                    activating = false
                    return@launch
                }
            }
            if (candidate !== selected || currentWallet() !== selected.original || currentBackend() != selected.backend) {
                cancel(); activating = false; return@launch
            }
            val result = activate(selected.original, selected.wallet)
            candidate = null
            preview = null
            busy = false
            activating = false
            message = if (result.successful) R.string.wallet_import_saved
                else if (result.wallet != null) R.string.wallet_import_failed else R.string.wallet_import_recovery_needed
        }
    }

    fun backUpCurrentWallet(startExport: ((SaitoWallet) -> Unit) -> Unit) {
        val selected = candidate ?: return
        if (busy || currentWallet() !== selected.original || preview?.sameIdentity != false) return
        preview = preview?.copy(backupVerified = false)
        startExport { exported ->
            // The selected object is a per-selection nonce holding the validated
            // file contents and both wallet objects. Old exports/reselected files
            // cannot satisfy this gate, even if their public identities match.
            if (candidate === selected && currentWallet() === selected.original && exported === selected.original) {
                preview = preview?.copy(backupVerified = true)
            }
        }
    }

    private fun isCurrent(operation: ReadAttempt) = readingAttempt === operation &&
        !operation.cancellation.isCanceled && currentWallet() === operation.original && currentBackend() == operation.backend

    private fun finish(operation: ReadAttempt, result: Int?) {
        if (readingAttempt !== operation) return
        readingAttempt = null
        operation.deadline?.cancel()
        busy = false
        message = result
        stopResources(operation)
    }

    fun cancel() {
        readingAttempt?.let { finish(it, R.string.wallet_import_cancelled) }
        candidate = null
        preview = null
        readJob?.cancel()
        balanceJob?.cancel()
        busy = false
    }

    private fun stopResources(operation: ReadAttempt) {
        // Remote cancellation and closing a provider descriptor can block. Neither
        // may delay terminal UI state or prevent the other cleanup from running.
        cleanupScope.launch { runCatching { operation.cancellation.cancel() } }
        cleanupScope.launch { runCatching { operation.descriptor?.close() } }
    }

    private class ReadAttempt(val original: SaitoWallet, val backend: SaitoBackend?) {
        val cancellation = CancellationSignal()
        var reading = false
        var deadline: Job? = null
        @Volatile var descriptor: ParcelFileDescriptor? = null
    }

    private class Candidate(val wallet: SaitoWallet, val original: SaitoWallet,
        val backend: SaitoBackend?, nativeWork: Boolean, nativeName: String?) {
        // Candidate construction runs on IO, keeping large imported metadata off the UI thread.
        val risks = original.replacementRisks().let {
            it.copy(savedName = nativeName ?: it.savedName, pendingWork = it.pendingWork || nativeWork)
        }
    }
    private class ReadResult(val candidate: Candidate?, val failure: Int?)

    companion object {
        private const val READ_TIMEOUT_MILLIS = 30_000L
        private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
