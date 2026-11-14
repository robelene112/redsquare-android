package nl.davidengelen.redsquare.saito

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Original seller withdrawal, retained before its first possible physical transmission. */
internal class StoreDelistStore(private val context: Context, private val owner: String) {
    data class Record(val backend: SaitoBackend, val delist: SaitoStoreDelist, val mayHaveSent: Boolean = false, val lastObservation: SaitoStoreDelistChain.Observation? = null)
    private val prefix = "store-delists.$owner."
    private fun file(suffix: String) = EncryptedLocalFile(context, prefix + suffix, "redsquare-store-delists-v1", "store-delists.")
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
            val row = value.asJsonObject; require(row.keySet().containsAll(setOf("backend", "candidate", "offer", "offerSignature", "mayHaveSent")) && (row.keySet() - setOf("backend", "candidate", "offer", "offerSignature", "mayHaveSent")).all { it in setOf("lastObservation", "provenance") })
            val product = SaitoStoreLineage.readSaved(row.get("offerSignature").asString, row.get("offer").asString,
                row.get("provenance")?.asJsonArray?.map { it.asString }.orEmpty())
            val delist = SaitoStoreDelist.restore(row.get("candidate").asString, product)
            require(delist.author == owner && ids.add(delist.transaction.id) && delist.inputs.all { locations.add(Triple(it.block, it.ordinal, it.index)) })
            require(row.get("mayHaveSent").asJsonPrimitive.isBoolean)
            Record(SaitoBackend.entries.single { it.id == row.get("backend").asString }, delist, row.get("mayHaveSent").asBoolean,
                row.get("lastObservation")?.takeUnless { it.isJsonNull }?.asJsonObject?.let { item ->
                    require(item.keySet() == setOf("height", "hash", "ordinal", "returned") && item.get("returned").asJsonPrimitive.isBoolean)
                    SaitoStoreDelistChain.Observation(item.get("height").asString.toBigInteger().also { require(it.signum() > 0 && it.bitLength() <= 64) },
                        item.get("hash").asString.also { require(it.matches(Regex("[0-9a-f]{64}"))) },
                        item.get("ordinal").asString.toBigInteger().also { require(it.signum() >= 0 && it.bitLength() <= 64) }, item.get("returned").asBoolean)
                })
        }
    }
    fun reserve(backend: SaitoBackend, delist: SaitoStoreDelist): Record = synchronized(SaitoLocalState.lock) {
        val rows = read()
        rows.firstOrNull { it.delist.transaction.id == delist.transaction.id }?.let {
            require(it.backend == backend && it.delist.transaction.encoded() == delist.transaction.encoded()); return@synchronized it
        }
        require(rows.size < 100 && delist.author == owner && TransferStore(context, owner).available(delist.inputs) == delist.inputs)
        save(rows + Record(backend, delist)); read().single { it.delist.transaction.id == delist.transaction.id }
    }
    fun markSending(record: Record) = synchronized(SaitoLocalState.lock) {
        val rows = read(); val saved = rows.single { it.delist.transaction.id == record.delist.transaction.id }
        require(saved.backend == record.backend && saved.delist.transaction.encoded() == record.delist.transaction.encoded())
        save(rows.map { if (it == saved) it.copy(mayHaveSent = true) else it })
    }
    fun observe(record: Record, observation: SaitoStoreDelistChain.Observation) = synchronized(SaitoLocalState.lock) {
        val rows = read(); val saved = rows.single { it.delist.transaction.id == record.delist.transaction.id }
        require(saved.backend == record.backend && saved.delist.transaction.encoded() == record.delist.transaction.encoded())
        save(rows.map { if (it == saved) it.copy(mayHaveSent = true, lastObservation = observation) else it })
    }
    fun discard(record: Record) = synchronized(SaitoLocalState.lock) {
        val rows = read(); val saved = rows.single { it.delist.transaction.id == record.delist.transaction.id }
        require(!saved.mayHaveSent && saved.backend == record.backend && saved.delist.transaction.encoded() == record.delist.transaction.encoded())
        save(rows.filterNot { it == saved })
    }
    private fun save(rows: List<Record>) {
        val json = JsonArray().apply { rows.forEach { record -> add(JsonObject().apply {
            addProperty("backend", record.backend.id); addProperty("candidate", record.delist.transaction.encoded())
            addProperty("offer", record.delist.product.envelope); addProperty("offerSignature", record.delist.product.signature)
            add("provenance", JsonArray().apply { record.delist.product.provenance.forEach(::add) })
            addProperty("mayHaveSent", record.mayHaveSent)
            add("lastObservation", record.lastObservation?.let { observation -> JsonObject().apply {
                addProperty("height", observation.height.toString()); addProperty("hash", observation.hash)
                addProperty("ordinal", observation.ordinal.toString()); addProperty("returned", observation.returned)
            } } ?: com.google.gson.JsonNull.INSTANCE)
        }) } }
        file("rows").save(EncryptedLocalFile.Contents(1, json.toString()))
        if (!exists("guard")) file("guard").save(EncryptedLocalFile.Contents(1, owner))
        check(read().map { listOf(it.backend, it.delist.transaction.encoded(), it.mayHaveSent, it.lastObservation) } == rows.map { listOf(it.backend, it.delist.transaction.encoded(), it.mayHaveSent, it.lastObservation) })
    }
}
