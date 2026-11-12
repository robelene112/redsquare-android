package nl.davidengelen.redsquare.saito

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64

/** Saito Fileshare's live wire contract. The authenticated WebRTC peer owns unsigned Relay controls. */
object Fileshare {
    const val CHUNK_BYTES = 64_256
    const val MAX_FRAME_BYTES = 128 * 1024
    private val controls = setOf("query file permission", "grant file permission", "deny file permission", "stop file transfer")
    data class Offer(val id: String, val name: String, val size: Long, val type: String) {
        init {
            require(id.matches(Regex("[a-zA-Z0-9_-]{1,80}")))
            require(name.isNotBlank() && name.length <= 255 && name.none { it.code < 32 || it == '/' || it == '\\' })
            require(size in 1..9_007_199_254_740_991L && type.length <= 255 && type.none { it.code < 32 })
        }
        fun data() = JsonObject().apply {
            addProperty("id", id); addProperty("name", name); addProperty("size", size); addProperty("type", type)
        }
    }
    data class Chunk(val offer: Offer, val offset: Long, val index: Long, val bytes: ByteArray)
    data class Message(val body: JsonObject, val id: String? = null, val sender: String? = null) {
        val request: String get() = body.requiredString("request")
        val data: JsonObject get() = body.getAsJsonObject("data")
    }

    fun readOffer(data: JsonObject) = Offer(data.requiredString("id"), data.requiredString("name"),
        number(data, "size"), data.requiredString("type"))

    fun readChunk(data: JsonObject): Chunk {
        val meta = data.getAsJsonObject("meta")
        val raw = data.requiredString("raw")
        require(raw.length <= (CHUNK_BYTES + 2) / 3 * 4)
        val bytes = Base64.getDecoder().decode(raw)
        require(bytes.isNotEmpty() && bytes.size <= CHUNK_BYTES && number(meta, "chunk") == bytes.size.toLong())
        return Chunk(readOffer(meta), number(meta, "offset"), number(meta, "chunk_id"), bytes)
    }

    fun offer(wallet: SaitoWallet, peer: String, offer: Offer, now: Long): SaitoTransaction =
        control(wallet, peer, "query file permission", offer.data().apply { addProperty("publicKey", wallet.publicKey) }, now)

    fun control(wallet: SaitoWallet, peer: String, request: String, data: JsonObject, now: Long): SaitoTransaction {
        require(request in controls)
        return SaitoTransaction.create(wallet, JsonObject().apply {
            addProperty("module", "Relay"); addProperty("request", request); add("data", data)
        }, now, listOf(peer))
    }

    fun signal(wallet: SaitoWallet, peer: String, request: String, details: JsonObject, now: Long): SaitoTransaction {
        require(request in setOf("peer-joined", "peer-left", "peer-description", "peer-candidate"))
        return Stun.signal(wallet, peer, request, details, now)
    }

    /** Ownership only; direct() still authenticates/validates the selected frame before it is used. */
    fun ownsDirectFrame(envelope: String): Boolean = runCatching {
        val body = rawBody(envelope)
        body.get("module")?.asString == "Fileshare" || body.get("module")?.asString == "Relay" &&
            body.get("request")?.asString in controls
    }.getOrDefault(false)

    fun chunk(wallet: SaitoWallet, peer: String, chunk: Chunk, now: Long): SaitoTransaction {
        require(chunk.bytes.size in 1..CHUNK_BYTES && chunk.offset >= 0 && chunk.index > 0)
        require(chunk.offset <= chunk.offer.size - chunk.bytes.size)
        return SaitoTransaction.create(wallet, JsonObject().apply {
            addProperty("module", "Fileshare"); addProperty("request", "share file")
            add("data", JsonObject().apply {
                add("meta", chunk.offer.data().apply {
                    addProperty("offset", chunk.offset); addProperty("chunk_id", chunk.index); addProperty("chunk", chunk.bytes.size)
                })
                addProperty("raw", Base64.getEncoder().encodeToString(chunk.bytes))
            })
        }, now, listOf(peer))
    }

    fun receipt(wallet: SaitoWallet, peer: String, id: String, index: Long, now: Long) =
        SaitoTransaction.create(wallet, JsonObject().apply {
            addProperty("module", "Fileshare"); addProperty("request", "read receipt")
            add("data", JsonObject().apply { addProperty("id", id); addProperty("chunk_id", index) })
        }, now, listOf(peer))

    fun relay(transaction: SaitoTransaction) = JsonObject().apply {
        addProperty("request", "relay peer message"); add("data", transaction.signedJson())
    }.toString().toByteArray(Charsets.UTF_8)

    /** Only signed signaling is usable over a server Relay. Chat frames remain owned by Chat. */
    fun network(bytes: ByteArray, peer: String, owner: String): Message? {
        val envelope = SaitoEnvelope.unverifiedNetworkEnvelope(bytes)
        if (envelope.length > 2 * MAX_FRAME_BYTES) return null // Large encrypted Chat/image frames belong to Chat.
        val raw = rawBody(envelope)
        if (!relevant(raw)) return null
        val signed = SaitoEnvelope.decode(envelope)
        require(signed.author in setOf(peer, owner) && owner in signed.recipients)
        return Message(signed.body, signed.id, signed.author)
    }

    /** The caller binds this channel to signed Stun SDP. Unsigned controls never gain signer status. */
    fun direct(envelope: String, peer: String, owner: String): Message {
        require(envelope.toByteArray(Charsets.UTF_8).size <= MAX_FRAME_BYTES)
        val outer = JsonParser.parseString(envelope).asJsonObject
        val binary = Base64.getDecoder().decode(outer.requiredString("t"))
        require(binary.size >= 93)
        val header = ByteBuffer.wrap(binary)
        val inputs = header.getInt(0); val outputs = header.getInt(4)
        require(inputs == 1 && outputs in 1..255 && header.getInt(8) == 0 && header.getInt(12) == 0)
        require(binary.size == 93 + 59 * (inputs + outputs))
        require(Base58.encode(binary.copyOfRange(93, 126)) == peer)
        require((0 until outputs).any { i -> Base58.encode(binary.copyOfRange(152 + i * 59, 185 + i * 59)) == owner })
        val raw = rawBody(envelope)
        require(relevant(raw))
        val unsignedControl = binary.copyOfRange(16, 80).all { it == 0.toByte() } &&
            raw.requiredString("module") == "Relay" && raw.requiredString("request") in controls
        if (!unsignedControl) {
            val signed = SaitoEnvelope.decode(envelope)
            require(signed.author == peer && owner in signed.recipients)
        }
        return Message(raw)
    }

    private fun relevant(body: JsonObject) = when (body.get("module")?.asString) {
        "Stun" -> body.get("request")?.asString in setOf("peer-joined", "peer-left", "peer-description", "peer-candidate")
        "Fileshare" -> body.get("request")?.asString in setOf("share file", "read receipt")
        "Relay" -> body.get("request")?.asString in controls
        else -> false
    }
    private fun rawBody(envelope: String): JsonObject {
        require(envelope.length <= 2 * MAX_FRAME_BYTES)
        val outer = JsonParser.parseString(envelope).asJsonObject
        val bytes = Base64.getDecoder().decode(outer.requiredString("m"))
        require(bytes.size <= MAX_FRAME_BYTES)
        val value = JsonParser.parseString(bytes.toString(Charsets.UTF_8))
        return if (value.isJsonObject) value.asJsonObject else JsonObject()
    }
    fun number(data: JsonObject, key: String): Long {
        val value = data.get(key)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber && value.asString.matches(Regex("[0-9]{1,16}")))
        return value.asLong.also { require(it <= 9_007_199_254_740_991L) }
    }
    private fun JsonObject.requiredString(key: String): String {
        val value = get(key)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString)
        return value.asString
    }

    /** Acknowledge only after append succeeds. A duplicate of the last committed chunk is idempotent. */
    class Receiver(private val offer: Offer) {
        var received = 0L; private set
        private var next = 1L
        private var lastHash: ByteArray? = null
        fun append(chunk: Chunk, write: (ByteArray) -> Unit): Boolean {
            require(chunk.offer == offer && chunk.bytes.size in 1..CHUNK_BYTES)
            val hash = MessageDigest.getInstance("SHA-256").digest(chunk.bytes)
            if (chunk.index == next - 1 && chunk.offset == received - chunk.bytes.size && hash.contentEquals(lastHash))
                return received == offer.size
            require(chunk.index == next && chunk.offset == received && chunk.bytes.size <= offer.size - received)
            write(chunk.bytes)
            received += chunk.bytes.size; next++; lastHash = hash
            return received == offer.size
        }
    }
}
