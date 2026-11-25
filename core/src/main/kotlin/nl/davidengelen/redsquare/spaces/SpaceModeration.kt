package nl.davidengelen.redsquare.spaces

import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.SaitoWallet

data class SpaceMessageReport(val id: String, val channel: String, val message: String,
    val reporter: String, val reason: String, val receivedAt: Long) {
    init {
        require(id.matches(EVENT_ID) && channel.matches(SCOPE_ID) && message.matches(EVENT_ID))
        require(SaitoWallet.isPublicKey(reporter) && receivedAt >= 0)
        require(reason == cleanReason(reason))
    }

    fun json() = JsonObject().apply {
        addProperty("id", id)
        addProperty("channel_id", channel)
        addProperty("message_id", message)
        addProperty("reporter", reporter)
        addProperty("reason", reason)
        addProperty("received_at", receivedAt)
    }

    override fun toString() = "Private community report"

    companion object {
        const val MAX_REASON = 500
        private val SCOPE_ID = Regex("[0-9a-f]{64}")
        private val EVENT_ID = Regex("[0-9a-f]{128}")

        fun cleanReason(value: String): String = value.trim().also {
            require(it.codePointCount(0, it.length) in 1..MAX_REASON) { "A report needs a reason" }
        }

        fun read(json: JsonObject) = SpaceMessageReport(json.reportText("id"), json.reportText("channel_id"),
            json.reportText("message_id"), json.reportText("reporter"), json.reportText("reason"),
            json.get("received_at").toString().toLong())
    }
}

data class SpaceReportPage(val reports: List<SpaceMessageReport>, val next: Long?)

data class SpaceAdminCursor(val at: Long, val event: String) {
    init { require(at >= 0 && event.matches(Regex("[0-9a-f]{128}"))) }
}

data class SpaceAdminEvent(val cursor: SpaceAdminCursor, val actor: String, val action: String, val channel: String?, val member: String?) {
    init {
        require(SaitoWallet.isPublicKey(actor) && action.matches(Regex("[a-z][a-z-]{0,63}")))
        require(channel == null || channel.matches(Regex("[0-9a-f]{64}")))
        require(member == null || SaitoWallet.isPublicKey(member))
    }

    fun json() = JsonObject().apply {
        addProperty("event_id", cursor.event)
        addProperty("accepted_at", cursor.at)
        addProperty("actor", actor)
        addProperty("action", action)
        channel?.let { addProperty("channel_id", it) }
        member?.let { addProperty("member", it) }
    }

    override fun toString() = "Private community administration entry"

    companion object {
        fun read(json: JsonObject) = SpaceAdminEvent(SpaceAdminCursor(json.get("accepted_at").toString().toLong(), json.reportText("event_id")),
            json.reportText("actor"), json.reportText("action"),
            if (json.has("channel_id")) json.reportText("channel_id") else null,
            if (json.has("member")) json.reportText("member") else null)
    }
}

data class SpaceAdminPage(val events: List<SpaceAdminEvent>, val next: SpaceAdminCursor?)

data class SpaceScopedWriteRestriction(val channel: String?, val expiresAt: Long)

data class SpaceRestrictionOptions(val channels: Set<String>, val active: List<SpaceScopedWriteRestriction>)

private fun JsonObject.reportText(field: String) = get(field).also {
    require(it != null && it.isJsonPrimitive && it.asJsonPrimitive.isString)
}.asString
