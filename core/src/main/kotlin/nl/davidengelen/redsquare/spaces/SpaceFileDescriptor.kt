package nl.davidengelen.redsquare.spaces

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Base64

/** Private channels protect this descriptor in the same archive envelope as channel messages. */
class SpaceFileDescriptor(val name: String, val mime: String, val sealed: SpaceFileCipher.Sealed) {
    init {
        require(name.isNotBlank() && name.toByteArray(Charsets.UTF_8).size <= 255 &&
            name.none { it.isISOControl() || it == '/' || it == '\\' } && name !in setOf(".", ".."))
        require(mime.length <= 127 && mime.matches(MIME))
    }

    fun encode(): String = JsonObject().apply {
        addProperty("version", 1)
        addProperty("name", name)
        addProperty("mime", mime)
        addProperty("object", sealed.id)
        addProperty("sha256", sealed.sha256)
        addProperty("size", sealed.size)
        addProperty("key", Base64.getEncoder().encodeToString(sealed.keyBytes()))
    }.toString()

    override fun toString() = "Space file descriptor"

    companion object {
        fun read(value: String): SpaceFileDescriptor {
            require(value.length <= 2048)
            val body = JsonParser.parseString(value).asJsonObject
            require(body.keySet() == setOf("version", "name", "mime", "object", "sha256", "size", "key") &&
                body.storageNumber("version") == 1L)
            val key = body.storageText("key")
            require(key.matches(Regex("[a-zA-Z0-9+/]{43}=")))
            return SpaceFileDescriptor(body.storageText("name"), body.storageText("mime"),
                SpaceFileCipher.Sealed(body.storageText("object"), body.storageText("sha256"),
                    body.storageNumber("size"), Base64.getDecoder().decode(key)))
        }

        private val MIME = Regex("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+")
    }
}
