package nl.davidengelen.redsquare.spaces

import com.google.gson.JsonObject
import java.util.Locale

enum class SpaceChannelKind { CHAT, ANNOUNCEMENT, VOICE, ARTICLE, FILE }

/** Display metadata has channel identity and audience supplied by its signed control. */
data class SpaceChannelProfile(val name: String, val kind: SpaceChannelKind = SpaceChannelKind.CHAT, val category: String = "") {
    init {
        require(name.isNotBlank() && name.toByteArray(Charsets.UTF_8).size <= 128)
        require(category.toByteArray(Charsets.UTF_8).size <= 128)
    }
    fun json() = JsonObject().apply {
        addProperty("version", 1); addProperty("name", name)
        addProperty("kind", kind.name.lowercase(Locale.ROOT)); addProperty("category", category)
    }
    companion object {
        fun fromJson(value: JsonObject): SpaceChannelProfile {
            require(value.keySet() == setOf("version", "name", "kind", "category") && value.get("version").toString() == "1")
            fun text(field: String) = value.get(field).also { require(it.isJsonPrimitive && it.asJsonPrimitive.isString) }.asString
            val kind = requireNotNull(SpaceChannelKind.entries.find { it.name.lowercase(Locale.ROOT) == text("kind") })
            return SpaceChannelProfile(text("name"), kind, text("category"))
        }
    }
}
