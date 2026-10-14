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
    data class Entry(val group: NamedChatGroup, val candidate: SaitoTransaction, val status: Status, val creationKey: SaitoWallet? = null)
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
        val creation = record.get("creation")?.takeUnless{it.isJsonNull}?.asString
        val controls=record.get("controls")?.asJsonArray?.map{it.asString}.orEmpty()
        val group = if(creation!=null) GroupChat.restore(creation,controls) else {
            val invitation=GroupInvitation.parse(record.get("invitation").asString)
            GroupChat.restore(invitation,record.get("confirmation").asString,controls)
        }
        require(group.id == id && wallet.publicKey in group.memberRoles)
        val candidate = SaitoTransaction.restore(record.get("candidate").asString)
        val original=creation ?: record.get("confirmation").asString
        require(candidate.envelope() in (listOf(original)+group.controls) && SaitoEnvelope.decode(candidate.envelope()).author == wallet.publicKey)
        val creationKey=record.get("creationKey")?.takeUnless{it.isJsonNull}?.let {
            require(it.isJsonPrimitive && it.asJsonPrimitive.isString)
            SaitoWallet.fromBackup(it.asString).also{key->require(key.publicKey==group.id)}
        }
        Entry(group,candidate,Status.valueOf(record.get("status").asString),creationKey)
    }

    fun prepareCreation(candidate: SaitoTransaction, creationKey: SaitoWallet? = null): Entry = synchronized(SaitoLocalState.lock) {
        assertSelected()
        val group = GroupChat.readCreation(candidate.envelope())
        require(SaitoEnvelope.decode(candidate.envelope()).author == wallet.publicKey)
        require(creationKey==null || creationKey.publicKey==group.id)
        check(load(group.id) == null)
        save(Entry(group,candidate,Status.UNKNOWN,creationKey))
    }

    /** Confirmation belongs to this active wallet; unsigned link origin never chooses a backend. */
    fun prepareInvitation(invitation: GroupInvitation, candidate: SaitoTransaction): Entry = synchronized(SaitoLocalState.lock) {
        assertSelected()
        val signed=SaitoEnvelope.decode(candidate.envelope())
        require(signed.author==wallet.publicKey)
        val previous=load(invitation.id)
        val effective=previous?.group?.let{invitation.usingKnownGroup(it)} ?: invitation
        require(invitation.admin==effective.admin && invitation.name==effective.name)
        val group=GroupChat.confirmInvitation(effective,candidate.envelope())
        require(signed.recipients==listOf(effective.id,wallet.publicKey,effective.admin,effective.sender).distinct())
        if(previous==null) save(Entry(group,candidate,Status.UNKNOWN)) else {
            check(previous.status==Status.FORWARDED)
            save(previous.copy(group=GroupChat.applyControl(previous.group,candidate.envelope()),candidate=candidate,status=Status.UNKNOWN))
        }
    }

    /** Retain the signed departure and its observed role before the first network attempt. */
    fun prepareLeave(candidate:SaitoTransaction):Entry = synchronized(SaitoLocalState.lock) {
        assertSelected()
        val signed=SaitoEnvelope.decode(candidate.envelope())
        require(signed.author==wallet.publicKey && signed.body.get("request").asString=="chat remove")
        require(signed.body.get("member_id").asString==wallet.publicKey)
        val previous=requireNotNull(load(signed.body.get("group_id").asString))
        check(previous.status==Status.FORWARDED && previous.group.memberRoles[wallet.publicKey]?.active==true)
        save(previous.copy(group=GroupChat.applyControl(previous.group,candidate.envelope()),candidate=candidate,status=Status.UNKNOWN))
    }

    /** Explicit retry preserves the original signature, origin and protected group key. */
    fun prepareRetry(id:String,signature:String):Entry = synchronized(SaitoLocalState.lock) {
        assertSelected()
        val previous=requireNotNull(load(id))
        check(previous.candidate.id==signature && previous.status in listOf(Status.UNKNOWN,Status.SUBMITTED))
        save(previous.copy(status=Status.UNKNOWN))
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
            entry.group.creationEnvelope?.let{addProperty("creation",it)} ?: run {
                addProperty("invitation",requireNotNull(entry.group.confirmedInvitation).encoded())
                addProperty("confirmation",entry.group.controls.first())
            }
            add("controls",com.google.gson.JsonArray().apply{entry.group.controls.forEach(::add)})
            addProperty("candidate",entry.candidate.encoded())
            addProperty("status",entry.status.name)
            entry.creationKey?.let { key ->
                require(key.publicKey==entry.group.id)
                addProperty("creationKey",key.exportJson())
            }
        }
        file(entry.group.id).save(EncryptedLocalFile.Contents(1,record.toString()))
        return requireNotNull(load(entry.group.id)).also { check(it.candidate.id == entry.candidate.id && it.status == entry.status &&
            it.creationKey?.exportJson()==entry.creationKey?.exportJson()) }
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
