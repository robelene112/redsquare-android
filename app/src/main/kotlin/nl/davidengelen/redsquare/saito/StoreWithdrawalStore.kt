package nl.davidengelen.redsquare.saito

import android.content.Context
import com.google.gson.*

/** Paged original treasury requests. History never releases uncertain wallet-wide fee reservations. */
internal class StoreWithdrawalStore(private val context: Context, private val owner: String) {
    data class Record(val backend: SaitoBackend, val candidate: SaitoStoreWithdrawal.Candidate,
        val chain: String, val network: String, val mayHaveSent: Boolean = false, val result: SaitoStoreWithdrawal.Result? = null)
    data class History(val rows: List<Record>, val more: Boolean)
    init { require(SaitoWallet.isPublicKey(owner)) }
    private val prefix = "store-withdrawals.$owner."
    private fun file(suffix: String) = EncryptedLocalFile(context, prefix + suffix, "redsquare-store-withdrawals-v1", "store-withdrawals.")
    private fun exists(suffix: String) = requireNotNull(context.noBackupFilesDir.listFiles()).any {
        it.name in setOf(prefix + suffix, prefix + suffix + ".bak", prefix + suffix + ".new")
    }
    private fun pageName(page: Int) = if (page == 0) "rows" else "rows.$page"
    private fun pageCount(): Int {
        val additional = requireNotNull(context.noBackupFilesDir.listFiles()).mapNotNull { item ->
            Regex(Regex.escape(prefix) + "rows\\.([1-9][0-9]*)(?:\\.bak)?").matchEntire(item.name)?.groupValues?.get(1)?.toInt()
        }.toSet()
        if (!exists("rows") && !exists("guard") && !exists("pages")) { require(additional.isEmpty()); return 0 }
        val guard = requireNotNull(file("guard").load()); require(guard.version == 1.toByte() && guard.text == owner && exists("rows"))
        val count = if (!exists("pages")) 1 else requireNotNull(file("pages").load()).let {
            require(it.version == 1.toByte() && it.text.matches(Regex("[1-9][0-9]{0,9}")))
            it.text.toInt().also { value -> require(value > 0) }
        }
        val discovered = Math.addExact(additional.maxOrNull() ?: 0, 1)
        // Committed pages may never disappear. A partial, unindexed .new file is not committed work.
        require(discovered >= count && additional.size == discovered - 1 && additional.all { it in 1 until discovered })
        if (discovered > count) {
            // Recover an append interrupted after page commit and before index publication. Authenticate
            // every original and reservation before repairing metadata; never retransmit a financial RPC.
            val ids = HashSet<String>()
            val locations = HashSet<Triple<java.math.BigInteger, java.math.BigInteger, Int>>()
            for (page in 0 until discovered) for (record in physical(page)) {
                require(ids.add(record.candidate.transaction.id) && record.candidate.inputs.all {
                    locations.add(Triple(it.block, it.ordinal, it.index))
                })
            }
            file("pages").save(EncryptedLocalFile.Contents(1, discovered.toString()))
        }
        return discovered
    }
    private fun physical(page: Int): List<Record> {
        val saved = requireNotNull(file(pageName(page)).load()); require(saved.version == 1.toByte())
        val rows = JsonParser.parseString(saved.text).asJsonArray; require(rows.size() <= 100)
        val ids = HashSet<String>(); val locations = HashSet<Triple<java.math.BigInteger, java.math.BigInteger, Int>>()
        return rows.map { value ->
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
    /** Newest physical page first; existing v1 rows remain the original page without rewriting. */
    fun read(page: Int = 0): List<Record> = synchronized(SaitoLocalState.lock) {
        require(page >= 0)
        val count = pageCount()
        if (count == 0) { require(page == 0); emptyList() }
        else { require(page < count); physical(count - 1 - page) }
    }
    private fun originals(count: Int): Sequence<Record> = sequence {
        for (page in count - 1 downTo 0) for (record in physical(page).asReversed()) yield(record)
    }
    fun history(backend: SaitoBackend, page: Int = 0): History = synchronized(SaitoLocalState.lock) {
        require(page >= 0 && page <= Int.MAX_VALUE / 24)
        val rows = originals(pageCount()).filter { it.backend == backend }.drop(page * 24).take(25).toList()
        History(rows.take(24), rows.size > 24)
    }
    fun find(id: String): Record? = synchronized(SaitoLocalState.lock) { originals(pageCount()).firstOrNull { it.candidate.transaction.id == id } }
    fun hasWork(): Boolean = synchronized(SaitoLocalState.lock) { originals(pageCount()).any() }
    fun reservedInputs(): List<SaitoBalanceSnapshot.Slip> = synchronized(SaitoLocalState.lock) {
        originals(pageCount()).flatMap { it.candidate.inputs.asSequence() }.toList()
    }
    fun reserve(backend: SaitoBackend, candidate: SaitoStoreWithdrawal.Candidate, chain: String, network: String): Record = synchronized(SaitoLocalState.lock) {
        require(candidate.author == owner)
        val count = pageCount()
        require(originals(count).none { it.candidate.transaction.id == candidate.transaction.id ||
            it.backend == backend && it.candidate.quote.store == candidate.quote.store && it.candidate.quote.claimIds.any { id -> id in candidate.quote.claimIds } })
        require(TransferStore(context, owner).available(candidate.inputs) == candidate.inputs)
        val record = Record(backend, candidate, chain, network)
        val previous = if (count == 0) emptyList() else physical(count - 1)
        if (previous.size < 100 && encoded(previous + record).toByteArray().size <= 512 * 1024) {
            savePage((count - 1).coerceAtLeast(0), previous + record)
        } else {
            require(count < Int.MAX_VALUE)
            savePage(count, listOf(record))
            // Publish the page only after its original has committed. Partial creation remains fail-closed.
            file("pages").save(EncryptedLocalFile.Contents(1, (count + 1).toString()))
        }
        requireNotNull(find(candidate.transaction.id))
    }
    private data class Location(val page: Int, val rows: List<Record>, val record: Record)
    private fun locate(record: Record): Location {
        for (page in pageCount() - 1 downTo 0) {
            val rows = physical(page)
            val saved = rows.singleOrNull { it.candidate.transaction.id == record.candidate.transaction.id } ?: continue
            require(saved.backend == record.backend && saved.candidate.transaction.encoded() == record.candidate.transaction.encoded() &&
                saved.candidate.quote.store == record.candidate.quote.store && saved.chain == record.chain && saved.network == record.network)
            return Location(page, rows, saved)
        }
        error("Original withdrawal is unavailable")
    }
    fun markSending(record: Record) = synchronized(SaitoLocalState.lock) {
        val saved = locate(record)
        savePage(saved.page, saved.rows.map { if (it == saved.record) it.copy(mayHaveSent = true) else it })
    }
    fun observe(record: Record, result: SaitoStoreWithdrawal.Result) = synchronized(SaitoLocalState.lock) {
        val saved = locate(record); require(saved.record.mayHaveSent)
        require(saved.record.result?.let { it.id == result.id && (it.state != SaitoStoreWithdrawal.State.SUBMITTED || result.state == it.state) } != false)
        savePage(saved.page, saved.rows.map { if (it == saved.record) it.copy(result = result) else it })
    }
    fun discard(record: Record) = synchronized(SaitoLocalState.lock) {
        val saved = locate(record); require(!saved.record.mayHaveSent)
        savePage(saved.page, saved.rows.filterNot { it == saved.record })
    }
    private fun encoded(rows: List<Record>): String {
        return JsonArray().apply { rows.forEach { record -> add(JsonObject().apply {
            addProperty("backend", record.backend.id); addProperty("candidate", record.candidate.transaction.encoded())
            addProperty("store", record.candidate.quote.store); addProperty("chain", record.chain); addProperty("network", record.network)
            addProperty("mayHaveSent", record.mayHaveSent)
            add("result", record.result?.let { result -> JsonObject().apply { addProperty("status", result.state.name.lowercase()); addProperty("id", result.id) } } ?: JsonNull.INSTANCE)
        }) } }.toString()
    }
    private fun savePage(page: Int, rows: List<Record>) {
        require(rows.size <= 100)
        file(pageName(page)).save(EncryptedLocalFile.Contents(1, encoded(rows)))
        if (!exists("guard")) file("guard").save(EncryptedLocalFile.Contents(1, owner))
        check(physical(page).map { listOf(it.backend, it.candidate.transaction.encoded(), it.chain, it.network, it.mayHaveSent, it.result) } ==
            rows.map { listOf(it.backend, it.candidate.transaction.encoded(), it.chain, it.network, it.mayHaveSent, it.result) })
    }
}
