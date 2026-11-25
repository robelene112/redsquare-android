package nl.davidengelen.redsquare.spaces

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.SaitoWallet

/** Signed room presence is short-lived. The channel itself is durable. */
data class SpaceVoiceRoom(
    val status: Status,
    val admitted: Boolean,
    val capacity: Int,
    val participants: List<Participant>,
    val observedAt: Long,
) {
    enum class Status { AVAILABLE, FULL, ARCHIVED, MEMBERS_ONLY, SESSION_ENDED }

    data class Participant(val wallet: String, val session: String, val canSpeak: Boolean, val expiresAt: Long) {
        init { require(SaitoWallet.isPublicKey(wallet) && session.matches(SESSION) && expiresAt > 0) }
    }

    init {
        require(capacity in 1..CAPACITY && participants.size <= capacity && observedAt >= 0)
        require(participants.map { it.wallet }.toSet().size == participants.size &&
            participants.map { it.session }.toSet().size == participants.size)
        require(participants.all { it.expiresAt > observedAt && it.expiresAt - observedAt <= LEASE_MILLIS })
        require(status != Status.ARCHIVED || participants.isEmpty() && !admitted)
        require(!admitted || status == Status.AVAILABLE)
    }

    fun json() = JsonObject().apply {
        addProperty("status", status.name.lowercase(java.util.Locale.ROOT))
        addProperty("admitted", admitted)
        addProperty("capacity", capacity)
        addProperty("observed_at", observedAt)
        add("participants", JsonArray().apply {
            participants.forEach { participant ->
                add(JsonObject().apply {
                    addProperty("wallet", participant.wallet)
                    addProperty("session", participant.session)
                    addProperty("can_speak", participant.canSpeak)
                    addProperty("expires_at", participant.expiresAt)
                })
            }
        })
    }

    enum class Command(val wire: String, val requiresSession: Boolean) {
        OBSERVE("observe", false), JOIN("join", true), RENEW("renew", true), LEAVE("leave", true),
        MUTE("mute", true), REMOVE("remove", true);

        companion object {
            fun read(wire: String): Command = entries.singleOrNull { it.wire == wire }
                ?: throw IllegalArgumentException("Unsupported room command")
        }
    }

    companion object {
        const val CAPACITY = 3
        const val LEASE_MILLIS = 12_000L
        private val ID = Regex("[0-9a-f]{64}")
        private val SESSION = Regex("[0-9a-f]{32}")

        fun validSession(session: String) = session.matches(SESSION)

        fun scope(space: String, channel: String, command: Command, session: String?): JsonObject {
            require(space.matches(ID) && channel.matches(ID))
            require(if (command.requiresSession) session?.let(::validSession) == true else session == null)
            return JsonObject().apply {
                addProperty("space_id", space)
                addProperty("channel_id", channel)
                addProperty("stream", "voice-room")
                addProperty("cursor", 0)
                addProperty("command", command.wire)
                session?.let { addProperty("session", it) }
            }
        }

        fun read(json: JsonObject): SpaceVoiceRoom {
            require(json.keySet() == setOf("status", "admitted", "capacity", "observed_at", "participants"))
            val rows = json.getAsJsonArray("participants")
            require(rows.size() <= CAPACITY)
            return SpaceVoiceRoom(
                Status.valueOf(json.get("status").asString.uppercase(java.util.Locale.ROOT)),
                json.boolean("admitted"), json.get("capacity").asInt,
                rows.map { item ->
                    val row = item.asJsonObject
                    require(row.keySet() == setOf("wallet", "session", "can_speak", "expires_at"))
                    Participant(row.get("wallet").asString, row.get("session").asString,
                        row.boolean("can_speak"), row.get("expires_at").asLong)
                },
                json.get("observed_at").asLong,
            )
        }

        private fun JsonObject.boolean(key: String): Boolean = getAsJsonPrimitive(key).let {
            require(it.isBoolean)
            it.asBoolean
        }
    }
}
