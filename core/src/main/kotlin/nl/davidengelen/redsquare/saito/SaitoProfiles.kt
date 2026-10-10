package nl.davidengelen.redsquare.saito

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.math.BigDecimal

data class ProfileUpdate(val id: String, val author: String, val createdAt: Long,
    val snapshot: Boolean, val fields: String, val envelope: String)

/** Public fields reconstructed only from this author's verified Profile messages. */
class SaitoProfile internal constructor(val author: String, val fields: String,
    val updates: List<ProfileUpdate>, val ignored: Int) {
    private val data = JsonParser.parseString(fields).asJsonObject
    val description: String get() = data.get("description")?.takeIf {
        it.isJsonPrimitive && it.asJsonPrimitive.isString
    }?.asString.orEmpty()
}

object SaitoProfiles {
    /** A partial local edit is merged into the complete verified profile before signing. */
    fun message(profile: SaitoProfile, changes: JsonObject): JsonObject {
        val fields = JsonParser.parseString(profile.fields).asJsonObject
        changes.entrySet().forEach { (key, value) ->
            if (value.isJsonNull) fields.remove(key) else fields.add(key, value.deepCopy())
        }
        return JsonObject().apply {
            addProperty("module", "Profile")
            addProperty("request", "update profile")
            addProperty("snapshot", 1)
            add("data", fields)
        }
    }

    fun readArchive(response: String, author: String): SaitoProfile {
        val page = readArchiveRows(response, ::decode, { it.author == author }, { it.id })
        val updates = page.entries.sortedByDescending { it.createdAt }
        val snapshot = updates.firstOrNull { it.snapshot }
        val fields = if (snapshot != null) JsonParser.parseString(snapshot.fields).asJsonObject
        else JsonObject().apply {
            // The pinned client merges legacy fields oldest to newest only when no snapshot exists.
            updates.asReversed().forEach { update ->
                JsonParser.parseString(update.fields).asJsonObject.entrySet().forEach { (key, value) ->
                    add(key, value.deepCopy())
                }
            }
        }
        return SaitoProfile(author, fields.toString(), updates, page.ignored)
    }

    private fun decode(envelope: String): ProfileUpdate {
        val signed = SaitoEnvelope.decode(envelope)
        val body = signed.body
        require(body.get("module").asString == "Profile" && body.get("request").asString == "update profile")
        require(body.toString().toByteArray(Charsets.UTF_8).size <= SaitoTransaction.MAX_MESSAGE_BYTES)
        val fields = body.get("data").asJsonObject
        val marker = body.get("snapshot")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
        val snapshot = marker != null && ((marker.isBoolean && marker.asBoolean) ||
            (marker.isNumber && marker.asString.toBigDecimalOrNull()?.compareTo(BigDecimal.ONE) == 0))
        return ProfileUpdate(signed.id, signed.author, signed.createdAt, snapshot, fields.toString(), envelope)
    }
}
