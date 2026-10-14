package nl.davidengelen.redsquare.chat

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import nl.davidengelen.redsquare.AndroidWalletStore
import nl.davidengelen.redsquare.saito.*

/** Encrypted original group state. Opening never transmits a retained attempt. */
internal class GroupChatStore(private val context: Context, private val wallet: SaitoWallet, private val backend: SaitoBackend) {
    enum class Status { UNKNOWN, SUBMITTED, FORWARDED }
    data class Entry(val group: NamedChatGroup, val candidate: SaitoTransaction, val status: Status)
    private val prefix = "groupchat.${backend.id}.${wallet.publicKey}."

    private fun file(id: String): EncryptedLocalFile {
        require(SaitoWallet.isPublicKey(id) && id != wallet.publicKey)
        return EncryptedLocalFile(context,"$prefix$id.v1","redsquare.groupchat.v1","groupchat.")
    }

    fun entries(): List<Entry> = synchronized(SaitoLocalState.lock) {
        val pattern = Regex("${Regex.escape(prefix)}([1-9A-HJ-NP-Za-km-z]{43,45})\\.v1(?:\\.bak)?")
        requireNotNull(context.noBackupFilesDir.listFiles()).mapNotNull { pattern.matchEntire(it.name)?.groupValues?.get(1) }
            .distinct().map { requireNotNull(load(it)) }
    }

    fun load(id: String): Entry? = synchronized(SaitoLocalState.lock) {
        val contents = file(id).load() ?: return@synchronized null
        require(contents.version == 1.toByte())
        val record = gson.fromJson(contents.text,JsonObject::class.java)
        val creation = record.get("creation").asString
        val group = GroupChat.readCreation(creation)
        require(group.id == id && wallet.publicKey in group.memberRoles)
        val candidate = SaitoTransaction.restore(record.get("candidate").asString)
        require(candidate.envelope() == creation && SaitoEnvelope.decode(creation).author == wallet.publicKey)
        Entry(group,candidate,Status.valueOf(record.get("status").asString))
    }

    fun prepareCreation(candidate: SaitoTransaction): Entry = synchronized(SaitoLocalState.lock) {
        assertSelected()
        val group = GroupChat.readCreation(candidate.envelope())
        require(SaitoEnvelope.decode(candidate.envelope()).author == wallet.publicKey)
        check(load(group.id) == null)
        save(Entry(group,candidate,Status.UNKNOWN))
    }

    fun assertSelected() {
        check(AndroidWalletStore(context).loadExisting().exportJson() == wallet.exportJson())
        check(AndroidBackendStore(context).load() == backend)
    }

    fun claim(id: String, signature: String) = synchronized(SaitoLocalState.lock) {
        assertSelected()
        check(load(id)?.let { it.candidate.id == signature && it.status == Status.UNKNOWN } == true)
    }

    fun record(id: String, signature: String, status: Status): Entry = synchronized(SaitoLocalState.lock) {
        val previous = requireNotNull(load(id))
        check(previous.candidate.id == signature)
        check(status in listOf(Status.SUBMITTED,Status.FORWARDED))
        save(previous.copy(status=status))
    }

    private fun save(entry: Entry): Entry {
        val record = JsonObject().apply {
            addProperty("creation",entry.group.creationEnvelope)
            addProperty("candidate",entry.candidate.encoded())
            addProperty("status",entry.status.name)
        }
        file(entry.group.id).save(EncryptedLocalFile.Contents(1,record.toString()))
        return requireNotNull(load(entry.group.id)).also { check(it.candidate.id == entry.candidate.id && it.status == entry.status) }
    }

    companion object {
        private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()
        /** Unfinished or unreadable matching work conservatively retains wallet-replacement disclosure. */
        fun hasWork(context: Context, wallet: SaitoWallet, backend: SaitoBackend): Boolean = synchronized(SaitoLocalState.lock) {
            try {
                val prefix="groupchat.${backend.id}.${wallet.publicKey}."
                val names=requireNotNull(context.noBackupFilesDir.listFiles()).map{it.name}.filter{it.startsWith(prefix)}
                val pattern=Regex("${Regex.escape(prefix)}([1-9A-HJ-NP-Za-km-z]{43,45})\\.v1(?:\\.bak|\\.new)?")
                if(names.any{pattern.matchEntire(it)==null || it.endsWith(".new")}) return@synchronized true
                GroupChatStore(context,wallet,backend).entries().any{it.status!=Status.FORWARDED}
            } catch(_:Exception){true}
        }
    }
}
