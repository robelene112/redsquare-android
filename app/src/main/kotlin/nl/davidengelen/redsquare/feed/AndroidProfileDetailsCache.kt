package nl.davidengelen.redsquare.feed

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoProfile
import nl.davidengelen.redsquare.saito.SaitoProfiles

/** Public signed profile metadata, isolated by author and selected server. */
internal class AndroidProfileDetailsCache(context: Context, backend: SaitoBackend, private val author: String) {
    init { require(author.matches(Regex("[1-9A-HJ-NP-Za-km-z]{43,45}"))) }
    private val avatars = AvatarProfileCache(context, backend)
    private val node = backend
    private val storage = PublicStringStore(context, "profilemeta.${backend.id}.$author.v1", 100)
    data class Saved(val profile: SaitoProfile, val unreadable: Boolean)

    fun load(): Saved {
        val saved = decode(storage.load())
        return saved.copy(profile = SaitoProfiles.merge(saved.profile, avatars.load(author)))
    }

    private fun decode(saved: PublicStringStore.Loaded): Saved {
        return try {
            val rows = JsonArray().apply {
                saved.values.forEach { envelope -> add(JsonObject().apply { addProperty("tx", envelope) }) }
            }
            val profile = SaitoProfiles.readArchive(rows.toString(), author)
            Saved(profile, saved.unreadable || profile.ignored > 0)
        } catch (_: Exception) { Saved(SaitoProfiles.readArchive("[]", author), true) }
    }

    /** Keep the latest verified snapshot even if another reader saves an older response. */
    fun save(profile: SaitoProfile): SaitoProfile {
        require(profile.author == author)
        var merged = profile
        storage.update { previous ->
            merged = SaitoProfiles.merge(SaitoProfiles.merge(decode(previous).profile, avatars.load(author)), profile)
            merged.updates.map { it.envelope }
        }
        updates.tryEmit(node to author)
        return merged
    }
    companion object {
        private val updates = kotlinx.coroutines.flow.MutableSharedFlow<Pair<SaitoBackend, String>>(
            extraBufferCapacity = 64, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
        val changes: kotlinx.coroutines.flow.SharedFlow<Pair<SaitoBackend, String>> = updates
    }
}
