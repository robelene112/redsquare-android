package nl.davidengelen.redsquare.chat

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import nl.davidengelen.redsquare.AndroidWalletStore
import nl.davidengelen.redsquare.saito.*

/** Local record failures are separate from unsupported or inadmissible network traffic. */
internal class GroupChatStorageException(cause:Exception):IllegalStateException("Group records require local recovery",cause)

/** Encrypted original group state. Opening never transmits a retained attempt. */
internal class GroupChatStore(private val context: Context, private val wallet: SaitoWallet, private val backend: SaitoBackend) {
    enum class Status { UNKNOWN, SUBMITTED, FORWARDED, RECEIVED, STOPPED }
    data class StoppedAction(val candidate:SaitoTransaction,val previousStatus:Status)
    data class Entry(val group: NamedChatGroup, val candidate: SaitoTransaction?, val status: Status, val creationKey: SaitoWallet? = null, val originals:List<String> = emptyList(), val messages:List<GroupChatMessage> = emptyList(), val draft:String = "", val reactions:List<GroupChatLikeEvent> = emptyList(), val ownCreation:Boolean=false,val stoppedActions:List<StoppedAction> = emptyList()) {
        fun likedBy(message:GroupChatMessage)=GroupChatReplay(group,messages,originals,reactions=reactions).likedBy(message)
        val hasPendingAction:Boolean get()=status==Status.UNKNOWN||status==Status.SUBMITTED
    }
    private val prefix = "groupchat.${backend.id}.${wallet.publicKey}."

    private fun file(id: String): EncryptedLocalFile {
        require(SaitoWallet.isPublicKey(id) && id != wallet.publicKey)
        return EncryptedLocalFile(context,"$prefix$id.v1","redsquare.groupchat.v1","groupchat.")
    }

    private fun requireNewRecord(id:String) {
        file(id) // Validate the scoped identifier before checking its atomic companions.
        val name="$prefix$id.v1"
        if(listOf(name,"$name.bak","$name.new").any{java.io.File(context.noBackupFilesDir,it).exists()})
            throw GroupChatStorageException(IllegalStateException("Existing protected group work requires recovery"))
    }

    fun entries(): List<Entry> = synchronized(SaitoLocalState.lock) {
        val pattern = Regex("${Regex.escape(prefix)}([1-9A-HJ-NP-Za-km-z]{43,45})\\.v1(?:\\.bak)?")
        val files=requireNotNull(context.noBackupFilesDir.listFiles())
        val unfinished=Regex("${Regex.escape(prefix)}([1-9A-HJ-NP-Za-km-z]{43,45})\\.v1\\.new")
        if(files.any{unfinished.matches(it.name)})
            throw GroupChatStorageException(IllegalStateException("Unfinished protected group work requires recovery"))
        files.mapNotNull { pattern.matchEntire(it.name)?.groupValues?.get(1) }
            .distinct().map { requireNotNull(load(it)) }
    }

    fun load(id: String): Entry? = synchronized(SaitoLocalState.lock) {
        val scoped=file(id) // Invalid network identifiers remain validation failures.
        try {
        val contents = scoped.load() ?: return@synchronized null
        require(contents.version == 1.toByte())
        val record = gson.fromJson(contents.text,JsonObject::class.java)
        val creation = record.get("creation")?.takeUnless{it.isJsonNull}?.asString
        val controls=record.get("controls")?.asJsonArray?.map{it.asString}.orEmpty()
        val origin = if(creation!=null) GroupChat.readCreation(creation) else {
            val invitation=GroupInvitation.parse(record.get("invitation").asString)
            GroupChat.confirmInvitation(invitation,record.get("confirmation").asString)
        }
        val originals=record.get("events")?.asJsonArray?.map{it.asString} ?: controls
        val history=GroupChat.replay(origin,originals)
        val group=history.group
        require(group.id == id && wallet.publicKey in group.memberRoles)
        require(record.has("candidate"))
        val candidate=record.get("candidate").takeUnless{it.isJsonNull}?.let{SaitoTransaction.restore(it.asString)}
        val status=Status.valueOf(record.get("status").asString)
        if(candidate==null)require(status==Status.RECEIVED && creation!=null) else {
            require(status!=Status.RECEIVED)
            val original=creation ?: record.get("confirmation").asString
            require(candidate.envelope() in (listOf(original)+group.controls+history.originals) &&
                SaitoEnvelope.decode(candidate.envelope()).author==wallet.publicKey)
        }
        val stopped=record.get("stopped")?.asJsonArray?.map { value ->
            val item=value.asJsonObject
            val retained=SaitoTransaction.restore(item.get("candidate").asString)
            val previousStatus=Status.valueOf(item.get("status").asString)
            require(previousStatus in listOf(Status.UNKNOWN,Status.SUBMITTED))
            val signed=SaitoEnvelope.decode(retained.envelope())
            require(signed.author==wallet.publicKey && retained.envelope() in history.originals)
            require(signed.body.get("request").asString=="chat update" ||
                (signed.body.get("request").asString=="chat remove"&&signed.body.get("member_id").asString!=wallet.publicKey))
            StoppedAction(retained,previousStatus)
        }.orEmpty()
        require(stopped.map{it.candidate.id}.distinct().size==stopped.size)
        if(status==Status.STOPPED)require(candidate!=null && stopped.any{it.candidate.envelope()==candidate.envelope()})
        val creationKey=record.get("creationKey")?.takeUnless{it.isJsonNull}?.let {
            require(it.isJsonPrimitive && it.asJsonPrimitive.isString)
            SaitoWallet.fromBackup(it.asString).also{key->require(key.publicKey==group.id)}
        }
        val draft=record.get("draft")?.also{require(it.isJsonPrimitive&&it.asJsonPrimitive.isString)}?.asString ?: ""
        Entry(group,candidate,status,creationKey,history.originals,history.messages,draft,history.reactions,
            creation!=null && SaitoEnvelope.decode(creation).author==wallet.publicKey,stopped)
        }catch(failure:GroupChatStorageException){throw failure}
        catch(failure:Exception){throw GroupChatStorageException(failure)}
    }

    fun prepareCreation(candidate: SaitoTransaction, creationKey: SaitoWallet? = null): Entry = synchronized(SaitoLocalState.lock) {
        assertSelected()
        val group = GroupChat.readCreation(candidate.envelope())
        require(SaitoEnvelope.decode(candidate.envelope()).author == wallet.publicKey)
        require(creationKey==null || creationKey.publicKey==group.id)
        check(load(group.id) == null)
        requireNewRecord(group.id)
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
        if(previous==null) {
            requireNewRecord(group.id)
            save(Entry(group,candidate,Status.UNKNOWN))
        } else {
            check(!previous.hasPendingAction)
            save(previous.copy(group=GroupChat.applyControl(previous.group,candidate.envelope()),candidate=candidate,status=Status.UNKNOWN,originals=previous.originals+candidate.envelope()))
        }
    }

    /** Validate the chosen promotion against fresh protected state before any network attempt. */
    fun preparePromotion(candidate:SaitoTransaction,member:String):Entry = synchronized(SaitoLocalState.lock) {
        prepareAdminChange(candidate) { group,time -> GroupChat.promote(wallet,group,member,time) }
    }

    /** Persist a removal before transmission, keeping the removed recipient in its Relay. */
    fun prepareRemoval(candidate:SaitoTransaction,member:String):Entry = synchronized(SaitoLocalState.lock) {
        prepareAdminChange(candidate) { group,time -> GroupChat.removeMember(wallet,group,member,time) }
    }

    private fun prepareAdminChange(candidate:SaitoTransaction,create:(NamedChatGroup,Long)->SaitoTransaction):Entry {
        assertSelected()
        val signed=SaitoEnvelope.decode(candidate.envelope())
        val previous=requireNotNull(load(signed.body.get("group_id").asString))
        check(!previous.hasPendingAction)
        // Fresh protected state must still permit exactly the user's chosen change.
        require(create(previous.group,signed.createdAt).envelope()==candidate.envelope())
        return save(previous.copy(group=GroupChat.applyControl(previous.group,candidate.envelope()),candidate=candidate,
            status=Status.UNKNOWN,originals=previous.originals+candidate.envelope()))
    }

    /** Retain the signed departure and its observed role before the first network attempt. */
    fun prepareLeave(candidate:SaitoTransaction):Entry = synchronized(SaitoLocalState.lock) {
        assertSelected()
        val signed=SaitoEnvelope.decode(candidate.envelope())
        require(signed.author==wallet.publicKey && signed.body.get("request").asString=="chat remove")
        require(signed.body.get("member_id").asString==wallet.publicKey)
        val previous=requireNotNull(load(signed.body.get("group_id").asString))
        check(!previous.hasPendingAction && previous.group.memberRoles[wallet.publicKey]?.active==true)
        save(previous.copy(group=GroupChat.applyControl(previous.group,candidate.envelope()),candidate=candidate,status=Status.UNKNOWN,originals=previous.originals+candidate.envelope()))
    }

    /** Persist authenticated group originals in observation order before incoming RPC acknowledgement. */
    fun receive(envelope:String):Entry = synchronized(SaitoLocalState.lock) {
        val signed=SaitoEnvelope.decode(envelope)
        require(signed.body.get("module")==com.google.gson.JsonPrimitive("Chat"))
        if(signed.body.get("request")==com.google.gson.JsonPrimitive("chat group")){
            val group=GroupChat.readCreation(envelope)
            require(wallet.publicKey in group.memberRoles)
            val previous=load(group.id)
            if(previous!=null){
                require(previous.group.creationEnvelope?.let{SaitoEnvelope.decode(it).id}==signed.id)
                return@synchronized previous
            }
            requireNewRecord(group.id)
            return@synchronized save(Entry(group,null,Status.RECEIVED))
        }
        val id=signed.body.get("group_id").asString
        val previous=requireNotNull(load(id))
        val replay=GroupChat.replay(previous.group,previous.originals+envelope)
        retainReplay(previous,replay)
    }

    /** One bounded Archive page is validated and committed together, without repeated full-cache replays. */
    fun receiveAvailable(id:String,envelopes:List<String>):Boolean = synchronized(SaitoLocalState.lock) {
        val previous=requireNotNull(load(id))
        val replay=GroupChat.replayAvailable(previous.group,previous.originals,envelopes)
        retainReplay(previous,replay)
        replay.incomplete
    }

    private fun retainReplay(previous:Entry,replay:GroupChatReplay):Entry =
        if(replay.originals==previous.originals)previous else save(previous.copy(
            group=replay.group,originals=replay.originals,messages=replay.messages,reactions=replay.reactions))

    fun prepareReaction(candidate:SaitoTransaction):Entry = synchronized(SaitoLocalState.lock) {
        assertSelected()
        val signed=SaitoEnvelope.decode(candidate.envelope())
        require(signed.author==wallet.publicKey && signed.body.get("request").asString=="chat like")
        val previous=requireNotNull(load(signed.body.get("group_id").asString))
        check(!previous.hasPendingAction && previous.group.memberRoles[wallet.publicKey]?.active==true)
        require(previous.messages.any{it.id==signed.body.get("signature").asString && it.author==signed.body.get("mentioned").asString})
        val replay=GroupChat.replay(previous.group,previous.originals+candidate.envelope())
        require(replay.originals.size==previous.originals.size+1)
        save(previous.copy(group=replay.group,originals=replay.originals,messages=replay.messages,
            reactions=replay.reactions,candidate=candidate,status=Status.UNKNOWN))
    }

    fun saveDraft(id:String,text:String):Entry = synchronized(SaitoLocalState.lock) {
        val previous=requireNotNull(load(id))
        check(!previous.hasPendingAction)
        save(previous.copy(draft=text))
    }

    fun prepareMessage(candidate:SaitoTransaction):Entry = synchronized(SaitoLocalState.lock) {
        assertSelected()
        val signed=SaitoEnvelope.decode(candidate.envelope())
        require(signed.author==wallet.publicKey && signed.body.get("request").asString=="chat message")
        val previous=requireNotNull(load(signed.body.get("group_id").asString))
        check(!previous.hasPendingAction && previous.group.memberRoles[wallet.publicKey]?.active==true)
        val replay=GroupChat.replay(previous.group,previous.originals+candidate.envelope())
        save(previous.copy(group=replay.group,originals=replay.originals,messages=replay.messages,reactions=replay.reactions,candidate=candidate,status=Status.UNKNOWN,draft=""))
    }

    /** Explicit retry preserves the original signature, origin and protected group key. */
    fun prepareRetry(id:String,signature:String):Entry = synchronized(SaitoLocalState.lock) {
        assertSelected()
        val previous=requireNotNull(load(id))
        check(previous.candidate?.id==signature && previous.status in listOf(Status.UNKNOWN,Status.SUBMITTED))
        save(previous.copy(status=Status.UNKNOWN))
    }

    /** Pure current-state classification, shared by UI guidance and protected local resolution. */
    fun isStaleAdminAction(entry:Entry):Boolean {
        if(!entry.hasPendingAction)return false
        val signed=SaitoEnvelope.decode(requireNotNull(entry.candidate).envelope())
        val request=signed.body.get("request").asString
        val adminAction=request=="chat update" ||
            (request=="chat remove"&&signed.body.get("member_id").asString!=wallet.publicKey)
        if(!adminAction)return false
        return try{requireActionMembership(entry);false}catch(_:IllegalStateException){true}
    }

    /** Stop future attempts locally, retaining the exact original and its uncertain progress. */
    fun stopStaleAction(id:String,signature:String):Entry = synchronized(SaitoLocalState.lock) {
        assertSelected()
        val previous=requireNotNull(load(id))
        check(previous.candidate?.id==signature && isStaleAdminAction(previous))
        save(previous.copy(status=Status.STOPPED,stoppedActions=previous.stoppedActions+
            StoppedAction(requireNotNull(previous.candidate),previous.status)))
    }

    fun assertSelected() {
        check(AndroidWalletStore(context).loadExisting().exportJson() == wallet.exportJson())
        check(AndroidBackendStore(context).load() == backend)
    }

    fun claim(id: String, signature: String) = synchronized(SaitoLocalState.lock) {
        assertSelected()
        val entry=requireNotNull(load(id))
        check(entry.candidate?.id==signature && entry.status==Status.UNKNOWN)
        requireActionMembership(entry)
    }

    /** Resolve current membership while SaitoPeer holds the selection/frame/enqueue lock. */
    fun relayRecipients(id:String,signature:String):List<String> = synchronized(SaitoLocalState.lock) {
        assertSelected()
        val entry=requireNotNull(load(id))
        val candidate=requireNotNull(entry.candidate)
        check(candidate.id==signature && entry.status==Status.SUBMITTED)
        requireActionMembership(entry)
        val signed=SaitoEnvelope.decode(candidate.envelope())
        val request=signed.body.get("request").asString
        if(request=="chat group"){
            // Direct creation Relay must keep the immutable original's recipients.
            check(signed.recipients.all{entry.group.memberRoles[it]?.active==true})
            return@synchronized signed.recipients
        }
        val removed=if(request=="chat remove")signed.body.get("member_id").asString else null
        val members=entry.group.memberRoles.filter{(key,role)->role.active||key==removed}.keys.filter{it!=wallet.publicKey}
        members.ifEmpty{listOf(wallet.publicKey)}
    }

    private fun requireActionMembership(entry:Entry) {
        val signed=SaitoEnvelope.decode(requireNotNull(entry.candidate).envelope())
        if(signed.body.get("request").asString=="chat update") {
            GroupChat.assertCurrentUpdate(entry.group,entry.candidate.envelope())
            return
        }
        val selfLeave=signed.body.get("request").asString=="chat remove" &&
            signed.body.get("member_id").asString==wallet.publicKey
        if(signed.body.get("request").asString=="chat remove"&&!selfLeave) {
            check(entry.group.memberRoles[wallet.publicKey]==ChatGroupRole.ADMIN)
            check(entry.group.memberRoles[signed.body.get("member_id").asString]==ChatGroupRole.REMOVED)
            return
        }
        check(if(selfLeave)entry.group.memberRoles[wallet.publicKey]==ChatGroupRole.LEFT
            else entry.group.memberRoles[wallet.publicKey]?.active==true)
    }

    fun record(id: String, signature: String, status: Status): Entry = synchronized(SaitoLocalState.lock) {
        val previous = requireNotNull(load(id))
        check(previous.candidate?.id == signature && previous.status!=Status.STOPPED)
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
            add("events",com.google.gson.JsonArray().apply{entry.originals.forEach(::add)})
            addProperty("candidate",entry.candidate?.encoded())
            addProperty("status",entry.status.name)
            addProperty("draft",entry.draft)
            add("stopped",com.google.gson.JsonArray().apply { entry.stoppedActions.forEach { item ->
                add(JsonObject().apply{addProperty("candidate",item.candidate.encoded());addProperty("status",item.previousStatus.name)})
            }})
            entry.creationKey?.let { key ->
                require(key.publicKey==entry.group.id)
                addProperty("creationKey",key.exportJson())
            }
        }
        try {
            file(entry.group.id).save(EncryptedLocalFile.Contents(1,record.toString()))
            return requireNotNull(load(entry.group.id)).also { check(it.candidate?.id == entry.candidate?.id && it.status == entry.status &&
                it.creationKey?.exportJson()==entry.creationKey?.exportJson() && it.draft==entry.draft &&
                it.stoppedActions.map{row->row.candidate.envelope() to row.previousStatus}==entry.stoppedActions.map{row->row.candidate.envelope() to row.previousStatus}) }
        }catch(failure:GroupChatStorageException){throw failure}
        catch(failure:Exception){throw GroupChatStorageException(failure)}
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
                GroupChatStore(context,wallet,backend).entries().any{it.hasPendingAction || it.stoppedActions.isNotEmpty() || it.draft.isNotBlank()}
            } catch(_:Exception){true}
        }
    }
}
