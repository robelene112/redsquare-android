package nl.davidengelen.redsquare.spaces

import com.google.gson.JsonObject

/** A category's stable identity is carried by its signed control. */
data class SpaceCategoryProfile(val name: String) {
    init { require(name.isNotBlank() && name.toByteArray(Charsets.UTF_8).size <= 128) }
    fun json() = JsonObject().apply { addProperty("name", name) }
    companion object {
        fun fromJson(value: JsonObject): SpaceCategoryProfile {
            require(value.keySet() == setOf("name"))
            val name = value.get("name").also { require(it.isJsonPrimitive && it.asJsonPrimitive.isString) }.asString
            return SpaceCategoryProfile(name)
        }
    }
}



data class SpaceCategoryRecord(val profile: SpaceCategoryProfile?, val revision: String,
    val metadata: Map<String, String> = emptyMap(), val defaultGroup: String? = null)

/** Metadata audience stays fixed even when later visibility changes restrict a channel. */
internal fun SpaceChannelRecord.metadataGroupId(channel: String, snapshot: SpaceSnapshot): String =
    metadataChannelId ?: if (allowedMembers != null) channel else requireNotNull(snapshot.generalChannelId)

internal fun SpaceSnapshot.categoryGroups(category: String, defaultGroup: String?): Set<String> = buildSet {
    defaultGroup?.let(::add)
    channels.forEach { (id, record) ->
        if (record.categoryId == category && record.visibility == SpaceVisibility.PRIVATE) add(record.metadataGroupId(id, this@categoryGroups))
    }
}
