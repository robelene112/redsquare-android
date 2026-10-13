package nl.davidengelen.redsquare.chat

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.Strictness
import nl.davidengelen.redsquare.AndroidWalletStore
import nl.davidengelen.redsquare.saito.*

/** Retain signed reaction operations and uncertain attempts in this wallet/backend/private pair only. */
internal class ChatReactionStore(private val context: Context, private val wallet: SaitoWallet,
    private val backend: SaitoBackend, private val contact: String) {
    enum class Status { NONE, UNKNOWN, SUBMITTED, FORWARDED }
    data class Saved(val events: List<PrivateChatLikeEvent> = emptyList(),
        val transaction: SaitoTransaction? = null, val status: Status = Status.NONE) {
        fun likedBy(message: PrivateChatMessage): Set<String> {
            val active = mutableSetOf<String>()
            events.filter { it.target == message.id && it.targetAuthor == message.author }.forEach {
                if (!active.add(it.author)) active.remove(it.author)
            }
            return active
        }
    }
    private val file = EncryptedLocalFile(context,
        "chatreactions.${backend.id}.${wallet.publicKey}.${PrivateChat.conversationId(wallet.publicKey, contact)}.v1",
        "redsquare.chatreactions.v1", "chatreactions.")

    fun load(): Saved = synchronized(SaitoLocalState.lock) {
        val contents = file.load() ?: return@synchronized Saved()
        require(contents.version == 1.toByte())
        val record = gson.fromJson(contents.text, JsonObject::class.java)
        val events = record.getAsJsonArray("events").map { value ->
            require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
            PrivateChatLike.read(wallet, contact, value.asString)
        }
        require(events.map { it.id }.distinct().size == events.size)
        val transaction = record.get("transaction")?.let {
            require(it.isJsonPrimitive && it.asJsonPrimitive.isString)
            SaitoTransaction.restore(it.asString)
        }
        val status = Status.valueOf(record.get("status").asString)
        require((transaction == null) == (status == Status.NONE))
        transaction?.let {
            val event = PrivateChatLike.read(wallet, contact, it.envelope())
            require(event.author == wallet.publicKey && events.any { saved -> saved.id == event.id })
        }
        Saved(events, transaction, status)
    }

    fun prepare(transaction: SaitoTransaction): Saved = synchronized(SaitoLocalState.lock) {
        assertSelected()
        val previous = load()
        check(previous.status !in listOf(Status.UNKNOWN, Status.SUBMITTED))
        val event = PrivateChatLike.read(wallet, contact, transaction.envelope())
        require(event.author == wallet.publicKey && previous.events.none { it.id == event.id })
        save(Saved(previous.events + event, transaction, Status.UNKNOWN))
    }

    fun assertSelected() {
        check(AndroidWalletStore(context).loadExisting().exportJson() == wallet.exportJson())
        check(AndroidBackendStore(context).load() == backend)
    }

    fun claim(id: String) = synchronized(SaitoLocalState.lock) {
        assertSelected()
        check(load().let { it.transaction?.id == id && it.status == Status.UNKNOWN })
    }

    fun record(id: String, status: Status): Saved = synchronized(SaitoLocalState.lock) {
        val previous = load()
        check(previous.transaction?.id == id && status in listOf(Status.SUBMITTED, Status.FORWARDED))
        save(previous.copy(status = status))
    }

    private fun save(saved: Saved): Saved {
        file.save(EncryptedLocalFile.Contents(1, JsonObject().apply {
            add("events", JsonArray().apply { saved.events.forEach { add(it.envelope) } })
            saved.transaction?.let { addProperty("transaction", it.encoded()) }
            addProperty("status", saved.status.name)
        }.toString()))
        return load().also { check(it.events == saved.events && it.transaction?.id == saved.transaction?.id && it.status == saved.status) }
    }

    companion object {
        private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()
        fun hasWork(context: Context, wallet: SaitoWallet, backend: SaitoBackend): Boolean = synchronized(SaitoLocalState.lock) {
            // Reaction records belong to the outgoing identity and are not part of wallet exports.
            requireNotNull(context.noBackupFilesDir.listFiles()).any {
                it.name.startsWith("chatreactions.${backend.id}.${wallet.publicKey}.")
            }
        }
    }
}
