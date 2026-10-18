package nl.davidengelen.redsquare.notifications

import android.content.Context
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import nl.davidengelen.redsquare.chat.ChatHistoryStore
import nl.davidengelen.redsquare.chat.GroupChatStore
import nl.davidengelen.redsquare.saito.*
import java.io.File

/** A verified wake association commits before background history, independently of publication bookkeeping. */
internal class ChatWakeRoute(private val context: Context, private val owner: String,
    private val backend: SaitoBackend, private val wake: String,
    private val kind: ChatPushReference.Kind = ChatPushReference.Kind.PRIVATE) {
    private val targetKey = if (kind == ChatPushReference.Kind.PRIVATE) "counterparty" else "group"
    private val prefix = "chatpush.$owner.${backend.id}.${segment(kind)}."
    private val name = "$prefix$wake.v1"
    private val file = EncryptedLocalFile(context, name, "redsquare.chatpush.$owner", "chatpush.$owner.")
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()
    data class Entry(val signature: String, val conversation: String, val expiresAt: Long) {
        init { require(signature.matches(Regex("[0-9a-f]{128}")) && SaitoWallet.isPublicKey(conversation) && expiresAt > 0) }
    }

    init { require(SaitoWallet.isPublicKey(owner) && wake.matches(Regex("[0-9a-f]{64}"))) }

    fun load(): Entry? = synchronized(SaitoLocalState.lock) {
        // An uncommitted .new without a base can be retried from a newly verified gateway original.
        val contents = file.load() ?: return@synchronized null
        require(contents.version == 1.toByte())
        val body = requireNotNull(gson.fromJson(contents.text, JsonObject::class.java))
        require(body.keySet() == setOf("signature", targetKey, "expiresAt"))
        val signature = body.get("signature")
        val conversation = body.get(targetKey)
        val expiry = body.get("expiresAt")
        require(signature.isJsonPrimitive && signature.asJsonPrimitive.isString && signature.asString.matches(Regex("[0-9a-f]{128}")))
        require(conversation.isJsonPrimitive && conversation.asJsonPrimitive.isString && SaitoWallet.isPublicKey(conversation.asString))
        require(expiry.isJsonPrimitive && expiry.asJsonPrimitive.isNumber)
        Entry(signature.asString, conversation.asString, expiry.asBigDecimal.longValueExact().also { require(it > 0) })
    }

    fun remember(wallet: SaitoWallet, expected: Entry, now: Long) = synchronized(SaitoLocalState.lock) {
        val createdAt = expected.expiresAt - LIFETIME
        require(wallet.publicKey == owner && createdAt >= 0 && now >= createdAt && now - createdAt <= LIFETIME)
        val previous = load()
        if (previous != null) { require(previous == expected); return@synchronized }
        // Only pending associations consume this small metadata budget. Completed messages remain in history.
        for (oldWake in ownedWakes()) {
            val old = ChatWakeRoute(context, owner, backend, oldWake, kind)
            try {
                val entry = old.load()
                if (entry != null && now >= entry.expiresAt) {
                    when (kind) {
                        ChatPushReference.Kind.PRIVATE -> ChatHistoryStore(context, wallet, backend, entry.conversation).completeAlert(entry.signature)
                        ChatPushReference.Kind.GROUP -> GroupChatStore(context, wallet, backend).completeAlert(entry.conversation, entry.signature)
                    }
                    old.remove()
                }
            } catch (_: Exception) {
                // Preserve unreadable association/history for recovery; it still counts against capacity.
            }
        }
        val existing = ownedWakes()
        check(wake in existing || existing.size < MAX_PENDING) { "Chat wake capacity reached" }
        file.save(EncryptedLocalFile.Contents(1, JsonObject().apply {
            addProperty("signature", expected.signature); addProperty(targetKey, expected.conversation)
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
        // Shared Chat consent has already loaded this key before a route can be retained.
        // Optional group publication bookkeeping has an independent key lifecycle.
        private fun segment(kind: ChatPushReference.Kind) =
            if (kind == ChatPushReference.Kind.PRIVATE) "wake" else "group-wake"

        /** Detect an already-associated inactive identity before signing any replacement-wallet request. */
        fun scope(context: Context, wake: String, kind: ChatPushReference.Kind = ChatPushReference.Kind.PRIVATE): Scope? = synchronized(SaitoLocalState.lock) {
            require(wake.matches(Regex("[0-9a-f]{64}")))
            val pattern = Regex("""chatpush\.([1-9A-HJ-NP-Za-km-z]{43,45})\.(owner|official)\.${segment(kind)}\.$wake\.v1(?:\.bak|\.new)?""")
            val scopes = requireNotNull(context.noBackupFilesDir.list()).mapNotNull { name ->
                val match = pattern.matchEntire(name) ?: return@mapNotNull null
                val owner = match.groupValues[1]
                require(SaitoWallet.isPublicKey(owner))
                Scope(owner, SaitoBackend.entries.single { it.id == match.groupValues[2] })
            }.distinct()
            check(scopes.size <= 1) { "Chat wake scope unavailable" }
            scopes.singleOrNull()
        }
    }
}
