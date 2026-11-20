package nl.davidengelen.redsquare.spaces

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import com.google.gson.Strictness
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.SignedSaitoMessage
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64

/** Application-owned community operations. Call from a serialized IO worker.
 * Signed originals and replacement key state commit before transport is called. */
class SpaceClient(private val wallet: SaitoWallet, private val node: String, private val service: String,
    store: SpaceClientStore, private val clock: Clock,
    private val onDeliveryAttempt: suspend (Delivery) -> Unit = {},
    private val exchange: suspend (SaitoTransaction) -> String) {
    init { require(node.isNotBlank() && node.length <= 2048 && SaitoWallet.isPublicKey(service)) }

    private val store = SpacePresentationStore(store)

    enum class Delivery { CONTROL, CONTENT }
    enum class ReadProblem { EXPIRED, UNAVAILABLE }
    class ReadDenied(val spaceId: String, val problem: ReadProblem) : IllegalArgumentException("Community access is not available")
    class DifferentNode(val node: String) : IllegalArgumentException("This community belongs to a different node")

    data class Channel(val id: String, val profile: SpaceChannelProfile, val visibility: SpaceVisibility, val ready: Boolean, val unread: Long? = null, val hasUnread: Boolean? = null,
        val archived: Boolean = false, val allowedMembers: Set<String>? = null, val metadataReady: Boolean = false)
    fun channels(space: String): List<Channel> = store.transaction {
        owned()
        val state = state(space)
        val general = generalChannel(state)
        val journal = journal(space)
        journal.snapshot.channels.mapNotNull { (id, record) ->
            if (!journal.canAccessChannel(wallet.publicKey, id)) return@mapNotNull null
            val data = channelState(space, id, state)
            val readableProfile = channelProfile(space, id, state, data, record)
            val profile = readableProfile ?: if (id == general) SpaceChannelProfile("general") else null
            val metadataGroup = if (record.allowedMembers != null) record else journal.snapshot.generalChannelId?.let(journal.snapshot.channels::get)
            val metadataReady = record.visibility == SpaceVisibility.PUBLIC || readableProfile != null && metadataGroup?.retiredDevices?.isEmpty() == true &&
                (if (record.allowedMembers != null) data else state).get("joined")?.asBoolean == true
            profile?.let { Channel(id, it, record.visibility, record.visibility == SpaceVisibility.PUBLIC ||
                data.get("joined")?.asBoolean == true && record.retiredDevices.isEmpty(),
                data.get("unread")?.asLong, unreadActivity(data), record.archived, record.allowedMembers, metadataReady) }
        }
    }
    enum class ChangeStatus { QUEUED, AWAITING_CONFIRMATION, ACCEPTED, REJECTED }
    data class ArchiveChange(val id: String, val channelId: String, val archived: Boolean, val status: ChangeStatus)
    data class RoleChange(val id: String, val memberKey: String, val administrator: Boolean, val status: ChangeStatus)
    enum class MembershipAction(val kind: String) {
        LEAVE("leave"), REMOVE("remove-member"), BAN("ban-member"), UNBAN("unban-member")
    }
    data class MembershipChange(val id: String, val status: ChangeStatus, val memberKey: String, val action: MembershipAction)
    enum class OwnershipAction(val kind: String) {
        OFFER("propose-owner"), ACCEPT("accept-owner"), CANCEL("cancel-owner"), DECLINE("decline-owner")
    }
    data class OwnershipChange(val id: String, val status: ChangeStatus, val memberKey: String, val action: OwnershipAction)
    data class ChannelAccessChange(val id: String, val channelId: String, val memberKey: String, val status: ChangeStatus, val granted: Boolean = false)
    data class ChannelCreation(val id: String, val channelId: String, val profile: SpaceChannelProfile, val status: ChangeStatus, val privateMembers: Set<String>? = null)
    data class ChannelEdit(val id: String, val channelId: String, val profile: SpaceChannelProfile, val status: ChangeStatus)

    fun grantChannelAccess(space: String, channel: String, member: String): String = store.transaction {
        owned()
        val state = state(space)
        val snapshot = journal(space).snapshot
        require(state.get("joined").asBoolean && !state.has("required_control"))
        require(snapshot.members[wallet.publicKey] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN) && snapshot.members[member] == SpaceRole.MEMBER)
        val record = requireNotNull(snapshot.channels[channel])
        require(record.visibility == SpaceVisibility.PRIVATE && record.allowedMembers != null && member !in record.allowedMembers)
        val original = event(space, "grant-channel-access") {
            addProperty("control_id", snapshot.controlId); addProperty("channel_id", channel); addProperty("member", member)
        }
        val target = channelState(space, channel, state)
        target.add("channel_access_change", JsonObject().apply {
            addProperty("id", original.id); addProperty("member", member); addProperty("granted", true)
            addProperty("status", ChangeStatus.QUEUED.name)
        })
        putChannelState(space, channel, target)
        queue(space, state, original)
        original.id
    }

    fun revokeChannelAccess(space: String, channel: String, member: String): String = store.transaction {
        owned()
        val state = state(space)
        val snapshot = journal(space).snapshot
        require(state.get("joined").asBoolean && !state.has("required_control"))
        require(snapshot.members[wallet.publicKey] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN) && snapshot.members[member] == SpaceRole.MEMBER)
        val record = requireNotNull(snapshot.channels[channel])
        require(record.visibility == SpaceVisibility.PRIVATE && record.allowedMembers?.contains(member) == true && record.retiredDevices.isEmpty())
        val target = channelState(space, channel, state)
        require(target.get("joined")?.asBoolean == true)
        val keys = SpaceCrypto.restore(decode(target.text("crypto")))
        require(keys.scope == SpaceCrypto.Scope(service, space, channel, record.generation) &&
            keys.epoch == record.epoch && keys.bindings == record.devices)
        val profile = requireNotNull(channelProfile(space, channel, state, target, record))
        val credential = record.devices.keys.singleOrNull { it.split(':')[1] == member }
        val change = credential?.let(keys::remove)
        val replacement = change?.session ?: keys
        val metadata = replacement.publish(wallet, "channel-profile", id(), requireNotNull(snapshot.controlId), JsonObject().apply {
            addProperty("version", 1); addProperty("channel_id", channel); add("profile", profile.json())
        }.toString(), clock.millis())
        val original = event(space, "revoke-channel-access") {
            addProperty("control_id", snapshot.controlId); addProperty("channel_id", channel); addProperty("member", member)
            addProperty("epoch", replacement.epoch); add("devices", devices(replacement.bindings))
            change?.let { addProperty("commit", it.commit) }
            addProperty("channel_metadata", encode(metadata.envelope().toByteArray(Charsets.UTF_8)))
        }
        target.add("channel_access_change", JsonObject().apply {
            addProperty("id", original.id); addProperty("member", member); addProperty("status", ChangeStatus.QUEUED.name)
        })
        putChannelState(space, channel, target)
        queue(space, state, original, replacement, change?.afterRejection)
        original.id
    }

    fun channelAccessChange(space: String, channel: String): ChannelAccessChange? = store.transaction {
        owned()
        val change = channelState(space, channel).getAsJsonObject("channel_access_change") ?: return@transaction null
        ChannelAccessChange(change.text("id"), channel, change.text("member"), changeStatus(space, change), change.get("granted")?.asBoolean == true)
    }

    fun editChannel(space: String, channel: String, profile: SpaceChannelProfile): String = store.transaction {
        owned()
        val state = state(space)
        val snapshot = journal(space).snapshot
        require(state.get("joined").asBoolean && !state.has("required_control"))
        require(snapshot.members[wallet.publicKey] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN))
        val record = requireNotNull(snapshot.channels[channel])
        val target = channelState(space, channel, state)
        val previous = requireNotNull(channelProfile(space, channel, state, target, record))
        require(profile.kind == previous.kind)
        val metadata = if (record.visibility == SpaceVisibility.PRIVATE) {
            val metadataChannel = if (record.allowedMembers != null) channel else requireNotNull(snapshot.generalChannelId)
            val metadataGroup = requireNotNull(snapshot.channels[metadataChannel])
            val metadataState = channelState(space, metadataChannel, state)
            require(metadataGroup.visibility == SpaceVisibility.PRIVATE && metadataGroup.retiredDevices.isEmpty() && metadataState.get("joined")?.asBoolean == true)
            val keys = SpaceCrypto.restore(decode(metadataState.text("crypto")))
            require(keys.scope == SpaceCrypto.Scope(service, space, metadataChannel, metadataGroup.generation) &&
                keys.epoch == metadataGroup.epoch && keys.bindings == metadataGroup.devices)
            keys.publish(wallet, "channel-profile-edit", id(), requireNotNull(snapshot.controlId), JsonObject().apply {
                addProperty("version", 2); addProperty("channel_id", channel)
                addProperty("name", profile.name); addProperty("category", profile.category)
            }.toString(), clock.millis(), targetChannelId = channel).envelope()
        } else null
        val original = event(space, "edit-channel") {
            addProperty("control_id", requireNotNull(snapshot.controlId)); addProperty("channel_id", channel)
            if (metadata == null) add("channel_profile", profile.json())
            else addProperty("channel_metadata", encode(metadata.toByteArray(Charsets.UTF_8)))
        }
        target.add("channel_edit", JsonObject().apply {
            addProperty("id", original.id); add("profile", profile.json()); addProperty("status", ChangeStatus.QUEUED.name)
            metadata?.let { addProperty("metadata", encode(it.toByteArray(Charsets.UTF_8))) }
        })
        putChannelState(space, channel, target)
        queue(space, state, original)
        original.id
    }

    fun channelEdit(space: String, channel: String): ChannelEdit? = store.transaction {
        owned()
        val change = channelState(space, channel).getAsJsonObject("channel_edit") ?: return@transaction null
        ChannelEdit(change.text("id"), channel, SpaceChannelProfile.fromJson(change.getAsJsonObject("profile")), changeStatus(space, change))
    }


    data class Management(val archive: ArchiveChange? = null, val role: RoleChange? = null,
        val edit: ChannelEdit? = null, val creation: ChannelCreation? = null, val access: ChannelAccessChange? = null,
        val membership: MembershipChange? = null, val ownership: OwnershipChange? = null, val ownershipOffer: SpaceOwnershipOffer? = null)
    data class Presentation(val communities: List<Community>, val profiles: Map<String, SpaceProfile>,
        val current: Community?, val channels: List<Channel>, val members: List<Member>, val bannedMembers: Set<String>,
        val window: HistoryWindow?, val messages: List<Message>, val draft: String, val anchor: ReadingAnchor?,
        val invitation: Invitation?, val publicLink: String?, val listingIntent: Boolean?, val management: Management)

    /** A bounded, coherent read for native rendering. It authenticates control
     * history once per scope instead of independently rebuilding it for every
     * visible property. No cached authority survives this transaction. */
    fun presentation(space: String? = null, channel: String? = null, from: Long? = null): Presentation = store.project {
        val communities = communities()
        val profiles = communities.associate { it.id to profile(it.id) }.toMutableMap()
        val current = space?.let { community(it, channel) }
        val selected = current?.channel
        if (space != null) profiles[space] = profile(space)
        val members = space?.let(::members).orEmpty()
        val window = space?.let { historyWindow(it, from, selected) }
        Presentation(communities, profiles, current, space?.let(::channels).orEmpty(), members,
            space?.let(::bannedMembers).orEmpty(), window,
            space?.let { timeline(it, from = window?.from, channel = selected) }.orEmpty(),
            space?.let { draft(it, selected) }.orEmpty(), space?.let { readingAnchor(it, selected) },
            space?.let(::invitation), space?.takeIf { profiles[it]?.visibility == SpaceVisibility.PUBLIC && members.isNotEmpty() }?.let(::publicLink),
            space?.let(::listingIntent), space?.let { management(it, selected) } ?: Management())
    }

    /** Urgent delivery projects just its durable management results. */
    fun management(space: String, channel: String? = null): Management = store.project {
        val selected = channel ?: community(space).channel
        Management(archiveChange(space, selected), roleChange(space), channelEdit(space, selected), channelCreation(space),
            channelAccessChange(space, selected), membershipChange(space), ownershipChange(space), ownershipOffer(space))
    }

    fun ownershipOffer(space: String): SpaceOwnershipOffer? = store.transaction {
        owned(); state(space); journal(space).snapshot.ownershipOffer
    }

    fun ownershipChange(space: String): OwnershipChange? = store.transaction {
        owned()
        val change = state(space).getAsJsonObject("ownership_change") ?: return@transaction null
        OwnershipChange(change.text("id"), changeStatus(space, change), change.text("member"),
            OwnershipAction.valueOf(change.text("action")))
    }

    fun offerOwnership(space: String, member: String): String = changeOwnership(space, OwnershipAction.OFFER, member = member)
    fun acceptOwnership(space: String, proposalId: String): String = changeOwnership(space, OwnershipAction.ACCEPT, proposalId = proposalId)
    fun cancelOwnership(space: String, proposalId: String): String = changeOwnership(space, OwnershipAction.CANCEL, proposalId = proposalId)
    fun declineOwnership(space: String, proposalId: String): String = changeOwnership(space, OwnershipAction.DECLINE, proposalId = proposalId)

    /** Consent and readiness are separate accepted controls. A queued request
     * never projects an ownership change before its signed receipt arrives. */
    private fun changeOwnership(space: String, action: OwnershipAction, member: String? = null,
        proposalId: String? = null): String = store.transaction {
        owned()
        val state = state(space)
        val snapshot = journal(space).snapshot
        require(state.get("joined").asBoolean && !state.has("required_control") && wallet.publicKey in snapshot.members)
        val target = if (action == OwnershipAction.OFFER) {
            require(wallet.publicKey == snapshot.owner && snapshot.ownershipOffer == null)
            require(member in snapshot.members && member != snapshot.owner)
            requireNotNull(member)
        } else {
            val offer = requireNotNull(snapshot.ownershipOffer)
            require(offer.id == proposalId)
            if (action == OwnershipAction.CANCEL) require(wallet.publicKey == snapshot.owner)
            else require(wallet.publicKey == offer.member)
            if (action == OwnershipAction.ACCEPT) require(offer.acceptedId == null)
            offer.member
        }
        val original = event(space, action.kind) {
            addProperty("control_id", requireNotNull(snapshot.controlId))
            if (action == OwnershipAction.OFFER) addProperty("member", target) else addProperty("proposal_id", proposalId)
        }
        state.add("ownership_change", JsonObject().apply {
            addProperty("id", original.id); addProperty("status", ChangeStatus.QUEUED.name)
            addProperty("action", action.name); addProperty("member", target)
            proposalId?.let { addProperty("proposal_id", it) }
        })
        queue(space, state, original)
        original.id
    }

    /** Membership changes keep the exact durable original separate from accepted membership. */
    fun leave(space: String): String = changeMembership(space, wallet.publicKey, MembershipAction.LEAVE)
    fun removeMember(space: String, member: String): String = changeMembership(space, member, MembershipAction.REMOVE)
    fun banMember(space: String, member: String): String = changeMembership(space, member, MembershipAction.BAN)
    fun unbanMember(space: String, member: String): String = changeMembership(space, member, MembershipAction.UNBAN)

    private fun changeMembership(space: String, member: String, action: MembershipAction): String = store.transaction {
        owned()
        val state = state(space)
        val snapshot = journal(space).snapshot
        require(state.get("joined").asBoolean && !state.has("required_control"))
        val role = snapshot.members[wallet.publicKey]
        if (action == MembershipAction.LEAVE) {
            require(wallet.publicKey in snapshot.members && snapshot.owner != wallet.publicKey) {
                "The owner must transfer ownership before leaving"
            }
        } else {
            require(role in setOf(SpaceRole.OWNER, SpaceRole.ADMIN))
            require(SaitoWallet.isPublicKey(member) && member != snapshot.owner && member != wallet.publicKey)
            when (action) {
                MembershipAction.REMOVE -> require(member in snapshot.members)
                MembershipAction.BAN -> require(member !in snapshot.bannedMembers)
                MembershipAction.UNBAN -> require(member in snapshot.bannedMembers)
                else -> error("Unexpected membership action")
            }
            require(role == SpaceRole.OWNER || snapshot.members[member] != SpaceRole.ADMIN)
        }
        val original = event(space, action.kind) {
            addProperty("control_id", requireNotNull(snapshot.controlId))
            if (action != MembershipAction.LEAVE) addProperty("member", member)
        }
        state.add("membership_change", JsonObject().apply {
            addProperty("id", original.id); addProperty("status", ChangeStatus.QUEUED.name)
            addProperty("member", member); addProperty("action", action.name)
        })
        queue(space, state, original)
        original.id
    }

    fun bannedMembers(space: String): Set<String> = store.transaction {
        owned(); state(space); journal(space).snapshot.bannedMembers
    }

    fun membershipChange(space: String): MembershipChange? = store.transaction {
        owned()
        val change = state(space).getAsJsonObject("membership_change") ?: return@transaction null
        MembershipChange(change.text("id"), changeStatus(space, change),
            change.get("member")?.asString ?: wallet.publicKey,
            change.get("action")?.asString?.let(MembershipAction::valueOf) ?: MembershipAction.LEAVE)
    }

    fun setAdministrator(space: String, memberKey: String, administrator: Boolean): String = store.transaction {
        owned()
        val state = state(space)
        val snapshot = journal(space).snapshot
        require(state.get("joined").asBoolean && !state.has("required_control"))
        require(snapshot.owner == wallet.publicKey && memberKey in snapshot.members && memberKey != snapshot.owner)
        val original = event(space, "set-role") {
            addProperty("control_id", requireNotNull(snapshot.controlId)); addProperty("member", memberKey)
            addProperty("role", if (administrator) "admin" else "member")
        }
        state.add("role_change", JsonObject().apply {
            addProperty("id", original.id); addProperty("member", memberKey); addProperty("administrator", administrator)
            addProperty("status", ChangeStatus.QUEUED.name)
        })
        queue(space, state, original)
        original.id
    }

    fun roleChange(space: String): RoleChange? = store.transaction {
        owned()
        val change = state(space).getAsJsonObject("role_change") ?: return@transaction null
        val id = change.text("id")
        RoleChange(id, change.text("member"), change.get("administrator").asBoolean, changeStatus(space, change))
    }

    fun archiveChannel(space: String, channel: String, archived: Boolean): String = store.transaction {
        owned()
        val state = state(space)
        val snapshot = journal(space).snapshot
        require(state.get("joined").asBoolean && !state.has("required_control"))
        require(snapshot.members[wallet.publicKey] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN))
        require(channel in snapshot.channels)
        val original = event(space, "archive-channel") {
            addProperty("control_id", requireNotNull(snapshot.controlId)); addProperty("channel_id", channel)
            addProperty("archived", archived)
        }
        val target = channelState(space, channel, state)
        target.add("archive_change", JsonObject().apply {
            addProperty("id", original.id); addProperty("archived", archived); addProperty("status", ChangeStatus.QUEUED.name)
        })
        putChannelState(space, channel, target)
        queue(space, state, original)
        original.id
    }

    /** Absence of an outbox entry is never treated as an accepted change. */
    fun archiveChange(space: String, channel: String): ArchiveChange? = store.transaction {
        owned()
        val change = channelState(space, channel).getAsJsonObject("archive_change") ?: return@transaction null
        val id = change.text("id")
        ArchiveChange(id, channel, change.get("archived").asBoolean, changeStatus(space, change))
    }

    private fun SpaceClientStore.Transaction.changeStatus(space: String, change: JsonObject): ChangeStatus {
        val recorded = ChangeStatus.valueOf(change.text("status"))
        return if (recorded == ChangeStatus.QUEUED) {
            val pointer = requireNotNull(get("$space/operation/${change.text("id")}")).toString(Charsets.UTF_8)
            val pending = json(requireNotNull(get(pointer)))
            if (pending.get("attempted").asBoolean) ChangeStatus.AWAITING_CONFIRMATION else ChangeStatus.QUEUED
        } else recorded
    }

    fun channelCreation(space: String): ChannelCreation? = store.transaction {
        owned()
        val change = state(space).getAsJsonObject("channel_creation") ?: return@transaction null
        ChannelCreation(change.text("id"), change.text("channel"), SpaceChannelProfile.fromJson(change.getAsJsonObject("profile")),
            changeStatus(space, change), change.getAsJsonArray("private_members")?.map { it.asString }?.toSet())
    }

    fun createChannel(space: String, profile: SpaceChannelProfile): String = store.transaction {
        owned()
        val scope = SpaceCrypto.Scope(service, space, id())
        val state = state(space)
        require(!state.has("required_control"))
        enqueueChannel(space, state, profile, scope)
        scope.channelId
    }
    fun createPrivateChannel(space: String, profile: SpaceChannelProfile, members: Set<String>): String = store.transaction {
        owned()
        val state = state(space)
        require(!state.has("required_control"))
        val scope = SpaceCrypto.Scope(service, space, id())
        enqueueChannel(space, state, profile, scope, members)
        scope.channelId
    }

    private fun SpaceClientStore.Transaction.enqueueChannel(space: String, state: JsonObject,
        profile: SpaceChannelProfile, scope: SpaceCrypto.Scope, members: Set<String>? = null) {
        val snapshot = journal(space).snapshot
        require(state.get("joined").asBoolean && snapshot.members[wallet.publicKey] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN))
        require(scope.serviceKey == service && scope.spaceId == space && scope.channelId !in snapshot.channels)
        require(members == null || members.all { it in snapshot.members })
        val private = members != null || snapshot.visibility == SpaceVisibility.PRIVATE
        val saved = get(channelStateKey(space, scope.channelId))?.let(::json)
        require(saved == null || SpaceCrypto.Scope.fromJson(saved.getAsJsonObject("scope")) == scope &&
            SpaceChannelProfile.fromJson(saved.getAsJsonObject("channel_profile")) == profile)
        val crypto = if (private) saved?.get("crypto")?.asString?.let { SpaceCrypto.restore(decode(it)) }
            ?: SpaceCrypto.device(wallet.publicKey, id()).create(scope) else null
        require(crypto == null || crypto.scope == scope)
        val metadata = if (private) {
            val metadataKeys = if (members != null) requireNotNull(crypto) else SpaceCrypto.restore(decode(state.text("crypto")))
            require(metadataKeys.scope?.channelId == if (members != null) scope.channelId else generalChannel(state))
            if (members == null) {
                val general = snapshot.channels.getValue(generalChannel(state))
                require(general.retiredDevices.isEmpty() && metadataKeys.scope == SpaceCrypto.Scope(service, space, generalChannel(state), general.generation) &&
                    metadataKeys.epoch == general.epoch && metadataKeys.bindings == general.devices)
            }
            metadataKeys.publish(wallet, "channel-profile", id(), requireNotNull(snapshot.controlId), JsonObject().apply {
                addProperty("version", 1); addProperty("channel_id", scope.channelId); add("profile", profile.json())
            }.toString(), clock.millis())
        } else null
        val original = event(space, "create-channel") {
            addProperty("control_id", snapshot.controlId); addProperty("channel_id", scope.channelId)
            addProperty("visibility", if (private) "private" else "public")
            addProperty("epoch", 0); addProperty("key_generation", scope.generation)
            addProperty("admin_posts_only", profile.kind == SpaceChannelKind.ANNOUNCEMENT)
            addProperty("channel_kind", profile.kind.name.lowercase(java.util.Locale.ROOT))
            members?.let { add("allowed_members", JsonArray().apply { it.sorted().forEach(::add) }) }
            if (private) {
                addProperty("metadata", encode(requireNotNull(metadata).envelope().toByteArray(Charsets.UTF_8)))
                add("devices", devices(requireNotNull(crypto).bindings))
            } else add("channel_profile", profile.json())
        }
        put(channelStateKey(space, scope.channelId), bytes((saved ?: JsonObject()).apply {
            add("scope", scope.json()); add("channel_profile", profile.json()); addProperty("content_cursor", 0)
            addProperty("joined", private)
            crypto?.let { addProperty("crypto", encode(it.save())) }
        }))
        state.add("channel_creation", JsonObject().apply {
            addProperty("id", original.id); addProperty("channel", scope.channelId); add("profile", profile.json())
            members?.let { add("private_members", JsonArray().apply { it.sorted().forEach(::add) }) }
            addProperty("status", ChangeStatus.QUEUED.name)
        })
        queue(space, state, original)
    }
    /** Each planned channel advances only on its accepted original. A rejected
     * candidate can be prepared against a newly authenticated control head. */
    private fun SpaceClientStore.Transaction.enqueueStarter(space: String, state: JsonObject) {
        if (!state.get("joined").asBoolean || state.has("control_pending") || state.has("required_control")) return
        val next = state.getAsJsonArray("starter_channels")?.firstOrNull()?.asJsonObject ?: return
        enqueueChannel(space, state, SpaceChannelProfile.fromJson(next.getAsJsonObject("profile")),
            SpaceCrypto.Scope.fromJson(next.getAsJsonObject("scope")))
    }
    private fun SpaceClientStore.Transaction.prepareChannelJoin(space: String, state: JsonObject, snapshot: SpaceSnapshot, channel: String) {
        val record = requireNotNull(snapshot.channels[channel])
        val data = channelState(space, channel, state)
        if (record.visibility == SpaceVisibility.PRIVATE && state.get("joined").asBoolean &&
            data.get("joined")?.asBoolean != true && !data.has("joining_channel")) {
            val scope = SpaceCrypto.Scope.fromJson(data.getAsJsonObject("scope"))
            val device = SpaceCrypto.device(wallet.publicKey, id())
            val packageData = device.keyPackage(scope, wallet)
            val original = event(space, "join-channel") {
                addProperty("control_id", snapshot.controlId); addProperty("channel_id", channel)
                add("key_package", JsonObject().apply {
                    addProperty("wallet", wallet.publicKey); addProperty("device", packageData.deviceId)
                    addProperty("package", packageData.packageBytes); addProperty("signer", packageData.signer)
                    addProperty("signature", packageData.walletSignature)
                })
            }
            data.addProperty("joining_crypto", encode(device.save())); data.addProperty("joining_channel", original.id)
            putChannelState(space, channel, data)
            queue(space, state, original)
        }
    }
    fun selectChannel(space: String, channel: String) = store.transaction {
        owned()
        val state = state(space)
        val journal = journal(space)
        require(journal.canAccessChannel(wallet.publicKey, channel))
        prepareChannelJoin(space, state, journal.snapshot, channel)
        state.addProperty("selected_channel", channel); put(stateKey(space), bytes(state))
    }

    data class Community(val id: String, val name: String, val channel: String, val joined: Boolean,
        val canInvite: Boolean, val pending: Boolean, val canSend: Boolean, val membershipRequested: Boolean = false, val unread: Long? = null, val hasUnread: Boolean? = null,
        val canManage: Boolean = false)
    data class Message(val id: String, val author: String, val createdAt: Long, val text: String?,
        val pending: Boolean, val attempted: Boolean, val missingKeys: Boolean, val rejected: Boolean = false, val position: Long? = null) {
        override fun toString() = "Private community message"
    }
    data class Invitation(val link: String, val confirmed: Boolean, val expiresAt: Long = 0, val expired: Boolean = false, val revoked: Boolean = false, val revocationPending: Boolean = false, val revocationFailed: Boolean = false) {
        override fun toString() = "Private community invitation"
    }
    fun communities(): List<Community> = store.transaction {
        if (get("identity") == null) return@transaction emptyList()
        owned()
        buildList {
            val prefix = "communities/"
            var cursor = prefix
            while (true) {
                val rows = page(prefix, cursor)
                rows.forEach { add(communityFromStore(it.value.toString(Charsets.UTF_8))) }
                if (rows.size < 50) break
                cursor = rows.last().key + '\u0000'
            }
        }
    }
    fun community(space: String, channel: String? = null): Community = store.transaction { owned(); communityFromStore(space, channel) }
    private fun SpaceClientStore.Transaction.communityFromStore(space: String, channelId: String? = null): Community {
        val state = state(space)
        val data = channelState(space, channelId ?: selectedChannel(state), state)
        val scope = SpaceCrypto.Scope.fromJson(data.getAsJsonObject("scope"))
        val journal = journal(space)
        val snapshot = journal.snapshot
        val channel = snapshot.channels[scope.channelId]
        val role = state.get("role")?.asString ?: snapshot.members[wallet.publicKey]?.name
        val adminPostsOnly = channel?.adminPostsOnly == true || channel?.profile?.kind == SpaceChannelKind.ANNOUNCEMENT
        val eligible = snapshot.channels.filter { (_, record) -> record.visibility == SpaceVisibility.PUBLIC ||
            record.devices.keys.any { it.split(':')[1] == wallet.publicKey } }.keys.map { channelState(space, it, state) }
        val counts = eligible.map { it.get("unread")?.asLong }
        val unread = counts.takeIf { it.isNotEmpty() && it.none { count -> count == null } }
            ?.fold(0L) { total, count -> Math.addExact(total, requireNotNull(count)) }
        val activity = eligible.map(::unreadActivity)
        val hasUnread = when {
            activity.any { it == true } -> true
            activity.isNotEmpty() && activity.all { it == false } -> false
            else -> null
        }
        return Community(space, state.text("name"), scope.channelId, state.get("joined").asBoolean,
            role in setOf(SpaceRole.OWNER.name, SpaceRole.ADMIN.name),
            state.has("starter_channels") || state.has("desired_listed") || page("$space/pending/", limit = 1).isNotEmpty(), state.get("joined").asBoolean && !state.has("control_pending") && !state.has("required_control") &&
                journal.canContributeChannel(wallet.publicKey, scope.channelId) &&
                (data.get("joined")?.asBoolean == true || channel?.visibility == SpaceVisibility.PUBLIC) &&
                channel?.retiredDevices?.isEmpty() == true && channel.archived != true && (!adminPostsOnly || role in setOf(SpaceRole.OWNER.name, SpaceRole.ADMIN.name)),
            !state.get("joined").asBoolean && state.has("joining_invitation"), unread, hasUnread,
            canManage = state.get("joined").asBoolean && !state.has("control_pending") && !state.has("required_control") &&
                snapshot.members[wallet.publicKey] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN))
    }
    fun profile(space: String): SpaceProfile = store.transaction {
        owned(); val state = state(space)
        state.getAsJsonObject("profile")?.let(SpaceProfile::fromJson) ?: SpaceProfile(state.text("name"))
    }
    data class Member(val publicKey: String, val role: SpaceRole)
    fun members(space: String): List<Member> = store.transaction {
        owned(); state(space)
        journal(space).snapshot.members.map { Member(it.key, it.value) }.sortedWith(compareBy<Member> { it.role.ordinal }.thenBy { it.publicKey })
    }
    fun draft(space: String, channel: String? = null): String = store.transaction {
        owned(); val current = state(space); val target = channel ?: selectedChannel(current)
        channelState(space, target, current)
        get(draftKey(space, target))?.toString(Charsets.UTF_8).orEmpty()
    }
    fun saveDraft(space: String, text: String, channel: String? = null) = store.transaction {
        owned(); val current = state(space); val target = channel ?: selectedChannel(current)
        channelState(space, target, current); require(text.toByteArray(Charsets.UTF_8).size <= 64 * 1024)
        put(draftKey(space, target), text.toByteArray(Charsets.UTF_8))
    }
    fun invitation(space: String): Invitation? = store.transaction {
        owned()
        val state = state(space)
        val link = state.get("invitation")?.asString ?: return@transaction null
        val invitation = SpaceInvitation.parse(link)
        val accepted = journal(space).snapshot.invitations[invitation.invitationId]
        val expiry = accepted?.expiresAt ?: state.get("invitation_expires_at")?.asLong ?: 0L
        Invitation(link, accepted != null, expiry, expiry > 0 && expiry <= clock.millis(),
            state.get("revoked_invitation")?.asString == invitation.invitationId,
            state.get("revoking_invitation")?.asString == invitation.invitationId,
            state.get("revocation_failed")?.asString == invitation.invitationId)
    }
    data class InvitationPreview(val spaceId: String, val name: String, val profile: SpaceProfile = SpaceProfile(name), val expiresAt: Long = 0, val rulesToken: String = "")
    data class PublicCommunity(val id: String, val profile: SpaceProfile)
    data class PublicDirectoryPage(val items: List<PublicCommunity>, val nextCursor: Long, val hasMore: Boolean)
    fun publicLink(space: String): String = store.transaction {
        owned(); state(space); val snapshot = journal(space).snapshot
        require(snapshot.visibility == SpaceVisibility.PUBLIC && snapshot.owner != null &&
            snapshot.channels.values.any { it.visibility == SpaceVisibility.PUBLIC })
        SpacePublicReference(node, service, space).link()
    }
    private fun publicReference(link: String): SpacePublicReference {
        val reference = SpacePublicReference.parse(link)
        if (reference.node != node) throw DifferentNode(reference.node)
        require(reference.serviceKey == service) { "This reference does not match the selected service" }
        return reference
    }
    /** Previously verified public context is readable without a new network
     * round trip. This never creates a community, joins or grants channel access. */
    fun cachedPublicLink(link: String): Community? {
        val reference = publicReference(link)
        return store.transaction {
            if (get("identity") == null) return@transaction null
            owned()
            if (get(stateKey(reference.spaceId)) == null) return@transaction null
            val snapshot = journal(reference.spaceId).snapshot
            if (snapshot.visibility != SpaceVisibility.PUBLIC || snapshot.publicProfile?.visibility != SpaceVisibility.PUBLIC)
                return@transaction null
            val community = communityFromStore(reference.spaceId)
            community.takeIf { snapshot.channels[it.channel]?.visibility == SpaceVisibility.PUBLIC }
        }
    }
    suspend fun openPublicLink(link: String): Community {
        return openPublic(publicReference(link).spaceId)
    }
    /** Public browsing stores no MLS device and does not enter My Spaces. */
    suspend fun openPublic(space: String): Community {
        require(space.matches(ID))
        val journal = SpaceJournal(space, service)
        val controls = mutableListOf<String>()
        var retainedBytes = 0L
        while (true) {
            val before = journal.snapshot.nextPosition
            val result = readPage(JsonObject().apply {
                addProperty("space_id", space); addProperty("stream", "control"); addProperty("cursor", before)
            })
            for (item in result.getAsJsonArray("items")) {
                val receipt = item.asString
                retainedBytes += receipt.toByteArray(Charsets.UTF_8).size
                require(retainedBytes <= 16L * 1024 * 1024) { "Community history exceeds this client's preview capacity" }
                journal.receive(receipt); controls += receipt
            }
            require(journal.snapshot.nextPosition == result.number("next_cursor"))
            if (journal.snapshot.nextPosition == before) break
        }
        val profile = requireNotNull(journal.snapshot.publicProfile)
        require(profile.visibility == SpaceVisibility.PUBLIC)
        val channel = journal.snapshot.channels.entries.firstOrNull { it.value.visibility == SpaceVisibility.PUBLIC }
            ?: throw IllegalArgumentException("This community has no public channel")
        store.transaction {
            owned(initialize = true)
            if (get(stateKey(space)) == null) {
                val state = JsonObject().apply {
                    add("scope", SpaceCrypto.Scope(service, space, channel.key, channel.value.generation).json())
                    addProperty("name", profile.name); add("profile", profile.json())
                    addProperty("joined", wallet.publicKey in journal.snapshot.members)
                    addProperty("next_outbox", 0); addProperty("content_cursor", 0)
                }
                put(stateKey(space), bytes(state))
                val accepted = journal(space)
                for (receipt in controls) accept(space, receipt, controlJournal = accepted)
                if (wallet.publicKey in journal.snapshot.members) put("communities/$space", space.toByteArray(Charsets.UTF_8))
            } else {
                val accepted = journal(space)
                controls.forEach { accept(space, it, controlJournal = accepted) }
            }
        }
        return community(space)
    }

    /** Direct public admission is a signed operation, never an owner-online MLS request. */
    fun joinPublic(space: String, acceptedRulesToken: String? = null) = store.transaction {
        owned()
        val current = state(space)
        val journal = journal(space)
        val scope = SpaceCrypto.Scope.fromJson(current.getAsJsonObject("scope"))
        require(journal.snapshot.visibility == SpaceVisibility.PUBLIC &&
            journal.snapshot.channels[scope.channelId]?.visibility == SpaceVisibility.PUBLIC)
        require(wallet.publicKey !in journal.snapshot.members && !current.get("joined").asBoolean)
        val profile = requireNotNull(journal.snapshot.publicProfile)
        require((acceptedRulesToken ?: "") == profile.publicRulesToken) { "Accept the community house rules before joining" }
        queue(space, current, event(space, "join-public") {
            addProperty("control_id", journal.snapshot.controlId); addProperty("channel_id", scope.channelId)
            addProperty("rules_token", profile.publicRulesToken)
        })
    }
    fun setListed(space: String, listed: Boolean) = store.transaction {
        owned(); val state = state(space); val snapshot = journal(space).snapshot
        require(snapshot.owner == wallet.publicKey && snapshot.visibility == SpaceVisibility.PUBLIC)
        state.addProperty("desired_listed", listed); put(stateKey(space), bytes(state))
        enqueueListing(space, state)
    }
    fun listingIntent(space: String): Boolean? = store.transaction { owned(); state(space).get("desired_listed")?.asBoolean }
    /** Keep the latest owner choice separate from an immutable in-flight original.
     * Only a definitive rejection permits a fresh proposal at the recovered head. */
    private fun SpaceClientStore.Transaction.enqueueListing(space: String, state: JsonObject) {
        val desired = state.get("desired_listed")?.asBoolean ?: return
        if (state.has("control_pending") || state.has("required_control")) return
        val snapshot = journal(space).snapshot
        require(snapshot.owner == wallet.publicKey && snapshot.visibility == SpaceVisibility.PUBLIC)
        if (requireNotNull(snapshot.publicProfile).listed == desired) {
            state.remove("desired_listed"); put(stateKey(space), bytes(state)); return
        }
        val original = event(space, "set-listed") {
            addProperty("control_id", snapshot.controlId); addProperty("listed", desired)
        }
        queue(space, state, original)
    }
    fun cachedDirectory(): List<PublicCommunity> = store.transaction {
        if (get("identity") == null) return@transaction emptyList()
        owned()
        val prefix = "directory/"
        var cursor = prefix
        buildList {
            while (true) {
                val rows = page(prefix, cursor)
                rows.forEach { add(PublicCommunity(it.key.removePrefix(prefix), SpaceProfile.fromJson(json(it.value)))) }
                if (rows.size < 50) break
                cursor = rows.last().key + '\u0000'
            }
        }
    }
    suspend fun discover(query: String = "", cursor: Long = 0): PublicDirectoryPage {
        require(query.codePointCount(0, query.length) <= 128 && cursor >= 0)
        val result = readPage(JsonObject().apply {
            addProperty("space_id", "0".repeat(64)); addProperty("stream", "directory")
            addProperty("cursor", cursor); addProperty("query", query.trim())
        })
        val communities = result.getAsJsonArray("items").map { item ->
            val value = item.asJsonObject
            val receipt = SaitoEnvelope.decode(value.text("creation"))
            val space = receipt.body.text("space_id")
            val journal = SpaceJournal(space, service)
            journal.receive(value.text("creation"))
            val profile = requireNotNull(journal.snapshot.publicProfile)
            require(profile.visibility == SpaceVisibility.PUBLIC)
            val listingEnvelope = value.get("listing")?.asString ?: value.text("creation")
            val listed = if (listingEnvelope == value.text("creation")) profile.listed else {
                val listing = SaitoEnvelope.decode(listingEnvelope)
                require(listing.author == service && listing.body.text("module") == "Spaces" && listing.body.number("version") == 1L &&
                    listing.body.text("request") == "receipt" && listing.body.text("stream") == "control" &&
                    listing.body.text("space_id") == space && listing.body.number("position") > 0)
                val command = SaitoEnvelope.decode(listing.body.text("event"))
                require(command.author == journal.snapshot.owner && command.recipients == listOf(command.author, service).distinct())
                require(command.body.text("module") == "Spaces" && command.body.number("version") == 1L &&
                    command.body.text("request") == "event" && command.body.text("kind") == "set-listed" &&
                    command.body.text("space_id") == space && command.body.text("operation_id").matches(ID) &&
                    command.body.text("control_id").matches(Regex("[0-9a-f]{128}")))
                command.body.get("listed").also { require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) }.asBoolean
            }
            require(listed)
            PublicCommunity(space, profile.copy(listed = listed))
        }
        val next = result.number("next_cursor")
        val more = result.get("has_more").also { require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) }.asBoolean
        require(next >= cursor && (!more || next > cursor))
        store.transaction {
            owned(initialize = true)
            cacheDirectory(communities)
        }
        return PublicDirectoryPage(communities, next, more)
    }

    /** Only disposable discovery metadata is evicted, never joined-community
     * state, private content, keys, originals or unsent writing. */
    private fun SpaceClientStore.Transaction.cacheDirectory(communities: List<PublicCommunity>) {
        val metadata = get("directory-cache/state")?.let(::json)
        var sequence = metadata?.number("sequence") ?: 0
        var count = metadata?.number("count") ?: 0
        require(count in 0..DIRECTORY_CACHE_LIMIT.toLong())
        for (community in communities) {
            val pointer = "directory-last/${community.id}"
            val previous = get(pointer)?.toString(Charsets.UTF_8)
            if (previous == null) count++ else {
                require(previous.startsWith("directory-order/") && previous.endsWith("/${community.id}"))
                remove(previous)
            }
            sequence = Math.addExact(sequence, 1)
            val order = "directory-order/${positionKey(sequence)}/${community.id}"
            put("directory/${community.id}", bytes(community.profile.json()))
            put(order, community.id.toByteArray(Charsets.UTF_8))
            put(pointer, order.toByteArray(Charsets.UTF_8))
        }
        while (count > DIRECTORY_CACHE_LIMIT) {
            val oldest = requireNotNull(page("directory-order/", limit = 1).firstOrNull())
            val space = oldest.value.toString(Charsets.UTF_8).also { require(it.matches(ID)) }
            remove("directory/$space"); remove("directory-last/$space"); remove(oldest.key)
            count--
        }
        put("directory-cache/state", bytes(JsonObject().apply {
            addProperty("sequence", sequence); addProperty("count", count)
        }))
    }
    private data class Preview(val invitation: SpaceInvitation, val profile: SpaceProfile, val controls: List<String>, val journal: SpaceJournal)

    fun revokeInvitation(space: String) = store.transaction {
        owned()
        val state = state(space)
        val invitation = SpaceInvitation.parse(state.text("invitation"))
        val journal = journal(space)
        require(journal.snapshot.members[wallet.publicKey] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN))
        require(invitation.invitationId in journal.snapshot.invitations)
        state.addProperty("revoking_invitation", invitation.invitationId); state.remove("revocation_failed")
        queue(space, state, event(space, "revoke-invite") {
            addProperty("control_id", journal.snapshot.controlId); addProperty("invite_id", invitation.invitationId)
        })
    }

    fun invite(space: String, expiresAfterMillis: Long = 604_800_000): String = store.transaction {
        owned()
        require(expiresAfterMillis in 60_000..31_536_000_000L)
        val state = state(space)
        val journal = journal(space)
        require(journal.snapshot.members[wallet.publicKey] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN))
        require(!state.has("control_pending")) { "A community change is awaiting confirmation" }
        val scope = SpaceCrypto.Scope.fromJson(state.getAsJsonObject("scope"))
        val bearer = SaitoWallet.create()
        val key = ByteArray(32).also(SecureRandom()::nextBytes)
        val operation = id()
        val expiry = Math.addExact(clock.millis(), expiresAfterMillis)
        val original = event(space, "invite", operation) {
            addProperty("control_id", journal.snapshot.controlId); addProperty("invite_key", bearer.publicKey)
            addProperty("expires_at", expiry)
            val profile = state.getAsJsonObject("profile")?.let(SpaceProfile::fromJson) ?: SpaceProfile(state.text("name"))
            addProperty("preview_format", "profile-v1"); addProperty("channel_id", scope.channelId)
            if (journal.snapshot.channels[scope.channelId]?.visibility == SpaceVisibility.PUBLIC)
                addProperty("rules_token", profile.publicRulesToken)
            addProperty("preview", SpaceInvitation.encryptPreview(scope, key, operation, profile.json().toString()))
        }
        val link = SpaceInvitation(node, scope, original.id, bearer, key).link()
        queue(space, state, original)
        state.addProperty("invitation", link); state.addProperty("invitation_expires_at", expiry)
        put(stateKey(space), bytes(state))
        link
    }

    /** Inspecting a valid invitation generates no MLS device or membership. */
    suspend fun preview(link: String): InvitationPreview = loadPreview(link).let { preview ->
        InvitationPreview(preview.invitation.scope.spaceId, preview.profile.name, preview.profile,
            requireNotNull(preview.journal.snapshot.invitations[preview.invitation.invitationId]).expiresAt,
            requireNotNull(preview.journal.snapshot.invitations[preview.invitation.invitationId]).rulesToken)
    }

    suspend fun join(link: String, acceptedRulesToken: String? = null): String {
        val preview = loadPreview(link)
        val invitation = preview.invitation
        val scope = invitation.scope
        if (preview.journal.snapshot.channels[scope.channelId]?.visibility == SpaceVisibility.PUBLIC) {
            val opened = openPublic(scope.spaceId)
            if (!opened.joined) joinPublic(scope.spaceId, acceptedRulesToken)
            return scope.spaceId
        }
        val saved = store.transaction { owned(initialize = true); get(stateKey(scope.spaceId))?.let(::json) }
        if (saved != null) {
            require(SpaceCrypto.Scope.fromJson(saved.getAsJsonObject("scope")) == scope)
            // Preserve the original device and its Welcome material. A fresh
            // invitation must never replace pending keys or unsent writing.
            store.transaction { preview.controls.forEach { accept(scope.spaceId, it) } }
            if (store.transaction { state(scope.spaceId).get("joined").asBoolean }) return scope.spaceId
        }
        require(preview.profile.houseRules.isEmpty() || acceptedRulesToken ==
            requireNotNull(preview.journal.snapshot.invitations[invitation.invitationId]).rulesToken) { "Accept the community house rules before joining" }
        require(wallet.publicKey !in preview.journal.snapshot.members) { "This identity is already a member; restore its community keys" }
        val previous = saved?.let { SpaceCrypto.restore(decode(it.text("crypto"))) }
        val device = when {
            previous == null -> SpaceCrypto.device(wallet.publicKey, id())
            previous.scope == null -> previous
            else -> saved.get("joining_crypto")?.asString?.let { SpaceCrypto.restore(decode(it)) }
                ?: SpaceCrypto.device(wallet.publicKey, id())
        }
        val keyPackage = device.keyPackage(scope, wallet)
        val operation = id()
        val original = event(scope.spaceId, "join", operation) {
            addProperty("membership_control", requireNotNull(preview.journal.snapshot.controlId))
            addProperty("channel_id", scope.channelId); addProperty("control_id", invitation.invitationId)
            addProperty("invite_id", invitation.invitationId); addProperty("rules_token", requireNotNull(preview.journal.snapshot.invitations[invitation.invitationId]).rulesToken)
            add("key_package", JsonObject().apply {
                addProperty("wallet", wallet.publicKey); addProperty("device", keyPackage.deviceId)
                addProperty("package", keyPackage.packageBytes); addProperty("signer", keyPackage.signer)
                addProperty("signature", keyPackage.walletSignature)
            })
            addProperty("invite_proof", invitation.joinProof(wallet.publicKey, operation, keyPackage.walletSignature))
        }
        store.transaction {
            owned(initialize = true)
            if (saved == null) for (receipt in preview.controls) {
                val position = SaitoEnvelope.decode(receipt).body.number("position")
                put("${scope.spaceId}/control/${positionKey(position)}", receipt.toByteArray(Charsets.UTF_8))
            }
            val state = (if (saved == null) JsonObject().apply {
                add("scope", scope.json()); addProperty("name", preview.profile.name); add("profile", preview.profile.json()); addProperty("crypto", encode(device.save()))
                addProperty("joined", false); addProperty("next_outbox", 0); addProperty("content_cursor", 0)
            } else state(scope.spaceId)).apply { addProperty("joining_invitation", link) }
            // Keep received history intact while a new, separately durable
            // device waits for its own accepted Welcome after departure.
            if (previous?.scope != null) state.addProperty("joining_crypto", encode(device.save()))
            queue(scope.spaceId, state, original)
        }
        return scope.spaceId
    }

    /** Valid explicit join requests are admitted by the existing owner/admin.
     * Both MLS branches and the original commit are durable before transport. */
    private data class DeferredJoin(val key: String, val receipt: String)

    /** Keep unavailable-channel requests durable without holding up other channels. */
    private fun SpaceClientStore.Transaction.deferredJoin(space: String): DeferredJoin? {
        val journal = journal(space)
        val state = state(space)
        for ((channel, record) in journal.snapshot.channels) {
            if (record.visibility != SpaceVisibility.PRIVATE || !journal.canAccessChannel(wallet.publicKey, channel) ||
                channelState(space, channel, state).get("joined")?.asBoolean != true) continue
            val prefix = "$space/deferred-join/$channel/"
            var cursor = prefix
            while (true) {
                val rows = page(prefix, cursor)
                for (row in rows) {
                    val receipt = row.value.toString(Charsets.UTF_8)
                    val received = SaitoEnvelope.decode(receipt)
                    require(received.author == service && received.body.text("space_id") == space && received.body.text("stream") == "join")
                    val original = SaitoEnvelope.decode(received.body.text("event"))
                    require(original.body.text("space_id") == space && original.body.text("channel_id") == channel)
                    val channelOnly = original.body.text("kind") == "join-channel"
                    val invitation = if (channelOnly) null else journal.snapshot.invitations[original.body.text("invite_id")]
                    val completed = if (channelOnly) !journal.canJoinChannel(original.author, channel, original.body.text("control_id"))
                        else original.author in journal.snapshot.members || invitation == null || invitation.expiresAt <= clock.millis() ||
                            !journal.hasCurrentMembershipIntent(original.author, original.body.get("membership_control")?.asString)
                    if (completed) remove(row.key) else return DeferredJoin(row.key, receipt)
                }
                if (rows.size < 50) break
                cursor = rows.last().key + '\u0000'
            }
        }
        return null
    }

    suspend fun admitPending(space: String) {
        val state = store.transaction { owned(); state(space) }
        if (!state.get("joined").asBoolean) return
        val role = store.transaction { journal(space).snapshot.members[wallet.publicKey] }
        if (role !in setOf(SpaceRole.OWNER, SpaceRole.ADMIN)) return
        if (store.transaction { page("$space/pending/", limit = 1).isNotEmpty() }) return
        syncControls(space)
        if (store.transaction { enqueueAccessRotation(space, state(space)) }) return
        if (store.transaction { journal(space).snapshot.members[wallet.publicKey] } !in setOf(SpaceRole.OWNER, SpaceRole.ADMIN)) return
        while (true) {
            if (store.transaction { page("$space/pending/", limit = 1).isNotEmpty() }) return
            val deferred = store.transaction { deferredJoin(space) }
            val cursor = store.transaction { state(space).get("join_cursor")?.asLong ?: 0 }
            val result = if (deferred == null) readPage(JsonObject().apply {
                addProperty("space_id", space); addProperty("stream", "join"); addProperty("cursor", cursor)
            }) else null
            val next = result?.number("next_cursor")?.also { require(it >= cursor) } ?: cursor
            val request = deferred?.receipt ?: result?.getAsJsonArray("items")?.firstOrNull()?.asString
            if (request == null) {
                store.transaction {
                    owned()
                    val current = state(space)
                    require((current.get("join_cursor")?.asLong ?: 0) == cursor)
                    current.addProperty("join_cursor", next); put(stateKey(space), bytes(current))
                }
                if (next == cursor) return
                continue
            }
            val waitingForKeys = store.transaction {
                owned()
                if (page("$space/pending/", limit = 1).isNotEmpty()) return@transaction false
                val journal = journal(space)
                val received = SaitoEnvelope.decode(request)
                require(received.author == service && received.body.text("stream") == "join" && received.body.text("space_id") == space &&
                    received.body.text("module") == "Spaces" && received.body.text("request") == "receipt" && received.body.number("version") == 1L)
                val position = received.body.number("position").also { require(it >= 0 && (deferred != null || it >= cursor && it < next)) }
                val join = journal.verifyJoin(received.body.text("event"), clock.millis())
                val current = state(space)
                require((current.get("join_cursor")?.asLong ?: 0) == cursor)
                current.addProperty("join_cursor", if (deferred == null) position else cursor)
                val target = channelState(space, join.channelId, current)
                val deferredKey = "$space/deferred-join/${join.channelId}/${positionKey(position)}"
                if (deferred != null) require(deferred.key == deferredKey && get(deferredKey)?.toString(Charsets.UTF_8) == request)
                if (target.get("joined")?.asBoolean != true || journal.snapshot.channels.getValue(join.channelId).retiredDevices.isNotEmpty()) {
                    val previous = get(deferredKey)
                    require(previous == null || previous.toString(Charsets.UTF_8) == request)
                    put(deferredKey, request.toByteArray(Charsets.UTF_8))
                    current.addProperty("join_cursor", Math.addExact(position, 1))
                    put(stateKey(space), bytes(current))
                    return@transaction true
                }
                val crypto = SpaceCrypto.restore(decode(target.text("crypto")))
                require(crypto.scope == join.keyPackage.scope)
                val record = journal.snapshot.channels.getValue(join.channelId)
                val profile = if (record.allowedMembers != null)
                    requireNotNull(channelProfile(space, join.channelId, current, target, record)) else null
                val admission = crypto.admit(join.keyPackage)
                val historyDeliveries = mutableListOf<SaitoTransaction>()
                var from = 0L
                while (true) {
                    val history = admission.session.archiveHistory(wallet, join.keyPackage.credential, id(),
                        requireNotNull(journal.snapshot.controlId), clock.millis(), from)
                    historyDeliveries += history.transaction
                    from = history.nextEpoch ?: break
                }
                val original = event(space, "admit") {
                    addProperty("control_id", journal.snapshot.controlId); addProperty("channel_id", join.channelId)
                    addProperty("epoch", admission.session.epoch); addProperty("join", join.envelope)
                    addProperty("welcome", admission.welcome); addProperty("commit", admission.commit)
                    add("devices", devices(admission.bindings))
                    add("history_deliveries", JsonArray().apply { historyDeliveries.forEach { add(it.id) } })
                    if (profile != null) {
                        val metadata = admission.session.publish(wallet, "channel-profile", id(), requireNotNull(journal.snapshot.controlId),
                            JsonObject().apply {
                                addProperty("version", 1); addProperty("channel_id", join.channelId); add("profile", profile.json())
                            }.toString(), clock.millis())
                        addProperty("channel_metadata", encode(metadata.envelope().toByteArray(Charsets.UTF_8)))
                    }
                }
                queue(space, current, original, admission.session, admission.afterRejection,
                    if (deferred == null) Math.addExact(position, 1) else cursor, historyDeliveries)
                false
            }
            if (!waitingForKeys) return
        }
    }

    private suspend fun loadPreview(link: String): Preview {
        val invitation = SpaceInvitation.parse(link)
        require(invitation.node == node && invitation.scope.serviceKey == service) { "Invitation belongs to a different community node" }
        val journal = SpaceJournal(invitation.scope.spaceId, service)
        val controls = mutableListOf<String>()
        var retainedBytes = 0L
        while (true) {
            val before = journal.snapshot.nextPosition
            val page = readPage(JsonObject().apply {
                addProperty("space_id", invitation.scope.spaceId); addProperty("stream", "invitation")
                addProperty("invite_id", invitation.invitationId); addProperty("cursor", before)
            }, invitation)
            for (value in page.getAsJsonArray("items")) {
                val receipt = value.asString
                retainedBytes += receipt.toByteArray(Charsets.UTF_8).size
                require(retainedBytes <= 16L * 1024 * 1024) { "Invitation history exceeds this client's preview capacity" }
                journal.receive(receipt); controls += receipt
            }
            require(journal.snapshot.nextPosition == page.number("next_cursor"))
            if (journal.snapshot.nextPosition == before) break
        }
        val authorized = requireNotNull(journal.snapshot.invitations[invitation.invitationId])
        require(authorized.expiresAt > clock.millis() && invitation.scope.channelId in journal.snapshot.channels)
        val original = controls.asSequence().map { SaitoEnvelope.decode(SaitoEnvelope.decode(it).body.text("event")) }
            .first { it.id == invitation.invitationId }
        val text = invitation.preview(original.body.text("preview"), original.body.text("operation_id"))
        val profile = if (original.body.get("preview_format")?.asString == "profile-v1")
            SpaceProfile.fromJson(json(text.toByteArray(Charsets.UTF_8))) else SpaceProfile(text)
        require(invitation.scope.channelId == authorized.channelId)
        if (journal.snapshot.channels[invitation.scope.channelId]?.visibility == SpaceVisibility.PUBLIC)
            require(profile.publicRulesToken == authorized.rulesToken) { "Invitation rules do not match its signed authority" }
        return Preview(invitation, profile, controls, journal)
    }

    fun create(name: String): String = create(SpaceProfile(name))
    fun create(profile: SpaceProfile, channels: List<SpaceChannelProfile> = listOf(SpaceChannelProfile("general"))): String {
        require(channels.size in 1..32) { "Choose between one and thirty-two starter channels" }
        require(channels.map { it.category.lowercase(java.util.Locale.ROOT) to it.name.lowercase(java.util.Locale.ROOT) }.distinct().size == channels.size) {
            "Starter channel names must be distinct within a category"
        }
        val first = channels.first()
        val scope = SpaceCrypto.Scope(service, id(), id())
        val crypto = SpaceCrypto.device(wallet.publicKey, id()).create(scope)
        // Metadata uses the same authenticated archive envelope, with a separate
        // kind and fixed genesis predecessor. It is never sent as plaintext.
        val metadata = crypto.publish(wallet, "metadata", id(), "0".repeat(128), profile.json().toString(), clock.millis())
        val channelMetadata = if (profile.visibility == SpaceVisibility.PRIVATE) crypto.publish(wallet, "channel-profile", id(), "0".repeat(128),
            JsonObject().apply { addProperty("version", 1); addProperty("channel_id", scope.channelId); add("profile", first.json()) }.toString(), clock.millis()) else null
        val original = event(scope.spaceId, "create") {
            addProperty("visibility", if (profile.visibility == SpaceVisibility.PUBLIC) "public" else "private")
            if (profile.visibility == SpaceVisibility.PUBLIC) {
                add("public_profile", profile.json()); add("channel_profile", first.json())
            } else addProperty("channel_metadata", encode(requireNotNull(channelMetadata).envelope().toByteArray(Charsets.UTF_8)))
            addProperty("admin_posts_only", first.kind == SpaceChannelKind.ANNOUNCEMENT)
            addProperty("channel_kind", first.kind.name.lowercase(java.util.Locale.ROOT))
            addProperty("channel_id", scope.channelId)
            addProperty("channel_visibility", if (profile.visibility == SpaceVisibility.PUBLIC) "public" else "private")
            addProperty("key_generation", scope.generation); addProperty("epoch", 0)
            addProperty("metadata", encode(metadata.envelope().toByteArray(Charsets.UTF_8)))
            add("devices", devices(crypto.bindings))
        }
        store.transaction {
            owned(initialize = true)
            val state = JsonObject().apply {
                add("scope", scope.json()); addProperty("name", profile.name); add("profile", profile.json())
                addProperty("crypto", encode(crypto.save())); add("channel_profile", first.json())
                if (channels.size > 1) add("starter_channels", JsonArray().apply {
                    channels.drop(1).forEach { channel -> add(JsonObject().apply {
                        add("scope", SpaceCrypto.Scope(service, scope.spaceId, id()).json()); add("profile", channel.json())
                    }) }
                })
                addProperty("joined", false); addProperty("next_outbox", 0); addProperty("content_cursor", 0)
            }
            require(get(stateKey(scope.spaceId)) == null)
            queue(scope.spaceId, state, original)
        }
        return scope.spaceId
    }

    /** Key maintenance is derived from accepted policy, never from a peer's
     * unconfirmed removal request. One original and both MLS outcomes are durable. */
    private fun SpaceClientStore.Transaction.enqueueAccessRotation(space: String, state: JsonObject): Boolean {
        if (!state.get("joined").asBoolean || state.has("control_pending") || state.has("required_control")) return false
        // Accepted controls maintain this hint in the same transaction as their
        // receipt. Legacy stores derive it once. Idle flushes must not reverify
        // the entire history while a local form waits for the shared store.
        if (state.get("access_rotation_required")?.asBoolean == false) return false
        val journal = journal(space)
        val required = journal.snapshot.channels.values.any { it.retiredDevices.isNotEmpty() }
        state.addProperty("access_rotation_required", required)
        put(stateKey(space), bytes(state))
        if (!required) return false
        val head = journal.snapshot.controlId ?: return false
        if (state.get("access_rotation_rejected_head")?.asString == head) return false
        for ((channel, record) in journal.snapshot.channels) {
            if (record.visibility != SpaceVisibility.PRIVATE || record.retiredDevices.isEmpty() || !journal.canAccessChannel(wallet.publicKey, channel)) continue
            val target = channelState(space, channel, state)
            if (target.get("joined")?.asBoolean != true || !target.has("crypto")) continue
            val keys = SpaceCrypto.restore(decode(target.text("crypto")))
            if (keys.credential in record.retiredDevices || keys.scope != SpaceCrypto.Scope(service, space, channel, record.generation) ||
                keys.epoch != record.epoch || keys.bindings != record.devices) continue
            val removed = record.retiredDevices.sorted().first()
            val changed = keys.remove(removed)
            val original = event(space, "rotate-access") {
                addProperty("control_id", head); addProperty("channel_id", channel); addProperty("removed_device", removed)
                addProperty("epoch", changed.session.epoch); addProperty("commit", changed.commit)
                add("devices", devices(changed.bindings))
            }
            queue(space, state, original, changed.session, changed.afterRejection)
            return true
        }
        return false
    }

    private fun SpaceClientStore.Transaction.enqueueOwnershipCompletion(space: String, state: JsonObject): Boolean {
        if (!state.get("joined").asBoolean || state.has("control_pending") || state.has("required_control") ||
            state.get("ownership_completion_required")?.asBoolean != true) return false
        val journal = journal(space)
        val offer = journal.snapshot.ownershipOffer ?: return false
        if (offer.member != wallet.publicKey || offer.acceptedId == null) return false
        val head = journal.snapshot.controlId ?: return false
        if (state.get("ownership_completion_rejected_head")?.asString == head) return false
        for ((channel, record) in journal.snapshot.channels) {
            if (record.visibility != SpaceVisibility.PRIVATE) continue
            val data = channelState(space, channel, state)
            if (data.get("joined")?.asBoolean != true || !data.has("crypto")) return false
            val keys = SpaceCrypto.restore(decode(data.text("crypto")))
            if (keys.scope != SpaceCrypto.Scope(service, space, channel, record.generation) || keys.epoch != record.epoch ||
                keys.bindings != record.devices || keys.credential in record.retiredDevices) return false
        }
        val original = event(space, "complete-owner") {
            addProperty("control_id", head); addProperty("proposal_id", offer.id); addProperty("acceptance_id", offer.acceptedId)
        }
        queue(space, state, original)
        return true
    }

    /** A local durable queue is not a remote sent/encrypted confirmation. */
    fun send(space: String, text: String, channel: String? = null): String = store.transaction {
        owned()
        val state = state(space)
        require(state.get("joined").asBoolean && !state.has("control_pending") && !state.has("required_control")) { "Community admission or key change is not confirmed" }
        val journal = journal(space)
        val data = channelState(space, channel ?: selectedChannel(state), state)
        val scope = SpaceCrypto.Scope.fromJson(data.getAsJsonObject("scope"))
        val record = requireNotNull(journal.snapshot.channels[scope.channelId])
        require(journal.canContributeChannel(wallet.publicKey, scope.channelId)) { "Private access is still being prepared" }
        require(!record.archived) { "This channel is archived" }
        if (record.adminPostsOnly || record.profile?.kind == SpaceChannelKind.ANNOUNCEMENT)
            require(journal.snapshot.members[wallet.publicKey] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN))
        val control = requireNotNull(journal.snapshot.controlId)
        val original = if (record.visibility == SpaceVisibility.PUBLIC) {
            require(text.isNotBlank() && text.toByteArray(Charsets.UTF_8).size <= 64 * 1024)
            event(space, "public-content") {
                addProperty("channel_id", scope.channelId); addProperty("control_id", control)
                addProperty("epoch", record.epoch); addProperty("key_generation", record.generation)
                addProperty("content_kind", "message"); addProperty("text", text)
            }
        } else {
            require(data.get("joined")?.asBoolean == true && record.retiredDevices.isEmpty()) { "Current channel keys are not available on this device" }
            val keys = SpaceCrypto.restore(decode(data.text("crypto")))
            require(keys.scope == scope && keys.epoch == record.epoch && keys.bindings == record.devices)
            keys.publish(wallet, "message", id(), control, text, clock.millis())
        }
        queue(space, state, original)
        val draft = draftKey(space, scope.channelId)
        if (get(draft)?.toString(Charsets.UTF_8) == text) remove(draft)
        original.id
    }

    /** On an uncertain response keep the exact signed original for reconciliation. */
    suspend fun flush(space: String) {
        val prefix = "$space/pending/"
        repeat(50) {
            val item = store.transaction {
                owned()
                if (page(prefix, limit = 1).isEmpty()) {
                    enqueueListing(space, state(space))
                    if (page(prefix, limit = 1).isEmpty()) enqueueAccessRotation(space, state(space))
                    if (page(prefix, limit = 1).isEmpty()) enqueueOwnershipCompletion(space, state(space))
                    if (page(prefix, limit = 1).isEmpty()) enqueueStarter(space, state(space))
                }
                val row = page(prefix, limit = 1).firstOrNull() ?: return@transaction null
                val prepared = json(row.value).apply { addProperty("attempted", true) }
                val value = bytes(prepared)
                put(row.key, value)
                SpaceClientStore.Entry(row.key, value)
            } ?: return
            val transaction = SaitoTransaction.restore(json(item.value).text("original"))
            val outbound = SaitoEnvelope.decode(transaction.envelope())
            require(outbound.author == wallet.publicKey)
            // Presentation observes the durable attempt before transport can
            // suspend. Signed read RPCs have no outbox change to project.
            onDeliveryAttempt(if (outbound.body.text("kind") in CONTROL_KINDS) Delivery.CONTROL else Delivery.CONTENT)
            val result = exchange(transaction)
            val received = SaitoEnvelope.decode(result)
            require(received.author == service && received.body.text("module") == "Spaces" && received.body.number("version") == 1L &&
                received.body.text("request") == "receipt" && received.body.text("space_id") == space &&
                received.body.text("event") == transaction.envelope())
            val authorization = received.body.get("authorized_control")?.asString
            if (received.body.text("stream") == "content" && authorization != null && store.transaction {
                    journal(space, through = authorization).snapshot.controlId != authorization
                }) syncControls(space)
            store.transaction {
                owned()
                require(get(item.key)?.contentEquals(item.value) == true) { "Community operation changed during delivery" }
                val received = SaitoEnvelope.decode(result)
                require(received.body.text("event") == transaction.envelope()) { "Community service returned a different original" }
                accept(space, result)
                remove(item.key)
                messageChannel(outbound.body)?.let { channel -> remove(messageIndex(space, channel, "pending") + item.key.substringAfterLast('/')) }
                remove("$space/operation/${transaction.id}")
            }
        }
    }

    private suspend fun syncControls(space: String) {
        while (true) {
            val before = store.transaction { owned(); state(space) }
            val invitation = before.get("joining_invitation")?.asString?.let(SpaceInvitation::parse)?.takeIf { link ->
                // Accepted membership outlives the capability that requested it.
                // Once that capability expires, use the actual wallet's member
                // read authority to recover its already accepted Welcome.
                store.transaction { journal(space).snapshot.invitations[link.invitationId]?.expiresAt ?: 0 } > clock.millis()
            }
            val controlCursor = store.transaction { journal(space).snapshot.nextPosition }
            suspend fun readControls(capability: SpaceInvitation?) = readPage(JsonObject().apply {
                addProperty("space_id", space); addProperty("stream", if (capability == null) "control" else "invitation")
                addProperty("cursor", controlCursor)
                capability?.let { addProperty("invite_id", it.invitationId) }
            }, capability)
            val controls = try { readControls(invitation) } catch (denied: ReadDenied) {
                if (invitation == null || denied.spaceId != space) throw denied
                // A revoked capability cannot undo an already accepted
                // admission. Only a fresh wallet-member read can recover it.
                readControls(null)
            }
            store.transaction {
                owned()
                val accepted = journal(space)
                controls.getAsJsonArray("items").forEach { accept(space, it.asString, controlJournal = accepted) }
                require(accepted.snapshot.nextPosition == controls.number("next_cursor"))
            }
            if (controls.number("next_cursor") == controlCursor) return
        }
    }

    /** Key grants are indexed separately from content, so a cold join does
     * not need to download the entire old conversation to obtain its keys. */
    private suspend fun refreshKeys(space: String, channel: String): Long {
        while (true) {
            val before = store.transaction { owned(); channelState(space, channel) }
            val keys = SpaceCrypto.restore(decode(before.text("crypto")))
            val cursor = before.get("key_cursor")?.asLong ?: 0
            val page = readPage(JsonObject().apply {
                addProperty("space_id", space); addProperty("stream", "keys"); addProperty("cursor", cursor)
                addProperty("channel_id", before.getAsJsonObject("scope").text("channel"))
                addProperty("recipient_credential", keys.credential)
            })
            for (item in page.getAsJsonArray("items")) ensureAuthorization(space, item.asString, before.getAsJsonObject("scope").text("channel"))
            store.transaction {
                owned()
                require((channelState(space, channel).get("key_cursor")?.asLong ?: 0) == cursor)
                var previous = cursor - 1
                for (item in page.getAsJsonArray("items")) {
                    val receipt = SaitoEnvelope.decode(item.asString)
                    val original = SaitoEnvelope.decode(receipt.body.text("event"))
                    val position = receipt.body.number("position")
                    require(position >= cursor && position > previous && original.body.text("recipient_credential") == keys.credential)
                    accept(space, item.asString); previous = position
                }
                val next = page.number("next_cursor")
                require(next == if (previous < cursor) cursor else Math.addExact(previous, 1))
                val updated = channelState(space, channel)
                updated.addProperty("key_cursor", next); putChannelState(space, channel, updated)
            }
            if (page.number("next_cursor") == cursor) return page.number("content_end")
        }
    }

    private data class AuthenticatedContent(val envelope: String, val receipt: SignedSaitoMessage, val original: SignedSaitoMessage)

    private suspend fun ensureAuthorization(space: String, envelope: String, expectedChannel: String,
        pageControls: MutableMap<String, SpaceJournal>? = null): AuthenticatedContent {
        val signed = SaitoEnvelope.decode(envelope)
        require(signed.author == service && signed.body.text("module") == "Spaces" && signed.body.number("version") == 1L &&
            signed.body.text("request") == "receipt" && signed.body.text("space_id") == space && signed.body.text("stream") == "content")
        val original = SaitoEnvelope.decode(signed.body.text("event"))
        val channel = signed.body.text("channel_id")
        require(original.body.text("space_id") == space && original.body.text("channel_id") == channel &&
            channel == expectedChannel) { "Community content belongs to a different channel" }
        val control = signed.body.get("authorized_control")?.asString ?: original.body.text("control_id")
        val authenticated = AuthenticatedContent(envelope, signed, original)
        if (pageControls?.containsKey(control) == true) return authenticated
        var authority = store.transaction { journal(space, through = control) }
        if (authority.snapshot.controlId != control) {
            syncControls(space)
            authority = store.transaction { journal(space, through = control) }
        }
        require(authority.snapshot.controlId == control)
        pageControls?.put(control, authority)
        return authenticated
    }

    private suspend fun contentPage(space: String, cursor: Long, until: Long? = null, channel: String, received: JsonObject? = null): Long {
        val result = received ?: readPage(JsonObject().apply {
            addProperty("space_id", space); addProperty("stream", "content")
            addProperty("channel_id", channel); addProperty("cursor", cursor)
        })
        val selected = result.getAsJsonArray("items").map { it.asString }.takeWhile {
            until == null || SaitoEnvelope.decode(it).body.number("position") < until
        }
        // Accepted control history is immutable. Reuse its verified authority
        // only within this bounded page; each receipt and original still has
        // its own signature, Space/channel and content permission checks.
        val pageControls = mutableMapOf<String, SpaceJournal>()
        val authenticated = mutableMapOf<String, AuthenticatedContent>()
        for (item in selected) authenticated[item] = ensureAuthorization(space, item, channel, pageControls)
        return store.transaction {
            owned()
            var next = cursor
            for (item in selected) {
                val verified = requireNotNull(authenticated[item])
                require(verified.receipt.body.number("position") == next)
                accept(space, item, pageControls, authenticated = verified); next = Math.addExact(next, 1)
            }
            require(next == minOf(result.number("next_cursor"), until ?: Long.MAX_VALUE))
            next
        }
    }

    suspend fun older(space: String, channel: String? = null): Long {
        val targetChannel = store.transaction { owned(); val current = state(space); channel ?: selectedChannel(current) }
        val upper = store.transaction { channelState(space, targetChannel).get("history_from")?.asLong ?: 0 }
        val start = (upper - 50).coerceAtLeast(0)
        var cursor = start
        while (cursor < upper) {
            val next = contentPage(space, cursor, upper, targetChannel)
            require(next > cursor) { "Earlier conversation history is unavailable" }
            cursor = next
        }
        store.transaction {
            val current = channelState(space, targetChannel)
            require((current.get("history_from")?.asLong ?: 0) == upper)
            current.addProperty("history_from", start); putChannelState(space, targetChannel, current)
        }
        return start
    }

    suspend fun refresh(space: String, channel: String? = null) {
        val targetChannel = store.transaction { owned(); val current = state(space); channel ?: selectedChannel(current) }
        syncControls(space)
        store.transaction {
            owned()
            val state = state(space)
            val journal = journal(space)
            val preparingHandover = journal.snapshot.ownershipOffer?.let { it.member == wallet.publicKey && it.acceptedId != null } == true
            if (state.get("joined").asBoolean) journal.snapshot.channels.forEach { (channel, record) ->
                if ((record.allowedMembers != null || preparingHandover) && journal.canAccessChannel(wallet.publicKey, channel))
                    prepareChannelJoin(space, state, journal.snapshot, channel)
            }
        }
        val currentChannel = store.transaction { requireNotNull(journal(space).snapshot.channels[targetChannel]) }
        if (currentChannel.visibility != SpaceVisibility.PUBLIC && !store.transaction { channelState(space, targetChannel).get("joined")?.asBoolean == true }) return
        val before = store.transaction { channelState(space, targetChannel) }
        val first = !before.has("content_loaded")
        val leading = if (currentChannel.visibility == SpaceVisibility.PUBLIC && first) readPage(JsonObject().apply {
            addProperty("space_id", space); addProperty("stream", "content")
            addProperty("channel_id", targetChannel); addProperty("cursor", 0)
        }) else null
        val end = if (currentChannel.visibility == SpaceVisibility.PUBLIC) leading?.number("content_end") else refreshKeys(space, targetChannel)
        val cursor = if (first) (requireNotNull(end) - 50).coerceAtLeast(0) else before.number("content_cursor")
        val next = contentPage(space, cursor, channel = targetChannel, received = leading?.takeIf { cursor == 0L })
        store.transaction {
            val current = channelState(space, targetChannel)
            require(current.number("content_cursor") == before.number("content_cursor"))
            current.addProperty("content_cursor", next); current.addProperty("content_loaded", true)
            if (first) current.addProperty("history_from", cursor)
            putChannelState(space, targetChannel, current)
        }
    }

    data class HistoryWindow(val from: Long, val loadedFrom: Long, val hasEarlier: Boolean, val hasLater: Boolean, val loaded: Boolean = false)
    fun historyWindow(space: String, from: Long? = null, channel: String? = null): HistoryWindow = store.transaction {
        owned()
        val state = state(space)
        val current = channelState(space, channel ?: selectedChannel(state), state)
        val last = current.get("last_content")?.asLong ?: 0
        val start = from ?: maxOf((last - 49).coerceAtLeast(0), current.get("history_from")?.asLong ?: 0)
        require(start >= 0)
        HistoryWindow(start, current.get("history_from")?.asLong ?: start, start > 0, last >= start + 50, current.get("content_loaded")?.asBoolean == true)
    }

    /** Bounded local page, including explicit unavailable keys and durable
     * queued originals. The UI never infers receipt from an optimistic row. */
    fun timeline(space: String, from: Long? = null, limit: Int = 50, channel: String? = null): List<Message> = store.transaction {
        owned(); require(limit in 1..50)
        val state = state(space)
        ensureMessageIndexes(space, state)
        val targetChannel = channel ?: selectedChannel(state)
        val data = channelState(space, targetChannel, state)
        val crypto by lazy { SpaceCrypto.restore(decode(data.text("crypto"))) }
        val prefix = contentPrefix(space, targetChannel)
        val last = data.get("last_content")?.asLong ?: 0
        val start = from ?: maxOf((last - limit + 1).coerceAtLeast(0), data.get("history_from")?.asLong ?: 0)
        fun message(envelope: String, pending: Boolean, attempted: Boolean, rejected: Boolean = false, position: Long? = null): Message? {
            val original = SaitoEnvelope.decode(envelope)
            if (original.body.get("channel_id")?.asString != targetChannel) return null
            if (original.body.text("kind") !in setOf("content", "public-content") || original.body.text("content_kind") != "message") return null
            if (original.body.text("kind") == "public-content") return Message(original.id, original.author, original.createdAt,
                original.body.text("text"), pending, attempted, missingKeys = false, rejected = rejected, position = position)
            var missing = false
            val text = try { crypto.read(envelope).text }
                catch (_: SpaceCrypto.MissingKeys) { missing = true; null }
                catch (_: java.security.GeneralSecurityException) { null }
            return Message(original.id, original.author, original.createdAt, text, pending, attempted, missing, rejected, position)
        }
        val confirmed = page(prefix, prefix + positionKey(start), limit).mapNotNull { row ->
            message(originalEnvelope(row.value), pending = false, attempted = true, position = json(row.value).number("position"))
        }
        val pending = page(messageIndex(space, targetChannel, "pending"), limit = limit).mapNotNull { row ->
            val saved = json(requireNotNull(get(row.value.toString(Charsets.UTF_8))))
            message(SaitoTransaction.restore(saved.text("original")).envelope(), pending = true, attempted = saved.get("attempted").asBoolean)
        }
        val rejected = page(messageIndex(space, targetChannel, "rejected"), limit = limit).mapNotNull { row ->
            val saved = json(requireNotNull(get(row.value.toString(Charsets.UTF_8))))
            message(SaitoTransaction.restore(saved.text("original")).envelope(), pending = false, attempted = true, rejected = true)
        }
        (confirmed + pending + rejected).distinctBy { it.id }
    }
    fun messages(space: String, channel: String? = null): List<String> = timeline(space, channel = channel).filter { !it.pending && !it.rejected }.mapNotNull { it.text }

    data class ReadingAnchor(val messageId: String, val position: Long, val offset: Int, val windowFrom: Long) {
        init {
            require(messageId.matches(Regex("[0-9a-f]{128}")) && position >= 0 && windowFrom >= 0 &&
                windowFrom <= position)
        }
    }
    fun readingAnchor(space: String, channel: String? = null): ReadingAnchor? = store.transaction {
        owned(); val parent = state(space); val target = channel ?: selectedChannel(parent)
        channelState(space, target, parent)
        val saved = get("$space/channels/$target/reading")?.let(::json) ?: return@transaction null
        require(saved.number("version") == 1L)
        val offset = saved.get("offset").also { require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber) }.asString.toInt()
        ReadingAnchor(saved.text("message"), saved.number("position"), offset, saved.number("window_from"))
    }
    fun saveReadingAnchor(space: String, channel: String, anchor: ReadingAnchor?) = store.transaction {
        owned(); channelState(space, channel)
        val key = "$space/channels/$channel/reading"
        if (anchor == null) remove(key) else {
            val record = json(requireNotNull(get(contentPrefix(space, channel) + positionKey(anchor.position))))
            val original = SaitoEnvelope.decode(record.text("event"))
            require(record.number("position") == anchor.position && original.id == anchor.messageId &&
                original.body.text("space_id") == space && original.body.text("channel_id") == channel &&
                original.body.text("content_kind") == "message")
            put(key, bytes(JsonObject().apply {
                addProperty("version", 1); addProperty("message", anchor.messageId); addProperty("position", anchor.position)
                addProperty("offset", anchor.offset); addProperty("window_from", anchor.windowFrom)
            }))
        }
    }

    /** A known remaining message keeps ordinary activity visible even while
     * its exact count is unknown after a partial read. */
    private fun unreadActivity(data: JsonObject): Boolean? = data.get("unread_message_end")?.asLong?.let {
        it > (data.get("read_cursor")?.asLong ?: 0)
    }
    /** A read cursor refers to real, authenticated local content, never a timer. */
    fun markRead(space: String, channel: String, position: Long) = store.transaction {
        owned(); require(position >= 0)
        val data = channelState(space, channel)
        val record = json(requireNotNull(get(contentPrefix(space, channel) + positionKey(position))))
        val original = SaitoEnvelope.decode(record.text("event"))
        require(original.body.text("channel_id") == channel && original.body.text("content_kind") == "message")
        val cursor = Math.addExact(position, 1)
        if (cursor > (data.get("read_cursor")?.asLong ?: 0)) {
            data.addProperty("read_cursor", cursor)
            if (data.get("unread_message_end")?.asLong?.let { cursor >= it } == true) data.addProperty("unread", 0)
            else data.remove("unread")
            putChannelState(space, channel, data)
        }
    }
    /** Bounded summaries count other authors' actual message records. They do
     * not download history, decrypt content or treat key grants as messages. */
    suspend fun refreshUnread(space: String) {
        syncControls(space)
        val targets = store.transaction {
            owned(); state(space); val snapshot = journal(space).snapshot
            if (wallet.publicKey !in snapshot.members) return@transaction emptyList<String>()
            snapshot.channels.filter { (_, record) -> record.visibility == SpaceVisibility.PUBLIC ||
                record.devices.keys.any { it.split(':')[1] == wallet.publicKey } }.keys.toList()
        }
        for (batch in targets.chunked(50)) {
            val positions = store.transaction { owned(); JsonObject().apply {
                batch.forEach { channel -> addProperty(channel, channelState(space, channel).get("read_cursor")?.asLong ?: 0) }
            } }
            val page = readPage(JsonObject().apply {
                addProperty("space_id", space); addProperty("stream", "heads"); addProperty("cursor", 0); add("positions", positions)
            })
            require(page.number("next_cursor") == 0L)
            val items = page.getAsJsonArray("items").map { it.asJsonObject }
            require(items.map { it.text("channel_id") }.toSet() == batch.toSet() && items.size == batch.size)
            store.transaction {
                owned()
                for (item in items) {
                    val channel = item.text("channel_id")
                    val end = item.number("end_cursor"); val count = item.number("unread_count")
                    val messageEnd = item.number("message_end")
                    require(end >= positions.number(channel) && messageEnd <= end &&
                        (if (count == 0L) messageEnd == 0L else messageEnd > positions.number(channel) && count <= messageEnd - positions.number(channel)))
                    val data = channelState(space, channel)
                    // A locally advanced read marker wins over a held older response.
                    if ((data.get("read_cursor")?.asLong ?: 0) != positions.number(channel) ||
                        end < (data.get("unread_end")?.asLong ?: 0)) continue
                    data.addProperty("unread", count); data.addProperty("unread_end", end)
                    data.addProperty("unread_message_end", messageEnd)
                    data.addProperty("unread_checked_at", clock.millis()); putChannelState(space, channel, data)
                }
            }
        }
    }

    private suspend fun readPage(scope: JsonObject, invitation: SpaceInvitation? = null): JsonObject {
        val requestId = id()
        fun request(kind: String, nonce: String? = null) = SaitoTransaction.create(wallet, JsonObject().apply {
            addProperty("module", "Spaces"); addProperty("request", kind); addProperty("version", 1)
            addProperty("request_id", requestId); add("scope", scope.deepCopy())
            nonce?.let {
                addProperty("nonce", it)
                invitation?.let { invite -> addProperty("invite_proof", invite.proof(wallet.publicKey, nonce)) }
            }
        }, clock.millis(), listOf(service))
        fun verified(envelope: String, kind: String?): JsonObject {
            val signed = SaitoEnvelope.decode(envelope)
            val body = signed.body
            require(signed.author == service && signed.recipients == listOf(service, wallet.publicKey).distinct())
            require(body.text("module") == "Spaces" && body.number("version") == 1L && (kind == null || body.text("request") == kind))
            require(body.text("request_id") == requestId && body.text("caller") == wallet.publicKey && body.getAsJsonObject("scope") == scope)
            return body
        }
        val challenge = verified(exchange(request("challenge")), "challenge")
        require(challenge.number("expires_at") > clock.millis()) { "Community read challenge expired" }
        val nonce = challenge.text("nonce").also { require(it.matches(ID)) }
        val result = verified(exchange(request("read", nonce)), null)
        require(result.text("nonce") == nonce)
        if (result.text("request") == "denied") {
            val problem = when (result.text("reason")) {
                "expired" -> ReadProblem.EXPIRED
                "unavailable" -> ReadProblem.UNAVAILABLE
                else -> throw IllegalArgumentException("Unknown community read disposition")
            }
            throw ReadDenied(scope.text("space_id"), problem)
        }
        require(result.text("request") == "page" && result.getAsJsonArray("items").size() <= 50)
        return result
    }

    private fun SpaceClientStore.Transaction.accept(space: String, envelope: String,
        pageControls: Map<String, SpaceJournal> = emptyMap(), controlJournal: SpaceJournal? = null,
        authenticated: AuthenticatedContent? = null) {
        require(authenticated == null || authenticated.envelope == envelope)
        val receipt = authenticated?.receipt ?: SaitoEnvelope.decode(envelope)
        val body = receipt.body
        require(receipt.author == service && body.text("module") == "Spaces" && body.number("version") == 1L &&
            body.text("request") == "receipt" && body.text("space_id") == space)
        val original = authenticated?.original ?: SaitoEnvelope.decode(body.text("event"))
        val state = state(space)
        when (body.text("stream")) {
            "control" -> {
                val journal = controlJournal ?: journal(space)
                journal.receive(envelope)
                val position = body.number("position")
                val key = "$space/control/${positionKey(position)}"
                val existing = get(key)
                if (existing != null) require(SaitoEnvelope.decode(existing.toString(Charsets.UTF_8)).body.text("event") == body.text("event"))
                else {
                    when (original.body.text("kind")) {
                        "create" -> {
                            if (original.author == wallet.publicKey) {
                                if (state.has("crypto")) {
                                    val keys = SpaceCrypto.restore(decode(state.text("crypto")))
                                    require(journal.snapshot.channels.values.single().devices == keys.bindings)
                                } else require(journal.snapshot.channels.values.single().visibility == SpaceVisibility.PUBLIC)
                                state.addProperty("joined", true)
                            }
                        }
                        "create-channel" -> {
                            recordChannelCreationResult(state, original, ChangeStatus.ACCEPTED)
                            val channel = original.body.text("channel_id")
                            val record = requireNotNull(journal.snapshot.channels[channel])
                            val plan = state.getAsJsonArray("starter_channels")
                            if (original.author == wallet.publicKey && plan?.firstOrNull()?.asJsonObject?.getAsJsonObject("scope")?.text("channel") == channel) {
                                plan.remove(0)
                                if (plan.isEmpty) state.remove("starter_channels")
                            }
                            if (get(channelStateKey(space, channel)) == null) put(channelStateKey(space, channel), bytes(JsonObject().apply {
                                add("scope", SpaceCrypto.Scope(service, space, channel, record.generation).json())
                                record.profile?.let { add("channel_profile", it.json()) }
                                addProperty("content_cursor", 0)
                            }))
                        }
                        "grant-channel-access" -> recordChannelAccessResult(space, state, original, ChangeStatus.ACCEPTED)
                        "revoke-channel-access" -> {
                            val channelId = original.body.text("channel_id")
                            val target = channelState(space, channelId, state)
                            val record = requireNotNull(journal.snapshot.channels[channelId])
                            val current = target.get("crypto")?.asString?.let { SpaceCrypto.restore(decode(it)) }
                            val pending = get("$space/operation/${original.id}")?.toString(Charsets.UTF_8)?.let { get(it) }?.let(::json)
                            if (original.body.text("member") == wallet.publicKey) {
                                target.addProperty("joined", false); target.remove("joining_channel"); target.remove("joining_crypto")
                            } else {
                                val next = when {
                                    pending?.has("candidate") == true -> SpaceCrypto.restore(decode(pending.text("candidate")))
                                    current == null || target.get("joined")?.asBoolean != true -> null
                                    current.epoch == record.epoch -> current
                                    else -> current.receiveCommit(original.body.text("commit"),
                                        current.bindings.keys.single { it.split(':')[1] == original.author }, record.devices)
                                }
                                if (next != null) {
                                    require(next.scope == SpaceCrypto.Scope(service, space, channelId, record.generation) &&
                                        next.epoch == record.epoch && next.bindings == record.devices)
                                    target.addProperty("crypto", encode(next.save()))
                                }
                            }
                            putChannelState(space, channelId, target)
                            recordChannelAccessResult(space, state, original, ChangeStatus.ACCEPTED)
                        }
                        "rotate-access" -> {
                            val channelId = original.body.text("channel_id")
                            val target = channelState(space, channelId, state)
                            val record = journal.snapshot.channels.getValue(channelId)
                            val current = target.get("crypto")?.asString?.let { SpaceCrypto.restore(decode(it)) }
                            val pointer = get("$space/operation/${original.id}")?.toString(Charsets.UTF_8)
                            val pending = pointer?.let { get(it) }?.let(::json)
                            if (current?.credential == original.body.text("removed_device")) {
                                target.addProperty("joined", false)
                                val joining = target.get("joining_crypto")?.asString?.let { SpaceCrypto.restore(decode(it)) }
                                // Retiring the old device must not discard the
                                // separately durable fresh rejoin request.
                                if (joining == null || joining.credential == original.body.text("removed_device")) {
                                    target.remove("joining_channel"); target.remove("joining_crypto")
                                }
                            } else if (current != null && target.get("joined")?.asBoolean == true) {
                                val next = pending?.get("candidate")?.asString?.let { SpaceCrypto.restore(decode(it)) }
                                    ?: current.receiveCommit(original.body.text("commit"),
                                        current.bindings.keys.single { it.split(':')[1] == original.author }, record.devices)
                                require(next.scope == SpaceCrypto.Scope(service, space, channelId, record.generation) && next.epoch == record.epoch && next.bindings == record.devices)
                                target.addProperty("crypto", encode(next.save()))
                            }
                            putChannelState(space, channelId, target)
                            state.remove("access_rotation_rejected_head")
                        }
                        "archive-channel" -> recordArchiveResult(space, state, original, ChangeStatus.ACCEPTED)
                        "set-role" -> recordRoleResult(state, original, ChangeStatus.ACCEPTED)
                        "leave", "remove-member", "ban-member", "unban-member" -> recordMembershipResult(state, original, ChangeStatus.ACCEPTED)
                        "edit-channel" -> recordChannelEditResult(space, state, original, ChangeStatus.ACCEPTED)
                        "propose-owner", "accept-owner", "cancel-owner", "decline-owner" -> recordOwnershipResult(state, original, ChangeStatus.ACCEPTED)
                        "complete-owner" -> Unit
                        "invite" -> Unit
                        "revoke-invite" -> {
                            if (state.get("revoking_invitation")?.asString == original.body.text("invite_id")) state.remove("revoking_invitation")
                            val link = state.get("invitation")?.asString
                            if (link != null && SpaceInvitation.parse(link).invitationId == original.body.text("invite_id"))
                                state.addProperty("revoked_invitation", original.body.text("invite_id"))
                        }
                        "set-listed" -> state.add("profile", requireNotNull(journal.snapshot.publicProfile).json())
                        "join-public" -> if (original.author == wallet.publicKey) state.addProperty("joined", true)
                        "admit" -> {
                            val channelId = original.body.text("channel_id")
                            val target = channelState(space, channelId, state)
                            val scope = SpaceCrypto.Scope.fromJson(target.getAsJsonObject("scope"))
                            val channel = requireNotNull(journal.snapshot.channels[channelId])
                            val current = target.get("crypto")?.asString?.let { SpaceCrypto.restore(decode(it)) }
                            val join = SaitoEnvelope.decode(original.body.text("join"))
                            val pointer = get("$space/operation/${original.id}")?.toString(Charsets.UTF_8)
                            val pending = pointer?.let { get(it) }?.let(::json)
                            val next = when {
                                pending?.has("candidate") == true -> SpaceCrypto.restore(decode(pending.text("candidate")))
                                join.author == wallet.publicKey && target.get("joined")?.asBoolean != true -> {
                                    val device = target.get("joining_crypto")?.asString?.let { SpaceCrypto.restore(decode(it)) } ?: current
                                    device?.join(scope, original.body.text("welcome"), channel.devices)?.let { joined ->
                                        if (current?.scope == scope) joined.withRetainedHistory(current) else joined
                                    }
                                }
                                current == null -> null
                                target.get("joined")?.asBoolean == true -> current.receiveCommit(original.body.text("commit"),
                                    current.bindings.keys.single { it.split(':')[1] == original.author }, channel.devices)
                                else -> null
                            }
                            if (next != null) {
                                require(next.scope == scope && next.epoch == channel.epoch && next.bindings == channel.devices)
                                target.addProperty("crypto", encode(next.save())); target.addProperty("joined", true)
                                target.remove("joining_channel"); target.remove("joining_crypto")
                                if (channelId == generalChannel(state)) state.remove("joining_invitation")
                                if (pending?.has("candidate") == true) {
                                    pending.get("after_join_cursor")?.asLong?.let { state.addProperty("join_cursor", it) }
                                    val recipient = channel.devices.keys.single { it.split(':')[1] == join.author }
                                    if (state.get("control_pending")?.asString == original.id) state.remove("control_pending")
                                    val deliveries = pending.getAsJsonArray("history_deliveries")
                                    if (deliveries != null) {
                                        val originals = deliveries.map { SaitoTransaction.restore(it.asString) }
                                        require(originals.map { it.id } == original.body.getAsJsonArray("history_deliveries").map { it.asString })
                                        originals.forEach { queue(space, state, it) }
                                    } else {
                                        var from = 0L
                                        while (true) {
                                            val history = next.archiveHistory(wallet, recipient, id(), original.id, clock.millis(), from)
                                            target.addProperty("crypto", encode(history.session.save()))
                                            queue(space, state, history.transaction)
                                            from = history.nextEpoch ?: break
                                        }
                                    }
                                }
                                putChannelState(space, channelId, target)
                            }
                        }
                        else -> throw IllegalArgumentException("Unsupported community control")
                    }
                    state.addProperty("access_rotation_required", journal.snapshot.channels.values.any { it.retiredDevices.isNotEmpty() })
                    state.addProperty("ownership_completion_required", journal.snapshot.ownershipOffer?.let {
                        it.member == wallet.publicKey && it.acceptedId != null && it.acceptedAt != null
                    } == true)
                    val role = journal.snapshot.members[wallet.publicKey]
                    if (role != null) state.addProperty("role", role.name) else {
                        state.remove("role"); state.addProperty("joined", false)
                    }
                    if (state.get("control_pending")?.asString == original.id) state.remove("control_pending")
                    if (state.get("required_control")?.asString == original.id) state.remove("required_control")
                    put(key, envelope.toByteArray(Charsets.UTF_8))
                    put(stateKey(space), bytes(state))
                }
            }
            "rejection" -> {
                require(original.author == wallet.publicKey && body.text("reason") == "not-accepted")
                val pointer = requireNotNull(get("$space/operation/${original.id}")).toString(Charsets.UTF_8)
                val pending = json(requireNotNull(get(pointer)))
                require(SaitoTransaction.restore(pending.text("original")).envelope() == body.text("event"))
                if (original.body.text("kind") in setOf("revoke-channel-access", "grant-channel-access")) recordChannelAccessResult(space, state, original, ChangeStatus.REJECTED)
                if (original.body.text("kind") == "create-channel") recordChannelCreationResult(state, original, ChangeStatus.REJECTED)
                if (original.body.text("kind") == "archive-channel") recordArchiveResult(space, state, original, ChangeStatus.REJECTED)
                if (original.body.text("kind") == "set-role") recordRoleResult(state, original, ChangeStatus.REJECTED)
                if (original.body.text("kind") in setOf("leave", "remove-member", "ban-member", "unban-member")) recordMembershipResult(state, original, ChangeStatus.REJECTED)
                if (original.body.text("kind") == "edit-channel") recordChannelEditResult(space, state, original, ChangeStatus.REJECTED)
                if (original.body.text("kind") in OwnershipAction.entries.map { it.kind }) recordOwnershipResult(state, original, ChangeStatus.REJECTED)
                if (original.body.text("kind") == "rotate-access") state.addProperty("access_rotation_rejected_head", original.body.text("control_id"))
                if (original.body.text("kind") == "complete-owner") state.addProperty("ownership_completion_rejected_head", original.body.text("control_id"))
                if (original.body.text("kind") == "revoke-invite") {
                    state.remove("revoking_invitation"); state.addProperty("revocation_failed", original.body.text("invite_id"))
                }
                if (pending.has("rejected")) {
                    val channel = original.body.text("channel_id")
                    val target = channelState(space, channel, state)
                    val rollback = SpaceCrypto.restore(decode(pending.text("rejected")))
                    val accepted = target.get("crypto")?.asString?.let { SpaceCrypto.restore(decode(it)) }
                    // Only accepted controls update active keys; candidates stay
                    // in the outbox. A delayed rejection cannot undo a newer
                    // accepted epoch, generation or retained historical state.
                    val newerAccepted = accepted != null && (accepted.scope != rollback.scope || accepted.epoch > rollback.epoch)
                    if (!newerAccepted) {
                        val restored = if (accepted != null) rollback.withRetainedHistory(accepted) else rollback
                        target.addProperty("crypto", encode(restored.save())); putChannelState(space, channel, target)
                    }
                }
                if (state.get("control_pending")?.asString == original.id) state.remove("control_pending")
                body.get("authorized_control")?.asString?.let { head ->
                    require(head.matches(Regex("[0-9a-f]{128}")))
                    // A delayed disposition can refer to an authenticated
                    // predecessor, rather than the newest local control.
                    if (journal(space, through = head).snapshot.controlId != head) state.addProperty("required_control", head)
                }
                pending.addProperty("disposition", envelope)
                val rejectedKey = "$space/rejected/${original.id}"
                put(rejectedKey, bytes(pending))
                messageChannel(original.body)?.let { channel ->
                    put(messageIndex(space, channel, "rejected") + original.id, rejectedKey.toByteArray(Charsets.UTF_8))
                }
                put(stateKey(space), bytes(state))
            }
            "join" -> {
                require(original.author == wallet.publicKey && original.body.text("kind") in setOf("join", "join-channel"))
                // This acknowledges the queued request, not membership. The
                // exact old acknowledgment can arrive after actual admission.
            }
            "content" -> {
                val channel = body.text("channel_id")
                require(original.body.text("channel_id") == channel && original.body.text("space_id") == space)
                val contentState = channelState(space, channel, state)
                val scope = SpaceCrypto.Scope.fromJson(contentState.getAsJsonObject("scope"))
                require(body.text("channel_id") == scope.channelId)
                val control = body.get("authorized_control")?.asString ?: original.body.text("control_id")
                val historic = pageControls[control] ?: journal(space, through = control)
                require(historic.snapshot.controlId == control)
                historic.verifyContent(body.text("event"))
                val key = contentPrefix(space, channel) + positionKey(body.number("position"))
                val existing = get(key)
                if (existing != null) require(json(existing).text("event") == body.text("event"))
                else {
                    if (original.body.text("kind") == "content" && original.body.text("content_kind") == "history-keys") {
                        val keys = SpaceCrypto.restore(decode(contentState.text("crypto")))
                        if (original.body.text("recipient_credential") == keys.credential) {
                            contentState.addProperty("crypto", encode(keys.acceptArchiveHistory(body.text("event")).save()))
                            putChannelState(space, channel, contentState)
                        }
                    }
                    if (original.body.text("kind") == "history") {
                        val keys = SpaceCrypto.restore(decode(contentState.text("crypto")))
                        if (original.body.text("recipient_credential") == keys.credential) {
                            contentState.addProperty("crypto", encode(keys.acceptHistory(body.text("event")).save()))
                            putChannelState(space, channel, contentState)
                        }
                    }
                    put(key, bytes(body))
                    if (original.body.text("kind") in setOf("content", "public-content") && original.body.text("content_kind") == "message")
                        contentState.addProperty("last_content", maxOf(contentState.get("last_content")?.asLong ?: 0, body.number("position")))
                    putChannelState(space, channel, contentState)
                }
            }
            else -> throw IllegalArgumentException("Unexpected community receipt stream")
        }
    }

    private fun SpaceClientStore.Transaction.recordChannelAccessResult(space: String, state: JsonObject,
        original: SignedSaitoMessage, status: ChangeStatus) {
        val channel = original.body.text("channel_id")
        val target = channelState(space, channel, state)
        val change = target.getAsJsonObject("channel_access_change") ?: return
        if (change.text("id") != original.id) return
        require(change.text("member") == original.body.text("member"))
        change.addProperty("status", status.name)
        putChannelState(space, channel, target)
    }

    private fun recordChannelCreationResult(state: JsonObject, original: SignedSaitoMessage, status: ChangeStatus) {
        val change = state.getAsJsonObject("channel_creation") ?: return
        if (change.text("id") != original.id) return
        require(change.text("channel") == original.body.text("channel_id"))
        original.body.getAsJsonObject("channel_profile")?.let { require(change.getAsJsonObject("profile") == it) }
        change.addProperty("status", status.name)
    }

    private fun recordOwnershipResult(state: JsonObject, original: SignedSaitoMessage, status: ChangeStatus) {
        val change = state.getAsJsonObject("ownership_change") ?: return
        if (change.text("id") != original.id) return
        require(original.author == wallet.publicKey)
        val action = OwnershipAction.valueOf(change.text("action"))
        require(original.body.text("kind") == action.kind)
        if (action == OwnershipAction.OFFER) require(change.text("member") == original.body.text("member"))
        else require(change.text("proposal_id") == original.body.text("proposal_id"))
        change.addProperty("status", status.name)
    }

    private fun recordMembershipResult(state: JsonObject, original: SignedSaitoMessage, status: ChangeStatus) {
        val change = state.getAsJsonObject("membership_change") ?: return
        if (change.text("id") != original.id) return
        require(original.author == wallet.publicKey)
        val action = change.get("action")?.asString?.let(MembershipAction::valueOf) ?: MembershipAction.LEAVE
        require(original.body.text("kind") == action.kind)
        if (action != MembershipAction.LEAVE) require(change.text("member") == original.body.text("member"))
        change.addProperty("status", status.name)
    }

    private fun recordRoleResult(state: JsonObject, original: SignedSaitoMessage, status: ChangeStatus) {
        val change = state.getAsJsonObject("role_change") ?: return
        if (change.text("id") != original.id) return
        require(change.text("member") == original.body.text("member") &&
            change.get("administrator").asBoolean == (original.body.text("role") == "admin"))
        change.addProperty("status", status.name)
    }

    private fun SpaceClientStore.Transaction.recordArchiveResult(space: String, state: JsonObject,
        original: SignedSaitoMessage, status: ChangeStatus) {
        val channel = original.body.text("channel_id")
        val target = channelState(space, channel, state)
        val change = target.getAsJsonObject("archive_change") ?: return
        if (change.text("id") != original.id) return
        require(change.get("archived").asBoolean == original.body.get("archived").asBoolean)
        change.addProperty("status", status.name)
        putChannelState(space, channel, target)
    }

    private fun SpaceClientStore.Transaction.recordChannelEditResult(space: String, state: JsonObject,
        original: SignedSaitoMessage, status: ChangeStatus) {
        val channel = original.body.text("channel_id")
        val target = channelState(space, channel, state)
        if (status == ChangeStatus.ACCEPTED && original.body.has("channel_metadata")) {
            target.remove("channel_profile")
            putChannelState(space, channel, target)
        }
        val change = target.getAsJsonObject("channel_edit") ?: return
        if (change.text("id") != original.id) return
        if (original.body.has("channel_metadata")) require(change.get("metadata") == original.body.get("channel_metadata"))
        else require(change.getAsJsonObject("profile") == original.body.getAsJsonObject("channel_profile"))
        change.addProperty("status", status.name)
        putChannelState(space, channel, target)
    }

    private fun SpaceClientStore.Transaction.journal(space: String, through: String? = null): SpaceJournal =
        store.verifiedJournal(service, space, through) {
            val journal = SpaceJournal(space, service)
            val prefix = "$space/control/"
            var cursor = prefix
            var rows: List<SpaceClientStore.Entry>
            do {
                rows = page(prefix, cursor)
                for (row in rows) {
                    journal.receive(row.value.toString(Charsets.UTF_8))
                    if (journal.snapshot.controlId == through) return@verifiedJournal journal
                }
                rows.lastOrNull()?.let { cursor = it.key + '\u0000' }
            } while (rows.size == 50)
            journal
        }

    private fun SpaceClientStore.Transaction.queue(space: String, state: JsonObject, original: SaitoTransaction,
        candidate: SpaceCrypto? = null, rejected: SpaceCrypto? = null, afterJoinCursor: Long? = null,
        historyDeliveries: List<SaitoTransaction>? = null) {
        val outbound = SaitoEnvelope.decode(original.envelope())
        val kind = outbound.body.text("kind")
        ensureMessageIndexes(space, state)
        if (kind in CONTROL_KINDS) {
            require(!state.has("control_pending")) { "A community control is still awaiting confirmation" }
            state.addProperty("control_pending", original.id)
        }
        val sequence = state.number("next_outbox")
        val key = "$space/pending/${positionKey(sequence)}"
        put(key, bytes(JsonObject().apply {
            addProperty("original", original.encoded()); addProperty("attempted", false)
            candidate?.let { addProperty("candidate", encode(it.save())) }
            rejected?.let { addProperty("rejected", encode(it.save())) }
            afterJoinCursor?.let { addProperty("after_join_cursor", it) }
            historyDeliveries?.let { add("history_deliveries", JsonArray().apply { it.forEach { add(it.encoded()) } }) }
        }))
        messageChannel(outbound.body)?.let { channel ->
            put(messageIndex(space, channel, "pending") + positionKey(sequence), key.toByteArray(Charsets.UTF_8))
        }
        put("$space/operation/${original.id}", key.toByteArray(Charsets.UTF_8))
        state.addProperty("next_outbox", Math.addExact(sequence, 1))
        put(stateKey(space), bytes(state))
        put("communities/$space", space.toByteArray(Charsets.UTF_8))
    }
    private fun messageChannel(body: JsonObject): String? {
        if (body.text("kind") !in setOf("content", "public-content") || body.text("content_kind") != "message") return null
        return body.text("channel_id").also { require(it.matches(ID)) }
    }
    private fun messageIndex(space: String, channel: String, disposition: String) = "$space/channels/$channel/$disposition/"
    /** Rebuild disposable pointers from pre-index records, retaining every original. */
    private fun SpaceClientStore.Transaction.ensureMessageIndexes(space: String, state: JsonObject) {
        state.get("message_index_version")?.let { require(it.toString() == "1"); return }
        for (disposition in listOf("pending", "rejected")) {
            val prefix = "$space/$disposition/"
            var cursor = prefix
            while (true) {
                val rows = page(prefix, cursor)
                for (row in rows) {
                    val original = SaitoEnvelope.decode(SaitoTransaction.restore(json(row.value).text("original")).envelope())
                    require(original.author == wallet.publicKey && original.recipients == listOf(wallet.publicKey, service).distinct() &&
                        original.body.text("module") == "Spaces" && original.body.text("request") == "event" &&
                        original.body.number("version") == 1L && original.body.text("space_id") == space)
                    messageChannel(original.body)?.let { channel ->
                        put(messageIndex(space, channel, disposition) + row.key.substringAfterLast('/'), row.key.toByteArray(Charsets.UTF_8))
                    }
                }
                if (rows.size < 50) break
                cursor = rows.last().key + '\u0000'
            }
        }
        state.addProperty("message_index_version", 1); put(stateKey(space), bytes(state))
    }

    private fun generalChannel(state: JsonObject) = SpaceCrypto.Scope.fromJson(state.getAsJsonObject("scope")).channelId
    private fun selectedChannel(state: JsonObject) = (state.get("selected_channel")?.asString ?: generalChannel(state)).also { require(it.matches(ID)) }
    private fun channelStateKey(space: String, channel: String) = "$space/channels/$channel/state"
    private fun SpaceClientStore.Transaction.channelState(space: String, channel: String, parent: JsonObject = state(space)): JsonObject {
        require(channel.matches(ID))
        val data = if (channel == generalChannel(parent)) parent else get(channelStateKey(space, channel))?.let(::json)
            ?: JsonObject().apply {
                val record = requireNotNull(journal(space).snapshot.channels[channel])
                add("scope", SpaceCrypto.Scope(service, space, channel, record.generation).json())
                addProperty("content_cursor", 0); addProperty("joined", false)
            }.also { put(channelStateKey(space, channel), bytes(it)) }
        val scope = SpaceCrypto.Scope.fromJson(data.getAsJsonObject("scope"))
        require(scope.serviceKey == service && scope.spaceId == space && scope.channelId == channel)
        return data
    }
    /** Presentation metadata must never gate Welcome/control recovery. Earlier
     * General archive keys may arrive only after the control page is committed. */
    private fun SpaceClientStore.Transaction.channelProfile(space: String, channel: String, parent: JsonObject,
        data: JsonObject, record: SpaceChannelRecord): SpaceChannelProfile? {
        record.profile?.let { return it }
        val kind = record.kind ?: run {
            // Earlier private histories carry their immutable kind only in the
            // signed creation ciphertext. Never infer it from an editable label.
            if (record.initialMetadata == null) {
                require(channel == generalChannel(parent))
                SpaceChannelKind.CHAT // The original General-only protocol.
            } else {
                val keyState = if (record.allowedMembers != null) data else parent
                // Retained history can describe an authorized channel while
                // readmission is pending. It does not authorize new editing.
                if (!keyState.has("crypto")) return null
                val keys = SpaceCrypto.restore(decode(keyState.text("crypto")))
                val initial = decode(record.initialMetadata).toString(Charsets.UTF_8)
                require(SaitoEnvelope.decode(initial).body.text("content_kind") == "channel-profile")
                val contents = try { json(keys.read(initial).text.toByteArray(Charsets.UTF_8)) }
                    catch (_: SpaceCrypto.MissingKeys) { return null }
                require(contents.number("version") == 1L && contents.text("channel_id") == channel)
                SpaceChannelProfile.fromJson(contents.getAsJsonObject("profile")).kind
            }
        }
        data.getAsJsonObject("channel_profile")?.let {
            return SpaceChannelProfile.fromJson(it).also { cached -> require(cached.kind == kind) }
        }
        if (record.metadata == null && channel == generalChannel(parent)) return SpaceChannelProfile("general", kind)
        val profile = record.profile ?: record.metadata?.takeIf { parent.get("joined").asBoolean }?.let { encoded ->
            val envelope = decode(encoded).toString(Charsets.UTF_8)
            val original = SaitoEnvelope.decode(envelope)
            val contentKind = original.body.text("content_kind")
            require(contentKind in setOf("channel-profile", "channel-profile-edit"))
            if (contentKind == "channel-profile-edit") require(if (original.body.has("target_channel_id"))
                original.body.text("target_channel_id") == channel else record.allowedMembers != null && original.body.text("channel_id") == channel)
            val keyState = if (record.allowedMembers != null) data.takeIf { it.get("joined")?.asBoolean == true } ?: return null else parent
            val keys = SpaceCrypto.restore(decode(keyState.text("crypto")))
            val text = try { keys.read(envelope).text } catch (_: SpaceCrypto.MissingKeys) { return null }
            val contents = json(text.toByteArray(Charsets.UTF_8))
            require(contents.text("channel_id") == channel)
            val decoded = if (contentKind == "channel-profile-edit") {
                require(contents.keySet() == setOf("version", "channel_id", "name", "category") && contents.get("version").toString() == "2")
                SpaceChannelProfile(contents.text("name"), kind, contents.text("category"))
            } else {
                require(contents.number("version") == 1L)
                SpaceChannelProfile.fromJson(contents.getAsJsonObject("profile"))
            }
            decoded.also {
                require(it.kind == kind)
                require(record.adminPostsOnly == (it.kind == SpaceChannelKind.ANNOUNCEMENT))
            }
        }
        if (profile != null) {
            data.add("channel_profile", profile.json()); putChannelState(space, channel, data)
        }
        return profile
    }
    private fun SpaceClientStore.Transaction.putChannelState(space: String, channel: String, data: JsonObject) {
        put(if (channel == generalChannel(state(space))) stateKey(space) else channelStateKey(space, channel), bytes(data))
    }
    private fun SpaceClientStore.Transaction.contentPrefix(space: String, channel: String) =
        if (channel == generalChannel(state(space))) "$space/content/" else "$space/channels/$channel/content/"
    private fun SpaceClientStore.Transaction.draftKey(space: String, channel: String) =
        if (channel == generalChannel(state(space))) "$space/draft" else "$space/channels/$channel/draft"

    private fun SpaceClientStore.Transaction.state(space: String): JsonObject {
        require(space.matches(ID))
        return json(requireNotNull(get(stateKey(space))) { "Community is not stored for this account and node" })
    }
    private fun SpaceClientStore.Transaction.owned(initialize: Boolean = false) {
        val expected = JsonObject().apply {
            addProperty("version", 1); addProperty("owner", wallet.publicKey)
            addProperty("node", node); addProperty("service", service)
        }
        val stored = get("identity")
        if (stored == null) { require(initialize); put("identity", bytes(expected)) }
        else require(json(stored) == expected) { "Community storage belongs to another account or node" }
    }
    private fun event(space: String, kind: String, operation: String = id(), extra: JsonObject.() -> Unit): SaitoTransaction =
        SaitoTransaction.create(wallet, JsonObject().apply {
            addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
            addProperty("kind", kind); addProperty("space_id", space); addProperty("operation_id", operation); extra()
        }, clock.millis(), listOf(service))

    private fun originalEnvelope(body: ByteArray) = json(body).text("event")
    private fun stateKey(space: String) = "$space/state"
    private fun positionKey(value: Long) = value.also { require(it >= 0) }.toString().padStart(20, '0')
    private fun devices(values: Map<String, String>) = JsonObject().apply { values.forEach { (key, value) -> addProperty(key, value) } }
    private fun id() = ByteArray(32).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun encode(value: ByteArray) = Base64.getEncoder().encodeToString(value)
    private fun decode(value: String) = Base64.getDecoder().decode(value)
    private fun bytes(value: JsonObject) = value.toString().toByteArray(Charsets.UTF_8)
    private fun json(value: ByteArray) = GSON.fromJson(value.toString(Charsets.UTF_8), JsonObject::class.java)
    private fun JsonObject.text(name: String): String = get(name).also {
        require(it != null && it.isJsonPrimitive && it.asJsonPrimitive.isString)
    }.asString
    private fun JsonObject.number(name: String): Long = get(name).also {
        require(it != null && it.isJsonPrimitive && it.asJsonPrimitive.isNumber && it.asString.matches(Regex("0|[1-9][0-9]{0,18}")))
    }.asLong.also { require(it >= 0) }
    private companion object {
        val CONTROL_KINDS = setOf("create", "invite", "admit", "set-listed", "join-public", "revoke-invite", "create-channel",
            "archive-channel", "set-role", "edit-channel", "revoke-channel-access", "grant-channel-access", "rotate-access", "leave", "remove-member", "ban-member", "unban-member", "propose-owner", "accept-owner", "cancel-owner", "decline-owner", "complete-owner")
        const val DIRECTORY_CACHE_LIMIT = 200
        val ID = Regex("[0-9a-f]{64}")
        val GSON = GsonBuilder().setStrictness(Strictness.STRICT).create()
    }
}
