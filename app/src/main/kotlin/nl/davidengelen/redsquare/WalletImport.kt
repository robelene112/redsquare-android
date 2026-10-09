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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.ImportBalance
import java.io.IOException
import java.security.MessageDigest

/** Selection validates a backup without publishing a replacement identity. */
class WalletImport(
    private val activity: ComponentActivity,
    private val currentWallet: () -> SaitoWallet?,
    private val currentBackend: () -> SaitoBackend?,
    private val balances: WalletBalances,
    private val chooseDocument: () -> Unit,
) {
    data class Preview(val publicKey: String, val sameIdentity: Boolean, val backupVerified: Boolean = false,
        val balance: ImportBalance? = null, val backupSkipped: Boolean = false)

    var preview by mutableStateOf<Preview?>(null)
        private set
    var message by mutableStateOf<Int?>(null)
        private set
    var busy by mutableStateOf(false)
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
        val operation = ReadAttempt(currentWallet() ?: return, currentBackend())
        readingAttempt = operation
        candidate = null
        preview = null
        message = null
        busy = true
        try { chooseDocument() }
        catch (_: Exception) { finish(operation, R.string.wallet_import_open_failed) }
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
                    val fingerprint = MessageDigest.getInstance("SHA-256").run {
                        update(buffer, 0, size)
                        digest()
                    }
                    ReadResult(Candidate(SaitoWallet.fromBackup(String(buffer, 0, size, Charsets.UTF_8)), operation.original, fingerprint, operation.backend), null)
                } catch (_: IllegalArgumentException) {
                    // Provider and parser messages can include private file values.
                    ReadResult(null, R.string.wallet_import_invalid)
                } catch (_: Exception) {
                    ReadResult(null, R.string.wallet_import_open_failed)
                } finally { operation.descriptor = null; buffer.fill(0) }
            }
            if (isCurrent(operation)) {
                candidate = result.candidate
                preview = result.candidate?.wallet?.let { Preview(it.publicKey, it.publicKey == operation.original.publicKey) }
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
                preview = preview?.copy(balance = balance, backupSkipped = false)
            }
        }
    }

    fun skipBackup() {
        val selected = candidate ?: return
        if (!busy && currentWallet() === selected.original && currentBackend() == selected.backend &&
            preview?.balance == ImportBalance.ZERO) preview = preview?.copy(backupSkipped = true)
    }

    fun backUpCurrentWallet(startExport: ((SaitoWallet) -> Unit) -> Unit) {
        val selected = candidate ?: return
        if (busy || currentWallet() !== selected.original || preview?.sameIdentity != false) return
        preview = preview?.copy(backupVerified = false)
        startExport { exported ->
            // The selected object is a per-selection nonce bound to the exact file
            // fingerprint and both wallet objects. Old exports/reselected files
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

    private class Candidate(val wallet: SaitoWallet, val original: SaitoWallet, val fingerprint: ByteArray,
        val backend: SaitoBackend?)
    private class ReadResult(val candidate: Candidate?, val failure: Int?)

    companion object {
        private const val READ_TIMEOUT_MILLIS = 30_000L
        private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
