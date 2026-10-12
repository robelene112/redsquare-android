package nl.davidengelen.redsquare.saito

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Wallet-wide reservations survive backend changes. No monetary queue or automatic send. */
class TransferStore(private val context: Context, private val author: String) {
    init { require(SaitoWallet.isPublicKey(author)) }
    data class Pending(val backend: SaitoBackend, val payment: SaitoPayment)
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
            require(row.keySet() == setOf("backend", "candidate"))
            require(row.get("backend").asJsonPrimitive.isString && row.get("candidate").asJsonPrimitive.isString)
            val backend = requireNotNull(SaitoBackend.entries.find { it.id == row.get("backend").asString })
            val payment = SaitoPayment.restore(row.get("candidate").asString)
            require(payment.author == author && ids.add(payment.transaction.id))
            require(payment.inputs.all { locations.add(Triple(it.block, it.ordinal, it.index)) })
            Pending(backend, payment)
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
        val records = prior + Pending(backend, payment)
        val rows = JsonArray().apply { records.forEach { record -> add(JsonObject().apply {
            addProperty("backend", record.backend.id)
            addProperty("candidate", record.payment.transaction.encoded())
        }) } }
        file("pending").save(EncryptedLocalFile.Contents(1, rows.toString()))
        if (!exists("guard")) file("guard").save(EncryptedLocalFile.Contents(1, author))
        check(pending().map { it.payment.transaction.encoded() } == records.map { it.payment.transaction.encoded() })
    }
}

