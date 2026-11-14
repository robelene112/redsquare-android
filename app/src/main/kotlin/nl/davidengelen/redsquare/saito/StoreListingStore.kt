package nl.davidengelen.redsquare.saito

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Wallet-wide exact offer reservations. An unreadable guard blocks new stock spending. */
internal class StoreListingStore(private val context: Context, private val owner: String) {
    data class Record(val backend: SaitoBackend, val listing: SaitoStoreListing, val mayHaveSent: Boolean = false)
    private val prefix = "store-listings.$owner."
    private fun file(suffix: String) = EncryptedLocalFile(context, prefix + suffix, "redsquare-store-listings-v1", "store-listings.")
    private fun exists(suffix: String) = requireNotNull(context.noBackupFilesDir.listFiles()).any {
        it.name in setOf(prefix + suffix, prefix + suffix + ".bak", prefix + suffix + ".new")
    }
    fun read(): List<Record> = synchronized(SaitoLocalState.lock) {
        if (!exists("rows") && !exists("guard")) return@synchronized emptyList()
        val guard = requireNotNull(file("guard").load()); require(guard.version == 1.toByte() && guard.text == owner)
        val contents = requireNotNull(file("rows").load()); require(contents.version == 1.toByte())
        val rows = JsonParser.parseString(contents.text).asJsonArray; require(rows.size() <= 100)
        val ids = HashSet<String>(); val locations = HashSet<Triple<java.math.BigInteger, java.math.BigInteger, Int>>()
        rows.map { element ->
            val row = element.asJsonObject; require(row.keySet() == setOf("backend", "candidate", "mayHaveSent"))
            val listing = SaitoStoreListing.restore(row.get("candidate").asString)
            require(listing.author == owner && ids.add(listing.transaction.id) &&
                listing.inputs.all { locations.add(Triple(it.block, it.ordinal, it.index)) })
            require(row.get("mayHaveSent").asJsonPrimitive.isBoolean)
            Record(SaitoBackend.entries.single { it.id == row.get("backend").asString }, listing, row.get("mayHaveSent").asBoolean)
        }
    }
    fun reserve(backend: SaitoBackend, listing: SaitoStoreListing): Record = synchronized(SaitoLocalState.lock) {
        val rows = read()
        rows.firstOrNull { it.listing.transaction.id == listing.transaction.id }?.let {
            require(it.backend == backend && it.listing.transaction.encoded() == listing.transaction.encoded()); return@synchronized it
        }
        require(rows.size < 100 && listing.author == owner && TransferStore(context, owner).availableKeys(listOf(listing.holding)).size == 1)
        save(rows + Record(backend, listing)); read().single { it.listing.transaction.id == listing.transaction.id }
    }
    fun markSending(record: Record) = synchronized(SaitoLocalState.lock) {
        val rows = read(); val saved = rows.single { it.listing.transaction.id == record.listing.transaction.id }
        require(saved.backend == record.backend && saved.listing.transaction.encoded() == record.listing.transaction.encoded())
        save(rows.map { if (it == saved) it.copy(mayHaveSent = true) else it })
    }
    fun discard(record: Record) = synchronized(SaitoLocalState.lock) {
        val rows = read(); val saved = rows.single { it.listing.transaction.id == record.listing.transaction.id }
        require(!saved.mayHaveSent && saved.backend == record.backend && saved.listing.transaction.encoded() == record.listing.transaction.encoded())
        save(rows.filterNot { it == saved })
    }
    private fun save(rows: List<Record>) {
        val json = JsonArray().apply { rows.forEach { record -> add(JsonObject().apply {
            addProperty("backend", record.backend.id); addProperty("candidate", record.listing.transaction.encoded())
            addProperty("mayHaveSent", record.mayHaveSent)
        }) } }
        file("rows").save(EncryptedLocalFile.Contents(1, json.toString()))
        if (!exists("guard")) file("guard").save(EncryptedLocalFile.Contents(1, owner))
        check(read().map { Triple(it.backend, it.listing.transaction.encoded(), it.mayHaveSent) } ==
            rows.map { Triple(it.backend, it.listing.transaction.encoded(), it.mayHaveSent) })
    }
}
