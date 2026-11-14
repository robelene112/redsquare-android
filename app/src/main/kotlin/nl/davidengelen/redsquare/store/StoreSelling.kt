package nl.davidengelen.redsquare.store

import android.app.Activity
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import nl.davidengelen.redsquare.*
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.*

/** Foreground selling authority. Reads never submit; every retry uses the retained signed offer. */
internal class StoreSelling(private val activity: Activity, private val wallet: SaitoWallet,
    private val backend: SaitoBackend, private val selected: SaitoBackend, private val endpoint: String,
    private val balances: WalletBalances, private val scope: CoroutineScope) {
    data class Inventory(val holding: SaitoNft, val envelope: String?, val title: String)
    private val context = activity.applicationContext
    private val journal = StoreListingStore(context, wallet.publicKey)
    var inventory by mutableStateOf<List<Inventory>>(emptyList()); private set
    var records by mutableStateOf<List<StoreListingStore.Record>>(emptyList()); private set
    var reported by mutableStateOf<List<StoreSellerListing>>(emptyList()); private set
    var focused by mutableStateOf<StoreListingStore.Record?>(null); private set
    var busy by mutableStateOf(false); private set
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
        val token = ++revision; busy = true; problem = null
        job = scope.launch {
            try { work(token) }
            catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                if (publishable(token)) problem = if (failure is Changed) R.string.store_sell_context else R.string.store_sell_failed
            } finally { if (revision == token) busy = false }
        }
    }
    fun load(page: Int = inventoryPage) = operation { token ->
        val result = withContext(Dispatchers.IO) {
            current(token)
            val saved = journal.read()
            val funding = requireNotNull(balances.funding(backend, wallet.publicKey))
            val minimum = if (funding.snapshot.height > funding.genesisPeriod) funding.snapshot.height - funding.genesisPeriod + java.math.BigInteger.valueOf(2) else java.math.BigInteger.ONE
            val available = TransferStore(context, wallet.publicKey).availableKeys(funding.snapshot.nfts)
                .filter { it.positions.all { position -> position.block >= minimum } }.sortedWith(compareBy({ it.id }, { it.witnesses.first() }))
            val position = page.coerceIn(0, (available.size - 1).coerceAtLeast(0) / 24)
            val selectedHoldings = available.drop(position * 24).take(24)
            SaitoPeer(wallet, endpoint).use { connection ->
                connection.servicePublicKey("Store")
                val remote = SaitoStoreSeller.readInventory(connection.request(SaitoStoreSeller.inventoryRequest(wallet.publicKey).toByteArray()).decodeToString(), wallet.publicKey)
                val items = coroutineScope { selectedHoldings.map { holding -> async {
                    val envelope = runCatching {
                        SaitoStoreListing.readInventoryArchive(holding, connection.request(SaitoVault.archiveRequest(holding).toByteArray()).decodeToString())
                    }.getOrNull()
                    val body = envelope?.let { SaitoStoreListing.metadata(holding, it) }
                    Inventory(holding, envelope, body?.getAsJsonObject("data")?.get("filename")?.asString
                        ?: body?.get("title")?.asString ?: holding.type)
                } }.awaitAll() }
                currentCoroutineContext().ensureActive(); current(token)
                Load(saved, remote, items, position, available.size > (position + 1) * 24)
            }
        }
        if (publishable(token)) {
            records = result.saved; reported = result.remote; inventory = result.items
            inventoryPage = result.page; moreInventory = result.more
            focused = focused?.let { previous -> result.saved.firstOrNull { it.listing.transaction.id == previous.listing.transaction.id } }
        }
    }
    private data class Load(val saved: List<StoreListingStore.Record>, val remote: List<StoreSellerListing>, val items: List<Inventory>, val page: Int, val more: Boolean)
    fun prepare(item: Inventory, terms: StoreListingTerms, ready: () -> Unit) = operation { token ->
        val result = withContext(Dispatchers.IO) {
            current(token); val envelope = requireNotNull(item.envelope)
            val observed = requireNotNull(balances.funding(backend, wallet.publicKey))
            val holding = observed.snapshot.nfts.single { it.witnesses == item.holding.witnesses }
            val minimum = if (observed.snapshot.height > observed.genesisPeriod) observed.snapshot.height - observed.genesisPeriod + java.math.BigInteger.valueOf(2) else java.math.BigInteger.ONE
            require(holding.positions.all { it.block >= minimum })
            val service = SaitoPeer(wallet, endpoint).use { it.servicePublicKey("Store") }
            val listing = SaitoStoreListing.create(wallet, holding, envelope, service, terms, System.currentTimeMillis())
            val active = currentCoroutineContext().job
            synchronized(SaitoLocalState.lock) { active.ensureActive(); current(token); journal.reserve(backend, listing) }
        }
        if (publishable(token)) { focused = result; records = records + result; ready() }
    }
    fun open(record: StoreListingStore.Record) { if (!busy) { focused = record; problem = null } }
    fun closeReview() { if (!busy) focused = null }
    fun discard(record: StoreListingStore.Record) = operation { token ->
        withContext(Dispatchers.IO) { synchronized(SaitoLocalState.lock) { current(token); journal.discard(record) } }
        records = records.filterNot { it.listing.transaction.id == record.listing.transaction.id }; focused = null
    }
    fun authorize(record: StoreListingStore.Record) {
        if (closed || busy || record.backend != backend) return
        if (!activity.getSystemService(KeyguardManager::class.java).isDeviceSecure) { problem = R.string.wallet_auth_unavailable; return }
        val token = ++revision; busy = true; problem = null
        val signal = CancellationSignal().also { cancellation = it }
        try {
            val builder = BiometricPrompt.Builder(activity).setTitle(activity.getString(R.string.store_sell_authorize))
            if (Build.VERSION.SDK_INT >= 30) builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            else { @Suppress("DEPRECATION") builder.setDeviceCredentialAllowed(true) }
            builder.build().authenticate(signal, activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (!publishable(token)) return
                    cancellation = null; send(record, token)
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
        closed = true; revision++; cancellation?.cancel(); cancellation = null; job?.cancel(); peer?.close(); peer = null
    }
}
