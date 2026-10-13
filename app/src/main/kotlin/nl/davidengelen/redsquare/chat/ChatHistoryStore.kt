package nl.davidengelen.redsquare.chat

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.Strictness
import nl.davidengelen.redsquare.saito.*

/** Device-encrypted original ciphertexts, isolated by wallet, backend and private conversation. */
internal class ChatHistoryStore(context: Context, private val wallet: SaitoWallet,
    backend: SaitoBackend, private val counterparty: String) {
    private val file = EncryptedLocalFile(context,
        "chat.${backend.id}.${wallet.publicKey}.${PrivateChat.conversationId(wallet.publicKey, counterparty)}.v1",
        "redsquare.chat.v1", "chat.")
    data class Entry(val envelope: String, val message: PrivateChatMessage)

    fun load(): List<Entry> = synchronized(SaitoLocalState.lock) {
        val contents = file.load() ?: return@synchronized emptyList()
        require(contents.version == 1.toByte())
        gson.fromJson(contents.text, JsonArray::class.java).map { value ->
            require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
            Entry(value.asString, PrivateChat.read(wallet, value.asString, counterparty))
        }
    }

    /** Never replace retained history with an empty or pruned server page. */
    fun save(envelopes: List<String>): List<Entry> = synchronized(SaitoLocalState.lock) {
        val entries = merge(load(), envelopes.map { Entry(it, PrivateChat.read(wallet, it, counterparty)) })
        val payload = JsonArray().apply { entries.forEach { add(it.envelope) } }
        file.save(EncryptedLocalFile.Contents(1, payload.toString()))
        entries
    }

    companion object {
        private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()
        data class Contacts(val accounts: List<String>, val unreadable: Boolean)

        /** Recover counterparties from authenticated encrypted cache, including before portable contact publication. */
        fun contacts(context: Context, wallet: SaitoWallet, backend: SaitoBackend): Contacts = synchronized(SaitoLocalState.lock) {
            val prefix = "chat.${backend.id}.${wallet.publicKey}."
            val pattern = Regex("${Regex.escape(prefix)}([0-9a-f]{64})\\.v1(?:\\.bak)?")
            val groups = requireNotNull(context.noBackupFilesDir.listFiles()).mapNotNull {
                pattern.matchEntire(it.name)?.groupValues?.get(1)
            }.distinct()
            var unreadable = false
            val accounts = groups.mapNotNull { group ->
                try {
                    val record = EncryptedLocalFile(context, "$prefix$group.v1", "redsquare.chat.v1", "chat.").load()
                        ?: return@mapNotNull null
                    require(record.version == 1.toByte())
                    val rows = gson.fromJson(record.text, JsonArray::class.java)
                    val participants = rows.map {
                        require(it.isJsonPrimitive && it.asJsonPrimitive.isString)
                        PrivateChat.recover(wallet, it.asString, group).counterparty
                    }.distinct()
                    require(participants.size <= 1)
                    participants.singleOrNull()
                } catch (_: Exception) { unreadable = true; null }
            }.distinct()
            Contacts(accounts, unreadable)
        }

        fun merge(first: List<Entry>, second: List<Entry>): List<Entry> = (first + second)
            .distinctBy { it.message.id }
            .sortedWith(compareBy<Entry> { it.message.createdAt }.thenBy { it.message.id })
    }
}
