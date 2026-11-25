package nl.davidengelen.redsquare.spaces

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import java.net.URI
import java.util.Base64

/** An exact address for content or a channel. The address itself grants no access. */
data class SpaceDestination(val community: SpacePublicReference, val channel: String, val kind: Kind,
    val record: String? = null, val thread: String? = null) {
    enum class Kind(val wire: String, val receiptKind: String?) {
        CHANNEL("channel", null),
        ROOM("room", null),
        MESSAGE("message", "message"),
        THREAD("thread", "message"),
        ARTICLE("article", "article"),
        FILE("file", "file");

        fun requiresRecord() = receiptKind != null

        fun acceptsChannel(kind: SpaceChannelKind?) = when (this) {
            CHANNEL -> true
            ROOM -> kind == SpaceChannelKind.VOICE
            MESSAGE, THREAD -> kind == null || kind == SpaceChannelKind.CHAT || kind == SpaceChannelKind.ANNOUNCEMENT
            ARTICLE -> kind == SpaceChannelKind.ARTICLE
            FILE -> kind == SpaceChannelKind.FILE
        }

        fun expectedRecord(thread: String?) = if (thread != null) "thread-reply" else receiptKind

        fun validateAddress(record: String?, thread: String?) {
            require(requiresRecord() == (record != null))
            require(record == null || record.matches(RECORD_ID))
            require(thread == null || this == MESSAGE && thread.matches(RECORD_ID) && record != thread)
        }

        companion object {
            fun read(wire: String) = requireNotNull(entries.firstOrNull { it.wire == wire })
        }
    }

    init {
        require(channel.matches(CHANNEL_ID))
        kind.validateAddress(record, thread)
    }

    fun link(): String {
        val data = JsonObject().apply {
            addProperty("version", 1)
            addProperty("node", community.node)
            addProperty("service", community.serviceKey)
            addProperty("space", community.spaceId)
            addProperty("channel", channel)
            addProperty("kind", kind.wire)
            record?.let { addProperty("record", it) }
            thread?.let { addProperty("thread", it) }
        }
        return "saito://spaces/target#" + Base64.getUrlEncoder().withoutPadding().encodeToString(data.toString().toByteArray(Charsets.UTF_8))
    }

    override fun toString() = "Space destination"

    companion object {
        private val CHANNEL_ID = Regex("[0-9a-f]{64}")
        private val RECORD_ID = Regex("[0-9a-f]{128}")
        private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()

        fun parse(link: String): SpaceDestination {
            require(link.length in 1..8192)
            val uri = URI(link)
            require(uri.scheme == "saito" && uri.host == "spaces" && uri.rawPath == "/target" &&
                uri.rawQuery == null && uri.userInfo == null && uri.port == -1)
            val decoded = Base64.getUrlDecoder().decode(requireNotNull(uri.rawFragment))
            require(decoded.size <= 4096)
            val data = requireNotNull(gson.fromJson(decoded.toString(Charsets.UTF_8), JsonObject::class.java))
            val required = setOf("version", "node", "service", "space", "channel", "kind")
            require(data.keySet().containsAll(required) && data.keySet().all { it in required || it == "record" || it == "thread" })
            require(data.get("version").toString() == "1")
            fun text(field: String) = data.get(field).also {
                require(it != null && it.isJsonPrimitive && it.asJsonPrimitive.isString)
            }.asString
            return SpaceDestination(SpacePublicReference(text("node"), text("service"), text("space")),
                text("channel"), Kind.read(text("kind")), if (data.has("record")) text("record") else null,
                if (data.has("thread")) text("thread") else null)
        }
    }
}
