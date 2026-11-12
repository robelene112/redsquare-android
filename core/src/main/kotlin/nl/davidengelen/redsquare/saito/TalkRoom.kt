package nl.davidengelen.redsquare.saito

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.net.URLDecoder
import java.util.Base64

enum class TalkMode(val wire: String) { VOICE("voice"), VIDEO("video") }

/** Public invitation metadata. Opening it is neither joining nor permission to capture media. */
data class TalkRoom(val id: String, val host: String, val peers: List<String> = emptyList(),
    val mode: TalkMode = TalkMode.VIDEO) {
    init {
        require(SaitoWallet.isPublicKey(id) && SaitoWallet.isPublicKey(host))
        require(peers.size <= MAX_PEERS && peers.distinct().size == peers.size && peers.all(SaitoWallet::isPublicKey))
    }

    fun json() = JsonObject().apply {
        addProperty("call_id", id); addProperty("host_public_key", host)
        add("call_peers", com.google.gson.JsonArray().apply { peers.forEach(::add) })
        addProperty("ui", mode.wire)
    }

    companion object {
        const val MAX_PEERS = 32
        fun read(body: JsonObject): TalkRoom {
            val peers = body.get("call_peers")
            require(peers == null || peers.isJsonArray && peers.asJsonArray.size() <= MAX_PEERS)
            val mode = body.get("ui")?.let { string(body, "ui") } ?: "video"
            require(mode in setOf("voice", "video", "large"))
            return TalkRoom(string(body, "call_id"), string(body, "host_public_key"),
                peers?.asJsonArray?.map { value ->
                    require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
                    value.asString
                }?.distinct().orEmpty(), if (mode == "voice") TalkMode.VOICE else TalkMode.VIDEO)
        }

        internal fun string(body: JsonObject, name: String): String {
            val value = body.get(name)
            require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString)
            return value.asString
        }
    }
}

/** Approved provider belongs to this invitation, without changing the app's saved node. */
data class TalkLink(val backend: SaitoBackend, val room: TalkRoom) {
    fun url(): String = backend.webUrl + "/videocall/?stun_video_chat=" +
        Base64.getEncoder().encodeToString(room.json().toString().toByteArray(Charsets.UTF_8))

    companion object {
        fun parse(url: String): TalkLink? = runCatching {
            require(url.length <= 16_384 && url.none { it.isWhitespace() || it.isISOControl() })
            val uri = URI(url)
            require(uri.scheme in setOf("https", "http") && uri.rawUserInfo == null && uri.rawFragment == null)
            require(uri.port == -1 || uri.port == if (uri.scheme == "https") 443 else 80)
            val backend = SaitoBackend.entries.singleOrNull { it.domain.equals(uri.host, true) } ?: return null
            require(uri.rawPath == uri.path && uri.path.removeSuffix("/") == "/videocall")
            val query = requireNotNull(uri.rawQuery).split('&')
            require(query.size <= 16)
            val parameters = query.map { it.substringBefore('=') to it.substringAfter('=', "") }
            require(parameters.all { it.first == "stun_video_chat" || it.first.startsWith("utm_") })
            val encoded = parameters.single { it.first == "stun_video_chat" }.second
            // Legacy links contain ordinary base64; literal '+' must not become a form-space.
            val decoded = URLDecoder.decode(encoded.replace("+", "%2B"), "UTF-8")
            val bytes = Base64.getDecoder().decode(decoded)
            require(bytes.size <= 12_288)
            val text = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
            val body = JsonParser.parseString(text)
            require(body.isJsonObject)
            TalkLink(backend, TalkRoom.read(body.asJsonObject))
        }.getOrNull()
    }
}
