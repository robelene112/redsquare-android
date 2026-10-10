package nl.davidengelen.redsquare.saito

import com.google.gson.JsonObject
import java.net.URI

/** Display descriptors only. The signed envelope and original arrays remain unchanged. */
object RedSquareImages {
    const val MAX_COUNT = 4
    private val inlineRaster = Regex("data:image/(?:jpeg|jpg|png|webp|gif);base64,[A-Za-z0-9+/]*={0,2}", RegexOption.IGNORE_CASE)

    fun read(data: JsonObject): List<String> = data.get("images")?.takeIf { it.isJsonArray }?.asJsonArray
        ?.mapNotNull { value ->
            value.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.let(::safeSource)
        }?.take(MAX_COUNT).orEmpty()

    fun safeSource(source: String): String? {
        val value = source.trim()
        if (value.isEmpty() || value.any { it.isWhitespace() || it in "<>\\" }) return null
        if (value.startsWith("data:", ignoreCase = true)) return value.takeIf { inlineRaster.matches(it) }
        if (value.length > 8192) return null
        return try {
            val uri = URI(value)
            when {
                value.startsWith("/") && !value.startsWith("//") && !uri.isAbsolute && uri.rawAuthority == null -> value
                uri.scheme?.lowercase() in setOf("http", "https") && uri.host != null && uri.rawUserInfo == null &&
                    uri.port in -1..65535 -> value
                else -> null
            }
        } catch (_: Exception) { null }
    }
}
