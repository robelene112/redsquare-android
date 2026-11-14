package nl.davidengelen.redsquare.saito

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.Strictness
import org.bouncycastle.crypto.digests.Blake3Digest
import java.math.BigInteger
import java.util.Base64

/** A verified signed path and contract binding, never proof of a current provider's acceptance. */
class SaitoVaultRental private constructor(val creator: String, val fileId: String, val masterScript: SaitoAccessScript,
    val source: String, private val hops: List<Hop>) {
    private data class Hop(val recipient: String, val expiresAt: Long, val delegated: Boolean)
    val grant: Grant? = hops.firstOrNull { !it.delegated }?.let { Grant(creator, fileId, it.recipient, it.expiresAt, masterScript) }

    /** Append only for the actual signer at the end of this immutable path. */
    fun append(wallet: SaitoWallet, recipient: String, expiresAt: Long, timestamp: Long, delegated: Boolean): SaitoVaultRental {
        require(wallet.publicKey == (hops.lastOrNull()?.recipient ?: creator) && SaitoWallet.isPublicKey(recipient))
        require(expiresAt >= 0 && timestamp >= 0 && BigInteger.valueOf(expiresAt) <= maximumTime && BigInteger.valueOf(timestamp) <= maximumTime)
        val payload = JsonObject().apply {
            addProperty("timestamp", timestamp); addProperty("file_id", fileId); addProperty("expires_at", expiresAt)
            addProperty("delegated", if (delegated) 1 else 0)
        }
        val encoded = Base64.getEncoder().encodeToString(payload.toString().toByteArray(Charsets.UTF_8))
        val digest = hash("$recipient|$encoded|".toByteArray(Charsets.UTF_8))
        val rows = requireNotNull(gson.fromJson(source, JsonArray::class.java))
        rows.add(JsonObject().apply {
            addProperty("to", recipient); addProperty("value", encoded)
            addProperty("sig", wallet.sign(digest.toByteArray(Charsets.UTF_8)))
        })
        return parse(creator, fileId, masterScript.source, rows.toString())
    }

    /** Off-chain checkout intent. The provider must still accept the actual Archive update. */
    fun checkout(wallet: SaitoWallet, timestamp: Long): SaitoTransaction {
        val rental = requireNotNull(grant) { "No rental was granted by this path" }
        require(wallet.publicKey in setOf(creator, rental.renter))
        val script = requireNotNull(gson.fromJson(masterScript.source, JsonObject::class.java))
        val rows = requireNotNull(gson.fromJson(source, JsonArray::class.java))
        fun attach(node: JsonObject) {
            if (node.get("op")?.asString?.uppercase(java.util.Locale.ROOT) == "CHECKPATHHOP")
                node.add("witness", JsonObject().apply { add("hops", rows.deepCopy()) })
            else node.get("args")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { if (it.isJsonObject) attach(it.asJsonObject) }
        }
        attach(script)
        val inner = SaitoTransaction.create(wallet, JsonObject().apply {
            addProperty("module", "Vault"); addProperty("request", "vault checkout rental")
            addProperty("access_script", script.toString())
            add("data", JsonObject().apply { addProperty("file_id", fileId); add("path", rows) })
        }, timestamp)
        return SaitoTransaction.create(wallet, JsonObject().apply {
            addProperty("request", "vault checkout rental"); addProperty("data", inner.envelope())
        }, timestamp)
    }

    /** Explicit creator request; only the provider's actual current loan and NOW can authorise it. */
    fun restoreMaster(wallet: SaitoWallet, timestamp: Long): SaitoTransaction {
        require(wallet.publicKey == creator)
        val previous = requireNotNull(grant)
        return SaitoTransaction.create(wallet, JsonObject().apply {
            addProperty("request", "vault restore rental")
            add("data", JsonObject().apply {
                addProperty("file_id", fileId); addProperty("renter", previous.renter); addProperty("expires_at", previous.expiresAt)
            })
        }, timestamp)
    }

    fun readRestoration(response: String) {
        require(response.toByteArray(Charsets.UTF_8).size <= 128 * 1024)
        val row = requireNotNull(gson.fromJson(response, JsonObject::class.java))
        require(row.get("status")?.asString == "ok" && row.get("file_id")?.asString == fileId && row.get("owner")?.asString == masterScript.hash)
        require(row.get("already_restored")?.let { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean } == true)
    }

    class Grant internal constructor(val creator: String, val fileId: String, val renter: String, val expiresAt: Long,
        val masterScript: SaitoAccessScript) {
        val loanScript = SaitoVaultRules.loan(creator, renter, expiresAt)
        fun access(wallet: SaitoWallet, timestamp: Long): SaitoTransaction {
            require(wallet.publicKey in setOf(creator, renter))
            return SaitoTransaction.create(wallet, JsonObject().apply {
                addProperty("request", "vault access rental")
                add("data", JsonObject().apply {
                    addProperty("request", "vault access rental"); addProperty("access_script", loanScript.source)
                    addProperty("access_hash", loanScript.hash)
                    add("data", JsonObject().apply { addProperty("file_id", fileId) })
                })
            }, timestamp)
        }
        /** Authenticate the original master-locked bytes; the loan never re-signs the file. */
        fun readFile(response: String): VaultFile = SaitoVault.readRentalFile(this, response)
        /** Local contract eligibility for display; the selected provider still evaluates its actual NOW. */
        fun eligibleActorsAt(timeMillis: Long): Set<String> {
            require(timeMillis >= 0)
            return when { timeMillis < expiresAt -> setOf(renter); timeMillis > expiresAt -> setOf(creator); else -> emptySet() }
        }
    }

    companion object {
        private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()
        private val maximumTime = BigInteger("9007199254740991")
        fun parse(creator: String, fileId: String, masterSource: String, path: String): SaitoVaultRental {
            require(SaitoWallet.isPublicKey(creator) && fileId.matches(Regex("[0-9a-f]{128}")))
            val master = SaitoAccessScript.parse(masterSource).withoutWitnesses()
            require(master.hash == SaitoVaultRules.rental(creator).hash) { "Unsupported rental constitution" }
            require(path.toByteArray(Charsets.UTF_8).size <= 64 * 1024)
            val rows = requireNotNull(gson.fromJson(path, JsonArray::class.java)).also { require(it.size() <= 32) }
            var signer = creator
            val hops = rows.map { value ->
                val row = value.asJsonObject
                require(row.keySet() == setOf("to", "value", "sig"))
                val recipient = text(row, "to").also { require(SaitoWallet.isPublicKey(it)) }
                val encoded = text(row, "value").also { require(it.length <= 12 * 1024) }
                val signature = text(row, "sig").also { require(it.matches(Regex("[0-9a-fA-F]{128}"))) }
                val bytes = Base64.getDecoder().decode(encoded)
                require(bytes.size <= 8 * 1024 && Base64.getEncoder().encodeToString(bytes) == encoded)
                val payload = requireNotNull(gson.fromJson(bytes.toString(Charsets.UTF_8), JsonObject::class.java))
                require(payload.keySet() == setOf("timestamp", "file_id", "expires_at", "delegated"))
                require(text(payload, "file_id") == fileId) { "Rental path refers to another file" }
                numeric(payload, "timestamp")
                val expiresAt = numeric(payload, "expires_at")
                val delegation = numeric(payload, "delegated").also { require(it in 0..1) }
                val digest = hash("$recipient|$encoded|".toByteArray(Charsets.UTF_8))
                require(SaitoWallet.verify(digest.toByteArray(Charsets.UTF_8), signature, signer)) { "Invalid rental path signature" }
                signer = recipient
                Hop(recipient, expiresAt, delegation == 1L)
            }
            return SaitoVaultRental(creator, fileId, master, rows.toString(), hops)
        }
        private fun text(row: JsonObject, name: String): String = row.get(name).let {
            require(it != null && it.isJsonPrimitive && it.asJsonPrimitive.isString); it.asString
        }
        private fun numeric(row: JsonObject, name: String): Long {
            val value = row.get(name)
            require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber && value.asString.length <= 32)
            val decimal = value.asString.toBigDecimal()
            require(decimal.precision() <= 17 && decimal.scale() in -16..16)
            val number = decimal.toBigIntegerExact()
            require(number.signum() >= 0 && number <= maximumTime)
            return number.toLong()
        }
        private fun hash(bytes: ByteArray): String = ByteArray(32).also {
            Blake3Digest(256).apply { update(bytes, 0, bytes.size); doFinal(it, 0) }
        }.joinToString("") { "%02x".format(it) }
    }
}
