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
    enum class Status { NONE, QUEUED, UNKNOWN, SUBMITTED, FORWARDED }
    data class Saved(val draft: String = "", val transaction: SaitoTransaction? = null, val status: Status = Status.NONE) {
        val frozen:Boolean get()=status==Status.QUEUED||status==Status.UNKNOWN
    }
    private val file = EncryptedLocalFile(context,
        "chatwork.${backend.id}.${wallet.publicKey}.${PrivateChat.conversationId(wallet.publicKey, contact)}.v1",
        "redsquare.chatwork.v1", "chatwork.")

    fun load(): Saved = synchronized(SaitoLocalState.lock) {
        val contents = file.load() ?: return@synchronized Saved()
        decode(contents, wallet, PrivateChat.conversationId(wallet.publicKey, contact))
    }

    fun saveDraft(text: String): Saved = synchronized(SaitoLocalState.lock) {
        val previous = load()
        check(!previous.frozen)
        save(previous.copy(draft = text))
    }

    fun prepare(transaction: SaitoTransaction, queued:Boolean=false): Saved = synchronized(SaitoLocalState.lock) {
        assertSelected()
        check(!load().frozen)
        require(PrivateChat.read(wallet, transaction.envelope(), contact).author == wallet.publicKey)
        save(Saved("", transaction, if(queued)Status.QUEUED else Status.UNKNOWN))
    }

    /** Cancellation and socket claim share the identity/selection lock. */
    fun cancelQueued(id:String):Saved = synchronized(SaitoLocalState.lock) {
        assertSelected()
        val previous=load()
        val candidate=requireNotNull(previous.transaction)
        check(candidate.id==id&&previous.status==Status.QUEUED)
        val text=PrivateChat.read(wallet,candidate.envelope(),contact).text
        ChatHistoryStore(context,wallet,backend,contact).discardUnsent(candidate.envelope())
        save(Saved(draft=text))
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
        val previous=load()
        check(previous.transaction?.id == id && previous.frozen)
        if(previous.status==Status.QUEUED)save(previous.copy(status=Status.UNKNOWN))
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

    companion object {
        private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()

        /** Inspect this wallet's retained work without loading or transmitting another identity's records. */
        fun hasWork(context: Context, wallet: SaitoWallet, backend: SaitoBackend): Boolean = synchronized(SaitoLocalState.lock) {
            val prefix = "chatwork.${backend.id}.${wallet.publicKey}."
            val names = requireNotNull(context.noBackupFilesDir.listFiles()).map { it.name }
                .filter { it.startsWith(prefix) }
            names.map { it.removeSuffix(".bak").removeSuffix(".new") }.distinct().any { name ->
                val group = name.removePrefix(prefix).removeSuffix(".v1")
                require(name == "$prefix$group.v1" && group.matches(Regex("[0-9a-f]{64}")))
                if ("$name.new" in names) return@any true
                val contents = EncryptedLocalFile(context, name, "redsquare.chatwork.v1", "chatwork.").load()
                // An unfinished file or unreadable record must not silently remove the replacement warning.
                val saved = decode(requireNotNull(contents), wallet, group)
                saved.draft.isNotBlank() || saved.transaction != null
            }
        }

        private fun decode(contents: EncryptedLocalFile.Contents, wallet: SaitoWallet, group: String): Saved {
            require(contents.version == 1.toByte())
            val record = gson.fromJson(contents.text, JsonObject::class.java)
            val draft = record.get("draft").also { require(it.isJsonPrimitive && it.asJsonPrimitive.isString) }.asString
            val transaction = record.get("transaction")?.let {
                require(it.isJsonPrimitive && it.asJsonPrimitive.isString)
                SaitoTransaction.restore(it.asString)
            }
            val status = Status.valueOf(record.get("status").asString)
            require((transaction == null) == (status == Status.NONE))
            transaction?.let { require(PrivateChat.recover(wallet, it.envelope(), group).message.author == wallet.publicKey) }
            return Saved(draft, transaction, status)
        }
    }
}
