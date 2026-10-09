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
import java.io.IOException

/** Selection validates a backup without publishing a replacement identity. */
class WalletImport(
    private val activity: ComponentActivity,
    private val currentWallet: () -> SaitoWallet?,
    private val chooseDocument: () -> Unit,
) {
    var message by mutableStateOf<Int?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    private var outgoing: SaitoWallet? = null
    private var readJob: Job? = null

    fun clearMessage() { if (!busy) message = null }

    fun start() {
        if (busy) return
        outgoing = currentWallet() ?: return
        message = null
        busy = true
        try { chooseDocument() }
        catch (_: Exception) { finish(R.string.wallet_import_open_failed) }
    }

    fun documentChosen(uri: Uri?) {
        val original = outgoing ?: return
        if (uri == null) { finish(R.string.wallet_import_cancelled); return }
        readJob = activity.lifecycleScope.launch {
            val failure = withContext(Dispatchers.IO) {
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
                    SaitoWallet.fromBackup(String(buffer, 0, size, Charsets.UTF_8))
                    null
                } catch (_: IllegalArgumentException) {
                    // Provider and parser messages can include private file values.
                    R.string.wallet_import_invalid
                } catch (_: Exception) {
                    R.string.wallet_import_open_failed
                } finally { buffer.fill(0) }
            }
            if (outgoing === original && currentWallet() === original) finish(failure)
        }
    }

    private fun finish(result: Int?) {
        outgoing = null
        busy = false
        message = result
    }

    fun cancel() {
        outgoing = null
        readJob?.cancel()
        busy = false
    }
}
