package nl.davidengelen.redsquare.spaces

import com.google.gson.JsonObject

enum class SpaceVisibility { PUBLIC, PRIVATE }

/** Community metadata has an explicit audience, independent of listing. */
data class SpaceProfile(val name: String, val description: String = "",
    val visibility: SpaceVisibility = SpaceVisibility.PRIVATE,
    val listed: Boolean = visibility == SpaceVisibility.PUBLIC) {
    init {
        require(name.isNotBlank() && name.toByteArray(Charsets.UTF_8).size <= 256)
        require(description.toByteArray(Charsets.UTF_8).size <= 4096)
        require(visibility == SpaceVisibility.PUBLIC || !listed)
    }

    fun json() = JsonObject().apply {
        addProperty("version", 1); addProperty("name", name); addProperty("description", description)
        addProperty("visibility", if (visibility == SpaceVisibility.PUBLIC) "public" else "private")
        addProperty("listed", listed)
    }

    companion object {
        fun fromJson(value: JsonObject): SpaceProfile {
            require(value.keySet() == setOf("version", "name", "description", "visibility", "listed"))
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
            return SpaceProfile(text("name"), text("description"), visibility, listed)
        }
    }
}
