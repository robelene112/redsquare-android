package nl.davidengelen.redsquare.spaces

import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.SignedSaitoMessage
import java.util.Base64
import java.util.Collections

enum class SpaceRole { OWNER, ADMIN, MEMBER }
data class SpaceInvitationRecord(val key: String, val expiresAt: Long, val channelId: String, val rulesToken: String = "",
    val recipient: String? = null) {
    fun accepts(wallet: String): Boolean = recipient == null || recipient == wallet
}
data class SpaceChannelRecord(val epoch: Long, val devices: Map<String, String>, val generation: String = "0".repeat(64), val epochStartedAt: Long = 0,
    val visibility: SpaceVisibility = SpaceVisibility.PRIVATE, val profile: SpaceChannelProfile? = null,
    val metadata: String? = null, val adminPostsOnly: Boolean = false, val archived: Boolean = false,
    val contributionStartedAt: Long = 0, val allowedMembers: Set<String>? = null,
    val accessStartedAt: Map<String, Long> = emptyMap(), val kind: SpaceChannelKind? = null,
    val initialMetadata: String? = metadata, val retiredDevices: Set<String> = emptySet(), val categoryId: String? = null, val metadataChannelId: String? = null)
data class SpaceOwnershipOffer(val id: String, val member: String, val proposedAt: Long,
    val acceptedId: String? = null, val acceptedAt: Long? = null)

data class ProtectedChat(val directPeer: String? = null) {
    init { require(directPeer == null || SaitoWallet.isPublicKey(directPeer)) }
    internal fun acceptsRecipient(recipient: String?) = recipient != null && (directPeer == null || directPeer == recipient)
}

data class SpaceJoinRequest(val envelope: String, val id: String, val actor: String,
    val operationId: String, val channelId: String, val keyPackage: SpaceCrypto.BoundPackage,
    val replacedDevice: String? = null)

data class SpaceSnapshot(val owner: String? = null, val members: Map<String, SpaceRole> = emptyMap(),
    val controlId: String? = null, val nextPosition: Long = 0,
    val invitations: Map<String, SpaceInvitationRecord> = emptyMap(),
    val channels: Map<String, SpaceChannelRecord> = emptyMap(),
    val visibility: SpaceVisibility = SpaceVisibility.PRIVATE, val publicProfile: SpaceProfile? = null,
    val administratorStartedAt: Map<String, Long> = emptyMap(), val generalChannelId: String? = null,
    val membershipEndedAt: Map<String, Long> = emptyMap(), val bannedMembers: Set<String> = emptySet(),
    val membershipReadThrough: Map<String, Long> = emptyMap(), val ownershipOffer: SpaceOwnershipOffer? = null,
    val categories: Map<String, SpaceCategoryRecord> = emptyMap(), val entryChannelId: String? = generalChannelId, val dissolved: Boolean = false, val protectedChat: ProtectedChat? = null)

/** Authenticated control history. Content pages have their own channel cursors. */
class SpaceJournal(private val spaceId: String, private val serviceKey: String) {
    private var current = SpaceSnapshot()
    private val applied = mutableMapOf<Long, String>()
    private val operations = mutableMapOf<String, String>()
    private val controlPositions = mutableMapOf<String, Long>()
    private var ownershipStartedAt = 0L
    private data class HistoryDelivery(val author: String, val channel: String, val generation: String,
        val epoch: Long, val recipient: String, val control: String)
    private val historyDeliveries = mutableMapOf<String, HistoryDelivery>()

    init { require(spaceId.matches(ID) && SaitoWallet.isPublicKey(serviceKey)) }

    val snapshot: SpaceSnapshot get() = current

    fun canAccessChannel(actor: String, channel: String): Boolean {
        val record = current.channels[channel] ?: return false
        return record.visibility == SpaceVisibility.PUBLIC || hasChannelAudience(current, actor, record) ||
            hasConsentedHandoverAccess(current, actor)
    }

    /** Preparing handover keys grants reading, not contribution or staff powers. */
    fun canContributeChannel(actor: String, channel: String): Boolean =
        !current.dissolved && current.channels[channel]?.let {
            hasChannelAudience(current, actor, it) && (current.protectedChat == null || it.devices.size >= 2)
        } == true

    private fun hasChannelAudience(snapshot: SpaceSnapshot, actor: String, channel: SpaceChannelRecord): Boolean {
        val role = snapshot.members[actor] ?: return false
        return channel.visibility == SpaceVisibility.PUBLIC || channel.allowedMembers == null || actor in channel.allowedMembers ||
            role in setOf(SpaceRole.OWNER, SpaceRole.ADMIN)
    }

    private fun hasConsentedHandoverAccess(snapshot: SpaceSnapshot, actor: String): Boolean =
        actor in snapshot.members && snapshot.ownershipOffer?.let {
            it.member == actor && it.acceptedId != null && it.acceptedAt != null
        } == true

    /** Initial legacy requests remain valid, but ending membership requires a
     * newly signed intent acknowledging that departure or a later control. */
    fun hasCurrentMembershipIntent(actor: String, controlId: String?): Boolean {
        val endedAt = current.membershipEndedAt[actor]
        return !current.dissolved && actor !in current.bannedMembers && if (controlId == null) endedAt == null else
            controlPositions[controlId]?.let { it >= (endedAt ?: 0L) } == true
    }

    /** Regranting staff powers cannot revive an earlier moderation decision. */
    fun hasCurrentAdministratorIntent(actor: String, controlId: String): Boolean {
        val reference = controlPositions[controlId] ?: return false
        val role = current.members[actor]
        if (current.dissolved || role !in setOf(SpaceRole.OWNER, SpaceRole.ADMIN)) return false
        val startedAt = if (role == SpaceRole.OWNER) ownershipStartedAt else current.administratorStartedAt[actor] ?: 0L
        return reference >= maxOf(startedAt, current.membershipEndedAt[actor] ?: 0L)
    }

    /** A policy regrant cannot revive a request from an earlier access term. */
    fun canJoinChannel(actor: String, channel: String, controlId: String): Boolean {
        val record = current.channels[channel] ?: return false
        val position = controlPositions[controlId] ?: return false
        if (current.dissolved || record.visibility != SpaceVisibility.PRIVATE || actor !in current.members || actor in current.bannedMembers || !canAccessChannel(actor, channel) ||
            record.devices.keys.any { credentialWallet(it) == actor }) return false
        val startedAt = if (record.allowedMembers == null || actor in record.allowedMembers)
            record.accessStartedAt[actor] ?: 0L else if (current.members[actor] == SpaceRole.ADMIN)
            current.administratorStartedAt[actor] ?: 0L else 0L
        val handoverStart = if (!hasChannelAudience(current, actor, record) && hasConsentedHandoverAccess(current, actor))
            requireNotNull(current.ownershipOffer?.acceptedAt) else 0L
        return position >= maxOf(startedAt, current.membershipEndedAt[actor] ?: 0L, handoverStart)
    }

    fun canApproveDeviceRequests(actor: String): Boolean =
        current.members[actor] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN) ||
            current.protectedChat != null && actor in current.members

    private fun canReplaceDevice(actor: String, channel: String, credential: String, controlId: String): Boolean {
        val record = current.channels[channel] ?: return false
        val position = controlPositions[controlId] ?: return false
        return !current.dissolved && actor in current.members && actor !in current.bannedMembers &&
            record.visibility == SpaceVisibility.PRIVATE && canAccessChannel(actor, channel) &&
            credential in record.devices && credential !in record.retiredDevices && credentialWallet(credential) == actor &&
            position >= maxOf(current.membershipEndedAt[actor] ?: 0L, record.accessStartedAt[actor] ?: 0L)
    }

    fun pendingJoinIsCurrent(join: SignedSaitoMessage, now: Long): Boolean = when (join.body.text("kind")) {
        "join-channel" -> canJoinChannel(join.author, join.body.text("channel_id"), join.body.text("control_id"))
        "replace-device" -> canReplaceDevice(join.author, join.body.text("channel_id"),
            join.body.text("replace_device"), join.body.text("control_id"))
        else -> current.invitations[join.body.text("invite_id")]?.let { invitation ->
            join.author !in current.members && invitation.expiresAt > now &&
                hasCurrentMembershipIntent(join.author, join.body.get("membership_control")?.asString)
        } == true
    }

    fun receive(receipt: String): SpaceSnapshot {
        val signedReceipt = SaitoEnvelope.decode(receipt)
        require(signedReceipt.author == serviceKey) { "Spaces receipt has a different service identity" }
        val received = signedReceipt.body
        header(received, "receipt")
        require(received.text("space_id") == spaceId && received.text("stream") == "control")
        val position = received.number("position")
        val event = SaitoEnvelope.decode(received.text("event"))
        header(event.body, "event")
        require(event.body.text("space_id") == spaceId && event.recipients == listOf(event.author, serviceKey).distinct())
        require(event.body.text("operation_id").matches(ID))
        applied[position]?.let { id ->
            require(id == event.id) { "Spaces service returned conflicting history" }
            return current
        }
        require(position == current.nextPosition) { "Spaces history has a missing predecessor" }
        val operation = event.body.text("operation_id")
        require(operation !in operations) { "Spaces operation already has an accepted result" }
        require(!current.dissolved) { "This Space has been dissolved" }
        var admittedJoin: SpaceJoinRequest? = null
        var admittedHistory: Map<String, HistoryDelivery> = emptyMap()
        val next = when (event.body.text("kind")) {
            "create" -> {
                require(current.owner == null && position == 0L && !event.body.has("control_id"))
                require(event.body.text("visibility") in setOf("public", "private"))
                val visibility = if (event.body.text("visibility") == "public") SpaceVisibility.PUBLIC else SpaceVisibility.PRIVATE
                val profile = event.body.getAsJsonObject("public_profile")?.let(SpaceProfile::fromJson)
                require(profile == null || visibility == SpaceVisibility.PUBLIC && profile.visibility == visibility)
                require(event.body.text("channel_id").matches(ID) && event.body.number("epoch") == 0L)
                val channelVisibility = when (event.body.get("channel_visibility")?.asString) {
                    null, "private" -> SpaceVisibility.PRIVATE
                    "public" -> SpaceVisibility.PUBLIC.also { require(visibility == it) }
                    else -> throw IllegalArgumentException("Unknown channel visibility")
                }
                val metadata = Base64.getDecoder().decode(event.body.text("metadata"))
                require(metadata.size in 29..65_536) { "Invalid Spaces metadata length" }
                val devices = devices(event.body)
                require(devices.size == 1 && credentialWallet(devices.keys.single()) == event.author)
                val channelProfile = if (channelVisibility == SpaceVisibility.PUBLIC)
                    event.body.getAsJsonObject("channel_profile")?.let(SpaceChannelProfile::fromJson) ?: SpaceChannelProfile("general") else null
                val channelMetadata = event.body.get("channel_metadata")?.let { event.body.text("channel_metadata").also { encoded ->
                    require(channelVisibility == SpaceVisibility.PRIVATE && Base64.getDecoder().decode(encoded).size in 29..65_536)
                } }
                require(channelVisibility != SpaceVisibility.PRIVATE || !event.body.has("channel_profile"))
                val channelKind = declaredChannelKind(event.body) ?: channelProfile?.kind
                require(channelProfile == null || channelKind == channelProfile.kind)
                val adminPostsOnly = event.body.get("admin_posts_only")?.let {
                    require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean); it.asBoolean
                } ?: (channelProfile?.kind == SpaceChannelKind.ANNOUNCEMENT)
                require(channelKind == null || adminPostsOnly == (channelKind == SpaceChannelKind.ANNOUNCEMENT))
                val protectedChat = event.body.get("protected_chat")?.let { value ->
                    require(value.isJsonObject)
                    val details = value.asJsonObject
                    require(details.keySet().all { it == "direct_peer" })
                    val peer = details.get("direct_peer")?.let { details.text("direct_peer") }
                    require(visibility == SpaceVisibility.PRIVATE && channelVisibility == SpaceVisibility.PRIVATE &&
                        channelKind == SpaceChannelKind.CHAT && peer != event.author)
                    ProtectedChat(peer)
                }
                val created = SpaceSnapshot(event.author, mapOf(event.author to SpaceRole.OWNER), event.id, 1,
                    channels = mapOf(event.body.text("channel_id") to SpaceChannelRecord(0, devices,
                        event.body.takeIf { it.has("key_generation") }?.text("key_generation")?.also { require(it.matches(ID)) }
                            ?: "0".repeat(64), visibility = channelVisibility,
                        profile = channelProfile, metadata = channelMetadata, adminPostsOnly = adminPostsOnly, kind = channelKind, metadataChannelId = event.body.text("channel_id"))), visibility = visibility, publicProfile = profile, generalChannelId = event.body.text("channel_id"), protectedChat = protectedChat)
                categorizedChannel(event, created, event.body.text("channel_id"), created.channels.getValue(event.body.text("channel_id")))
            }
            "create-category", "edit-category" -> {
                require(current.protectedChat == null) { "Protected chats have one private conversation" }
                require(current.members[event.author] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN))
                require(event.body.text("control_id") == current.controlId)
                val category = event.body.text("category_id").also { require(it.matches(ID)) }
                require((category in current.categories) == (event.body.text("kind") == "edit-category"))
                val record = categoryRecord(event, event.body, current, category, current.categories[category])
                current.copy(controlId = event.id, nextPosition = Math.addExact(position, 1),
                    categories = Collections.unmodifiableMap(current.categories + (category to record)))
            }
            "create-channel" -> {
                require(current.protectedChat == null) { "Protected chats have one private conversation" }
                require(current.members[event.author] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN))
                require(event.body.text("control_id") == current.controlId)
                val allowed = event.body.get("allowed_members")?.let { value ->
                    require(value.isJsonArray)
                    val values = value.asJsonArray
                    val members = values.map { value ->
                        require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
                        value.asString.also { require(it in current.members) }
                    }.toSet()
                    require(members.size == values.size())
                    Collections.unmodifiableSet(members)
                }
                val visibility = when (event.body.text("visibility")) {
                    "public" -> SpaceVisibility.PUBLIC.also { require(current.visibility == it && allowed == null) }
                    "private" -> SpaceVisibility.PRIVATE.also { require(current.visibility == it || allowed != null) }
                    else -> throw IllegalArgumentException("Unknown channel visibility")
                }
                require(allowed == null || visibility == SpaceVisibility.PRIVATE)
                if (visibility == SpaceVisibility.PRIVATE && allowed == null) {
                    val entry = current.channels.getValue(requireNotNull(current.entryChannelId))
                    require(entry.visibility == SpaceVisibility.PRIVATE && entry.retiredDevices.isEmpty())
                }
                val channel = event.body.text("channel_id").also { require(it.matches(ID) && it !in current.channels) }
                val generation = event.body.text("key_generation").also { require(it.matches(ID)) }
                require(event.body.number("epoch") == 0L)
                val profile = if (visibility == SpaceVisibility.PUBLIC)
                    SpaceChannelProfile.fromJson(event.body.getAsJsonObject("channel_profile")) else null

                val metadata = if (visibility == SpaceVisibility.PRIVATE) event.body.text("metadata").also {
                    require(!event.body.has("channel_profile") && Base64.getDecoder().decode(it).size in 29..65_536)
                } else null
                if (allowed != null) verifyChannelMetadata(requireNotNull(metadata), event,
                    SpaceCrypto.Scope(serviceKey, spaceId, channel, generation), 0)
                val bindings = if (visibility == SpaceVisibility.PRIVATE) devices(event.body).also {
                    require(it.size == 1 && credentialWallet(it.keys.single()) == event.author)
                } else emptyMap()
                val channelKind = declaredChannelKind(event.body) ?: profile?.kind
                require(profile == null || channelKind == profile.kind)
                val adminPostsOnly = event.body.get("admin_posts_only")?.let {
                    require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean); it.asBoolean
                } ?: (profile?.kind == SpaceChannelKind.ANNOUNCEMENT)
                require(channelKind == null || adminPostsOnly == (channelKind == SpaceChannelKind.ANNOUNCEMENT))
                val created = current.copy(controlId = event.id, nextPosition = Math.addExact(position, 1),
                    entryChannelId = if (current.visibility == SpaceVisibility.PUBLIC && visibility == SpaceVisibility.PUBLIC &&
                        current.channels[current.entryChannelId]?.visibility != SpaceVisibility.PUBLIC) channel else current.entryChannelId,
                    channels = Collections.unmodifiableMap(current.channels + (channel to SpaceChannelRecord(0, bindings,
                        generation, position, visibility, profile, metadata, adminPostsOnly, allowedMembers = allowed, kind = channelKind,
                        accessStartedAt = Collections.unmodifiableMap(allowed.orEmpty().associateWith { position }), metadataChannelId = if (visibility == SpaceVisibility.PRIVATE)
                            if (allowed != null) channel else requireNotNull(current.entryChannelId) else null))))
                categorizedChannel(event, created, channel, created.channels.getValue(channel))
            }
            "grant-channel-access" -> {
                require(current.members[event.author] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN))
                require(event.body.text("control_id") == current.controlId)
                val channel = event.body.text("channel_id")
                val old = requireNotNull(current.channels[channel])
                require(old.visibility == SpaceVisibility.PRIVATE && old.allowedMembers != null)
                val member = event.body.text("member")
                require(current.members[member] == SpaceRole.MEMBER && member !in old.allowedMembers)
                current.copy(controlId = event.id, nextPosition = Math.addExact(position, 1),
                    channels = Collections.unmodifiableMap(current.channels + (channel to old.copy(
                        allowedMembers = Collections.unmodifiableSet(old.allowedMembers + member),
                        accessStartedAt = Collections.unmodifiableMap(old.accessStartedAt + (member to position))))))
            }
            "revoke-channel-access" -> {
                require(current.members[event.author] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN))
                require(event.body.text("control_id") == current.controlId)
                val channel = event.body.text("channel_id")
                val old = requireNotNull(current.channels[channel])
                require(old.visibility == SpaceVisibility.PRIVATE && old.allowedMembers != null)
                require(old.retiredDevices.isEmpty()) {
                    "Channel access keys need updating before publishing replacement metadata"
                }
                val member = event.body.text("member")
                require(current.members[member] == SpaceRole.MEMBER && member in old.allowedMembers)
                require(old.devices.keys.any { credentialWallet(it) == event.author })
                val removed = old.devices.keys.filter { credentialWallet(it) == member }
                require(removed.size <= 1)
                val epoch = Math.addExact(old.epoch, if (removed.isEmpty()) 0 else 1)
                require(event.body.number("epoch") == epoch)
                val bindings = devices(event.body)
                require(bindings == old.devices - removed.toSet())
                if (removed.isEmpty()) require(!event.body.has("commit"))
                else require(Base64.getDecoder().decode(event.body.text("commit")).size in 1..1_048_576)
                val metadata = event.body.text("channel_metadata")
                require(Base64.getDecoder().decode(metadata).size in 29..65_536)
                verifyChannelMetadata(metadata, event, SpaceCrypto.Scope(serviceKey, spaceId, channel, old.generation), epoch)
                current.copy(controlId = event.id, nextPosition = Math.addExact(position, 1),
                    channels = Collections.unmodifiableMap(current.channels + (channel to old.copy(epoch = epoch, devices = bindings,
                        retiredDevices = Collections.unmodifiableSet(old.retiredDevices.intersect(bindings.keys)),
                        epochStartedAt = if (removed.isEmpty()) old.epochStartedAt else position, metadata = metadata,
                        allowedMembers = Collections.unmodifiableSet(old.allowedMembers - member),
                        accessStartedAt = Collections.unmodifiableMap(old.accessStartedAt - member)))))
            }
            "edit-channel" -> {
                require(current.members[event.author] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN))
                require(event.body.text("control_id") == current.controlId)
                val channel = event.body.text("channel_id")
                val old = requireNotNull(current.channels[channel])
                val edited = if (old.visibility == SpaceVisibility.PUBLIC) {
                    require(current.visibility == SpaceVisibility.PUBLIC) { "Public archives stay read-only while the Space is private" }
                    require(!event.body.has("channel_metadata"))
                    val value = event.body.get("channel_profile").also { require(it != null && it.isJsonObject) }
                    val profile = SpaceChannelProfile.fromJson(value.asJsonObject)
                    require(profile.kind == requireNotNull(old.profile).kind) { "Metadata editing cannot change the channel kind" }
                    old.copy(profile = profile)
                } else {
                    require(!event.body.has("channel_profile") && !event.body.has("channel_kind"))
                    val metadataChannel = old.metadataGroupId(channel, current)
                    val metadataGroup = requireNotNull(current.channels[metadataChannel])
                    require(metadataGroup.retiredDevices.isEmpty()) { "Channel access keys need updating" }
                    require(metadataGroup.visibility == SpaceVisibility.PRIVATE && metadataGroup.devices.keys.any { credentialWallet(it) == event.author }) {
                        "Channel metadata requires a current holder of its metadata keys"
                    }
                    val metadata = event.body.text("channel_metadata")
                    verifyChannelMetadata(metadata, event, SpaceCrypto.Scope(serviceKey, spaceId, metadataChannel, metadataGroup.generation),
                        metadataGroup.epoch, "channel-profile-edit", channel, allowImplicitTarget = old.allowedMembers != null)
                    old.copy(metadata = metadata)
                }
                categorizedChannel(event, current.copy(controlId = event.id, nextPosition = Math.addExact(position, 1)), channel, edited)
            }
            "rotate-access" -> {
                require(event.body.text("control_id") == current.controlId)
                val channel = event.body.text("channel_id")
                val old = requireNotNull(current.channels[channel])
                require(old.visibility == SpaceVisibility.PRIVATE && canAccessChannel(event.author, channel))
                val removed = event.body.text("removed_device")
                require(removed in old.retiredDevices)
                require(old.devices.keys.any { credentialWallet(it) == event.author && it !in old.retiredDevices })
                val epoch = event.body.number("epoch")
                val bindings = devices(event.body)
                require(epoch == Math.addExact(old.epoch, 1) && bindings == old.devices - removed)
                require(Base64.getDecoder().decode(event.body.text("commit")).size in 1..1_048_576)
                require(!event.body.has("metadata") && !event.body.has("channel_metadata") && !event.body.has("channel_profile"))
                current.copy(controlId = event.id, nextPosition = Math.addExact(position, 1),
                    channels = Collections.unmodifiableMap(current.channels + (channel to old.copy(epoch = epoch, devices = bindings,
                        epochStartedAt = position, retiredDevices = Collections.unmodifiableSet(old.retiredDevices - removed)))))
            }
            "leave" -> {
                require(event.body.text("control_id") == current.controlId)
                require(event.author in current.members && event.author != current.owner) {
                    "The owner must transfer ownership before leaving"
                }
                endMembership(current.copy(controlId = event.id, nextPosition = Math.addExact(position, 1)), event.author, position)
            }
            "remove-member", "ban-member" -> {
                require(event.body.text("control_id") == current.controlId)
                val actorRole = current.members[event.author]
                require(actorRole in setOf(SpaceRole.OWNER, SpaceRole.ADMIN))
                val member = event.body.text("member")
                val role = current.members[member]
                val banning = event.body.text("kind") == "ban-member"
                require(SaitoWallet.isPublicKey(member) && member != current.owner && member != event.author)
                require(if (banning) member !in current.bannedMembers else role != null)
                require(actorRole == SpaceRole.OWNER || role == null || role == SpaceRole.MEMBER) { "Administrators cannot moderate each other" }
                endMembership(current.copy(controlId = event.id, nextPosition = Math.addExact(position, 1),
                    bannedMembers = if (banning) Collections.unmodifiableSet(current.bannedMembers + member) else current.bannedMembers), member, position)
            }
            "unban-member" -> {
                require(event.body.text("control_id") == current.controlId)
                require(current.members[event.author] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN))
                val member = event.body.text("member")
                require(member in current.bannedMembers)
                current.copy(controlId = event.id, nextPosition = Math.addExact(position, 1),
                    bannedMembers = Collections.unmodifiableSet(current.bannedMembers - member),
                    membershipEndedAt = Collections.unmodifiableMap(current.membershipEndedAt + (member to position)))
            }
            "propose-owner" -> {
                require(current.protectedChat?.directPeer == null)
                require(event.author == current.owner && event.body.text("control_id") == current.controlId)
                require(current.ownershipOffer == null)
                val member = event.body.text("member")
                require(member in current.members && member != current.owner)
                current.copy(controlId = event.id, nextPosition = Math.addExact(position, 1),
                    ownershipOffer = SpaceOwnershipOffer(event.id, member, position))
            }
            "accept-owner" -> {
                val offer = requireNotNull(current.ownershipOffer)
                require(event.body.text("control_id") == current.controlId && event.body.text("proposal_id") == offer.id)
                require(event.author == offer.member && event.author in current.members && offer.acceptedId == null)
                // Published device credentials do not prove that the accepting
                // device has received usable keys. Private Spaces always wait
                // for its separately durable local-readiness acknowledgement.
                if (current.channels.values.all { it.visibility == SpaceVisibility.PUBLIC }) transferOwnership(current, event.author, event.id, position)
                else current.copy(controlId = event.id, nextPosition = Math.addExact(position, 1),
                    ownershipOffer = offer.copy(acceptedId = event.id, acceptedAt = position))
            }
            "cancel-owner", "decline-owner" -> {
                val offer = requireNotNull(current.ownershipOffer)
                require(event.body.text("control_id") == current.controlId && event.body.text("proposal_id") == offer.id)
                require(event.author == if (event.body.text("kind") == "cancel-owner") current.owner else offer.member)
                retireExcludedDevices(current.copy(controlId = event.id, nextPosition = Math.addExact(position, 1), ownershipOffer = null))
            }
            "complete-owner" -> {
                val offer = requireNotNull(current.ownershipOffer)
                require(event.body.text("control_id") == current.controlId && event.body.text("proposal_id") == offer.id)
                require(event.author == offer.member && offer.acceptedId != null && event.body.text("acceptance_id") == offer.acceptedId)
                require(event.author in current.members && privateKeysAdmitted(current, event.author))
                transferOwnership(current, event.author, event.id, position)
            }
            "set-role" -> {
                require(event.author == current.owner && event.body.text("control_id") == current.controlId)
                val member = event.body.text("member")
                require(member in current.members && member != current.owner)
                val role = when (event.body.text("role")) {
                    "admin" -> SpaceRole.ADMIN
                    "member" -> SpaceRole.MEMBER
                    else -> throw IllegalArgumentException("Only administrator or member roles can be assigned")
                }
                val administratorStartedAt = if (role == SpaceRole.ADMIN && current.members[member] != role)
                    Collections.unmodifiableMap(current.administratorStartedAt + (member to position)) else current.administratorStartedAt
                retireExcludedDevices(current.copy(controlId = event.id, nextPosition = Math.addExact(position, 1),
                    members = Collections.unmodifiableMap(current.members + (member to role)), administratorStartedAt = administratorStartedAt))
            }
            "archive-channel" -> {
                require(current.members[event.author] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN))
                require(event.body.text("control_id") == current.controlId)
                val channel = event.body.text("channel_id")
                val old = requireNotNull(current.channels[channel])
                val value = event.body.get("archived").also { require(it != null && it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) }.asBoolean
                require(value || old.visibility != SpaceVisibility.PUBLIC || current.visibility == SpaceVisibility.PUBLIC) { "Public archives stay read-only while the Space is private" }
                current.copy(controlId = event.id, nextPosition = Math.addExact(position, 1),
                    channels = Collections.unmodifiableMap(current.channels + (channel to old.copy(archived = value,
                        contributionStartedAt = if (old.archived != value) position else old.contributionStartedAt))))
            }
            "invite" -> {
                require(current.owner != null && event.body.text("control_id") == current.controlId)
                require(current.members[event.author] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN))
                val key = event.body.text("invite_key").also { require(SaitoWallet.isPublicKey(it)) }
                val expiry = event.body.number("expires_at").also { require(it > signedReceipt.createdAt) }
                val format = event.body.get("preview_format")?.let { event.body.text("preview_format") }
                require(format == null || format == "profile-v1")
                val channelId = if (format == null) current.channels.keys.first() else event.body.text("channel_id")
                val channel = requireNotNull(current.channels[channelId])
                // Earlier peers also invited directly into unrestricted private
                // member channels. Opening a private Space freezes those audiences,
                // excluding their earlier history from this compatibility path.
                val legacyMemberChannel = channel.visibility == SpaceVisibility.PRIVATE && channel.allowedMembers == null
                require(channelId == current.entryChannelId || legacyMemberChannel) { "An invitation must use the community entrance or an unrestricted private member channel" }
                val recipient = event.body.get("recipient")?.let { event.body.text("recipient") }
                require(current.protectedChat?.acceptsRecipient(recipient) != false)
                if (recipient != null) {
                    require(channel.visibility == SpaceVisibility.PRIVATE && format == "profile-v1")
                    require(SaitoWallet.isPublicKey(recipient) && recipient !in current.members)
                }
                val token = if (channel.visibility == SpaceVisibility.PUBLIC) {
                    val value = event.body.get("rules_token")?.let { event.body.text("rules_token") } ?: ""
                    require(value == requireNotNull(current.publicProfile).publicRulesToken)
                    value
                } else if (format == "profile-v1") event.id else ""
                // Private consent binds the signed invitation containing its
                // encrypted preview, never a guessable digest of private text.
                current.copy(controlId = event.id, nextPosition = Math.addExact(position, 1),
                    invitations = Collections.unmodifiableMap(current.invitations + (event.id to SpaceInvitationRecord(key, expiry, channelId, token, recipient))))
            }
            "revoke-invite" -> {
                require(current.members[event.author] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN))
                require(event.body.text("control_id") == current.controlId)
                val invitation = event.body.text("invite_id")
                require(invitation in current.invitations)
                current.copy(controlId = event.id, nextPosition = Math.addExact(position, 1),
                    invitations = Collections.unmodifiableMap(current.invitations - invitation))
            }
            "dissolve" -> {
                require(current.owner == event.author && event.body.text("control_id") == current.controlId)
                current.copy(controlId = event.id, nextPosition = Math.addExact(position, 1), dissolved = true,
                    invitations = emptyMap(), ownershipOffer = null, publicProfile = current.publicProfile?.copy(listed = false),
                    channels = Collections.unmodifiableMap(current.channels.mapValues { (_, channel) ->
                        channel.copy(archived = true, contributionStartedAt = position)
                    }))
            }
            "set-visibility" -> {
                require(current.protectedChat == null) { "Protected chats have one private conversation" }
                require(current.owner == event.author && event.body.text("control_id") == current.controlId)
                if (event.body.text("visibility") == "private") closePublicSpace(event, position) else {
                    require(current.visibility == SpaceVisibility.PRIVATE && event.body.text("visibility") == "public")
                    val profile = SpaceProfile.fromJson(event.body.getAsJsonObject("public_profile"))
                    require(profile.visibility == SpaceVisibility.PUBLIC)
                    val channel = event.body.text("channel_id").also { require(it.matches(ID) && it !in current.channels) }
                    val generation = event.body.text("key_generation").also { require(it.matches(ID)) }
                    require(event.body.number("epoch") == 0L)
                    event.body.get("devices").let { require(it != null && it.isJsonObject && it.asJsonObject.size() == 0) }
                    val entry = SpaceChannelProfile.fromJson(event.body.getAsJsonObject("channel_profile"))
                    require(entry.kind == SpaceChannelKind.CHAT && entry.category.isEmpty())
                    // Preserve each old audience while moving shared names to its own encrypted group.
                    val rebound = current.channels.filter { (id, record) ->
                        record.visibility == SpaceVisibility.PRIVATE && record.allowedMembers == null && record.metadataGroupId(id, current) != id
                    }
                    val metadataUpdates = event.body.get("channel_metadata_updates")?.also { require(it.isJsonObject) }?.asJsonObject ?: JsonObject()
                    require(metadataUpdates.keySet() == rebound.keys)
                    val channels = current.channels.mapValues { (id, record) ->
                        if (record.visibility == SpaceVisibility.PRIVATE && record.allowedMembers == null) {
                            val frozen = record.copy(allowedMembers = Collections.unmodifiableSet(current.members.keys.toSet()))
                            if (id !in rebound) frozen else {
                                require(record.retiredDevices.isEmpty() && record.devices.keys.any { credentialWallet(it) == event.author })
                                val metadata = metadataUpdates.text(id)
                                verifyChannelMetadata(metadata, event, SpaceCrypto.Scope(serviceKey, spaceId, id, record.generation), record.epoch)
                                frozen.copy(metadata = metadata, metadataChannelId = id)
                            }
                        } else record
                    } + (channel to SpaceChannelRecord(0, emptyMap(), generation, position, SpaceVisibility.PUBLIC,
                        entry, kind = entry.kind))
                    val next = current.copy(controlId = event.id, nextPosition = Math.addExact(position, 1),
                        visibility = SpaceVisibility.PUBLIC, publicProfile = profile, entryChannelId = channel,
                        invitations = emptyMap(), channels = Collections.unmodifiableMap(channels))
                    val categories = rebound.values.mapNotNull { it.categoryId }.toSet().filter { current.categories.getValue(it).profile == null }.toSet()
                    val categoryUpdates = event.body.get("category_metadata_updates")?.also { require(it.isJsonObject) }?.asJsonObject ?: JsonObject()
                    require(categoryUpdates.keySet() == categories)
                    val acceptedCategories = current.categories.toMutableMap()
                    for (category in categories) {
                        val old = current.categories.getValue(category)
                        val update = categoryUpdates.get(category).also { require(it.isJsonObject) }.asJsonObject
                        require(update.keySet() == setOf("category_metadata"))
                        acceptedCategories[category] = old.copy(metadata = categoryMetadata(event, update, next,
                            category, old.revision, next.categoryGroups(category, old.defaultGroup)))
                    }
                    next.copy(categories = Collections.unmodifiableMap(acceptedCategories))
                }
            }
            "set-listed" -> {
                require(current.owner == event.author && event.body.text("control_id") == current.controlId)
                val profile = requireNotNull(current.publicProfile)
                require(current.visibility == SpaceVisibility.PUBLIC)
                val listed = event.body.get("listed").also { require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) }.asBoolean
                current.copy(controlId = event.id, nextPosition = Math.addExact(position, 1),
                    publicProfile = profile.copy(listed = listed))
            }
            "join-public" -> {
                require(current.visibility == SpaceVisibility.PUBLIC && event.author !in current.bannedMembers)
                val profile = requireNotNull(current.publicProfile)
                require(event.body.text("control_id") == current.controlId && event.author !in current.members)
                require(current.channels[event.body.text("channel_id")]?.visibility == SpaceVisibility.PUBLIC)
                require((event.body.get("rules_token")?.let { event.body.text("rules_token") } ?: "") == profile.publicRulesToken) {
                    "Spaces house rules have not been accepted"
                }
                current.copy(controlId = event.id, nextPosition = Math.addExact(position, 1),
                    members = Collections.unmodifiableMap(current.members + (event.author to SpaceRole.MEMBER)))
            }
            "admit" -> {
                require(current.owner != null && event.body.text("control_id") == current.controlId)
                val channel = event.body.text("channel_id")
                val oldChannel = requireNotNull(current.channels[channel])
                require(oldChannel.visibility == SpaceVisibility.PRIVATE && oldChannel.retiredDevices.isEmpty())
                val join = verifyJoin(event.body.text("join"), signedReceipt.createdAt)
                require(join.channelId == channel && join.operationId != operation)
                val replacing = join.replacedDevice
                if (replacing == null) require(current.members[event.author] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN))
                else require(canApproveDeviceRequests(event.author) && event.author != join.actor &&
                    canAccessChannel(event.author, channel) && oldChannel.devices.keys.any { credentialWallet(it) == event.author })
                val keyPackage = join.keyPackage
                val epoch = event.body.number("epoch")
                val devices = devices(event.body)
                val continuing = if (replacing == null) oldChannel.devices else oldChannel.devices - replacing
                require(epoch == Math.addExact(oldChannel.epoch, 1) &&
                    devices == continuing + (keyPackage.credential to keyPackage.signer))
                for (field in listOf("welcome", "commit")) {
                    require(Base64.getDecoder().decode(event.body.text(field)).size in 1..1_048_576)
                }
                event.body.get("history_deliveries")?.let { manifest ->
                    require(manifest.isJsonArray && manifest.asJsonArray.size() in 1..128)
                    val ids = manifest.asJsonArray.map {
                        require(it.isJsonPrimitive && it.asJsonPrimitive.isString)
                        it.asString.also { id -> require(id.matches(Regex("[0-9a-f]{128}")) && id !in historyDeliveries) }
                    }
                    require(ids.distinct().size == ids.size)
                    val delivery = HistoryDelivery(event.author, channel, oldChannel.generation, epoch,
                        keyPackage.credential, requireNotNull(current.controlId))
                    admittedHistory = ids.associateWith { delivery }
                }
                admittedJoin = join
                current.copy(controlId = event.id, nextPosition = Math.addExact(position, 1),
                    members = Collections.unmodifiableMap(current.members + (join.actor to (current.members[join.actor] ?: SpaceRole.MEMBER))),
                    channels = Collections.unmodifiableMap(current.channels + (channel to oldChannel.copy(epoch = epoch, devices = devices, epochStartedAt = position,
                        metadata = if (oldChannel.allowedMembers != null) event.body.text("channel_metadata").also {
                            require(Base64.getDecoder().decode(it).size in 29..65_536)
                            verifyChannelMetadata(it, event, SpaceCrypto.Scope(serviceKey, spaceId, channel, oldChannel.generation), epoch)
                        } else oldChannel.metadata))))
            }
            else -> throw IllegalArgumentException("Unsupported Spaces control version or kind")
        }
        controlPositions[event.id] = position
        applied[position] = event.id
        operations[operation] = event.id
        admittedJoin?.let { operations[it.operationId] = it.id }
        historyDeliveries.putAll(admittedHistory)
        if (next.owner != current.owner) ownershipStartedAt = position
        current = next
        return next
    }

    private fun closePublicSpace(event: SignedSaitoMessage, position: Long): SpaceSnapshot {
        require(current.visibility == SpaceVisibility.PUBLIC && event.body.text("visibility") == "private")
        require(!event.body.has("public_profile") && !event.body.has("channel_profile") &&
            !event.body.has("channel_metadata_updates") && !event.body.has("category_metadata_updates"))
        val channel = event.body.text("channel_id").also { require(it.matches(ID) && it !in current.channels) }
        val generation = event.body.text("key_generation").also { require(it.matches(ID)) }
        require(event.body.number("epoch") == 0L)
        val bindings = devices(event.body)
        require(bindings.size == 1 && credentialWallet(bindings.keys.single()) == event.author)
        val metadata = event.body.text("channel_metadata")
        verifyChannelMetadata(metadata, event, SpaceCrypto.Scope(serviceKey, spaceId, channel, generation), 0)
        val channels = current.channels.mapValues { (_, record) ->
            if (record.visibility == SpaceVisibility.PUBLIC) record.copy(archived = true, contributionStartedAt = position) else record
        } + (channel to SpaceChannelRecord(0, bindings, generation, position, SpaceVisibility.PRIVATE,
            metadata = metadata, kind = SpaceChannelKind.CHAT, metadataChannelId = channel))
        return current.copy(controlId = event.id, nextPosition = Math.addExact(position, 1),
            visibility = SpaceVisibility.PRIVATE, publicProfile = null, entryChannelId = channel,
            invitations = emptyMap(), channels = Collections.unmodifiableMap(channels))
    }

    private fun categorizedChannel(event: SignedSaitoMessage, snapshot: SpaceSnapshot, channel: String,
        record: SpaceChannelRecord): SpaceSnapshot {
        val body = event.body
        val previous = snapshot.channels[channel]
        val legacyReassignment = !body.has("category_id") && record.profile != null && previous?.profile != null &&
            record.profile.category != previous.profile.category
        val category = if (legacyReassignment) snapshot.categories.entries.firstOrNull {
            it.value.profile?.name == record.profile?.category
        }?.key else if (!body.has("category_id")) record.categoryId else body.get("category_id").let { value ->
            if (value.isJsonNull) null else {
                require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
                value.asString.also { require(it.matches(ID)) }
            }
        }
        val linked = record.copy(categoryId = category)
        var next = snapshot.copy(channels = Collections.unmodifiableMap(snapshot.channels + (channel to linked)))
        if (category == null) {
            require(!body.has("category_creation") && !body.has("category_attachment"))
            return next
        }
        var resolved = snapshot.categories[category]
        if (body.has("category_creation")) {
            require(resolved == null && !body.has("category_attachment"))
            val creation = body.get("category_creation").also { require(it.isJsonObject) }.asJsonObject
            require(!creation.has("default_group"))
            resolved = categoryRecord(event, creation, next, category, null)
        } else require(resolved != null)
        var accepted = requireNotNull(resolved)
        val publicProfile = accepted.profile
        if (publicProfile != null) {
            require(!body.has("category_attachment"))
            record.profile?.let { require(it.category == publicProfile.name ||
                !body.has("category_id") && it.category == previous?.profile?.category) }
        } else {
            require(record.visibility == SpaceVisibility.PRIVATE)
            val group = record.metadataGroupId(channel, next)
            if (body.has("category_attachment")) {
                val attachment = body.get("category_attachment").also { require(it.isJsonObject) }.asJsonObject
                require(attachment.keySet() == setOf("category_metadata"))
                accepted = accepted.copy(metadata = Collections.unmodifiableMap(accepted.metadata +
                    categoryMetadata(event, attachment, next, category, accepted.revision, setOf(group))))
            }
            require(group in accepted.metadata)
        }
        next = next.copy(categories = Collections.unmodifiableMap(next.categories + (category to accepted)))
        return next
    }

    private fun categoryRecord(event: SignedSaitoMessage, body: JsonObject, snapshot: SpaceSnapshot,
        category: String, previous: SpaceCategoryRecord?): SpaceCategoryRecord {
        val revision = event.body.text("operation_id")
        if (body.has("category_profile")) {
            require(snapshot.visibility == SpaceVisibility.PUBLIC && (previous == null || previous.profile != null))
            require(!body.has("category_metadata") && !body.has("default_group"))
            val value = body.get("category_profile").also { require(it.isJsonObject) }
            return SpaceCategoryRecord(SpaceCategoryProfile.fromJson(value.asJsonObject), revision)
        }
        val privatizing = previous?.profile != null
        if (privatizing) require(snapshot.visibility == SpaceVisibility.PRIVATE &&
            body.text("default_group") == snapshot.entryChannelId)
        val defaultGroup = if (previous == null || privatizing) body.takeIf { it.has("default_group") }?.text("default_group") else {
            require(!body.has("default_group")); previous.defaultGroup
        }
        val required = snapshot.categoryGroups(category, defaultGroup)
        require(required.isNotEmpty())
        val metadata = categoryMetadata(event, body, snapshot, category, revision, required)
        return SpaceCategoryRecord(null, revision, metadata, defaultGroup)
    }

    private fun categoryMetadata(event: SignedSaitoMessage, body: JsonObject, snapshot: SpaceSnapshot,
        category: String, revision: String, required: Set<String>): Map<String, String> {
        val value = body.get("category_metadata").also { require(it != null && it.isJsonObject) }.asJsonObject
        require(value.keySet() == required)
        return Collections.unmodifiableMap(required.associateWith { channel ->
            val group = requireNotNull(snapshot.channels[channel])
            require(group.visibility == SpaceVisibility.PRIVATE && group.retiredDevices.isEmpty())
            require(group.devices.keys.any { credentialWallet(it) == event.author })
            val encoded = value.text(channel)
            verifyChannelMetadata(encoded, event, SpaceCrypto.Scope(serviceKey, spaceId, channel, group.generation),
                group.epoch, "category-profile")
            val signed = SaitoEnvelope.decode(Base64.getDecoder().decode(encoded).toString(Charsets.UTF_8))
            require(signed.body.text("operation_id") == revision && signed.body.text("target_category_id") == category)
            encoded
        })
    }

    private fun privateKeysAdmitted(snapshot: SpaceSnapshot, actor: String): Boolean = snapshot.channels.values.all { record ->
        record.visibility == SpaceVisibility.PUBLIC || record.devices.keys.any { credential ->
            credentialWallet(credential) == actor && credential !in record.retiredDevices
        }
    }

    private fun transferOwnership(snapshot: SpaceSnapshot, member: String, control: String, position: Long): SpaceSnapshot {
        val previousOwner = requireNotNull(snapshot.owner)
        return retireExcludedDevices(snapshot.copy(controlId = control, nextPosition = Math.addExact(position, 1), owner = member,
            members = Collections.unmodifiableMap(snapshot.members + (previousOwner to SpaceRole.MEMBER) + (member to SpaceRole.OWNER)),
            administratorStartedAt = Collections.unmodifiableMap(snapshot.administratorStartedAt - previousOwner - member), ownershipOffer = null))
    }

    private fun endMembership(snapshot: SpaceSnapshot, member: String, position: Long): SpaceSnapshot =
        retireExcludedDevices(snapshot.copy(members = Collections.unmodifiableMap(snapshot.members - member),
            administratorStartedAt = Collections.unmodifiableMap(snapshot.administratorStartedAt - member),
            ownershipOffer = snapshot.ownershipOffer?.takeUnless { it.member == member },
            membershipEndedAt = Collections.unmodifiableMap(snapshot.membershipEndedAt + (member to position)),
            membershipReadThrough = if (member in snapshot.members) Collections.unmodifiableMap(snapshot.membershipReadThrough + (member to position))
                else snapshot.membershipReadThrough,
            channels = Collections.unmodifiableMap(snapshot.channels.mapValues { (_, channel) ->
                channel.copy(allowedMembers = channel.allowedMembers?.let { Collections.unmodifiableSet(it - member) },
                    accessStartedAt = Collections.unmodifiableMap(channel.accessStartedAt - member))
            })))

    /** Retired credentials stay retired even if their wallet later regains a
     * role. New admission must use fresh keys after their actual removal. */
    private fun retireExcludedDevices(snapshot: SpaceSnapshot): SpaceSnapshot = snapshot.copy(
        channels = Collections.unmodifiableMap(snapshot.channels.mapValues { (_, record) ->
            if (record.visibility == SpaceVisibility.PUBLIC) record else {
                val excluded = record.devices.keys.filter { credential ->
                    val member = credentialWallet(credential)
                    val role = snapshot.members[member]
                    role == null || !hasChannelAudience(snapshot, member, record) && !hasConsentedHandoverAccess(snapshot, member)
                }
                record.copy(retiredDevices = Collections.unmodifiableSet(record.retiredDevices.intersect(record.devices.keys) + excluded))
            }
        }))

    private fun declaredChannelKind(body: JsonObject): SpaceChannelKind? = body.get("channel_kind")?.let { value ->
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
        requireNotNull(SpaceChannelKind.entries.find { it.name.lowercase(java.util.Locale.ROOT) == value.asString })
    }

    private fun verifyChannelMetadata(encoded: String, event: SignedSaitoMessage, scope: SpaceCrypto.Scope, epoch: Long,
        contentKind: String = "channel-profile", targetChannelId: String? = null, allowImplicitTarget: Boolean = false) {
        val metadata = SaitoEnvelope.decode(Base64.getDecoder().decode(encoded).toString(Charsets.UTF_8))
        val body = metadata.body
        header(body, "event")
        require(metadata.author == event.author && metadata.recipients == event.recipients)
        require(body.text("kind") == "content" && body.text("content_kind") == contentKind)
        if (targetChannelId != null) require(if (body.has("target_channel_id")) body.text("target_channel_id") == targetChannelId
            else allowImplicitTarget && scope.channelId == targetChannelId) // Earlier restricted edits were bound by their own signed scope.
        require(body.text("space_id") == scope.spaceId && body.text("channel_id") == scope.channelId &&
            body.text("key_generation") == scope.generation && body.number("epoch") == epoch &&
            body.text("control_id") == (if (event.body.text("kind") == "create") "0".repeat(128) else event.body.text("control_id")))
        require(Base64.getDecoder().decode(body.text("ciphertext")).size in 29..65_536)
    }

    /** A valid request alone does not activate membership or advance control. */
    fun verifyJoin(envelope: String, acceptedAt: Long): SpaceJoinRequest {
        require(!current.dissolved) { "This Space has been dissolved" }
        val join = SaitoEnvelope.decode(envelope)
        header(join.body, "event")
        val kind = join.body.text("kind")
        require(kind in setOf("join", "join-channel", "replace-device") && join.body.text("space_id") == spaceId &&
            join.recipients == listOf(join.author, serviceKey).distinct())
        val channel = join.body.text("channel_id")
        require(current.channels[channel]?.visibility == SpaceVisibility.PRIVATE)
        if (current.protectedChat != null) require(join.body.number("protected_chat_version") == 1L) {
            "This member needs a client that supports protected chat"
        }
        val operation = join.body.text("operation_id")
        require(operation.matches(ID) && operation !in operations)
        val invitationId = if (kind == "join") join.body.text("invite_id") else null
        val invitation = invitationId?.let { requireNotNull(current.invitations[it]) }
        val replacedDevice = if (kind == "replace-device") join.body.text("replace_device") else null
        require(pendingJoinIsCurrent(join, acceptedAt)) { "The device or membership request is no longer current" }
        if (invitation != null) {
            require(channel == requireNotNull(invitation).channelId && invitation.accepts(join.author))
            require(join.body.text("control_id") == invitationId && acceptedAt >= 0 && invitation.expiresAt > acceptedAt)
            require((join.body.get("rules_token")?.let { join.body.text("rules_token") } ?: "") == invitation.rulesToken) {
                "Spaces house rules have not been accepted"
            }
        }
        val data = join.body.getAsJsonObject("key_package")
        val scope = SpaceCrypto.Scope(serviceKey, spaceId, channel, requireNotNull(current.channels[channel]).generation)
        val keyPackage = SpaceCrypto.BoundPackage(scope, data.text("wallet"), data.text("device"),
            data.text("package"), data.text("signer"), data.text("signature"))
        keyPackage.verify()
        require(keyPackage.walletKey == join.author)
        if (replacedDevice != null) require(keyPackage.credential !in current.channels.getValue(channel).devices)
        if (invitation != null) {
            val proof = "SaitoSpacesJoin/v1:${scope.groupId}:$invitationId:${join.author}:$operation:${keyPackage.walletSignature}"
            require(SaitoWallet.verify(proof.toByteArray(Charsets.UTF_8), join.body.text("invite_proof"), invitation.key)) {
                "Spaces invitation does not authorize this join"
            }
        }
        return SpaceJoinRequest(envelope, join.id, join.author, operation, channel, keyPackage, replacedDevice)
    }

    /** The service checks accepted membership and routing, never community plaintext. */
    fun verifyContent(envelope: String) {
        val event = SaitoEnvelope.decode(envelope)
        val body = event.body
        header(body, "event")
        require(body.text("space_id") == spaceId && event.recipients == listOf(event.author, serviceKey).distinct())
        require(body.text("operation_id").matches(ID) && body.text("operation_id") !in operations)
        historyDeliveries[event.id]?.let { delivery ->
            // An admission commits these exact encrypted originals before it
            // changes roles or keys. Retrying their delivery cannot create new
            // writing or redirect history to another device.
            val channel = requireNotNull(current.channels[delivery.channel])
            require(event.author == delivery.author && body.text("kind") == "content" && body.text("content_kind") == "history-keys")
            require(body.text("channel_id") == delivery.channel && body.text("key_generation") == delivery.generation &&
                body.number("epoch") == delivery.epoch && body.text("control_id") == delivery.control &&
                body.text("recipient_credential") == delivery.recipient)
            val recipient = credentialWallet(delivery.recipient)
            require(channel.visibility == SpaceVisibility.PRIVATE && channel.generation == delivery.generation &&
                recipient in current.members && canAccessChannel(recipient, delivery.channel) &&
                delivery.recipient in channel.devices && delivery.recipient !in channel.retiredDevices)
            require(Base64.getDecoder().decode(body.text("ciphertext")).size in 29..(64 * 1024 + 28))
            return
        }
        require(!current.dissolved) { "This Space has been dissolved" }
        require(event.author in current.members)
        val channel = requireNotNull(current.channels[body.text("channel_id")])
        require(canContributeChannel(event.author, body.text("channel_id")))
        val keyDelivery = body.text("kind") == "history" ||
            body.text("kind") == "content" && body.text("content_kind") == "history-keys"
        require(!channel.archived || keyDelivery) { "This channel is archived" }
        val contentKind = body.get("content_kind")?.asString
        if (body.has("thread_root_id")) require(contentKind in setOf("message-edit", "message-delete", "message-reaction", "message-pin") &&
            body.text("thread_root_id").matches(Regex("[0-9a-f]{128}")))
        val threadContribution = contentKind in setOf("thread-reply", "message-reaction", "message-pin") || contentKind in setOf("message-edit", "message-delete", "message-reaction", "message-pin") && body.has("thread_root_id")
        if (contentKind == "message-pin") require(hasCurrentAdministratorIntent(event.author, body.text("control_id"))) {
            "Pin changes need current administrator authority"
        }
        val administratorOnly = (channel.adminPostsOnly || channel.profile?.kind == SpaceChannelKind.ANNOUNCEMENT) && !threadContribution
        if (administratorOnly)
            require(current.members[event.author] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN))
        val reference = requireNotNull(controlPositions[body.text("control_id")])
        require(reference >= (current.membershipEndedAt[event.author] ?: 0L)) { "Writing belongs to a previous membership term" }
        require(keyDelivery || !administratorOnly || reference >= (current.administratorStartedAt[event.author] ?: 0)) {
            "Administrator writing belongs to a previous role term"
        }
        require(reference >= channel.epochStartedAt) { "Content belongs to a previous channel epoch" }
        // Reopening permits newly reviewed writing, not old queued originals.
        // History-key delivery remains independent of the contribution window.
        require(keyDelivery || reference >= channel.contributionStartedAt) { "Channel writing permissions changed" }
        require(body.number("epoch") == channel.epoch && body.text("key_generation") == channel.generation)
        if (body.get("content_kind")?.asString in setOf("message-edit", "message-delete", "thread-reply", "message-reaction", "message-pin")) {
            require(body.text("target_message_id").matches(Regex("[0-9a-f]{128}")) && body.number("target_position") >= 0)
        }
        if (channel.visibility == SpaceVisibility.PUBLIC) {
            require(body.text("kind") == "public-content" && body.text("content_kind") in setOf("message", "message-edit", "message-delete", "thread-reply", "message-reaction", "message-pin"))
            val text = body.text("text")
            require(text.isNotBlank() && text.toByteArray(Charsets.UTF_8).size <= 64 * 1024)
            if (contentKind == "message-reaction") SpaceReactionSelection.decode(text)
            if (contentKind == "message-pin") require(text in setOf("pinned", "unpinned"))
            return
        }
        require(channel.retiredDevices.isEmpty()) { "Private content must wait for current membership keys" }
        require(channel.devices.keys.any { credentialWallet(it) == event.author })
        when (body.text("kind")) {
            "content" -> {
                require(body.text("content_kind").matches(Regex("[a-z][a-z-]{0,31}")))
                if (body.text("content_kind") == "history-keys") require(body.text("recipient_credential") in channel.devices)
                val size = Base64.getDecoder().decode(body.text("ciphertext")).size
                require(size in 29..(if (contentKind == "message-reaction") 2048 else 64 * 1024 + 28))
            }
            "history" -> {
                require(body.text("recipient_credential") in channel.devices)
                require(Base64.getDecoder().decode(body.text("wire")).size in 1..1_048_576)
            }
            else -> throw IllegalArgumentException("Unsupported Spaces content kind")
        }
    }

    private fun devices(body: JsonObject): Map<String, String> {
        val element = body.get("devices")
        require(element != null && element.isJsonObject)
        val value = element.asJsonObject
        require(value.size() in 1..16_384)
        return Collections.unmodifiableMap(value.entrySet().associate { (credential, signer) ->
            credentialWallet(credential)
            require(signer.isJsonPrimitive && signer.asJsonPrimitive.isString &&
                Base64.getDecoder().decode(signer.asString).size == 32)
            credential to signer.asString
        })
    }

    private fun credentialWallet(credential: String): String {
        val parts = credential.split(':')
        require(parts.size == 3 && parts[0] == "SaitoSpaces/v1" && SaitoWallet.isPublicKey(parts[1]) && parts[2].matches(ID))
        return parts[1]
    }

    private fun header(body: JsonObject, request: String) {
        require(body.text("module") == "Spaces" && body.text("request") == request && body.number("version") == 1L)
    }

    private fun JsonObject.text(name: String): String {
        val value = get(name)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString)
        return value.asString
    }

    private fun JsonObject.number(name: String): Long {
        val value = get(name)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber &&
            value.asString.matches(Regex("0|[1-9][0-9]{0,18}")))
        return value.asString.toLong().also { require(it >= 0) }
    }

    private companion object { val ID = Regex("[0-9a-f]{64}") }
}
