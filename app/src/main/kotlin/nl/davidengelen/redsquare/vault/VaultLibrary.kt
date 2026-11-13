package nl.davidengelen.redsquare.vault

import android.content.Context
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import nl.davidengelen.redsquare.WalletBalances
import nl.davidengelen.redsquare.saito.*
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

internal data class VaultEntry(val token: String, val name: String, val key: VaultKey?, val copy: VaultCopy?, val type: String?)
internal enum class VaultProblem { CONNECTION, SERVICE_UNAVAILABLE }
private data class VaultDiscovery(val keys: List<Pair<SaitoNft, VaultKey?>>, val page: Int, val more: Boolean, val cacheWarning: Boolean, val local: VaultCopies)
private class VaultServiceUnavailable : IOException()
internal class VaultLocalFailure(cause: Exception) : IOException(cause)

/** Native read/offline state. Cached metadata never grants current network access. */
internal class VaultLibrary(context: Context, private val wallet: SaitoWallet, private val backend: SaitoBackend,
    private val endpoint: String, private val balances: WalletBalances) {
    val disk = VaultDisk(context.applicationContext, wallet.publicKey, backend)
    var entries by mutableStateOf<List<VaultEntry>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var problem by mutableStateOf<VaultProblem?>(null); private set
    var storageWarning by mutableStateOf(false); private set
    var storedBytes by mutableLongStateOf(0L); private set
    var more by mutableStateOf(false); private set
    private var keys = emptyList<Pair<SaitoNft, VaultKey?>>()
    private var copies = emptyList<VaultCopy>()
    var page by mutableIntStateOf(0); private set
    private val generation = AtomicLong()

    suspend fun load(requestedPage: Int = page) {
        if (loading) return
        val current = generation.incrementAndGet()
        loading = true; problem = null
        require(requestedPage >= 0)
        try {
            try {
                val saved = withContext(Dispatchers.IO) { disk.copies() }
                if (generation.get() != current) return
                applyCopies(saved)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { storageWarning = true }
            val result = withContext(Dispatchers.IO) {
                val observed = balances.read(backend, wallet.publicKey) ?: throw IOException("Vault ownership unavailable")
                val holdings = observed.nfts.filter { it.type in setOf("vault", "vault-nft-key", "vault-nft-rental") }.distinctBy { it.id }.sortedBy { it.id }
                val selectedPage = requestedPage.coerceAtMost(((holdings.size - 1).coerceAtLeast(0)) / PAGE_SIZE)
                val pageHoldings = holdings.drop(selectedPage * PAGE_SIZE).take(PAGE_SIZE)
                val cached = try { disk.keys() } catch (_: Exception) { emptyMap() }
                val resolved = SaitoPeer(wallet, endpoint).use { peer ->
                    if ("vault" !in peer.services()) throw VaultServiceUnavailable()
                    val permits = Semaphore(4)
                    coroutineScope {
                        pageHoldings.map { holding -> async {
                            permits.withPermit {
                                val key = cached[holding.id]?.let { runCatching { SaitoVault.readKey(holding, it) }.getOrNull() }
                                    ?: try {
                                        SaitoVault.readKeyArchive(holding, peer.request(SaitoVault.archiveRequest(holding).toByteArray()).decodeToString())
                                    } catch (cancelled: CancellationException) { throw cancelled }
                                    catch (_: Exception) { null }
                                holding to key
                            }
                        } }.awaitAll()
                    }
                }
                currentCoroutineContext().ensureActive()
                if (generation.get() != current) throw CancellationException("Obsolete Vault discovery")
                var cacheFailed = false
                try { disk.saveKeys(resolved.mapNotNull { it.second }) } catch (_: Exception) { cacheFailed = true }
                VaultDiscovery(resolved, selectedPage, holdings.size > (selectedPage + 1) * PAGE_SIZE, cacheFailed, disk.copies())
            }
            if (generation.get() != current) return
            keys = result.keys; page = result.page; more = result.more; storageWarning = storageWarning || result.cacheWarning
            applyCopies(result.local)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            if (generation.get() == current) {
                keys = emptyList(); more = false
                problem = if (failure is VaultServiceUnavailable) VaultProblem.SERVICE_UNAVAILABLE else VaultProblem.CONNECTION
                merge()
            }
        } finally { if (generation.get() == current) loading = false }
    }

    suspend fun download(entry: VaultEntry, supplied: SaitoAccessScript? = null): VaultFile {
        entry.copy?.let {
            try { return withContext(Dispatchers.IO) { disk.read(it.fileId) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { throw VaultLocalFailure(failure) }
        }
        val selected = requireNotNull(entry.key)
        return withContext(Dispatchers.IO) {
            val current = balances.read(backend, wallet.publicKey) ?: throw IOException("Current ownership unavailable")
            val holding = current.nfts.firstOrNull { it.id == selected.holding.id && it.creator == selected.holding.creator }
                ?: throw IOException("This key is no longer held by this account")
            val key = SaitoVault.readKey(holding, selected.envelope)
            val job = currentCoroutineContext().job
            SaitoPeer(wallet, endpoint).use { peer ->
                require("vault" in peer.services())
                val response = peer.request(SaitoVault.access(wallet, key, System.currentTimeMillis(), supplied)) { job.ensureActive() }
                SaitoVault.readFile(key, response.decodeToString())
            }
        }
    }

    suspend fun keep(entry: VaultEntry, supplied: SaitoAccessScript? = null) {
        val file = download(entry, supplied)
        generation.incrementAndGet(); loading = false
        val saved = try { withContext(Dispatchers.IO) {
            currentCoroutineContext().ensureActive()
            val cleanupWarning = disk.save(file)
            disk.copies().let { it.copy(warning = it.warning || cleanupWarning) }
        } } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            try { applyCopies(withContext(Dispatchers.IO) { disk.copies() }) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { storageWarning = true }
            throw VaultLocalFailure(failure)
        }
        applyCopies(saved)
    }
    suspend fun remove(copy: VaultCopy) {
        generation.incrementAndGet(); loading = false
        val saved = withContext(Dispatchers.IO) {
            currentCoroutineContext().ensureActive(); disk.remove(copy.fileId); disk.copies()
        }
        applyCopies(saved)
    }
    suspend fun refreshLocal() {
        try { applyCopies(withContext(Dispatchers.IO) { disk.copies() }) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { storageWarning = true }
    }
    private fun applyCopies(saved: VaultCopies) {
        copies = saved.items; storedBytes = saved.bytes; storageWarning = storageWarning || saved.warning; merge()
    }
    private fun merge() {
        val owned = keys.map { (holding, key) ->
            VaultEntry(holding.id, key?.name ?: holding.id.take(12), key,
                copies.firstOrNull { it.fileId == key?.fileId }, holding.type)
        }
        val represented = owned.mapNotNull { it.key?.fileId }.toSet()
        entries = owned + copies.filter { it.fileId !in represented }.map {
            VaultEntry("file.${it.fileId}", it.name, null, it, null)
        }
    }
    companion object { private const val PAGE_SIZE = 20 }

}
