package nl.davidengelen.redsquare.chat

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.Strictness
import nl.davidengelen.redsquare.saito.*

/** Device-encrypted original ciphertexts, isolated by wallet, backend and private conversation. */
internal class ChatHistoryStore(context: Context, private val wallet: SaitoWallet,
    backend: SaitoBackend, private val counterparty: String) {
    private val file = EncryptedChatRecord(context,
        "chat.${backend.id}.${wallet.publicKey}.${PrivateChat.conversationId(wallet.publicKey, counterparty)}.v1",
        "redsquare.chat.v1", "chat.")
    data class Entry(val envelope: String, val message: PrivateChatMessage)
    data class Read(val entries: List<Entry>, val incomplete: Boolean)

    fun load(): Read = synchronized(SaitoLocalState.lock) { read(originals()) }

    /** Retained authenticated originals stay seen even if their older keys cannot decrypt them now. */
    fun containsOriginal(id: String): Boolean = synchronized(SaitoLocalState.lock) {
        originals().any { authenticated(it).id == id }
    }

    private data class Record(val envelopes: List<String>, val pendingAlerts: Set<String>)

    private fun stored(): Record = file.load()?.let(::parseRecord) ?: Record(emptyList(), emptySet())
    private fun originals(): List<String> = stored().envelopes

    private fun write(record: Record) {
        // Keep metadata after originals so publication changes only the tail of a multipart history.
        val payload = com.google.gson.JsonObject().apply {
            addProperty("version", 2)
            add("envelopes", JsonArray().apply { record.envelopes.forEach(::add) })
            add("pendingAlerts", JsonArray().apply { record.pendingAlerts.sorted().forEach(::add) })
        }
        file.save(EncryptedLocalFile.Contents(1, payload.toString()))
    }

    private fun mergeOriginals(existing: List<String>, additions: List<String>): List<String> =
        (existing + additions).map { it to authenticated(it) }.distinctBy { it.second.id }
            .sortedWith(compareBy<Pair<String, VerifiedSaitoPayload>> { it.second.createdAt }.thenBy { it.second.id })
            .map { it.first }

    /** Commit new history and its notification eligibility together, independently of optional alert bookkeeping. */
    fun retainIncoming(envelope: String): Boolean = synchronized(SaitoLocalState.lock) {
        val message = PrivateChat.read(wallet, envelope, counterparty)
        val previous = stored()
        if (previous.envelopes.any { authenticated(it).id == message.id })
            return@synchronized message.id in previous.pendingAlerts
        val incoming = message.author != wallet.publicKey
        write(Record(mergeOriginals(previous.envelopes, listOf(envelope)),
            previous.pendingAlerts + if (incoming) setOf(message.id) else emptySet()))
        incoming
    }

    fun completeAlert(id: String) = synchronized(SaitoLocalState.lock) {
        val previous = stored()
        if (id in previous.pendingAlerts) write(previous.copy(pendingAlerts = previous.pendingAlerts - id))
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
        val previous = stored()
        val retained = mergeOriginals(previous.envelopes, envelopes)
        write(Record(retained, previous.pendingAlerts))
        read(retained)
    }

    companion object {
        private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()
        /** Legacy arrays remain readable; the physical encrypted multipart format is unchanged. */
        private fun parseRecord(contents: EncryptedLocalFile.Contents): Record {
            require(contents.version == 1.toByte())
            val body = requireNotNull(gson.fromJson(contents.text, com.google.gson.JsonElement::class.java))
            fun strings(array: JsonArray): List<String> = array.map { value ->
                require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
                value.asString
            }
            if (body.isJsonArray) return Record(strings(body.asJsonArray), emptySet())
            require(body.isJsonObject)
            val root = body.asJsonObject
            require(root.keySet() == setOf("version", "envelopes", "pendingAlerts"))
            val version = root.get("version")
            require(version.isJsonPrimitive && version.asJsonPrimitive.isNumber && version.asBigDecimal.intValueExact() == 2)
            require(root.get("envelopes").isJsonArray && root.get("pendingAlerts").isJsonArray)
            val envelopes = strings(root.getAsJsonArray("envelopes"))
            val pending = strings(root.getAsJsonArray("pendingAlerts"))
            require(pending.distinct().size == pending.size && pending.size <= envelopes.size &&
                pending.all { it.matches(Regex("[0-9a-f]{128}")) })
            if (pending.isNotEmpty()) require(envelopes.map { SaitoEnvelope.decodePayload(it).id }.toSet().containsAll(pending))
            return Record(envelopes, pending.toSet())
        }

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
                    val record = EncryptedChatRecord(context, "$prefix$group.v1", "redsquare.chat.v1", "chat.").load()
                        ?: return@mapNotNull null
                    val rows = parseRecord(record).envelopes
                    val participants = rows.mapNotNull {
                        try {
                            PrivateChat.recover(wallet, it, group).counterparty
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
