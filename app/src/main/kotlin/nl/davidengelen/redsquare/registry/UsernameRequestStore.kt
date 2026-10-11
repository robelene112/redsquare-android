package nl.davidengelen.redsquare.registry

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import nl.davidengelen.redsquare.AndroidWalletStore
import nl.davidengelen.redsquare.saito.*

/** One immutable signed request per wallet/server. Reopening cannot sign a replacement. */
internal class UsernameRequestStore(private val context: Context, private val owner: String,
    private val backend: SaitoBackend, private val registrar: String = SaitoRegistry.REGISTRAR) {
    init { require(owner.matches(Regex("[1-9A-HJ-NP-Za-km-z]{43,45}"))) }
    private val file = EncryptedLocalFile(context, "namerequest.${backend.id}.$owner.v1",
        "redsquare.namerequest.v1", "namerequest.")
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()

    private data class Stored(val transaction: SaitoTransaction, val retired: Boolean)
    private data class Attempt(val anchor: Stored?, val previous: Stored?, val intended: Stored)
    private var loaded = false
    private var expected: Stored? = null
    private var attempt: Attempt? = null

    private fun read(): Stored? {
        val contents = file.load() ?: return null
        val encoded = when (contents.version) {
            1.toByte() -> requireNotNull(gson.fromJson(contents.text, String::class.java))
            2.toByte() -> {
                val marker = requireNotNull(gson.fromJson(contents.text, JsonObject::class.java))
                require(marker.keySet() == setOf("transaction", "retired"))
                val retired = marker.get("retired")
                require(retired.isJsonPrimitive && retired.asJsonPrimitive.isBoolean && retired.asBoolean)
                val transaction = marker.get("transaction")
                require(transaction.isJsonPrimitive && transaction.asJsonPrimitive.isString)
                transaction.asString
            }
            else -> error("Unknown username request version")
        }
        return Stored(SaitoTransaction.restore(encoded).also(::validate), contents.version == 2.toByte())
    }

    fun load(): SaitoTransaction? = synchronized(SaitoLocalState.lock) {
        val current = read()
        expected = current
        loaded = true
        attempt = null
        current?.takeUnless { it.retired }?.transaction
    }

    fun save(transaction: SaitoTransaction) = synchronized(SaitoLocalState.lock) {
        validate(transaction)
        val existing = read()
        val intended = Stored(transaction, false)
        checkExpected(existing, intended)
        check(existing == null || if (existing.retired) existing.transaction.id != transaction.id
            else existing.transaction.encoded() == transaction.encoded())
        write(existing, intended)
    }

    /** Explicitly stop retries for this signed request after verified conflicting ownership. */
    fun retire(transaction: SaitoTransaction, conflict: SaitoRegisteredName) = synchronized(SaitoLocalState.lock) {
        validate(transaction)
        val identifier = SaitoEnvelope.decode(transaction.envelope()).body.get("identifier").asString
        require(SaitoRegistry.readNamecheck("[${conflict.proof}]", identifier, registrar) == conflict &&
            conflict.publicKey != owner && conflict.active)
        check(AndroidBackendStore(context).load() == backend && AndroidWalletStore(context).loadExisting().publicKey == owner)
        val existing = requireNotNull(read())
        val intended = Stored(transaction, true)
        checkExpected(existing, intended)
        check(existing.transaction.encoded() == transaction.encoded())
        // Keep a durable marker, rather than deleting and allowing a stale saver to revive it.
        write(existing, intended)
    }

    private fun checkExpected(current: Stored?, intended: Stored) {
        check(loaded)
        val retry = attempt?.let {
            same(it.anchor, expected) && same(it.intended, intended) &&
                (same(current, it.previous) || same(current, it.intended))
        } == true
        check(same(current, expected) || retry)
    }

    private fun write(previous: Stored?, intended: Stored) {
        val text = if (intended.retired) JsonObject().apply {
            addProperty("transaction", intended.transaction.encoded())
            addProperty("retired", true)
        }.toString() else gson.toJson(intended.transaction.encoded())
        file.save(EncryptedLocalFile.Contents(if (intended.retired) 2 else 1,
            text)) {
            // Only this mounted store's exact pre/post-write outcomes may recover a late failure.
            attempt = Attempt(expected, previous, intended)
        }
        check(same(read(), intended)) { "Username request persistence verification failed" }
        expected = intended
        attempt = null
    }

    private fun same(a: Stored?, b: Stored?): Boolean = a?.retired == b?.retired &&
        a?.transaction?.encoded() == b?.transaction?.encoded()

    fun claim(id: String): Boolean = synchronized(SaitoLocalState.lock) {
        AndroidBackendStore(context).load() == backend &&
            AndroidWalletStore(context).loadExisting().publicKey == owner &&
            read()?.let { !it.retired && it.transaction.id == id } == true
    }

    private fun validate(transaction: SaitoTransaction) {
        val signed = SaitoEnvelope.decode(transaction.envelope())
        require(signed.author == owner && signed.recipients == listOf(registrar, owner))
        require(signed.body.keySet() == setOf("module", "request", "identifier"))
        require(signed.body.get("module").asString == "Registry" && signed.body.get("request").asString == "register")
        val identifier = signed.body.get("identifier")
        require(identifier.isJsonPrimitive && identifier.asJsonPrimitive.isString &&
            identifier.asString.matches(Regex("[A-Za-z0-9]{1,45}@saito")))
    }
}
