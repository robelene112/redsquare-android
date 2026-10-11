package nl.davidengelen.redsquare.registry

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import nl.davidengelen.redsquare.AndroidWalletStore
import nl.davidengelen.redsquare.saito.*

/** One immutable signed request per wallet/server. Reopening cannot sign a replacement. */
internal class UsernameRequestStore(private val context: Context, private val owner: String,
    private val backend: SaitoBackend) {
    init { require(owner.matches(Regex("[1-9A-HJ-NP-Za-km-z]{43,45}"))) }
    private val file = EncryptedLocalFile(context, "namerequest.${backend.id}.$owner.v1",
        "redsquare.namerequest.v1", "namerequest.")
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()

    fun load(): SaitoTransaction? = synchronized(SaitoLocalState.lock) {
        val contents = file.load() ?: return@synchronized null
        require(contents.version == 1.toByte())
        val encoded = requireNotNull(gson.fromJson(contents.text, String::class.java))
        SaitoTransaction.restore(encoded).also(::validate)
    }

    fun save(transaction: SaitoTransaction) = synchronized(SaitoLocalState.lock) {
        validate(transaction)
        val existing = load()
        // Also permits retrying this exact outcome after a late save failure.
        check(existing == null || existing.encoded() == transaction.encoded())
        file.save(EncryptedLocalFile.Contents(1, gson.toJson(transaction.encoded())))
        check(load()?.encoded() == transaction.encoded())
    }

    fun claim(id: String): Boolean = synchronized(SaitoLocalState.lock) {
        AndroidBackendStore(context).load() == backend &&
            AndroidWalletStore(context).loadExisting().publicKey == owner && load()?.id == id
    }

    private fun validate(transaction: SaitoTransaction) {
        val signed = SaitoEnvelope.decode(transaction.envelope())
        require(signed.author == owner && signed.recipients == listOf(SaitoRegistry.REGISTRAR, owner))
        require(signed.body.keySet() == setOf("module", "request", "identifier"))
        require(signed.body.get("module").asString == "Registry" && signed.body.get("request").asString == "register")
        val identifier = signed.body.get("identifier")
        require(identifier.isJsonPrimitive && identifier.asJsonPrimitive.isString &&
            identifier.asString.matches(Regex("[A-Za-z0-9]{1,45}@saito")))
    }
}
