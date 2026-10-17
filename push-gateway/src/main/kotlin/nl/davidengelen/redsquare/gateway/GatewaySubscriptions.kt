package nl.davidengelen.redsquare.gateway

import com.google.gson.JsonObject
import com.google.gson.JsonArray
import nl.davidengelen.redsquare.saito.RedSquareFeed
import nl.davidengelen.redsquare.saito.SaitoFullBlock
import java.math.BigInteger
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
internal class GatewaySubscriptions(directory: Path, origin: String, private val clock: java.time.Clock) : Closeable {
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

    @Synchronized fun handle(path: String, body: JsonObject, signature: String?, recipient: String? = null): GatewayReply {
        val now = clock.millis()
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
            "/v1/observer/status" -> {
                fields(body, setOf("version", "backend"))
                val backend = text(body, "backend").also { value -> require(SaitoBackend.entries.any { it.id == value }) }
                val state = store.load()
                val gap = state.getAsJsonObject("gaps").get(backend)?.asJsonObject
                    ?.takeIf { savedNumber(it, "expiresAt") > now }
                GatewayReply(200, JsonObject().apply {
                    addProperty("version", 1); addProperty("backend", backend)
                    add("gap", gap?.deepCopy()?.apply { remove("records") })
                    add("gaps", gapRecords(gap, now))
                    add("cursor", state.getAsJsonObject("chains").get(backend))
                })
            }
            "/v1/wakes/resolve" -> resolve(body, signature, recipient, now)
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

    @Synchronized fun cursor(backend: SaitoBackend): GatewayChain.Cursor? = try {
        store.load().getAsJsonObject("chains").get(backend.id)?.asJsonObject?.let {
            GatewayChain.Cursor(BigInteger(savedText(it, "height")), savedText(it, "hash"), savedText(it, "forkId"))
        }
    } catch (failure: Exception) { throw IOException("Gateway chain cursor unavailable", failure) }

    /** Only a full hash already retained from verified blocks authorizes a rewind. */
    @Synchronized fun rewind(backend: SaitoBackend, height: BigInteger, hash: String, forkId: String): GatewayChain.Cursor? {
        val state = store.load()
        val chain = state.getAsJsonObject("chains").get(backend.id)?.asJsonObject ?: return null
        if (height >= BigInteger(savedText(chain, "height"))) return null
        val history = chain.getAsJsonArray("history")
        val index = history.indexOfFirst { point -> point.asJsonObject.let {
            savedText(it, "height") == height.toString() && savedText(it, "hash") == hash
        } }
        if (index < 0) return null
        while (history.size() > index + 1) history.remove(history.size() - 1)
        chain.addProperty("height", height.toString()); chain.addProperty("hash", hash); chain.addProperty("forkId", forkId)
        for ((_, value) in state.getAsJsonObject("events").entrySet()) {
            val event = value.asJsonObject
            if (savedText(event, "backend") == backend.id && savedText(event, "state") == "pending" &&
                event.has("sourceHeight") && BigInteger(savedText(event, "sourceHeight")) > height) {
                event.addProperty("state", "withdrawn")
            }
        }
        recordGap(state, backend, "chain-reorganization", clock.millis())
        store.save(state)
        return GatewayChain.Cursor(height, hash, forkId)
    }

    /** Candidate outcomes and processed cursor share one atomic durable snapshot. */
    @Synchronized fun processed(backend: SaitoBackend, block: SaitoFullBlock, forkId: String,
        candidates: List<GatewayArchive.VerifiedSocial>) {
        val previous = cursor(backend)
        if (previous != null && (block.height != previous.height + BigInteger.ONE || block.previousHash != previous.hash)) {
            throw IOException("Processed block does not extend retained cursor")
        }
        val state = store.load()
        val now = clock.millis()
        prune(state, now)
        enqueue(state, backend, candidates, block, now)
        val chains = state.getAsJsonObject("chains")
        val history = chains.get(backend.id)?.asJsonObject?.getAsJsonArray("history") ?: JsonArray()
        if (history.isEmpty && block.height > BigInteger.ZERO) history.add(JsonObject().apply {
            addProperty("height", (block.height - BigInteger.ONE).toString()); addProperty("hash", block.previousHash)
            addProperty("processed", false) // Verified header ancestry, not a processed predecessor block.
        })
        history.add(JsonObject().apply {
            addProperty("height", block.height.toString()); addProperty("hash", block.hash); addProperty("processed", true)
        })
        while (history.size() > 128) history.remove(0)
        chains.add(backend.id, JsonObject().apply {
            addProperty("height", block.height.toString()); addProperty("hash", block.hash)
            addProperty("forkId", forkId); add("history", history)
        })
        store.save(state)
    }

    @Synchronized fun gap(backend: SaitoBackend, reason: String) {
        val state = store.load()
        val now = clock.millis()
        prune(state, now)
        recordGap(state, backend, reason, now)
        store.save(state)
    }

    /** Verified originals are supplied only by the selected read-only Archive adapter. */
    @Synchronized fun enqueue(backend: SaitoBackend, candidates: List<GatewayArchive.VerifiedSocial>) {
        val now = clock.millis()
        val state = store.load()
        prune(state, now)
        enqueue(state, backend, candidates, null, now)
        store.save(state) // Every opaque reference is durable before the sender sees it.
    }

    private fun enqueue(state: JsonObject, backend: SaitoBackend, candidates: List<GatewayArchive.VerifiedSocial>,
        source: SaitoFullBlock?, now: Long) {
        val events = state.getAsJsonObject("events")
        if (candidates.any { it.unavailableNames }) recordGap(state, backend, "name-proof-unavailable", now)
        for ((subscription, value) in state.getAsJsonObject("subscriptions").entrySet()) {
            val binding = value.asJsonObject
            if (savedText(binding, "backend") != backend.id || savedNumber(binding, "expiresAt") <= now) continue
            val owner = savedText(binding, "owner")
            val categories = savedText(binding, "categories").split(',')
            for (candidate in candidates) {
                val post = candidate.post
                if (post.author == owner || post.createdAt > now || now - post.createdAt >= LIFETIME) continue
                val category = if (candidate.parentAuthor == owner && "replies" in categories) "replies"
                else if ("mentions" in categories && (owner in candidate.namedRecipients || RedSquareFeed.readMentions(JsonArray().apply {
                    add(JsonObject().apply { addProperty("tx", post.envelope) })
                }.toString(), owner).posts.any { it.id == post.id })) "mentions" else continue
                val existing = events.entrySet().firstOrNull { (_, value) -> value.asJsonObject.let {
                    savedText(it, "subscription") == subscription && savedText(it, "backend") == backend.id &&
                        savedText(it, "signature") == post.id
                } }?.value?.asJsonObject
                if (existing != null) {
                    if (source != null) {
                        existing.addProperty("sourceHeight", source.height.toString()); existing.addProperty("sourceHash", source.hash)
                        // Re-inclusion can restore the same logical wake, but never acknowledge an unsent attempt.
                        if (savedText(existing, "state") == "withdrawn") {
                            existing.addProperty("state", "pending"); existing.addProperty("attempts", 0); existing.addProperty("nextAttempt", 0)
                        }
                    }
                    continue
                }
                if (events.size() >= 4096) throw IOException("Gateway event capacity reached")
                val wake = ByteArray(32).also(SecureRandom()::nextBytes).hex()
                events.add(wake, JsonObject().apply {
                    addProperty("subscription", subscription); addProperty("owner", owner)
                    addProperty("backend", backend.id); addProperty("signature", post.id); addProperty("category", category)
                    addProperty("expiresAt", minOf(now + 24 * 60 * 60 * 1000L, post.createdAt + LIFETIME,
                        savedNumber(binding, "expiresAt")))
                    addProperty("retainUntil", post.createdAt + LIFETIME)
                    addProperty("state", "pending"); addProperty("attempts", 0); addProperty("nextAttempt", 0)
                    if (source != null) {
                        addProperty("sourceHeight", source.height.toString()); addProperty("sourceHash", source.hash)
                    }
                })
            }
        }
    }

    internal data class Delivery(val wake: String, val subscription: String, val generation: String,
        val fid: String, val expiresAt: Long)

    @Synchronized fun pending(backend: SaitoBackend): List<Delivery> {
        val now = clock.millis()
        val state = store.load()
        return state.getAsJsonObject("events").entrySet().mapNotNull { (wake, value) ->
            val event = value.asJsonObject
            if (savedText(event, "backend") != backend.id || savedText(event, "state") != "pending" ||
                savedNumber(event, "expiresAt") <= now || savedNumber(event, "nextAttempt") > now) return@mapNotNull null
            val binding = currentBinding(state, event, now) ?: return@mapNotNull null
            Delivery(wake, savedText(event, "subscription"), savedText(binding, "intentHash"),
                savedText(binding, "fid"), savedNumber(event, "expiresAt"))
        }
    }

    /** Re-read durable demand for each queued send, rather than trusting the batch snapshot. */
    @Synchronized fun current(delivery: Delivery): Delivery? {
        val now = clock.millis()
        val state = store.load()
        val event = state.getAsJsonObject("events").get(delivery.wake)?.asJsonObject ?: return null
        if (savedText(event, "state") != "pending" || savedNumber(event, "expiresAt") <= now ||
            savedNumber(event, "nextAttempt") > now) return null
        val binding = currentBinding(state, event, now) ?: return null
        return Delivery(delivery.wake, savedText(event, "subscription"), savedText(binding, "intentHash"),
            savedText(binding, "fid"), savedNumber(event, "expiresAt"))
    }

    @Synchronized fun complete(delivery: Delivery, result: FcmSender.Result) {
        val now = clock.millis()
        val state = store.load()
        val event = state.getAsJsonObject("events").get(delivery.wake)?.asJsonObject ?: return
        val binding = currentBinding(state, event, now) ?: return
        // A delayed response for an old FID must never retire or acknowledge a renewed target.
        if (savedText(binding, "intentHash") != delivery.generation || savedText(binding, "fid") != delivery.fid) return
        when (result) {
            is FcmSender.Result.Accepted -> event.addProperty("state", "accepted")
            FcmSender.Result.Unregistered -> {
                state.getAsJsonObject("subscriptions").remove(delivery.subscription)
                event.addProperty("state", "unregistered")
            }
            is FcmSender.Result.Rejected -> event.addProperty("state", "rejected")
            is FcmSender.Result.Retry -> {
                val attempts = minOf(savedNumber(event, "attempts") + 1, 30)
                val backoff = minOf(3_600_000L, 10_000L * (1L shl minOf(attempts - 1, 8).toInt()))
                val delay = maxOf(result.afterMillis, backoff) + SecureRandom().nextInt(1000)
                event.addProperty("attempts", attempts)
                event.addProperty("nextAttempt", if (delay < 0 || delay > Long.MAX_VALUE - now) Long.MAX_VALUE else now + delay)
            }
        }
        store.save(state)
    }

    private fun currentBinding(state: JsonObject, event: JsonObject, now: Long): JsonObject? {
        val binding = state.getAsJsonObject("subscriptions").get(savedText(event, "subscription"))?.asJsonObject ?: return null
        return binding.takeIf {
            savedNumber(it, "expiresAt") > now && savedText(it, "owner") == savedText(event, "owner") &&
                savedText(it, "backend") == savedText(event, "backend") &&
                savedText(event, "category") in savedText(it, "categories").split(',')
        }
    }

    private fun resolve(body: JsonObject, signature: String?, recipient: String?, now: Long): GatewayReply {
        fields(body, setOf("version", "wake"))
        val wake = hex(body, "wake")
        if (recipient == null || !SaitoWallet.isPublicKey(recipient) || signature == null || !SaitoWallet.verify(
            "Saito push resolve v1\n$origin/v1/wakes/resolve\n$wake".toByteArray(Charsets.UTF_8), signature, recipient)) {
            throw GatewayProblem(401)
        }
        val state = store.load()
        val event = state.getAsJsonObject("events").get(wake)?.asJsonObject ?: throw GatewayProblem(404)
        if (savedText(event, "owner") != recipient) throw GatewayProblem(401)
        if (savedText(event, "state") == "withdrawn" || savedNumber(event, "expiresAt") <= now ||
            currentBinding(state, event, now) == null) throw GatewayProblem(410)
        return GatewayReply(200, JsonObject().apply {
            addProperty("version", 1); addProperty("wallet", recipient)
            addProperty("backend", savedText(event, "backend")); addProperty("signature", savedText(event, "signature"))
        })
    }

    /** One recent warning per supported reason; later kinds must not erase earlier coverage gaps. */
    private fun recordGap(state: JsonObject, backend: SaitoBackend, reason: String, now: Long) {
        require(reason in setOf("bootstrap-history-unavailable", "chain-unavailable", "chain-ancestry-unavailable",
            "chain-history-unavailable", "full-block-unavailable", "archive-candidate-unavailable",
            "name-proof-unavailable", "chain-reorganization", "archive-unavailable"))
        val gaps = state.getAsJsonObject("gaps")
        val records = gapRecords(gaps.get(backend.id)?.asJsonObject, now)
        val previous = records.firstOrNull { savedText(it.asJsonObject, "reason") == reason }?.asJsonObject
        val warning = JsonObject().apply {
            addProperty("reason", reason)
            addProperty("since", previous?.let { savedNumber(it, "since") } ?: now)
            addProperty("lastAt", now); addProperty("expiresAt", now + LIFETIME)
        }
        records.removeAll { savedText(it.asJsonObject, "reason") == reason }
        records.add(warning)
        gaps.add(backend.id, warning.deepCopy().apply { add("records", records) })
    }

    /** Older snapshots contain only the latest warning; retain that available record on mutation. */
    private fun gapRecords(gap: JsonObject?, now: Long): JsonArray {
        val records = gap?.getAsJsonArray("records") ?: JsonArray().apply { gap?.let { add(it.deepCopy()) } }
        return JsonArray().apply {
            records.filter { savedNumber(it.asJsonObject, "expiresAt") > now }.forEach { add(it.deepCopy()) }
        }
    }

    private fun prune(state: JsonObject, now: Long) {
        for (name in listOf("subscriptions", "retired")) {
            val records = state.getAsJsonObject(name)
            records.entrySet().removeIf { savedNumber(it.value.asJsonObject, "expiresAt") <= now }
        }
        val gaps = state.getAsJsonObject("gaps")
        gaps.entrySet().removeIf { savedNumber(it.value.asJsonObject, "expiresAt") <= now }
        gaps.entrySet().forEach { (_, value) ->
            val gap = value.asJsonObject
            if (gap.has("records")) gap.add("records", gapRecords(gap, now))
        }
        state.getAsJsonObject("events").entrySet().removeIf { savedNumber(it.value.asJsonObject, "retainUntil") <= now }
    }

    @Synchronized fun cleanup() {
        val now = clock.millis()
        challenges.entries.removeIf { it.value.expiresAt <= now }
        val state = store.load()
        prune(state, now)
        store.save(state)
    }

    @Synchronized override fun close() = store.close()

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
