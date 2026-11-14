package nl.davidengelen.redsquare.saito

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Wallet-wide original image mint reservations. A missing guard never frees monetary inputs. */
internal class StoreImageStore(private val context: Context, private val owner: String) {
    data class Record(val backend: SaitoBackend, val mint: SaitoImageMint, val mayHaveSent: Boolean = false)
    private val prefix = "store-images.$owner."
    private fun file(suffix: String) = EncryptedLocalFile(context, prefix + suffix, "redsquare-store-images-v1", "store-images.")
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
            val mint = SaitoImageMint.restore(row.get("candidate").asString)
            require(mint.author == owner && ids.add(mint.transaction.id) &&
                mint.inputs.all { locations.add(Triple(it.block, it.ordinal, it.index)) })
            require(row.get("mayHaveSent").asJsonPrimitive.isBoolean)
            Record(SaitoBackend.entries.single { it.id == row.get("backend").asString }, mint, row.get("mayHaveSent").asBoolean)
        }
    }
    fun reserve(backend: SaitoBackend, mint: SaitoImageMint): Record = synchronized(SaitoLocalState.lock) {
        val rows = read()
        rows.firstOrNull { it.mint.transaction.id == mint.transaction.id }?.let {
            require(it.backend == backend && it.mint.transaction.encoded() == mint.transaction.encoded()); return@synchronized it
        }
        require(rows.size < 100 && mint.author == owner && TransferStore(context, owner).available(mint.inputs) == mint.inputs)
        save(rows + Record(backend, mint)); read().single { it.mint.transaction.id == mint.transaction.id }
    }
    fun markSending(record: Record) = synchronized(SaitoLocalState.lock) {
        val rows = read(); val saved = rows.single { it.mint.transaction.id == record.mint.transaction.id }
        require(saved.backend == record.backend && saved.mint.transaction.encoded() == record.mint.transaction.encoded())
        save(rows.map { if (it == saved) it.copy(mayHaveSent = true) else it })
    }
    fun discard(record: Record) = synchronized(SaitoLocalState.lock) {
        val rows = read(); val saved = rows.single { it.mint.transaction.id == record.mint.transaction.id }
        require(!saved.mayHaveSent && saved.backend == record.backend && saved.mint.transaction.encoded() == record.mint.transaction.encoded())
        save(rows.filterNot { it == saved })
    }
    private fun save(rows: List<Record>) {
        val json = JsonArray().apply { rows.forEach { record -> add(JsonObject().apply {
            addProperty("backend", record.backend.id); addProperty("candidate", record.mint.transaction.encoded())
            addProperty("mayHaveSent", record.mayHaveSent)
        }) } }
        file("rows").save(EncryptedLocalFile.Contents(1, json.toString()))
        if (!exists("guard")) file("guard").save(EncryptedLocalFile.Contents(1, owner))
        check(read().map { Triple(it.backend, it.mint.transaction.encoded(), it.mayHaveSent) } ==
            rows.map { Triple(it.backend, it.mint.transaction.encoded(), it.mayHaveSent) })
    }
}
