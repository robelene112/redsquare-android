package nl.davidengelen.redsquare

import com.google.gson.JsonParser
import com.google.gson.JsonObject

/** Select an independently signed custom key/file without changing its synthetic current holding. */
internal fun vaultCustomReference(original: JsonObject): JsonObject = original.deepCopy().apply {
    val custom = original.getAsJsonObject("custom")
    addProperty("name", custom.get("name").asString)
    add("key", custom.getAsJsonObject("key").deepCopy())
    add("file", custom.getAsJsonObject("file").deepCopy())
    add("script", JsonParser.parseString(custom.get("script").asString))
    addProperty("scriptHash", custom.get("hash").asString)
}
