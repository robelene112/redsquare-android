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
import nl.davidengelen.redsquare.spaces.AccountRecoveryBackup
import nl.davidengelen.redsquare.spaces.SpaceHistoryKeys
import java.io.IOException
import java.io.InputStream
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
    private val retainArchive: suspend (SaitoWallet, List<SpaceHistoryKeys>) -> Unit = { _, keys -> check(keys.isEmpty()) },
) {
    data class Preview(val publicKey: String, val sameIdentity: Boolean, val backupVerified: Boolean = false,
        val balance: ImportBalance? = null, val backupSkipped: Boolean = false, val acknowledged: Boolean = false,
        val risks: SaitoWallet.ReplacementRisks? = null, val archiveScopes: Int? = null)
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
    private var lockedBackup by mutableStateOf<LockedBackup?>(null)
    val needsPassphrase get() = lockedBackup != null
    private var readJob: Job? = null
    private var balanceJob: Job? = null

    fun clearMessage() { if (!busy) message = null }

    fun start() {
        if (busy) return
        balanceJob?.cancel()
        readJob?.cancel()
        val original = currentWallet() ?: return
        val backend = currentBackend()
        clearLockedBackup()
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
                var buffer: ByteArray? = null
                try {
                    require(uri.scheme == "content")
                    check(isCurrent(operation))
                    val descriptor = documents.openFileDescriptor(uri, "r", operation.cancellation)
                        ?: throw IOException("Wallet document unavailable")
                    operation.descriptor = descriptor
                    val (bytes, size) = ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                        val marker = ByteArray(8)
                        val prefix = readDocument(input, marker)
                        val protected = prefix == marker.size && AccountRecoveryBackup.recognizes(marker)
                        val limit = if (protected) AccountRecoveryBackup.maxFileBytes else SaitoWallet.MAX_BACKUP_BYTES
                        val incoming = ByteArray(limit + 1)
                        buffer = incoming
                        marker.copyInto(incoming, endIndex = prefix)
                        marker.fill(0)
                        val length = readDocument(input, incoming, prefix)
                        descriptor.checkError()
                        require(length in 1..limit)
                        incoming to length
                    }
                    operation.descriptor = null
                    check(isCurrent(operation))
                    if (AccountRecoveryBackup.recognizes(bytes)) {
                        ReadResult(null, null, bytes.copyOf(size))
                    } else {
                        val json = Charsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes, 0, size)).toString()
                        ReadResult(candidateFor(SaitoWallet.fromBackup(json), operation.original, operation.backend), null)
                    }
                } catch (_: CharacterCodingException) {
                    ReadResult(null, R.string.wallet_import_invalid)
                } catch (_: IllegalArgumentException) {
                    // Provider and parser messages can include private file values.
                    ReadResult(null, R.string.wallet_import_invalid)
                } catch (_: Exception) {
                    ReadResult(null, R.string.wallet_import_open_failed)
                } finally { operation.descriptor = null; buffer?.fill(0) }
            }
            if (isCurrent(operation)) {
                finish(operation, result.failure)
                result.protectedBytes?.let { lockedBackup = LockedBackup(operation.original, operation.backend, it) }
                result.candidate?.let(::offerPreview)
            } else result.protectedBytes?.fill(0)
        }
    }

    fun unlock(passphrase: CharArray) {
        val backup = lockedBackup ?: return
        if (busy || passphrase.isEmpty() || currentWallet() !== backup.original || currentBackend() != backup.backend) return
        val secret = passphrase.copyOf()
        busy = true
        message = null
        readJob = activity.lifecycleScope.launch {
            try {
                val selected = withContext(Dispatchers.IO) {
                    val restored = AccountRecoveryBackup.open(backup.bytes, secret)
                    candidateFor(restored.wallet, backup.original, backup.backend, restored.history)
                }
                if (lockedBackup === backup && currentWallet() === backup.original && currentBackend() == backup.backend) {
                    clearLockedBackup()
                    offerPreview(selected)
                    busy = false
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (lockedBackup === backup) message = R.string.wallet_protected_unlock_failed
            } finally {
                secret.fill('\u0000')
                if (lockedBackup === backup) busy = false
            }
        }
    }

    private fun readDocument(input: InputStream, bytes: ByteArray, start: Int = 0): Int {
        var size = start
        while (size < bytes.size) {
            val read = input.read(bytes, size, bytes.size - size)
            if (read < 0) break
            check(read > 0)
            size += read
        }
        return size
    }

    private fun offerPreview(selected: Candidate) {
        candidate = selected
        preview = Preview(selected.wallet.publicKey, selected.wallet.publicKey == selected.original.publicKey,
            risks = selected.risks, archiveScopes = selected.archive?.size)
        if (preview?.sameIdentity == false) assessBalance(selected)
    }

    private fun candidateFor(incoming: SaitoWallet, original: SaitoWallet, backend: SaitoBackend?,
        archive: List<SpaceHistoryKeys>? = null): Candidate {
        var nativeWork = try {
            nl.davidengelen.redsquare.saito.TransferStore(activity.applicationContext,
                original.publicKey).hasWork() ||
                nl.davidengelen.redsquare.vault.VaultUploads.hasPreparation(original.publicKey) ||
                nl.davidengelen.redsquare.feed.SocialActionJournal.hasWork(activity.applicationContext,
                    original.publicKey) || SaitoBackend.entries.any { backend ->
                nl.davidengelen.redsquare.feed.PostDraftStore.authorWork(activity.applicationContext,
                    original.publicKey, backend).isNotEmpty() ||
                    nl.davidengelen.redsquare.vault.VaultUploadStore(activity.applicationContext, original.publicKey, backend).hasWork() ||
                    nl.davidengelen.redsquare.feed.ProfileDraftStore(activity.applicationContext,
                        original.publicKey, backend).load().hasWork ||
                    nl.davidengelen.redsquare.vault.VaultDisk(activity.applicationContext,
                        original.publicKey, backend).let { it.hasDraftData() || it.copies().items.isNotEmpty() } ||
                    nl.davidengelen.redsquare.chat.ChatWorkStore.hasWork(activity.applicationContext,
                        original, backend) ||
                    nl.davidengelen.redsquare.chat.ChatReactionStore.hasWork(activity.applicationContext,
                        original, backend) ||
                    nl.davidengelen.redsquare.chat.GroupChatStore.hasWork(activity.applicationContext,
                        original, backend)
            }
        } catch (_: Exception) { true }
        val registrar = backend?.let {
            RegistryAuthority.fromIntent(activity.intent, FeedEndpoints.fromIntent(activity.intent, it))
        } ?: SaitoRegistry.REGISTRAR
        val nativeName = try {
            RegisteredNameStore(activity.applicationContext, original.publicKey, registrar)
                .load()?.name?.identifier
        } catch (_: Exception) { nativeWork = true; null }
        val registryWork = try {
            SaitoBackend.entries.any { backend ->
                val authority = RegistryAuthority.fromIntent(activity.intent,
                    FeedEndpoints.fromIntent(activity.intent, backend))
                UsernameRequestStore(activity.applicationContext, original.publicKey, backend, authority)
                    .load()?.let { request ->
                        SaitoEnvelope.decode(request.envelope()).body.get("identifier").asString != nativeName
                    } ?: false
            }
        } catch (_: Exception) { true }
        return Candidate(incoming, original, backend, nativeWork || registryWork, nativeName, archive)
    }

    private fun clearLockedBackup() {
        lockedBackup?.bytes?.fill(0)
        lockedBackup = null
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
            if (!view.sameIdentity) {
                val retainedWork = withContext(Dispatchers.IO) {
                    try {
                        nl.davidengelen.redsquare.feed.SocialActionJournal.hasWork(activity.applicationContext, selected.original.publicKey) ||
                            nl.davidengelen.redsquare.saito.TransferStore(activity.applicationContext, selected.original.publicKey).hasWork() ||
                            nl.davidengelen.redsquare.vault.VaultUploads.hasPreparation(selected.original.publicKey) ||
                            SaitoBackend.entries.any { backend ->
                                nl.davidengelen.redsquare.vault.VaultUploadStore(activity.applicationContext, selected.original.publicKey, backend).hasWork() ||
                                nl.davidengelen.redsquare.vault.VaultDisk(activity.applicationContext, selected.original.publicKey, backend)
                                    .let { it.hasDraftData() || it.copies().items.isNotEmpty() }
                            }
                    } catch (_: Exception) { true }
                }
                if (candidate !== selected || currentWallet() !== selected.original || currentBackend() != selected.backend) {
                    cancel(); activating = false; return@launch
                }
                if (retainedWork && !selected.risks.pendingWork) {
                    selected.risks = selected.risks.copy(pendingWork = true)
                    preview = preview?.copy(risks = selected.risks, acknowledged = false)
                    message = R.string.wallet_import_work_changed
                    busy = false
                    activating = false
                    return@launch
                }
            }
            try {
                selected.archive?.let { retainArchive(selected.wallet, it) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                message = R.string.wallet_protected_restore_failed
                busy = false
                activating = false
                return@launch
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
        clearLockedBackup()
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

    private class LockedBackup(val original: SaitoWallet, val backend: SaitoBackend?, val bytes: ByteArray)

    private class Candidate(val wallet: SaitoWallet, val original: SaitoWallet,
        val backend: SaitoBackend?, nativeWork: Boolean, nativeName: String?, val archive: List<SpaceHistoryKeys>?) {
        // Candidate construction runs on IO, keeping large imported metadata off the UI thread.
        var risks = original.replacementRisks().let {
            it.copy(savedName = nativeName ?: it.savedName, pendingWork = it.pendingWork || nativeWork)
        }
    }
    private class ReadResult(val candidate: Candidate?, val failure: Int?, val protectedBytes: ByteArray? = null)

    companion object {
        private const val READ_TIMEOUT_MILLIS = 30_000L
        private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
