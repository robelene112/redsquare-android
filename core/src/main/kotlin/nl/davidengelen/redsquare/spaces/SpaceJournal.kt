package nl.davidengelen.redsquare.spaces

import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoWallet
import java.util.Base64

enum class SpaceRole { OWNER, ADMIN, MEMBER }

data class SpaceSnapshot(val owner: String? = null, val members: Map<String, SpaceRole> = emptyMap(),
    val controlId: String? = null, val nextPosition: Long = 0)

/** Authenticated control history. Content pages have their own channel cursors. */
class SpaceJournal(private val spaceId: String, private val serviceKey: String) {
    private var current = SpaceSnapshot()
    private val applied = mutableMapOf<Long, String>()

    init { require(spaceId.matches(ID) && SaitoWallet.isPublicKey(serviceKey)) }

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
        require(current.owner == null && position == 0L && event.body.text("kind") == "create")
        require(event.body.text("visibility") in setOf("public", "private"))
        require(event.body.text("channel_id").matches(ID) && event.body.number("epoch") == 0L)
        val metadata = Base64.getDecoder().decode(event.body.text("metadata"))
        require(metadata.size in 29..65_536) { "Invalid Spaces metadata length" }
        val next = SpaceSnapshot(event.author, mapOf(event.author to SpaceRole.OWNER), event.id, 1)
        applied[position] = event.id
        current = next
        return next
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
