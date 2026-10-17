package nl.davidengelen.redsquare.gateway

import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet
import java.io.Closeable
import java.io.IOException
import java.net.URI
import java.nio.file.Path
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.Locale

internal class GatewayProblem(val status: Int) : RuntimeException()
internal data class GatewayReply(val status: Int, val body: JsonObject)

/** Wallet-signed binding and independent installation retirement share one durable state transition. */
internal class GatewaySubscriptions(directory: Path, origin: String) : Closeable {
    private val origin = URI(origin).let {
        require(it.scheme == "https" && !it.host.isNullOrBlank() && it.rawUserInfo == null &&
            it.rawQuery == null && it.rawFragment == null && it.path in listOf("", "/") &&
            (it.port == -1 || it.port in 1..65535))
        URI("https", null, it.host.lowercase(Locale.ROOT), if (it.port == 443) -1 else it.port, null, null, null)
    }.toASCIIString()
    private val store = GatewayStateFile(directory, this.origin)
    private data class Challenge(val owner: String, val expiresAt: Long, val sequence: Long)
    private val challenges = mutableMapOf<String, Challenge>()
    private val challengeEpoch = UUID.randomUUID().toString()
    private var challengeSequence = 0L

    @Synchronized fun handle(path: String, body: JsonObject, signature: String?): GatewayReply {
        val now = System.currentTimeMillis()
        challenges.entries.removeIf { it.value.expiresAt <= now }
        return when (path) {
            "/v1/challenges" -> {
                fields(body, setOf("version", "owner"))
                val owner = text(body, "owner").also { require(SaitoWallet.isPublicKey(it)) }
                if (challenges.size >= CAPACITY) throw GatewayProblem(503)
                val nonce = ByteArray(32).also(SecureRandom()::nextBytes).hex()
                val expiresAt = now + 5 * 60 * 1000L
                challengeSequence = Math.incrementExact(challengeSequence)
                challenges[nonce] = Challenge(owner, expiresAt, challengeSequence)
                GatewayReply(201, JsonObject().apply {
                    addProperty("version", 1); addProperty("nonce", nonce); addProperty("expiresAt", expiresAt)
                })
            }
            "/v1/subscriptions/bind" -> bind(body, signature, now)
            "/v1/subscriptions/status", "/v1/subscriptions/revoke" -> authority(path, body, now)
            else -> throw GatewayProblem(404)
        }
    }

    private fun bind(body: JsonObject, signature: String?, now: Long): GatewayReply {
        fields(body, setOf("version", "nonce", "subscription", "installation", "fid", "owner",
            "backend", "categories", "expiresAt", "revocationHash"))
        val nonce = hex(body, "nonce")
        val id = hex(body, "subscription")
        val installation = text(body, "installation").also { require(UUID.fromString(it).toString() == it) }
        val fid = text(body, "fid").also { require(it.matches(Regex("[A-Za-z0-9_-]{1,256}"))) }
        val owner = text(body, "owner").also { require(SaitoWallet.isPublicKey(it)) }
        val backend = text(body, "backend").also { value -> require(SaitoBackend.entries.any { it.id == value }) }
        val categories = text(body, "categories").also { require(it in listOf("mentions", "replies", "mentions,replies")) }
        val expiresAt = number(body, "expiresAt").also { require(it > now && it - now <= LIFETIME) }
        val revocation = hex(body, "revocationHash")
        val intent = "Saito push bind v1\n$origin/v1/subscriptions/bind\n$nonce\n$id\n$installation\n" +
            "${sha256(fid)}\n$owner\n$backend\n$categories\n$expiresAt\n$revocation"
        if (signature == null || !SaitoWallet.verify(intent.toByteArray(Charsets.UTF_8), signature, owner)) {
            throw GatewayProblem(401)
        }
        val state = store.load()
        prune(state, now)
        val retired = state.getAsJsonObject("retired")
        if (retired.has(id)) throw GatewayProblem(410)
        val subscriptions = state.getAsJsonObject("subscriptions")
        val digest = sha256(intent)
        val existing = subscriptions.get(id)?.asJsonObject
        if (existing == null || savedText(existing, "intentHash") != digest) {
            val challenge = challenges[nonce]
            if (challenge == null || challenge.owner != owner || challenge.expiresAt <= now) throw GatewayProblem(410)
            if (existing != null) {
                if (savedText(existing, "owner") != owner || savedText(existing, "installation") != installation ||
                    savedText(existing, "revocationHash") != revocation) throw GatewayProblem(409)
                // An older, still-valid challenge must not undo a newer accepted renewal.
                // A new runtime has no prior challenges, so a new epoch can safely start at1.
                if (existing.has("nonceEpoch") && savedText(existing, "nonceEpoch") == challengeEpoch &&
                    savedNumber(existing, "nonceSequence") >= challenge.sequence) throw GatewayProblem(409)
            } else {
                if (subscriptions.size() >= CAPACITY) throw GatewayProblem(503)
                if (subscriptions.entrySet().any { savedText(it.value.asJsonObject, "installation") == installation }) {
                    throw GatewayProblem(409) // Retire the previous installation binding first.
                }
            }
            subscriptions.add(id, body.deepCopy().apply {
                addProperty("intentHash", digest)
                addProperty("nonceEpoch", challengeEpoch); addProperty("nonceSequence", challenge.sequence)
            })
        }
        // Otherwise exact retransmission acknowledges the existing operation without a new mutation.
        store.save(state)
        challenges.remove(nonce)
        return GatewayReply(201, JsonObject().apply {
            addProperty("version", 1); addProperty("subscription", id); addProperty("expiresAt", expiresAt)
        })
    }

    private fun authority(path: String, body: JsonObject, now: Long): GatewayReply {
        fields(body, setOf("version", "subscription", "revocationSecret"))
        val id = hex(body, "subscription")
        val hash = sha256(hex(body, "revocationSecret"))
        val state = store.load()
        val subscriptions = state.getAsJsonObject("subscriptions")
        val retired = state.getAsJsonObject("retired")
        val active = subscriptions.get(id)?.asJsonObject
        val previous = retired.get(id)?.asJsonObject
        val expected = active?.let { savedText(it, "revocationHash") } ?: previous?.let { savedText(it, "revocationHash") }
        if (expected != null && !MessageDigest.isEqual(expected.toByteArray(), hash.toByteArray())) throw GatewayProblem(401)
        if (path.endsWith("/revoke")) {
            prune(state, now)
            if (!retired.has(id) && retired.size() >= CAPACITY) throw GatewayProblem(503)
            // Persist a tombstone even if an older bind is still in flight and not yet present.
            retired.add(id, JsonObject().apply {
                addProperty("revocationHash", hash); addProperty("expiresAt", now + LIFETIME)
            })
            subscriptions.remove(id)
            store.save(state)
            return GatewayReply(200, JsonObject().apply { addProperty("version", 1); addProperty("active", false) })
        }
        if (expected == null) throw GatewayProblem(404)
        return GatewayReply(200, JsonObject().apply {
            addProperty("version", 1); addProperty("active", active != null && savedNumber(active, "expiresAt") > now)
            if (active != null) {
                addProperty("owner", savedText(active, "owner")); addProperty("backend", savedText(active, "backend"))
                addProperty("expiresAt", savedNumber(active, "expiresAt"))
            }
        })
    }

    private fun prune(state: JsonObject, now: Long) {
        for (name in listOf("subscriptions", "retired")) {
            val records = state.getAsJsonObject(name)
            records.entrySet().removeIf { savedNumber(it.value.asJsonObject, "expiresAt") <= now }
        }
    }

    override fun close() = store.close()

    private fun savedText(body: JsonObject, name: String): String = try { text(body, name) }
        catch (failure: Exception) { throw IOException("Gateway state unavailable", failure) }
    private fun savedNumber(body: JsonObject, name: String): Long = try { number(body, name) }
        catch (failure: Exception) { throw IOException("Gateway state unavailable", failure) }

    private companion object {
        const val CAPACITY = 2048
        const val LIFETIME = 7 * 24 * 60 * 60 * 1000L
        fun fields(body: JsonObject, names: Set<String>) {
            require(body.keySet() == names && number(body, "version") == 1L)
        }
        fun text(body: JsonObject, name: String): String {
            val value = requireNotNull(body.get(name))
            require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
            return value.asString
        }
        fun number(body: JsonObject, name: String): Long {
            val value = requireNotNull(body.get(name))
            require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
            return try { value.asBigDecimal.longValueExact() }
                catch (failure: ArithmeticException) { throw IllegalArgumentException("Expected an integer", failure) }
        }
        fun hex(body: JsonObject, name: String): String = text(body, name).also { require(it.matches(Regex("[0-9a-f]{64}"))) }
        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).hex()
        fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
