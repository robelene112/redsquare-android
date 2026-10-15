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
    data class Read(val entries: List<Entry>, val incomplete: Boolean)

    fun load(): Read = synchronized(SaitoLocalState.lock) { read(originals()) }

    /** Retained authenticated originals stay seen even if their older keys cannot decrypt them now. */
    fun containsOriginal(id: String): Boolean = synchronized(SaitoLocalState.lock) {
        originals().any { authenticated(it).id == id }
    }

    private fun originals(): List<String> {
        val contents = file.load() ?: return emptyList()
        require(contents.version == 1.toByte())
        return gson.fromJson(contents.text, JsonArray::class.java).map { value ->
            require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
            value.asString
        }
    }

    private fun authenticated(envelope: String): VerifiedSaitoPayload {
        val signed = SaitoEnvelope.decodePayload(envelope)
        val participants = setOf(wallet.publicKey, counterparty)
        require(signed.author in participants && signed.recipients.containsAll(participants))
        require(signed.body.isJsonPrimitive && signed.body.asJsonPrimitive.isString)
        return signed
    }

    private fun read(envelopes: List<String>): Read {
        var incomplete = false
        val entries = envelopes.mapNotNull { envelope ->
            authenticated(envelope)
            try { Entry(envelope, PrivateChat.read(wallet, envelope, counterparty)) }
            catch (_: Exception) { incomplete = true; null }
        }
        return Read(entries, incomplete)
    }

    /** Never replace retained history with an empty or pruned server page. */
    fun save(envelopes: List<String>): Read = synchronized(SaitoLocalState.lock) {
        // New messages must decrypt and match this private group; preserved originals need not share currently imported keys.
        envelopes.forEach { PrivateChat.read(wallet, it, counterparty) }
        val retained = (originals() + envelopes).map { it to authenticated(it) }
            .distinctBy { it.second.id }.sortedWith(compareBy<Pair<String, VerifiedSaitoPayload>> { it.second.createdAt }.thenBy { it.second.id })
            .map { it.first }
        val payload = JsonArray().apply { retained.forEach(::add) }
        file.save(EncryptedLocalFile.Contents(1, payload.toString()))
        read(retained)
    }

    companion object {
        private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()
        data class Contacts(val accounts: List<String>, val unreadable: Boolean, val unavailable: Boolean)

        /** Recover counterparties from authenticated encrypted cache, including before portable contact publication. */
        fun contacts(context: Context, wallet: SaitoWallet, backend: SaitoBackend): Contacts = synchronized(SaitoLocalState.lock) {
            val prefix = "chat.${backend.id}.${wallet.publicKey}."
            val pattern = Regex("${Regex.escape(prefix)}([0-9a-f]{64})\\.v1(?:\\.bak)?")
            val groups = requireNotNull(context.noBackupFilesDir.listFiles()).mapNotNull {
                pattern.matchEntire(it.name)?.groupValues?.get(1)
            }.distinct()
            var unreadable = false
            var unavailable = false
            val accounts = groups.mapNotNull { group ->
                try {
                    val record = EncryptedLocalFile(context, "$prefix$group.v1", "redsquare.chat.v1", "chat.").load()
                        ?: return@mapNotNull null
                    require(record.version == 1.toByte())
                    val rows = gson.fromJson(record.text, JsonArray::class.java)
                    val participants = rows.mapNotNull {
                        try {
                            require(it.isJsonPrimitive && it.asJsonPrimitive.isString)
                            PrivateChat.recover(wallet, it.asString, group).counterparty
                        } catch (_: Exception) { unreadable = true; null }
                    }.distinct()
                    require(participants.size <= 1)
                    participants.singleOrNull()
                } catch (_: Exception) { unavailable = true; null }
            }.distinct()
            Contacts(accounts, unreadable, unavailable)
        }

        fun merge(first: List<Entry>, second: List<Entry>): List<Entry> = (first + second)
            .distinctBy { it.message.id }
            .sortedWith(compareBy<Entry> { it.message.createdAt }.thenBy { it.message.id })
    }
}
