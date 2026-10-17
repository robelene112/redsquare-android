package nl.davidengelen.redsquare.notifications

import android.content.Context
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import nl.davidengelen.redsquare.chat.ChatHistoryStore
import nl.davidengelen.redsquare.saito.*
import java.io.File

/** A verified wake association commits before background history, independently of publication bookkeeping. */
internal class PrivateChatWakeRoute(private val context: Context, private val owner: String,
    private val backend: SaitoBackend, private val wake: String) {
    private val prefix = "chatpush.$owner.${backend.id}.wake."
    private val name = "$prefix$wake.v1"
    private val file = EncryptedLocalFile(context, name, "redsquare.chatpush.$owner", "chatpush.$owner.")
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()
    data class Entry(val signature: String, val counterparty: String, val expiresAt: Long)

    init { require(SaitoWallet.isPublicKey(owner) && wake.matches(Regex("[0-9a-f]{64}"))) }

    fun load(): Entry? = synchronized(SaitoLocalState.lock) {
        // An uncommitted .new without a base can be retried from a newly verified gateway original.
        val contents = file.load() ?: return@synchronized null
        require(contents.version == 1.toByte())
        val body = requireNotNull(gson.fromJson(contents.text, JsonObject::class.java))
        require(body.keySet() == setOf("signature", "counterparty", "expiresAt"))
        val signature = body.get("signature")
        val counterparty = body.get("counterparty")
        val expiry = body.get("expiresAt")
        require(signature.isJsonPrimitive && signature.asJsonPrimitive.isString && signature.asString.matches(Regex("[0-9a-f]{128}")))
        require(counterparty.isJsonPrimitive && counterparty.asJsonPrimitive.isString && SaitoWallet.isPublicKey(counterparty.asString))
        require(expiry.isJsonPrimitive && expiry.asJsonPrimitive.isNumber)
        Entry(signature.asString, counterparty.asString, expiry.asBigDecimal.longValueExact().also { require(it > 0) })
    }

    fun remember(wallet: SaitoWallet, received: ReceivedPrivateChat, now: Long) = synchronized(SaitoLocalState.lock) {
        require(wallet.publicKey == owner && now >= received.message.createdAt &&
            now - received.message.createdAt <= LIFETIME)
        val expected = Entry(received.message.id, received.counterparty, received.message.createdAt + LIFETIME)
        val previous = load()
        if (previous != null) { require(previous == expected); return@synchronized }
        // Only pending associations consume this small metadata budget. Completed messages remain in history.
        for (oldWake in ownedWakes()) {
            val old = PrivateChatWakeRoute(context, owner, backend, oldWake)
            try {
                val entry = old.load()
                if (entry != null && now >= entry.expiresAt) {
                    ChatHistoryStore(context, wallet, backend, entry.counterparty).completeAlert(entry.signature)
                    old.remove()
                }
            } catch (_: Exception) {
                // Preserve unreadable association/history for recovery; it still counts against capacity.
            }
        }
        val existing = ownedWakes()
        check(wake in existing || existing.size < MAX_PENDING) { "Private wake capacity reached" }
        file.save(EncryptedLocalFile.Contents(1, JsonObject().apply {
            addProperty("signature", expected.signature); addProperty("counterparty", expected.counterparty)
            addProperty("expiresAt", expected.expiresAt)
        }.toString()))
    }

    fun remove() = synchronized(SaitoLocalState.lock) {
        AtomicFile(File(context.noBackupFilesDir, name)).delete()
        check(listOf(name, "$name.bak", "$name.new").none { File(context.noBackupFilesDir, it).exists() })
        val directory = Os.open(context.noBackupFilesDir.path, OsConstants.O_RDONLY, 0)
        try { Os.fsync(directory) } finally { Os.close(directory) }
    }

    private fun ownedWakes(): Set<String> {
        val pattern = Regex("${Regex.escape(prefix)}([0-9a-f]{64})\\.v1(?:\\.bak|\\.new)?")
        return requireNotNull(context.noBackupFilesDir.list()).mapNotNull { pattern.matchEntire(it)?.groupValues?.get(1) }.toSet()
    }

    companion object {
        const val LIFETIME = 7 * 24 * 60 * 60 * 1000L
        private const val MAX_PENDING = 1000
        data class Scope(val owner: String, val backend: SaitoBackend)

        /** Detect an already-associated inactive identity before signing any replacement-wallet request. */
        fun scope(context: Context, wake: String): Scope? = synchronized(SaitoLocalState.lock) {
            require(wake.matches(Regex("[0-9a-f]{64}")))
            val pattern = Regex("""chatpush\.([1-9A-HJ-NP-Za-km-z]{43,45})\.(owner|official)\.wake\.$wake\.v1(?:\.bak|\.new)?""")
            val scopes = requireNotNull(context.noBackupFilesDir.list()).mapNotNull { name ->
                val match = pattern.matchEntire(name) ?: return@mapNotNull null
                val owner = match.groupValues[1]
                require(SaitoWallet.isPublicKey(owner))
                Scope(owner, SaitoBackend.entries.single { it.id == match.groupValues[2] })
            }.distinct()
            check(scopes.size <= 1) { "Private wake scope unavailable" }
            scopes.singleOrNull()
        }
    }
}
