package nl.davidengelen.redsquare.notifications

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.Strictness
import nl.davidengelen.redsquare.saito.EncryptedLocalFile
import nl.davidengelen.redsquare.saito.SaitoLocalState
import java.io.File

/** Android publication receipts are independent of in-app unread activity and scoped to wallet/server. */
internal class NotificationReceiptLedger(context: Context, owner: String, backend: nl.davidengelen.redsquare.saito.SaitoBackend,
    private val signature: String, domain: Domain) {
    enum class Domain(val prefix: String) { SOCIAL("socialpush"), PRIVATE_CHAT("chatpush") }
    private val prefix = domain.prefix
    private val name = "$prefix.$owner.${backend.id}.ledger.v1"
    private val pendingFile = File(context.noBackupFilesDir, "$name.new")
    private val file = EncryptedLocalFile(context, name, "redsquare.$prefix.$owner", "$prefix.$owner.")
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()
    private data class Receipt(val signature: String, val receivedAt: Long, val published: Boolean)

    fun wasPublished(now: Long): Boolean = synchronized(SaitoLocalState.lock) {
        read(now).any { it.signature == signature && it.published }
    }

    fun hasPending(now: Long): Boolean = synchronized(SaitoLocalState.lock) {
        read(now).any { it.signature == signature && !it.published }
    }

    /** Record the recoverable attempt before calling Android. Never evict an unexpired receipt. */
    fun prepare(now: Long): Boolean = synchronized(SaitoLocalState.lock) {
        val receipts = read(now)
        val existing = receipts.singleOrNull { it.signature == signature }
        if (existing?.published == true) return@synchronized false
        if (existing == null) {
            check(receipts.size < MAX_RECEIPTS) { "Alert receipt capacity reached" }
            write(receipts + Receipt(signature, now, false))
        }
        true
    }

    /** notify returning is not a user-display receipt; its separate disk acknowledgement is not atomic. */
    fun recordPublication(now: Long) = synchronized(SaitoLocalState.lock) {
        val receipts = read(now)
        require(receipts.any { it.signature == signature })
        write(receipts.map { if (it.signature == signature) it.copy(published = true) else it })
    }

    private fun read(now: Long): List<Receipt> {
        require(now >= 0)
        val contents = file.load() ?: run { check(!pendingFile.exists()); return emptyList() }
        require(contents.version == 1.toByte())
        val rows = requireNotNull(gson.fromJson(contents.text, JsonArray::class.java))
        require(rows.size() <= MAX_RECEIPTS)
        val receipts = rows.map { value ->
            require(value.isJsonObject)
            val row = value.asJsonObject
            require(row.keySet() == setOf("signature", "receivedAt", "published"))
            val signature = row.get("signature")
            val at = row.get("receivedAt")
            val published = row.get("published")
            require(signature.isJsonPrimitive && signature.asJsonPrimitive.isString && signature.asString.matches(Regex("[0-9a-f]{128}")))
            require(at.isJsonPrimitive && at.asJsonPrimitive.isNumber)
            require(published.isJsonPrimitive && published.asJsonPrimitive.isBoolean)
            Receipt(signature.asString, at.asBigDecimal.longValueExact().also { require(it >= 0) }, published.asBoolean)
        }
        require(receipts.distinctBy { it.signature }.size == receipts.size)
        // A clock rollback conservatively retains future receipts instead of clearing deduplication.
        return receipts.filter { now < it.receivedAt || now - it.receivedAt <= RETENTION }
    }

    private fun write(receipts: List<Receipt>) {
        require(receipts.size <= MAX_RECEIPTS && receipts.distinctBy { it.signature }.size == receipts.size)
        val rows = JsonArray().apply { receipts.forEach { receipt -> add(JsonObject().apply {
            addProperty("signature", receipt.signature); addProperty("receivedAt", receipt.receivedAt)
            addProperty("published", receipt.published)
        }) } }
        file.save(EncryptedLocalFile.Contents(1, rows.toString()))
    }

    private companion object {
        const val MAX_RECEIPTS = 1000
        const val RETENTION = 7 * 24 * 60 * 60 * 1000L
    }
}
