package nl.davidengelen.redsquare.feed

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoLocalState
import nl.davidengelen.redsquare.saito.EncryptedLocalFile
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction

/** Unpublished profile changes stay encrypted and tied to their wallet and server. */
internal class ProfileDraftStore(private val context: Context, private val owner: String, private val backend: SaitoBackend) {
    init { require(owner.matches(Regex("[1-9A-HJ-NP-Za-km-z]{43,45}"))) }
    private val file = EncryptedLocalFile(context, "profiledraft.${backend.id}.$owner.v1",
        "redsquare.profiledraft.v1", "profiledraft.")
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()

    data class Saved(val changes: JsonObject = JsonObject(), val transaction: SaitoTransaction? = null,
        val available: Boolean = false) {
        val hasWork: Boolean get() = !available && (changes.size() > 0 || transaction != null)
    }

    fun load(): Saved = synchronized(SaitoLocalState.lock) {
        val contents = file.load() ?: return@synchronized Saved()
        val record = requireNotNull(gson.fromJson(contents.text, JsonObject::class.java))
        val saved = when (contents.version) {
            1.toByte() -> Saved(record) // Existing v1 contains changes directly, not a wrapper.
            2.toByte() -> {
                require(record.keySet().all { it in setOf("changes", "transaction", "available") })
                val transaction = record.get("transaction")?.let {
                    require(it.isJsonPrimitive && it.asJsonPrimitive.isString)
                    SaitoTransaction.restore(it.asString)
                }
                val flag = record.get("available")
                require(flag.isJsonPrimitive && flag.asJsonPrimitive.isBoolean)
                Saved(record.getAsJsonObject("changes"), transaction, flag.asBoolean)
            }
            else -> error("Unknown profile draft version")
        }
        saved.also(::validate)
    }

    fun save(saved: Saved) = synchronized(SaitoLocalState.lock) {
        validate(saved)
        val record = JsonObject().apply {
            add("changes", saved.changes.deepCopy())
            saved.transaction?.let { addProperty("transaction", it.encoded()) }
            addProperty("available", saved.available)
        }
        file.save(EncryptedLocalFile.Contents(2, record.toString()))
        check(same(load(), saved)) { "Profile draft persistence verification failed" }
    }

    fun claim(id: String): Boolean = synchronized(SaitoLocalState.lock) {
        if (nl.davidengelen.redsquare.saito.AndroidBackendStore(context).load() != backend ||
            nl.davidengelen.redsquare.AndroidWalletStore(context).loadExisting().publicKey != owner) return@synchronized false
        val saved = load()
        saved.transaction?.id == id && !saved.available
    }

    fun confirmAvailable(id: String): Boolean = synchronized(SaitoLocalState.lock) {
        val saved = load()
        if (saved.transaction?.id != id) return@synchronized false
        save(saved.copy(available = true))
        true
    }

    private fun same(a: Saved, b: Saved): Boolean = a.changes == b.changes &&
        a.transaction?.encoded() == b.transaction?.encoded() && a.available == b.available

    private fun validate(saved: Saved) {
        require(saved.changes.keySet().all { it == "description" })
        saved.changes.get("description")?.let { require(it.isJsonPrimitive && it.asJsonPrimitive.isString) }
        require(!saved.available || saved.transaction != null)
        saved.transaction?.let { transaction ->
            val signed = SaitoEnvelope.decode(transaction.envelope())
            require(signed.author == owner && signed.recipients == listOf(owner))
            require(signed.body.get("module").asString == "Profile" && signed.body.get("request").asString == "update profile")
            val marker = signed.body.get("snapshot")
            require(marker.isJsonPrimitive && marker.asJsonPrimitive.isNumber && marker.asString.toBigDecimal().compareTo(java.math.BigDecimal.ONE) == 0)
            val fields = signed.body.getAsJsonObject("data")
            saved.changes.entrySet().forEach { (name, value) -> require(fields.get(name) == value) }
        }
    }
}
