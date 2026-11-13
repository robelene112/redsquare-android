package nl.davidengelen.redsquare.vault

import android.app.Activity
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import nl.davidengelen.redsquare.AndroidWalletStore
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.WalletBalances
import nl.davidengelen.redsquare.saito.*

/** Foreground authority is transient. The exact reserved candidate is durable and never automatically sent. */
internal class VaultKeyTransfers(private val activity: Activity, private val wallet: SaitoWallet,
    private val backend: SaitoBackend, private val endpoint: String, private val balances: WalletBalances,
    private val scope: CoroutineScope) {
    private val context = activity.applicationContext
    private val store = TransferStore(context, wallet.publicKey)
    var records by mutableStateOf<List<TransferStore.PendingKey>>(emptyList()); private set
    var inclusions by mutableStateOf<Map<String, SaitoPaymentChain.Inclusion>>(emptyMap()); private set
    var busy by mutableStateOf(false); private set
    var problem by mutableStateOf<Int?>(null); private set
    @Volatile private var revision = 0L
    private var job: Job? = null
    private var cancellation: CancellationSignal? = null
    @Volatile private var peer: SaitoPeer? = null
    @Volatile private var closed = false

    private fun current(token: Long) {
        check(!closed && revision == token && !activity.isFinishing && !activity.isDestroyed)
        if (AndroidWalletStore(context).loadExisting().publicKey != wallet.publicKey || AndroidBackendStore(context).load() != backend)
            throw ContextChanged()
    }
    private fun fresh(transfer: SaitoNftTransfer) {
        val holding = requireNotNull(balances.read(backend, wallet.publicKey)).nfts.singleOrNull {
            it.id == transfer.holding.id && it.creator == transfer.holding.creator
        }
        require(holding?.witnesses == transfer.holding.witnesses) { "Current key ownership changed" }
    }
    fun load() {
        if (busy || closed) return
        val token = ++revision
        busy = true; problem = null
        job = scope.launch {
            try { records = withContext(Dispatchers.IO) { current(token); store.pendingKeys() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (revision == token) {
                    records = emptyList(); inclusions = emptyMap(); problem = explained(failure, R.string.vault_key_storage_failed)
                }
            }
            finally { if (revision == token) busy = false }
        }
    }
    fun prepare(key: VaultKey, recipient: String, ready: (String) -> Unit) {
        if (busy || closed) return
        val token = ++revision
        busy = true; problem = null
        job = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    require(SaitoWallet.isPublicKey(recipient) && recipient != wallet.publicKey)
                    current(token)
                    val holding = requireNotNull(balances.read(backend, wallet.publicKey)).nfts.single {
                        it.id == key.holding.id && it.creator == key.holding.creator
                    }
                    val available = store.availableKeys(listOf(holding))
                    require(available.size == 1)
                    val currentKey = SaitoVault.readKey(holding, key.envelope)
                    SaitoPeer(wallet, endpoint).use { it.authenticate() }
                    val transfer = SaitoNftTransfer.create(wallet, currentKey, recipient, System.currentTimeMillis())
                    val operation = currentCoroutineContext().job
                    synchronized(SaitoLocalState.lock) {
                        operation.ensureActive(); current(token)
                        store.reserveKey(backend, transfer)
                    }
                    transfer to store.pendingKeys()
                }
                if (revision == token) { records = result.second; ready(result.first.transaction.id) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (revision == token) problem = explained(failure, R.string.vault_key_review_failed) }
            finally { if (revision == token) busy = false }
        }
    }
    fun discard(record: TransferStore.PendingKey, done: () -> Unit) {
        if (busy || closed) return
        val token = ++revision
        busy = true; problem = null
        job = scope.launch {
            try {
                val retained = withContext(Dispatchers.IO) {
                    require(record.backend == backend)
                    store.releaseUnsubmittedKey(backend, record.transfer) { current(token) }
                    store.pendingKeys()
                }
                if (revision == token) { records = retained; busy = false; done() }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (revision == token) problem = explained(failure, R.string.vault_key_storage_failed) }
            finally { if (revision == token) busy = false }
        }
    }
    fun check(record: TransferStore.PendingKey) {
        if (busy || closed || record.backend != backend) return
        val token = ++revision
        busy = true; problem = null
        inclusions = inclusions - record.transfer.transaction.id
        job = scope.launch {
            try {
                val inclusion = withContext(Dispatchers.IO) { current(token); balances.observeKeyTransfer(backend, record.transfer) }
                if (revision == token && inclusion != null) inclusions = inclusions + (record.transfer.transaction.id to inclusion)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (revision == token) problem = explained(failure, R.string.vault_key_check_failed) }
            finally { if (revision == token) busy = false }
        }
    }
    fun authorize(record: TransferStore.PendingKey) {
        if (busy || closed || record.backend != backend) return
        if (!activity.getSystemService(KeyguardManager::class.java).isDeviceSecure) {
            problem = R.string.transfer_auth_setup; return
        }
        val token = ++revision
        busy = true; problem = null
        val signal = CancellationSignal().also { cancellation = it }
        try {
            val builder = BiometricPrompt.Builder(activity).setTitle(activity.getString(R.string.vault_key_auth_title))
            if (Build.VERSION.SDK_INT >= 30) builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            else { @Suppress("DEPRECATION") builder.setDeviceCredentialAllowed(true) }
            builder.build().authenticate(signal, activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (revision != token || closed) return
                    cancellation = null
                    send(record, token)
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (revision != token || closed) return
                    cancellation = null; busy = false
                    problem = if (errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED || errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED)
                        R.string.transfer_cancelled else R.string.wallet_auth_unavailable
                }
            })
        } catch (_: Exception) {
            signal.cancel(); cancellation = null; busy = false; problem = R.string.wallet_auth_unavailable
        }
    }
    private fun send(record: TransferStore.PendingKey, token: Long) {
        job = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    current(token)
                    val transfer = record.transfer
                    val saved = store.pendingKeys().single { it.transfer.transaction.id == transfer.transaction.id }
                    require(saved.backend == backend && saved.transfer.transaction.encoded() == transfer.transaction.encoded())
                    val included = if (saved.mayHaveSent) balances.observeKeyTransfer(backend, transfer) else null
                    if (included == null) {
                        fresh(transfer)
                        val operation = currentCoroutineContext().job
                        SaitoPeer(wallet, endpoint).use { connection ->
                            peer = connection
                            connection.submit(transfer.transaction) {
                                operation.ensureActive(); current(token)
                                store.markKeyMayHaveSent(backend, transfer)
                            }
                            connection.finishBroadcast()
                        }
                        val submitted = store.pendingKeys()
                        withContext(Dispatchers.Main.immediate) { if (revision == token) records = submitted }
                    }
                    store.pendingKeys() to (included ?: balances.observeKeyTransfer(backend, transfer))
                }
                if (revision == token) {
                    records = result.first
                    result.second?.let { inclusions = inclusions + (record.transfer.transaction.id to it) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                val retained = withContext(NonCancellable + Dispatchers.IO) { runCatching { store.pendingKeys() }.getOrNull() }
                if (revision == token) {
                    retained?.let { records = it }
                    problem = if (failure is ContextChanged) R.string.vault_key_context_changed
                        else if (retained == null) R.string.vault_key_storage_failed
                        else if (retained.any { it.transfer.transaction.id == record.transfer.transaction.id && it.mayHaveSent })
                            R.string.vault_key_waiting_detail else R.string.vault_key_ownership_changed
                }
            } finally { peer = null; if (revision == token) busy = false }
        }
    }
    /** Closing never discards a candidate. Re-read after cancellation before exposing its uncertainty. */
    fun interrupt() {
        if (closed) return
        if (!busy) { problem = null; return }
        ++revision
        cancellation?.cancel(); cancellation = null
        peer?.close(); peer = null
        val prior = job
        prior?.cancel()
        busy = true
        job = scope.launch {
            try {
                prior?.join()
                records = withContext(Dispatchers.IO) { store.pendingKeys() }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { problem = R.string.vault_key_storage_failed }
            finally { busy = false }
        }
    }
    fun close() {
        closed = true; ++revision
        cancellation?.cancel(); cancellation = null
        job?.cancel(); peer?.close(); peer = null
    }
    private class ContextChanged : IllegalStateException()
    private fun explained(failure: Exception, fallback: Int) =
        if (failure is ContextChanged) R.string.vault_key_context_changed else fallback

}
