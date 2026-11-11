package nl.davidengelen.redsquare.saito

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness

/** Directory labels identify requested public keys; they are not registration-ownership proofs. */
object SaitoNameDirectory {
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()
    private val identifier = Regex("[A-Za-z0-9]{1,45}@saito")

    fun readMappings(response: String, requestedKeys: Collection<String>): Map<String, String> {
        require(requestedKeys.size in 1..50 && requestedKeys.toSet().size == requestedKeys.size)
        require(requestedKeys.all(SaitoWallet::isPublicKey))
        require(response.toByteArray(Charsets.UTF_8).size <= 64 * 1024)
        val entries = requireNotNull(gson.fromJson(response, JsonObject::class.java))
        val error = entries.get("err")
        require(error == null || (error.isJsonPrimitive && error.asJsonPrimitive.isString && error.asString.isEmpty())) {
            "Name directory returned an error"
        }
        val requested = requestedKeys.toSet()
        return buildMap {
            for ((key, value) in entries.entrySet()) {
                if (key in requested && value.isJsonPrimitive && value.asJsonPrimitive.isString &&
                    identifier.matches(value.asString)) put(key, value.asString)
            }
        }
    }
}
