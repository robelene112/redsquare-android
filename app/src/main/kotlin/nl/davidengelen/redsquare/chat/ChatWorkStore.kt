package nl.davidengelen.redsquare.chat

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import nl.davidengelen.redsquare.media.GiphyAttribution
import nl.davidengelen.redsquare.AndroidWalletStore
import nl.davidengelen.redsquare.saito.*

/** A saved original and its attempt state survive reopening; loading never transmits. */
internal class ChatWorkStore(private val context: Context, private val wallet: SaitoWallet,
    private val backend: SaitoBackend, val contact: String) {
    enum class Status { NONE, QUEUED, UNKNOWN, SUBMITTED, FORWARDED, AVAILABLE }
    data class Saved(val draft: String = "", val transaction: SaitoTransaction? = null, val status: Status = Status.NONE,
        val automatic:Boolean=false, val gifAttribution: GiphyAttribution? = null) {
        val frozen:Boolean get()=status==Status.QUEUED||status==Status.UNKNOWN||(automatic&&status==Status.SUBMITTED)
        val deliveryComplete:Boolean get()=status==Status.FORWARDED||status==Status.AVAILABLE
    }
    private val file = EncryptedLocalFile(context,
        "chatwork.${backend.id}.${wallet.publicKey}.${PrivateChat.conversationId(wallet.publicKey, contact)}.v1",
        "redsquare.chatwork.v1", "chatwork.")

    fun load(): Saved = synchronized(SaitoLocalState.lock) {
        val contents = file.load() ?: return@synchronized Saved()
        decode(contents, wallet, PrivateChat.conversationId(wallet.publicKey, contact))
    }

    fun saveDraft(text: String, selectedGif: GiphyAttribution? = null): Saved = synchronized(SaitoLocalState.lock) {
        val previous = load()
        check(!previous.frozen)
        val source = chatPhoto(privateMessageBody(text))?.source
        val credit = source?.let { selectedGif ?: previous.gifAttribution?.takeIf {
            chatPhoto(privateMessageBody(previous.draft))?.source == source
        } }
        save(previous.copy(draft = text, gifAttribution = credit))
    }

    fun prepare(transaction: SaitoTransaction, queued:Boolean=false): Saved = synchronized(SaitoLocalState.lock) {
        assertPublishingAllowed()
        val previous = load()
        check(!previous.frozen)
        val message = PrivateChat.read(wallet, transaction.envelope(), contact)
        require(message.author == wallet.publicKey)
        val source = chatPhoto(privateMessageBody(message.text))?.source
        val credit = previous.gifAttribution?.takeIf {
            source != null && chatPhoto(privateMessageBody(previous.draft))?.source == source
        }
        save(Saved("", transaction, if(queued)Status.QUEUED else Status.UNKNOWN, gifAttribution = credit))
    }

    /** Cancellation and socket claim share the identity/selection lock. */
    fun cancelQueued(id:String):Saved = synchronized(SaitoLocalState.lock) {
        assertSelected()
        val previous=load()
        val candidate=requireNotNull(previous.transaction)
        check(candidate.id==id&&previous.status==Status.QUEUED)
        val text=PrivateChat.read(wallet,candidate.envelope(),contact).text
        save(Saved(draft=text, gifAttribution=previous.gifAttribution))
    }

    fun enableAutomatic(id:String):Saved = synchronized(SaitoLocalState.lock) {
        assertSelected()
        val previous=load()
        check(previous.transaction?.id==id && (previous.status==Status.QUEUED||previous.automatic))
        if(previous.automatic)previous else save(previous.copy(automatic=true))
    }

    fun confirmAvailable(id:String):Saved = synchronized(SaitoLocalState.lock) {
        assertSelected()
        val previous=load()
        check(previous.transaction?.id==id&&previous.automatic&&previous.status in listOf(Status.UNKNOWN,Status.SUBMITTED))
        save(previous.copy(status=Status.AVAILABLE))
    }

    fun prepareRetry(id: String): Saved = synchronized(SaitoLocalState.lock) {
        assertPublishingAllowed()
        val previous = load()
        check(previous.transaction?.id == id && previous.status in listOf(Status.UNKNOWN, Status.SUBMITTED))
        save(previous.copy(status = Status.UNKNOWN))
    }

    fun assertSelected() {
        check(AndroidWalletStore(context).loadExisting().exportJson() == wallet.exportJson())
        check(AndroidBackendStore(context).load() == backend)
    }

    fun assertPublishingAllowed() = synchronized(SaitoLocalState.lock) {
        nl.davidengelen.redsquare.policy.PublishingPolicies.requireAllowed(context)
        assertSelected()
    }

    fun claim(id: String) = synchronized(SaitoLocalState.lock) {
        assertPublishingAllowed()
        val previous=load()
        check(previous.transaction?.id == id && (previous.frozen || (previous.automatic&&previous.status==Status.SUBMITTED)))
        if(previous.status==Status.QUEUED)save(previous.copy(status=Status.UNKNOWN))
    }

    fun record(id: String, status: Status): Saved = synchronized(SaitoLocalState.lock) {
        val previous = load()
        check(previous.transaction?.id == id && status in listOf(Status.SUBMITTED, Status.FORWARDED))
        save(previous.copy(status = status))
    }

    private fun save(saved: Saved): Saved {
        require(saved.gifAttribution == null || GiphyAttribution.fromJson(saved.gifAttribution.toJson()) == saved.gifAttribution)
        val record = JsonObject().apply {
            addProperty("draft", saved.draft)
            saved.transaction?.let { addProperty("transaction", it.encoded()) }
            addProperty("status", saved.status.name)
            addProperty("automatic",saved.automatic)
            saved.gifAttribution?.let { add("gifAttribution", it.toJson()) }
        }
        file.save(EncryptedLocalFile.Contents(1, record.toString()))
        check(load().let{it.draft==saved.draft&&it.transaction?.encoded()==saved.transaction?.encoded()&&
            it.status==saved.status&&it.automatic==saved.automatic&&it.gifAttribution==saved.gifAttribution})
        changes.value+=1
        return saved
    }

    companion object {
        private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()
        val changes=kotlinx.coroutines.flow.MutableStateFlow(0L)

        fun pendingDelivery(context:Context,wallet:SaitoWallet,backend:SaitoBackend):List<ChatWorkStore> = synchronized(SaitoLocalState.lock) {
            val prefix="chatwork.${backend.id}.${wallet.publicKey}."
            val pattern=Regex("${Regex.escape(prefix)}([0-9a-f]{64})\\.v1(?:\\.bak)?")
            val files=requireNotNull(context.noBackupFilesDir.listFiles())
            check(files.none{it.name.startsWith(prefix)&&it.name.endsWith(".new")})
            files.mapNotNull{pattern.matchEntire(it.name)?.groupValues?.get(1)}
                .distinct().mapNotNull{group->
                    val contents=EncryptedLocalFile(context,"$prefix$group.v1","redsquare.chatwork.v1","chatwork.").load()
                        ?: return@mapNotNull null
                    val saved=decode(contents,wallet,group)
                    if(saved.status!=Status.QUEUED&&(!saved.automatic||saved.deliveryComplete))return@mapNotNull null
                    val message=PrivateChat.recover(wallet,requireNotNull(saved.transaction).envelope(),group)
                    ChatWorkStore(context,wallet,backend,message.counterparty)
                }
        }

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
            val automatic=record.get("automatic")?.let{require(it.isJsonPrimitive&&it.asJsonPrimitive.isBoolean);it.asBoolean} ?: false
            require((transaction == null) == (status == Status.NONE))
            require(!automatic||transaction!=null)
            transaction?.let { require(PrivateChat.recover(wallet, it.envelope(), group).message.author == wallet.publicKey) }
            return Saved(draft, transaction, status,automatic, record.get("gifAttribution")?.let(GiphyAttribution::fromJson))
        }
    }
}
