package nl.davidengelen.redsquare.store

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

/** Selected-account seller observations. Reading this screen never creates a withdrawal. */
internal class StoreProceeds(private val activity: Activity, private val wallet: SaitoWallet,
    private val backend: SaitoBackend, private val selected: SaitoBackend, private val endpoint: String,
    private val scope: CoroutineScope) {
    var report by mutableStateOf<SaitoStoreWithdrawal.Proceeds?>(null); private set
    var loading by mutableStateOf(false); private set
    var problem by mutableStateOf<Int?>(null); private set
    var records by mutableStateOf<List<StoreWithdrawalStore.Record>>(emptyList()); private set
    var focused by mutableStateOf<StoreWithdrawalStore.Record?>(null); private set
    var busy by mutableStateOf(false); private set
    var methods by mutableStateOf<SaitoStoreCrypto.Methods?>(null); private set
    private val journal = StoreWithdrawalStore(activity.applicationContext, wallet.publicKey)
    private val balances = WalletBalances.fromIntent(activity.intent)
    private val networks = StoreCryptoNetworks(activity.intent)
    @Volatile private var closed = false
    @Volatile private var revision = 0L
    private var job: Job? = null
    private var authentication: CancellationSignal? = null
    private fun current(token: Long) {
        check(!closed && token == revision && !activity.isFinishing && !activity.isDestroyed &&
            AndroidWalletStore(activity).loadExisting().publicKey == wallet.publicKey && AndroidBackendStore(activity).load() == selected)
    }
    fun load() {
        if (closed || loading || busy) return
        val token = ++revision; loading = true; problem = null; report = null; methods = null
        job = scope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { current(token); journal.read() }
                if (!closed && token == revision) records = saved
                val result = withContext(Dispatchers.IO) {
                    current(token)
                    SaitoPeer(wallet, endpoint).use { peer ->
                        val store = peer.servicePublicKey("Store")
                        val request = SaitoTransaction.create(wallet, SaitoStoreWithdrawal.proceedsRequest(), System.currentTimeMillis(), listOf(store))
                        val report = SaitoStoreWithdrawal.readProceeds(peer.request(request) { current(token) }.decodeToString())
                        val methods = runCatching { discover(peer, token) }.getOrNull()
                        currentCoroutineContext().ensureActive(); current(token)
                        report to methods
                    }
                }
                currentCoroutineContext().ensureActive()
                if (!closed && token == revision) { report = result.first; methods = result.second }
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                if (!closed && token == revision) problem = R.string.store_proceeds_unavailable
            } finally { if (token == revision) loading = false }
        }
    }
    private suspend fun discover(peer: SaitoPeer, token: Long): SaitoStoreCrypto.Methods {
        val services = peer.services()
        val key = peer.servicePublicKey(if ("Mixin" in services) "Mixin" else "buysaito")
        return SaitoStoreCrypto.readMethods(peer.request(SaitoTransaction.create(wallet, SaitoStoreCrypto.methodsRequest(),
            System.currentTimeMillis(), listOf(key))) { current(token) }.decodeToString())
    }
    private fun stopReading() { revision++; job?.cancel(); job = null; loading = false }
    fun open(record: StoreWithdrawalStore.Record) { if (!busy) { stopReading(); focused = record; problem = null } }
    fun closeReview() { if (!busy) { stopReading(); focused = null; problem = null } }
    private fun operation(work: suspend (Long) -> Unit) {
        if (closed || busy) return
        stopReading(); val token = revision; busy = true; problem = null
        job = scope.launch {
            try { work(token) }
            catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                if (!closed && token == revision) problem = R.string.store_withdrawal_failed
            } finally { if (token == revision) busy = false }
        }
    }
    fun prepare(ticker: String, address: String, networkFee: String, ready: () -> Unit) = operation { token ->
        val saved = withContext(Dispatchers.IO) {
            current(token)
            SaitoPeer(wallet, endpoint).use { peer ->
                val store = peer.servicePublicKey("Store")
                val currency = discover(peer, token).currencies.single { it.ticker == ticker }
                val network = networks.name(currency.chainId)
                val response = peer.request(SaitoTransaction.create(wallet, SaitoStoreWithdrawal.quoteRequest(ticker, address),
                    System.currentTimeMillis(), listOf(store))) { current(token) }.decodeToString()
                val now = System.currentTimeMillis()
                val quote = SaitoStoreWithdrawal.readQuote(response, store, ticker, address, now)
                require(networkFee.length <= 24)
                val fee = networkFee.toBigDecimal().movePointRight(8).toBigIntegerExact().also { require(it.signum() >= 0 && it.bitLength() <= 53) }
                val required = quote.serviceFee + fee
                val funding = if (required.signum() == 0) emptyList() else {
                    val observed = requireNotNull(balances.funding(backend, wallet.publicKey))
                    val available = TransferStore(activity, wallet.publicKey).available(observed.eligible).sortedByDescending { it.amount }
                    var sum = java.math.BigInteger.ZERO
                    available.takeWhile { if (sum >= required) false else { sum += it.amount; true } }.also { require(sum >= required) }
                }
                val candidate = SaitoStoreWithdrawal.create(wallet, quote, funding, fee, now)
                val active = currentCoroutineContext().job
                synchronized(SaitoLocalState.lock) { active.ensureActive(); current(token); journal.reserve(backend, candidate, currency.chainId, network) }
            }
        }
        if (!closed && token == revision) { records = records + saved; focused = saved; ready() }
    }
    fun discard(record: StoreWithdrawalStore.Record) = operation { token ->
        withContext(Dispatchers.IO) { synchronized(SaitoLocalState.lock) { current(token); journal.discard(record) } }
        records = records.filterNot { it.candidate.transaction.id == record.candidate.transaction.id }; focused = null
    }
    fun authorize(record: StoreWithdrawalStore.Record) {
        if (closed || busy || record.backend != backend || record.result?.state == SaitoStoreWithdrawal.State.SUBMITTED) return
        if (!activity.getSystemService(KeyguardManager::class.java).isDeviceSecure) { problem = R.string.wallet_auth_unavailable; return }
        stopReading(); val token = revision; busy = true; problem = null
        val signal = CancellationSignal().also { authentication = it }
        try {
            val builder = BiometricPrompt.Builder(activity).setTitle(activity.getString(R.string.store_withdrawal_authorize))
            if (Build.VERSION.SDK_INT >= 30) builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            else { @Suppress("DEPRECATION") builder.setDeviceCredentialAllowed(true) }
            builder.build().authenticate(signal, activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (closed || token != revision) return
                    authentication = null; send(record, token)
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (closed || token != revision) return
                    authentication = null; busy = false; problem = R.string.transfer_cancelled
                }
            })
        } catch (_: Exception) { signal.cancel(); authentication = null; busy = false; problem = R.string.wallet_auth_unavailable }
    }
    private fun send(record: StoreWithdrawalStore.Record, token: Long) {
        job = scope.launch {
            try {
                val saved = withContext(Dispatchers.IO) {
                    current(token)
                    val exact = journal.read().single { it.candidate.transaction.id == record.candidate.transaction.id }
                    require(exact.backend == backend && exact.candidate.transaction.encoded() == record.candidate.transaction.encoded())
                    SaitoPeer(wallet, endpoint).use { peer ->
                        require(peer.servicePublicKey("Store") == exact.candidate.quote.store)
                        if (!exact.mayHaveSent) {
                            require(exact.candidate.quote.validAt(System.currentTimeMillis()))
                            require(discover(peer, token).currencies.any { it.ticker == exact.candidate.quote.ticker && it.chainId == exact.chain })
                            if (exact.candidate.inputs.isNotEmpty()) {
                                val funding = requireNotNull(balances.funding(backend, wallet.publicKey))
                                require(exact.candidate.inputs.all { it in funding.eligible })
                            }
                        }
                        val request = SaitoTransaction.create(wallet, SaitoStoreWithdrawal.submitRequest(exact.candidate), System.currentTimeMillis(), listOf(exact.candidate.quote.store))
                        val response = peer.request(request) { current(token); journal.markSending(exact) }.decodeToString()
                        val result = SaitoStoreWithdrawal.readResult(response)
                        val active = currentCoroutineContext().job
                        synchronized(SaitoLocalState.lock) { active.ensureActive(); current(token); journal.observe(exact, result) }
                        journal.read()
                    }
                }
                if (!closed && token == revision) { records = saved; focused = saved.single { it.candidate.transaction.id == record.candidate.transaction.id }; report = null }
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                if (!closed && token == revision) {
                    val saved = withContext(Dispatchers.IO) { runCatching { journal.read() }.getOrNull() }
                    if (saved != null) { records = saved; focused = saved.singleOrNull { it.candidate.transaction.id == record.candidate.transaction.id } }
                    problem = R.string.store_withdrawal_unknown
                }
            } finally { if (token == revision) busy = false }
        }
    }
    fun dispose() { closed = true; revision++; authentication?.cancel(); authentication = null; job?.cancel() }
}
