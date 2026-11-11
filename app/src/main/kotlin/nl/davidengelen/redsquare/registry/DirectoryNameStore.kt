package nl.davidengelen.redsquare.registry

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.Strictness
import nl.davidengelen.redsquare.saito.*
import java.security.MessageDigest

/** Last-known directory labels, never a source of registered ownership or wallet identity. */
internal class DirectoryNameStore(context: Context, owner: String, backend: SaitoBackend,
    registrar: String, selectedEndpoint: String, private val directoryEndpoint: String?) {
    data class Saved(val key: String, val name: String, val origin: IdentityNameOrigin, val checkedAt: Long)
    private val namespace = listOf(owner, backend.id, registrar, selectedEndpoint, directoryEndpoint.orEmpty()).joinToString("\n")
    private val digest = MessageDigest.getInstance("SHA-256").digest(namespace.toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private val file = EncryptedLocalFile(context, "displaynames.$digest.v1", "redsquare.displaynames.v1", "displaynames.")
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()

    fun load(): List<Saved> = synchronized(SaitoLocalState.lock) {
        val contents = file.load() ?: return@synchronized emptyList()
        require(contents.version == 1.toByte())
        val rows = requireNotNull(gson.fromJson(contents.text, JsonArray::class.java))
        require(rows.size() <= LIMIT)
        rows.mapNotNull { value ->
            try {
                val row = value.asJsonObject
                require(row.keySet() == setOf("key", "name", "origin", "checkedAt"))
                val key = text(row, "key")
                val name = text(row, "name")
                val map = JsonObject().apply { addProperty(key, name) }
                require(SaitoNameDirectory.readMappings(map.toString(), listOf(key))[key] == name)
                val origin = IdentityNameOrigin.valueOf(text(row, "origin"))
                require(origin == IdentityNameOrigin.SELECTED_NODE ||
                    (origin == IdentityNameOrigin.DIRECTORY && directoryEndpoint != null))
                val time = row.get("checkedAt")
                require(time.isJsonPrimitive && time.asJsonPrimitive.isNumber)
                val checkedAt = time.asBigDecimal.longValueExact()
                require(checkedAt in 0..System.currentTimeMillis())
                Saved(key, name, origin, checkedAt)
            } catch (_: Exception) { null } // A damaged entry cannot hide the other saved names.
        }.distinctBy { it.key }
    }

    fun save(incoming: List<Saved>) = synchronized(SaitoLocalState.lock) {
        // This disposable cache may be rebuilt; it never repairs or overwrites the ownership store.
        val existing = try { load() } catch (_: Exception) { emptyList() }
        val merged = existing.associateBy { it.key }.toMutableMap()
        incoming.forEach { next ->
            val previous = merged[next.key]
            if (previous?.origin != IdentityNameOrigin.DIRECTORY || next.origin == IdentityNameOrigin.DIRECTORY)
                merged[next.key] = next
        }
        val rows = JsonArray().apply {
            merged.values.sortedByDescending { it.checkedAt }.take(LIMIT).forEach { saved ->
                add(JsonObject().apply {
                    addProperty("key", saved.key); addProperty("name", saved.name)
                    addProperty("origin", saved.origin.name); addProperty("checkedAt", saved.checkedAt)
                })
            }
        }
        file.save(EncryptedLocalFile.Contents(1, rows.toString()))
    }

    private fun text(row: JsonObject, field: String): String {
        val value = row.get(field)
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
        return value.asString
    }

    companion object { const val LIMIT = 500 }
}
