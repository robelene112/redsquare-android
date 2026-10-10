package nl.davidengelen.redsquare.feed

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoLocalState
import nl.davidengelen.redsquare.saito.EncryptedLocalFile

/** Unpublished profile changes stay encrypted and tied to their wallet and server. */
internal class ProfileDraftStore(context: Context, owner: String, backend: SaitoBackend) {
    init { require(owner.matches(Regex("[1-9A-HJ-NP-Za-km-z]{43,45}"))) }
    private val file = EncryptedLocalFile(context, "profiledraft.${backend.id}.$owner.v1",
        "redsquare.profiledraft.v1", "profiledraft.")
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()

    fun load(): JsonObject = synchronized(SaitoLocalState.lock) {
        val contents = file.load() ?: return@synchronized JsonObject()
        require(contents.version == 1.toByte())
        requireNotNull(gson.fromJson(contents.text, JsonObject::class.java)).also(::validate)
    }

    fun save(changes: JsonObject) = synchronized(SaitoLocalState.lock) {
        validate(changes)
        file.save(EncryptedLocalFile.Contents(1, changes.toString()))
        check(load() == changes) { "Profile draft persistence verification failed" }
    }

    private fun validate(changes: JsonObject) {
        require(changes.keySet().all { it == "description" })
        changes.get("description")?.let { require(it.isJsonPrimitive && it.asJsonPrimitive.isString) }
    }
}
