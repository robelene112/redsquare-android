package nl.davidengelen.redsquare.spaces

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import nl.davidengelen.redsquare.saito.SaitoWallet
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64

/** Archive-only recovery must not clone a live MLS sender to another device. */
class SpaceHistoryKeys internal constructor(val owner: String, val scope: SpaceCrypto.Scope,
    private val keys: Map<Long, ByteArray>) {
    fun read(envelope: String): SpaceCrypto.PlainRecord = SpaceCrypto.readArchived(scope, keys, envelope)
    /** Secret portable bytes. Encrypt the artifact before exporting it. */
    fun save(): ByteArray = JsonObject().apply {
        addProperty("version", 1); addProperty("owner", owner); add("scope", scope.json())
        add("archive_keys", JsonObject().apply {
            keys.toSortedMap().forEach { (epoch, key) -> addProperty(epoch.toString(), Base64.getEncoder().encodeToString(key)) }
        })
    }.toString().toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_BYTES) }
    override fun toString() = "Private Space history keys"
    companion object {
        private const val MAX_BYTES = 2 * 1024 * 1024
        fun restore(bytes: ByteArray, expectedOwner: String): SpaceHistoryKeys {
            require(bytes.size in 1..MAX_BYTES && SaitoWallet.isPublicKey(expectedOwner))
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            val value = GsonBuilder().setStrictness(Strictness.STRICT).create().fromJson(text, JsonObject::class.java)
            require(value.keySet() == setOf("version", "owner", "scope", "archive_keys") && value.get("version").toString() == "1")
            val owner = value.get("owner").also { require(it.isJsonPrimitive && it.asJsonPrimitive.isString) }.asString
            require(owner == expectedOwner) { "History keys belong to a different account" }
            val scope = SpaceCrypto.Scope.fromJson(value.getAsJsonObject("scope"))
            val stored = value.getAsJsonObject("archive_keys")
            require(stored.size() in 1..16_384)
            val keys = stored.entrySet().associate { (epoch, key) ->
                require(epoch.matches(Regex("0|[1-9][0-9]{0,18}")) && key.isJsonPrimitive && key.asJsonPrimitive.isString)
                epoch.toLong() to Base64.getDecoder().decode(key.asString).also { require(it.size == 32) }
            }
            return SpaceHistoryKeys(owner, scope, keys)
        }
    }
}
