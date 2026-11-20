package nl.davidengelen.redsquare.spaces

import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.SignedSaitoMessage
import java.util.Base64
import java.util.Collections

enum class SpaceRole { OWNER, ADMIN, MEMBER }
data class SpaceInvitationRecord(val key: String, val expiresAt: Long, val channelId: String, val rulesToken: String = "")
data class SpaceChannelRecord(val epoch: Long, val devices: Map<String, String>, val generation: String = "0".repeat(64), val epochStartedAt: Long = 0,
    val visibility: SpaceVisibility = SpaceVisibility.PRIVATE, val profile: SpaceChannelProfile? = null,
    val metadata: String? = null, val adminPostsOnly: Boolean = false, val archived: Boolean = false,
    val contributionStartedAt: Long = 0, val allowedMembers: Set<String>? = null,
    val accessStartedAt: Map<String, Long> = emptyMap(), val kind: SpaceChannelKind? = null,
    val initialMetadata: String? = metadata, val retiredDevices: Set<String> = emptySet())
data class SpaceJoinRequest(val envelope: String, val id: String, val actor: String,
    val operationId: String, val channelId: String, val keyPackage: SpaceCrypto.BoundPackage)

data class SpaceSnapshot(val owner: String? = null, val members: Map<String, SpaceRole> = emptyMap(),
    val controlId: String? = null, val nextPosition: Long = 0,
    val invitations: Map<String, SpaceInvitationRecord> = emptyMap(),
    val channels: Map<String, SpaceChannelRecord> = emptyMap(),
    val visibility: SpaceVisibility = SpaceVisibility.PRIVATE, val publicProfile: SpaceProfile? = null,
    val administratorStartedAt: Map<String, Long> = emptyMap(), val generalChannelId: String? = null,
    val membershipEndedAt: Map<String, Long> = emptyMap(), val bannedMembers: Set<String> = emptySet(),
    val membershipReadThrough: Map<String, Long> = emptyMap())

/** Authenticated control history. Content pages have their own channel cursors. */
class SpaceJournal(private val spaceId: String, private val serviceKey: String) {
    private var current = SpaceSnapshot()
    private val applied = mutableMapOf<Long, String>()
    private val operations = mutableMapOf<String, String>()
    private val controlPositions = mutableMapOf<String, Long>()

    init { require(spaceId.matches(ID) && SaitoWallet.isPublicKey(serviceKey)) }

    val snapshot: SpaceSnapshot get() = current

    fun canAccessChannel(actor: String, channel: String): Boolean {
        val record = current.channels[channel] ?: return false
        if (record.visibility == SpaceVisibility.PUBLIC) return true
        val role = current.members[actor] ?: return false
        return record.allowedMembers == null || actor in record.allowedMembers || role in setOf(SpaceRole.OWNER, SpaceRole.ADMIN)
    }

    /** Initial legacy requests remain valid, but ending membership requires a
     * newly signed intent acknowledging that departure or a later control. */
    fun hasCurrentMembershipIntent(actor: String, controlId: String?): Boolean {
        val endedAt = current.membershipEndedAt[actor]
        return actor !in current.bannedMembers && if (controlId == null) endedAt == null else
            controlPositions[controlId]?.let { it >= (endedAt ?: 0L) } == true
    }

    /** A policy regrant cannot revive a request from an earlier access term. */
    fun canJoinChannel(actor: String, channel: String, controlId: String): Boolean {
        val record = current.channels[channel] ?: return false
        val position = controlPositions[controlId] ?: return false
        if (record.visibility != SpaceVisibility.PRIVATE || actor !in current.members || actor in current.bannedMembers || !canAccessChannel(actor, channel) ||
            record.devices.keys.any { credentialWallet(it) == actor }) return false
        val startedAt = if (record.allowedMembers == null || actor in record.allowedMembers)
            record.accessStartedAt[actor] ?: 0L else if (current.members[actor] == SpaceRole.ADMIN)
            current.administratorStartedAt[actor] ?: 0L else 0L
        return position >= maxOf(startedAt, current.membershipEndedAt[actor] ?: 0L)
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
        var admittedJoin: SpaceJoinRequest? = null
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
                SpaceSnapshot(event.author, mapOf(event.author to SpaceRole.OWNER), event.id, 1,
                    channels = mapOf(event.body.text("channel_id") to SpaceChannelRecord(0, devices,
                        event.body.takeIf { it.has("key_generation") }?.text("key_generation")?.also { require(it.matches(ID)) }
                            ?: "0".repeat(64), visibility = channelVisibility,
                        profile = channelProfile, metadata = channelMetadata, adminPostsOnly = adminPostsOnly, kind = channelKind)), visibility = visibility, publicProfile = profile, generalChannelId = event.body.text("channel_id"))
            }
            "create-channel" -> {
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
                if (visibility == SpaceVisibility.PRIVATE && allowed == null)
                    require(current.channels.getValue(requireNotNull(current.generalChannelId)).retiredDevices.isEmpty())
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
                current.copy(controlId = event.id, nextPosition = Math.addExact(position, 1),
                    channels = Collections.unmodifiableMap(current.channels + (channel to SpaceChannelRecord(0, bindings,
                        generation, position, visibility, profile, metadata, adminPostsOnly, allowedMembers = allowed, kind = channelKind,
                        accessStartedAt = Collections.unmodifiableMap(allowed.orEmpty().associateWith { position })))))
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
                    require(!event.body.has("channel_metadata"))
                    val value = event.body.get("channel_profile").also { require(it != null && it.isJsonObject) }
                    val profile = SpaceChannelProfile.fromJson(value.asJsonObject)
                    require(profile.kind == requireNotNull(old.profile).kind) { "Metadata editing cannot change the channel kind" }
                    old.copy(profile = profile)
                } else {
                    require(!event.body.has("channel_profile") && !event.body.has("channel_kind"))
                    val metadataChannel = if (old.allowedMembers != null) channel else requireNotNull(current.generalChannelId)
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
                current.copy(controlId = event.id, nextPosition = Math.addExact(position, 1),
                    channels = Collections.unmodifiableMap(current.channels + (channel to edited)))
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
                val token = if (channel.visibility == SpaceVisibility.PUBLIC) {
                    val value = event.body.get("rules_token")?.let { event.body.text("rules_token") } ?: ""
                    require(value == requireNotNull(current.publicProfile).publicRulesToken)
                    value
                } else if (format == "profile-v1") event.id else ""
                // Private consent binds the signed invitation containing its
                // encrypted preview, never a guessable digest of private text.
                current.copy(controlId = event.id, nextPosition = Math.addExact(position, 1),
                    invitations = Collections.unmodifiableMap(current.invitations + (event.id to SpaceInvitationRecord(key, expiry, channelId, token))))
            }
            "revoke-invite" -> {
                require(current.members[event.author] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN))
                require(event.body.text("control_id") == current.controlId)
                val invitation = event.body.text("invite_id")
                require(invitation in current.invitations)
                current.copy(controlId = event.id, nextPosition = Math.addExact(position, 1),
                    invitations = Collections.unmodifiableMap(current.invitations - invitation))
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
                require(current.members[event.author] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN))
                val channel = event.body.text("channel_id")
                val oldChannel = requireNotNull(current.channels[channel])
                require(oldChannel.visibility == SpaceVisibility.PRIVATE && oldChannel.retiredDevices.isEmpty())
                val join = verifyJoin(event.body.text("join"), signedReceipt.createdAt)
                require(join.channelId == channel && join.operationId != operation)
                val keyPackage = join.keyPackage
                val epoch = event.body.number("epoch")
                val devices = devices(event.body)
                require(epoch == Math.addExact(oldChannel.epoch, 1) &&
                    devices == oldChannel.devices + (keyPackage.credential to keyPackage.signer))
                for (field in listOf("welcome", "commit")) {
                    require(Base64.getDecoder().decode(event.body.text(field)).size in 1..1_048_576)
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
        current = next
        return next
    }

    private fun endMembership(snapshot: SpaceSnapshot, member: String, position: Long): SpaceSnapshot =
        retireExcludedDevices(snapshot.copy(members = Collections.unmodifiableMap(snapshot.members - member),
            administratorStartedAt = Collections.unmodifiableMap(snapshot.administratorStartedAt - member),
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
                    role == null || record.allowedMembers != null && member !in record.allowedMembers && role !in setOf(SpaceRole.OWNER, SpaceRole.ADMIN)
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
            body.text("control_id") == event.body.text("control_id"))
        require(Base64.getDecoder().decode(body.text("ciphertext")).size in 29..65_536)
    }

    /** A valid request alone does not activate membership or advance control. */
    fun verifyJoin(envelope: String, acceptedAt: Long): SpaceJoinRequest {
        val join = SaitoEnvelope.decode(envelope)
        header(join.body, "event")
        val channelOnly = join.body.text("kind") == "join-channel"
        require(join.body.text("kind") in setOf("join", "join-channel") && join.body.text("space_id") == spaceId &&
            join.recipients == listOf(join.author, serviceKey).distinct())
        val channel = join.body.text("channel_id")
        require(current.channels[channel]?.visibility == SpaceVisibility.PRIVATE)
        val operation = join.body.text("operation_id")
        require(operation.matches(ID) && operation !in operations)
        val invitationId = if (channelOnly) null else join.body.text("invite_id")
        val invitation = invitationId?.let { requireNotNull(current.invitations[it]) }
        if (channelOnly) {
            require(canJoinChannel(join.author, channel, join.body.text("control_id")))
        } else {
            require(channel == requireNotNull(invitation).channelId)
            require(hasCurrentMembershipIntent(join.author, join.body.takeIf { it.has("membership_control") }?.text("membership_control"))) {
                "Membership ended after this join was requested"
            }
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
        if (!channelOnly) {
            require(join.author !in current.members)
            val proof = "SaitoSpacesJoin/v1:${scope.groupId}:$invitationId:${join.author}:$operation:${keyPackage.walletSignature}"
            require(SaitoWallet.verify(proof.toByteArray(Charsets.UTF_8), join.body.text("invite_proof"), requireNotNull(invitation).key)) {
                "Spaces invitation does not authorize this join"
            }
        }
        return SpaceJoinRequest(envelope, join.id, join.author, operation, channel, keyPackage)
    }

    /** The service checks accepted membership and routing, never community plaintext. */
    fun verifyContent(envelope: String) {
        val event = SaitoEnvelope.decode(envelope)
        val body = event.body
        header(body, "event")
        require(body.text("space_id") == spaceId && event.recipients == listOf(event.author, serviceKey).distinct())
        require(body.text("operation_id").matches(ID) && body.text("operation_id") !in operations)
        require(event.author in current.members)
        val channel = requireNotNull(current.channels[body.text("channel_id")])
        require(canAccessChannel(event.author, body.text("channel_id")))
        val keyDelivery = body.text("kind") == "history" ||
            body.text("kind") == "content" && body.text("content_kind") == "history-keys"
        require(!channel.archived || keyDelivery) { "This channel is archived" }
        val administratorOnly = channel.adminPostsOnly || channel.profile?.kind == SpaceChannelKind.ANNOUNCEMENT
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
        if (channel.visibility == SpaceVisibility.PUBLIC) {
            require(body.text("kind") == "public-content" && body.text("content_kind") == "message")
            val text = body.text("text")
            require(text.isNotBlank() && text.toByteArray(Charsets.UTF_8).size <= 64 * 1024)
            return
        }
        require(channel.retiredDevices.isEmpty()) { "Private content must wait for current membership keys" }
        require(channel.devices.keys.any { credentialWallet(it) == event.author })
        when (body.text("kind")) {
            "content" -> {
                require(body.text("content_kind").matches(Regex("[a-z][a-z-]{0,31}")))
                if (body.text("content_kind") == "history-keys") require(body.text("recipient_credential") in channel.devices)
                require(Base64.getDecoder().decode(body.text("ciphertext")).size in 29..(64 * 1024 + 28))
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
