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
import nl.davidengelen.redsquare.WalletBalances
import nl.davidengelen.redsquare.saito.*
import java.math.BigInteger

/** One foreground purchase authority. The reviewed terms survive only as an immutable, original order. */
internal class StorePurchases(private val activity: Activity, private val wallet: SaitoWallet,
    val backend: SaitoBackend, private val selected: SaitoBackend, private val endpoint: String, private val balances: WalletBalances,
    private val scope: CoroutineScope) {
    data class Review(val product: StoreProduct, val quantity: BigInteger, val store: String,
        val inputs: List<SaitoBalanceSnapshot.Slip>, val unit: BigInteger, val fee: BigInteger) {
        val total: BigInteger get() = unit * quantity + fee
    }
    private val context = activity.applicationContext
    private val store = StoreOrderStore(context, wallet.publicKey)
    var orders by mutableStateOf<List<StoreOrderStore.Order>>(emptyList()); private set
    var review by mutableStateOf<Review?>(null); private set
    var focused by mutableStateOf<String?>(null); private set
    var observations by mutableStateOf<Map<String, SaitoStoreChain.Observation>>(emptyMap()); private set
    var busy by mutableStateOf(false); private set
    var refreshing by mutableStateOf(false); private set
    private var readJob: Job? = null
    private var readRevision = 0L
    private var refreshOffset = 0
    var problem by mutableStateOf<Problem?>(null); private set
    enum class Problem { CONNECTION, INSUFFICIENT, CONTEXT, STORAGE, AUTH, CANCELLED, TERMS }
    @Volatile private var revision = 0L
    private var job: Job? = null
    private var cancellation: CancellationSignal? = null
    @Volatile private var peer: SaitoPeer? = null
    @Volatile private var closed = false
    private class Changed : IllegalStateException()
    private class Insufficient : IllegalStateException()
    private class Terms : IllegalStateException()
    private fun publishable(token: Long) {
        if (closed || revision != token || activity.isFinishing || activity.isDestroyed) throw Changed()
    }
    /** Durable authority is inspected only by IO work, never by the UI state publisher. */
    private fun current(token: Long) {
        publishable(token)
        if (AndroidWalletStore(context).loadExisting().publicKey != wallet.publicKey || AndroidBackendStore(context).load() != selected) throw Changed()
    }
    private fun operation(work: suspend (Long) -> Unit) {
        if (busy || closed) return
        stopReading()
        val token = ++revision; busy = true; problem = null
        job = scope.launch {
            try { work(token) }
            catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                if (revision == token) problem = when (failure) {
                    is StoreOrderStore.StorageUnavailable -> Problem.STORAGE
                    is Changed -> Problem.CONTEXT; is Insufficient -> Problem.INSUFFICIENT; is Terms -> Problem.TERMS
                    else -> Problem.CONNECTION
                }
            } finally { if (revision == token) busy = false }
        }
    }
    fun load() = operation { token ->
        try { orders = withContext(Dispatchers.IO) { current(token); store.read() } }
        catch (failure: Exception) { currentCoroutineContext().ensureActive(); problem = Problem.STORAGE }
    }
    /** One bounded read at a time, independent of payment authority and order navigation. */
    fun refresh(id: String? = null) {
        if (closed || busy || refreshing) return
        val token = revision; val reading = ++readRevision; val offset = refreshOffset++
        refreshing = true
        readJob = scope.launch {
            var selectedId: String? = id
            try {
                val result = withContext(Dispatchers.IO) {
                    current(token)
                    val rows = store.read().filter { it.backend == backend }
                    val order = if (id != null) rows.firstOrNull { it.purchase.transaction.id == id }
                        else rows.getOrNull(if (rows.isEmpty()) 0 else Math.floorMod(offset, rows.size))
                    selectedId = order?.purchase?.transaction?.id
                    val outcome = order?.let { observe(it, token) }
                    store.read() to outcome
                }
                currentCoroutineContext().ensureActive(); publishable(token)
                if (reading == readRevision) {
                    problem = null; orders = result.first
                    selectedId?.let { target -> observations = observations - target +
                        (result.second?.let { mapOf(target to it) } ?: emptyMap()) }
                }
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                if (reading == readRevision && revision == token) {
                    selectedId?.let { observations = observations - it }
                    problem = when (failure) {
                        is StoreOrderStore.StorageUnavailable -> Problem.STORAGE
                        is Changed -> Problem.CONTEXT
                        else -> Problem.CONNECTION
                    }
                }
            } finally { if (reading == readRevision) refreshing = false }
        }
    }
    fun stopReading() { readRevision++; readJob?.cancel(); readJob = null; refreshing = false }
    fun prepare(product: StoreProduct, quantity: String, networkFee: String) = operation { token ->
        review = null
        val result = withContext(Dispatchers.IO) {
            current(token)
            val previous = store.read().firstOrNull { it.backend == backend && it.product.signature == product.signature && it.receipt == null }
            if (previous != null) return@withContext previous to null
            val count = quantity.toBigIntegerOrNull() ?: throw Terms()
            val fee = networkFee.toBigDecimalOrNull()?.movePointRight(8)?.toBigIntegerExact() ?: throw Terms()
            if (count.signum() <= 0 || count > BigInteger("9007199254740991") || fee.signum() < 0 || fee.bitLength() > 64) throw Terms()
            val unit = product.price?.takeIf { it.currency == "SAITO" }?.amount?.movePointRight(8)?.toBigIntegerExact() ?: throw Terms()
            if (unit.signum() <= 0 || unit * count > BigInteger("9007199254740991")) throw Terms()
            val key = SaitoPeer(wallet, endpoint).use { connection ->
                val counterparty = connection.servicePublicKey("Store")
                val currentOffer = SaitoStore.readOffer(connection.request(SaitoStore.listingRequest(
                    StoreLink(backend, signature = product.signature)).toByteArray()).decodeToString(), StoreLink(backend, signature = product.signature))
                if (currentOffer == null || currentOffer.nftId != product.nftId || currentOffer.seller != product.seller ||
                    currentOffer.available < count || currentOffer.price?.currency != "SAITO" || requireNotNull(currentOffer.price).amount.compareTo(requireNotNull(product.price).amount) != 0) throw Terms()
                val original = SaitoStore.readListingArchive(currentOffer, connection.request(SaitoStore.listingArchiveRequest(product.signature).toByteArray()).decodeToString())
                if (original != product) throw Terms()
                counterparty
            }
            val funding = requireNotNull(balances.funding(backend, wallet.publicKey))
            val available = TransferStore(context, wallet.publicKey).available(funding.eligible).sortedByDescending { it.amount }
            var sum = BigInteger.ZERO
            val inputs = available.takeWhile { if (sum >= unit * count + fee) false else { sum += it.amount; true } }
            if (sum < unit * count + fee || inputs.size !in 1..254) throw Insufficient()
            current(token)
            null to Review(product, count, key, inputs, unit, fee)
        }
        publishable(token)
        if (result.first != null) { orders = withContext(Dispatchers.IO) { store.read() }; focused = result.first?.purchase?.transaction?.id }
        else review = result.second
    }
    fun open(order: StoreOrderStore.Order) { if (!busy) { stopReading(); focused = order.purchase.transaction.id; review = null; problem = null } }
    fun closeReview() { if (!busy) { stopReading(); focused = null; review = null; problem = null } }
    fun discard(order: StoreOrderStore.Order) = operation { token ->
        val rows = withContext(Dispatchers.IO) { current(token); store.discard(order.purchase.transaction.id); store.read() }
        orders = rows; focused = null
    }
    fun check(order: StoreOrderStore.Order) { stopReading(); refresh(order.purchase.transaction.id) }
    private fun observe(order: StoreOrderStore.Order, token: Long): SaitoStoreChain.Observation {
        val latest = store.read().single { it.purchase.transaction.id == order.purchase.transaction.id }
        val result = requireNotNull(balances.observeStore(backend, latest.purchase, latest.product.creator, latest.payment?.let {
            SaitoStoreChain.Payment(it.height, it.hash, it.ordinal)
        }, latest.receipt, latest.receiptBlock, latest.cursor))
        current(token)
        store.observe(order.purchase.transaction.id, result)
        return result
    }
    fun authorize(existing: StoreOrderStore.Order? = null) {
        if (busy || closed || (existing == null && review == null) || (existing != null && existing.backend != backend)) return
        if (!activity.getSystemService(KeyguardManager::class.java).isDeviceSecure) { problem = Problem.AUTH; return }
        val reviewed = review
        stopReading()
        val token = ++revision; busy = true; problem = null
        val signal = CancellationSignal().also { cancellation = it }
        try {
            val builder = BiometricPrompt.Builder(activity).setTitle(activity.getString(nl.davidengelen.redsquare.R.string.store_purchase_auth))
            if (Build.VERSION.SDK_INT >= 30) builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            else { @Suppress("DEPRECATION") builder.setDeviceCredentialAllowed(true) }
            builder.build().authenticate(signal, activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (revision != token || closed) return
                    cancellation = null; send(existing, reviewed, token)
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (revision != token || closed) return
                    cancellation = null; busy = false
                    problem = if (errorCode in listOf(BiometricPrompt.BIOMETRIC_ERROR_CANCELED, BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED)) Problem.CANCELLED else Problem.AUTH
                }
            })
        } catch (_: Exception) { signal.cancel(); cancellation = null; busy = false; problem = Problem.AUTH }
    }
    private fun send(existing: StoreOrderStore.Order?, reviewed: Review?, token: Long) {
        job = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    current(token)
                    val order = existing ?: run {
                        val terms = requireNotNull(reviewed)
                        val operation = currentCoroutineContext().job
                        synchronized(SaitoLocalState.lock) {
                            operation.ensureActive(); current(token)
                            require(TransferStore(context, wallet.publicKey).available(terms.inputs) == terms.inputs)
                            val purchase = SaitoStorePurchase.create(wallet, terms.inputs, terms.store, terms.product.nftId,
                                terms.quantity, terms.unit, BigInteger.ZERO, terms.fee, System.currentTimeMillis())
                            store.reserve(backend, purchase, terms.product)
                        }
                    }
                    val retained = store.read()
                    withContext(Dispatchers.Main.immediate) { if (revision == token) { orders = retained; focused = order.purchase.transaction.id; review = null } }
                    val observation = if (order.mayHaveSent) observe(order, token) else null
                    if (observation?.payment == null && observation?.receipt == null && order.payment == null && order.receipt == null) {
                        val funding = requireNotNull(balances.funding(backend, wallet.publicKey))
                        require(order.purchase.inputs.all { it in funding.eligible })
                        val operation = currentCoroutineContext().job
                        SaitoPeer(wallet, endpoint).use { connection ->
                            peer = connection
                            require(connection.servicePublicKey("Store") == order.purchase.store)
                            connection.submit(order.purchase.transaction) {
                                operation.ensureActive(); current(token); store.markSending(order)
                            }
                            connection.finishBroadcast()
                        }
                    }
                    val latest = observation ?: observe(order, token)
                    store.read() to latest
                }
                publishable(token); orders = result.first; focused?.let { observations = observations + (it to result.second) }
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                if (revision == token) {
                    try {
                        orders = withContext(Dispatchers.IO) { store.read() }
                        problem = if (failure is Changed) Problem.CONTEXT else Problem.CONNECTION
                    } catch (recovery: Exception) {
                        currentCoroutineContext().ensureActive(); problem = Problem.STORAGE
                    }
                }
            } finally { peer = null; if (revision == token) busy = false }
        }
    }
    fun dispose() {
        closed = true; stopReading(); revision++; cancellation?.cancel(); cancellation = null; peer?.close(); peer = null; job?.cancel()
    }
}
