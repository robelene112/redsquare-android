package nl.davidengelen.redsquare.saito

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.ByteBuffer
import java.util.Base64

enum class TalkDial(val wire: String) {
    REQUEST("stun-connection-request"), CANCEL("stun-connection-request-cancel"),
    ACCEPT("stun-connection-accepted"), REJECT("stun-connection-rejected"), PING("stun-connection-ping")
}

class TalkMessage internal constructor(val sender: String, val identityVerified: Boolean,
    val createdAt: Long, val signature: String?, private val content: JsonObject) {
    val body: JsonObject get() = content.deepCopy()
    val request: String get() = content.get("request").asString
    val module: String get() = content.get("module").asString
    val room: TalkRoom? get() = if (module == "Relay") TalkRoom.read(content.getAsJsonObject("data")) else null
    val roomId: String? get() = room?.id ?: content.get("call_id")?.asString
}

/** Legacy invitations are claims. Only a signature or authenticated DTLS peer establishes identity. */
object TalkProtocol {
    const val MAX_MESSAGE_BYTES = 128 * 1024
    private val roomRequests = setOf("call-list-request", "call-list-response", "peer-joined", "peer-left",
        "peer-kicked", "peer-kick-broadcast", "toggle-audio", "toggle-video", "screen-share-start",
        "screen-share-stop", "broadcast-call-list", "toggle-transcription")
    private val stunRequests = setOf("peer-joined", "peer-left", "peer-description", "peer-candidate", "peer-kicked", "peer-kick-broadcast")

    fun invite(wallet: SaitoWallet, peer: String, room: TalkRoom, command: TalkDial, now: Long): SaitoTransaction {
        require(SaitoWallet.isPublicKey(peer) && peer != wallet.publicKey)
        if (command in setOf(TalkDial.REQUEST, TalkDial.CANCEL)) require(room.host == wallet.publicKey)
        return SaitoTransaction.create(wallet, JsonObject().apply {
            addProperty("module", "Relay"); addProperty("request", command.wire); add("data", room.json())
        }, now, listOf(peer))
    }

    fun enter(wallet: SaitoWallet, room: TalkRoom, now: Long): SaitoTransaction =
        roomTransaction(wallet, room.id, "call-list-request", listOf(room.id, room.host) + room.peers, now)

    fun participants(wallet: SaitoWallet, room: TalkRoom, recipient: String, participants: List<String>,
        now: Long): SaitoTransaction {
        require(participants.size in 1..TalkRoom.MAX_PEERS && participants.distinct().size == participants.size &&
            participants.all(SaitoWallet::isPublicKey) && wallet.publicKey in participants)
        return roomTransaction(wallet, room.id, "call-list-response", listOf(recipient), now) {
            addProperty("public_key", recipient)
            add("call_list", com.google.gson.JsonArray().apply { participants.forEach(::add) })
        }
    }

    fun event(wallet: SaitoWallet, room: TalkRoom, request: String, peers: List<String>, now: Long,
        enabled: Boolean? = null): SaitoTransaction {
        require(request in setOf("peer-joined", "peer-left", "toggle-audio", "toggle-video"))
        if (request.startsWith("toggle-")) require(enabled != null)
        val body = JsonObject().apply {
            addProperty("module", if (request == "peer-left") "Stun" else "Videocall")
            addProperty("request", request); addProperty("call_id", room.id)
            if (enabled != null) add("data", JsonObject().apply {
                addProperty("public_key", wallet.publicKey); addProperty("enabled", enabled)
            })
        }
        require(peers.size <= TalkRoom.MAX_PEERS && peers.all(SaitoWallet::isPublicKey))
        return SaitoTransaction.create(wallet, body, now, peers)
    }

    private fun roomTransaction(wallet: SaitoWallet, roomId: String, request: String, recipients: List<String>,
        now: Long, content: JsonObject.() -> Unit = {}): SaitoTransaction {
        require(SaitoWallet.isPublicKey(roomId) && recipients.size <= TalkRoom.MAX_PEERS + 2 &&
            recipients.all(SaitoWallet::isPublicKey))
        return SaitoTransaction.create(wallet, JsonObject().apply {
            addProperty("module", "Videocall"); addProperty("request", request); addProperty("call_id", roomId)
            content()
        }, now, recipients)
    }

    fun network(bytes: ByteArray, owner: String): TalkMessage? =
        read(SaitoEnvelope.unverifiedNetworkEnvelope(bytes), owner)

    fun read(envelope: String, owner: String, authenticatedPeer: String? = null): TalkMessage? {
        require(SaitoWallet.isPublicKey(owner))
        if (envelope.length > 3 * MAX_MESSAGE_BYTES) return null // Large Chat/images belong to their original receiver.
        val outer = JsonParser.parseString(envelope).asJsonObject
        val bytes = Base64.getDecoder().decode(TalkRoom.string(outer, "m"))
        val body = JsonParser.parseString(Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString())
        if (!body.isJsonObject) return null // Ordinary encrypted Chat stays owned by Chat.
        val content = body.asJsonObject
        val module = content.get("module")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        val request = content.get("request")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        val relevant = when (module) {
            "Relay" -> TalkDial.entries.any { it.wire == request }
            "Videocall" -> request in roomRequests
            "Stun" -> request in stunRequests && (request in setOf("peer-joined", "peer-description", "peer-candidate") || content.has("call_id"))
            else -> false
        }
        if (!relevant) return null
        require(bytes.size <= MAX_MESSAGE_BYTES)
        val binary = Base64.getDecoder().decode(TalkRoom.string(outer, "t"))
        require(binary.size >= 152)
        val signed = binary.copyOfRange(16, 80).any { it != 0.toByte() }
        val sender: String
        val recipients: List<String>
        val timestamp: Long
        val signature: String?
        if (signed) {
            val verified = SaitoEnvelope.decode(envelope)
            sender = verified.author; recipients = verified.recipients
            timestamp = verified.createdAt; signature = verified.id
        } else {
            require(module == "Relay") // SDP/ICE and room membership never inherit unsigned invitation rules.
            val header = ByteBuffer.wrap(binary)
            val inputs = header.getInt(0); val outputs = header.getInt(4)
            require(inputs == 1 && outputs in 1..(TalkRoom.MAX_PEERS + 2) && header.getInt(8) == 0 &&
                header.getInt(12) == 0 && binary[92] == 0.toByte())
            require(binary.size == 93 + 59 * (inputs + outputs))
            require(header.getInt(88) in 0..1)
            val addresses = (0..outputs).map { index ->
                val offset = 93 + 59 * index
                require(header.getLong(offset + 33) == 0L)
                Base58.encode(binary.copyOfRange(offset, offset + 33)).also { require(SaitoWallet.isPublicKey(it)) }
            }
            sender = addresses.first(); recipients = addresses.drop(1)
            timestamp = header.getLong(80); signature = null
            require(timestamp >= 0)
        }
        require(owner in recipients)
        if (authenticatedPeer != null) require(sender == authenticatedPeer)
        if (module == "Relay") {
            val room = TalkRoom.read(content.getAsJsonObject("data"))
            if (request in setOf(TalkDial.REQUEST.wire, TalkDial.CANCEL.wire)) require(room.host == sender)
        } else if (module == "Videocall" || content.has("call_id")) {
            require(SaitoWallet.isPublicKey(TalkRoom.string(content, "call_id")))
        }
        if (request == "call-list-response") {
            require(sender == owner || TalkRoom.string(content, "public_key") == owner)
            val list = content.get("call_list")
            require(list != null && list.isJsonArray && list.asJsonArray.size() in 1..TalkRoom.MAX_PEERS)
            val peers = list.asJsonArray.map { value ->
                require(value.isJsonPrimitive && value.asJsonPrimitive.isString && SaitoWallet.isPublicKey(value.asString))
                value.asString
            }
            require(peers.distinct().size == peers.size && sender in peers)
        }
        return TalkMessage(sender, signed || authenticatedPeer != null, timestamp, signature, content.deepCopy())
    }
}
