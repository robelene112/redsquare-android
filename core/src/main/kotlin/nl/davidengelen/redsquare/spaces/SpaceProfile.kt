package nl.davidengelen.redsquare.spaces

import com.google.gson.JsonObject
import java.security.MessageDigest
import java.util.Base64

enum class SpaceVisibility { PUBLIC, PRIVATE }

/** Community metadata has an explicit audience, independent of listing. */
data class SpaceProfile(val name: String, val description: String = "",
    val visibility: SpaceVisibility = SpaceVisibility.PRIVATE,
    val listed: Boolean = visibility == SpaceVisibility.PUBLIC, val houseRules: String = "", val image: String? = null) {
    init {
        require(name.isNotBlank() && name.toByteArray(Charsets.UTF_8).size <= 256)
        require(description.toByteArray(Charsets.UTF_8).size <= 4096)
        require(visibility == SpaceVisibility.PUBLIC || !listed)
        require(houseRules.toByteArray(Charsets.UTF_8).size <= 4096 && (houseRules.isEmpty() || houseRules.isNotBlank()))
        image?.let { source ->
            require(source.startsWith(IMAGE_PREFIX) && source.length <= IMAGE_CHARACTER_LIMIT)
            val encoded = source.removePrefix(IMAGE_PREFIX)
            val bytes = Base64.getDecoder().decode(encoded)
            require(bytes.size in 4..MAX_IMAGE_BYTES && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() &&
                bytes[bytes.size - 2] == 0xff.toByte() && bytes.last() == 0xd9.toByte())
            require(Base64.getEncoder().encodeToString(bytes) == encoded)
        }
        require(json().toString().toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
    }

    /** Only intentionally public rules may have a plaintext-derived review token. */
    val publicRulesToken: String get() {
        require(visibility == SpaceVisibility.PUBLIC)
        return if (houseRules.isEmpty()) "" else MessageDigest.getInstance("SHA-256")
            .digest(houseRules.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    fun json() = JsonObject().apply {
        addProperty("version", 1); addProperty("name", name); addProperty("description", description)
        addProperty("visibility", if (visibility == SpaceVisibility.PUBLIC) "public" else "private")
        addProperty("listed", listed); addProperty("house_rules", houseRules)
        image?.let { addProperty("image", it) }
    }

    companion object {
        const val MAX_BYTES = 32 * 1024
        const val MAX_IMAGE_BYTES = 16 * 1024
        const val IMAGE_PREFIX = "data:image/jpeg;base64,"
        val IMAGE_CHARACTER_LIMIT = IMAGE_PREFIX.length + 4 * ((MAX_IMAGE_BYTES + 2) / 3)
        fun fromJson(value: JsonObject): SpaceProfile {
            val required = setOf("version", "name", "description", "visibility", "listed")
            require(value.keySet().containsAll(required) && value.keySet().all { it in required || it in setOf("house_rules", "image") })
            require(value.get("version").toString() == "1")
            fun text(key: String) = value.get(key).also {
                require(it.isJsonPrimitive && it.asJsonPrimitive.isString)
            }.asString
            val visibility = when (text("visibility")) {
                "public" -> SpaceVisibility.PUBLIC
                "private" -> SpaceVisibility.PRIVATE
                else -> throw IllegalArgumentException("Unknown community visibility")
            }
            val listed = value.get("listed").also { require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) }.asBoolean
            return SpaceProfile(text("name"), text("description"), visibility, listed,
                if (value.has("house_rules")) text("house_rules") else "",
                value.get("image")?.takeUnless { it.isJsonNull }?.let { text("image") })
        }
    }
}
