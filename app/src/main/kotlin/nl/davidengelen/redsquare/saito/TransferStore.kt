package nl.davidengelen.redsquare.saito

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.math.BigInteger

/** Wallet-wide reservations survive backend changes. No monetary queue or automatic send. */
class TransferStore(private val context: Context, private val author: String) {
    init { require(SaitoWallet.isPublicKey(author)) }
    data class Pending(val backend: SaitoBackend, val payment: SaitoPayment,
        val lastInclusion: SaitoPaymentChain.Inclusion? = null)
    private val name = "transfer.$author"
    private fun file(suffix: String) = EncryptedLocalFile(context, "$name.$suffix",
        "redsquare-transfers-v1", "transfer.")
    private fun exists(suffix: String) = context.noBackupFilesDir.listFiles()?.any {
        it.name == "$name.$suffix" || it.name == "$name.$suffix.bak" || it.name == "$name.$suffix.new"
    } ?: error("Transfer storage unavailable")

    fun pending(): List<Pending> = synchronized(SaitoLocalState.lock) {
        if (!exists("pending") && !exists("guard")) return@synchronized emptyList()
        // Partial creation or loss of either committed file is a recovery barrier.
        val guard = requireNotNull(file("guard").load())
        require(guard.version == 1.toByte() && guard.text == author)
        val contents = requireNotNull(file("pending").load())
        require(contents.version == 1.toByte())
        val rows = JsonParser.parseString(contents.text).asJsonArray
        require(rows.size() in 1..100)
        val ids = HashSet<String>()
        val locations = HashSet<Triple<java.math.BigInteger, java.math.BigInteger, Int>>()
        rows.map { element ->
            val row = element.asJsonObject
            require(row.keySet() == setOf("backend", "candidate") ||
                row.keySet() == setOf("backend", "candidate", "lastInclusion"))
            require(row.get("backend").asJsonPrimitive.isString && row.get("candidate").asJsonPrimitive.isString)
            val backend = requireNotNull(SaitoBackend.entries.find { it.id == row.get("backend").asString })
            val payment = SaitoPayment.restore(row.get("candidate").asString)
            require(payment.author == author && ids.add(payment.transaction.id))
            require(payment.inputs.all { locations.add(Triple(it.block, it.ordinal, it.index)) })
            Pending(backend, payment, if (row.has("lastInclusion"))
                readInclusion(row.getAsJsonObject("lastInclusion")) else null)
        }
    }

    fun available(slips: List<SaitoBalanceSnapshot.Slip>): List<SaitoBalanceSnapshot.Slip> {
        val reserved = pending().flatMap { it.payment.inputs }.map { Triple(it.block, it.ordinal, it.index) }.toSet()
        return slips.filter { Triple(it.block, it.ordinal, it.index) !in reserved }
    }

    /** Called under the shared physical-send lock. Every saved candidate remains reserved. */
    fun reserve(backend: SaitoBackend, payment: SaitoPayment) = synchronized(SaitoLocalState.lock) {
        require(payment.author == author)
        val prior = pending()
        require(prior.size < 100 && prior.none { it.payment.transaction.id == payment.transaction.id })
        val locations = prior.flatMap { it.payment.inputs }.map { Triple(it.block, it.ordinal, it.index) }.toSet()
        require(payment.inputs.none { Triple(it.block, it.ordinal, it.index) in locations })
        save(prior + Pending(backend, payment))
    }

    /** Historical observations never release inputs or authorize transmission. */
    fun recordInclusions(backend: SaitoBackend, observations: Map<String, SaitoPaymentChain.Inclusion>): List<Pending> =
        synchronized(SaitoLocalState.lock) {
            val prior = pending()
            if (observations.isEmpty()) return@synchronized prior
            require(observations.keys.all { id -> prior.any { it.backend == backend && it.payment.transaction.id == id } })
            observations.values.forEach(::validateInclusion)
            val records = prior.map { record ->
                observations[record.payment.transaction.id]?.let { record.copy(lastInclusion = it) } ?: record
            }
            save(records)
            records
        }

    private fun save(records: List<Pending>) {
        val rows = JsonArray().apply { records.forEach { record -> add(JsonObject().apply {
            addProperty("backend", record.backend.id)
            addProperty("candidate", record.payment.transaction.encoded())
            record.lastInclusion?.let { observation -> add("lastInclusion", JsonObject().apply {
                addProperty("height", observation.height.toString())
                addProperty("hash", observation.hash)
                addProperty("tipHeight", observation.tipHeight.toString())
                addProperty("tipHash", observation.tipHash)
                addProperty("tipTimestamp", observation.tipTimestamp.toString())
            }) }
        }) } }
        file("pending").save(EncryptedLocalFile.Contents(1, rows.toString()))
        if (!exists("guard")) file("guard").save(EncryptedLocalFile.Contents(1, author))
        check(pending().map { Triple(it.backend, it.payment.transaction.encoded(), it.lastInclusion) } ==
            records.map { Triple(it.backend, it.payment.transaction.encoded(), it.lastInclusion) })
    }

    private fun readInclusion(row: JsonObject): SaitoPaymentChain.Inclusion {
        require(row.keySet() == setOf("height", "hash", "tipHeight", "tipHash", "tipTimestamp"))
        require(row.entrySet().all { it.value.isJsonPrimitive && it.value.asJsonPrimitive.isString })
        fun unsigned(name: String): BigInteger {
            val text = row.get(name).asString
            require(text.matches(Regex("[1-9][0-9]{0,19}")))
            return BigInteger(text)
        }
        val timestamp = unsigned("tipTimestamp")
        require(timestamp.bitLength() <= 63) // Checked conversion also supported on Android 10.
        return SaitoPaymentChain.Inclusion(unsigned("height"), row.get("hash").asString,
            unsigned("tipHeight"), row.get("tipHash").asString, timestamp.toLong())
            .also(::validateInclusion)
    }

    private fun validateInclusion(value: SaitoPaymentChain.Inclusion) {
        require(value.height.signum() > 0 && value.height.bitLength() <= 64)
        require(value.tipHeight >= value.height && value.tipHeight.bitLength() <= 64 && value.tipTimestamp > 0)
        for (hash in listOf(value.hash, value.tipHash))
            require(hash.matches(Regex("[0-9a-f]{64}")) && hash.any { it != '0' })
    }
}

