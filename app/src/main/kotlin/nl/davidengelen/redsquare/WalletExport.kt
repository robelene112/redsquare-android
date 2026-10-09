package nl.davidengelen.redsquare

import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
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

/** One device-authenticated export. No grant or secret is saved across recreation. */
class WalletExport(
    private val activity: ComponentActivity,
    private val currentWallet: () -> SaitoWallet?,
    private val chooseDocument: (String) -> Unit,
) {
    private val documents = activity.applicationContext.contentResolver
    var message by mutableStateOf<Int?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    @Volatile private var attempt: Attempt? = null

    private class Attempt(val wallet: SaitoWallet) {
        val cancellation = CancellationSignal()
        var uri: Uri? = null
        var authenticating = false
        var deadline: Job? = null
        @Volatile var descriptor: ParcelFileDescriptor? = null
    }

    fun clearMessage() { if (!busy) message = null }

    fun start() {
        if (busy) return
        if (!activity.getSystemService(KeyguardManager::class.java).isDeviceSecure) {
            message = R.string.wallet_auth_setup
            return
        }
        val wallet = currentWallet() ?: return
        val operation = Attempt(wallet)
        attempt = operation
        busy = true
        message = null
        try { chooseDocument("saito-wallet-${wallet.publicKey.takeLast(8)}.json") }
        catch (_: Exception) { finish(operation, R.string.wallet_export_failed) }
    }

    fun documentChosen(uri: Uri?) {
        val operation = attempt ?: return
        if (uri == null) { finish(operation, R.string.wallet_export_cancelled); return }
        operation.uri = uri
        if (!isCurrent(operation)) { cancel(); return }
        operation.authenticating = true
        try {
            val builder = BiometricPrompt.Builder(activity)
                .setTitle(activity.getString(R.string.wallet_auth_title))
                .setSubtitle(activity.getString(R.string.wallet_auth_subtitle))
            if (Build.VERSION.SDK_INT >= 30) {
                builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            } else {
                @Suppress("DEPRECATION")
                builder.setDeviceCredentialAllowed(true)
            }
            builder.build().authenticate(operation.cancellation, activity.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (!isCurrent(operation) || !operation.authenticating) return
                        operation.authenticating = false
                        save(operation, uri)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (!isCurrent(operation) || !operation.authenticating) return
                        val cancelled = errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED ||
                            errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED
                        discard(operation)
                        finish(operation, if (cancelled) R.string.wallet_export_cancelled
                            else R.string.wallet_auth_unavailable)
                    }
                })
        } catch (_: Exception) {
            discard(operation)
            finish(operation, R.string.wallet_auth_unavailable)
        }
    }

    private fun save(operation: Attempt, uri: Uri) {
        operation.deadline = activity.lifecycleScope.launch {
            delay(SAVE_TIMEOUT_MILLIS)
            if (isCurrent(operation)) {
                // Publish the terminal state without awaiting a remote provider.
                finish(operation, R.string.wallet_export_failed)
                discard(operation)
            }
        }
        activity.lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                var verified = false
                var bytes: ByteArray? = null
                try {
                    check(isCurrent(operation))
                    val backup = operation.wallet.exportJson().toByteArray(Charsets.UTF_8)
                    bytes = backup
                    require(backup.size in 1..SaitoWallet.MAX_BACKUP_BYTES)
                    check(isCurrent(operation))
                    val output = requireNotNull(documents.openFileDescriptor(uri, "wt", operation.cancellation))
                    operation.descriptor = output
                    ParcelFileDescriptor.AutoCloseOutputStream(output).use { stream ->
                        check(isCurrent(operation))
                        stream.write(backup)
                        stream.flush()
                        output.checkError()
                    }
                    operation.descriptor = null
                    check(isCurrent(operation))
                    val input = requireNotNull(documents.openFileDescriptor(uri, "r", operation.cancellation))
                    operation.descriptor = input
                    val readback = ByteArray(backup.size + 1)
                    try {
                        val size = ParcelFileDescriptor.AutoCloseInputStream(input).use { stream ->
                            var count = 0
                            while (count < readback.size) {
                                val read = stream.read(readback, count, readback.size - count)
                                if (read < 0) break
                                check(read > 0)
                                count += read
                            }
                            input.checkError()
                            count
                        }
                        check(size == backup.size)
                        check(backup.indices.all { backup[it] == readback[it] })
                        check(SaitoWallet.fromBackup(String(readback, 0, size, Charsets.UTF_8)).publicKey == operation.wallet.publicKey)
                        check(isCurrent(operation))
                        verified = true
                    } finally { readback.fill(0) }
                } catch (_: Exception) {
                    // Provider/parser messages may contain private values. Never surface them.
                } finally {
                    operation.descriptor = null
                    bytes?.fill(0)
                    if (!verified) discard(operation)
                }
                verified
            }
            if (isCurrent(operation)) finish(operation,
                if (saved) R.string.wallet_export_saved else R.string.wallet_export_failed)
        }
    }

    private fun isCurrent(operation: Attempt) =
        attempt === operation && !operation.cancellation.isCanceled && currentWallet() === operation.wallet

    private fun finish(operation: Attempt, result: Int) {
        if (attempt !== operation) return
        attempt = null
        operation.deadline?.cancel()
        busy = false
        message = result
        stopResources(operation)
    }

    fun cancel() {
        val operation = attempt ?: return
        finish(operation, R.string.wallet_export_cancelled)
        discard(operation)
    }

    private fun stopResources(operation: Attempt) {
        cleanupScope.launch {
            runCatching { operation.cancellation.cancel() }
            runCatching { operation.descriptor?.close() }
        }
    }

    private fun discard(operation: Attempt) {
        val uri = operation.uri ?: return
        val resolver = documents
        cleanupScope.launch { runCatching { DocumentsContract.deleteDocument(resolver, uri) } }
    }

    companion object {
        private const val SAVE_TIMEOUT_MILLIS = 30_000L
        // Resource cleanup must still run after the activity scope is destroyed.
        private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
