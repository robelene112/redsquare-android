package nl.davidengelen.redsquare.saito

import com.google.gson.JsonObject

data class GroupChatMessage(val id: String, val author: String, val groupId: String, val createdAt: Long, val text: String)

data class GroupChatReplay(val group:NamedChatGroup,val messages:List<GroupChatMessage>,val originals:List<String>)

enum class ChatGroupRole {
    ADMIN, MEMBER, LEFT, REMOVED;
    val active: Boolean get() = this == ADMIN || this == MEMBER
}

class NamedChatGroup internal constructor(val id: String, val name: String,
    val memberRoles: Map<String, ChatGroupRole>, val creationEnvelope: String?,
    val confirmedInvitation: GroupInvitation? = null,
    val controls: List<String> = emptyList())

/** Compatible unencrypted named-group controls; participants sign with their own wallet. */
object GroupChat {
    fun originalEnvelope(networkTransaction:ByteArray):String = throw UnsupportedOperationException("Group network original pending")

    /** Replay observed originals from their retained origin, never by sorting authority timestamps. */
    fun replay(origin:NamedChatGroup,originals:List<String>):GroupChatReplay {
        var group=origin.creationEnvelope?.let(::readCreation) ?: confirmInvitation(
            requireNotNull(origin.confirmedInvitation),origin.controls.first())
        val seen=group.controls.map{SaitoEnvelope.decode(it).id}.toMutableSet()
        group.creationEnvelope?.let{seen.add(SaitoEnvelope.decode(it).id)}
        val messages=mutableListOf<GroupChatMessage>()
        val retained=mutableListOf<String>()
        originals.forEach{envelope->
            val signed=SaitoEnvelope.decode(envelope)
            if(!seen.add(signed.id))return@forEach
            if(signed.body.requiredString("request")=="chat message") {
                val message=readMessage(group,envelope)
                messages.add(message)
                if(group.memberRoles[message.author]?.active!=true) {
                    group=NamedChatGroup(group.id,group.name,java.util.Collections.unmodifiableMap(
                        group.memberRoles+(message.author to ChatGroupRole.MEMBER)),group.creationEnvelope,group.confirmedInvitation,group.controls)
                }
            } else group=applyControl(group,envelope)
            retained.add(envelope)
        }
        return GroupChatReplay(group,java.util.Collections.unmodifiableList(messages.sortedWith(
            compareBy<GroupChatMessage>{it.createdAt}.thenBy{it.id})),java.util.Collections.unmodifiableList(retained))
    }

    fun message(wallet: SaitoWallet, group: NamedChatGroup, text: String, timestamp: Long,
        mentioned: List<String> = emptyList()): SaitoTransaction {
        require(group.memberRoles[wallet.publicKey]?.active == true)
        require(text.isNotBlank() && timestamp >= 0 && mentioned.all(SaitoWallet::isPublicKey))
        val body = JsonObject().apply {
            addProperty("module", "Chat"); addProperty("request", "chat message")
            addProperty("group_id", group.id); addProperty("message", text); addProperty("timestamp", timestamp)
            add("mentioned", com.google.gson.JsonArray().apply { mentioned.distinct().forEach(::add) })
            addProperty("group_name", group.name)
        }
        val activeMembers = group.memberRoles.filterValues { it.active }.keys
        return SaitoTransaction.create(wallet, body, timestamp, listOf(group.id) + mentioned.filter { it !in activeMembers })
    }

    fun readMessage(group: NamedChatGroup, envelope: String): GroupChatMessage {
        val signed = SaitoEnvelope.decode(envelope)
        val body = signed.body
        require(body.requiredString("module") == "Chat" && body.requiredString("request") == "chat message")
        require(body.requiredString("group_id") == group.id && group.id in signed.recipients)
        require(signed.recipients.all(SaitoWallet::isPublicKey))
        require(group.memberRoles[signed.author] != ChatGroupRole.REMOVED)
        val text = body.requiredString("message")
        require(text.isNotBlank())
        val timestamp = body.get("timestamp")
        require(timestamp != null && timestamp.isJsonPrimitive && timestamp.asJsonPrimitive.isNumber)
        require(timestamp.asString.matches(Regex("[0-9]{1,19}")))
        val time = timestamp.asString.toLong()
        require(time >= 0)
        return GroupChatMessage(signed.id, signed.author, group.id, signed.createdAt, text)
    }

    /** Records a deliberate unsigned-link trust choice, not a verified creation or admission. */
    fun confirmInvitation(invitation: GroupInvitation, joinEnvelope: String): NamedChatGroup {
        val signed = SaitoEnvelope.decode(joinEnvelope)
        val body = signed.body
        require(body.requiredString("module") == "Chat" && body.requiredString("request") == "chat join")
        require(body.requiredString("group_id") == invitation.id && body.requiredString("invited_by") == invitation.sender)
        require(signed.author != invitation.id && signed.recipients.firstOrNull() == invitation.id)
        require(signed.recipients.all(SaitoWallet::isPublicKey))
        require(signed.recipients.containsAll(listOf(signed.author, invitation.admin, invitation.sender)))
        val members = linkedMapOf(signed.author to ChatGroupRole.MEMBER, invitation.admin to ChatGroupRole.ADMIN)
        return NamedChatGroup(invitation.id, invitation.name, java.util.Collections.unmodifiableMap(members),
            null, invitation, java.util.Collections.singletonList(joinEnvelope))
    }

    fun restore(invitation: GroupInvitation, joinEnvelope: String, controls: List<String>): NamedChatGroup =
        controls.fold(confirmInvitation(invitation, joinEnvelope)) { group, original -> applyControl(group, original) }

    /** Revalidate retained originals in their observed order; arrival order is not global consensus order. */
    fun restore(creationEnvelope: String, controls: List<String>): NamedChatGroup =
        controls.fold(readCreation(creationEnvelope)) { group, original -> applyControl(group, original) }

    fun applyControl(group: NamedChatGroup, envelope: String): NamedChatGroup {
        val signed = SaitoEnvelope.decode(envelope)
        val body = signed.body
        require(body.requiredString("module") == "Chat" && body.requiredString("group_id") == group.id)
        require(group.id in signed.recipients && signed.recipients.all(SaitoWallet::isPublicKey))
        if (group.controls.any { SaitoEnvelope.decode(it).id == signed.id }) return group
        val members = group.memberRoles.toMutableMap()
        var name = group.name
        when (body.requiredString("request")) {
            "chat join" -> {
                require(SaitoWallet.isPublicKey(body.requiredString("invited_by")))
                require(members[signed.author] != ChatGroupRole.REMOVED)
                if (members[signed.author] != ChatGroupRole.ADMIN) members[signed.author] = ChatGroupRole.MEMBER
            }
            "chat remove" -> {
                val member = body.requiredString("member_id")
                require(SaitoWallet.isPublicKey(member))
                require(signed.author == member || members[signed.author] == ChatGroupRole.ADMIN)
                // A self-leave cannot undo an administrator's removal, including when delayed.
                if (members[member] != ChatGroupRole.REMOVED) {
                    members[member] = if (signed.author == member) ChatGroupRole.LEFT else ChatGroupRole.REMOVED
                }
            }
            "chat update" -> {
                require(members[signed.author] == ChatGroupRole.ADMIN)
                name = body.requiredString("group_name")
                require(name.isNotBlank() && Charsets.UTF_8.newEncoder().canEncode(name))
                val roles = body.get("member_ids")
                require(roles != null && roles.isJsonObject)
                roles.asJsonObject.entrySet().forEach { (member, value) ->
                    require(SaitoWallet.isPublicKey(member) && member != group.id && value.isJsonPrimitive)
                    val primitive = value.asJsonPrimitive
                    val role = when {
                        primitive.isString && primitive.asString == "admin" -> ChatGroupRole.ADMIN
                        primitive.isNumber && primitive.asString == "1" -> ChatGroupRole.MEMBER
                        primitive.isNumber && primitive.asString == "0" -> ChatGroupRole.LEFT
                        primitive.isNumber && primitive.asString == "-1" -> ChatGroupRole.REMOVED
                        else -> throw IllegalArgumentException("Invalid group role")
                    }
                    // A snapshot has no predecessor/revision proof. Preserve observed departure/removal.
                    if (members[member] == ChatGroupRole.REMOVED) return@forEach
                    if (members[member] == ChatGroupRole.LEFT && role.active) return@forEach
                    members[member] = role
                }
            }
            else -> throw IllegalArgumentException("Unsupported group control")
        }
        return NamedChatGroup(group.id, name, java.util.Collections.unmodifiableMap(members),
            group.creationEnvelope, group.confirmedInvitation, java.util.Collections.unmodifiableList(group.controls + envelope))
    }

    fun leave(wallet: SaitoWallet, groupId: String, timestamp: Long): SaitoTransaction {
        require(SaitoWallet.isPublicKey(groupId) && groupId != wallet.publicKey)
        val body = JsonObject().apply {
            addProperty("module", "Chat")
            addProperty("request", "chat remove")
            addProperty("group_id", groupId)
            addProperty("member_id", wallet.publicKey)
        }
        return SaitoTransaction.createTo(wallet, body, timestamp, groupId)
    }

    /** Construction follows an explicit joining choice; unsigned claims are not membership proof. */
    fun join(wallet: SaitoWallet, invitation: GroupInvitation, timestamp: Long): SaitoTransaction {
        require(wallet.publicKey != invitation.id)
        val body = JsonObject().apply {
            addProperty("module", "Chat")
            addProperty("request", "chat join")
            addProperty("group_id", invitation.id)
            addProperty("invited_by", invitation.sender)
        }
        return SaitoTransaction.createTo(wallet, body, timestamp, invitation.id,
            listOf(invitation.admin, invitation.sender))
    }

    /** Authenticate available creation without implying the reader has joined. */
    fun readCreation(envelope: String): NamedChatGroup {
        val signed = SaitoEnvelope.decode(envelope)
        val body = signed.body
        require(body.requiredString("module") == "Chat" && body.requiredString("request") == "chat group")
        require(body.requiredString("admin") == signed.author)
        require(signed.recipients.firstOrNull() == signed.author)
        require(signed.recipients.all(SaitoWallet::isPublicKey))
        val id = body.requiredString("id")
        val name = body.requiredString("name")
        require(SaitoWallet.isPublicKey(id) && id !in signed.recipients)
        require(name.isNotBlank() && Charsets.UTF_8.newEncoder().canEncode(name))
        val members = signed.recipients.distinct().associateWith {
            if (it == signed.author) ChatGroupRole.ADMIN else ChatGroupRole.MEMBER
        }
        return NamedChatGroup(id, name, java.util.Collections.unmodifiableMap(members), envelope)
    }

    fun create(wallet: SaitoWallet, groupId: String, name: String, invitees: List<String>,
        timestamp: Long): SaitoTransaction {
        require(SaitoWallet.isPublicKey(groupId) && groupId != wallet.publicKey)
        require(name.isNotBlank())
        require(invitees.all { SaitoWallet.isPublicKey(it) && it != groupId })
        val recipients = invitees.distinct().filter { it != wallet.publicKey }
        require(recipients.size <= 254)
        val body = JsonObject().apply {
            addProperty("module", "Chat")
            addProperty("request", "chat group")
            addProperty("name", name)
            addProperty("admin", wallet.publicKey)
            addProperty("id", groupId)
        }
        return SaitoTransaction.create(wallet, body, timestamp, recipients)
    }

    private fun JsonObject.requiredString(name: String): String {
        val value = get(name)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString)
        return value.asString
    }
}
