package nl.davidengelen.redsquare.store

import android.app.Activity
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withPermit
import nl.davidengelen.redsquare.*
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.*

/** Foreground selling authority. Reads never submit; every retry uses the retained signed offer. */
internal class StoreSelling(private val activity: Activity, private val wallet: SaitoWallet,
    private val backend: SaitoBackend, private val selected: SaitoBackend, private val endpoint: String,
    private val balances: WalletBalances, private val scope: CoroutineScope) {
    data class Inventory(val holding: SaitoNft, val envelope: String?, val title: String,
        val order: StoreOrderStore.Order? = null) {
        val hasMetadata: Boolean get() = envelope != null || order?.receipt != null
    }
    private val context = activity.applicationContext
    private val journal = StoreListingStore(context, wallet.publicKey)
    private val removals = StoreDelistStore(context, wallet.publicKey)
    private val imageStore = StoreImageStore(context, wallet.publicKey)
    var images by mutableStateOf<List<StoreImageStore.Record>>(emptyList()); private set
    var focusedImage by mutableStateOf<StoreImageStore.Record?>(null); private set
    var ownedImages by mutableStateOf<Set<String>>(emptySet()); private set
    var delistRecords by mutableStateOf<List<StoreDelistStore.Record>>(emptyList()); private set
    var focusedDelist by mutableStateOf<StoreDelistStore.Record?>(null); private set
    var delistObservations by mutableStateOf<Map<String, SaitoStoreDelistChain.Observation>>(emptyMap()); private set
    var inventory by mutableStateOf<List<Inventory>>(emptyList()); private set
    var records by mutableStateOf<List<StoreListingStore.Record>>(emptyList()); private set
    var reported by mutableStateOf<List<StoreSellerListing>>(emptyList()); private set
    var focused by mutableStateOf<StoreListingStore.Record?>(null); private set
    var busy by mutableStateOf(false); private set
    var loading by mutableStateOf(false); private set
    var localReady by mutableStateOf(false); private set
    private var readJob: Job? = null
    @Volatile private var readRevision = 0L
    var problem by mutableStateOf<Int?>(null); private set
    var inventoryPage by mutableIntStateOf(0); private set
    var moreInventory by mutableStateOf(false); private set
    @Volatile private var revision = 0L
    @Volatile private var closed = false
    private var job: Job? = null
    private var cancellation: CancellationSignal? = null
    @Volatile private var peer: SaitoPeer? = null
    private class Changed : IllegalStateException()
    private fun current(token: Long) {
        if (closed || revision != token || activity.isFinishing || activity.isDestroyed ||
            AndroidWalletStore(context).loadExisting().publicKey != wallet.publicKey || AndroidBackendStore(context).load() != selected) throw Changed()
    }
    private fun publishable(token: Long) = !closed && revision == token && !activity.isDestroyed
    private fun operation(work: suspend (Long) -> Unit) {
        if (busy || closed) return
        stopLoading()
        val token = ++revision; busy = true; problem = null
        job = scope.launch {
            try { work(token) }
            catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                if (publishable(token)) problem = when (failure) { is Changed -> R.string.store_sell_context; is SaitoStoreSeller.Rejected -> R.string.store_sell_rejected; else -> R.string.store_sell_failed }
            } finally { if (revision == token) busy = false }
        }
    }
    private fun readOperation(work: suspend (Long, Long) -> Unit) {
        if (busy || closed) return
        stopLoading()
        val token = revision; val reading = ++readRevision
        loading = true; problem = null
        readJob = scope.launch {
            try { work(token, reading) }
            catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                if (publishable(token) && reading == readRevision) problem = when (failure) { is Changed -> R.string.store_sell_context; is SaitoStoreSeller.Rejected -> R.string.store_sell_rejected; else -> R.string.store_sell_failed }
            } finally { if (reading == readRevision) loading = false }
        }
    }
    fun load(page: Int = inventoryPage) = readOperation { token, reading ->
        ownedImages = emptySet(); delistObservations = emptyMap()
        val retainedState = withContext(Dispatchers.IO) { current(token); Triple(journal.read(), removals.read(), imageStore.read()) }
        val retained = retainedState.first
        if (publishable(token) && reading == readRevision) {
            records = retained; delistRecords = retainedState.second; images = retainedState.third; localReady = true
            focused = focused?.let { previous -> retained.firstOrNull { it.listing.transaction.id == previous.listing.transaction.id } }
        }
        val result = withContext(Dispatchers.IO) {
            current(token)
            val saved = retained
            val funding = requireNotNull(balances.funding(backend, wallet.publicKey))
            val minimum = if (funding.snapshot.height > funding.genesisPeriod) funding.snapshot.height - funding.genesisPeriod + java.math.BigInteger.valueOf(2) else java.math.BigInteger.ONE
            val available = TransferStore(context, wallet.publicKey).availableKeys(funding.snapshot.nfts)
                .filter { it.positions.all { position -> position.block >= minimum } }.sortedWith(compareBy({ it.id }, { it.witnesses.first() }))
            val position = page.coerceIn(0, (available.size - 1).coerceAtLeast(0) / 24)
            val selectedHoldings = available.drop(position * 24).take(24)
            val orders = StoreOrderStore(context, wallet.publicKey).read()
            SaitoPeer(wallet, endpoint).use { connection ->
                connection.servicePublicKey("Store")
                val remote = SaitoStoreSeller.readInventory(connection.request(SaitoStoreSeller.inventoryRequest(wallet.publicKey).toByteArray()).decodeToString(), wallet.publicKey)
                val permits = kotlinx.coroutines.sync.Semaphore(4)
                val items = coroutineScope { selectedHoldings.map { holding -> async { permits.withPermit {
                    val order = orders.lastOrNull { order -> order.receipt?.let { receipt ->
                        receipt.kind == SaitoStoreReceipt.Kind.FULFILLMENT && receipt.asset?.let {
                            it.id == holding.id && it.creator == holding.creator && it.owner == holding.owner
                        } == true
                    } == true }
                    val envelope = if (order != null) null else runCatching {
                        SaitoStoreListing.readInventoryArchive(holding, connection.request(SaitoVault.archiveRequest(holding).toByteArray()).decodeToString())
                    }.getOrNull()
                    val body = order?.let { SaitoStoreListing.receiptMetadata(holding, it.purchase, requireNotNull(it.receipt).encoded) }
                        ?: envelope?.let { SaitoStoreListing.metadata(holding, it) }
                    val data = body?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                    Inventory(holding, envelope, data?.get("filename")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                        ?: body?.get("title")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString ?: holding.type, order)
                } } }.awaitAll() }
                currentCoroutineContext().ensureActive(); current(token)
                Load(saved, remote, items, position, available.size > (position + 1) * 24)
            }
        }
        if (publishable(token) && reading == readRevision) {
            records = result.saved; reported = result.remote; inventory = result.items
            inventoryPage = result.page; moreInventory = result.more
            focused = focused?.let { previous -> result.saved.firstOrNull { it.listing.transaction.id == previous.listing.transaction.id } }
        }
    }
    private fun stopLoading() { readRevision++; readJob?.cancel(); readJob = null; loading = false }
    private data class Load(val saved: List<StoreListingStore.Record>, val remote: List<StoreSellerListing>, val items: List<Inventory>, val page: Int, val more: Boolean)
    fun prepare(item: Inventory, terms: StoreListingTerms, ready: () -> Unit) = operation { token ->
        val result = withContext(Dispatchers.IO) {
            current(token); require(item.hasMetadata)
            val observed = requireNotNull(balances.funding(backend, wallet.publicKey))
            val holding = observed.snapshot.nfts.single { it.witnesses == item.holding.witnesses }
            val minimum = if (observed.snapshot.height > observed.genesisPeriod) observed.snapshot.height - observed.genesisPeriod + java.math.BigInteger.valueOf(2) else java.math.BigInteger.ONE
            require(holding.positions.all { it.block >= minimum })
            val service = SaitoPeer(wallet, endpoint).use { it.servicePublicKey("Store") }
            val timestamp = System.currentTimeMillis()
            val listing = item.order?.let { order -> SaitoStoreListing.createFromReceipt(wallet, holding,
                order.purchase, requireNotNull(order.receipt).encoded, service, terms, timestamp) }
                ?: SaitoStoreListing.create(wallet, holding, requireNotNull(item.envelope), service, terms, timestamp)
            val active = currentCoroutineContext().job
            synchronized(SaitoLocalState.lock) { active.ensureActive(); current(token); journal.reserve(backend, listing) }
        }
        if (publishable(token)) { focused = result; records = records + result; ready() }
    }
    fun open(record: StoreListingStore.Record) { if (!busy) { stopLoading(); focused = record; problem = null } }
    fun closeReview() { if (!busy) { stopLoading(); focused = null } }
    fun discard(record: StoreListingStore.Record) = operation { token ->
        withContext(Dispatchers.IO) { synchronized(SaitoLocalState.lock) { current(token); journal.discard(record) } }
        records = records.filterNot { it.listing.transaction.id == record.listing.transaction.id }; focused = null
    }
    fun authorize(record: StoreListingStore.Record) {
        if (record.backend == backend) authorizeAction(R.string.store_sell_authorize) { token -> send(record, token) }
    }
    fun authorizeDelist(record: StoreDelistStore.Record) {
        if (record.backend == backend && delistObservations[record.delist.transaction.id] == null) authorizeAction(R.string.store_delist_authorize) { token -> sendDelist(record, token) }
    }
    fun authorizeImage(record: StoreImageStore.Record) {
        if (record.backend == backend && record.mint.transaction.id !in ownedImages) authorizeAction(R.string.store_image_authorize) { token -> sendImage(record, token) }
    }
    private fun authorizeAction(title: Int, authorized: (Long) -> Unit) {
        if (closed || busy) return
        if (!activity.getSystemService(KeyguardManager::class.java).isDeviceSecure) { problem = R.string.wallet_auth_unavailable; return }
        stopLoading()
        val token = ++revision; busy = true; problem = null
        val signal = CancellationSignal().also { cancellation = it }
        try {
            val builder = BiometricPrompt.Builder(activity).setTitle(activity.getString(title))
            if (Build.VERSION.SDK_INT >= 30) builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            else { @Suppress("DEPRECATION") builder.setDeviceCredentialAllowed(true) }
            builder.build().authenticate(signal, activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (!publishable(token)) return
                    cancellation = null; authorized(token)
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (!publishable(token)) return
                    cancellation = null; busy = false; problem = R.string.transfer_cancelled
                }
            })
        } catch (_: Exception) { signal.cancel(); cancellation = null; busy = false; problem = R.string.wallet_auth_unavailable }
    }
    private fun send(record: StoreListingStore.Record, token: Long) {
        job = scope.launch {
            try {
                val saved = withContext(Dispatchers.IO) {
                    current(token)
                    val exact = journal.read().single { it.listing.transaction.id == record.listing.transaction.id }
                    require(exact.backend == backend && exact.listing.transaction.encoded() == record.listing.transaction.encoded())
                    SaitoPeer(wallet, endpoint).use { connection ->
                        peer = connection
                        require(connection.servicePublicKey("Store") == exact.listing.store)
                        val remote = SaitoStoreSeller.readInventory(connection.request(SaitoStoreSeller.inventoryRequest(wallet.publicKey).toByteArray()).decodeToString(), wallet.publicKey)
                        if (remote.none { it.summary.signature == exact.listing.transaction.id }) {
                            val fresh = requireNotNull(balances.funding(backend, wallet.publicKey))
                            val minimum = if (fresh.snapshot.height > fresh.genesisPeriod) fresh.snapshot.height - fresh.genesisPeriod + java.math.BigInteger.valueOf(2) else java.math.BigInteger.ONE
                            require(fresh.snapshot.nfts.any { it.witnesses == exact.listing.holding.witnesses } && exact.listing.inputs.all { it.block >= minimum })
                            connection.submit(exact.listing.transaction) { current(token); journal.markSending(exact) }
                            connection.finishBroadcast()
                        }
                        currentCoroutineContext().ensureActive(); current(token)
                        journal.read()
                    }
                }
                if (publishable(token)) { records = saved; focused = saved.single { it.listing.transaction.id == record.listing.transaction.id } }
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                if (publishable(token)) {
                    val saved = withContext(Dispatchers.IO) { runCatching { journal.read() }.getOrNull() }
                    if (saved != null) { records = saved; focused = saved.firstOrNull { it.listing.transaction.id == record.listing.transaction.id } }
                    problem = if (failure is Changed) R.string.store_sell_context else R.string.store_sell_submission_unknown
                }
            } finally { peer = null; if (revision == token) busy = false }
        }
    }
    fun prepareImage(title: String, description: String, ready: () -> Unit) = operation { token ->
        val result = withContext(Dispatchers.IO) {
            current(token)
            val file = requireNotNull(nl.davidengelen.redsquare.vault.VaultDisk(context, wallet.publicKey, backend).draft())
            require(file.mime in setOf("image/png", "image/jpeg", "image/webp", "image/gif") && file.size <= SaitoImageMint.MAX_IMAGE_BYTES)
            val image = "data:" + file.mime + ";base64," + java.util.Base64.getEncoder().encodeToString(file.bytes)
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(file.bytes, 0, file.size, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 20_000_000L)
            SaitoPeer(wallet, endpoint).use { it.servicePublicKey("Store") }
            val funding = requireNotNull(balances.funding(backend, wallet.publicKey))
            val available = TransferStore(context, wallet.publicKey).available(funding.eligible).sortedByDescending { it.amount }
            var sum = java.math.BigInteger.ZERO
            val inputs = available.takeWhile { if (sum >= SaitoVaultMint.DEPOSIT) false else { sum += it.amount; true } }
            require(sum >= SaitoVaultMint.DEPOSIT)
            val candidate = SaitoImageMint.create(wallet, inputs, title, description, image, System.currentTimeMillis())
            val active = currentCoroutineContext().job
            synchronized(SaitoLocalState.lock) { active.ensureActive(); current(token); imageStore.reserve(backend, candidate) }
        }
        if (publishable(token)) { images = images + result; focusedImage = result; ready() }
    }
    fun openImage(record: StoreImageStore.Record) { if (!busy) { stopLoading(); focusedImage = record; problem = null } }
    fun closeImage() { if (!busy) { stopLoading(); focusedImage = null } }
    fun discardImage(record: StoreImageStore.Record) = operation { token ->
        withContext(Dispatchers.IO) { synchronized(SaitoLocalState.lock) { current(token); imageStore.discard(record) } }
        images = images.filterNot { it.mint.transaction.id == record.mint.transaction.id }; focusedImage = null
    }
    private fun observesImage(record: StoreImageStore.Record): Boolean {
        val snapshot = requireNotNull(balances.read(backend, wallet.publicKey))
        return snapshot.nfts.any { it.id == record.mint.nftId && it.creator == wallet.publicKey && it.owner == wallet.publicKey &&
            it.type == "image" && it.quantity == java.math.BigInteger.ONE && it.deposit == record.mint.deposit }
    }
    fun checkImage(record: StoreImageStore.Record) = readOperation { token, reading ->
        val id = record.mint.transaction.id; ownedImages = ownedImages - id
        val owned = withContext(Dispatchers.IO) { current(token); observesImage(record).also { current(token) } }
        if (publishable(token) && reading == readRevision && owned) ownedImages = ownedImages + id
    }
    private fun sendImage(record: StoreImageStore.Record, token: Long) {
        job = scope.launch {
            try {
                val saved = withContext(Dispatchers.IO) {
                    current(token)
                    val exact = imageStore.read().single { it.mint.transaction.id == record.mint.transaction.id }
                    require(exact.backend == backend && exact.mint.transaction.encoded() == record.mint.transaction.encoded())
                    val already = observesImage(exact)
                    if (!already) {
                        val funding = requireNotNull(balances.funding(backend, wallet.publicKey))
                        require(exact.mint.inputs.all { it in funding.eligible })
                        SaitoPeer(wallet, endpoint).use { connection ->
                            peer = connection; connection.servicePublicKey("Store")
                            connection.submit(exact.mint.transaction) { current(token); imageStore.markSending(exact) }
                            connection.finishBroadcast()
                        }
                    }
                    currentCoroutineContext().ensureActive(); current(token)
                    imageStore.read() to already
                }
                if (publishable(token)) {
                    images = saved.first; focusedImage = saved.first.single { it.mint.transaction.id == record.mint.transaction.id }
                    if (saved.second) ownedImages = ownedImages + record.mint.transaction.id
                }
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                if (publishable(token)) {
                    val saved = withContext(Dispatchers.IO) { runCatching { imageStore.read() }.getOrNull() }
                    if (saved != null) { images = saved; focusedImage = saved.firstOrNull { it.mint.transaction.id == record.mint.transaction.id } }
                    problem = if (failure is Changed) R.string.store_sell_context else R.string.store_sell_submission_unknown
                }
            } finally { peer = null; if (revision == token) busy = false }
        }
    }
    private suspend fun currentSpend(connection: SaitoPeer, signature: String, token: Long): String {
        val message = com.google.gson.JsonObject().apply {
            addProperty("request", "load-listing-spend")
            add("data", com.google.gson.JsonObject().apply { addProperty("module", "Store"); addProperty("signature", signature) })
        }
        return connection.request(SaitoTransaction.create(wallet, message, System.currentTimeMillis())) { current(token) }.decodeToString()
    }
    fun prepareDelist(item: StoreSellerListing) = operation { token ->
        val result = withContext(Dispatchers.IO) {
            current(token)
            val original = removals.read().firstOrNull { it.backend == backend && it.delist.product.signature == item.summary.signature }
            if (original != null) return@withContext original
            SaitoPeer(wallet, endpoint).use { connection ->
                val service = connection.servicePublicKey("Store")
                val product = requireNotNull(readStoreProduct(connection, item.summary.signature, item.summary))
                require(product.seller == wallet.publicKey)
                val candidate = SaitoStoreDelist.create(wallet, product, currentSpend(connection, product.signature, token), System.currentTimeMillis())
                require(candidate.store == service)
                val active = currentCoroutineContext().job
                synchronized(SaitoLocalState.lock) { active.ensureActive(); current(token); removals.reserve(backend, candidate) }
            }
        }
        if (publishable(token)) { focusedDelist = result; delistRecords = delistRecords.filterNot { it.delist.transaction.id == result.delist.transaction.id } + result }
    }
    fun openDelist(record: StoreDelistStore.Record) { if (!busy) { stopLoading(); focusedDelist = record; problem = null } }
    fun closeDelist() { if (!busy) { stopLoading(); focusedDelist = null } }
    fun discardDelist(record: StoreDelistStore.Record) = operation { token ->
        withContext(Dispatchers.IO) { synchronized(SaitoLocalState.lock) { current(token); removals.discard(record) } }
        delistRecords = delistRecords.filterNot { it.delist.transaction.id == record.delist.transaction.id }; focusedDelist = null
    }
    fun checkDelist(record: StoreDelistStore.Record) = readOperation { token, reading ->
        val id = record.delist.transaction.id; delistObservations = delistObservations - id
        val result = withContext(Dispatchers.IO) {
            current(token)
            val observation = balances.observeStoreDelist(backend, record.delist)
            val active = currentCoroutineContext().job
            synchronized(SaitoLocalState.lock) {
                active.ensureActive(); current(token); check(reading == readRevision)
                observation?.let { removals.observe(record, it) }
                removals.read() to observation
            }
        }
        if (publishable(token) && reading == readRevision) {
            delistRecords = result.first
            focusedDelist = result.first.firstOrNull { it.delist.transaction.id == id }
            result.second?.let { delistObservations = delistObservations + (id to it) }
        }
    }
    private fun sendDelist(record: StoreDelistStore.Record, token: Long) {
        job = scope.launch {
            try {
                val saved = withContext(Dispatchers.IO) {
                    current(token)
                    val exact = removals.read().single { it.delist.transaction.id == record.delist.transaction.id }
                    require(exact.backend == backend && exact.delist.transaction.encoded() == record.delist.transaction.encoded())
                    val already = balances.observeStoreDelist(backend, exact.delist)
                    if (already == null) SaitoPeer(wallet, endpoint).use { connection ->
                        peer = connection
                        val service = connection.servicePublicKey("Store")
                        require(exact.delist.store == service && exact.delist.matchesSpend(currentSpend(connection, exact.delist.product.signature, token)))
                        connection.submit(exact.delist.transaction) { current(token); removals.markSending(exact) }
                        connection.finishBroadcast()
                    }
                    currentCoroutineContext().ensureActive(); current(token)
                    if (already != null) {
                        val active = currentCoroutineContext().job
                        synchronized(SaitoLocalState.lock) { active.ensureActive(); current(token); removals.observe(exact, already) }
                    }
                    removals.read() to already
                }
                if (publishable(token)) {
                    delistRecords = saved.first; focusedDelist = saved.first.single { it.delist.transaction.id == record.delist.transaction.id }
                    saved.second?.let { delistObservations = delistObservations + (record.delist.transaction.id to it) }
                }
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                if (publishable(token)) {
                    val saved = withContext(Dispatchers.IO) { runCatching { removals.read() }.getOrNull() }
                    if (saved != null) { delistRecords = saved; focusedDelist = saved.firstOrNull { it.delist.transaction.id == record.delist.transaction.id } }
                    problem = if (failure is Changed) R.string.store_sell_context else R.string.store_sell_submission_unknown
                }
            } finally { peer = null; if (revision == token) busy = false }
        }
    }
    fun submitMarketplace(item: StoreSellerListing) = operation { token ->
        val result = withContext(Dispatchers.IO) {
            current(token)
            SaitoPeer(wallet, endpoint).use { connection ->
                connection.servicePublicKey("Store")
                val transaction = SaitoTransaction.create(wallet, SaitoStoreSeller.marketplaceRequest(item.summary.signature), System.currentTimeMillis())
                val response = connection.request(transaction) { current(token) }.decodeToString()
                SaitoStoreSeller.readSubmission(item.summary.signature, response)
                val observed = SaitoStoreSeller.readInventory(connection.request(SaitoStoreSeller.inventoryRequest(wallet.publicKey).toByteArray()).decodeToString(), wallet.publicKey)
                currentCoroutineContext().ensureActive(); current(token); observed
            }
        }
        if (publishable(token)) reported = result
    }
    fun dispose() {
        closed = true; revision++; stopLoading(); cancellation?.cancel(); cancellation = null; job?.cancel(); peer?.close(); peer = null
    }
}
