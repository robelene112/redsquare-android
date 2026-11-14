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
    val backend: SaitoBackend, private val endpoint: String, private val balances: WalletBalances,
    private val scope: CoroutineScope) {
    data class Review(val product: StoreProduct, val quantity: BigInteger, val store: String,
        val inputs: List<SaitoBalanceSnapshot.Slip>, val unit: BigInteger, val fee: BigInteger) {
        val total: BigInteger get() = unit * quantity + fee
    }
    private val context = activity.applicationContext
    private val store = StoreOrderStore(context, wallet.publicKey)
    private val selected = AndroidBackendStore(context).load()
    var orders by mutableStateOf<List<StoreOrderStore.Order>>(emptyList()); private set
    var review by mutableStateOf<Review?>(null); private set
    var focused by mutableStateOf<String?>(null); private set
    var observations by mutableStateOf<Map<String, SaitoStoreChain.Observation>>(emptyMap()); private set
    var busy by mutableStateOf(false); private set
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
    private fun current(token: Long) {
        if (closed || revision != token || activity.isFinishing || activity.isDestroyed ||
            AndroidWalletStore(context).loadExisting().publicKey != wallet.publicKey || AndroidBackendStore(context).load() != selected) throw Changed()
    }
    private fun operation(work: suspend (Long) -> Unit) {
        if (busy || closed) return
        val token = ++revision; busy = true; problem = null
        job = scope.launch {
            try { work(token) }
            catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                if (revision == token) problem = when (failure) {
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
        current(token)
        if (result.first != null) { orders = withContext(Dispatchers.IO) { store.read() }; focused = result.first?.purchase?.transaction?.id }
        else review = result.second
    }
    fun open(order: StoreOrderStore.Order) { if (!busy) { focused = order.purchase.transaction.id; review = null; problem = null } }
    fun closeReview() { if (!busy) { focused = null; review = null; problem = null } }
    fun discard(order: StoreOrderStore.Order) = operation { token ->
        val rows = withContext(Dispatchers.IO) { current(token); store.discard(order.purchase.transaction.id); store.read() }
        orders = rows; focused = null
    }
    fun check(order: StoreOrderStore.Order) = operation { token ->
        val result = withContext(Dispatchers.IO) { current(token); observe(order, token) }
        current(token); observations = observations + (order.purchase.transaction.id to result)
        orders = withContext(Dispatchers.IO) { store.read() }
    }
    private fun observe(order: StoreOrderStore.Order, token: Long): SaitoStoreChain.Observation {
        val result = requireNotNull(balances.observeStore(backend, order.purchase, order.product.creator, order.payment?.let {
            SaitoStoreChain.Payment(it.height, it.hash, it.ordinal)
        }))
        current(token)
        store.observe(order.purchase.transaction.id, result.payment?.let { StoreOrderStore.Payment(it.height, it.hash, it.ordinal) },
            result.receipt, result.receiptBlock)
        return result
    }
    fun authorize(existing: StoreOrderStore.Order? = null) {
        if (busy || closed || (existing == null && review == null) || (existing != null && existing.backend != backend)) return
        if (!activity.getSystemService(KeyguardManager::class.java).isDeviceSecure) { problem = Problem.AUTH; return }
        val reviewed = review
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
                current(token); orders = result.first; focused?.let { observations = observations + (it to result.second) }
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                if (revision == token) {
                    orders = withContext(Dispatchers.IO) { store.read() }
                    problem = if (failure is Changed) Problem.CONTEXT else Problem.CONNECTION
                }
            } finally { peer = null; if (revision == token) busy = false }
        }
    }
    fun dispose() {
        closed = true; revision++; cancellation?.cancel(); cancellation = null; peer?.close(); peer = null; job?.cancel()
    }
}
