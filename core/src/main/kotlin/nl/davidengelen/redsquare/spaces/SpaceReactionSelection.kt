package nl.davidengelen.redsquare.spaces

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.Strictness

/** The author's complete desired selection, rather than a non-idempotent toggle. */
internal object SpaceReactionSelection {
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()
    fun encode(emojis: Set<String>): String = JsonArray().apply { emojis.sorted().forEach(::add) }.toString().also { decode(it) }
    fun decode(text: String): Set<String> {
        require(text.toByteArray(Charsets.UTF_8).size <= 2048)
        val element = try { gson.fromJson(text, JsonElement::class.java) }
        catch (failure: JsonParseException) { throw IllegalArgumentException("Invalid reaction selection", failure) }
        val array = requireNotNull(element).also { require(it.isJsonArray) }.asJsonArray
        require(array.size() <= 16)
        return array.map { item ->
            require(item.isJsonPrimitive && item.asJsonPrimitive.isString)
            item.asString.also { value ->
                require(Charsets.UTF_8.newEncoder().canEncode(value))
                require(value.isNotBlank() && value.toByteArray(Charsets.UTF_8).size <= 32 && value.none { Character.isISOControl(it) })
            }
        }.toSet().also { require(it.size == array.size()) }
    }
}
