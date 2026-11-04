package nl.davidengelen.redsquare.feed

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Bounded public discovery cache. Retain signed evidence, never unsigned picture hints. */
internal class AvatarProfileCache(context: Context, backend: SaitoBackend) {
    private val storage = PublicStringStore(context, "avatarprofiles.${backend.id}.v1", 64)

    fun load(author: String): SaitoProfile = find(storage.load().values, author)

    suspend fun save(profile: SaitoProfile): SaitoProfile {
        val session = currentCoroutineContext()
        return synchronized(SaitoLocalState.lock) {
            session.ensureActive()
            var merged = profile
            storage.update { previous ->
                merged = SaitoProfiles.merge(find(previous.values, profile.author), profile)
                val record = JsonObject().apply {
                    addProperty("author", profile.author)
                    add("rows", JsonArray().apply { merged.updates.forEach { update ->
                        add(JsonObject().apply { addProperty("tx", update.envelope) })
                    } })
                }
                listOf(record.toString()) + previous.values.filter { author(it) != profile.author }.take(63)
            }
            merged
        }
    }

    private fun author(value: String): String? = try { JsonParser.parseString(value).asJsonObject.get("author").asString }
        catch (_: Exception) { null }

    private fun find(values: List<String>, key: String): SaitoProfile {
        for (value in values) try {
            val record = JsonParser.parseString(value).asJsonObject
            if (record.get("author").asString == key) return SaitoProfiles.readArchive(record.get("rows").toString(), key)
        } catch (_: Exception) { /* Unavailable cache evidence cannot supply a picture. */ }
        return SaitoProfiles.readArchive("[]", key)
    }
}
