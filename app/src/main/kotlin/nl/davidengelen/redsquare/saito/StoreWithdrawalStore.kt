package nl.davidengelen.redsquare.saito

import android.content.Context
import com.google.gson.*

/** Original manual-treasury requests and wallet-wide fee reservations. Reading never submits. */
internal class StoreWithdrawalStore(private val context: Context, private val owner: String) {
    data class Record(val backend: SaitoBackend, val candidate: SaitoStoreWithdrawal.Candidate,
        val chain: String, val network: String, val mayHaveSent: Boolean = false, val result: SaitoStoreWithdrawal.Result? = null)
    init { require(SaitoWallet.isPublicKey(owner)) }
    private val prefix = "store-withdrawals.$owner."
    private fun file(suffix: String) = EncryptedLocalFile(context, prefix + suffix, "redsquare-store-withdrawals-v1", "store-withdrawals.")
    private fun exists(suffix: String) = requireNotNull(context.noBackupFilesDir.listFiles()).any {
        it.name in setOf(prefix + suffix, prefix + suffix + ".bak", prefix + suffix + ".new")
    }
    fun read(): List<Record> = synchronized(SaitoLocalState.lock) {
        if (!exists("rows") && !exists("guard")) return@synchronized emptyList()
        val guard = requireNotNull(file("guard").load()); require(guard.version == 1.toByte() && guard.text == owner)
        val saved = requireNotNull(file("rows").load()); require(saved.version == 1.toByte())
        val rows = JsonParser.parseString(saved.text).asJsonArray; require(rows.size() <= 100)
        val ids = HashSet<String>(); val locations = HashSet<Triple<java.math.BigInteger, java.math.BigInteger, Int>>()
        rows.map { value ->
            val row = value.asJsonObject
            require(row.keySet() == setOf("backend", "candidate", "store", "chain", "network", "mayHaveSent", "result"))
            val candidate = SaitoStoreWithdrawal.restore(row.get("candidate").asString, row.get("store").asString)
            require(candidate.author == owner && ids.add(candidate.transaction.id) && candidate.inputs.all { locations.add(Triple(it.block, it.ordinal, it.index)) })
            val chain = row.get("chain").asString.also { SaitoStoreCrypto.chainUrl(it) }
            val network = row.get("network").asString.also { require(it.isNotBlank() && it.length <= 120 && it.none(Char::isISOControl)) }
            require(row.get("mayHaveSent").asJsonPrimitive.isBoolean)
            val sent = row.get("mayHaveSent").asBoolean
            val result = row.get("result").takeUnless { it.isJsonNull }?.asJsonObject?.let {
                require(sent); SaitoStoreWithdrawal.readResult(it.toString())
            }
            Record(SaitoBackend.entries.single { it.id == row.get("backend").asString }, candidate, chain, network, sent, result)
        }
    }
    fun reserve(backend: SaitoBackend, candidate: SaitoStoreWithdrawal.Candidate, chain: String, network: String): Record = synchronized(SaitoLocalState.lock) {
        val rows = read(); require(candidate.author == owner && rows.size < 100)
        require(rows.none { it.backend == backend && it.candidate.quote.store == candidate.quote.store &&
            it.candidate.quote.claimIds.any { id -> id in candidate.quote.claimIds } })
        require(TransferStore(context, owner).available(candidate.inputs) == candidate.inputs)
        val record = Record(backend, candidate, chain, network)
        save(rows + record); read().single { it.candidate.transaction.id == candidate.transaction.id }
    }
    private fun original(rows: List<Record>, record: Record): Record = rows.single { it.candidate.transaction.id == record.candidate.transaction.id }.also {
        require(it.backend == record.backend && it.candidate.transaction.encoded() == record.candidate.transaction.encoded() &&
            it.candidate.quote.store == record.candidate.quote.store && it.chain == record.chain && it.network == record.network)
    }
    fun markSending(record: Record) = synchronized(SaitoLocalState.lock) {
        val rows = read(); val saved = original(rows, record)
        save(rows.map { if (it == saved) it.copy(mayHaveSent = true) else it })
    }
    fun observe(record: Record, result: SaitoStoreWithdrawal.Result) = synchronized(SaitoLocalState.lock) {
        val rows = read(); val saved = original(rows, record); require(saved.mayHaveSent)
        require(saved.result?.let { it.id == result.id && (it.state != SaitoStoreWithdrawal.State.SUBMITTED || result.state == it.state) } != false)
        save(rows.map { if (it == saved) it.copy(result = result) else it })
    }
    fun discard(record: Record) = synchronized(SaitoLocalState.lock) {
        val rows = read(); val saved = original(rows, record); require(!saved.mayHaveSent)
        save(rows.filterNot { it == saved })
    }
    private fun save(rows: List<Record>) {
        val json = JsonArray().apply { rows.forEach { record -> add(JsonObject().apply {
            addProperty("backend", record.backend.id); addProperty("candidate", record.candidate.transaction.encoded())
            addProperty("store", record.candidate.quote.store); addProperty("chain", record.chain); addProperty("network", record.network)
            addProperty("mayHaveSent", record.mayHaveSent)
            add("result", record.result?.let { result -> JsonObject().apply { addProperty("status", result.state.name.lowercase()); addProperty("id", result.id) } } ?: JsonNull.INSTANCE)
        }) } }
        file("rows").save(EncryptedLocalFile.Contents(1, json.toString()))
        if (!exists("guard")) file("guard").save(EncryptedLocalFile.Contents(1, owner))
        check(read().map { listOf(it.backend, it.candidate.transaction.encoded(), it.chain, it.network, it.mayHaveSent, it.result) } ==
            rows.map { listOf(it.backend, it.candidate.transaction.encoded(), it.chain, it.network, it.mayHaveSent, it.result) })
    }
}
