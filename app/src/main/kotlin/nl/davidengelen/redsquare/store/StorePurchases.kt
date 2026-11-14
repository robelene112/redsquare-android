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
    data class CryptoReview(val product: StoreProduct, val quantity: BigInteger, val store: String, val provider: String,
        val currency: SaitoStoreCrypto.Currency, val network: String, val deposit: java.math.BigDecimal, val preparedAt: Long)
    var cryptoReview by mutableStateOf<CryptoReview?>(null); private set
    private val networks = StoreCryptoNetworks(activity.intent)
    private val context = activity.applicationContext
    private val store = StoreOrderStore(context, wallet.publicKey)
    var orders by mutableStateOf<List<StoreOrderStore.Order>>(emptyList()); private set
    var review by mutableStateOf<Review?>(null); private set
    var otherMethods by mutableStateOf<SaitoStoreCrypto.Methods?>(null); private set
    var otherMethodsFor by mutableStateOf<String?>(null); private set
    var methodsProduct by mutableStateOf<StoreProduct?>(null); private set
    var methodsQuantity by mutableStateOf("1"); private set
    private var providerKey: String? = null
    private var storeKey: String? = null
    var focused by mutableStateOf<String?>(null); private set
    var observations by mutableStateOf<Map<String, SaitoStoreChain.Observation>>(emptyMap()); private set
    var busy by mutableStateOf(false); private set
    var refreshing by mutableStateOf(false); private set
    private var readJob: Job? = null
    @Volatile private var readRevision = 0L
    private var refreshOffset = 0
    var problem by mutableStateOf<Problem?>(null); private set
    enum class Problem { CONNECTION, INSUFFICIENT, CONTEXT, STORAGE, AUTH, CANCELLED, TERMS, PROVIDER, PROVIDER_OLD, OTHER_ORDER }
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
                    is SaitoStoreCrypto.Unavailable -> Problem.PROVIDER
                    is SaitoStoreCrypto.Unsupported -> Problem.PROVIDER_OLD
                    is SaitoStoreCrypto.Conflict -> Problem.OTHER_ORDER
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
                    val order = if (id != null) rows.firstOrNull { it.id == id }
                        else rows.getOrNull(if (rows.isEmpty()) 0 else Math.floorMod(offset, rows.size))
                    selectedId = order?.id
                    val active = currentCoroutineContext().job
                    val outcome = order?.let { observe(it, token) {
                        active.ensureActive(); check(reading == readRevision)
                    } }
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
                        is SaitoStoreCrypto.Unavailable -> Problem.PROVIDER
                        is SaitoStoreCrypto.Unsupported -> Problem.PROVIDER_OLD
                        is SaitoStoreCrypto.Conflict -> Problem.OTHER_ORDER
                        is Changed -> Problem.CONTEXT
                        else -> Problem.CONNECTION
                    }
                }
            } finally { if (reading == readRevision) refreshing = false }
        }
    }
    fun stopReading() { readRevision++; readJob?.cancel(); readJob = null; refreshing = false }
    fun discoverCrypto(product: StoreProduct, quantity: String) = operation { token ->
        otherMethods = null; otherMethodsFor = null; methodsProduct = null
        val count = quantity.toBigIntegerOrNull()?.takeIf { it.signum() > 0 && it <= BigInteger("9007199254740991") } ?: throw Terms()
        val result = withContext(Dispatchers.IO) {
            current(token)
            SaitoPeer(wallet, endpoint).use { connection ->
                val services = connection.services()
                if ("Store" !in services || ("Mixin" !in services && "buysaito" !in services)) throw SaitoStoreCrypto.Unavailable()
                val provider = connection.servicePublicKey(if ("Mixin" in services) "Mixin" else "buysaito")
                val service = connection.servicePublicKey("Store")
                suspend fun request(body: com.google.gson.JsonObject) = connection.request(
                    SaitoTransaction.create(wallet, body, System.currentTimeMillis(), listOf(provider))) { current(token) }.decodeToString()
                if (!SaitoStoreCrypto.readEnabled(request(SaitoStoreCrypto.statusRequest()))) throw SaitoStoreCrypto.Unavailable()
                val methods = SaitoStoreCrypto.readMethods(request(SaitoStoreCrypto.methodsRequest()))
                if (!methods.verifiedInvoices) throw SaitoStoreCrypto.Unsupported()
                if (methods.currencies.none { product.price?.currency == "SAITO" || product.price?.currency == it.ticker }) throw SaitoStoreCrypto.Unavailable()
                current(token)
                Triple(methods, provider, service)
            }
        }
        publishable(token); otherMethods = result.first; providerKey = result.second; storeKey = result.third; otherMethodsFor = product.signature
        methodsProduct = product; methodsQuantity = count.toString()
    }
    fun prepareCrypto(product: StoreProduct, quantity: String, currency: SaitoStoreCrypto.Currency) = operation { token ->
        review = null; cryptoReview = null
        val result = withContext(Dispatchers.IO) {
            current(token)
            val count = quantity.toBigIntegerOrNull()?.takeIf { it.signum() > 0 && it <= BigInteger("9007199254740991") } ?: throw Terms()
            val prior = store.read().firstOrNull { it.backend == backend && it.product.signature == product.signature && it.receipt == null }
            if (prior != null) return@withContext prior to null
            if (otherMethodsFor != product.signature) throw Terms()
            val provider = requireNotNull(providerKey); val service = requireNotNull(storeKey)
            val methods = SaitoPeer(wallet, endpoint).use { connection ->
                require(connection.servicePublicKey("Store") == service)
                validateOffer(connection, product, count)
                paymentMethods(connection, provider, token)
            }
            val selectedCurrency = methods.currencies.single { it.ticker == currency.ticker && it.chainId == currency.chainId }
            val deposit = SaitoStoreCrypto.estimate(requireNotNull(product.price), count, selectedCurrency, methods.saitoUsd, System.currentTimeMillis())
            val network = networks.name(selectedCurrency.chainId)
            current(token)
            null to CryptoReview(product, count, service, provider, selectedCurrency, network, deposit, System.currentTimeMillis())
        }
        publishable(token)
        if (result.first != null) { orders = withContext(Dispatchers.IO) { store.read() }; focused = result.first?.id }
        else cryptoReview = result.second
        methodsProduct = null
    }
    private suspend fun validateOffer(connection: SaitoPeer, product: StoreProduct, quantity: BigInteger) {
        val target = StoreLink(backend, signature = product.signature)
        val currentOffer = SaitoStore.readOffer(connection.request(SaitoStore.listingRequest(target).toByteArray()).decodeToString(), target)
        if (currentOffer == null || currentOffer.available < quantity || !requireNotNull(product.price).matches(currentOffer.price) ||
            readStoreProduct(connection, product.signature, currentOffer) != product) throw Terms()
    }
    private suspend fun paymentMethods(connection: SaitoPeer, provider: String, token: Long): SaitoStoreCrypto.Methods {
        val services = connection.services()
        if ("Mixin" !in services && "buysaito" !in services) throw SaitoStoreCrypto.Unavailable()
        require(connection.servicePublicKey(if ("Mixin" in services) "Mixin" else "buysaito") == provider)
        suspend fun read(body: com.google.gson.JsonObject) = connection.request(
            SaitoTransaction.create(wallet, body, System.currentTimeMillis(), listOf(provider))) { current(token) }.decodeToString()
        if (!SaitoStoreCrypto.readEnabled(read(SaitoStoreCrypto.statusRequest()))) throw SaitoStoreCrypto.Unavailable()
        return SaitoStoreCrypto.readMethods(read(SaitoStoreCrypto.methodsRequest())).also { if (!it.verifiedInvoices) throw SaitoStoreCrypto.Unsupported() }
    }
    fun prepare(product: StoreProduct, quantity: String, networkFee: String) = operation { token ->
        review = null; cryptoReview = null
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
                val original = readStoreProduct(connection, product.signature, currentOffer)
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
        if (result.first != null) { orders = withContext(Dispatchers.IO) { store.read() }; focused = result.first?.id }
        else review = result.second
    }
    fun open(order: StoreOrderStore.Order) { if (!busy) { stopReading(); focused = order.id; review = null; cryptoReview = null; methodsProduct = null; problem = null } }
    fun closeReview() { if (!busy) { stopReading(); focused = null; review = null; cryptoReview = null; methodsProduct = null; problem = null } }
    fun discard(order: StoreOrderStore.Order) = operation { token ->
        val rows = withContext(Dispatchers.IO) { current(token); store.discard(order.id); store.read() }
        orders = rows; focused = null
    }
    fun check(order: StoreOrderStore.Order) { stopReading(); refresh(order.id) }
    private suspend fun observe(order: StoreOrderStore.Order, token: Long, stillOwned: () -> Unit): SaitoStoreChain.Observation? {
        var latest = store.read().single { it.id == order.id }
        if (latest.external != null) {
            val original = requireNotNull(latest.external)
            try {
                val response = SaitoPeer(wallet, endpoint).use { connection ->
                    val services = connection.services()
                    if ("Mixin" !in services && "buysaito" !in services) throw SaitoStoreCrypto.Unavailable()
                    require(connection.servicePublicKey(if ("Mixin" in services) "Mixin" else "buysaito") == original.provider)
                    val body = SaitoStoreCrypto.statusRequest(original.carrier, original.currency)
                    connection.request(SaitoTransaction.create(wallet, body, System.currentTimeMillis(), listOf(original.provider))) {
                        current(token); stillOwned()
                    }.decodeToString()
                }
                val status = com.google.gson.JsonParser.parseString(response).asJsonObject
                if (status.has("err")) throw SaitoStoreCrypto.Unavailable()
                if (status.get("store_purchase_protocol")?.asString != "1") throw SaitoStoreCrypto.Unsupported()
                val found = status.get("found").also { require(it.asJsonPrimitive.isBoolean) }.asBoolean
                if (found) synchronized(SaitoLocalState.lock) { current(token); stillOwned(); store.recordProvider(order.id, response) }
                else if (original.invoice != null) throw SaitoStoreCrypto.Unavailable()
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive(); current(token); stillOwned()
                if (original.issuance == null && original.externalHash == null) throw failure
            }
            latest = store.read().single { it.id == order.id }
            val external = requireNotNull(latest.external)
            if (external.issuance == null && external.invoice != null && external.externalHash != null) {
                val discovered = requireNotNull(balances.discoverStoreIssuance(backend, external.carrier, external.invoice,
                    external.externalHash, external.discoveryCursor))
                synchronized(SaitoLocalState.lock) { current(token); stillOwned(); store.recordDiscovery(order.id, discovered) }
                latest = store.read().single { it.id == order.id }
            }
            if (latest.fundedPayment == null) return null
        }
        val result = requireNotNull(balances.observeStore(backend, requireNotNull(latest.fundedPayment), latest.product.creator, latest.payment?.let {
            SaitoStoreChain.Payment(it.height, it.hash, it.ordinal)
        }, latest.receipt, latest.receiptBlock, latest.cursor))
        synchronized(SaitoLocalState.lock) {
            current(token); stillOwned()
            store.observe(order.id, result)
        }
        return result
    }
    fun authorize(existing: StoreOrderStore.Order? = null) {
        if (busy || closed || (existing == null && review == null && cryptoReview == null) || (existing != null && existing.backend != backend)) return
        if (!activity.getSystemService(KeyguardManager::class.java).isDeviceSecure) { problem = Problem.AUTH; return }
        val reviewed = review
        val externalReview = cryptoReview
        stopReading()
        val token = ++revision; busy = true; problem = null
        val signal = CancellationSignal().also { cancellation = it }
        try {
            val builder = BiometricPrompt.Builder(activity).setTitle(activity.getString(
                if (externalReview != null || existing?.external != null) nl.davidengelen.redsquare.R.string.store_crypto_auth
                else nl.davidengelen.redsquare.R.string.store_purchase_auth))
            if (Build.VERSION.SDK_INT >= 30) builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            else { @Suppress("DEPRECATION") builder.setDeviceCredentialAllowed(true) }
            builder.build().authenticate(signal, activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (revision != token || closed) return
                    cancellation = null
                    if (externalReview != null || existing?.external != null) sendExternal(existing, externalReview, token)
                    else send(existing, reviewed, token)
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (revision != token || closed) return
                    cancellation = null; busy = false
                    problem = if (errorCode in listOf(BiometricPrompt.BIOMETRIC_ERROR_CANCELED, BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED)) Problem.CANCELLED else Problem.AUTH
                }
            })
        } catch (_: Exception) { signal.cancel(); cancellation = null; busy = false; problem = Problem.AUTH }
    }
    fun hidePayment(order: StoreOrderStore.Order, hidden: Boolean = true) = operation { token ->
        orders = withContext(Dispatchers.IO) {
            val active = currentCoroutineContext().job
            synchronized(SaitoLocalState.lock) { active.ensureActive(); current(token); store.hideInstructions(order.id, hidden); store.read() }
        }
    }
    private fun sendExternal(existing: StoreOrderStore.Order?, reviewed: CryptoReview?, token: Long) {
        job = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    current(token)
                    val active = currentCoroutineContext().job
                    var order = existing?.let { store.read().single { row -> row.id == it.id && row.backend == backend } }
                    order?.takeIf { it.mayHaveSent }?.let { observe(it, token) { active.ensureActive() } }
                    val existingId = order?.id
                    if (existingId != null) order = store.read().single { it.id == existingId }
                    if (order?.external?.invoice == null) {
                        val provider = order?.external?.provider ?: requireNotNull(reviewed).provider
                        val service = order?.external?.carrier?.store ?: requireNotNull(reviewed).store
                        SaitoPeer(wallet, endpoint).use { connection ->
                            peer = connection
                            require(connection.servicePublicKey("Store") == service)
                            val methods = paymentMethods(connection, provider, token)
                            val product = order?.product ?: requireNotNull(reviewed).product
                            val count = order?.external?.carrier?.quantity ?: requireNotNull(reviewed).quantity
                            validateOffer(connection, product, count)
                            val selectedCurrency = order?.external?.currency ?: requireNotNull(reviewed).currency
                            val currentCurrency = methods.currencies.single { it.ticker == selectedCurrency.ticker && it.chainId == selectedCurrency.chainId }
                            val deposit = SaitoStoreCrypto.estimate(requireNotNull(product.price), count, currentCurrency, methods.saitoUsd, System.currentTimeMillis())
                            if (order == null) {
                                val terms = requireNotNull(reviewed)
                                if (System.currentTimeMillis() - terms.preparedAt > 300_000) throw Terms()
                                order = synchronized(SaitoLocalState.lock) {
                                    active.ensureActive(); current(token)
                                    val prior = store.read().firstOrNull { it.backend == backend && it.product.signature == product.signature && it.receipt == null }
                                    if (prior != null) throw Terms()
                                    val carrier = SaitoStoreCarrier.create(wallet, service, product.nftId, count, requireNotNull(product.price), System.currentTimeMillis())
                                    store.reserveExternal(backend, carrier, product, currentCurrency, provider, terms.network)
                                }
                            }
                            val retained = requireNotNull(order)
                            val rows = store.read()
                            withContext(Dispatchers.Main.immediate) {
                                if (revision == token) { orders = rows; focused = retained.id; cryptoReview = null }
                            }
                            val external = requireNotNull(retained.external)
                            val body = SaitoStoreCrypto.reservationRequest(external.carrier, external.currency, deposit)
                            val response = connection.request(SaitoTransaction.create(wallet, body, System.currentTimeMillis(), listOf(provider))) {
                                synchronized(SaitoLocalState.lock) { active.ensureActive(); current(token); store.markSending(retained) }
                            }.decodeToString()
                            synchronized(SaitoLocalState.lock) { active.ensureActive(); current(token); store.recordProvider(retained.id, response) }
                        }
                    }
                    val retained = requireNotNull(order)
                    store.read() to retained.id
                }
                publishable(token); orders = result.first; focused = result.second; cryptoReview = null
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                if (revision == token) {
                    try { orders = withContext(Dispatchers.IO) { store.read() } }
                    catch (recovery: Exception) { currentCoroutineContext().ensureActive(); problem = Problem.STORAGE; return@launch }
                    problem = when (failure) {
                        is Changed -> Problem.CONTEXT; is Terms -> Problem.TERMS
                        is SaitoStoreCrypto.Unavailable -> Problem.PROVIDER
                        is SaitoStoreCrypto.Unsupported -> Problem.PROVIDER_OLD
                        is SaitoStoreCrypto.Conflict -> Problem.OTHER_ORDER
                        is StoreOrderStore.StorageUnavailable -> Problem.STORAGE
                        else -> Problem.CONNECTION
                    }
                }
            } finally { peer = null; if (revision == token) busy = false }
        }
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
                    withContext(Dispatchers.Main.immediate) { if (revision == token) { orders = retained; focused = order.id; review = null } }
                    val sending = currentCoroutineContext().job
                    val observation = if (order.mayHaveSent) observe(order, token) { sending.ensureActive() } else null
                    if (observation?.payment == null && observation?.receipt == null && order.payment == null && order.receipt == null) {
                        val funding = requireNotNull(balances.funding(backend, wallet.publicKey))
                        require(requireNotNull(order.purchase).inputs.all { it in funding.eligible })
                        val operation = currentCoroutineContext().job
                        SaitoPeer(wallet, endpoint).use { connection ->
                            peer = connection
                            require(connection.servicePublicKey("Store") == requireNotNull(order.purchase).store)
                            connection.submit(requireNotNull(order.purchase).transaction) {
                                operation.ensureActive(); current(token); store.markSending(order)
                            }
                            connection.finishBroadcast()
                        }
                    }
                    val latest = observation ?: observe(order, token) { sending.ensureActive() }
                    store.read() to requireNotNull(latest)
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
