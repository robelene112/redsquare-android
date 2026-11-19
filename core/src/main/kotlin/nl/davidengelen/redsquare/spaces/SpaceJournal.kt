package nl.davidengelen.redsquare.spaces

import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoWallet
import java.util.Base64
import java.util.Collections

enum class SpaceRole { OWNER, ADMIN, MEMBER }
data class SpaceInvitationRecord(val key: String, val expiresAt: Long)
data class SpaceChannelRecord(val epoch: Long, val devices: Map<String, String>, val generation: String = "0".repeat(64), val epochStartedAt: Long = 0)
data class SpaceJoinRequest(val envelope: String, val id: String, val actor: String,
    val operationId: String, val channelId: String, val keyPackage: SpaceCrypto.BoundPackage)

data class SpaceSnapshot(val owner: String? = null, val members: Map<String, SpaceRole> = emptyMap(),
    val controlId: String? = null, val nextPosition: Long = 0,
    val invitations: Map<String, SpaceInvitationRecord> = emptyMap(),
    val channels: Map<String, SpaceChannelRecord> = emptyMap())

/** Authenticated control history. Content pages have their own channel cursors. */
class SpaceJournal(private val spaceId: String, private val serviceKey: String) {
    private var current = SpaceSnapshot()
    private val applied = mutableMapOf<Long, String>()
    private val operations = mutableMapOf<String, String>()
    private val controlPositions = mutableMapOf<String, Long>()

    init { require(spaceId.matches(ID) && SaitoWallet.isPublicKey(serviceKey)) }

    val snapshot: SpaceSnapshot get() = current

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
                require(event.body.text("channel_id").matches(ID) && event.body.number("epoch") == 0L)
                val metadata = Base64.getDecoder().decode(event.body.text("metadata"))
                require(metadata.size in 29..65_536) { "Invalid Spaces metadata length" }
                val devices = devices(event.body)
                require(devices.size == 1 && credentialWallet(devices.keys.single()) == event.author)
                SpaceSnapshot(event.author, mapOf(event.author to SpaceRole.OWNER), event.id, 1,
                    channels = mapOf(event.body.text("channel_id") to SpaceChannelRecord(0, devices,
                        event.body.takeIf { it.has("key_generation") }?.text("key_generation")?.also { require(it.matches(ID)) }
                            ?: "0".repeat(64))))
            }
            "invite" -> {
                require(current.owner != null && event.body.text("control_id") == current.controlId)
                require(current.members[event.author] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN))
                val key = event.body.text("invite_key").also { require(SaitoWallet.isPublicKey(it)) }
                val expiry = event.body.number("expires_at").also { require(it > signedReceipt.createdAt) }
                current.copy(controlId = event.id, nextPosition = Math.addExact(position, 1),
                    invitations = Collections.unmodifiableMap(current.invitations + (event.id to SpaceInvitationRecord(key, expiry))))
            }
            "admit" -> {
                require(current.owner != null && event.body.text("control_id") == current.controlId)
                require(current.members[event.author] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN))
                val channel = event.body.text("channel_id")
                val oldChannel = requireNotNull(current.channels[channel])
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
                    members = Collections.unmodifiableMap(current.members + (join.actor to SpaceRole.MEMBER)),
                    channels = Collections.unmodifiableMap(current.channels + (channel to SpaceChannelRecord(epoch, devices, oldChannel.generation, position))))
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

    /** A valid request alone does not activate membership or advance control. */
    fun verifyJoin(envelope: String, acceptedAt: Long): SpaceJoinRequest {
        val join = SaitoEnvelope.decode(envelope)
        header(join.body, "event")
        require(join.body.text("kind") == "join" && join.body.text("space_id") == spaceId &&
            join.recipients == listOf(join.author, serviceKey).distinct())
        val channel = join.body.text("channel_id")
        require(channel in current.channels)
        val operation = join.body.text("operation_id")
        require(operation.matches(ID) && operation !in operations)
        val invitationId = join.body.text("invite_id")
        val invitation = requireNotNull(current.invitations[invitationId])
        require(join.body.text("control_id") == invitationId && acceptedAt >= 0 && invitation.expiresAt > acceptedAt)
        val data = join.body.getAsJsonObject("key_package")
        val scope = SpaceCrypto.Scope(serviceKey, spaceId, channel, requireNotNull(current.channels[channel]).generation)
        val keyPackage = SpaceCrypto.BoundPackage(scope, data.text("wallet"), data.text("device"),
            data.text("package"), data.text("signer"), data.text("signature"))
        keyPackage.verify()
        require(keyPackage.walletKey == join.author && join.author !in current.members)
        val proof = "SaitoSpacesJoin/v1:${scope.groupId}:$invitationId:${join.author}:$operation:${keyPackage.walletSignature}"
        require(SaitoWallet.verify(proof.toByteArray(Charsets.UTF_8), join.body.text("invite_proof"), invitation.key)) {
            "Spaces invitation does not authorize this join"
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
        val reference = requireNotNull(controlPositions[body.text("control_id")])
        require(reference >= channel.epochStartedAt) { "Content belongs to a previous channel epoch" }
        require(body.number("epoch") == channel.epoch && body.text("key_generation") == channel.generation)
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
        val value = body.getAsJsonObject("devices")
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
