package nl.davidengelen.redsquare.feed

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import com.google.gson.Strictness
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoLocalState
import nl.davidengelen.redsquare.saito.EncryptedLocalFile
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoProfile
import nl.davidengelen.redsquare.saito.SaitoProfiles
import nl.davidengelen.redsquare.policy.PublishingPolicies

/** Unpublished profile changes stay encrypted and tied to their wallet and server. */
internal class ProfileDraftStore(private val context: Context, private val owner: String, private val backend: SaitoBackend) {
    init { require(owner.matches(Regex("[1-9A-HJ-NP-Za-km-z]{43,45}"))) }
    private val file = EncryptedLocalFile(context, "profiledraft.${backend.id}.$owner.v1",
        "redsquare.profiledraft.v1", "profiledraft.")
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()

    // available records a past exact confirmation, not current server retention.
    data class Saved(val changes: JsonObject = JsonObject(), val transaction: SaitoTransaction? = null,
        val available: Boolean = false, val superseding: SaitoProfile? = null, val revision: Long = 0,
        val baseline: SaitoProfile? = null) {
        val hasWork: Boolean get() = !available && (changes.size() > 0 || transaction != null)
    }

    // A failed save can already have replaced the file. Keep only this store's
    // exact mounted anchor and pre/post-write outcomes, not arbitrary stale records.
    private data class Attempt(val anchor: Saved, val previous: Saved, val intended: Saved)
    private var attempt: Attempt? = null

    fun load(): Saved = synchronized(SaitoLocalState.lock) {
        val contents = file.load() ?: return@synchronized Saved()
        val record = requireNotNull(gson.fromJson(contents.text, JsonObject::class.java))
        val saved = when (contents.version) {
            1.toByte() -> Saved(record) // Existing v1 contains changes directly, not a wrapper.
            2.toByte() -> {
                require(record.keySet().all { it in setOf("changes", "transaction", "available", "superseding", "revision", "baseline") })
                val transaction = record.get("transaction")?.let {
                    require(it.isJsonPrimitive && it.asJsonPrimitive.isString)
                    SaitoTransaction.restore(it.asString)
                }
                val flag = record.get("available")
                require(flag.isJsonPrimitive && flag.asJsonPrimitive.isBoolean)
                val superseding = record.get("superseding")?.let {
                    require(it.isJsonPrimitive && it.asJsonPrimitive.isString)
                    readSnapshot(it.asString)
                }
                val revision = record.get("revision")?.let {
                    require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber)
                    it.asString.toBigDecimal().longValueExact().also { value -> require(value >= 0) }
                } ?: 0
                val baseline = record.get("baseline")?.let {
                    require(it.isJsonPrimitive && it.asJsonPrimitive.isString)
                    readSnapshot(it.asString)
                }
                Saved(record.getAsJsonObject("changes"), transaction, flag.asBoolean, superseding, revision, baseline)
            }
            else -> error("Unknown profile draft version")
        }
        saved.also(::validate)
    }

    /** A mounted editor cannot replace another candidate or regress durable delivery progress. */
    fun save(saved: Saved, expected: Saved): Saved = synchronized(SaitoLocalState.lock) {
        validate(saved)
        val current = load()
        // Revision survives clears, distinguishing a new empty draft from an old mounted one.
        val retry = ownAttempt(expected, current)
        // Only explicit retirement may retry a failed clear; ordinary saving cannot resurrect it.
        check(retry == null || retry.anchor.transaction == null || retry.intended.transaction != null || saved.transaction == null)
        if (!same(current, expected) && retry == null) {
            check(current.transaction != null && expected.transaction?.encoded() == current.transaction.encoded() &&
                expected.changes == current.changes)
        }
        val replacement = (if (current.transaction == null) {
            saved.copy(baseline = if (saved.transaction != null) null else current.baseline?.let { retained ->
                saved.baseline?.let { SaitoProfiles.merge(retained, it) } ?: retained
            } ?: saved.baseline)
        } else {
            check(saved.transaction?.encoded() == current.transaction.encoded() && saved.changes == current.changes)
            val superseding = when {
                current.superseding == null -> saved.superseding
                saved.superseding == null -> current.superseding
                else -> SaitoProfiles.merge(current.superseding, saved.superseding)
            }
            saved.copy(available = superseding == null && (current.available || saved.available), superseding = superseding, baseline = null)
        }).copy(revision = Math.addExact(current.revision, 1))
        write(replacement, expected, current)
        replacement
    }

    /** Clear only this confirmed candidate; preserve progress a stale editor has not observed. */
    fun clearAvailable(id: String, expected: Saved): Saved = synchronized(SaitoLocalState.lock) {
        val current = load()
        val retry = ownAttempt(expected, current)
        if (current.transaction?.id != id) {
            // Retry only our exact already-committed clear, never another empty record.
            check(retry != null && retry.anchor.transaction?.id == id &&
                empty(retry.intended) && empty(current))
        } else if ((!same(current, expected) && retry == null) || !current.available || current.superseding != null) {
            return@synchronized current
        }
        Saved(revision = Math.addExact(current.revision, 1)).also { write(it, expected, current) }
    }

    /** Retire exactly the mounted candidate, retaining its newest signed fields privately. */
    fun startNew(id: String, expected: Saved, known: SaitoProfile): Saved = synchronized(SaitoLocalState.lock) {
        require(known.author == owner && known.ignored == 0)
        val current = load()
        val retry = ownAttempt(expected, current)
        val retained = if (current.transaction?.id == id) {
            if (!same(current, expected) && retry == null) return@synchronized current
            val candidate = readSnapshot(current.transaction.envelope())
            current.superseding?.let { SaitoProfiles.merge(candidate, it) } ?: candidate
        } else {
            // A late failure may have committed only our own exact retired outcome.
            check(retry != null && retry.anchor.transaction?.id == id &&
                retry.intended.transaction == null && retry.intended.baseline != null &&
                current.transaction == null && current.baseline != null)
            requireNotNull(current.baseline)
        }
        val baseline = SaitoProfiles.merge(retained, known)
        Saved(revision = Math.addExact(current.revision, 1), baseline = baseline).also { write(it, expected, current) }
    }

    private fun ownAttempt(expected: Saved, current: Saved): Attempt? = attempt?.takeIf {
        same(it.anchor, expected) && (same(current, it.previous) || same(current, it.intended))
    }

    private fun empty(saved: Saved): Boolean = saved.changes.size() == 0 && saved.transaction == null &&
        !saved.available && saved.superseding == null && saved.baseline == null

    private fun witness(saved: Saved): Saved = saved.copy(changes = saved.changes.deepCopy())

    private fun write(saved: Saved, expected: Saved, previous: Saved) {
        validate(saved)
        val record = JsonObject().apply {
            add("changes", saved.changes.deepCopy())
            saved.transaction?.let { addProperty("transaction", it.encoded()) }
            addProperty("available", saved.available)
            addProperty("revision", saved.revision)
            saved.superseding?.let { addProperty("superseding", it.updates.single().envelope) }
            saved.baseline?.let { addProperty("baseline", it.updates.single().envelope) }
        }
        file.save(EncryptedLocalFile.Contents(2, record.toString())) {
            // Codec validation completed. Earlier failures preserve the prior witness;
            // later failures retain both possible outcomes of this attempt.
            attempt = Attempt(witness(expected), witness(previous), witness(saved))
        }
        check(same(load(), saved)) { "Profile draft persistence verification failed" }
        attempt = null
    }

    fun claim(id: String): Boolean = synchronized(SaitoLocalState.lock) {
        PublishingPolicies.requireAllowed(context)
        if (nl.davidengelen.redsquare.saito.AndroidBackendStore(context).load() != backend ||
            nl.davidengelen.redsquare.AndroidWalletStore(context).loadExisting().publicKey != owner) return@synchronized false
        val saved = load()
        saved.transaction?.id == id && !saved.available && saved.superseding == null
    }

    fun confirmAvailable(id: String): Saved? = synchronized(SaitoLocalState.lock) {
        val saved = load()
        if (saved.transaction?.id != id || saved.superseding != null) return@synchronized null
        save(saved.copy(available = true), saved)
    }

    private fun same(a: Saved, b: Saved): Boolean = a.changes == b.changes &&
        a.transaction?.encoded() == b.transaction?.encoded() && a.available == b.available &&
        a.superseding?.updates == b.superseding?.updates && a.revision == b.revision &&
        a.baseline?.updates == b.baseline?.updates

    private fun readSnapshot(envelope: String): SaitoProfile {
        val rows = JsonArray().apply { add(JsonObject().apply { addProperty("tx", envelope) }) }
        return SaitoProfiles.readArchive(rows.toString(), owner).also {
            require(it.ignored == 0 && it.updates.size == 1 && it.updates.single().snapshot)
        }
    }

    private fun validate(saved: Saved) {
        require(saved.revision >= 0)
        require(saved.changes.keySet().all { it in setOf("description", "image", "banner") })
        saved.changes.get("description")?.let { require(it.isJsonPrimitive && it.asJsonPrimitive.isString) }
        for (name in listOf("image", "banner")) saved.changes.get(name)?.takeUnless { it.isJsonNull }?.let {
            require(it.isJsonPrimitive && it.asJsonPrimitive.isString)
            val source = it.asString
            require(source.length <= 512 * 1024 && source.startsWith("data:image/jpeg;base64,") &&
                nl.davidengelen.redsquare.saito.RedSquareImages.safeSource(source) == source)
        }
        require(!saved.available || saved.transaction != null)
        saved.baseline?.let { profile ->
            require(saved.transaction == null && profile.author == owner && profile.ignored == 0 && profile.updates.size == 1)
            val verified = readSnapshot(profile.updates.single().envelope)
            require(verified.fields == profile.fields && verified.updates == profile.updates)
        }
        saved.superseding?.let { profile ->
            val transaction = requireNotNull(saved.transaction)
            require(!saved.available && profile.author == owner && profile.ignored == 0 && profile.updates.size == 1)
            val update = profile.updates.single()
            val verified = readSnapshot(update.envelope)
            require(verified.fields == profile.fields && verified.updates == profile.updates)
            require(update.id != transaction.id && update.createdAt >= SaitoEnvelope.decode(transaction.envelope()).createdAt)
        }
        saved.transaction?.let { transaction ->
            val signed = SaitoEnvelope.decode(transaction.envelope())
            require(signed.author == owner && signed.recipients == listOf(owner))
            require(signed.body.get("module").asString == "Profile" && signed.body.get("request").asString == "update profile")
            val marker = signed.body.get("snapshot")
            require(marker.isJsonPrimitive && marker.asJsonPrimitive.isNumber && marker.asString.toBigDecimal().compareTo(java.math.BigDecimal.ONE) == 0)
            val fields = signed.body.getAsJsonObject("data")
            saved.changes.entrySet().forEach { (name, value) ->
                if (value.isJsonNull) require(!fields.has(name)) else require(fields.get(name) == value)
            }
        }
    }
}
