package nl.davidengelen.redsquare.spaces

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import nl.davidengelen.redsquare.saito.SaitoWallet
import java.net.URI
import java.util.Base64

/** Public routing only. Opening verifies the selected service and its signed
 * history; a reference never carries admission authority or changes the node. */
data class SpacePublicReference(val node: String, val serviceKey: String, val spaceId: String) {
    init {
        require(node.isNotBlank() && node.toByteArray(Charsets.UTF_8).size <= 2048)
        require(SaitoWallet.isPublicKey(serviceKey) && spaceId.matches(Regex("[0-9a-f]{64}")))
    }
    fun link(): String {
        val body = JsonObject().apply {
            addProperty("version", 1); addProperty("node", node); addProperty("service", serviceKey); addProperty("space", spaceId)
        }
        return "saito://spaces/community#" + Base64.getUrlEncoder().withoutPadding().encodeToString(body.toString().toByteArray(Charsets.UTF_8))
    }
    companion object {
        private val GSON = GsonBuilder().setStrictness(Strictness.STRICT).create()
        fun parse(link: String): SpacePublicReference {
            require(link.length in 1..8192)
            val uri = URI(link)
            require(uri.scheme == "saito" && uri.host == "spaces" && uri.path == "/community" &&
                uri.rawQuery == null && uri.userInfo == null && uri.port == -1)
            val decoded = Base64.getUrlDecoder().decode(requireNotNull(uri.rawFragment))
            require(decoded.size <= 4096)
            val body = GSON.fromJson(decoded.toString(Charsets.UTF_8), JsonObject::class.java)
            require(body.keySet() == setOf("version", "node", "service", "space") && body.get("version").toString() == "1")
            fun text(key: String) = body.get(key).also { require(it.isJsonPrimitive && it.asJsonPrimitive.isString) }.asString
            return SpacePublicReference(text("node"), text("service"), text("space"))
        }
    }
}
