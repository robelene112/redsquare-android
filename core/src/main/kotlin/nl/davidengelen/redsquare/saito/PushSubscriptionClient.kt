package nl.davidengelen.redsquare.saito

import com.google.gson.GsonBuilder
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.Strictness
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID

/** Portable subscription lifecycle; Android supplies encrypted device storage and HTTPS. */
class PushSubscriptionClient(origin: String, private val journal: Journal, private val http: Http) {
    private val origin = canonicalOrigin(origin)
    interface Journal {
        fun load(): String?
        /** Return only after the replacement is durable. */
        fun save(value: String)
    }
    fun interface Http {
        fun post(origin: String, path: String, body: String, signature: String?): Reply
    }
    data class Reply(val status: Int, val body: String)
    class Demand(val wallet: SaitoWallet, val backend: SaitoBackend, val fid: String,
        val categories: String = "mentions,replies") {
        init {
            require(fid.matches(Regex("[A-Za-z0-9_-]{1,256}")))
            require(categories in listOf("mentions", "replies", "mentions,replies"))
        }
    }

    /** The caller serializes all reconciliation for this journal, including across client instances. */
    fun reconcile(currentDemand: () -> Demand?) {
        var state = load()
        val previous = state?.get("binding")?.takeUnless { it.isJsonNull }?.asJsonObject
        val initial = currentDemand()
        if (previous != null && (initial == null || text(previous, "owner") != initial.wallet.publicKey ||
                text(previous, "origin") != origin)) {
            val reply = response(http.post(text(previous, "origin"), "/v1/subscriptions/revoke",
                JsonObject().apply {
                    addProperty("version", 1); addProperty("subscription", text(previous, "subscription"))
                    addProperty("revocationSecret", text(previous, "revocationSecret"))
                }.toString(), null), 200, setOf("version", "active"))
            check(reply.get("active").isJsonPrimitive && reply.getAsJsonPrimitive("active").isBoolean &&
                !reply.get("active").asBoolean)
            state!!.add("binding", JsonNull.INSTANCE)
            save(state)
        }
        val desired = currentDemand() ?: return
        if (state == null) state = JsonObject().apply {
            addProperty("version", 1); addProperty("installation", UUID.randomUUID().toString())
            add("binding", JsonNull.INSTANCE)
        }
        val binding = state.get("binding").takeUnless { it.isJsonNull }?.asJsonObject ?: JsonObject().apply {
            addProperty("origin", origin); addProperty("owner", desired.wallet.publicKey)
            addProperty("subscription", randomHex()); addProperty("revocationSecret", randomHex())
            state.add("binding", this)
        }
        if (text(binding, "owner") != desired.wallet.publicKey || text(binding, "origin") != origin) {
            throw IOException("Push demand changed during reconciliation")
        }
        // Before any request can commit, retain authority usable without this wallet's key.
        save(state)
        val challenge = response(http.post(origin, "/v1/challenges", JsonObject().apply {
            addProperty("version", 1); addProperty("owner", desired.wallet.publicKey)
        }.toString(), null), 201, setOf("version", "nonce", "expiresAt"))
        val nonce = hex(challenge, "nonce")
        check(number(challenge, "expiresAt") > System.currentTimeMillis())
        requireCurrent(desired, currentDemand)
        val expiry = System.currentTimeMillis() + 6 * 24 * 60 * 60 * 1000L
        val id = text(binding, "subscription")
        val installation = text(state, "installation")
        val hash = sha256(text(binding, "revocationSecret"))
        val body = JsonObject().apply {
            addProperty("version", 1); addProperty("nonce", nonce); addProperty("subscription", id)
            addProperty("installation", installation); addProperty("fid", desired.fid)
            addProperty("owner", desired.wallet.publicKey); addProperty("backend", desired.backend.id)
            addProperty("categories", desired.categories); addProperty("expiresAt", expiry)
            addProperty("revocationHash", hash)
        }
        val intent = "Saito push bind v1\n$origin/v1/subscriptions/bind\n$nonce\n$id\n$installation\n" +
            "${sha256(desired.fid)}\n${desired.wallet.publicKey}\n${desired.backend.id}\n" +
            "${desired.categories}\n$expiry\n$hash"
        val reply = response(http.post(origin, "/v1/subscriptions/bind", body.toString(),
            desired.wallet.sign(intent.toByteArray(Charsets.UTF_8))), 201, setOf("version", "subscription", "expiresAt"))
        check(text(reply, "subscription") == id && number(reply, "expiresAt") == expiry)
        requireCurrent(desired, currentDemand)
    }

    private fun requireCurrent(expected: Demand, current: () -> Demand?) {
        val fresh = current()
        if (fresh == null || fresh.wallet.publicKey != expected.wallet.publicKey || fresh.backend != expected.backend ||
            fresh.fid != expected.fid || fresh.categories != expected.categories) {
            throw IOException("Push demand changed during reconciliation")
        }
    }

    private fun load(): JsonObject? {
        val contents = journal.load() ?: return null
        return try {
            val state = parse(contents)
            require(state.keySet() == setOf("version", "installation", "binding") && number(state, "version") == 1L)
            val installation = text(state, "installation")
            require(UUID.fromString(installation).toString() == installation)
            if (!state.get("binding").isJsonNull) {
                val binding = state.getAsJsonObject("binding")
                require(binding.keySet() == setOf("origin", "owner", "subscription", "revocationSecret"))
                require(canonicalOrigin(text(binding, "origin")) == text(binding, "origin"))
                require(SaitoWallet.isPublicKey(text(binding, "owner")))
                hex(binding, "subscription"); hex(binding, "revocationSecret")
            }
            state
        } catch (_: Exception) { throw IOException("Push subscription journal unavailable") }
    }

    private fun save(state: JsonObject) {
        val contents = state.toString()
        journal.save(contents)
        check(journal.load() == contents) { "Push subscription journal not committed" }
    }

    private companion object {
        fun canonicalOrigin(value: String): String = URI(value).let {
            require(value.length <= 512 && it.scheme == "https" && !it.host.isNullOrBlank() &&
                it.rawUserInfo == null && it.rawQuery == null && it.rawFragment == null &&
                it.path in listOf("", "/") && (it.port == -1 || it.port in 1..65535))
            URI("https", null, it.host.lowercase(Locale.ROOT), if (it.port == 443) -1 else it.port,
                null, null, null).toASCIIString()
        }
        fun parse(value: String): JsonObject {
            require(value.toByteArray(Charsets.UTF_8).size in 1..4096)
            return requireNotNull(GsonBuilder().setStrictness(Strictness.STRICT).create().fromJson(value, JsonObject::class.java))
        }
        fun response(reply: Reply, status: Int, fields: Set<String>): JsonObject {
            if (reply.status != status) throw IOException("Push gateway operation unavailable")
            return parse(reply.body).also { require(it.keySet() == fields && number(it, "version") == 1L) }
        }
        fun text(body: JsonObject, name: String): String = requireNotNull(body.get(name)).let {
            require(it.isJsonPrimitive && it.asJsonPrimitive.isString); it.asString
        }
        fun number(body: JsonObject, name: String): Long = requireNotNull(body.get(name)).let {
            require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber); it.asBigDecimal.longValueExact()
        }
        fun hex(body: JsonObject, name: String): String = text(body, name).also { require(it.matches(Regex("[0-9a-f]{64}"))) }
        fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).hex()
        fun randomHex() = ByteArray(32).also(SecureRandom()::nextBytes).hex()
        fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
