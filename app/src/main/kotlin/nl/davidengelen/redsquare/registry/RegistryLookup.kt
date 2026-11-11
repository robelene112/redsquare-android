package nl.davidengelen.redsquare.registry

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.yield
import nl.davidengelen.redsquare.saito.SaitoRegisteredName
import nl.davidengelen.redsquare.saito.SaitoPeer
import nl.davidengelen.redsquare.saito.SaitoNameDirectory
import nl.davidengelen.redsquare.saito.SaitoRegistry
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Hints locate records; only the registrar-signed namecheck establishes ownership. */
internal class RegistryLookup(private val peer: SaitoPeer, private val registrar: String) {
    suspend fun namecheck(identifier: String): SaitoRegisteredName? {
        val text = response(JsonObject().apply {
            addProperty("request", "registry namecheck"); addProperty("identifier", identifier)
        }, awaitRegistrar = true)
        // An eager caller can enqueue the request before yielding ownership work to its dispatcher.
        yield()
        return SaitoRegistry.readNamecheck(text, identifier, registrar)
    }

    suspend fun hints(publicKeys: Collection<String>): Map<String, String> {
        require(publicKeys.size in 1..50 && publicKeys.toSet().size == publicKeys.size)
        val text = response(JsonObject().apply {
            addProperty("request", "registry query")
            add("keys", JsonArray().apply { publicKeys.forEach { add(it) } })
        })
        return SaitoNameDirectory.readMappings(text, publicKeys)
    }

    private suspend fun response(data: JsonObject, awaitRegistrar: Boolean = false): String {
        val query = JsonObject().apply { addProperty("request", "registry query"); add("data", data) }
        val message = query.toString().toByteArray(Charsets.UTF_8)
        val response = if (awaitRegistrar) peer.requestRegistryNamecheck(message) else peer.request(message)
        require(response.size <= 64 * 1024)
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(response)).toString()
    }
}
