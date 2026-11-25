package nl.davidengelen.redsquare.spaces

import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import java.math.BigInteger
import java.net.URI
import java.security.SecureRandom

/** The provider's signed terms for a single Space owner on one selected node. */
class SpaceStorageOffer private constructor(
    val envelope: String,
    val id: String,
    val provider: String,
    val origin: String,
    val service: String,
    val space: String,
    val owner: String,
    val capacityBytes: Long,
    val expiresAt: Long,
    val confirmBy: Long,
    val costNolan: BigInteger,
) {
    fun verifyScope(space: String, service: String, owner: String, at: Long) {
        require(this.space == space && this.service == service && this.owner == owner)
        require(at < confirmBy && at < expiresAt) { "The storage offer expired" }
    }

    override fun toString() = "Signed Space storage offer"

    companion object {
        fun create(provider: SaitoWallet, origin: String, service: String, space: String, owner: String,
            capacityBytes: Long, expiresAt: Long, confirmBy: Long, costNolan: BigInteger, now: Long): SpaceStorageOffer {
            val body = JsonObject().apply {
                addProperty("module", "SpacesStorage")
                addProperty("version", 1)
                addProperty("request", "offer")
                addProperty("origin", origin)
                addProperty("service", service)
                addProperty("space", space)
                addProperty("owner", owner)
                addProperty("capacity_bytes", capacityBytes)
                addProperty("expires_at", expiresAt)
                addProperty("confirm_by", confirmBy)
                addProperty("cost_nolan", costNolan.toString())
                addProperty("offer_nonce", ByteArray(32).also(SecureRandom()::nextBytes)
                    .joinToString("") { "%02x".format(it.toInt() and 255) })
            }
            return read(SaitoTransaction.create(provider, body, now, listOf(owner)).envelope())
        }

        fun read(envelope: String): SpaceStorageOffer {
            require(envelope.length <= 8192)
            val signed = SaitoEnvelope.decode(envelope)
            val body = signed.body
            require(body.storageText("module") == "SpacesStorage" && body.get("version").toString() == "1" &&
                body.storageText("request") == "offer")
            require(body.keySet() == setOf("module", "version", "request", "origin", "service", "space", "owner",
                "capacity_bytes", "expires_at", "confirm_by", "cost_nolan", "offer_nonce"))
            val service = body.storageText("service")
            val owner = body.storageText("owner")
            require(SaitoWallet.isPublicKey(service) && SaitoWallet.isPublicKey(owner) &&
                signed.author != service && signed.recipients == listOf(signed.author, owner).distinct())
            val space = body.storageText("space").also { require(it.matches(ID)) }
            require(body.storageText("offer_nonce").matches(ID))
            val origin = body.storageText("origin").also(::verifySpaceStorageOrigin)
            val capacity = body.storageNumber("capacity_bytes").also { require(it in 1..MAX_CAPACITY) }
            val expires = body.storageNumber("expires_at")
            val deadline = body.storageNumber("confirm_by")
            require(signed.createdAt < deadline && deadline <= expires)
            val cost = body.storageText("cost_nolan").also { require(it.matches(Regex("0|[1-9][0-9]{0,35}"))) }.toBigInteger()
            return SpaceStorageOffer(envelope, signed.id, signed.author, origin, service, space, owner, capacity, expires, deadline, cost)
        }

        private const val MAX_CAPACITY = 1L shl 40
        private val ID = Regex("[0-9a-f]{64}")
    }
}

/** The owner intent as accepted by the pinned Spaces service. */
class SpaceStorageApproval private constructor(val operation: String, val offer: SpaceStorageOffer) {
    companion object {
        fun read(envelope: String, authority: String): SpaceStorageApproval {
            val receipt = SaitoEnvelope.decode(envelope)
            val body = receipt.body
            require(receipt.author == authority && body.storageText("module") == "Spaces" &&
                body.get("version").toString() == "1" && body.storageText("request") == "receipt" &&
                body.storageText("stream") == "control")
            body.storageNumber("position")
            val original = SaitoEnvelope.decode(body.storageText("event"))
            val intent = original.body
            require(intent.storageText("module") == "Spaces" && intent.get("version").toString() == "1" &&
                intent.storageText("request") == "event" && intent.storageText("kind") == "approve-file-storage")
            val offer = SpaceStorageOffer.read(intent.storageText("offer"))
            val space = body.storageText("space_id")
            require(intent.storageText("space_id") == space && original.recipients == listOf(original.author, authority).distinct() &&
                receipt.recipients == listOf(authority, original.author).distinct())
            offer.verifyScope(space, authority, original.author, receipt.createdAt)
            return SpaceStorageApproval(original.id, offer)
        }
    }
}

fun verifySpaceStorageOrigin(value: String) {
    require(value.length <= 512)
    val uri = URI.create(value)
    require(uri.scheme == "https" && uri.host != null && uri.userInfo == null && uri.rawQuery == null &&
        uri.rawFragment == null && uri.rawPath in setOf("", "/") && (uri.port == -1 || uri.port in 1..65535))
}

internal fun JsonObject.storageText(name: String): String = get(name).also {
    require(it != null && it.isJsonPrimitive && it.asJsonPrimitive.isString)
}.asString

internal fun JsonObject.storageNumber(name: String): Long = get(name).also {
    require(it != null && it.isJsonPrimitive && it.asJsonPrimitive.isNumber && it.asString.matches(Regex("0|[1-9][0-9]{0,18}")))
}.asLong.also { require(it >= 0) }
