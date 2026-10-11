package nl.davidengelen.redsquare.registry

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.Strictness
import nl.davidengelen.redsquare.saito.SaitoPeer
import nl.davidengelen.redsquare.saito.SaitoRegistry
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Hints locate records; only the registrar-signed namecheck establishes ownership. */
internal class RegistryLookup(private val peer: SaitoPeer, private val registrar: String) {
    suspend fun namecheck(identifier: String) = SaitoRegistry.readNamecheck(response(JsonObject().apply {
        addProperty("request", "registry namecheck"); addProperty("identifier", identifier)
    }), identifier, registrar)

    suspend fun hints(publicKeys: Collection<String>): Map<String, String> {
        require(publicKeys.size in 1..50 && publicKeys.toSet().size == publicKeys.size)
        val text = response(JsonObject().apply {
            addProperty("request", "registry query")
            add("keys", JsonArray().apply { publicKeys.forEach { add(it) } })
        })
        val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()
        val hints = requireNotNull(gson.fromJson(text, JsonObject::class.java))
        require(hints.keySet().all { it in publicKeys })
        return hints.entrySet().associate { (key, hint) ->
            require(hint.isJsonPrimitive && hint.asJsonPrimitive.isString)
            key to hint.asString.also { require(it.matches(Regex("[A-Za-z0-9]{1,45}@saito"))) }
        }
    }

    private suspend fun response(data: JsonObject): String {
        val query = JsonObject().apply { addProperty("request", "registry query"); add("data", data) }
        val response = peer.request(query.toString().toByteArray(Charsets.UTF_8))
        require(response.size <= 64 * 1024)
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(response)).toString()
    }
}
