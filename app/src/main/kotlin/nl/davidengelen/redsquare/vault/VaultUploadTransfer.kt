package nl.davidengelen.redsquare.vault

import android.content.Context
import kotlinx.coroutines.*
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.AndroidWalletStore
import nl.davidengelen.redsquare.WalletBalances
import nl.davidengelen.redsquare.saito.*
import java.math.BigInteger
import java.io.IOException

internal enum class VaultSendStep { KEY, FILE, OWNERSHIP }
internal class VaultInsufficientFunds : IOException()
internal class VaultFundingChanged : IOException()
internal class VaultServiceMissing : IOException()

/** Compatible mint/upload transitions; callbacks are observed facts, not implicit mint confirmation. */
internal class VaultUploadTransfer(private val context: Context, private val wallet: SaitoWallet,
    private val backend: SaitoBackend, private val endpoint: String, private val balances: WalletBalances) {
    @Volatile private var activePeer: SaitoPeer? = null
    fun cancel() { activePeer?.close() }
    private val store = VaultUploadStore(context, wallet.publicKey, backend)
    private val reservations = TransferStore(context, wallet.publicKey)
    private val disk = VaultDisk(context, wallet.publicKey, backend)

    fun isCurrent() = AndroidWalletStore(context).loadExisting().publicKey == wallet.publicKey && AndroidBackendStore(context).load() == backend
    private fun current() { check(isCurrent()) { "The selected account or node changed" } }
    fun restorePlan(record: VaultUploadRecord, file: VaultFile): SaitoVaultMint {
        require(file.size == record.size)
        return SaitoVaultMint.create(wallet, record.candidate.inputs, record.candidate.name, record.mime, file.bytes,
            record.candidate.timestamp).also { require(it.mint.encoded() == record.candidate.transaction.encoded()) }
    }

    suspend fun prepare(file: VaultFile, guard: () -> Unit): VaultUploadRecord = withContext(Dispatchers.IO) {
        current(); currentCoroutineContext().ensureActive()
        SaitoPeer(wallet, endpoint).use { if ("vault" !in it.services()) throw VaultServiceMissing() }
        val saved = store.read()
        if (saved != null) {
            restorePlan(saved, file)
            return@withContext saved
        }
        val funding = balances.funding(backend, wallet.publicKey) ?: throw IOException("Funding unavailable")
        val available = reservations.available(funding.eligible).sortedByDescending { it.amount }
        var total = BigInteger.ZERO
        val chosen = available.takeWhile { input ->
            if (total >= SaitoVaultMint.DEPOSIT) false else { total += input.amount; true }
        }
        if (total < SaitoVaultMint.DEPOSIT) throw VaultInsufficientFunds()
        val plan = SaitoVaultMint.create(wallet, chosen, file.name, file.mime, file.bytes, System.currentTimeMillis())
        val record = VaultUploadRecord(SaitoVaultMint.readCandidate(plan.mint.encoded()), file.mime, file.size)
        currentCoroutineContext().ensureActive(); current()
        store.save(record, guard)
        record
    }

    suspend fun check(record: VaultUploadRecord, guard: () -> Unit): VaultUploadRecord = withContext(Dispatchers.IO) {
        current(); currentCoroutineContext().ensureActive()
        val snapshot = balances.read(backend, wallet.publicKey) ?: throw IOException("Ownership unavailable")
        val holding = snapshot.nfts.firstOrNull { it.id == record.candidate.nftId && it.creator == wallet.publicKey }
        val observed = record.copy(owned = holding != null)
        currentCoroutineContext().ensureActive(); guard()
        store.save(observed, guard)
        if (observed.complete && holding != null) {
            disk.saveKeys(listOf(SaitoVault.readKey(holding, record.candidate.transaction.envelope())))
            disk.discardDraft()
        }
        observed
    }

    suspend fun send(record: VaultUploadRecord, file: VaultFile, guard: () -> Unit,
        onStep: suspend (VaultSendStep) -> Unit): VaultUploadRecord = withContext(Dispatchers.IO) {
        current(); currentCoroutineContext().ensureActive()
        val plan = restorePlan(record, file)
        require(store.read()?.candidate?.transaction?.encoded() == record.candidate.transaction.encoded())
        var saved = record
        val operation = currentCoroutineContext().job
        fun admit() { operation.ensureActive(); current(); guard() }
        try { SaitoPeer(wallet, endpoint).use { peer ->
            activePeer = peer
            if ("vault" !in peer.services()) throw VaultServiceMissing()
            val snapshot = balances.read(backend, wallet.publicKey) ?: throw IOException("Ownership unavailable")
            val owned = snapshot.nfts.any { it.id == record.candidate.nftId && it.creator == wallet.publicKey }
            if (!owned) {
                val funding = balances.funding(backend, wallet.publicKey) ?: throw IOException("Funding unavailable")
                val eligible = record.candidate.inputs.all { it in funding.eligible }
                if (!eligible && !record.mayHaveSent) throw VaultFundingChanged()
                if (eligible) {
                    onStep(VaultSendStep.KEY)
                    peer.submit(plan.mint) {
                        admit()
                        require(store.read()?.candidate?.transaction?.encoded() == record.candidate.transaction.encoded())
                        saved = saved.copy(mayHaveSent = true)
                        store.save(saved, ::admit)
                        reservations.reserveMint(backend, record.candidate)
                    }
                    peer.finishBroadcast()
                }
            }
            if (!saved.uploaded) {
                onStep(VaultSendStep.FILE)
                val response = peer.requestVaultUpload(plan, ::admit)
                val result = JsonParser.parseString(response.decodeToString()).asJsonObject
                require(result.get("status")?.asString == "success" && result.get("err")?.asString.orEmpty().isEmpty())
                admit()
                saved = saved.copy(uploaded = true)
                store.save(saved, ::admit)
            }
            onStep(VaultSendStep.OWNERSHIP)
            check(saved, ::admit)
        } } finally { activePeer = null }
    }
}
