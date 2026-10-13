package nl.davidengelen.redsquare.chat

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import nl.davidengelen.redsquare.AndroidWalletStore
import nl.davidengelen.redsquare.saito.*

/** A saved original and its attempt state survive reopening; loading never transmits. */
internal class ChatWorkStore(private val context: Context, private val wallet: SaitoWallet,
    private val backend: SaitoBackend, private val contact: String) {
    enum class Status { NONE, UNKNOWN, SUBMITTED, FORWARDED }
    data class Saved(val draft: String = "", val transaction: SaitoTransaction? = null, val status: Status = Status.NONE)
    private val file = EncryptedLocalFile(context,
        "chatwork.${backend.id}.${wallet.publicKey}.${PrivateChat.conversationId(wallet.publicKey, contact)}.v1",
        "redsquare.chatwork.v1", "chatwork.")
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()

    fun load(): Saved = synchronized(SaitoLocalState.lock) {
        val contents = file.load() ?: return@synchronized Saved()
        require(contents.version == 1.toByte())
        val record = gson.fromJson(contents.text, JsonObject::class.java)
        val draft = record.get("draft").also { require(it.isJsonPrimitive && it.asJsonPrimitive.isString) }.asString
        val transaction = record.get("transaction")?.let {
            require(it.isJsonPrimitive && it.asJsonPrimitive.isString)
            SaitoTransaction.restore(it.asString)
        }
        val status = Status.valueOf(record.get("status").asString)
        require((transaction == null) == (status == Status.NONE))
        transaction?.let { require(PrivateChat.read(wallet, it.envelope(), contact).author == wallet.publicKey) }
        Saved(draft, transaction, status)
    }

    fun saveDraft(text: String): Saved = synchronized(SaitoLocalState.lock) {
        val previous = load()
        check(previous.status != Status.UNKNOWN)
        save(previous.copy(draft = text))
    }

    fun prepare(transaction: SaitoTransaction): Saved = synchronized(SaitoLocalState.lock) {
        assertSelected()
        check(load().status != Status.UNKNOWN)
        require(PrivateChat.read(wallet, transaction.envelope(), contact).author == wallet.publicKey)
        save(Saved("", transaction, Status.UNKNOWN))
    }

    fun prepareRetry(id: String): Saved = synchronized(SaitoLocalState.lock) {
        assertSelected()
        val previous = load()
        check(previous.transaction?.id == id && previous.status in listOf(Status.UNKNOWN, Status.SUBMITTED))
        save(previous.copy(status = Status.UNKNOWN))
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
        val record = JsonObject().apply {
            addProperty("draft", saved.draft)
            saved.transaction?.let { addProperty("transaction", it.encoded()) }
            addProperty("status", saved.status.name)
        }
        file.save(EncryptedLocalFile.Contents(1, record.toString()))
        check(load().let { it.draft == saved.draft && it.transaction?.id == saved.transaction?.id && it.status == saved.status })
        return saved
    }
}
