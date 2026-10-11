package nl.davidengelen.redsquare.registry

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import nl.davidengelen.redsquare.saito.*

/** Cached ownership is revalidated, with the source/time retained separately from the proof. */
internal class RegisteredNameStore(context: Context, private val owner: String, private val registrar: String) {
    init { require(owner.matches(Regex("[1-9A-HJ-NP-Za-km-z]{43,45}"))) }
    private val file = EncryptedLocalFile(context, "namemap.$owner.v1", "redsquare.namemap.v1", "namemap.")
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()
    data class Saved(val name: SaitoRegisteredName, val backend: SaitoBackend, val checkedAt: Long)

    fun load(): Saved? = synchronized(SaitoLocalState.lock) {
        val contents = file.load() ?: return@synchronized null
        require(contents.version == 1.toByte())
        val record = requireNotNull(gson.fromJson(contents.text, JsonObject::class.java))
        require(record.keySet() == setOf("proof", "backend", "checkedAt"))
        val proof = text(record, "proof")
        val source = SaitoBackend.entries.single { it.id == text(record, "backend") }
        val time = record.get("checkedAt")
        require(time.isJsonPrimitive && time.asJsonPrimitive.isNumber)
        val checkedAt = time.asBigDecimal.longValueExact().also { require(it >= 0) }
        Saved(validate(proof), source, checkedAt)
    }

    fun save(name: SaitoRegisteredName, backend: SaitoBackend): Saved = synchronized(SaitoLocalState.lock) {
        require(validate(name.proof) == name && name.active)
        val saved = Saved(name, backend, System.currentTimeMillis().also { require(it >= 0) })
        val record = JsonObject().apply {
            addProperty("proof", name.proof); addProperty("backend", backend.id); addProperty("checkedAt", saved.checkedAt)
        }
        file.save(EncryptedLocalFile.Contents(1, record.toString()))
        check(load() == saved)
        saved
    }

    private fun validate(proof: String): SaitoRegisteredName {
        require(proof.toByteArray(Charsets.UTF_8).size <= 64 * 1024)
        val row = requireNotNull(gson.fromJson(proof, JsonObject::class.java))
        val name = requireNotNull(SaitoRegistry.readNamecheck("[$proof]", text(row, "identifier"), registrar))
        require(name.publicKey == owner && name.active)
        return name
    }

    private fun text(record: JsonObject, field: String): String {
        val value = record.get(field)
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
        return value.asString
    }
}
