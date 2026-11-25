package nl.davidengelen.redsquare.spaces

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import com.google.gson.Strictness
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoStack
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.SignedSaitoMessage
import nl.davidengelen.redsquare.saito.StackLink
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
    private var supportsMessageHistory = false
    private var supportsMessageReactions = false
    private var supportsMessagePins = false
    @Volatile private var supportsAddressedInvitations = false
    @Volatile private var supportsProtectedChat = false
    @Volatile private var supportsDeviceReplacement = false
    @Volatile private var supportsSpaceArticles = false
    @Volatile private var supportsWriteRestrictions = false
    @Volatile private var supportsFileStorage = false
    @Volatile private var supportsVoiceRooms = false
    @Volatile private var supportsModerationReports = false

    enum class Delivery { CONTROL, CONTENT }
    enum class ReadProblem { EXPIRED, UNAVAILABLE }
    class ReadDenied(val spaceId: String, val problem: ReadProblem) : IllegalArgumentException("Community access is not available")
    class DifferentNode(val node: String) : IllegalArgumentException("This community belongs to a different node")

    data class Channel(val id: String, val profile: SpaceChannelProfile, val visibility: SpaceVisibility, val ready: Boolean, val unread: Long? = null, val hasUnread: Boolean? = null,
        val archived: Boolean = false, val allowedMembers: Set<String>? = null, val metadataReady: Boolean = false, val categoryId: String? = null, val personalUnread: Long? = null)
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
            val metadataGroupId = record.metadataGroupId(id, journal.snapshot)
            val metadataGroup = journal.snapshot.channels[metadataGroupId]
            val metadataReady = record.visibility == SpaceVisibility.PUBLIC || readableProfile != null && metadataGroup?.retiredDevices?.isEmpty() == true &&
                channelState(space, metadataGroupId, state).get("joined")?.asBoolean == true
            profile?.let { visible ->
                val category = record.categoryId?.let { categoryProfile(space, it, state, journal)?.name }
                val attention = cachedAttention(space, id)
                Channel(id, visible.copy(category = category ?: visible.category), record.visibility, record.visibility == SpaceVisibility.PUBLIC ||
                data.get("joined")?.asBoolean == true && record.retiredDevices.isEmpty(),
                data.get("unread")?.asLong, attention.hasUnread ?: unreadActivity(data)?.takeIf { it }, record.archived, record.allowedMembers, metadataReady, record.categoryId, attention.personal) }
        }
    }
    data class Category(val id: String, val profile: SpaceCategoryProfile, val isPrivate: Boolean)
    fun categories(space: String): List<Category> = store.project {
        store.transaction {
            owned()
            val state = state(space)
            val journal = journal(space)
            journal.snapshot.categories.mapNotNull { (id, record) ->
                categoryProfile(space, id, state, journal)?.let { Category(id, it, record.profile == null) }
            }
        }
    }

    private fun SpaceClientStore.Transaction.categoryProfile(space: String, category: String,
        state: JsonObject, journal: SpaceJournal): SpaceCategoryProfile? {
        val record = requireNotNull(journal.snapshot.categories[category])
        record.profile?.let { return it }
        for (group in journal.snapshot.categoryGroups(category, record.defaultGroup)) {
            if (!journal.canAccessChannel(wallet.publicKey, group)) continue
            val accepted = journal.snapshot.channels.getValue(group)
            if (accepted.retiredDevices.isNotEmpty()) continue
            val data = channelState(space, group, state)
            if (data.get("joined")?.asBoolean != true || !data.has("crypto")) continue
            val metadata = record.metadata[group] ?: continue
            val keys = SpaceCrypto.restore(decode(data.text("crypto")))
            if (keys.scope != SpaceCrypto.Scope(service, space, group, accepted.generation) ||
                keys.epoch != accepted.epoch || keys.bindings != accepted.devices) continue
            val original = decode(metadata).toString(Charsets.UTF_8)
            val contents = try { json(keys.read(original).text.toByteArray(Charsets.UTF_8)) }
                catch (_: SpaceCrypto.MissingKeys) { continue }
            require(contents.keySet() == setOf("version", "category_id", "profile") && contents.number("version") == 1L &&
                contents.text("category_id") == category)
            require(SaitoEnvelope.decode(original).body.text("operation_id") == record.revision &&
                SaitoEnvelope.decode(original).body.text("target_category_id") == category)
            return SpaceCategoryProfile.fromJson(contents.getAsJsonObject("profile"))
        }
        return null
    }

    data class CategoryChange(val id: String, val categoryId: String, val profile: SpaceCategoryProfile, val status: ChangeStatus,
        val creating: Boolean = false, val isPrivate: Boolean = false)

    fun createCategory(space: String, profile: SpaceCategoryProfile, expectedVisibility: SpaceVisibility? = null): String = store.transaction {
        owned()
        val state = state(space)
        val snapshot = categoryManagement(space, state)
        require(expectedVisibility == null || expectedVisibility == snapshot.visibility) { "Community visibility changed before category creation" }
        val category = id()
        val operation = id()
        val body = JsonObject()
        if (snapshot.visibility == SpaceVisibility.PUBLIC) body.add("category_profile", profile.json()) else {
            val entry = requireNotNull(snapshot.entryChannelId)
            body.addProperty("default_group", entry)
            body.add("category_metadata", encryptedCategory(space, category, profile, operation, setOf(entry), state, snapshot))
        }
        enqueueCategory(space, state, snapshot, category, profile, operation, "create-category", body)
        category
    }

    fun editCategory(space: String, category: String, profile: SpaceCategoryProfile): String = store.transaction {
        owned()
        val state = state(space)
        val snapshot = categoryManagement(space, state)
        val record = requireNotNull(snapshot.categories[category])
        val operation = id()
        val body = JsonObject()
        if (record.profile != null && snapshot.visibility == SpaceVisibility.PUBLIC) body.add("category_profile", profile.json()) else {
            val defaultGroup = if (record.profile != null) requireNotNull(snapshot.entryChannelId) else record.defaultGroup
            if (record.profile != null) body.addProperty("default_group", defaultGroup)
            body.add("category_metadata", encryptedCategory(space, category, profile, operation,
                snapshot.categoryGroups(category, defaultGroup), state, snapshot))
        }
        enqueueCategory(space, state, snapshot, category, profile, operation, "edit-category", body)
    }

    fun categoryChange(space: String): CategoryChange? = store.transaction {
        owned()
        val change = state(space).getAsJsonObject("category_change") ?: return@transaction null
        CategoryChange(change.text("id"), change.text("category"), SpaceCategoryProfile.fromJson(change.getAsJsonObject("profile")),
            changeStatus(space, change), change.get("creating")?.asBoolean == true, change.get("is_private")?.asBoolean == true)
    }

    private fun SpaceClientStore.Transaction.categoryManagement(space: String, state: JsonObject): SpaceSnapshot {
        val snapshot = journal(space).snapshot
        require(state.get("joined").asBoolean && !state.has("required_control"))
        require(snapshot.members[wallet.publicKey] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN))
        return snapshot
    }

    private fun SpaceClientStore.Transaction.enqueueCategory(space: String, state: JsonObject, snapshot: SpaceSnapshot,
        category: String, profile: SpaceCategoryProfile, operation: String, kind: String, body: JsonObject): String {
        val original = event(space, kind, operation) {
            addProperty("control_id", requireNotNull(snapshot.controlId)); addProperty("category_id", category)
            body.entrySet().forEach { (key, value) -> add(key, value) }
        }
        state.add("category_change", JsonObject().apply {
            addProperty("id", original.id); addProperty("category", category); add("profile", profile.json())
            addProperty("creating", kind == "create-category"); addProperty("is_private", body.has("category_metadata"))
            addProperty("status", ChangeStatus.QUEUED.name)
        })
        queue(space, state, original)
        return original.id
    }

    private fun SpaceClientStore.Transaction.encryptedCategory(space: String, category: String, profile: SpaceCategoryProfile,
        revision: String, groups: Set<String>, state: JsonObject, snapshot: SpaceSnapshot,
        overrides: Map<String, SpaceCrypto> = emptyMap()): JsonObject {
        require(groups.isNotEmpty())
        return JsonObject().apply {
            for (channel in groups.sorted()) {
                val record = snapshot.channels.getValue(channel)
                require(record.visibility == SpaceVisibility.PRIVATE && record.retiredDevices.isEmpty())
                val keys = overrides[channel] ?: channelState(space, channel, state).let { data ->
                    require(data.get("joined")?.asBoolean == true)
                    SpaceCrypto.restore(decode(data.text("crypto")))
                }
                require(keys.scope == SpaceCrypto.Scope(service, space, channel, record.generation) &&
                    keys.epoch == record.epoch && keys.bindings == record.devices)
                val metadata = keys.publish(wallet, "category-profile", revision, requireNotNull(snapshot.controlId), JsonObject().apply {
                    addProperty("version", 1); addProperty("category_id", category); add("profile", profile.json())
                }.toString(), clock.millis(), targetCategoryId = category)
                addProperty(channel, encode(metadata.envelope().toByteArray(Charsets.UTF_8)))
            }
        }
    }

    private fun recordCategoryResult(state: JsonObject, original: SignedSaitoMessage, status: ChangeStatus) {
        val change = state.getAsJsonObject("category_change") ?: return
        if (change.text("id") != original.id) return
        require(change.text("category") == original.body.text("category_id"))
        original.body.getAsJsonObject("category_profile")?.let { require(change.getAsJsonObject("profile") == it) }
        change.addProperty("status", status.name)
    }

    /** One signed channel operation creates or attaches its folder atomically. */
    private fun SpaceClientStore.Transaction.categoryAssignment(space: String, state: JsonObject, snapshot: SpaceSnapshot,
        channel: String, record: SpaceChannelRecord, profile: SpaceChannelProfile, operation: String,
        chosen: String? = null, overrides: Map<String, SpaceCrypto> = emptyMap()): JsonObject {
        val result = JsonObject()
        if (profile.category.isBlank()) {
            require(chosen == null)
            result.add("category_id", com.google.gson.JsonNull.INSTANCE)
            return result
        }
        val private = record.visibility == SpaceVisibility.PRIVATE
        val retained = record.categoryId?.takeIf { id ->
            (private || snapshot.categories.getValue(id).profile != null) &&
                categoryProfile(space, id, state, journal(space))?.name == profile.category
        }
        val existing = chosen?.also { require(it in snapshot.categories) } ?: retained ?: snapshot.categories.keys.firstOrNull { id ->
            (private || snapshot.categories.getValue(id).profile != null) &&
                categoryProfile(space, id, state, journal(space))?.name == profile.category
        }
        val category = existing ?: id()
        result.addProperty("category_id", category)
        val accepted = existing?.let(snapshot.categories::get)
        if (accepted?.profile != null) {
            require(accepted.profile.name == profile.category)
        } else if (accepted != null) {
            require(private && categoryProfile(space, category, state, journal(space))?.name == profile.category)
            val group = record.metadataGroupId(channel, snapshot)
            result.add("category_attachment", JsonObject().apply {
                add("category_metadata", encryptedCategory(space, category, SpaceCategoryProfile(profile.category), accepted.revision,
                    setOf(group), state, snapshot.copy(channels = snapshot.channels + (channel to record)), overrides))
            })
        } else {
            result.add("category_creation", JsonObject().apply {
                if (!private) add("category_profile", SpaceCategoryProfile(profile.category).json()) else {
                    val group = record.metadataGroupId(channel, snapshot)
                    add("category_metadata", encryptedCategory(space, category, SpaceCategoryProfile(profile.category), operation,
                        setOf(group), state, snapshot.copy(channels = snapshot.channels + (channel to record)), overrides))
                }
            })
        }
        return result
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
    data class ChannelCreation(val id: String, val channelId: String, val profile: SpaceChannelProfile, val status: ChangeStatus, val privateMembers: Set<String>? = null, val categoryId: String? = null)
    data class ChannelEdit(val id: String, val channelId: String, val profile: SpaceChannelProfile, val status: ChangeStatus, val categoryId: String? = null)

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

    fun editChannel(space: String, channel: String, profile: SpaceChannelProfile, categoryId: String? = null): String = store.transaction {
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
            val metadataChannel = record.metadataGroupId(channel, snapshot)
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
        val operation = id()
        val assignment = categoryAssignment(space, state, snapshot, channel, record, profile, operation, categoryId)
        val original = event(space, "edit-channel", operation) {
            addProperty("control_id", requireNotNull(snapshot.controlId)); addProperty("channel_id", channel)
            assignment.entrySet().forEach { (key, value) -> add(key, value) }
            if (metadata == null) add("channel_profile", profile.json())
            else addProperty("channel_metadata", encode(metadata.toByteArray(Charsets.UTF_8)))
        }
        target.add("channel_edit", JsonObject().apply {
            addProperty("id", original.id); add("profile", profile.json()); addProperty("status", ChangeStatus.QUEUED.name)
            add("category_id", assignment.get("category_id"))
            metadata?.let { addProperty("metadata", encode(it.toByteArray(Charsets.UTF_8))) }
        })
        putChannelState(space, channel, target)
        queue(space, state, original)
        original.id
    }

    fun channelEdit(space: String, channel: String): ChannelEdit? = store.transaction {
        owned()
        val change = channelState(space, channel).getAsJsonObject("channel_edit") ?: return@transaction null
        ChannelEdit(change.text("id"), channel, SpaceChannelProfile.fromJson(change.getAsJsonObject("profile")), changeStatus(space, change),
            change.get("category_id")?.takeUnless { it.isJsonNull }?.asString)
    }


    data class Management(val archive: ArchiveChange? = null, val role: RoleChange? = null,
        val edit: ChannelEdit? = null, val creation: ChannelCreation? = null, val access: ChannelAccessChange? = null,
        val membership: MembershipChange? = null, val ownership: OwnershipChange? = null, val ownershipOffer: SpaceOwnershipOffer? = null, val category: CategoryChange? = null, val visibility: VisibilityChange? = null, val dissolution: DissolutionChange? = null)
    data class Presentation(val communities: List<Community>, val profiles: Map<String, SpaceProfile>,
        val current: Community?, val channels: List<Channel>, val members: List<Member>, val bannedMembers: Set<String>,
        val window: HistoryWindow?, val messages: List<Message>, val draft: String, val anchor: ReadingAnchor?,
        val invitation: Invitation?, val publicLink: String?, val listingIntent: Boolean?, val management: Management, val categories: List<Category> = emptyList(), val threadParent: Message? = null)

    /** A bounded, coherent read for native rendering. It authenticates control
     * history once per scope instead of independently rebuilding it for every
     * visible property. No cached authority survives this transaction. */
    fun presentation(space: String? = null, channel: String? = null, from: Long? = null, thread: Message? = null): Presentation = store.project {
        val communities = communities()
        val profiles = communities.associate { it.id to profile(it.id) }.toMutableMap()
        val current = space?.let { community(it, channel) }
        val selected = current?.channel
        if (space != null) profiles[space] = profile(space)
        val members = space?.let(::members).orEmpty()
        val parent = if (space != null && thread != null) {
            store.transaction { threadParent(space, requireNotNull(selected), thread) }
            requireNotNull(message(space, thread.id, selected))
        } else null
        val window = space?.let { if (parent == null) historyWindow(it, from, selected) else threadHistoryWindow(it, parent, from, selected) }
        Presentation(communities, profiles, current, space?.let(::channels).orEmpty(), members,
            space?.let(::bannedMembers).orEmpty(), window,
            space?.let { if (parent == null) timeline(it, from = window?.from, channel = selected) else thread(it, parent, from = window?.from, channel = selected) }.orEmpty(),
            space?.let { if (parent == null) draft(it, selected) else threadDraft(it, parent, selected) }.orEmpty(),
            space?.let { if (parent == null) readingAnchor(it, selected) else threadReadingAnchor(it, parent, selected) },
            space?.let(::invitation), space?.takeIf { profiles[it]?.visibility == SpaceVisibility.PUBLIC && members.isNotEmpty() }?.let(::publicLink),
            space?.let(::listingIntent), space?.let { management(it, selected) } ?: Management(), space?.let(::categories).orEmpty(), parent)
    }

    /** Urgent delivery projects just its durable management results. */
    fun management(space: String, channel: String? = null): Management = store.project {
        val selected = channel ?: community(space).channel
        Management(archiveChange(space, selected), roleChange(space), channelEdit(space, selected), channelCreation(space),
            channelAccessChange(space, selected), membershipChange(space), ownershipChange(space), ownershipOffer(space), categoryChange(space), visibilityChange(space), dissolutionChange(space))
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
        require(snapshot.protectedChat?.directPeer == null)
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

    fun setWriteRestriction(space: String, member: String, channel: String?, expiresAt: Long?): String = store.transaction {
        owned()
        val state = state(space)
        require(supportsWriteRestrictions && state.get("joined").asBoolean && !state.has("required_control"))
        val journal = journal(space)
        require(journal.canRestrictWriting(wallet.publicKey, member, channel))
        if (expiresAt != null) require(expiresAt > clock.millis())
        else require((journal.snapshot.writeRestrictions[member to channel]?.expiresAt ?: 0) > clock.millis())
        val original = event(space, "set-write-restriction") {
            addProperty("control_id", requireNotNull(journal.snapshot.controlId))
            addProperty("member", member)
            channel?.let { addProperty("channel_id", it) }
            addProperty("expires_at", expiresAt ?: 0)
        }
        queue(space, state, original)
        original.id
    }

    fun scopedWriteRestriction(space: String, member: String, channel: String?): Long? = store.transaction {
        owned()
        journal(space).snapshot.writeRestrictions[member to channel]?.expiresAt
    }

    fun restrictionOptions(space: String, member: String): SpaceRestrictionOptions = store.transaction {
        owned()
        val journal = journal(space)
        require(supportsWriteRestrictions && journal.canRestrictWriting(wallet.publicKey, member, null))
        val channels = journal.snapshot.channels.keys.filterTo(linkedSetOf()) { channel ->
            journal.canRestrictWriting(wallet.publicKey, member, channel)
        }
        val now = clock.millis()
        val active = journal.snapshot.writeRestrictions.entries.mapNotNull { (target, value) ->
            val (key, channel) = target
            if (key == member && value.expiresAt > now && (channel == null || channel in channels))
                SpaceScopedWriteRestriction(channel, value.expiresAt) else null
        }
        SpaceRestrictionOptions(channels, active)
    }

    /** The owner explicitly approves the exact provider-signed terms. */
    fun approveFileStorage(space: String, reviewed: SpaceStorageOffer): String = store.transaction {
        owned()
        val state = state(space)
        val snapshot = journal(space).snapshot
        require(state.get("joined").asBoolean && !snapshot.dissolved && snapshot.owner == wallet.publicKey)
        require(snapshot.protectedChat == null && snapshot.channels.values.any { (it.kind ?: it.profile?.kind) == SpaceChannelKind.FILE })
        val saved = "$space/file-storage/intents/${reviewed.id}"
        get(saved)?.let { return@transaction it.toString(Charsets.UTF_8) }
        require(supportsFileStorage && !state.has("required_control")) { "File storage is unavailable on this node" }
        reviewed.verifyScope(space, service, wallet.publicKey, clock.millis())
        require(reviewed.costNolan.signum() == 0) { "This provider offer requires unsupported payment" }
        require(!java.net.URI.create(reviewed.origin).host.equals(runCatching { java.net.URI.create(node).host }.getOrNull(), ignoreCase = true)) {
            "Choose a storage provider separate from this node"
        }
        val original = event(space, "approve-file-storage") {
            addProperty("control_id", requireNotNull(snapshot.controlId))
            addProperty("offer", reviewed.envelope)
        }
        queue(space, state, original)
        put(saved, original.id.toByteArray(Charsets.UTF_8))
        original.id
    }

    fun fileStorageApproval(space: String, operation: String): String? = store.transaction {
        owned()
        require(space.matches(ID) && operation.matches(Regex("[0-9a-f]{128}")))
        get("$space/file-storage/approvals/$operation")?.toString(Charsets.UTF_8)
    }

    fun fileStorage(space: String): SpaceFileLease? = store.transaction {
        owned(); state(space); journal(space).snapshot.fileStorage
    }

    fun fileStorageSupported(): Boolean = supportsFileStorage
    fun voiceRoomsSupported(): Boolean = supportsVoiceRooms
    fun moderationReportsSupported(): Boolean = supportsModerationReports
    fun writeRestrictionsSupported(): Boolean = supportsWriteRestrictions

    fun fileOperation(space: String, originalId: String): ChangeStatus? = store.transaction {
        owned(); state(space)
        require(originalId.matches(FILE_SIGNATURE))
        when {
            get("$space/rejected/$originalId") != null -> ChangeStatus.REJECTED
            get("$space/operation/$originalId") != null -> ChangeStatus.QUEUED
            else -> null
        }
    }

    fun filePublication(space: String, channel: String, objectId: String): String? = store.transaction {
        owned(); state(space)
        require(channel.matches(ID) && objectId.matches(ID))
        get("$space/channels/$channel/file-intents/$objectId")?.toString(Charsets.UTF_8)
    }

    fun activateFileStorage(space: String, approvalEnvelope: String, leaseEnvelope: String): String = store.transaction {
        owned()
        val state = state(space)
        val snapshot = journal(space).snapshot
        require(supportsFileStorage && state.get("joined").asBoolean && !snapshot.dissolved && snapshot.owner == wallet.publicKey)
        val approval = SpaceStorageApproval.read(approvalEnvelope, service)
        val lease = SpaceFileLease.read(approval, leaseEnvelope)
        val offer = lease.offer
        require(offer.space == space && offer.owner == wallet.publicKey && snapshot.fileStorageOffers[offer.id] == approval.operation)
        val saved = "$space/file-storage/activations/${lease.id}"
        get(saved)?.toString(Charsets.UTF_8)?.let {
            if (get("$space/rejected/$it") == null) return@transaction it
        }
        require(clock.millis() < offer.expiresAt && !state.has("required_control"))
        val original = event(space, "activate-file-storage") {
            addProperty("control_id", requireNotNull(snapshot.controlId))
            addProperty("approval", approvalEnvelope)
            addProperty("lease", leaseEnvelope)
        }
        queue(space, state, original)
        put(saved, original.id.toByteArray(Charsets.UTF_8))
        original.id
    }

    suspend fun fileUploadPermission(space: String, channel: String, file: SpaceFileCipher.Sealed): SpaceFileGrant {
        val control = store.transaction {
            owned(); state(space)
            val journal = journal(space)
            val control = requireNotNull(journal.snapshot.controlId)
            journal.verifyFileUpload(wallet.publicKey, channel, control, clock.millis())
            control
        }
        val lease = requireNotNull(fileStorage(space))
        val result = readPage(fileScope(space, channel, "upload").apply {
            addProperty("control", control)
            addProperty("object", file.id)
            addProperty("sha256", file.sha256)
            addProperty("bytes", file.bytes)
        })
        val grant = providerGrant(result, lease, SpaceFileGrant.Action.PUT)
        require(grant.objectId == file.id && grant.sha256 == file.sha256 && grant.bytes == file.bytes)
        return grant
    }

    data class FileDownload(val origin: String, val grant: SpaceFileGrant, val accessTerm: String)

    suspend fun fileDownloadPermission(space: String, channel: String, fileId: String): FileDownload {
        val lease = requireNotNull(fileStorage(space))
        val result = readPage(fileScope(space, channel, "download").apply { addProperty("file", fileId) })
        return FileDownload(lease.offer.origin, providerGrant(result, lease, SpaceFileGrant.Action.GET), result.text("access_term"))
    }

    suspend fun confirmFileDownload(space: String, channel: String, fileId: String, expected: FileDownload) {
        val result = readPage(fileScope(space, channel, "confirm").apply { addProperty("file", fileId) })
        require(result.text("access_term") == expected.accessTerm) { "File access changed during download" }
    }

    suspend fun reportMessage(space: String, channel: String, message: String, reason: String): SpaceMessageReport {
        val requestedReason = SpaceMessageReport.cleanReason(reason)
        val result = readPage(JsonObject().apply {
            addProperty("space_id", space)
            addProperty("stream", "message-report")
            addProperty("cursor", 0)
            addProperty("channel_id", channel)
            addProperty("message_id", message)
            addProperty("reason", requestedReason)
        })
        return SpaceMessageReport.read(result.getAsJsonArray("items").single().asJsonObject).also {
            require(it.channel == channel && it.message == message && it.reporter == wallet.publicKey)
            if (it.reason != requestedReason) throw SpaceReportAlreadyFiled()
        }
    }

    suspend fun reviewReportedMessage(space: String, report: SpaceMessageReport): SpaceReportedMessage? = try {
        syncControls(space)
        val page = messagePage(space, report.channel, limit = 1, thread = report.thread, exactReport = report)
        val evidence = if (page.count > 0) reportedMessage(space, report) else null
        if (evidence != null && (evidence.current.missingKeys || evidence.original == null)) {
            refreshKeys(space, report.channel)
            val refreshed = messagePage(space, report.channel, limit = 1, thread = report.thread, exactReport = report)
            if (refreshed.count > 0) reportedMessage(space, report) else null
        } else evidence
    } catch (denied: ReadDenied) {
        // Staff may investigate the report even if this device was never given the channel keys.
        messageReports(space)
        null
    }

    private fun reportedMessage(space: String, report: SpaceMessageReport): SpaceReportedMessage? = store.project { store.transaction {
        owned()
        val current = message(space, report.message, report.channel) ?: return@transaction null
        val pointer = get(messageIdKey(space, report.channel, report.message)) ?: return@transaction null
        val receipt = get(pointer.toString(Charsets.UTF_8)) ?: return@transaction null
        val envelope = originalEnvelope(receipt)
        val original = SaitoEnvelope.decode(envelope)
        require(original.id == report.message && original.body.text("channel_id") == report.channel)
        val text = if (original.body.text("kind") == "public-content") original.body.text("text") else try {
            protectedHistoryReader(space, report.channel)(envelope, original)
        } catch (_: SpaceCrypto.MissingKeys) { null }
        catch (_: java.security.GeneralSecurityException) { null }
        SpaceReportedMessage(text, current)
    } }

    suspend fun messageReports(space: String, before: Long = Long.MAX_VALUE): SpaceReportPage {
        val result = readPage(JsonObject().apply {
            addProperty("space_id", space)
            addProperty("stream", "staff-reports")
            addProperty("cursor", before)
        })
        val next = result.number("next_cursor")
        require(next in 0 until before)
        return SpaceReportPage(result.getAsJsonArray("items").map { SpaceMessageReport.read(it.asJsonObject) },
            next.takeIf { it > 0 })
    }

    suspend fun adminLog(space: String, before: SpaceAdminCursor? = null): SpaceAdminPage {
        val result = readPage(JsonObject().apply {
            addProperty("space_id", space)
            addProperty("stream", "admin-log")
            addProperty("cursor", before?.at ?: Long.MAX_VALUE)
            addProperty("before_id", before?.event ?: "f".repeat(128))
        })
        val events = result.getAsJsonArray("items").map { SpaceAdminEvent.read(it.asJsonObject) }
        val next = result.get("next_event")?.let { SpaceAdminCursor(result.number("next_cursor"), it.asString) }
        require(events.zipWithNext().all { (newer, older) -> older.cursor < newer.cursor })
        require(before == null || events.all { it.cursor < before })
        require(next == null || next == events.lastOrNull()?.cursor)
        return SpaceAdminPage(events, next)
    }

    private operator fun SpaceAdminCursor.compareTo(other: SpaceAdminCursor): Int =
        compareValuesBy(this, other, SpaceAdminCursor::at, SpaceAdminCursor::event)

    suspend fun fileStorageUsagePermission(space: String, channel: String): SpaceFileGrant =
        providerGrant(readPage(fileScope(space, channel, "usage")), requireNotNull(fileStorage(space)), SpaceFileGrant.Action.STATUS)

    suspend fun voiceRoom(space: String, channel: String): SpaceVoiceRoom =
        voiceRoomCommand(SpaceVoiceRoom.scope(space, channel, SpaceVoiceRoom.Command.OBSERVE, null))

    suspend fun joinVoiceRoom(space: String, channel: String, contact: SpaceVoiceSignalKey.Contact): SpaceVoiceRoom {
        require(contact.wallet == wallet.publicKey && contact.verify(SpaceVoiceSignalKey.Address(service, space, channel)))
        return voiceRoomCommand(SpaceVoiceRoom.scope(space, channel, SpaceVoiceRoom.Command.JOIN, contact.session).apply {
            add("contact", contact.json())
        })
    }

    suspend fun renewVoiceRoom(space: String, channel: String, session: String, receivedThrough: Long = 0): SpaceVoiceRoom {
        require(receivedThrough >= 0)
        return voiceRoomCommand(SpaceVoiceRoom.scope(space, channel, SpaceVoiceRoom.Command.RENEW, session).apply {
            addProperty("received_through", receivedThrough)
        })
    }

    suspend fun leaveVoiceRoom(space: String, channel: String, session: String): SpaceVoiceRoom =
        voiceRoomCommand(SpaceVoiceRoom.scope(space, channel, SpaceVoiceRoom.Command.LEAVE, session))

    suspend fun moderateVoiceRoom(space: String, channel: String, session: String, remove: Boolean): SpaceVoiceRoom =
        voiceRoomCommand(SpaceVoiceRoom.scope(space, channel, if (remove) SpaceVoiceRoom.Command.REMOVE else SpaceVoiceRoom.Command.MUTE, session))

    suspend fun relayVoiceSignal(space: String, channel: String, session: String, packet: SpaceVoiceSignalKey.Packet): SpaceVoiceRoom {
        require(packet.from == session)
        return voiceRoomCommand(SpaceVoiceRoom.scope(space, channel, SpaceVoiceRoom.Command.SIGNAL, session).apply {
            add("packet", packet.json())
        })
    }

    private suspend fun voiceRoomCommand(scope: JsonObject): SpaceVoiceRoom {
        val space = scope.text("space_id")
        val channel = scope.text("channel_id")
        val session = scope.get("session")?.asString
        val device = store.transaction {
            if (get(stateKey(space)) == null) return@transaction null
            owned()
            val record = journal(space).snapshot.channels[channel]
            if (record?.visibility != SpaceVisibility.PRIVATE) return@transaction null
            val saved = channelState(space, channel).get("crypto")?.asString ?: throw SpaceCrypto.MissingKeys()
            SpaceCrypto.restore(decode(saved)).also { require(it.credential in record.devices) }
        }
        val reply = readPage(scope, decorateRead = device?.let { keys ->
            { nonce, request ->
                request.addProperty("room_device", keys.credential)
                request.addProperty("room_proof", keys.proveRoomDevice(SpaceVoiceRoom.deviceChallenge(service, wallet.publicKey, scope, nonce)))
            }
        })
        val room = SpaceVoiceRoom.read(reply.getAsJsonObject("voice_room"))
        require(!room.admitted || room.participants.any { it.wallet == wallet.publicKey && it.session == session })
        val address = SpaceVoiceSignalKey.Address(service, space, channel)
        require(room.participants.all { it.contact.verify(address) })
        require(room.signals.all { delivery -> delivery.packet.to == session && room.participants.any { it.session == delivery.packet.from } })
        return room
    }

    suspend fun fileRemovalPermission(space: String, channel: String, fileId: String): SpaceFileGrant =
        providerGrant(readPage(fileScope(space, channel, "remove").apply { addProperty("file", fileId) }),
            requireNotNull(fileStorage(space)), SpaceFileGrant.Action.DELETE)

    suspend fun fileAbandonPermission(space: String, channel: String, objectId: String): SpaceFileGrant? {
        val result = readPage(fileScope(space, channel, "abandon").apply { addProperty("object", objectId) })
        if (!result.has("permission")) return null
        return providerGrant(result, requireNotNull(fileStorage(space)), SpaceFileGrant.Action.DELETE)
    }

    private fun fileScope(space: String, channel: String, access: String) = JsonObject().apply {
        addProperty("space_id", space); addProperty("channel_id", channel)
        addProperty("stream", "files"); addProperty("cursor", 0); addProperty("access", access)
    }

    private fun providerGrant(result: JsonObject, lease: SpaceFileLease, action: SpaceFileGrant.Action): SpaceFileGrant {
        require(supportsFileStorage)
        return SpaceFileGrant.read(result.text("permission"), service, lease.offer.provider).also {
            require(it.action == action && it.space == lease.offer.space && it.lease == lease.id)
            it.requireCurrent(clock.millis(), allowClockSkew = true)
        }
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
            changeStatus(space, change), change.getAsJsonArray("private_members")?.map { it.asString }?.toSet(), change.get("category_id")?.takeUnless { it.isJsonNull }?.asString)
    }

    fun createChannel(space: String, profile: SpaceChannelProfile, categoryId: String? = null): String = store.transaction {
        owned()
        val scope = SpaceCrypto.Scope(service, space, id())
        val state = state(space)
        require(!state.has("required_control"))
        enqueueChannel(space, state, profile, scope, categoryId = categoryId)
        scope.channelId
    }
    fun createPrivateChannel(space: String, profile: SpaceChannelProfile, members: Set<String>, categoryId: String? = null): String = store.transaction {
        owned()
        val state = state(space)
        require(!state.has("required_control"))
        val scope = SpaceCrypto.Scope(service, space, id())
        enqueueChannel(space, state, profile, scope, members, categoryId)
        scope.channelId
    }

    private fun SpaceClientStore.Transaction.enqueueChannel(space: String, state: JsonObject,
        profile: SpaceChannelProfile, scope: SpaceCrypto.Scope, members: Set<String>? = null, categoryId: String? = null) {
        val snapshot = journal(space).snapshot
        require(snapshot.protectedChat == null) { "Protected chats have one private conversation" }
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
            val metadataChannel = if (members != null) scope.channelId else requireNotNull(snapshot.entryChannelId)
            val metadataKeys = if (members != null) requireNotNull(crypto)
                else channelState(space, metadataChannel, state).let { data ->
                    require(data.get("joined")?.asBoolean == true) { "Wait for current private channel keys" }
                    SpaceCrypto.restore(decode(data.text("crypto")))
                }
            require(metadataKeys.scope?.channelId == metadataChannel)
            if (members == null) {
                val entry = snapshot.channels.getValue(metadataChannel)
                require(entry.visibility == SpaceVisibility.PRIVATE && entry.retiredDevices.isEmpty() &&
                    metadataKeys.scope == SpaceCrypto.Scope(service, space, metadataChannel, entry.generation) &&
                    metadataKeys.epoch == entry.epoch && metadataKeys.bindings == entry.devices)
            }
            metadataKeys.publish(wallet, "channel-profile", id(), requireNotNull(snapshot.controlId), JsonObject().apply {
                addProperty("version", 1); addProperty("channel_id", scope.channelId); add("profile", profile.json())
            }.toString(), clock.millis())
        } else null
        val operation = id()
        val candidate = SpaceChannelRecord(0, crypto?.bindings.orEmpty(), scope.generation, visibility = if (private) SpaceVisibility.PRIVATE else SpaceVisibility.PUBLIC,
            profile = profile.takeUnless { private }, allowedMembers = members, metadataChannelId = if (members != null) scope.channelId else snapshot.entryChannelId)
        val assignment = categoryAssignment(space, state, snapshot, scope.channelId, candidate, profile, operation, categoryId,
            overrides = crypto?.let { mapOf(scope.channelId to it) }.orEmpty())
        val original = event(space, "create-channel", operation) {
            addProperty("control_id", snapshot.controlId); addProperty("channel_id", scope.channelId)
            assignment.entrySet().forEach { (key, value) -> add(key, value) }
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
            add("category_id", assignment.get("category_id"))
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
        if (journal(space).snapshot.dissolved) {
            state.remove("starter_channels"); put(stateKey(space), bytes(state)); return
        }
        enqueueChannel(space, state, SpaceChannelProfile.fromJson(next.getAsJsonObject("profile")),
            SpaceCrypto.Scope.fromJson(next.getAsJsonObject("scope")))
    }
    private fun SpaceClientStore.Transaction.prepareChannelJoin(space: String, state: JsonObject, snapshot: SpaceSnapshot, channel: String) {
        if (snapshot.dissolved) return
        val record = requireNotNull(snapshot.channels[channel])
        val data = channelState(space, channel, state)
        if (record.visibility == SpaceVisibility.PRIVATE && state.get("joined").asBoolean &&
            data.get("joined")?.asBoolean != true && !data.has("joining_channel") &&
            !data.has("replacement_request") && data.get("device_replaced")?.asBoolean != true &&
            record.devices.keys.none { it.split(':')[1] == wallet.publicKey }) {
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
    fun selectChannel(space: String, channel: String, requestKeys: Boolean = true) = store.transaction {
        owned()
        val state = state(space)
        val journal = journal(space)
        val canAccess = journal.canAccessChannel(wallet.publicKey, channel)
        // Returning from an already-open cached thread is local navigation,
        // including after access ended. It never requests fresh membership.
        require(canAccess || channel == selectedChannel(state))
        if (canAccess && requestKeys) prepareChannelJoin(space, state, journal.snapshot, channel)
        state.addProperty("selected_channel", channel); state.remove("selected_thread"); put(stateKey(space), bytes(state))
    }

    /** Persist only the accepted identity of the focus, never a stale copy of its text. */
    fun selectThread(space: String, parent: Message, channel: String? = null, requestKeys: Boolean = true) = store.transaction {
        owned()
        val state = state(space); val target = channel ?: selectedChannel(state)
        val journal = journal(space)
        val canAccess = journal.canAccessChannel(wallet.publicKey, target)
        require(canAccess || target == selectedChannel(state))
        threadParent(space, target, parent)
        indexConfirmedMessage(space, target, json(requireNotNull(get(contentPrefix(space, target) + positionKey(requireNotNull(parent.position))))))
        if (canAccess && requestKeys) prepareChannelJoin(space, state, journal.snapshot, target)
        state.addProperty("selected_channel", target)
        state.add("selected_thread", JsonObject().apply {
            addProperty("version", 1); addProperty("channel_id", target)
            addProperty("message_id", parent.id); addProperty("position", parent.position)
        })
        put(stateKey(space), bytes(state))
    }

    fun selectedThread(space: String, channel: String? = null): Message? = store.project { store.transaction {
        owned()
        val state = state(space); val target = channel ?: selectedChannel(state)
        val saved = state.getAsJsonObject("selected_thread") ?: return@transaction null
        if (saved.number("version") != 1L || saved.text("channel_id") != target) return@transaction null
        message(space, saved.text("message_id"), target)?.takeIf { it.position == saved.number("position") }
    } }

    /** A bounded lookup for a previously accepted message, including its current changes. */
    fun message(space: String, messageId: String, channel: String? = null): Message? = store.project { store.transaction {
        owned()
        val target = channel ?: selectedChannel(state(space))
        val pointer = get(messageIdKey(space, target, messageId)) ?: return@transaction null
        val receipt = get(pointer.toString(Charsets.UTF_8)) ?: return@transaction null
        val body = json(receipt)
        val original = SaitoEnvelope.decode(body.text("event"))
        require(original.id == messageId)
        conversationTimeline(space, body.number("position"), 1, target, messageThread(original.body)).firstOrNull { it.id == messageId }
    } }

    data class Article(val id: String, val author: String, val content: SpaceArticleContent?, val createdAt: Long,
        val pending: Boolean, val attempted: Boolean, val rejected: Boolean, val missingKeys: Boolean, val edited: Boolean, val deleted: Boolean) {
        override fun toString() = "Space article"
    }

    fun article(space: String, articleId: String, channel: String? = null): Article? =
        message(space, articleId, channel)?.asArticle()

    fun articleLink(space: String, channel: String, articleId: String): String = store.project {
        val reference = SpaceArticleReference(SpacePublicReference(node, service, space), channel, articleId)
        require(cachedArticle(reference)?.let { !it.deleted && it.content != null } == true)
        reference.link()
    }

    fun cachedArticleLink(link: String): Article? = store.project { cachedArticle(articleReference(link)) }

    private fun cachedArticle(reference: SpaceArticleReference): Article? {
        val available = store.transaction {
            val space = reference.community.spaceId
            if (get("identity") == null || get(stateKey(space)) == null) return@transaction false
            owned()
            val journal = journal(space)
            get(articleReadDeniedKey(space, reference.channel)) == null &&
                journal.snapshot.channels[reference.channel]?.rootContentKind == "article" &&
                journal.canAccessChannel(wallet.publicKey, reference.channel)
        }
        return if (available) article(reference.community.spaceId, reference.article, reference.channel) else null
    }

    private fun articleReference(link: String): SpaceArticleReference = SpaceArticleReference.parse(link).also {
        if (it.community.node != node) throw DifferentNode(it.community.node)
        require(it.community.serviceKey == service) { "This reference does not match the selected service" }
    }

    private fun articleReadDeniedKey(space: String, channel: String) = "$space/channels/$channel/article-read-denied"

    private fun rememberArticleReadDenial(space: String, channel: String) = store.transaction {
        if (get(stateKey(space)) != null) {
            owned()
            if (journal(space).snapshot.channels[channel]?.rootContentKind == "article")
                put(articleReadDeniedKey(space, channel), byteArrayOf(1))
        }
    }

    suspend fun openArticleLink(link: String): Article? {
        val reference = articleReference(link)
        val space = reference.community.spaceId
        val channel = reference.channel
        val page = try {
            val known = store.transaction { get(stateKey(space)) != null }
            if (known) syncControls(space) else openPublic(space)
            val hasActiveKeys = store.transaction {
                owned()
                val journal = journal(space)
                val record = journal.snapshot.channels[channel]
                if (record?.rootContentKind != "article" || !journal.canAccessChannel(wallet.publicKey, channel))
                    throw ReadDenied(space, ReadProblem.UNAVAILABLE)
                record.visibility == SpaceVisibility.PRIVATE && channelState(space, channel).get("joined")?.asBoolean == true
            }
            if (hasActiveKeys) refreshKeys(space, channel)
            messagePage(space, channel, limit = 1, exactArticle = reference.article)
        } catch (denied: ReadDenied) {
            // The service can revoke access between the controls and exact
            // content reads. An offline retry must honor the observed denial.
            rememberArticleReadDenial(space, channel)
            throw denied
        }
        if (page.count == 0) return null
        return cachedArticleLink(link)
    }

    data class Destination(val address: SpaceDestination, val record: Message? = null, val parent: Message? = null) {
        override fun toString() = "Verified Space destination"
    }

    private fun requireDestinationService(address: SpaceDestination) {
        if (address.community.node != node) throw DifferentNode(address.community.node)
        require(address.community.serviceKey == service) { "This reference does not match the selected service" }
    }

    private fun destinationDenialKey(space: String, channel: String) = "$space/channels/$channel/destination-read-denied"

    private fun rememberDestinationDenial(space: String, channel: String) = store.transaction {
        owned()
        put(destinationDenialKey(space, channel), byteArrayOf(1))
    }

    /** Reading a link does not select a channel, enroll a device or reserve a room seat. */
    suspend fun openDestination(address: SpaceDestination): Destination? {
        requireDestinationService(address)
        val space = address.community.spaceId
        val channel = address.channel
        val known = store.transaction { get(stateKey(space)) != null }
        try {
            if (known) syncControls(space) else openPublic(space)
        } catch (denied: ReadDenied) {
            if (known) rememberDestinationDenial(space, channel)
            throw denied
        }
        val refreshingKeys = store.project { store.transaction {
            owned()
            val journal = journal(space)
            val target = journal.snapshot.channels[channel]
            if (target == null || !address.kind.acceptsChannel(target.kind ?: target.profile?.kind) ||
                !journal.canAccessChannel(wallet.publicKey, channel)) throw ReadDenied(space, ReadProblem.UNAVAILABLE)
            target.visibility == SpaceVisibility.PRIVATE && channelState(space, channel).get("joined")?.asBoolean == true
        } }
        try {
            if (!address.kind.requiresRecord()) {
                val checked = readPage(JsonObject().apply {
                    addProperty("space_id", space)
                    addProperty("stream", "messages")
                    addProperty("channel_id", channel)
                    addProperty("cursor", Long.MAX_VALUE)
                    addProperty("direction", "before")
                    addProperty("limit", 1)
                    addProperty("destination_kind", address.kind.wire)
                })
                require(checked.get("destination_authorized")?.asBoolean == true && checked.getAsJsonArray("items").size() == 0)
            } else {
                if (refreshingKeys) refreshKeys(space, channel)
                val exact = messagePage(space, channel, limit = 1, thread = address.thread, exactDestination = address)
                if (exact.count == 0) return null
                address.thread?.let { parent ->
                    val root = address.copy(kind = SpaceDestination.Kind.THREAD, record = parent, thread = null)
                    if (messagePage(space, channel, limit = 1, exactDestination = root).count == 0) return null
                }
            }
        } catch (denied: ReadDenied) {
            rememberDestinationDenial(space, channel)
            throw denied
        }
        store.transaction { owned(); remove(destinationDenialKey(space, channel)) }
        return cachedDestination(address)
    }

    fun cachedDestination(address: SpaceDestination): Destination? = store.project { store.transaction {
        requireDestinationService(address)
        val space = address.community.spaceId
        val channel = address.channel
        if (get("identity") == null || get(stateKey(space)) == null || get(destinationDenialKey(space, channel)) != null)
            return@transaction null
        owned()
        val journal = journal(space)
        val target = journal.snapshot.channels[channel] ?: return@transaction null
        if (!journal.canAccessChannel(wallet.publicKey, channel) || !address.kind.acceptsChannel(target.kind ?: target.profile?.kind))
            return@transaction null
        fun accepted(id: String, kind: String, thread: String?): Message? {
            val receiptKey = get(messageIdKey(space, channel, id))?.toString(Charsets.UTF_8) ?: return null
            val stored = get(receiptKey) ?: return null
            val original = SaitoEnvelope.decode(originalEnvelope(stored))
            require(original.id == id && original.body.text("channel_id") == channel)
            if (original.body.text("content_kind") != kind || messageThread(original.body) != thread) return null
            return message(space, id, channel)?.takeIf { it.position != null && !it.pending && !it.rejected && !it.cancelled }
        }
        val record = address.record?.let { accepted(it, requireNotNull(address.kind.expectedRecord(address.thread)), address.thread) ?: return@transaction null }
        val parent = if (address.kind == SpaceDestination.Kind.THREAD) record else address.thread?.let {
            accepted(it, "message", null) ?: return@transaction null
        }
        Destination(address, record.takeUnless { address.kind == SpaceDestination.Kind.THREAD }, parent)
    } }

    /** A bounded page of previously accepted records. The current signed
     * channel audience, local keys and accepted edits decide what may appear. */
    fun searchCached(space: String, query: String, filter: SpaceSearchFilter = SpaceSearchFilter(),
        after: SpaceSearchCursor? = null, limit: Int = 15): SpaceSearchPage = store.project { store.transaction {
        owned()
        require(limit in 1..20)
        val needle = query.trim().also { require(it.length <= 128) }
        if (needle.isEmpty()) return@transaction SpaceSearchPage(emptyList(), null)
        val journal = journal(space)
        val channels = channels(space).filter { channel ->
            (filter.channel == null || channel.id == filter.channel) &&
                (after == null || channel.id >= after.channel) &&
                channel.profile.kind != SpaceChannelKind.VOICE &&
                (filter.kind == null || filter.kind.acceptsChannel(channel.profile.kind)) &&
                get(destinationDenialKey(space, channel.id)) == null && journal.canAccessChannel(wallet.publicKey, channel.id)
        }.sortedBy { it.id }
        val hits = ArrayList<SpaceSearchHit>(limit)
        val community = SpacePublicReference(node, service, space)
        var remaining = 40
        for ((index, channel) in channels.take(4).withIndex()) {
            val prefix = contentPrefix(space, channel.id)
            val before = after?.takeIf { it.channel == channel.id }?.before?.let { prefix + positionKey(it) }
            val rows = pageBefore(prefix, before ?: prefix + '\uffff', remaining + 1)
            var examined = 0
            for (row in rows.take(remaining)) {
                examined++
                val receipt = json(row.value)
                val original = SaitoEnvelope.decode(receipt.text("event"))
                val kind = when (original.body.get("content_kind")?.asString) {
                    "message", "thread-reply" -> SpaceDestination.Kind.MESSAGE
                    "article" -> SpaceDestination.Kind.ARTICLE
                    "file" -> SpaceDestination.Kind.FILE
                    else -> continue
                }
                if (filter.kind != null && kind != filter.kind) continue
                val content = message(space, original.id, channel.id)?.takeIf { !it.deleted && !it.missingKeys }?.text ?: continue
                val matching = searchText(kind, content, needle) ?: continue
                val address = SpaceDestination(community, channel.id, kind, original.id, messageThread(original.body))
                hits.add(SpaceSearchHit(address, channel.profile.name, original.author, matching))
                if (hits.size == limit) break
            }
            remaining -= examined
            if (examined < rows.size) {
                val position = rows[examined - 1].key.removePrefix(prefix).toLong()
                return@transaction SpaceSearchPage(hits, SpaceSearchCursor(channel.id, position))
            }
            if (remaining == 0 || hits.size == limit || index == 3)
                return@transaction SpaceSearchPage(hits, channels.getOrNull(index + 1)?.let { SpaceSearchCursor(it.id) })
        }
        SpaceSearchPage(hits, null)
    } }

    private fun searchText(kind: SpaceDestination.Kind, content: String, needle: String): String? {
        val candidates = when (kind) {
            SpaceDestination.Kind.ARTICLE -> when (val article = runCatching { SpaceArticleContent.decode(content) }.getOrNull()) {
                is SpaceArticleContent.Original -> listOf(article.title, article.subtitle, article.markdown)
                is SpaceArticleContent.PublicStack -> listOf(article.url)
                null -> emptyList()
            }
            SpaceDestination.Kind.FILE -> runCatching { listOf(SpaceFileDescriptor.read(content).name) }.getOrDefault(emptyList())
            else -> listOf(content)
        }
        val match = candidates.firstOrNull { it.contains(needle, ignoreCase = true) } ?: return null
        val start = (match.indexOf(needle, ignoreCase = true) - 30).coerceAtLeast(0)
        return match.drop(start).take(170).trim()
    }

    private fun Message.asArticle(): Article? {
        if (!isArticle) return null
        return Article(id, author, text?.let { runCatching { SpaceArticleContent.decode(it) }.getOrNull() },
            createdAt, pending, attempted, rejected, missingKeys, edited, deleted)
    }

    data class Community(val id: String, val name: String, val channel: String, val joined: Boolean,
        val canInvite: Boolean, val pending: Boolean, val canSend: Boolean, val membershipRequested: Boolean = false, val unread: Long? = null, val hasUnread: Boolean? = null,
        val canManage: Boolean = false, val dissolved: Boolean = false, val canReply: Boolean = false, val canReact: Boolean = false, val canPin: Boolean = false, val personalUnread: Long? = null, val protectedChat: ProtectedChat? = null,
        val deviceReplaced: Boolean = false, val replacingDevice: Boolean = false, val replacementRejected: Boolean = false,
        val archiveScope: SpaceCrypto.Scope? = null, val legacyChat: String? = null, val writeRestrictedUntil: Long? = null,
        val articleReadDenied: Boolean = false, val writeRestrictedSpaceWide: Boolean = false)
    enum class MessageChangeKind { EDIT, DELETE }
    data class MessageChange(val operationId: String, val kind: MessageChangeKind, val attempted: Boolean,
        val rejected: Boolean, val text: String?, val cancelled: Boolean = false) {
        override fun toString() = "Private community message change"
    }
    data class PinChange(val operationId: String, val attempted: Boolean, val rejected: Boolean, val cancelled: Boolean = false) {
        override fun toString() = "Private community pin change"
    }
    data class Reaction(val emoji: String, val count: Long?, val selected: Boolean) {
        override fun toString() = "Private community reaction"
    }
    data class Message(val id: String, val author: String, val createdAt: Long, val text: String?,
        val pending: Boolean, val attempted: Boolean, val missingKeys: Boolean, val rejected: Boolean = false, val position: Long? = null,
        val edited: Boolean = false, val deleted: Boolean = false, val replyCount: Long = 0, val change: MessageChange? = null, val cancelled: Boolean = false, val reactions: List<Reaction> = emptyList(), val reactionsComplete: Boolean = true, val reactionsHaveMore: Boolean = false, val ownReactionsAvailable: Boolean = true, val pinned: Boolean = false, val pinPending: Boolean = false, val pinAvailable: Boolean = true, val pinChange: PinChange? = null,
        val isArticle: Boolean = false, val editedAt: Long? = null, val isFile: Boolean = false) {
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
    /** Export archive keys from this authenticated service partition, including
     * keys already recovered on the current device. No sending state is copied. */
    fun exportHistoryKeys(): List<SpaceHistoryKeys> = store.transaction {
        if (get("identity") == null) return@transaction emptyList()
        owned()
        val recovered = linkedMapOf<SpaceCrypto.Scope, SpaceHistoryKeys>()
        fun retain(keys: SpaceHistoryKeys) {
            recovered[keys.scope] = recovered[keys.scope]?.merge(keys) ?: keys
            require(recovered.size <= AccountRecoveryBackup.MAX_HISTORY_SCOPES) { "Account backup has too many history scopes" }
        }
        forEachRecoveryRow("recovered-archives/") { record ->
            retain(SpaceHistoryKeys.restore(record.value, wallet.publicKey))
        }
        forEachRecoveryRow("communities/") { record ->
            val space = record.value.toString(Charsets.UTF_8)
            val snapshot = journal(space).snapshot
            for (channel in snapshot.channels.keys) {
                val data = channelState(space, channel)
                val saved = data.get("crypto")?.asString ?: continue
                val session = SpaceCrypto.restore(decode(saved))
                if (session.scope != null) retain(session.archiveHistorySnapshot())
            }
        }
        recovered.values.toList()
    }

    /** Import readable history without enrolling a device or touching pending
     * operations. The selected node must already authenticate this service. */
    fun restoreHistoryKeys(archives: List<SpaceHistoryKeys>) {
        require(archives.size <= AccountRecoveryBackup.MAX_HISTORY_SCOPES)
        require(archives.all { it.owner == wallet.publicKey && it.scope.serviceKey == service })
        store.transaction {
            owned(initialize = true)
            for (keys in archives) {
                val key = recoveredArchiveKey(keys.scope)
                val previous = get(key)
                val existing = previous?.let { SpaceHistoryKeys.restore(it, wallet.publicKey) }
                val merged = (existing?.merge(keys) ?: keys).save()
                if (previous == null || !previous.contentEquals(merged)) put(key, merged)
            }
        }
    }

    fun recoveredScopes(): List<SpaceCrypto.Scope> = store.transaction {
        if (get("identity") == null) return@transaction emptyList()
        owned()
        buildList {
            forEachRecoveryRow("recovered-archives/") { row ->
                val (space, channel, generation) = row.key.removePrefix("recovered-archives/").split('/')
                add(SpaceCrypto.Scope(service, space, channel, generation))
            }
        }
    }

    class RecoveredHistoryOtherModule : IllegalArgumentException("This conversation belongs to a different module")

    /** Register authenticated read-only history. This path never creates a
     * key package, modifies membership or restores a saved sender. */
    suspend fun openRecoveredHistory(scope: SpaceCrypto.Scope, expectProtectedChat: Boolean): String {
        require(scope.serviceKey == service)
        val space = scope.spaceId
        val archived = store.transaction {
            owned()
            SpaceHistoryKeys.restore(requireNotNull(get(recoveredArchiveKey(scope))), wallet.publicKey)
        }
        val (verified, controls) = loadSignedControls(space)
        val snapshot = verified.snapshot
        if ((snapshot.protectedChat != null) != expectProtectedChat) throw RecoveredHistoryOtherModule()
        require(snapshot.channels[scope.channelId]?.visibility == SpaceVisibility.PRIVATE &&
            verified.canAccessChannel(wallet.publicKey, scope.channelId))
        val root = requireNotNull(snapshot.generalChannelId)
        val rootScope = SpaceCrypto.Scope(service, space, root, snapshot.channels.getValue(root).generation)
        val profile = if (scope.channelId != root) null else runCatching {
            val created = SaitoEnvelope.decode(SaitoEnvelope.decode(controls.first()).body.text("event"))
            val metadata = decode(created.body.text("metadata")).toString(Charsets.UTF_8)
            SpaceProfile.fromJson(json(archived.read(metadata).text.toByteArray(Charsets.UTF_8)))
        }.getOrNull()
        val exists = store.transaction { get(stateKey(space)) != null }
        if (exists) syncControls(space)
        store.transaction {
            owned()
            val state = if (exists) state(space) else JsonObject().apply {
                require(get(stateKey(space)) == null)
                controls.forEach { receipt ->
                    val position = SaitoEnvelope.decode(receipt).body.number("position")
                    put("$space/control/${positionKey(position)}", receipt.toByteArray(Charsets.UTF_8))
                }
                add("scope", rootScope.json())
                addProperty("name", profile?.name ?: snapshot.publicProfile?.name ?:
                    if (expectProtectedChat) "Recovered chat" else "Recovered Space")
                profile?.let { add("profile", it.json()) }
                addProperty("joined", false); addProperty("next_outbox", 0); addProperty("content_cursor", 0)
                put(stateKey(space), bytes(this))
                put("communities/$space", space.toByteArray(Charsets.UTF_8))
            }
            val target = channelState(space, scope.channelId, state)
            state.addProperty("selected_channel", scope.channelId)
            if (target.get("joined")?.asBoolean != true) target.addProperty("archive_only", true)
            if (scope.channelId != root) putChannelState(space, scope.channelId, target)
            put(stateKey(space), bytes(state))
        }
        refresh(space, scope.channelId)
        return space
    }

    private fun SpaceClientStore.Transaction.forEachRecoveryRow(prefix: String, visit: (SpaceClientStore.Entry) -> Unit) {
        var cursor = prefix
        while (true) {
            val rows = page(prefix, cursor, limit = 50)
            rows.forEach(visit)
            if (rows.size < 50) return
            cursor = rows.last().key + '\u0000'
        }
    }

    private fun recoveredArchiveKey(scope: SpaceCrypto.Scope) =
        "recovered-archives/${scope.spaceId}/${scope.channelId}/${scope.generation}"

    private fun SpaceClientStore.Transaction.protectedHistoryReader(space: String, channel: String): (String, SignedSaitoMessage) -> String {
        val active by lazy {
            channelState(space, channel).get("crypto")?.asString?.let { SpaceCrypto.restore(decode(it)) }
                ?.takeIf { session ->
                    session.scope?.let { it.serviceKey == service && it.spaceId == space && it.channelId == channel } == true
                }
        }
        val recovered = mutableMapOf<String, SpaceHistoryKeys?>()
        return read@{ envelope, signed ->
            val generation = signed.body.text("key_generation")
            val current = active
            if (current != null && current.scope?.generation == generation) {
                try { return@read current.read(envelope).text }
                catch (_: SpaceCrypto.MissingKeys) { /* A portable archive may cover an earlier epoch. */ }
            }
            if (!recovered.containsKey(generation)) {
                val scope = SpaceCrypto.Scope(service, space, channel, generation)
                recovered[generation] = get(recoveredArchiveKey(scope))?.let { SpaceHistoryKeys.restore(it, wallet.publicKey) }
            }
            val archive = recovered[generation] ?: throw SpaceCrypto.MissingKeys()
            archive.read(envelope).text
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
        val eligibleChannels = snapshot.channels.filter { (_, record) -> record.visibility == SpaceVisibility.PUBLIC ||
            record.devices.keys.any { it.split(':')[1] == wallet.publicKey } }.keys
        val eligible = eligibleChannels.map { channelState(space, it, state) }
        val counts = eligible.map { it.get("unread")?.asLong }
        val unread = counts.takeIf { it.isNotEmpty() && it.none { count -> count == null } }
            ?.fold(0L) { total, count -> Math.addExact(total, requireNotNull(count)) }
        val channelAttention = eligibleChannels.map { cachedAttention(space, it) }
        val personal = channelAttention.map { it.personal }.takeIf { it.isNotEmpty() && it.none { count -> count == null } }
            ?.fold(0L) { total, count -> Math.addExact(total, requireNotNull(count)) }
        val activity = eligible.mapIndexed { index, value -> channelAttention[index].hasUnread ?: unreadActivity(value)?.takeIf { it } }
        val hasUnread = when {
            activity.any { it == true } -> true
            activity.isNotEmpty() && activity.all { it == false } -> false
            else -> null
        }
        val restrictedUntil = journal.writeRestrictedUntil(wallet.publicKey, scope.channelId, clock.millis())
        val articleReadDenied = channel?.rootContentKind == "article" &&
            (get(articleReadDeniedKey(space, scope.channelId)) != null || !journal.canAccessChannel(wallet.publicKey, scope.channelId))
        val canReply = !articleReadDenied && restrictedUntil == null && state.get("joined").asBoolean && !state.has("control_pending") && !state.has("required_control") &&
            journal.canContributeChannel(wallet.publicKey, scope.channelId) &&
            (data.get("joined")?.asBoolean == true || channel?.visibility == SpaceVisibility.PUBLIC) &&
            channel?.retiredDevices?.isEmpty() == true && channel.archived != true
        return Community(space, state.text("name"), scope.channelId, state.get("joined").asBoolean,
            !snapshot.dissolved && role in setOf(SpaceRole.OWNER.name, SpaceRole.ADMIN.name),
            state.has("starter_channels") || state.has("desired_listed") || page("$space/pending/", limit = 1).isNotEmpty(),
            canReply && (!adminPostsOnly || role in setOf(SpaceRole.OWNER.name, SpaceRole.ADMIN.name)),
            !state.get("joined").asBoolean && state.has("joining_invitation"), unread, hasUnread,
            canManage = !snapshot.dissolved && state.get("joined").asBoolean && !state.has("control_pending") && !state.has("required_control") &&
                snapshot.members[wallet.publicKey] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN), dissolved = snapshot.dissolved, canReply = canReply,
            canReact = canReply && (supportsMessageReactions || data.get("reactions_supported")?.asBoolean == true),
            canPin = canReply && snapshot.members[wallet.publicKey] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN) &&
                (supportsMessagePins || data.get("pins_supported")?.asBoolean == true), personalUnread = personal,
            protectedChat = snapshot.protectedChat ?: state.getAsJsonObject("new_protected_chat")?.let { ProtectedChat(it.get("direct_peer")?.asString) },
            writeRestrictedUntil = restrictedUntil, articleReadDenied = articleReadDenied,
            writeRestrictedSpaceWide = restrictedUntil != null && snapshot.writeRestrictions[wallet.publicKey to null]?.expiresAt == restrictedUntil,
            deviceReplaced = data.get("device_replaced")?.asBoolean == true, replacingDevice = data.has("replacement_request"),
            replacementRejected = data.get("replacement_rejected")?.asBoolean == true,
            archiveScope = scope.takeIf { data.get("archive_only")?.asBoolean == true }, legacyChat = state.get("legacy_chat")?.asString)
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
    fun articleDraft(space: String, channel: String, article: String?): SpaceArticleDraft? = store.transaction {
        owned()
        require(journal(space).snapshot.channels[channel]?.rootContentKind == "article")
        get(articleDraftKey(space, channel, article))?.let(SpaceArticleDraft::restore)
    }
    fun saveArticleDraft(space: String, channel: String, article: String?, draft: SpaceArticleDraft) = store.transaction {
        owned()
        require(journal(space).snapshot.channels[channel]?.rootContentKind == "article")
        put(articleDraftKey(space, channel, article), draft.bytes())
    }
    fun threadDraft(space: String, parent: Message, channel: String? = null): String = store.transaction {
        owned(); val target = channel ?: selectedChannel(state(space)); threadParent(space, target, parent)
        get(threadDraftKey(space, target, parent.id))?.toString(Charsets.UTF_8) ?: ""
    }
    fun saveThreadDraft(space: String, parent: Message, text: String, channel: String? = null) = store.transaction {
        owned(); val target = channel ?: selectedChannel(state(space)); threadParent(space, target, parent)
        val value = text.toByteArray(Charsets.UTF_8).also { require(it.size <= 64 * 1024) }
        put(threadDraftKey(space, target, parent.id), value)
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
    data class InvitationPreview(val spaceId: String, val name: String, val profile: SpaceProfile = SpaceProfile(name),
        val expiresAt: Long = 0, val rulesToken: String = "", val protectedChat: ProtectedChat? = null, val owner: String? = null)
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
        val channel = journal.snapshot.channels.entries.firstOrNull { it.key == journal.snapshot.entryChannelId && it.value.visibility == SpaceVisibility.PUBLIC }
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
        val entry = requireNotNull(journal.snapshot.entryChannelId)
        val scope = SpaceCrypto.Scope(service, space, entry, journal.snapshot.channels.getValue(entry).generation)
        require(!journal.snapshot.dissolved && journal.snapshot.visibility == SpaceVisibility.PUBLIC &&
            journal.snapshot.channels[scope.channelId]?.visibility == SpaceVisibility.PUBLIC)
        require(wallet.publicKey !in journal.snapshot.members && !current.get("joined").asBoolean)
        val profile = requireNotNull(journal.snapshot.publicProfile)
        require((acceptedRulesToken ?: "") == profile.publicRulesToken) { "Accept the community house rules before joining" }
        if (journal.snapshot.channels[selectedChannel(current)]?.visibility != SpaceVisibility.PUBLIC)
            current.addProperty("selected_channel", entry)
        queue(space, current, event(space, "join-public") {
            addProperty("control_id", journal.snapshot.controlId); addProperty("channel_id", scope.channelId)
            addProperty("rules_token", profile.publicRulesToken)
        })
    }
    data class DissolutionChange(val id: String, val status: ChangeStatus)

    fun dissolve(space: String): String = store.transaction {
        owned()
        val state = state(space)
        val snapshot = journal(space).snapshot
        require(state.get("joined").asBoolean && snapshot.owner == wallet.publicKey && !snapshot.dissolved)
        require(!state.has("required_control") && !state.has("control_pending")) { "A community change is awaiting confirmation" }
        val original = event(space, "dissolve") { addProperty("control_id", requireNotNull(snapshot.controlId)) }
        state.add("dissolution_change", JsonObject().apply {
            addProperty("id", original.id); addProperty("status", ChangeStatus.QUEUED.name)
        })
        queue(space, state, original)
        original.id
    }

    fun dissolutionChange(space: String): DissolutionChange? = store.transaction {
        owned()
        val change = state(space).getAsJsonObject("dissolution_change") ?: return@transaction null
        DissolutionChange(change.text("id"), changeStatus(space, change))
    }

    private fun recordDissolutionResult(state: JsonObject, original: SignedSaitoMessage, status: ChangeStatus) {
        val change = state.getAsJsonObject("dissolution_change") ?: return
        if (change.text("id") != original.id) return
        require(original.author == wallet.publicKey && original.body.text("kind") == "dissolve")
        change.addProperty("status", status.name)
    }

    data class VisibilityChange(val id: String, val visibility: SpaceVisibility, val entryChannelId: String, val status: ChangeStatus, val listed: Boolean? = null)

    /** New entrances preserve earlier private audiences and keep old public copies truthful. */
    fun changeVisibility(space: String, visibility: SpaceVisibility, listed: Boolean = true): String = store.transaction {
        owned()
        val state = state(space)
        val snapshot = journal(space).snapshot
        require(snapshot.protectedChat == null) { "A protected conversation stays private" }
        require(state.get("joined").asBoolean && snapshot.owner == wallet.publicKey)
        require(!state.has("required_control") && !state.has("control_pending")) { "A community change is awaiting confirmation" }
        require(!snapshot.dissolved && snapshot.visibility != visibility)
        val opening = visibility == SpaceVisibility.PUBLIC
        val profile = (state.getAsJsonObject("profile")?.let(SpaceProfile::fromJson) ?: SpaceProfile(state.text("name")))
            .copy(visibility = visibility, listed = opening && listed)
        val rebound = if (opening) snapshot.channels.filter { (channel, record) ->
            record.visibility == SpaceVisibility.PRIVATE && record.allowedMembers == null && record.metadataGroupId(channel, snapshot) != channel
        } else emptyMap()
        val projected = snapshot.copy(channels = snapshot.channels.mapValues { (channel, record) ->
            if (channel in rebound) record.copy(metadataChannelId = channel) else record
        })
        val channelMetadata = JsonObject()
        for ((channel, record) in rebound) {
            val data = channelState(space, channel, state)
            require(data.get("joined")?.asBoolean == true && record.retiredDevices.isEmpty()) { "Wait for current private channel keys" }
            val keys = SpaceCrypto.restore(decode(data.text("crypto")))
            require(keys.scope == SpaceCrypto.Scope(service, space, channel, record.generation) && keys.epoch == record.epoch && keys.bindings == record.devices)
            val channelProfile = requireNotNull(channelProfile(space, channel, state, data, record))
            val metadata = keys.publish(wallet, "channel-profile", id(), requireNotNull(snapshot.controlId), JsonObject().apply {
                addProperty("version", 1); addProperty("channel_id", channel); add("profile", channelProfile.json())
            }.toString(), clock.millis())
            channelMetadata.addProperty(channel, encode(metadata.envelope().toByteArray(Charsets.UTF_8)))
        }
        val categoryMetadata = JsonObject()
        for (category in rebound.values.mapNotNull { it.categoryId }.toSet().sorted()) {
            val record = snapshot.categories.getValue(category)
            if (record.profile != null) continue
            val categoryProfile = requireNotNull(categoryProfile(space, category, state, journal(space)))
            categoryMetadata.add(category, JsonObject().apply {
                add("category_metadata", encryptedCategory(space, category, categoryProfile, record.revision,
                    projected.categoryGroups(category, record.defaultGroup), state, projected))
            })
        }
        val entry = SpaceCrypto.Scope(service, space, id())
        val keys = if (opening) null else SpaceCrypto.device(wallet.publicKey, id()).create(entry)
        val entranceProfile = SpaceChannelProfile(if (opening) "public-lobby" else "members-lobby")
        val metadata = keys?.publish(wallet, "channel-profile", id(), requireNotNull(snapshot.controlId), JsonObject().apply {
            addProperty("version", 1); addProperty("channel_id", entry.channelId); add("profile", entranceProfile.json())
        }.toString(), clock.millis())
        val original = event(space, "set-visibility") {
            addProperty("control_id", requireNotNull(snapshot.controlId))
            addProperty("visibility", if (opening) "public" else "private")
            addProperty("channel_id", entry.channelId); addProperty("key_generation", entry.generation); addProperty("epoch", 0)
            if (opening) {
                add("public_profile", profile.json()); add("channel_profile", entranceProfile.json()); add("devices", JsonObject())
                add("channel_metadata_updates", channelMetadata); add("category_metadata_updates", categoryMetadata)
            } else {
                add("devices", devices(requireNotNull(keys).bindings))
                addProperty("channel_metadata", encode(requireNotNull(metadata).envelope().toByteArray(Charsets.UTF_8)))
            }
        }
        if (keys != null) put(channelStateKey(space, entry.channelId), bytes(JsonObject().apply {
            add("scope", entry.json()); add("channel_profile", entranceProfile.json())
            addProperty("crypto", encode(keys.save())); addProperty("joined", true); addProperty("content_cursor", 0)
        }))
        state.add("visibility_change", JsonObject().apply {
            addProperty("id", original.id); addProperty("visibility", visibility.name)
            addProperty("channel", entry.channelId); addProperty("listed", profile.listed); addProperty("status", ChangeStatus.QUEUED.name)
        })
        queue(space, state, original)
        original.id
    }

    fun visibilityChange(space: String): VisibilityChange? = store.transaction {
        owned()
        val change = state(space).getAsJsonObject("visibility_change") ?: return@transaction null
        VisibilityChange(change.text("id"), SpaceVisibility.valueOf(change.text("visibility")), change.text("channel"), changeStatus(space, change),
            change.get("listed")?.asBoolean)
    }

    private fun recordVisibilityResult(state: JsonObject, original: SignedSaitoMessage, status: ChangeStatus) {
        val change = state.getAsJsonObject("visibility_change") ?: return
        if (change.text("id") != original.id) return
        require(original.author == wallet.publicKey && change.text("channel") == original.body.text("channel_id"))
        require(change.text("visibility").lowercase(java.util.Locale.ROOT) == original.body.text("visibility"))
        change.addProperty("status", status.name)
    }

    fun setListed(space: String, listed: Boolean) = store.transaction {
        owned(); val state = state(space); val snapshot = journal(space).snapshot
        require(snapshot.owner == wallet.publicKey && !snapshot.dissolved && snapshot.visibility == SpaceVisibility.PUBLIC)
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
        require(snapshot.owner == wallet.publicKey && !snapshot.dissolved && snapshot.visibility == SpaceVisibility.PUBLIC)
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
        val communities = result.getAsJsonArray("items").map { readDirectoryCommunity(it.asJsonObject) }
        val next = result.number("next_cursor")
        val more = result.get("has_more").also { require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) }.asBoolean
        require(next >= cursor && (!more || next > cursor))
        store.transaction {
            owned(initialize = true)
            cacheDirectory(communities)
        }
        return PublicDirectoryPage(communities, next, more)
    }

    private suspend fun readDirectoryCommunity(value: JsonObject): PublicCommunity {
        val creation = value.text("creation")
        val receipt = SaitoEnvelope.decode(creation)
        val space = receipt.body.text("space_id")
        val journal = SpaceJournal(space, service)
        journal.receive(creation)
        val listingEnvelope = value.get("listing")?.asString ?: creation
        if (listingEnvelope != creation) {
            val listing = SaitoEnvelope.decode(listingEnvelope)
            require(listing.author == service && listing.body.text("module") == "Spaces" && listing.body.number("version") == 1L &&
                listing.body.text("request") == "receipt" && listing.body.text("stream") == "control" &&
                listing.body.text("space_id") == space)
            val position = listing.body.number("position").also { require(it > 0) }
            val command = SaitoEnvelope.decode(listing.body.text("event"))
            require(command.recipients == listOf(command.author, service).distinct())
            require(command.body.text("module") == "Spaces" && command.body.number("version") == 1L &&
                command.body.text("request") == "event" && command.body.text("kind") in setOf("set-listed", "set-visibility") &&
                command.body.text("space_id") == space && command.body.text("operation_id").matches(ID) &&
                command.body.text("control_id").matches(Regex("[0-9a-f]{128}")))
            if (command.body.text("kind") == "set-listed")
                command.body.get("listed").also { require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) }
            var retainedBytes = creation.toByteArray(Charsets.UTF_8).size.toLong() + listingEnvelope.toByteArray(Charsets.UTF_8).size
            // Ownership belongs to the verified control history, not permanently
            // to the original creator. Reading this proof never joins a Space.
            while (journal.snapshot.nextPosition < position) {
                val before = journal.snapshot.nextPosition
                val page = readPage(JsonObject().apply {
                    addProperty("space_id", space); addProperty("stream", "control"); addProperty("cursor", before)
                })
                val items = page.getAsJsonArray("items").also { require(it.size() <= 50) }
                var consumed = 0
                for (item in items) {
                    if (journal.snapshot.nextPosition == position) break
                    val control = item.asString
                    retainedBytes += control.toByteArray(Charsets.UTF_8).size
                    require(retainedBytes <= 16L * 1024 * 1024) { "Community listing history exceeds the preview capacity" }
                    journal.receive(control); consumed++
                }
                require(journal.snapshot.nextPosition > before)
                val next = page.number("next_cursor")
                require(next >= journal.snapshot.nextPosition)
                if (consumed == items.size()) require(next == journal.snapshot.nextPosition)
            }
            journal.receive(listingEnvelope)
        }
        val profile = requireNotNull(journal.snapshot.publicProfile)
        require(profile.visibility == SpaceVisibility.PUBLIC && profile.listed)
        return PublicCommunity(space, profile)
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

    fun invite(space: String, expiresAfterMillis: Long = 604_800_000): String =
        createInvitation(space, expiresAfterMillis, null)

    /** A signed on-node invitation that admits only the intended wallet. */
    fun inviteFor(space: String, recipient: String, expiresAfterMillis: Long = 604_800_000): String {
        require(supportsAddressedInvitations) { "This node has not confirmed addressed invitations" }
        require(SaitoWallet.isPublicKey(recipient))
        return createInvitation(space, expiresAfterMillis, recipient)
    }

    private fun createInvitation(space: String, expiresAfterMillis: Long, recipient: String?): String = store.transaction {
        owned()
        require(expiresAfterMillis in 60_000..31_536_000_000L)
        val state = state(space)
        val journal = journal(space)
        require(journal.snapshot.members[wallet.publicKey] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN))
        require(!state.has("control_pending")) { "A community change is awaiting confirmation" }
        val entry = requireNotNull(journal.snapshot.entryChannelId)
        val channel = journal.snapshot.channels.getValue(entry)
        require(journal.snapshot.protectedChat?.acceptsRecipient(recipient) != false)
        if (recipient != null) require(channel.visibility == SpaceVisibility.PRIVATE && recipient !in journal.snapshot.members)
        val scope = SpaceCrypto.Scope(service, space, entry, channel.generation)
        val bearer = SaitoWallet.create()
        val key = ByteArray(32).also(SecureRandom()::nextBytes)
        val operation = id()
        val expiry = Math.addExact(clock.millis(), expiresAfterMillis)
        val original = event(space, "invite", operation) {
            addProperty("control_id", journal.snapshot.controlId); addProperty("invite_key", bearer.publicKey)
            addProperty("expires_at", expiry)
            recipient?.let { addProperty("recipient", it) }
            val profile = state.getAsJsonObject("profile")?.let(SpaceProfile::fromJson) ?: SpaceProfile(state.text("name"))
            addProperty("preview_format", "profile-v1"); addProperty("channel_id", scope.channelId)
            if (journal.snapshot.channels[scope.channelId]?.visibility == SpaceVisibility.PUBLIC)
                addProperty("rules_token", profile.publicRulesToken)
            addProperty("preview", SpaceInvitation.encryptPreview(scope, key, operation, profile.json().toString()))
        }
        val link = SpaceInvitation(node, scope, original.id, bearer, key).link(journal.snapshot.protectedChat != null)
        queue(space, state, original)
        state.addProperty("invitation", link); state.addProperty("invitation_expires_at", expiry)
        put(stateKey(space), bytes(state))
        link
    }

    /** Inspecting a valid invitation generates no MLS device or membership. */
    suspend fun preview(link: String): InvitationPreview = loadPreview(link).let { preview ->
        val snapshot = preview.journal.snapshot
        val accepted = snapshot.invitations.getValue(preview.invitation.invitationId)
        InvitationPreview(preview.invitation.scope.spaceId, preview.profile.name, preview.profile,
            accepted.expiresAt, accepted.rulesToken, snapshot.protectedChat, snapshot.owner)
    }

    suspend fun join(link: String, acceptedRulesToken: String? = null, expectProtectedChat: Boolean? = null): String {
        val preview = loadPreview(link)
        if (expectProtectedChat != null) require((preview.journal.snapshot.protectedChat != null) == expectProtectedChat) {
            "This invitation is for a different kind of conversation"
        }
        val invitation = preview.invitation
        val scope = invitation.scope
        if (preview.journal.snapshot.channels[scope.channelId]?.visibility == SpaceVisibility.PUBLIC) {
            val opened = openPublic(scope.spaceId)
            if (!opened.joined) joinPublic(scope.spaceId, acceptedRulesToken)
            return scope.spaceId
        }
        val saved = store.transaction { owned(initialize = true); get(stateKey(scope.spaceId))?.let(::json) }
        if (saved != null) {
            val root = SpaceCrypto.Scope.fromJson(saved.getAsJsonObject("scope"))
            require(root.serviceKey == scope.serviceKey && root.spaceId == scope.spaceId)
            // Preserve the original device and its Welcome material. A fresh
            // invitation must never replace pending keys or unsent writing.
            store.transaction { preview.controls.forEach { accept(scope.spaceId, it) } }
            if (store.transaction { state(scope.spaceId).get("joined").asBoolean }) return scope.spaceId
        }
        require(preview.profile.houseRules.isEmpty() || acceptedRulesToken ==
            requireNotNull(preview.journal.snapshot.invitations[invitation.invitationId]).rulesToken) { "Accept the community house rules before joining" }
        require(wallet.publicKey !in preview.journal.snapshot.members) { "This identity is already a member; restore its community keys" }
        val savedChannel = saved?.let { store.transaction { channelState(scope.spaceId, scope.channelId) } }
        val previous = savedChannel?.get("crypto")?.asString?.let { SpaceCrypto.restore(decode(it)) }
        val device = when {
            previous == null -> SpaceCrypto.device(wallet.publicKey, id())
            previous.scope == null -> previous
            else -> savedChannel.get("joining_crypto")?.asString?.let { SpaceCrypto.restore(decode(it)) }
                ?: SpaceCrypto.device(wallet.publicKey, id())
        }
        val keyPackage = device.keyPackage(scope, wallet)
        val operation = id()
        val original = event(scope.spaceId, "join", operation) {
            if (preview.journal.snapshot.protectedChat != null) addProperty("protected_chat_version", 1)
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
            val target = if (saved == null) state else channelState(scope.spaceId, scope.channelId, state)
            target.addProperty("joined", false)
            if (previous?.scope != null) target.addProperty("joining_crypto", encode(device.save()))
            else target.addProperty("crypto", encode(device.save()))
            if (saved != null) putChannelState(scope.spaceId, scope.channelId, target)
            state.addProperty("selected_channel", scope.channelId)
            queue(scope.spaceId, state, original)
        }
        return scope.spaceId
    }

    /** The old link addresses the conversation only. The signed wallet read
     * and current journal grant determine authority, even after link expiry. */
    class DeviceReplacementUnsupported : IllegalArgumentException("This node does not support device replacement")
    class NoReplacementApprover : IllegalArgumentException("No other active device can approve this replacement")
    class ActiveDeviceAlreadyPresent : IllegalArgumentException("This device already has the conversation keys")

    suspend fun requestDeviceReplacement(link: String, expectProtectedChat: Boolean): String {
        val reference = SpaceInvitation.parse(link)
        if (reference.node != node) throw DifferentNode(reference.node)
        return requestDeviceReplacement(reference.scope, expectProtectedChat, reference)
    }

    suspend fun requestDeviceReplacement(scope: SpaceCrypto.Scope, expectProtectedChat: Boolean): String =
        requestDeviceReplacement(scope, expectProtectedChat, null)

    private suspend fun requestDeviceReplacement(requestedScope: SpaceCrypto.Scope, expectProtectedChat: Boolean,
        reference: SpaceInvitation?): String {
        require(requestedScope.serviceKey == service)
        val space = requestedScope.spaceId
        val saved = store.transaction { owned(initialize = true); get(stateKey(space)) != null }
        val authority = if (saved) {
            syncControls(space)
            store.transaction { journal(space) } to emptyList()
        } else loadSignedControls(space)
        if (!supportsDeviceReplacement) throw DeviceReplacementUnsupported()
        val verified = authority.first
        val snapshot = verified.snapshot
        val channelId = requestedScope.channelId
        val record = requireNotNull(snapshot.channels[channelId])
        require((snapshot.protectedChat != null) == expectProtectedChat && record.generation == requestedScope.generation)
        require(!snapshot.dissolved && record.visibility == SpaceVisibility.PRIVATE &&
            wallet.publicKey in snapshot.members && verified.canAccessChannel(wallet.publicKey, channelId))
        require(saved || snapshot.generalChannelId == channelId) { "Restore the original conversation device first" }
        val retiring = record.devices.keys.single { it.split(':')[1] == wallet.publicKey && it !in record.retiredDevices }
        if (record.devices.keys.none {
            it != retiring && it !in record.retiredDevices && verified.canApproveDeviceRequests(it.split(':')[1])
        }) throw NoReplacementApprover()
        store.transaction {
            owned()
            val root = snapshot.generalChannelId ?: error("Original conversation scope is missing")
            val current = if (saved) state(space) else {
                require(get(stateKey(space)) == null)
                authority.second.forEach { receipt ->
                    val position = SaitoEnvelope.decode(receipt).body.number("position")
                    put("$space/control/${positionKey(position)}", receipt.toByteArray(Charsets.UTF_8))
                }
                val scope = SpaceCrypto.Scope(service, space, root, snapshot.channels.getValue(root).generation)
                JsonObject().apply {
                    add("scope", scope.json())
                    addProperty("name", snapshot.publicProfile?.name ?: reference?.let { recoveredInvitationName(it, authority.second) }
                        ?: if (expectProtectedChat) "Protected chat" else "Recovered community")
                    snapshot.publicProfile?.let { add("profile", it.json()) }
                    addProperty("joined", false); addProperty("next_outbox", 0); addProperty("content_cursor", 0)
                    addProperty("role", snapshot.members.getValue(wallet.publicKey).name)
                    put(stateKey(space), bytes(this))
                }
            }
            require(journal(space).snapshot.controlId == snapshot.controlId)
            val target = channelState(space, channelId, current)
            if (target.has("replacement_request")) return@transaction
            if (target.get("joined")?.asBoolean == true && target.has("crypto")) throw ActiveDeviceAlreadyPresent()
            val fresh = SpaceCrypto.device(wallet.publicKey, id())
            val keyPackage = fresh.keyPackage(requestedScope, wallet)
            val original = event(space, "replace-device") {
                if (snapshot.protectedChat != null) addProperty("protected_chat_version", 1)
                addProperty("control_id", snapshot.controlId); addProperty("channel_id", channelId)
                addProperty("replace_device", retiring)
                add("key_package", JsonObject().apply {
                    addProperty("wallet", wallet.publicKey); addProperty("device", keyPackage.deviceId)
                    addProperty("package", keyPackage.packageBytes); addProperty("signer", keyPackage.signer)
                    addProperty("signature", keyPackage.walletSignature)
                })
            }
            target.addProperty("joining_crypto", encode(fresh.save()))
            target.addProperty("replacement_request", original.id)
            target.remove("replacement_rejected")
            target.addProperty("joined", false)
            current.addProperty("selected_channel", channelId)
            if (channelId != root) putChannelState(space, channelId, target)
            queue(space, current, original)
        }
        return space
    }

    private fun recoveredInvitationName(reference: SpaceInvitation, controls: List<String>): String? {
        for (receipt in controls) {
            val original = SaitoEnvelope.decode(SaitoEnvelope.decode(receipt).body.text("event"))
            if (original.id != reference.invitationId) continue
            return runCatching {
                require(original.body.text("kind") == "invite" && original.body.text("channel_id") == reference.scope.channelId)
                val value = reference.preview(original.body.text("preview"), original.body.text("operation_id"))
                if (original.body.get("preview_format")?.asString == "profile-v1")
                    SpaceProfile.fromJson(json(value.toByteArray(Charsets.UTF_8))).name else SpaceProfile(value).name
            }.getOrNull()
        }
        return null
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
                    if (!journal.pendingJoinIsCurrent(original, clock.millis())) remove(row.key) else return DeferredJoin(row.key, receipt)
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
        if (!store.transaction { journal(space).canApproveDeviceRequests(wallet.publicKey) }) return
        if (store.transaction { page("$space/pending/", limit = 1).isNotEmpty() }) return
        syncControls(space)
        if (store.transaction { journal(space).snapshot.dissolved }) return
        if (store.transaction { enqueueAccessRotation(space, state(space)) }) return
        if (!store.transaction { journal(space).canApproveDeviceRequests(wallet.publicKey) }) return
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
                if (join.replacedDevice != null && join.actor == wallet.publicKey) {
                    if (deferred != null) remove(deferredKey)
                    else current.addProperty("join_cursor", Math.addExact(position, 1))
                    put(stateKey(space), bytes(current))
                    return@transaction true
                }
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
                val admission = crypto.admit(join.keyPackage, join.replacedDevice)
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
        if (invitation.node != node) throw DifferentNode(invitation.node)
        require(invitation.scope.serviceKey == service)
        val (journal, controls) = loadSignedControls(invitation.scope.spaceId, invitation)
        val authorized = requireNotNull(journal.snapshot.invitations[invitation.invitationId])
        require(authorized.expiresAt > clock.millis() && authorized.accepts(wallet.publicKey) &&
            invitation.scope.channelId in journal.snapshot.channels)
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

    private suspend fun loadSignedControls(space: String, invitation: SpaceInvitation? = null): Pair<SpaceJournal, List<String>> {
        val journal = SpaceJournal(space, service)
        val controls = mutableListOf<String>()
        var retainedBytes = 0L
        while (true) {
            val before = journal.snapshot.nextPosition
            val page = readPage(JsonObject().apply {
                addProperty("space_id", space); addProperty("stream", if (invitation == null) "control" else "invitation")
                invitation?.let { addProperty("invite_id", it.invitationId) }
                addProperty("cursor", before)
            }, invitation)
            for (value in page.getAsJsonArray("items")) {
                val receipt = value.asString
                retainedBytes += receipt.toByteArray(Charsets.UTF_8).size
                require(retainedBytes <= 16L * 1024 * 1024) { "Control history exceeds this client's capacity" }
                journal.receive(receipt); controls += receipt
            }
            require(journal.snapshot.nextPosition == page.number("next_cursor"))
            if (journal.snapshot.nextPosition == before) break
        }
        return journal to controls
    }

    fun create(name: String): String = create(SpaceProfile(name))
    fun create(profile: SpaceProfile, channels: List<SpaceChannelProfile> = listOf(SpaceChannelProfile("general"))): String =
        createSpace(profile, channels, null)

    /** Require a signed capability before creating a private Chat conversation. */
    class ProtectedChatUnsupported : IllegalArgumentException("This node does not support protected conversations")

    suspend fun createProtectedChat(name: String, directPeer: String? = null, legacyId: String? = null): String {
        require(directPeer != wallet.publicKey)
        val chat = ProtectedChat(directPeer)
        require(legacyId == null || SaitoWallet.isPublicKey(legacyId) && (directPeer == null || legacyId == directPeer))
        val legacyChat = legacyId?.let { if (directPeer == null) "group:$it" else "direct:$it" }
        if (legacyChat != null) {
            communities().firstOrNull { it.legacyChat == legacyChat && !it.dissolved }?.let { return it.id }
        }
        readPage(JsonObject().apply {
            addProperty("space_id", "0".repeat(64)); addProperty("stream", "directory")
            addProperty("cursor", 0); addProperty("query", "")
        })
        if (!supportsProtectedChat || !supportsAddressedInvitations) throw ProtectedChatUnsupported()
        return createSpace(SpaceProfile(name, visibility = SpaceVisibility.PRIVATE),
            listOf(SpaceChannelProfile("messages")), chat, legacyChat)
    }

    private fun createSpace(profile: SpaceProfile, channels: List<SpaceChannelProfile>, protectedChat: ProtectedChat?,
        legacyChat: String? = null): String {
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
        val operation = id()
        val rootRecord = SpaceChannelRecord(0, crypto.bindings, scope.generation, visibility = profile.visibility,
            profile = first.takeIf { profile.visibility == SpaceVisibility.PUBLIC }, metadataChannelId = scope.channelId)
        val genesis = SpaceSnapshot(owner = wallet.publicKey, members = mapOf(wallet.publicKey to SpaceRole.OWNER),
            controlId = "0".repeat(128), channels = mapOf(scope.channelId to rootRecord), visibility = profile.visibility, generalChannelId = scope.channelId)
        val assignment = store.transaction {
            categoryAssignment(scope.spaceId, JsonObject(), genesis, scope.channelId, rootRecord, first, operation, overrides = mapOf(scope.channelId to crypto))
        }
        val original = event(scope.spaceId, "create", operation) {
            assignment.entrySet().forEach { (key, value) -> add(key, value) }
            protectedChat?.let { chat ->
                add("protected_chat", JsonObject().apply { chat.directPeer?.let { addProperty("direct_peer", it) } })
            }
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
                legacyChat?.let { addProperty("legacy_chat", it) }
                protectedChat?.let { chat ->
                    add("new_protected_chat", JsonObject().apply { chat.directPeer?.let { addProperty("direct_peer", it) } })
                }
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
        val required = !journal.snapshot.dissolved && journal.snapshot.channels.values.any { it.retiredDevices.isNotEmpty() }
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
    fun send(space: String, text: String, channel: String? = null): String =
        publishRoot(space, text, channel, "message")

    fun publishArticle(space: String, article: SpaceArticleContent.Original, channel: String? = null,
        reviewedDraft: SpaceArticleDraft? = null): String {
        require(reviewedDraft == null || reviewedDraft.content() == article)
        return publishRoot(space, article.encode(), channel, "article", reviewedDraft)
    }

    fun shareStackArticle(space: String, backend: SaitoBackend, signedEnvelope: String, channel: String? = null): String {
        val article = SaitoStack.decode(signedEnvelope)
        val reference = SpaceArticleContent.PublicStack(StackLink(backend, article.author, article.revision))
        return publishRoot(space, reference.encode(), channel, "article")
    }

    fun publishFile(space: String, channel: String, file: SpaceFileDescriptor, stage: SpaceFileStage,
        retryRejected: Boolean = false): String {
        stage.requireObject(file.sealed)
        return publishRoot(space, file.encode(), channel, "file", fileStage = stage, retryRejectedFile = retryRejected)
    }

    private fun publishRoot(space: String, text: String, channel: String?, contentKind: String,
        reviewedDraft: SpaceArticleDraft? = null, fileStage: SpaceFileStage? = null,
        retryRejectedFile: Boolean = false): String = store.transaction {
        owned()
        val state = state(space)
        require(state.get("joined").asBoolean && !state.has("control_pending") && !state.has("required_control")) { "Community admission or key change is not confirmed" }
        val journal = journal(space)
        val data = channelState(space, channel ?: selectedChannel(state), state)
        val scope = SpaceCrypto.Scope.fromJson(data.getAsJsonObject("scope"))
        val record = requireNotNull(journal.snapshot.channels[scope.channelId])
        require(record.rootContentKind == contentKind || contentKind == "message" && record.rootContentKind == "file") {
            "This content belongs to a different channel type"
        }
        if (contentKind == "article") require(supportsSpaceArticles || data.get("articles_supported")?.asBoolean == true) {
            "The authenticated node does not support Space articles"
        }
        require(contentKind != "article" || get(articleReadDeniedKey(space, scope.channelId)) == null) { "Article channel access is unavailable" }
        require(journal.canContributeChannel(wallet.publicKey, scope.channelId)) { "Private access is still being prepared" }
        require(journal.writeRestrictedUntil(wallet.publicKey, scope.channelId, clock.millis()) == null) { "Writing is temporarily restricted" }
        require(!record.archived) { "This channel is archived" }
        if (record.adminPostsOnly || record.profile?.kind == SpaceChannelKind.ANNOUNCEMENT)
            require(journal.snapshot.members[wallet.publicKey] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN))
        val control = requireNotNull(journal.snapshot.controlId)
        val fileIntent = fileStage?.let {
            val lease = requireNotNull(journal.snapshot.fileStorage)
            require(supportsFileStorage && it.space == space && clock.millis() < lease.offer.expiresAt)
            val verified = SpaceFileStage.read(it.envelope, service, lease.offer.provider)
            "$space/channels/${scope.channelId}/file-intents/${verified.objectId}"
        }
        fileIntent?.let { get(it)?.toString(Charsets.UTF_8)?.let { original ->
            if (!retryRejectedFile || get("$space/rejected/$original") == null) return@transaction original
        } }
        val original = if (record.visibility == SpaceVisibility.PUBLIC) {
            require(text.isNotBlank() && text.toByteArray(Charsets.UTF_8).size <= 64 * 1024)
            event(space, "public-content") {
                addProperty("channel_id", scope.channelId); addProperty("control_id", control)
                addProperty("epoch", record.epoch); addProperty("key_generation", record.generation)
                addProperty("content_kind", contentKind); addProperty("text", text)
                fileStage?.let { addProperty("storage_stage", it.envelope) }
            }
        } else {
            require(data.get("joined")?.asBoolean == true && record.retiredDevices.isEmpty()) { "Current channel keys are not available on this device" }
            val keys = SpaceCrypto.restore(decode(data.text("crypto")))
            require(keys.scope == scope && keys.epoch == record.epoch && keys.bindings == record.devices)
            if (fileStage == null) keys.publish(wallet, contentKind, id(), control, text, clock.millis())
            else keys.publishFile(wallet, id(), control, text, clock.millis(), fileStage)
        }
        queue(space, state, original)
        fileIntent?.let { put(it, original.id.toByteArray(Charsets.UTF_8)) }
        if (contentKind == "article") {
            if (reviewedDraft != null) consumeArticleDraft(space, scope.channelId, null, reviewedDraft)
        } else {
            val draft = draftKey(space, scope.channelId)
            if (get(draft)?.toString(Charsets.UTF_8) == text) remove(draft)
        }
        original.id
    }

    private fun articleDraftKey(space: String, channel: String, article: String?): String {
        require(space.matches(ID) && channel.matches(ID) && (article == null || article.matches(Regex("[0-9a-f]{128}"))))
        return "$space/channels/$channel/article-drafts/${article ?: "new"}"
    }

    private fun SpaceClientStore.Transaction.consumeArticleDraft(space: String, channel: String, article: String?,
        reviewed: SpaceArticleDraft) {
        val key = articleDraftKey(space, channel, article)
        if (get(key)?.contentEquals(reviewed.bytes()) == true) remove(key)
    }

    fun editMessage(space: String, message: Message, text: String, channel: String? = null): String {
        require(!message.isArticle)
        return replaceContent(space, message, text, channel)
    }

    fun editArticle(space: String, articleId: String, article: SpaceArticleContent.Original,
        channel: String? = null, reviewedDraft: SpaceArticleDraft? = null): String = store.project {
        val target = channel ?: store.transaction { selectedChannel(state(space)) }
        val original = requireNotNull(message(space, articleId, target))
        require(original.asArticle()?.content is SpaceArticleContent.Original)
        require(original.change?.let { it.rejected || it.cancelled } != false) { "An edit is already pending" }
        require(reviewedDraft == null || reviewedDraft.content() == article)
        replaceContent(space, original, article.encode(), target, reviewedDraft)
    }

    private fun replaceContent(space: String, message: Message, text: String, channel: String?,
        reviewedDraft: SpaceArticleDraft? = null): String = store.transaction {
        owned()
        val state = state(space)
        val targetChannel = channel ?: selectedChannel(state)
        val original = acceptedMessage(space, targetChannel, message)
        require(original.author == wallet.publicKey)
        val thread = messageThread(original.body)
        val available = communityFromStore(space, targetChannel)
        require(if (thread == null) available.canSend else available.canReply) { "Writing is not currently available" }
        val position = requireNotNull(message.position)
        val journal = journal(space)
        require(messageDeletion(space, targetChannel, original.id, original.author) == null) { "This message has been removed" }
        require(text.isNotBlank() && text.toByteArray(Charsets.UTF_8).size <= 64 * 1024)
        val accepted = journal.snapshot.channels.getValue(targetChannel)
        val control = requireNotNull(journal.snapshot.controlId)
        val replacement = if (accepted.visibility == SpaceVisibility.PUBLIC) event(space, "public-content") {
            addProperty("channel_id", targetChannel); addProperty("control_id", requireNotNull(journal.snapshot.controlId))
            addProperty("epoch", accepted.epoch); addProperty("key_generation", accepted.generation)
            addProperty("content_kind", "message-edit"); addProperty("text", text)
            addProperty("target_message_id", original.id); addProperty("target_position", position)
            thread?.let { addProperty("thread_root_id", it) }
        } else {
            val data = channelState(space, targetChannel, state)
            val scope = SpaceCrypto.Scope.fromJson(data.getAsJsonObject("scope"))
            val keys = SpaceCrypto.restore(decode(data.text("crypto")))
            require(keys.scope == scope && keys.epoch == accepted.epoch && keys.bindings == accepted.devices)
            keys.publishMessageEdit(wallet, id(), control, text, clock.millis(), original.id, position, thread)
        }
        queue(space, state, replacement)
        if (reviewedDraft != null) consumeArticleDraft(space, targetChannel, original.id, reviewedDraft)
        replacement.id
    }

    private fun SpaceClientStore.Transaction.acceptedMessage(space: String, channel: String, message: Message): SignedSaitoMessage {
        require(!message.pending && !message.rejected && message.position != null)
        val original = SaitoEnvelope.decode(originalEnvelope(requireNotNull(get(contentPrefix(space, channel) + positionKey(message.position)))))
        require(original.id == message.id && original.body.text("space_id") == space && original.body.text("channel_id") == channel &&
            original.body.text("content_kind") in TIMELINE_CONTENT_KINDS)
        return original
    }
    private fun SpaceClientStore.Transaction.threadParent(space: String, channel: String, parent: Message): SignedSaitoMessage =
        acceptedMessage(space, channel, parent).also { require(it.body.text("content_kind") == "message") }

    fun reply(space: String, parent: Message, text: String, channel: String? = null): String = store.transaction {
        owned()
        val state = state(space); val target = channel ?: selectedChannel(state)
        require(communityFromStore(space, target).canReply) { "Writing is not currently available" }
        val root = threadParent(space, target, parent)
        require(text.isNotBlank() && text.toByteArray(Charsets.UTF_8).size <= 64 * 1024)
        val journal = journal(space); val accepted = journal.snapshot.channels.getValue(target)
        val control = requireNotNull(journal.snapshot.controlId)
        val reply = if (accepted.visibility == SpaceVisibility.PUBLIC) event(space, "public-content") {
            addProperty("channel_id", target); addProperty("control_id", control)
            addProperty("epoch", accepted.epoch); addProperty("key_generation", accepted.generation)
            addProperty("content_kind", "thread-reply"); addProperty("text", text)
            addProperty("target_message_id", root.id); addProperty("target_position", parent.position)
        } else {
            val data = channelState(space, target, state)
            val scope = SpaceCrypto.Scope.fromJson(data.getAsJsonObject("scope"))
            val keys = SpaceCrypto.restore(decode(data.text("crypto")))
            require(keys.scope == scope && keys.epoch == accepted.epoch && keys.bindings == accepted.devices)
            keys.publishThreadReply(wallet, id(), control, text, clock.millis(), root.id, requireNotNull(parent.position))
        }
        queue(space, state, reply)
        val draft = threadDraftKey(space, target, root.id)
        if (get(draft)?.toString(Charsets.UTF_8) == text) remove(draft)
        reply.id
    }

    suspend fun refreshThread(space: String, parent: Message, channel: String? = null, from: Long? = null) {
        require(from == null || from >= 0)
        val target = store.transaction { owned(); val target = channel ?: selectedChannel(state(space)); threadParent(space, target, parent); target }
        syncControls(space)
        if (store.transaction { journal(space).snapshot.channels.getValue(target).visibility } != SpaceVisibility.PUBLIC)
            refreshKeys(space, target)
        val latest = messagePage(space, target, thread = parent.id)
        val readingFrom = from ?: threadReadingAnchor(space, parent, target)?.windowFrom
        if (readingFrom != null && readingFrom < latest.start) {
            var cursor: Long = readingFrom
            var remaining = 50
            while (remaining > 0) {
                val observed = messagePage(space, target, cursor, before = false, limit = remaining, thread = parent.id)
                remaining -= observed.count
                if (!observed.more || observed.count == 0) break
                require(observed.next > cursor)
                cursor = observed.next
            }
        }
        store.transaction {
            val state = conversationState(space, target, parent.id)
            state.addProperty("content_loaded", true); putConversationState(space, target, parent.id, state)
        }
    }

    fun thread(space: String, parent: Message, from: Long? = null, limit: Int = 50, channel: String? = null): List<Message> {
        val target = store.transaction { owned(); val target = channel ?: selectedChannel(state(space)); threadParent(space, target, parent); target }
        return conversationTimeline(space, from, limit, target, parent.id)
    }

    fun deleteMessage(space: String, message: Message, channel: String? = null): String = store.transaction {
        owned()
        val state = state(space)
        val target = channel ?: selectedChannel(state)
        val original = acceptedMessage(space, target, message)
        val thread = messageThread(original.body)
        val available = communityFromStore(space, target)
        require(if (thread == null) available.canSend else available.canReply) { "Writing is not currently available" }
        val position = requireNotNull(message.position)
        val journal = journal(space)
        require(original.author == wallet.publicKey || journal.snapshot.members[wallet.publicKey] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN))
        require(messageDeletion(space, target, original.id, original.author) == null) { "This message has already been removed" }
        val accepted = journal.snapshot.channels.getValue(target)
        val control = requireNotNull(journal.snapshot.controlId)
        val deletion = if (accepted.visibility == SpaceVisibility.PUBLIC) event(space, "public-content") {
            addProperty("channel_id", target); addProperty("control_id", control)
            addProperty("epoch", accepted.epoch); addProperty("key_generation", accepted.generation)
            addProperty("content_kind", "message-delete"); addProperty("text", "deleted")
            addProperty("target_message_id", original.id); addProperty("target_position", position)
            thread?.let { addProperty("thread_root_id", it) }
        } else {
            val data = channelState(space, target, state)
            val scope = SpaceCrypto.Scope.fromJson(data.getAsJsonObject("scope"))
            val keys = SpaceCrypto.restore(decode(data.text("crypto")))
            require(keys.scope == scope && keys.epoch == accepted.epoch && keys.bindings == accepted.devices)
            keys.publishMessageDeletion(wallet, id(), control, clock.millis(), original.id, position, thread)
        }
        queue(space, state, deletion)
        deletion.id
    }

    /** Staff moderation retains the exact reviewed target and current authority term. */
    fun pinMessage(space: String, message: Message, pinned: Boolean, channel: String? = null): String = store.transaction {
        owned(); val state = state(space); val target = channel ?: selectedChannel(state)
        require(communityFromStore(space, target).canPin)
        ensurePinProjection(space, target)
        require(!messagePin(space, target, message.id).pending) { "A pin change is already waiting for this message" }
        val original = acceptedMessage(space, target, message)
        require(original.body.text("content_kind") != "article")
        require(messageDeletion(space, target, original.id, original.author) == null)
        val journal = journal(space); val accepted = journal.snapshot.channels.getValue(target)
        val control = requireNotNull(journal.snapshot.controlId)
        val thread = messageThread(original.body)
        val candidate = if (accepted.visibility == SpaceVisibility.PUBLIC) event(space, "public-content") {
            addProperty("channel_id", target); addProperty("control_id", control)
            addProperty("epoch", accepted.epoch); addProperty("key_generation", accepted.generation)
            addProperty("content_kind", "message-pin"); addProperty("text", if (pinned) "pinned" else "unpinned")
            addProperty("target_message_id", original.id); addProperty("target_position", requireNotNull(message.position))
            thread?.let { addProperty("thread_root_id", it) }
        } else {
            val data = channelState(space, target, state)
            val keys = SpaceCrypto.restore(decode(data.text("crypto")))
            require(keys.scope == SpaceCrypto.Scope.fromJson(data.getAsJsonObject("scope")) && keys.epoch == accepted.epoch && keys.bindings == accepted.devices)
            keys.publishMessagePin(wallet, id(), control, pinned, clock.millis(), original.id, requireNotNull(message.position), thread)
        }
        queue(space, state, candidate); candidate.id
    }

    data class PinnedMessage(val message: Message, val parent: Message? = null, val threadId: String? = null)
    data class PinnedPage(val items: List<PinnedMessage>, val nextMessage: String?, val complete: Boolean)
    fun pinnedMessages(space: String, afterMessage: String? = null, channel: String? = null): PinnedPage = store.project { store.transaction {
        owned(); val target = channel ?: selectedChannel(state(space))
        afterMessage?.let { require(it.matches(Regex("[0-9a-f]{128}"))) }
        ensurePinProjection(space, target); retryUnavailablePins(space, target)
        val prefix = activePinsPrefix(space, target)
        val rows = page(prefix, if (afterMessage == null) prefix else prefix + afterMessage + '\u0000', 51)
        val messages = rows.take(50).mapNotNull { message(space, it.key.removePrefix(prefix), target) }
        val references = messages.filter { (it.pinned || it.pinPending) && !it.deleted }.map { value ->
            val pointer = requireNotNull(get(messageIdKey(space, target, value.id)))
            val receipt = json(requireNotNull(get(pointer.toString(Charsets.UTF_8))))
            val original = SaitoEnvelope.decode(receipt.text("event"))
            val parentId = messageThread(original.body)
            PinnedMessage(value, parentId?.let { message(space, it, target) }, parentId)
        }
        PinnedPage(references,
            rows.take(50).lastOrNull()?.key?.removePrefix(prefix)?.takeIf { rows.size > 50 },
            pinProjectionComplete(space, target) && messages.filterNot { it.deleted }.all { it.pinAvailable && !it.missingKeys } &&
                references.all { it.threadId == null || it.parent?.missingKeys == false })
    } }
    private data class PinProjection(val pinned: Boolean, val pending: Boolean, val available: Boolean)
    private fun SpaceClientStore.Transaction.pinReader(space: String, channel: String): (String) -> String? {
        val readPrivate = protectedHistoryReader(space, channel)
        return { envelope -> try {
            val signed = SaitoEnvelope.decode(envelope)
            if (signed.body.text("kind") == "public-content") signed.body.text("text") else readPrivate(envelope, signed)
        } catch (_: Exception) { null } }
    }
    private fun SpaceClientStore.Transaction.messagePin(space: String, channel: String, message: String,
        reader: ((String) -> String?)? = null): PinProjection {
        val accepted = get(messagePinKey(space, channel, message))?.let(::json)
        val read = reader ?: pinReader(space, channel)
        val known = accepted?.let { receipt ->
            val key = pinChoiceKey(space, channel, message)
            val cached = get(key)?.let(::json)
            if (cached?.number("position") == receipt.number("position")) cached.get("pinned").asBoolean else {
                val text = read(receipt.text("event"))
                if (text !in setOf("pinned", "unpinned")) null else (text == "pinned").also { pinned ->
                    put(key, bytes(JsonObject().apply { addProperty("position", receipt.number("position")); addProperty("pinned", pinned) }))
                }
            }
        }
        val pending = get(pinPendingKey(space, channel, message))?.let { get(it.toString(Charsets.UTF_8)) }?.let(::json)
            ?.takeUnless { it.has("disposition") || it.get("cancelled")?.asBoolean == true }
        if (pending != null) {
            val text = read(SaitoTransaction.restore(pending.text("original")).envelope())
            return PinProjection(text == "pinned", true, text in setOf("pinned", "unpinned"))
        }
        return PinProjection(known == true, false, accepted == null || known != null)
    }
    private fun SpaceClientStore.Transaction.reconcilePinTarget(space: String, channel: String, message: String,
        reader: ((String) -> String?)? = null) {
        val active = activePinsPrefix(space, channel) + message
        val unavailable = unavailablePinsPrefix(space, channel) + message
        val pointer = get(messageIdKey(space, channel, message)) ?: return
        val base = get(pointer.toString(Charsets.UTF_8))?.let(::json) ?: return
        val author = SaitoEnvelope.decode(base.text("event")).author
        if (messageDeletion(space, channel, message, author) != null) { remove(active); remove(unavailable); return }
        val choice = messagePin(space, channel, message, reader)
        if (!choice.available) { remove(active); put(unavailable, byteArrayOf(1)); return }
        remove(unavailable)
        // Keep an unpinning reference visible with its pending state until the
        // exact original is accepted, rather than implying a completed removal.
        if (choice.pinned || choice.pending) put(active, byteArrayOf(1)) else remove(active)
    }
    private fun SpaceClientStore.Transaction.ensurePinProjection(space: String, channel: String) {
        val data = channelState(space, channel)
        if (data.get("pin_projection_version")?.asLong == 1L) return
        val prefix = pinTargetsPrefix(space, channel)
        val after = data.get("pin_projection_after")?.asString ?: prefix
        val rows = page(prefix, after, 50); val reader = pinReader(space, channel)
        for (row in rows) reconcilePinTarget(space, channel, row.key.removePrefix(prefix), reader)
        if (rows.size < 50) { data.addProperty("pin_projection_version", 1); data.remove("pin_projection_after") }
        else data.addProperty("pin_projection_after", rows.last().key + '\u0000')
        putChannelState(space, channel, data)
    }
    private fun SpaceClientStore.Transaction.retryUnavailablePins(space: String, channel: String) {
        val prefix = unavailablePinsPrefix(space, channel)
        val key = "$space/channels/$channel/pin-repair-after"
        val after = get(key)?.toString(Charsets.UTF_8)?.takeIf { it.startsWith(prefix) } ?: prefix
        var rows = page(prefix, after, 16)
        if (rows.isEmpty() && after != prefix) rows = page(prefix, limit = 16)
        if (rows.isEmpty()) { remove(key); return }
        val reader = pinReader(space, channel)
        for (row in rows) reconcilePinTarget(space, channel, row.key.removePrefix(prefix), reader)
        put(key, (rows.last().key + '\u0000').toByteArray(Charsets.UTF_8))
    }
    private fun SpaceClientStore.Transaction.pinProjectionComplete(space: String, channel: String): Boolean {
        val data = channelState(space, channel)
        return data.get("pins_loaded")?.asBoolean == true && data.get("pin_projection_version")?.asLong == 1L &&
            (data.get("pin_loaded")?.asLong ?: 0) >= (data.get("pin_goal")?.asLong ?: 0) && page(unavailablePinsPrefix(space, channel), limit = 1).isEmpty()
    }
    data class PinStatus(val supported: Boolean, val hasPins: Boolean?, val complete: Boolean)
    fun pinStatus(space: String, channel: String? = null): PinStatus = store.project { store.transaction {
        owned(); val target = channel ?: selectedChannel(state(space))
        ensurePinProjection(space, target); retryUnavailablePins(space, target)
        val data = channelState(space, target)
        val supported = supportsMessagePins || data.get("pins_supported")?.asBoolean == true
        val complete = pinProjectionComplete(space, target)
        val any = page(activePinsPrefix(space, target), limit = 1).isNotEmpty()
        PinStatus(supported, if (any) true else if (complete) false else null, complete)
    } }

    /** The exact desired author selection is signed before transport, including rapid changes. */
    fun setReaction(space: String, message: Message, emoji: String, selected: Boolean, channel: String? = null): String? = store.transaction {
        owned()
        val state = state(space); val target = channel ?: selectedChannel(state)
        require(communityFromStore(space, target).canReply)
        val data = channelState(space, target, state)
        require(supportsMessageReactions || data.get("reactions_supported")?.asBoolean == true)
        val original = acceptedMessage(space, target, message)
        require(original.body.text("content_kind") != "article")
        require(messageDeletion(space, target, message.id, original.author) == null)
        get(reactionActorsPrefix(space, target, message.id) + wallet.publicKey)?.let { reconcileReaction(space, target, json(it)) }
        require(get(reactionInvalidPrefix(space, target, message.id) + wallet.publicKey) == null) { "Earlier reaction choices are not available yet" }
        SpaceReactionSelection.encode(setOf(emoji))
        require(ownReactionAvailable(space, target, message.id)) { "Earlier reaction choices are not available yet" }
        val current = reactionProjection(space, target, message.id).own
        val desired = if (selected) current + emoji else current - emoji
        if (desired == current) return@transaction null
        val text = SpaceReactionSelection.encode(desired)
        val journal = journal(space); val accepted = journal.snapshot.channels.getValue(target)
        val thread = if (original.body.text("content_kind") == "thread-reply") original.body.text("target_message_id") else null
        val control = requireNotNull(journal.snapshot.controlId)
        val reaction = if (accepted.visibility == SpaceVisibility.PUBLIC) event(space, "public-content") {
            addProperty("channel_id", target); addProperty("control_id", control)
            addProperty("epoch", accepted.epoch); addProperty("key_generation", accepted.generation)
            addProperty("content_kind", "message-reaction"); addProperty("text", text)
            addProperty("target_message_id", original.id); addProperty("target_position", message.position)
            thread?.let { addProperty("thread_root_id", it) }
        } else {
            val keys = SpaceCrypto.restore(decode(data.text("crypto")))
            require(keys.scope == SpaceCrypto.Scope.fromJson(data.getAsJsonObject("scope")) && keys.epoch == accepted.epoch && keys.bindings == accepted.devices)
            keys.publishMessageReaction(wallet, id(), control, text, clock.millis(), original.id, requireNotNull(message.position), thread)
        }
        queue(space, state, reaction)
        reaction.id
    }

    private data class ReactionProjection(val values: List<Reaction>, val own: Set<String>, val complete: Boolean, val more: Boolean = false, val nextEmoji: String? = null)
    private fun SpaceClientStore.Transaction.reactionDecoder(space: String, channel: String): (String) -> Set<String>? {
        val readPrivate = protectedHistoryReader(space, channel)
        return { envelope ->
            try {
                val signed = SaitoEnvelope.decode(envelope)
                val text = if (signed.body.text("kind") == "public-content") signed.body.text("text") else readPrivate(envelope, signed)
                SpaceReactionSelection.decode(text)
            } catch (_: Exception) { null }
        }
    }
    private fun SpaceClientStore.Transaction.readReaction(space: String, channel: String, envelope: String): Set<String>? =
        reactionDecoder(space, channel)(envelope)

    /** Key grants may arrive after the accepted actor state. Repair retained
     * originals locally, with fair bounded work and no replacement operation. */
    private fun SpaceClientStore.Transaction.retryUnavailableReactions(space: String, channel: String) {
        val prefix = "$space/channels/$channel/reaction-invalid/"
        val cursorKey = "$space/channels/$channel/reaction-repair-after"
        val cursor = get(cursorKey)?.toString(Charsets.UTF_8)?.takeIf { it.startsWith(prefix) } ?: prefix
        var rows = page(prefix, cursor, 16)
        if (rows.isEmpty() && cursor != prefix) rows = page(prefix, limit = 16)
        if (rows.isEmpty()) { remove(cursorKey); return }
        val decoder = reactionDecoder(space, channel)
        for (row in rows) {
            val parts = row.key.removePrefix(prefix).split('/')
            require(parts.size == 2)
            val original = get(reactionActorsPrefix(space, channel, parts[0]) + parts[1])
            if (original == null) remove(row.key) else reconcileReaction(space, channel, json(original), decoder)
        }
        put(cursorKey, (rows.last().key + '\u0000').toByteArray(Charsets.UTF_8))
    }

    /** Materialize each accepted author revision once in the encrypted store.
     * Rendering reads counts and the current author's choice, never every actor. */
    private fun SpaceClientStore.Transaction.reconcileReaction(space: String, channel: String, receipt: JsonObject, reader: ((String) -> Set<String>?)? = null) {
        val incoming = SaitoEnvelope.decode(receipt.text("event"))
        val message = incoming.body.text("target_message_id")
        val key = reactionActorsPrefix(space, channel, message) + incoming.author
        val prior = get(key)?.let(::json)
        val latest = if (prior == null || prior.number("position") < receipt.number("position")) receipt.also { put(key, bytes(it)) } else prior
        val appliedKey = reactionAppliedPrefix(space, channel, message) + incoming.author
        val applied = get(appliedKey)?.let(::json)
        if (applied != null && applied.number("position") >= latest.number("position")) return
        val invalidKey = reactionInvalidPrefix(space, channel, message) + incoming.author
        val choices = if (reader == null) readReaction(space, channel, latest.text("event")) else reader(latest.text("event"))
        if (choices == null) { put(invalidKey, byteArrayOf(1)); return }
        val previous = applied?.text("selection")?.let(SpaceReactionSelection::decode).orEmpty()
        for (emoji in previous + choices) {
            val delta = (if (emoji in choices) 1 else 0) - (if (emoji in previous) 1 else 0)
            if (delta == 0) continue
            val countKey = reactionCountsPrefix(space, channel, message) + Base64.getUrlEncoder().withoutPadding().encodeToString(emoji.toByteArray(Charsets.UTF_8))
            val count = Math.addExact(get(countKey)?.let(::json)?.number("count") ?: 0, delta.toLong())
            require(count >= 0)
            if (count == 0L) remove(countKey) else put(countKey, bytes(JsonObject().apply { addProperty("emoji", emoji); addProperty("count", count) }))
        }
        put(appliedKey, bytes(JsonObject().apply { addProperty("position", latest.number("position")); addProperty("selection", SpaceReactionSelection.encode(choices)) }))
        remove(invalidKey)
    }

    data class ReactionPage(val choices: List<Reaction>, val nextEmoji: String?, val complete: Boolean)
    fun reactionPage(space: String, message: Message, afterEmoji: String? = null, channel: String? = null): ReactionPage = store.project { store.transaction {
        owned(); val target = channel ?: selectedChannel(state(space))
        val original = acceptedMessage(space, target, message)
        retryUnavailableReactions(space, target)
        afterEmoji?.let { SpaceReactionSelection.encode(setOf(it)) }
        if (messageDeletion(space, target, message.id, original.author) != null) return@transaction ReactionPage(emptyList(), null, true)
        val projection = reactionProjection(space, target, message.id, afterEmoji, includeOwn = false)
        ReactionPage(projection.values, projection.nextEmoji, projection.complete)
    } }

    private fun SpaceClientStore.Transaction.ownReactionAvailable(space: String, channel: String, message: String): Boolean {
        if (get(reactionInvalidPrefix(space, channel, message) + wallet.publicKey) != null) return false
        val summary = get(reactionSummaryKey(space, channel, message))?.let(::json) ?: return true
        val through = get(ownReactionVersionKey(space, channel, message))?.let(::json)?.number("through") ?: -1L
        return summary.number("loaded") >= summary.number("goal") || through >= summary.number("goal")
    }

    private fun SpaceClientStore.Transaction.reactionProjection(space: String, channel: String, message: String,
        afterEmoji: String? = null, includeOwn: Boolean = true): ReactionProjection {
        val summary = get(reactionSummaryKey(space, channel, message))?.let(::json)
        var complete = (summary == null || summary.number("loaded") >= summary.number("goal")) && page(reactionInvalidPrefix(space, channel, message), limit = 1).isEmpty()
        val prefix = reactionCountsPrefix(space, channel, message)
        val after = afterEmoji?.let(::reactionEmojiKey)
        val first = page(prefix, if (after == null) prefix else prefix + after + '\u0000', 50)
        // A selection contains at most16 emojis. Seventeen extra count rows
        // preserve a full bounded page even if all16 own choices are removed.
        val following = if (first.size == 50) page(prefix, first.last().key + '\u0000', 17) else emptyList()
        val counts = (first + following).associate { row ->
            val body = json(row.value); body.text("emoji") to body.number("count")
        }.toMutableMap()
        val acceptedOwn = get(reactionAppliedPrefix(space, channel, message) + wallet.publicKey)?.let(::json)?.text("selection")?.let(SpaceReactionSelection::decode).orEmpty()
        var own = acceptedOwn
        val pending = get(reactionPendingKey(space, channel, message))?.let { get(it.toString(Charsets.UTF_8)) }?.let(::json)
        if (pending != null && !pending.has("disposition")) {
            val envelope = SaitoTransaction.restore(pending.text("original")).envelope()
            val desired = readReaction(space, channel, envelope)
            if (desired == null) complete = false else own = desired
        }
        for (emoji in acceptedOwn + own) {
            if (emoji !in counts) get(prefix + reactionEmojiKey(emoji))?.let { counts[emoji] = json(it).number("count") }
            val count = Math.addExact(counts[emoji] ?: 0, ((if (emoji in own) 1 else 0) - (if (emoji in acceptedOwn) 1 else 0)).toLong())
            require(count >= 0)
            if (count == 0L) counts.remove(emoji) else counts[emoji] = count
        }
        val ordered = counts.entries.filter { after == null || reactionEmojiKey(it.key) > after }.sortedBy { reactionEmojiKey(it.key) }
        val firstChoices = ordered.take(50)
        val shown = if (includeOwn) (firstChoices + ordered.filter { it.key in acceptedOwn || it.key in own }).distinctBy { it.key } else firstChoices
        val visible = shown.map { it.key }.toSet()
        val more = ordered.any { it.key !in visible }
        return ReactionProjection(shown.map { (emoji, count) -> Reaction(emoji, count.takeIf { complete }, emoji in own) }, own, complete,
            more, firstChoices.lastOrNull()?.key?.takeIf { more })
    }

    private fun SpaceClientStore.Transaction.reactionProgress(space: String, channel: String, cursor: Long, before: Boolean, thread: String?): JsonObject {
        val result = JsonObject()
        if (before && cursor == 0L) return result
        val prefix = if (before) reverseMessageIndex(space, channel, thread) else confirmedMessageIndex(space, channel, thread)
        val from = if (before) Long.MAX_VALUE - cursor + 1 else cursor
        for (row in page(prefix, prefix + positionKey(from), 50)) {
            val position = row.key.substringAfterLast('/').toLong().let { if (before) Long.MAX_VALUE - it else it }
            val id = get(messagePositionIdKey(space, channel, position))?.toString(Charsets.UTF_8) ?: continue
            val saved = get(reactionSummaryKey(space, channel, id))?.let(::json) ?: continue
            result.add(id, JsonObject().apply {
                val loaded = saved.number("loaded"); val goal = saved.number("goal")
                addProperty("after", if (loaded >= goal) loaded else saved.number("cursor"))
                if (loaded < goal) addProperty("through", goal)
            })
        }
        return result
    }

    /** Read pin targets independently of the current conversation window. */
    suspend fun refreshPins(space: String, channel: String? = null) = refreshPinPage(space, channel, 2)

    private suspend fun refreshPinPage(space: String, channel: String?, remainingPages: Int) {
        require(remainingPages in 0..2)
        val target = store.transaction { owned(); channel ?: selectedChannel(state(space)) }
        val before = store.transaction { ensurePinProjection(space, target); channelState(space, target).deepCopy() }
        val cursor = before.get("pin_cursor")?.asLong ?: before.get("pin_loaded")?.asLong ?: 0L
        val through = before.get("pin_through")?.asLong?.takeIf { it > cursor }
        val result = readPage(JsonObject().apply {
            addProperty("space_id", space); addProperty("channel_id", target); addProperty("stream", "pins"); addProperty("cursor", cursor)
            through?.let { addProperty("through", it) }
        })
        require(supportsMessagePins)
        val end = result.number("content_end"); val version = result.number("pin_version"); val goal = result.number("pin_end")
        val next = result.number("next_cursor"); val more = result.get("has_more").also { require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) }.asBoolean
        require(cursor <= next && next <= version && version <= goal && goal <= end && (!more || next > cursor))
        through?.let { require(it == version) }; if (!more) require(next == version)
        val records = mutableListOf<AuthenticatedContent>(); val controls = mutableMapOf<String, SpaceJournal>()
        val replyCounts = mutableMapOf<String, Long>()
        var lastPin = cursor - 1
        val targets = mutableSetOf<String>()
        for (value in result.getAsJsonArray("items")) {
            val item = value.asJsonObject
            require(item.keySet().all { it in setOf("message", "pin", "edit", "deletion", "reply_count", "parent") } && item.has("pin"))
            val original = ensureAuthorization(space, item.text("message"), target, controls)
            val body = original.original.body; val kind = body.text("content_kind")
            require(kind in setOf("message", "thread-reply") && original.receipt.body.number("position") < end && targets.add(original.original.id))
            val thread = if (kind == "thread-reply") body.text("target_message_id") else null
            if (thread != null) {
                val parentItem = item.getAsJsonObject("parent")
                require(parentItem.keySet().all { it in setOf("message", "edit", "deletion", "reply_count") })
                val parent = ensureAuthorization(space, parentItem.text("message"), target, controls)
                require(parent.original.id == thread && parent.original.body.text("content_kind") == "message" &&
                    parent.receipt.body.number("position") == body.number("target_position") && parent.receipt.body.number("position") < original.receipt.body.number("position"))
                if (parentItem.has("reply_count")) replyCounts[parent.original.id] = parentItem.number("reply_count")
                records.add(parent); records.addAll(messageVersions(space, target, parentItem, parent, null, end, controls))
            } else require(!item.has("parent"))
            val pin = SaitoEnvelope.decode(item.text("pin")); val position = pin.body.number("position")
            require(position >= cursor && position < version && position > lastPin); lastPin = position
            if (item.has("reply_count")) { require(thread == null); replyCounts[original.original.id] = item.number("reply_count") }
            records.add(original); records.addAll(messageVersions(space, target, item, original, thread, end, controls))
        }
        require(!more || records.isNotEmpty() && next == Math.addExact(lastPin, 1))
        store.transaction {
            owned()
            for (record in records) accept(space, record.envelope, controls, authenticated = record)
            recordReplyCounts(space, target, replyCounts, end)
            val current = channelState(space, target)
            val loaded = current.get("pin_loaded")?.asLong ?: 0
            val priorGoal = current.get("pin_goal")?.asLong ?: 0
            current.addProperty("pin_goal", maxOf(priorGoal, goal))
            if (loaded <= version) {
                if (!more) {
                    current.addProperty("pin_loaded", maxOf(loaded, version)); current.addProperty("pin_cursor", maxOf(loaded, version)); current.remove("pin_through")
                } else if (loaded < version) {
                    val priorVersion = current.get("pin_through")?.asLong
                    if (priorVersion == null || priorVersion <= version) {
                        current.addProperty("pin_cursor", maxOf(next, if (priorVersion == version) current.get("pin_cursor")?.asLong ?: 0 else 0))
                        current.addProperty("pin_through", version)
                    }
                }
            }
            current.addProperty("pins_loaded", true); putChannelState(space, target, current)
        }
        if (more && remainingPages > 0) refreshPinPage(space, target, remainingPages - 1)
    }

    /** Cancellation and the durable transmission marker share the same store transaction.
     * A cancelled original remains locally recoverable, without a forged node rejection. */
    fun cancelQueuedMessage(space: String, messageId: String): Boolean = cancelQueuedContent(space, messageId, change = false)
    fun cancelQueuedChange(space: String, operationId: String): Boolean = cancelQueuedContent(space, operationId, change = true)
    private fun cancelQueuedContent(space: String, messageId: String, change: Boolean): Boolean = store.transaction {
        owned(); state(space)
        val pointer = get("$space/operation/$messageId")?.toString(Charsets.UTF_8) ?: return@transaction false
        val saved = json(requireNotNull(get(pointer)))
        val original = SaitoEnvelope.decode(SaitoTransaction.restore(saved.text("original")).envelope())
        require(original.id == messageId && original.author == wallet.publicKey && original.body.text("space_id") == space)
        val channel = messageChannel(original.body)
        val pinChannel = original.body.get("channel_id")?.asString?.takeIf { original.body.get("content_kind")?.asString == "message-pin" }
        val changeKey = messageChangeKey(space, original.body) ?: pinChannel?.let { pinPendingKey(space, it, original.body.text("target_message_id")) }
        require(if (change) changeKey != null else channel != null)
        if (saved.get("attempted").asBoolean) return@transaction false
        val cancelledKey = "$space/rejected/$messageId"
        saved.addProperty("cancelled", true)
        put(cancelledKey, bytes(saved))
        if (channel != null) {
            put(messageIndex(space, channel, "rejected", messageThread(original.body)) + messageId, cancelledKey.toByteArray(Charsets.UTF_8))
            remove(messageIndex(space, channel, "pending", messageThread(original.body)) + pointer.substringAfterLast('/'))
        } else if (changeKey != null && get(changeKey)?.toString(Charsets.UTF_8) == pointer) {
            put(changeKey, cancelledKey.toByteArray(Charsets.UTF_8))
        }
        remove(pointer); remove("$space/operation/$messageId")
        pinChannel?.let { reconcilePinTarget(space, it, original.body.text("target_message_id")) }
        true
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
                messageChangeKey(space, outbound.body)?.let { key ->
                    if (get(key)?.toString(Charsets.UTF_8) == item.key) remove(key)
                }
                if (outbound.body.get("content_kind")?.asString == "message-pin") {
                    val key = pinPendingKey(space, outbound.body.text("channel_id"), outbound.body.text("target_message_id"))
                    if (get(key)?.toString(Charsets.UTF_8) == item.key) remove(key)
                }
                if (outbound.body.get("content_kind")?.asString == "message-reaction") {
                    val key = reactionPendingKey(space, outbound.body.text("channel_id"), outbound.body.text("target_message_id"))
                    if (get(key)?.toString(Charsets.UTF_8) == item.key) remove(key)
                }
                remove(item.key)
                messageChannel(outbound.body)?.let { channel -> remove(messageIndex(space, channel, "pending", messageThread(outbound.body)) + item.key.substringAfterLast('/')) }
                remove("$space/operation/${transaction.id}")
                if (outbound.body.get("content_kind")?.asString == "message-pin")
                    reconcilePinTarget(space, outbound.body.text("channel_id"), outbound.body.text("target_message_id"))
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

    private data class MessagePage(val start: Long, val end: Long, val next: Long, val count: Int, val more: Boolean)
    private suspend fun messagePage(space: String, channel: String, cursor: Long = Long.MAX_VALUE, before: Boolean = true, limit: Int = 50, thread: String? = null, reactionRounds: Int = 2, exactArticle: String? = null, exactReport: SpaceMessageReport? = null, exactDestination: SpaceDestination? = null): MessagePage {
        require(limit in 1..50)
        require(listOf(exactArticle, exactReport, exactDestination).count { it != null } <= 1)
        require(exactArticle == null || thread == null)
        require(exactDestination == null || exactDestination.channel == channel && exactDestination.thread == thread &&
            exactDestination.community.spaceId == space && exactDestination.record != null)
        val detached = exactArticle != null || exactReport != null || exactDestination != null
        require(!detached || (limit == 1 && before && cursor == Long.MAX_VALUE))
        val result = readPage(JsonObject().apply {
            addProperty("space_id", space); addProperty("stream", "messages"); addProperty("channel_id", channel)
            addProperty("cursor", cursor); addProperty("direction", if (before) "before" else "after"); addProperty("limit", limit)
            thread?.let { addProperty("thread_id", it) }
            exactArticle?.let { addProperty("article_id", it) }
            exactReport?.let { addProperty("report_id", it.id) }
            exactDestination?.let {
                addProperty("destination_kind", it.kind.wire)
                addProperty("record_id", requireNotNull(it.record))
            }
            if (!detached && supportsMessagePins) addProperty("pin_history", true)
            if (!detached && supportsMessageReactions) {
                addProperty("reaction_history", true)
                add("reaction_cursors", store.transaction { reactionProgress(space, channel, cursor, before, thread) })
            }
        })
        if (exactArticle != null) require(result.get("space_articles_v1")?.asBoolean == true)
        if (exactDestination != null) require(result.get("destination_authorized")?.asBoolean == true)
        require(result.getAsJsonArray("items").size() <= limit)
        val controls = mutableMapOf<String, SpaceJournal>()
        val records = mutableListOf<AuthenticatedContent>()
        val positions = mutableListOf<Long>()
        val replyCounts = mutableMapOf<String, Long>()
        val reactionSummaries = mutableMapOf<String, Triple<Long, Long, Boolean>>()
        val ownReactionVersions = mutableMapOf<String, Long>()
        val end = result.number("content_end")
        for (value in result.getAsJsonArray("items")) {
            val item = value.asJsonObject
            require(item.has("message") && item.keySet().all { it in setOf("message", "edit", "deletion", "reply_count", "reactions", "reactions_complete", "reaction_version", "reaction_next_cursor", "own_reaction", "pin") })
            val original = ensureAuthorization(space, item.text("message"), channel, controls)
            if (exactArticle != null)
                require(original.original.id == exactArticle && original.original.body.text("content_kind") == "article")
            if (exactReport != null)
                require(original.original.id == exactReport.message &&
                    original.original.body.text("content_kind") == if (thread == null) "message" else "thread-reply")
            if (exactDestination != null)
                require(original.original.id == exactDestination.record &&
                    original.original.body.text("content_kind") == exactDestination.kind.expectedRecord(thread))
            val position = original.receipt.body.number("position")
            require(original.original.body.text("content_kind") in (if (thread == null) ROOT_CONTENT_KINDS else setOf("thread-reply")) &&
                (thread == null || original.original.body.text("target_message_id") == thread) && position < end &&
                (if (before) position < cursor else position >= cursor) && (positions.lastOrNull()?.let { position > it } ?: true))
            positions.add(position); records.add(original)
            if (item.has("reply_count")) {
                require(thread == null)
                replyCounts[original.original.id] = item.number("reply_count")
            }
            if (item.has("reactions")) {
                require(result.get("message_reactions")?.asBoolean == true)
                val values = item.getAsJsonArray("reactions"); require(values.size() <= 50)
                val actors = mutableSetOf<String>()
                for (value in values) {
                    val reaction = ensureAuthorization(space, value.asString, channel, controls)
                    require(reaction.original.body.text("content_kind") == "message-reaction" && actors.add(reaction.original.author) &&
                        reaction.original.body.text("target_message_id") == original.original.id && reaction.original.body.number("target_position") == position &&
                        reaction.original.body.get("thread_root_id")?.asString == thread && reaction.receipt.body.number("position") in (position + 1) until end)
                    records.add(reaction)
                }
                val complete = item.get("reactions_complete").also { require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) }.asBoolean
                val version = item.number("reaction_version"); val next = item.number("reaction_next_cursor")
                val progress = result.getAsJsonObject("scope").getAsJsonObject("reaction_cursors")?.getAsJsonObject(original.original.id)
                val after = progress?.number("after") ?: 0L
                require(version <= end && after <= next && next <= version && (if (complete) next == version else values.size() > 0 && next > after))
                progress?.get("through")?.asLong?.let { require(version == it) }
                item.get("own_reaction")?.let { own ->
                    if (!own.isJsonNull) {
                        val reaction = ensureAuthorization(space, own.asString, channel, controls)
                        require(reaction.original.author == wallet.publicKey && reaction.original.body.text("content_kind") == "message-reaction" &&
                            reaction.original.body.text("target_message_id") == original.original.id && reaction.original.body.number("target_position") == position &&
                            reaction.original.body.get("thread_root_id")?.asString == thread && reaction.receipt.body.number("position") in (position + 1) until version)
                        records.add(reaction)
                    }
                    ownReactionVersions[original.original.id] = version
                }
                reactionSummaries[original.original.id] = Triple(version, next, complete)
            } else require(!item.has("reactions_complete"))
            records.addAll(messageVersions(space, channel, item, original, thread, end, controls))
        }
        require(result.number("next_cursor") == positions.lastOrNull()?.let { Math.addExact(it, 1) } ?: cursor)
        val more = result.get("has_more").also { require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) }.asBoolean
        require(!more || positions.isNotEmpty())
        val start = positions.firstOrNull() ?: if (before) 0 else cursor
        store.transaction {
            owned()
            // Every record is verified before this page changes the visible
            // projection. A malformed later entry rolls the whole page back.
            val decoder = reactionDecoder(space, channel)
            for (record in records) accept(space, record.envelope, controls, authenticated = record, reactionReader = decoder)
            if (exactReport != null) return@transaction
            if (exactDestination != null && exactDestination.kind != SpaceDestination.Kind.ARTICLE) return@transaction
            val denial = articleReadDeniedKey(space, channel)
            if (get(denial) != null) remove(denial)
            if (detached) return@transaction
            retryUnavailableReactions(space, channel)
            recordReplyCounts(space, channel, replyCounts, end)
            for ((id, version) in ownReactionVersions) {
                val key = ownReactionVersionKey(space, channel, id)
                val previous = get(key)?.let(::json)?.number("through") ?: -1L
                if (version > previous) put(key, bytes(JsonObject().apply { addProperty("through", version) }))
            }
            for ((id, progress) in reactionSummaries) {
                val (version, next, complete) = progress
                val key = reactionSummaryKey(space, channel, id)
                val previous = get(key)?.let(::json)
                val loaded = previous?.number("loaded") ?: 0L
                val goal = previous?.number("goal") ?: 0L
                if (version < loaded || version < goal || loaded >= version && previous != null) continue
                put(key, bytes(JsonObject().apply {
                    addProperty("loaded", if (complete) maxOf(loaded, version) else loaded)
                    addProperty("goal", version)
                    addProperty("cursor", if (complete) version else maxOf(next, if (goal == version) previous?.number("cursor") ?: 0 else 0))
                }))
            }
            val capability = channelState(space, channel)
            capability.addProperty("pins_supported", supportsMessagePins)
            capability.addProperty("reactions_supported", supportsMessageReactions); putChannelState(space, channel, capability)
            val current = conversationState(space, channel, thread)
            // Earlier immutable records remain useful, but an old page cannot
            // reset the latest-window boundary after a newer read completed.
            if (current.get("history_observed_end")?.asLong?.let { it > end } == true) return@transaction
            current.addProperty("history_observed_end", end)
            val indexed = current.get("message_history")?.asBoolean == true
            val previous = current.get("history_from")?.asLong
            val previousEnd = current.get("message_window_end")?.asLong
            // A latest page after a long offline gap is a separate window.
            // Never describe the interval between two islands as loaded.
            if (!indexed || previous == null || start <= previous ||
                before && cursor == Long.MAX_VALUE && (previousEnd == null || start > previousEnd)) {
                current.addProperty("history_from", start)
                if (before) current.addProperty("history_has_earlier", more)
            }
            current.addProperty("message_history", true)
            if (before && cursor == Long.MAX_VALUE) {
                current.addProperty("message_window_end", positions.lastOrNull()?.let { Math.addExact(it, 1) } ?: 0)
                current.addProperty("latest_message_from", start)
            }
            putConversationState(space, channel, thread, current)
        }
        if (reactionRounds > 0 && reactionSummaries.values.any { !it.third })
            return messagePage(space, channel, cursor, before, limit, thread, reactionRounds - 1)
        return MessagePage(start, end, result.number("next_cursor"), positions.size, more)
    }

    private fun SpaceClientStore.Transaction.recordReplyCounts(space: String, channel: String, replyCounts: Map<String, Long>, end: Long) {
        for ((id, count) in replyCounts) {
            val key = replyCountKey(space, channel, id)
            val previous = get(key)?.let(::json)
            if (previous != null && previous.number("content_end") == end) require(previous.number("count") == count)
            if (previous == null || previous.number("content_end") < end) put(key, bytes(JsonObject().apply {
                addProperty("count", count); addProperty("content_end", end)
            }))
        }
    }

    private suspend fun messageVersions(space: String, channel: String, item: JsonObject,
        original: AuthenticatedContent, thread: String?, end: Long, controls: MutableMap<String, SpaceJournal>): List<AuthenticatedContent> {
        val records = mutableListOf<AuthenticatedContent>()
        val position = original.receipt.body.number("position")
        item.get("pin")?.asString?.let { envelope ->
            require(supportsMessagePins)
            val pin = ensureAuthorization(space, envelope, channel, controls)
            val control = pin.receipt.body.get("authorized_control")?.asString ?: pin.original.body.text("control_id")
            require(pin.original.body.text("content_kind") == "message-pin" &&
                requireNotNull(controls[control]).hasCurrentAdministratorIntent(pin.original.author, pin.original.body.text("control_id")) &&
                pin.original.body.text("target_message_id") == original.original.id && pin.original.body.number("target_position") == position &&
                pin.original.body.get("thread_root_id")?.asString == thread && pin.receipt.body.number("position") in (position + 1) until end)
            records.add(pin)
        }
        item.get("edit")?.asString?.let { envelope ->
            val edit = ensureAuthorization(space, envelope, channel, controls)
            require(edit.original.body.text("content_kind") == "message-edit" && edit.original.author == original.original.author &&
                edit.original.body.text("target_message_id") == original.original.id && edit.original.body.number("target_position") == position &&
                edit.original.body.get("thread_root_id")?.asString == thread &&
                edit.receipt.body.number("position") in (position + 1) until end)
            records.add(edit)
        }
        item.get("deletion")?.asString?.let { envelope ->
            val deletion = ensureAuthorization(space, envelope, channel, controls)
            val control = deletion.receipt.body.get("authorized_control")?.asString ?: deletion.original.body.text("control_id")
            val authority = requireNotNull(controls[control])
            require(deletion.original.body.text("content_kind") == "message-delete" &&
                (deletion.original.author == original.original.author || authority.hasCurrentAdministratorIntent(deletion.original.author, deletion.original.body.text("control_id"))) &&
                deletion.original.body.text("target_message_id") == original.original.id && deletion.original.body.number("target_position") == position &&
                deletion.original.body.get("thread_root_id")?.asString == thread &&
                deletion.receipt.body.number("position") in (position + 1) until end)
            records.add(deletion)
        }
        return records
    }

    suspend fun olderThread(space: String, parent: Message, before: Long? = null, channel: String? = null): Long {
        require(before == null || before >= 0)
        val target = store.transaction { owned(); val target = channel ?: selectedChannel(state(space)); threadParent(space, target, parent); target }
        val cursor = before ?: store.transaction { conversationState(space, target, parent.id).get("history_from")?.asLong ?: 0 }
        return messagePage(space, target, cursor, thread = parent.id).start
    }

    suspend fun older(space: String, channel: String? = null, before: Long? = null): Long {
        require(before == null || before >= 0)
        val targetChannel = store.transaction { owned(); val current = state(space); channel ?: selectedChannel(current) }
        val upper = before ?: store.transaction { channelState(space, targetChannel).get("history_from")?.asLong ?: 0 }
        if (store.transaction { channelState(space, targetChannel).get("message_history")?.asBoolean == true })
            return messagePage(space, targetChannel, upper).start
        val start = (upper - 50).coerceAtLeast(0)
        var cursor = start
        while (cursor < upper) {
            val next = contentPage(space, cursor, upper, targetChannel)
            require(next > cursor) { "Earlier conversation history is unavailable" }
            cursor = next
        }
        store.transaction {
            val current = channelState(space, targetChannel)
            current.addProperty("history_from", minOf(current.get("history_from")?.asLong ?: start, start)); putChannelState(space, targetChannel, current)
        }
        return start
    }

    suspend fun refresh(space: String, channel: String? = null, from: Long? = null) {
        require(from == null || from >= 0)
        val targetChannel = store.transaction { owned(); val current = state(space); channel ?: selectedChannel(current) }
        try { syncControls(space) }
        catch (denied: ReadDenied) { rememberArticleReadDenial(space, targetChannel); throw denied }
        store.transaction {
            owned()
            val state = state(space)
            val journal = journal(space)
            val preparingHandover = journal.snapshot.ownershipOffer?.let { it.member == wallet.publicKey && it.acceptedId != null } == true
            if (state.get("joined").asBoolean) journal.snapshot.channels.forEach { (channel, record) ->
                if ((record.allowedMembers != null || preparingHandover || channel == journal.snapshot.entryChannelId && record.visibility == SpaceVisibility.PRIVATE) && journal.canAccessChannel(wallet.publicKey, channel))
                    prepareChannelJoin(space, state, journal.snapshot, channel)
            }
        }
        val currentChannel = store.transaction { requireNotNull(journal(space).snapshot.channels[targetChannel]) }
        val before = store.transaction { channelState(space, targetChannel) }
        val archiveOnly = before.get("archive_only")?.asBoolean == true && before.get("joined")?.asBoolean != true
        if (currentChannel.visibility != SpaceVisibility.PUBLIC && before.get("joined")?.asBoolean != true && !archiveOnly) return
        val first = !before.has("content_loaded")
        if (supportsMessageHistory) {
            if (currentChannel.visibility != SpaceVisibility.PUBLIC && !archiveOnly) refreshKeys(space, targetChannel)
            // Seed only the previously visible bounded cache when upgrading
            // from a raw-record service, preserving offline reading anchors.
            if (before.get("message_history")?.asBoolean != true) store.transaction {
                val prefix = contentPrefix(space, targetChannel)
                val from = maxOf(((before.get("last_content")?.asLong ?: 0) - 49).coerceAtLeast(0), before.get("history_from")?.asLong ?: 0)
                val anchor = get("$space/channels/$targetChannel/reading")?.let(::json)?.number("window_from")
                for (start in listOfNotNull(from, anchor).distinct())
                    page(prefix, prefix + positionKey(start)).forEach { indexConfirmedMessage(space, targetChannel, json(it.value)) }
            }
            val page = messagePage(space, targetChannel)
            val readingFrom = from ?: readingAnchor(space, targetChannel)?.windowFrom
            if (readingFrom != null && readingFrom < page.start) {
                var cursor: Long = readingFrom
                var remaining = 50
                while (remaining > 0) {
                    val observed = messagePage(space, targetChannel, cursor, before = false, limit = remaining)
                    remaining -= observed.count
                    if (!observed.more || observed.count == 0) break
                    require(observed.next > cursor)
                    cursor = observed.next
                }
            }
            val next = if (first || archiveOnly) page.end else contentPage(space, before.number("content_cursor"), channel = targetChannel)
            store.transaction {
                val current = channelState(space, targetChannel)
                // Another completed read owns the newer delivery cursor.
                // Retain verified immutable records without replacing its progress.
                if (current.number("content_cursor") != before.number("content_cursor")) return@transaction
                current.addProperty("content_cursor", next); current.addProperty("content_loaded", true)
                if (currentChannel.rootContentKind == "article") current.addProperty("articles_supported", supportsSpaceArticles)
                putChannelState(space, targetChannel, current)
            }
            if (supportsMessagePins && !archiveOnly) refreshPins(space, targetChannel)
            return
        }
        if (archiveOnly) return
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
    fun historyWindow(space: String, from: Long? = null, channel: String? = null): HistoryWindow =
        conversationHistoryWindow(space, from, channel, null)
    fun threadHistoryWindow(space: String, parent: Message, from: Long? = null, channel: String? = null): HistoryWindow =
        conversationHistoryWindow(space, from, channel, parent)

    private fun conversationHistoryWindow(space: String, from: Long?, channel: String?, parent: Message?): HistoryWindow = store.transaction {
        owned(); require(from == null || from >= 0)
        val state = state(space)
        val target = channel ?: selectedChannel(state)
        parent?.let { threadParent(space, target, it) }
        val current = conversationState(space, target, parent?.id)
        if (current.get("message_history")?.asBoolean == true) {
            val prefix = confirmedMessageIndex(space, target, parent?.id)
            val start = from ?: maxOf(current.get("latest_message_from")?.asLong ?: 0, latestMessageStart(space, target, 50, parent?.id))
            val localEarlier = page(prefix, limit = 1).firstOrNull()?.key?.substringAfterLast('/')?.toLong()?.let { it < start } == true
            val rows = page(prefix, prefix + positionKey(start), 50)
            val later = rows.lastOrNull()?.let { page(prefix, it.key + '\u0000', 1).isNotEmpty() } == true
            return@transaction HistoryWindow(start, current.get("history_from")?.asLong ?: start,
                start > 0 && (localEarlier || current.get("history_has_earlier")?.asBoolean == true), later, current.get("content_loaded")?.asBoolean == true)
        }
        val last = current.get("last_content")?.asLong ?: 0
        val start = from ?: maxOf((last - 49).coerceAtLeast(0), current.get("history_from")?.asLong ?: 0)
        require(start >= 0)
        HistoryWindow(start, current.get("history_from")?.asLong ?: start, start > 0, last >= start + 50, current.get("content_loaded")?.asBoolean == true)
    }

    /** Bounded local page, including explicit unavailable keys and durable
     * queued originals. The UI never infers receipt from an optimistic row. */
    fun timeline(space: String, from: Long? = null, limit: Int = 50, channel: String? = null): List<Message> =
        conversationTimeline(space, from, limit, channel, null)

    private fun conversationTimeline(space: String, from: Long?, limit: Int, channel: String?, thread: String?): List<Message> = store.transaction {
        owned(); require(limit in 1..50)
        val state = state(space)
        ensureMessageIndexes(space, state)
        val targetChannel = channel ?: selectedChannel(state)
        retryUnavailableReactions(space, targetChannel)
        val data = conversationState(space, targetChannel, thread)
        val readPrivate = protectedHistoryReader(space, targetChannel)
        val prefix = contentPrefix(space, targetChannel)
        val last = data.get("last_content")?.asLong ?: 0
        val indexed = data.get("message_history")?.asBoolean == true
        val start = from ?: if (indexed) maxOf(data.get("latest_message_from")?.asLong ?: 0, latestMessageStart(space, targetChannel, limit, thread)) else
            maxOf((last - limit + 1).coerceAtLeast(0), data.get("history_from")?.asLong ?: 0)
        val mayReadArticles by lazy {
            get(articleReadDeniedKey(space, targetChannel)) == null && journal(space).canAccessChannel(wallet.publicKey, targetChannel)
        }
        fun message(envelope: String, pending: Boolean, attempted: Boolean, rejected: Boolean = false, position: Long? = null, cancelled: Boolean = false): Message? {
            val original = SaitoEnvelope.decode(envelope)
            if (original.body.get("channel_id")?.asString != targetChannel) return null
            if (original.body.text("kind") !in setOf("content", "public-content")) return null
            val contentKind = original.body.text("content_kind")
            if (contentKind !in (if (thread == null) ROOT_CONTENT_KINDS else setOf("thread-reply"))) return null
            if (contentKind in setOf("article", "file") && !mayReadArticles) return null
            if (thread != null && original.body.text("target_message_id") != thread) return null
            var missing = false
            fun read(source: String, signed: SignedSaitoMessage): String? =
                if (signed.body.text("kind") == "public-content") signed.body.text("text") else
                    try { readPrivate(source, signed) }
                    catch (_: SpaceCrypto.MissingKeys) { missing = true; null }
                    catch (_: java.security.GeneralSecurityException) { null }
            val change = get(messageChangeKey(space, targetChannel, original.id))?.let { pointer ->
                get(pointer.toString(Charsets.UTF_8))?.let(::json)
            }?.let { saved ->
                val pending = SaitoEnvelope.decode(SaitoTransaction.restore(saved.text("original")).envelope())
                require(pending.author == wallet.publicKey && pending.body.text("target_message_id") == original.id &&
                    pending.body.text("channel_id") == targetChannel && pending.body.get("thread_root_id")?.asString == thread)
                val edit = pending.body.text("content_kind") == "message-edit"
                MessageChange(pending.id, if (edit) MessageChangeKind.EDIT else MessageChangeKind.DELETE,
                    saved.get("attempted").asBoolean, saved.has("disposition"),
                    if (edit) read(SaitoTransaction.restore(saved.text("original")).envelope(), pending) else null,
                    cancelled = saved.get("cancelled")?.asBoolean == true)
            }
            val reactions = if (pending || rejected || cancelled) ReactionProjection(emptyList(), emptySet(), true) else reactionProjection(space, targetChannel, original.id)
            val pin = if (pending || rejected || cancelled) PinProjection(false, false, true) else messagePin(space, targetChannel, original.id)
            val pinChange = get(pinPendingKey(space, targetChannel, original.id))?.let { get(it.toString(Charsets.UTF_8)) }?.let(::json)?.let { saved ->
                val intent = SaitoEnvelope.decode(SaitoTransaction.restore(saved.text("original")).envelope())
                require(intent.author == wallet.publicKey && intent.body.text("content_kind") == "message-pin" &&
                    intent.body.text("channel_id") == targetChannel && intent.body.text("target_message_id") == original.id &&
                    intent.body.get("thread_root_id")?.asString == thread)
                PinChange(intent.id, saved.get("attempted").asBoolean, saved.has("disposition"), saved.get("cancelled")?.asBoolean == true)
            }
            val base = Message(original.id, original.author, original.createdAt, read(envelope, original),
                pending, attempted, missing, rejected, position,
                replyCount = if (thread == null) get(replyCountKey(space, targetChannel, original.id))?.let(::json)?.number("count") ?: 0 else 0, change = change, cancelled = cancelled, reactions = reactions.values, reactionsComplete = reactions.complete, reactionsHaveMore = reactions.more, ownReactionsAvailable = ownReactionAvailable(space, targetChannel, original.id), pinned = pin.pinned, pinPending = pin.pending, pinAvailable = pin.available, pinChange = pinChange,
                isArticle = contentKind == "article", isFile = contentKind == "file")
            val removed = if (position == null) null else messageDeletion(space, targetChannel, original.id, original.author)
            if (removed != null && removed.number("target_position") == position && removed.get("thread_root_id")?.asString == thread) return base.copy(text = null, deleted = true, missingKeys = false, reactions = emptyList(), reactionsHaveMore = false, pinned = false, pinPending = false)
            val saved = if (position == null) null else get(messageEditKey(space, targetChannel, original.id, original.author))
            val editEnvelope = saved?.let(::originalEnvelope)
            val edit = editEnvelope?.let(SaitoEnvelope::decode)
            return if (edit != null && edit.author == original.author && edit.body.number("target_position") == position && edit.body.get("thread_root_id")?.asString == thread) {
                if (base.isArticle && base.text?.let {
                        runCatching { SpaceArticleContent.decode(it) }.getOrNull()
                    } is SpaceArticleContent.PublicStack) return base
                missing = false
                val replacement = read(requireNotNull(editEnvelope), edit)
                if (base.isArticle && replacement != null && runCatching {
                        SpaceArticleContent.decode(replacement)
                    }.getOrNull() !is SpaceArticleContent.Original) return base
                base.copy(text = replacement, edited = true, missingKeys = missing, editedAt = edit.createdAt)
            } else base
        }
        val confirmedRows = if (indexed) {
            val messages = confirmedMessageIndex(space, targetChannel, thread)
            page(messages, messages + positionKey(start), limit).map { requireNotNull(get(it.value.toString(Charsets.UTF_8))) }
        } else page(prefix, prefix + positionKey(start), limit).map { it.value }
        val confirmed = confirmedRows.mapNotNull { row ->
            message(originalEnvelope(row), pending = false, attempted = true, position = json(row).number("position"))
        }
        val pending = page(messageIndex(space, targetChannel, "pending", thread), limit = limit).mapNotNull { row ->
            val saved = json(requireNotNull(get(row.value.toString(Charsets.UTF_8))))
            message(SaitoTransaction.restore(saved.text("original")).envelope(), pending = true, attempted = saved.get("attempted").asBoolean)
        }
        val rejected = page(messageIndex(space, targetChannel, "rejected", thread), limit = limit).mapNotNull { row ->
            val saved = json(requireNotNull(get(row.value.toString(Charsets.UTF_8))))
            val cancelled = saved.get("cancelled")?.asBoolean == true
            message(SaitoTransaction.restore(saved.text("original")).envelope(), pending = false, attempted = !cancelled, rejected = !cancelled, cancelled = cancelled)
        }
        (confirmed + pending + rejected).distinctBy { it.id }
    }
    fun messages(space: String, channel: String? = null): List<String> = timeline(space, channel = channel).filter { !it.pending && !it.rejected && !it.cancelled }.mapNotNull { it.text }

    data class ReadingAnchor(val messageId: String, val position: Long, val offset: Int, val windowFrom: Long) {
        init {
            require(messageId.matches(Regex("[0-9a-f]{128}")) && position >= 0 && windowFrom >= 0 &&
                windowFrom <= position)
        }
    }
    fun readingAnchor(space: String, channel: String? = null): ReadingAnchor? = store.transaction {
        owned(); val target = channel ?: selectedChannel(state(space)); channelState(space, target)
        readConversationAnchor(space, target, null)
    }
    fun threadReadingAnchor(space: String, parent: Message, channel: String? = null): ReadingAnchor? = store.transaction {
        owned(); val target = channel ?: selectedChannel(state(space)); threadParent(space, target, parent)
        readConversationAnchor(space, target, parent.id)
    }
    private fun SpaceClientStore.Transaction.readConversationAnchor(space: String, channel: String, thread: String?): ReadingAnchor? {
        val saved = get(conversationPrefix(space, channel, thread) + "reading")?.let(::json) ?: return null
        require(saved.number("version") == 1L)
        val offset = saved.get("offset").also { require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber) }.asString.toInt()
        return ReadingAnchor(saved.text("message"), saved.number("position"), offset, saved.number("window_from"))
    }
    fun saveReadingAnchor(space: String, channel: String, anchor: ReadingAnchor?) = store.transaction {
        owned(); channelState(space, channel)
        saveConversationAnchor(space, channel, null, anchor)
    }
    fun saveThreadReadingAnchor(space: String, parent: Message, anchor: ReadingAnchor?, channel: String? = null) = store.transaction {
        owned(); val target = channel ?: selectedChannel(state(space)); threadParent(space, target, parent)
        saveConversationAnchor(space, target, parent, anchor)
    }
    private fun SpaceClientStore.Transaction.saveConversationAnchor(space: String, channel: String, parent: Message?, anchor: ReadingAnchor?) {
        val key = conversationPrefix(space, channel, parent?.id) + "reading"
        if (anchor == null) remove(key) else {
            val record = json(requireNotNull(get(contentPrefix(space, channel) + positionKey(anchor.position))))
            val original = SaitoEnvelope.decode(record.text("event"))
            require(record.number("position") == anchor.position && original.id == anchor.messageId &&
                original.body.text("space_id") == space && original.body.text("channel_id") == channel &&
                original.body.text("content_kind") == if (parent == null) "message" else "thread-reply")
            if (parent != null) require(original.body.text("target_message_id") == parent.id && original.body.number("target_position") == parent.position)
            put(key, bytes(JsonObject().apply {
                addProperty("version", 1); addProperty("message", anchor.messageId); addProperty("position", anchor.position)
                addProperty("offset", anchor.offset); addProperty("window_from", anchor.windowFrom)
            }))
        }
    }

    data class Attention(val personal: Long?, val hasUnread: Boolean?, val complete: Boolean)
    fun attention(space: String, channel: String? = null): Attention = store.project { store.transaction {
        owned(); val target = channel ?: selectedChannel(state(space))
        ensureAttentionProjection(space, target); repairAttention(space, target)
        cachedAttention(space, target)
    } }
    /** Cached sidebar projection performs no decryption or history migration. */
    private fun SpaceClientStore.Transaction.cachedAttention(space: String, target: String): Attention {
        val data = channelState(space, target)
        val counts = SpaceAttentionIndex(this, "$space/channels/$target").counts()
        val complete = data.get("attention_observed")?.asBoolean == true && data.get("attention_projection_version")?.asLong == 1L &&
            (data.get("attention_cursor")?.asLong ?: 0) >= (data.get("attention_goal")?.asLong ?: 0) &&
            page("$space/channels/$target/attention-dirty/", limit = 1).isEmpty()
        return Attention(if (complete) counts.personal else null, when {
            counts.ordinary != null && counts.ordinary > 0 -> true
            complete && counts.ordinary == 0L -> false
            else -> null
        }, complete && counts.personal != null)
    }
    fun messageAttention(space: String, messageId: String, channel: String? = null): SpaceMessageAttention? = store.transaction {
        owned(); val target = channel ?: selectedChannel(state(space))
        reconcileAttention(space, target, messageId, attentionReader(space, target))
        get("$space/channels/$target/message-attention/$messageId")?.let(::json)?.let { value ->
            SpaceMessageAttention(value.get("mentioned").asBoolean, value.get("replied").asBoolean,
                value.get("everyone").asBoolean, value.get("own").asBoolean)
        }
    }
    /** One bounded existing content page, retaining its authenticated ordering.
     * This does not move the conversation window, draft or reading anchor. */
    suspend fun refreshAttention(space: String, channel: String? = null) {
        syncControls(space)
        val target = store.transaction { owned(); channel ?: selectedChannel(state(space)) }
        if (store.transaction { journal(space).snapshot.channels.getValue(target).visibility } != SpaceVisibility.PUBLIC) refreshKeys(space, target)
        val cursor = store.transaction { channelState(space, target).get("attention_cursor")?.asLong ?: 0 }
        val page = readPage(JsonObject().apply {
            addProperty("space_id", space); addProperty("channel_id", target); addProperty("stream", "content"); addProperty("cursor", cursor)
        })
        val next = contentPage(space, cursor, channel = target, received = page)
        store.project { store.transaction {
            owned(); val data = channelState(space, target)
            data.addProperty("attention_cursor", maxOf(data.get("attention_cursor")?.asLong ?: 0, next))
            data.addProperty("attention_goal", maxOf(data.get("attention_goal")?.asLong ?: 0, page.number("content_end")))
            data.addProperty("attention_observed", true); putChannelState(space, target, data)
            ensureAttentionProjection(space, target); repairAttention(space, target)
        } }
    }
    fun markThreadRead(space: String, parent: Message, position: Long, channel: String? = null) = store.transaction {
        owned(); val target = channel ?: selectedChannel(state(space)); threadParent(space, target, parent)
        val receipt = json(requireNotNull(get(contentPrefix(space, target) + positionKey(position))))
        val original = SaitoEnvelope.decode(receipt.text("event"))
        require(original.body.text("channel_id") == target && original.body.text("content_kind") == "thread-reply" && messageThread(original.body) == parent.id)
        val data = conversationState(space, target, parent.id)
        data.addProperty("read_cursor", maxOf(data.get("read_cursor")?.asLong ?: 0, Math.addExact(position, 1)))
        putConversationState(space, target, parent.id, data)
        SpaceAttentionIndex(this, "$space/channels/$target").markRead(parent.id, position)
    }
    private fun SpaceClientStore.Transaction.attentionReader(space: String, channel: String): (String) -> String? {
        val readPrivate = protectedHistoryReader(space, channel)
        return { envelope -> try {
            val original = SaitoEnvelope.decode(envelope)
            if (original.body.text("kind") == "public-content") original.body.text("text") else readPrivate(envelope, original)
        } catch (_: Exception) { null } }
    }
    private fun SpaceClientStore.Transaction.reconcileAttention(space: String, channel: String, message: String, read: (String) -> String?): Boolean {
        val pointer = get(messageIdKey(space, channel, message)) ?: return false
        val receipt = get(pointer.toString(Charsets.UTF_8))?.let(::json) ?: return false
        val original = SaitoEnvelope.decode(receipt.text("event")); val thread = messageThread(original.body)
        val position = receipt.number("position")
        val edit = get(messageEditKey(space, channel, message, original.author))?.let(::json)
        val deletion = messageDeletionReceipt(space, channel, message, original.author)
        val latest = edit ?: receipt
        val text = if (deletion != null) "" else read(latest.text("event"))
        val own = original.author == wallet.publicKey
        val mentioned = text?.let { SpaceMessageAttention.mentions(it, wallet.publicKey) }
        val parent = thread?.let { get(messageIdKey(space, channel, it)) }?.let { get(it.toString(Charsets.UTF_8)) }?.let(::json)
            ?.let { SaitoEnvelope.decode(it.text("event")) }
        val replied = if (thread == null) false else parent?.let { it.author == wallet.publicKey }
        val personal = if (own || deletion != null) false else if (mentioned == true || replied == true) true else
            if (mentioned == null || replied == null) null else false
        val index = SpaceAttentionIndex(this, "$space/channels/$channel")
        index.observe(message, position, thread, personal, maxOf(position, edit?.number("position") ?: position, deletion?.number("position") ?: position), deletion != null, own)
        val key = "$space/channels/$channel/message-attention/$message"
        if (deletion != null || text == null || replied == null) remove(key) else {
            val event = SaitoEnvelope.decode(latest.text("event"))
            val control = latest.get("authorized_control")?.asString ?: event.body.text("control_id")
            val everyone = SpaceMessageAttention.everyone(text) &&
                journal(space, through = control).hasCurrentAdministratorIntent(event.author, event.body.text("control_id"))
            put(key, bytes(JsonObject().apply {
                addProperty("mentioned", mentioned == true); addProperty("replied", replied == true)
                addProperty("everyone", everyone); addProperty("own", own)
            }))
        }
        return true
    }
    private fun SpaceClientStore.Transaction.ensureAttentionProjection(space: String, channel: String) {
        val data = channelState(space, channel)
        if (data.get("attention_projection_version")?.asLong == 1L) return
        val prefix = messageIdKey(space, channel, "")
        val index = SpaceAttentionIndex(this, "$space/channels/$channel")
        (data.get("read_cursor")?.asLong ?: 0).takeIf { it > 0 }?.let { index.markRead(null, it - 1) }
        val rows = page(prefix, data.get("attention_projection_after")?.asString ?: prefix, 50)
        val read = attentionReader(space, channel)
        rows.forEach { reconcileAttention(space, channel, it.key.removePrefix(prefix), read) }
        if (rows.size < 50) { data.addProperty("attention_projection_version", 1); data.remove("attention_projection_after") }
        else data.addProperty("attention_projection_after", rows.last().key + '\u0000')
        putChannelState(space, channel, data)
    }
    private fun SpaceClientStore.Transaction.repairAttention(space: String, channel: String) {
        val prefix = "$space/channels/$channel/attention-dirty/"
        val read = attentionReader(space, channel)
        for (row in page(prefix, limit = 50)) if (reconcileAttention(space, channel, row.key.removePrefix(prefix), read)) remove(row.key)
        val index = SpaceAttentionIndex(this, "$space/channels/$channel")
        val cursor = "$space/channels/$channel/attention-repair-after"
        val ids = index.unavailable(after = get(cursor)?.toString(Charsets.UTF_8))
        ids.forEach { reconcileAttention(space, channel, it, read) }
        ids.lastOrNull()?.let { put(cursor, it.toByteArray(Charsets.UTF_8)) }
    }

    fun notificationPolicy(space: String, channel: String? = null): SpaceNotificationPolicy = store.transaction {
        owned(); val state = state(space); val target = channel ?: selectedChannel(state)
        require(target == generalChannel(state) || target in journal(space).snapshot.channels)
        val saved = get("$space/channels/$target/notification-policy")?.let(::json) ?: return@transaction SpaceNotificationPolicy()
        require(saved.keySet() == setOf("mode", "everyone") && saved.get("everyone").let { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean })
        SpaceNotificationPolicy(SpaceNotificationMode.entries.single { it.wire == saved.text("mode") }, saved.get("everyone").asBoolean)
    }
    fun setNotificationPolicy(space: String, policy: SpaceNotificationPolicy, channel: String? = null) = store.transaction {
        owned(); val state = state(space); val target = channel ?: selectedChannel(state)
        require(journal(space).canAccessChannel(wallet.publicKey, target) || target == selectedChannel(state))
        put("$space/channels/$target/notification-policy", bytes(JsonObject().apply {
            addProperty("mode", policy.mode.wire); addProperty("everyone", policy.allowEveryone)
        }))
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
        SpaceAttentionIndex(this, "$space/channels/$channel").markRead(null, position)
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
                    data.addProperty("attention_goal", maxOf(data.get("attention_goal")?.asLong ?: 0, end))
                    data.addProperty("unread_checked_at", clock.millis()); putChannelState(space, channel, data)
                }
            }
        }
    }
    /** Separate opt-in budget from cheap unread heads. Fairly visit one
     * eligible channel per cycle, never fetch an entire conversation at once. */
    suspend fun refreshNextAttention(space: String): Boolean {
        val work = store.transaction {
            owned(); state(space)
            val snapshot = journal(space).snapshot
            if (wallet.publicKey !in snapshot.members) return@transaction null
            val pending = snapshot.channels.mapNotNull { (channel, record) ->
                val data = channelState(space, channel)
                val hasDevice = record.devices.keys.any { it.split(':')[1] == wallet.publicKey }
                if (record.visibility != SpaceVisibility.PUBLIC && (!hasDevice || data.get("joined")?.asBoolean != true))
                    return@mapNotNull null
                val needsPage = data.get("attention_observed")?.asBoolean != true ||
                    (data.get("attention_cursor")?.asLong ?: 0) < (data.get("attention_goal")?.asLong ?: 0)
                val needsProjection = data.get("attention_projection_version")?.asLong != 1L ||
                    page("$space/channels/$channel/attention-dirty/", limit = 1).isNotEmpty()
                if (needsPage || needsProjection) channel to needsPage else null
            }.sortedBy { it.first }
            val key = "$space/attention-next-channel"
            val after = get(key)?.toString(Charsets.UTF_8)
            (pending.firstOrNull { after == null || it.first > after } ?: pending.firstOrNull())
                ?.also { put(key, it.first.toByteArray(Charsets.UTF_8)) }
        } ?: return false
        val (channel, needsPage) = work
        if (needsPage) refreshAttention(space, channel) else attention(space, channel)
        return true
    }

    private suspend fun readPage(scope: JsonObject, invitation: SpaceInvitation? = null,
        decorateRead: ((String, JsonObject) -> Unit)? = null): JsonObject {
        val requestId = id()
        fun request(kind: String, nonce: String? = null) = SaitoTransaction.create(wallet, JsonObject().apply {
            addProperty("module", "Spaces"); addProperty("request", kind); addProperty("version", 1)
            addProperty("request_id", requestId); add("scope", scope.deepCopy())
            nonce?.let {
                addProperty("nonce", it)
                invitation?.let { invite -> addProperty("invite_proof", invite.proof(wallet.publicKey, nonce)) }
                decorateRead?.invoke(it, this)
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
            val space = scope.text("space_id")
            if (scope.text("stream") != "message-report" && !scope.has("report_id")) {
                scope.get("channel_id")?.asString?.let { rememberArticleReadDenial(space, it) }
            }
            throw ReadDenied(space, problem)
        }
        require(result.text("request") == "page" && result.getAsJsonArray("items").size() <= 50)
        supportsMessageHistory = result.get("message_history")?.let { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && it.asBoolean } == true
        supportsMessageReactions = result.get("message_reactions")?.let { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && it.asBoolean } == true
        supportsMessagePins = result.get("message_pins")?.let { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && it.asBoolean } == true
        supportsAddressedInvitations = result.get("addressed_invitations")?.let {
            it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && it.asBoolean
        } == true
        supportsProtectedChat = result.get("protected_chat_v1")?.let {
            it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && it.asBoolean
        } == true
        supportsDeviceReplacement = result.get("device_replacement_v1")?.let {
            it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && it.asBoolean
        } == true
        supportsSpaceArticles = result.get("space_articles_v1")?.let {
            it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && it.asBoolean
        } == true
        supportsWriteRestrictions = result.get("space_write_restrictions_v1")?.let {
            it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && it.asBoolean
        } == true
        supportsFileStorage = result.get("space_file_storage_v1")?.let {
            it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && it.asBoolean
        } == true
        supportsVoiceRooms = result.get("space_voice_rooms_v1")?.let {
            it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && it.asBoolean
        } == true
        supportsModerationReports = result.get("space_moderation_reports_v1")?.let {
            it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && it.asBoolean
        } == true
        return result
    }

    private fun SpaceClientStore.Transaction.accept(space: String, envelope: String,
        pageControls: Map<String, SpaceJournal> = emptyMap(), controlJournal: SpaceJournal? = null,
        authenticated: AuthenticatedContent? = null, reactionReader: ((String) -> Set<String>?)? = null) {
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
                        "create-category", "edit-category" -> recordCategoryResult(state, original, ChangeStatus.ACCEPTED)
                        "create" -> {
                            if (original.author == wallet.publicKey) {
                                if (state.has("crypto")) {
                                    val keys = SpaceCrypto.restore(decode(state.text("crypto")))
                                    require(journal.snapshot.channels.values.single().devices == keys.bindings)
                                } else {
                                    // Public browsing verifies the full history before creating a
                                    // local anchor at the current public entry. That anchor may be
                                    // newer than a private genesis; replay grants no private keys.
                                    require(journal.snapshot.channels.values.single().visibility == SpaceVisibility.PUBLIC ||
                                        generalChannel(state) != journal.snapshot.generalChannelId &&
                                        state.getAsJsonObject("profile")?.let(SpaceProfile::fromJson)?.visibility == SpaceVisibility.PUBLIC)
                                }
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
                        "set-write-restriction" -> Unit // The verified journal owns the restriction projection.
                        "approve-file-storage" -> put("$space/file-storage/approvals/${original.id}", envelope.toByteArray(Charsets.UTF_8))
                        "activate-file-storage" -> Unit // The verified journal owns the active lease.
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
                        "set-visibility" -> {
                            recordVisibilityResult(state, original, ChangeStatus.ACCEPTED)
                            val profile = journal.snapshot.publicProfile ?: (state.getAsJsonObject("profile")?.let(SpaceProfile::fromJson)
                                ?: SpaceProfile(state.text("name"))).copy(visibility = SpaceVisibility.PRIVATE, listed = false)
                            state.add("profile", profile.json()); state.addProperty("name", profile.name)
                            state.remove("invitation"); state.remove("invitation_expires_at"); state.remove("desired_listed")
                            val channel = requireNotNull(journal.snapshot.entryChannelId)
                            if (channel != generalChannel(state) && get(channelStateKey(space, channel)) == null) {
                                val record = journal.snapshot.channels.getValue(channel)
                                put(channelStateKey(space, channel), bytes(JsonObject().apply {
                                    add("scope", SpaceCrypto.Scope(service, space, channel, record.generation).json())
                                    record.profile?.let { add("channel_profile", it.json()) }
                                    addProperty("content_cursor", 0)
                                }))
                            }
                        }
                        "dissolve" -> {
                            recordDissolutionResult(state, original, ChangeStatus.ACCEPTED)
                            state.remove("starter_channels")
                            state.remove("invitation"); state.remove("invitation_expires_at"); state.remove("desired_listed")
                            state.remove("joining_invitation"); state.remove("ownership_completion_required")
                            journal.snapshot.publicProfile?.let { state.add("profile", it.json()) }
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
                            val replaced = join.body.get("replace_device")?.takeIf { join.body.text("kind") == "replace-device" }?.asString
                            val pointer = get("$space/operation/${original.id}")?.toString(Charsets.UTF_8)
                            val pending = pointer?.let { get(it) }?.let(::json)
                            val next = when {
                                pending?.has("candidate") == true -> SpaceCrypto.restore(decode(pending.text("candidate")))
                                replaced != null && join.author == wallet.publicKey -> {
                                    if (target.get("replacement_request")?.asString == join.id) {
                                        val device = SpaceCrypto.restore(decode(target.text("joining_crypto")))
                                        val joined = device.join(scope, original.body.text("welcome"), channel.devices)
                                        if (current?.scope == scope) joined.withRetainedHistory(current) else joined
                                    } else {
                                        if (current?.credential == replaced || target.has("replacement_request")) {
                                            target.addProperty("joined", false)
                                            target.addProperty("device_replaced", true)
                                            target.remove("replacement_request")
                                            target.remove("joining_crypto")
                                            putChannelState(space, channelId, target)
                                        }
                                        null
                                    }
                                }
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
                                target.remove("replacement_request"); target.remove("device_replaced"); target.remove("replacement_rejected")
                                target.remove("archive_only")
                                if (join.author == wallet.publicKey && join.body.text("kind") == "join") {
                                    state.addProperty("joined", true)
                                    state.remove("joining_invitation")
                                }
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
                    state.addProperty("access_rotation_required", !journal.snapshot.dissolved && journal.snapshot.channels.values.any { it.retiredDevices.isNotEmpty() })
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
                if (original.body.text("kind") == "set-visibility") recordVisibilityResult(state, original, ChangeStatus.REJECTED)
                if (original.body.text("kind") == "dissolve") recordDissolutionResult(state, original, ChangeStatus.REJECTED)
                if (original.body.text("kind") in setOf("leave", "remove-member", "ban-member", "unban-member")) recordMembershipResult(state, original, ChangeStatus.REJECTED)
                if (original.body.text("kind") == "edit-channel") recordChannelEditResult(space, state, original, ChangeStatus.REJECTED)
                if (original.body.text("kind") in setOf("create-category", "edit-category")) recordCategoryResult(state, original, ChangeStatus.REJECTED)
                if (original.body.text("kind") in OwnershipAction.entries.map { it.kind }) recordOwnershipResult(state, original, ChangeStatus.REJECTED)
                if (original.body.text("kind") == "rotate-access") state.addProperty("access_rotation_rejected_head", original.body.text("control_id"))
                if (original.body.text("kind") == "complete-owner") state.addProperty("ownership_completion_rejected_head", original.body.text("control_id"))
                if (original.body.text("kind") == "replace-device") {
                    val channelId = original.body.text("channel_id")
                    val target = channelState(space, channelId, state)
                    if (target.get("replacement_request")?.asString == original.id) {
                        target.remove("replacement_request")
                        target.remove("joining_crypto")
                        target.addProperty("replacement_rejected", true)
                        putChannelState(space, channelId, target)
                    }
                }
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
                messageChangeKey(space, original.body)?.let { key ->
                    if (get(key)?.toString(Charsets.UTF_8) == pointer) put(key, rejectedKey.toByteArray(Charsets.UTF_8))
                }
                if (original.body.get("content_kind")?.asString == "message-pin") {
                    val key = pinPendingKey(space, original.body.text("channel_id"), original.body.text("target_message_id"))
                    if (get(key)?.toString(Charsets.UTF_8) == pointer) put(key, rejectedKey.toByteArray(Charsets.UTF_8))
                }
                messageChannel(original.body)?.let { channel ->
                    put(messageIndex(space, channel, "rejected", messageThread(original.body)) + original.id, rejectedKey.toByteArray(Charsets.UTF_8))
                }
                put(stateKey(space), bytes(state))
            }
            "join" -> {
                require(original.author == wallet.publicKey && original.body.text("kind") in setOf("join", "join-channel", "replace-device"))
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
                historic.verifyContent(body.text("event"), receipt.createdAt)
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
                    if (original.body.get("content_kind")?.asString == "message-pin") {
                        val pinKey = messagePinKey(space, channel, original.body.text("target_message_id"))
                        val previous = get(pinKey)?.let(::json)
                        if (previous == null || previous.number("position") < body.number("position")) put(pinKey, bytes(body))
                        put(pinTargetsPrefix(space, channel) + original.body.text("target_message_id"), byteArrayOf(1))
                    }
                    if (original.body.text("kind") in setOf("content", "public-content") && original.body.text("content_kind") == "message-edit") {
                        val editKey = messageEditKey(space, channel, original.body.text("target_message_id"), original.author)
                        val prior = get(editKey)?.let(::json)
                        if (prior == null || prior.number("position") < body.number("position")) put(editKey, bytes(body))
                    }
                    if (original.body.text("kind") in setOf("content", "public-content") && original.body.text("content_kind") == "message-delete") {
                        val target = original.body.text("target_message_id")
                        val deletionKey = if (historic.hasCurrentAdministratorIntent(original.author, original.body.text("control_id")))
                            messageModerationKey(space, channel, target) else messageDeletionKey(space, channel, target, original.author)
                        val prior = get(deletionKey)?.let(::json)
                        if (prior == null || prior.number("position") < body.number("position")) put(deletionKey, bytes(body))
                    }
                    if (original.body.text("kind") in setOf("content", "public-content") && original.body.text("content_kind") in ROOT_CONTENT_KINDS)
                        contentState.addProperty("last_content", maxOf(contentState.get("last_content")?.asLong ?: 0, body.number("position")))
                    val kind = original.body.get("content_kind")?.asString
                    if (kind in setOf("message", "thread-reply", "message-edit", "message-delete")) {
                        val message = if (kind == "message" || kind == "thread-reply") original.id else original.body.text("target_message_id")
                        put("$space/channels/$channel/attention-dirty/$message", byteArrayOf(1))
                        val end = Math.addExact(body.number("position"), 1)
                        contentState.addProperty("attention_goal", maxOf(contentState.get("attention_goal")?.asLong ?: 0, end))
                    }
                    putChannelState(space, channel, contentState)
                }
                if (original.body.get("content_kind")?.asString == "message-pin") {
                    val progress = channelState(space, channel)
                    progress.addProperty("pin_goal", maxOf(progress.get("pin_goal")?.asLong ?: 0, Math.addExact(body.number("position"), 1)))
                    putChannelState(space, channel, progress)
                }
                if (original.body.get("content_kind")?.asString in setOf("message-pin", "message-delete"))
                    reconcilePinTarget(space, channel, original.body.text("target_message_id"))
                if (original.body.get("content_kind")?.asString == "message-reaction") reconcileReaction(space, channel, body, reactionReader)
                indexConfirmedMessage(space, channel, body, original)
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
            put(messageIndex(space, channel, "pending", messageThread(outbound.body)) + positionKey(sequence), key.toByteArray(Charsets.UTF_8))
        }
        put("$space/operation/${original.id}", key.toByteArray(Charsets.UTF_8))
        messageChangeKey(space, outbound.body)?.let { put(it, key.toByteArray(Charsets.UTF_8)) }
        if (outbound.body.get("content_kind")?.asString == "message-pin") {
            put(pinPendingKey(space, outbound.body.text("channel_id"), outbound.body.text("target_message_id")), key.toByteArray(Charsets.UTF_8))
            put(pinTargetsPrefix(space, outbound.body.text("channel_id")) + outbound.body.text("target_message_id"), byteArrayOf(1))
            reconcilePinTarget(space, outbound.body.text("channel_id"), outbound.body.text("target_message_id"))
        }
        if (outbound.body.get("content_kind")?.asString == "message-reaction")
            put(reactionPendingKey(space, outbound.body.text("channel_id"), outbound.body.text("target_message_id")), key.toByteArray(Charsets.UTF_8))
        state.addProperty("next_outbox", Math.addExact(sequence, 1))
        put(stateKey(space), bytes(state))
        put("communities/$space", space.toByteArray(Charsets.UTF_8))
    }
    private fun messageChannel(body: JsonObject): String? {
        if (body.text("kind") !in setOf("content", "public-content") || body.text("content_kind") !in TIMELINE_CONTENT_KINDS) return null
        return body.text("channel_id").also { require(it.matches(ID)) }
    }
    private fun messageThread(body: JsonObject): String? =
        if (body.get("content_kind")?.asString == "thread-reply") body.text("target_message_id") else null
    private fun conversationPrefix(space: String, channel: String, thread: String?) =
        if (thread == null) "$space/channels/$channel/" else "$space/channels/$channel/threads/$thread/"
    private fun messageIndex(space: String, channel: String, disposition: String, thread: String? = null) = conversationPrefix(space, channel, thread) + "$disposition/"
    private fun confirmedMessageIndex(space: String, channel: String, thread: String? = null) = conversationPrefix(space, channel, thread) + "messages/"
    private fun reverseMessageIndex(space: String, channel: String, thread: String? = null) = conversationPrefix(space, channel, thread) + "messages-reverse/"
    private fun threadDraftKey(space: String, channel: String, parent: String) = conversationPrefix(space, channel, parent) + "draft"
    private fun replyCountKey(space: String, channel: String, parent: String) = "$space/channels/$channel/reply-counts/$parent"
    private fun SpaceClientStore.Transaction.conversationState(space: String, channel: String, thread: String?): JsonObject =
        if (thread == null) channelState(space, channel) else get(conversationPrefix(space, channel, thread) + "state")?.let(::json) ?: JsonObject().apply {
            addProperty("content_cursor", 0); addProperty("message_history", true)
        }
    private fun SpaceClientStore.Transaction.putConversationState(space: String, channel: String, thread: String?, data: JsonObject) {
        if (thread == null) putChannelState(space, channel, data) else put(conversationPrefix(space, channel, thread) + "state", bytes(data))
    }
    private fun SpaceClientStore.Transaction.indexConfirmedMessage(space: String, channel: String, receipt: JsonObject,
        original: SignedSaitoMessage = SaitoEnvelope.decode(receipt.text("event"))) {
        if (original.body.get("content_kind")?.asString !in TIMELINE_CONTENT_KINDS) return
        val thread = messageThread(original.body)
        val position = receipt.number("position")
        val pointer = (contentPrefix(space, channel) + positionKey(position)).toByteArray(Charsets.UTF_8)
        val idKey = messageIdKey(space, channel, original.id)
        if (get(idKey)?.contentEquals(pointer) == true) return
        put(messagePositionIdKey(space, channel, position), original.id.toByteArray(Charsets.UTF_8))
        put(idKey, pointer)
        put(confirmedMessageIndex(space, channel, thread) + positionKey(position), pointer)
        put(reverseMessageIndex(space, channel, thread) + positionKey(Long.MAX_VALUE - position), pointer)
    }
    private fun SpaceClientStore.Transaction.latestMessageStart(space: String, channel: String, limit: Int, thread: String? = null): Long =
        page(reverseMessageIndex(space, channel, thread), limit = limit).lastOrNull()?.let {
            Long.MAX_VALUE - it.key.substringAfterLast('/').toLong()
        } ?: 0
    private fun reactionActorsPrefix(space: String, channel: String, message: String) = "$space/channels/$channel/reaction-actors/$message/"
    private fun reactionEmojiKey(emoji: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(emoji.toByteArray(Charsets.UTF_8))
    private fun reactionAppliedPrefix(space: String, channel: String, message: String) = "$space/channels/$channel/reaction-applied/$message/"
    private fun reactionCountsPrefix(space: String, channel: String, message: String) = "$space/channels/$channel/reaction-counts/$message/"
    private fun reactionInvalidPrefix(space: String, channel: String, message: String) = "$space/channels/$channel/reaction-invalid/$message/"
    private fun messagePositionIdKey(space: String, channel: String, position: Long) = "$space/channels/$channel/message-position-ids/${positionKey(position)}"
    private fun reactionPendingKey(space: String, channel: String, message: String) = "$space/channels/$channel/reaction-pending/$message"
    private fun ownReactionVersionKey(space: String, channel: String, message: String) = "$space/channels/$channel/own-reaction-through/$message"
    private fun reactionSummaryKey(space: String, channel: String, message: String) = "$space/channels/$channel/reaction-summary/$message"
    private fun messagePinKey(space: String, channel: String, message: String) = "$space/channels/$channel/message-pin/$message"
    private fun pinPendingKey(space: String, channel: String, message: String) = "$space/channels/$channel/pin-pending/$message"
    private fun activePinsPrefix(space: String, channel: String) = "$space/channels/$channel/active-pins/"
    private fun unavailablePinsPrefix(space: String, channel: String) = "$space/channels/$channel/unavailable-pins/"
    private fun pinChoiceKey(space: String, channel: String, message: String) = "$space/channels/$channel/pin-choice/$message"
    private fun pinTargetsPrefix(space: String, channel: String) = "$space/channels/$channel/pin-targets/"
    private fun messageChangeKey(space: String, channel: String, message: String) = "$space/channels/$channel/message-change/$message"
    private fun messageChangeKey(space: String, body: JsonObject): String? =
        if (body.get("kind")?.asString in setOf("content", "public-content") && body.get("content_kind")?.asString in setOf("message-edit", "message-delete"))
            messageChangeKey(space, body.text("channel_id"), body.text("target_message_id")) else null
    private fun messageIdKey(space: String, channel: String, message: String) = "$space/channels/$channel/message-ids/$message"
    private fun messageDeletionKey(space: String, channel: String, message: String, author: String) =
        "$space/channels/$channel/message-deletions/$message/author/$author"
    private fun messageModerationKey(space: String, channel: String, message: String) =
        "$space/channels/$channel/message-deletions/$message/staff"
    private fun SpaceClientStore.Transaction.messageDeletion(space: String, channel: String, message: String, author: String): JsonObject? =
        messageDeletionReceipt(space, channel, message, author)?.let { SaitoEnvelope.decode(it.text("event")).body }
    private fun SpaceClientStore.Transaction.messageDeletionReceipt(space: String, channel: String, message: String, author: String): JsonObject? =
        listOf(messageDeletionKey(space, channel, message, author), messageModerationKey(space, channel, message))
            .mapNotNull { get(it)?.let(::json) }.maxByOrNull { it.number("position") }
    private fun messageEditKey(space: String, channel: String, message: String, author: String) =
        "$space/channels/$channel/message-edits/$message/$author"
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
                        put(messageIndex(space, channel, disposition, messageThread(original.body)) + row.key.substringAfterLast('/'), row.key.toByteArray(Charsets.UTF_8))
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
                require(channel == journal(space).snapshot.generalChannelId)
                SpaceChannelKind.CHAT // The original General-only protocol.
            } else {
                val initial = decode(record.initialMetadata).toString(Charsets.UTF_8)
                val original = SaitoEnvelope.decode(initial)
                require(original.body.text("content_kind") == "channel-profile")
                // A later audience change can move the current display metadata.
                // The immutable kind still belongs to its original signed group.
                val keyState = channelState(space, original.body.text("channel_id"), parent)
                // Retained history can describe an authorized channel while
                // readmission is pending. It does not authorize new editing.
                if (!keyState.has("crypto")) return null
                val keys = SpaceCrypto.restore(decode(keyState.text("crypto")))
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
            val keyState = (record.metadataChannelId?.let { channelState(space, it, parent) } ?: if (record.allowedMembers != null) data else parent)
                .takeIf { it.get("joined")?.asBoolean == true } ?: return null
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
        val ROOT_CONTENT_KINDS = setOf("message", "article", "file")
        val TIMELINE_CONTENT_KINDS = ROOT_CONTENT_KINDS + "thread-reply"
        val CONTROL_KINDS = setOf("create", "invite", "admit", "set-listed", "set-visibility", "dissolve", "join-public", "revoke-invite", "create-channel", "create-category", "edit-category",
            "archive-channel", "set-role", "edit-channel", "revoke-channel-access", "grant-channel-access", "rotate-access", "leave", "remove-member", "ban-member", "unban-member", "propose-owner", "accept-owner", "cancel-owner", "decline-owner", "complete-owner", "set-write-restriction", "approve-file-storage", "activate-file-storage")
        const val DIRECTORY_CACHE_LIMIT = 200
        val ID = Regex("[0-9a-f]{64}")
        val GSON = GsonBuilder().setStrictness(Strictness.STRICT).create()
    }
}
