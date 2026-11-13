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
internal class VaultUploadRejected : IOException()

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
            record.candidate.timestamp, record.candidate.accessScript, record.candidate.keyType == "vault-nft-rental").also { require(it.mint.encoded() == record.candidate.transaction.encoded()) }
    }

    suspend fun prepare(file: VaultFile, rules: VaultDraftRules = VaultDraftRules(), guard: () -> Unit): VaultUploadRecord = withContext(Dispatchers.IO) {
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
        val plan = SaitoVaultMint.create(wallet, chosen, file.name, file.mime, file.bytes, System.currentTimeMillis(),
            rules.script(wallet.publicKey), rules.mode == VaultRuleMode.RENTAL)
        val record = VaultUploadRecord(SaitoVaultMint.readCandidate(plan.mint.encoded()), file.mime, file.size)
        currentCoroutineContext().ensureActive(); current()
        store.save(record, guard)
        record
    }

    suspend fun check(record: VaultUploadRecord, guard: () -> Unit): VaultUploadRecord = withContext(Dispatchers.IO) {
        current(); currentCoroutineContext().ensureActive()
        val snapshot = balances.read(backend, wallet.publicKey) ?: throw IOException("Ownership unavailable")
        val holding = snapshot.nfts.firstOrNull { it.id == record.candidate.nftId && it.creator == wallet.publicKey }
        currentCoroutineContext().ensureActive(); current(); guard()
        // Ownership is independently useful even when the subsequent file read fails.
        var observed = store.save(record.copy(owned = holding != null), guard)
        if (holding != null && !observed.uploaded) {
            val key = SaitoVault.readKey(holding, record.candidate.transaction.envelope())
            val operation = currentCoroutineContext().job
            val response = withPeer { peer ->
                peer.request(SaitoVault.access(wallet, key, System.currentTimeMillis(), record.candidate.accessScript)) {
                    operation.ensureActive(); current(); guard()
                }
            }
            val received = SaitoVault.readFile(key, response.decodeToString())
            require(received.size == record.size && received.mime == record.mime)
            // A lost RPC reply is recoverable by an authenticated read of the exact signed
            // file bound to this key. Neither check nor retrieval submits a mint/upload.
            observed = observed.copy(uploaded = true)
        }
        currentCoroutineContext().ensureActive(); current(); guard()
        observed = store.save(observed, guard)
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
        val snapshot = balances.read(backend, wallet.publicKey) ?: throw IOException("Ownership unavailable")
        val owned = snapshot.nfts.any { it.id == record.candidate.nftId && it.creator == wallet.publicKey }
        saved = store.save(saved.copy(owned = owned), ::admit)
        if (!owned) {
            val funding = balances.funding(backend, wallet.publicKey) ?: throw IOException("Funding unavailable")
            val eligible = record.candidate.inputs.all { it in funding.eligible }
            if (!eligible && !record.mayHaveSent) throw VaultFundingChanged()
            if (eligible) {
                onStep(VaultSendStep.KEY)
                withPeer { peer ->
                    peer.submit(plan.mint) {
                        admit()
                        require(store.read()?.candidate?.transaction?.encoded() == record.candidate.transaction.encoded())
                        val original = reservations.pendingMints().firstOrNull { it.candidate.transaction.id == record.candidate.transaction.id }
                        if (original == null && reservations.available(record.candidate.inputs).size != record.candidate.inputs.size)
                            throw VaultFundingChanged()
                        reservations.reserveMint(backend, record.candidate)
                        saved = store.save(saved.copy(mayHaveSent = true), ::admit)
                    }
                    // A sole connection is gracefully closed by finishBroadcast. The subsequent
                    // file RPC must own a fresh lease, while other features retain their leases.
                    peer.finishBroadcast()
                }
                saved = store.save(saved.copy(submitted = true), ::admit)
            }
        }
        if (!saved.uploaded) {
            onStep(VaultSendStep.FILE)
            val response = withPeer { peer -> peer.requestVaultUpload(plan, ::admit) }
            val result = JsonParser.parseString(response.decodeToString()).asJsonObject
            admit()
            if (result.get("status")?.asString != "success" || result.get("err")?.asString.orEmpty().isNotEmpty()) {
                store.save(saved.copy(rejected = true), ::admit)
                throw VaultUploadRejected()
            }
            saved = store.save(saved.copy(uploaded = true), ::admit)
        }
        onStep(VaultSendStep.OWNERSHIP)
        check(saved, ::admit)
    }

    suspend fun discardUnsubmitted() = withContext(Dispatchers.IO) {
        synchronized(SaitoLocalState.lock) {
            val record = store.read()
            require(record?.mayHaveSent != true && record?.uploaded != true && record?.owned != true)
            record?.let { unsent -> reservations.releaseUnsubmittedMint(backend, unsent.candidate) {
                val current = requireNotNull(store.read())
                require(current.candidate.transaction.encoded() == unsent.candidate.transaction.encoded() &&
                    !current.mayHaveSent && !current.uploaded && !current.owned)
            } }
            store.remove()
        }
        disk.discardDraft()
    }

    private suspend fun <T> withPeer(block: suspend (SaitoPeer) -> T): T =
        try { SaitoPeer(wallet, endpoint).use { peer ->
            activePeer = peer
            if ("vault" !in peer.services()) throw VaultServiceMissing()
            block(peer)
        } } finally { activePeer = null }
}
