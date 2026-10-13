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
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()
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
        val entries = (load() + envelopes.map { Entry(it, PrivateChat.read(wallet, it, counterparty)) })
            .distinctBy { it.message.id }
            .sortedWith(compareBy<Entry> { it.message.createdAt }.thenBy { it.message.id })
        val payload = JsonArray().apply { entries.forEach { add(it.envelope) } }
        file.save(EncryptedLocalFile.Contents(1, payload.toString()))
        entries
    }
}
