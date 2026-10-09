package nl.davidengelen.redsquare

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private var outgoing: SaitoWallet? = null
    private var candidate: Candidate? = null
    private var readJob: Job? = null
    private var balanceJob: Job? = null

    fun clearMessage() { if (!busy) message = null }

    fun start() {
        if (busy) return
        balanceJob?.cancel()
        outgoing = currentWallet() ?: return
        candidate = null
        preview = null
        message = null
        busy = true
        try { chooseDocument() }
        catch (_: Exception) { finish(R.string.wallet_import_open_failed) }
    }

    fun documentChosen(uri: Uri?) {
        val original = outgoing ?: return
        val selectedBackend = currentBackend()
        if (uri == null) { finish(R.string.wallet_import_cancelled); return }
        readJob = activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val buffer = ByteArray(SaitoWallet.MAX_BACKUP_BYTES + 1)
                try {
                    require(uri.scheme == "content")
                    val size = (activity.contentResolver.openInputStream(uri)
                        ?: throw IOException("Wallet document unavailable")).use { input ->
                        var count = 0
                        while (count < buffer.size) {
                            val read = input.read(buffer, count, buffer.size - count)
                            if (read < 0) break
                            check(read > 0)
                            count += read
                        }
                        count
                    }
                    require(size in 1..SaitoWallet.MAX_BACKUP_BYTES)
                    val fingerprint = MessageDigest.getInstance("SHA-256").run {
                        update(buffer, 0, size)
                        digest()
                    }
                    ReadResult(Candidate(SaitoWallet.fromBackup(String(buffer, 0, size, Charsets.UTF_8)), original, fingerprint, selectedBackend), null)
                } catch (_: IllegalArgumentException) {
                    // Provider and parser messages can include private file values.
                    ReadResult(null, R.string.wallet_import_invalid)
                } catch (_: Exception) {
                    ReadResult(null, R.string.wallet_import_open_failed)
                } finally { buffer.fill(0) }
            }
            if (outgoing === original && currentWallet() === original) {
                candidate = result.candidate
                preview = result.candidate?.wallet?.let { Preview(it.publicKey, it.publicKey == original.publicKey) }
                finish(result.failure)
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

    private fun finish(result: Int?) {
        outgoing = null
        busy = false
        message = result
    }

    fun cancel() {
        outgoing = null
        candidate = null
        preview = null
        readJob?.cancel()
        balanceJob?.cancel()
        busy = false
    }

    private class Candidate(val wallet: SaitoWallet, val original: SaitoWallet, val fingerprint: ByteArray,
        val backend: SaitoBackend?)
    private class ReadResult(val candidate: Candidate?, val failure: Int?)
}
