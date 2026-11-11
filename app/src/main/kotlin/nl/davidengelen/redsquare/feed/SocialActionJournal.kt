package nl.davidengelen.redsquare.feed

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import nl.davidengelen.redsquare.AndroidWalletStore
import nl.davidengelen.redsquare.saito.*

/** Last candidate plus its settled predecessor, without retiring a receipt before replacement. */
internal class SocialActionJournal(private val context: Context, private val owner: String,
    private val backend: SaitoBackend, private val key: PostActionKey) {
    data class Saved(val candidate: SaitoTransaction, val available: Boolean,
        val previous: SaitoTransaction? = null, val submitted: Boolean = available)
    private val storage = PublicStringStore(context,
        "social.${backend.id}.$owner.${key.target}.${key.kind.storageId}.v1", 1)

    fun load(): Saved? = synchronized(SaitoLocalState.lock) {
        val loaded = storage.load()
        check(!loaded.unreadable)
        loaded.values.singleOrNull()?.let { value ->
            val record = GsonBuilder().setStrictness(Strictness.STRICT).create()
                .fromJson(value, JsonObject::class.java)
            require(record.keySet().all { it in setOf("transaction", "available", "previous", "submitted") })
            fun transaction(field: String): SaitoTransaction {
                val text = record.get(field).asJsonPrimitive
                require(text.isString)
                return SaitoTransaction.restore(text.asString).also {
                    val action = RedSquareActions.decode(it.envelope())
                    require(action.author == owner && action.target == key.target && action.kind == key.kind)
                }
            }
            val candidate = transaction("transaction")
            val available = record.get("available").asJsonPrimitive
            require(available.isBoolean)
            val previous = if (record.has("previous")) transaction("previous") else null
            require(previous == null || SaitoEnvelope.decode(previous.envelope()).createdAt <
                SaitoEnvelope.decode(candidate.envelope()).createdAt)
            val submitted = record.get("submitted")?.asJsonPrimitive?.also { require(it.isBoolean) }?.asBoolean
                ?: available.asBoolean
            Saved(candidate, available.asBoolean, previous, submitted)
        }
    }

    fun prepare(candidate: SaitoTransaction, expected: Saved?): Saved = synchronized(SaitoLocalState.lock) {
        val current = load()
        check(same(current, expected))
        if (current?.candidate?.encoded() == candidate.encoded()) return@synchronized current
        check(current == null || current.submitted)
        check(AndroidWalletStore(context).loadExisting().publicKey == owner &&
            AndroidBackendStore(context).load() == backend)
        val previous = current?.candidate
        check(previous == null || SaitoEnvelope.decode(previous.envelope()).createdAt <
            SaitoEnvelope.decode(candidate.envelope()).createdAt)
        Saved(candidate, false, previous, submitted = false).also(::write)
    }

    /** A successful peer socket enqueue completes the user's send attempt without archive confirmation. */
    fun markSubmitted(candidate: SaitoTransaction): Saved = synchronized(SaitoLocalState.lock) {
        val current = requireNotNull(load())
        check(current.candidate.encoded() == candidate.encoded())
        current.copy(submitted = true).also(::write)
    }

    fun settle(candidate: SaitoTransaction): Saved = synchronized(SaitoLocalState.lock) {
        val current = requireNotNull(load())
        check(current.candidate.encoded() == candidate.encoded())
        current.copy(available = true, submitted = true).also(::write)
    }

    private fun write(saved: Saved) {
        val record = JsonObject().apply {
            addProperty("transaction", saved.candidate.encoded())
            addProperty("available", saved.available)
            addProperty("submitted", saved.submitted)
            saved.previous?.let { addProperty("previous", it.encoded()) }
        }.toString()
        storage.save(listOf(record))
        check(storage.load().let { !it.unreadable && it.values == listOf(record) })
    }

    companion object {
        /** Unsubmitted social work is identity-wide; locally enqueued actions are complete here. */
        fun hasWork(context: Context, owner: String): Boolean = synchronized(SaitoLocalState.lock) {
            require(SaitoWallet.isPublicKey(owner))
            if (SocialActionOperations.hasWork(owner)) return@synchronized true
            val files = requireNotNull(context.noBackupFilesDir.listFiles())
            for (node in SaitoBackend.entries) {
                val prefix = "social.${node.id}.$owner."
                for (file in files.filter { it.name.startsWith(prefix) }) {
                    if (file.name.endsWith(".new")) return@synchronized true
                    val suffix = file.name.removePrefix(prefix).removeSuffix(".bak")
                    val match = Regex("([0-9a-f]{128})\\.(like|repost)\\.v1").matchEntire(suffix)
                        ?: return@synchronized true
                    val kind = RedSquareActionKind.entries.single { it.storageId == match.groupValues[2] }
                    val saved = SocialActionJournal(context, owner, node, PostActionKey(match.groupValues[1], kind)).load()
                    if (saved?.submitted != true) return@synchronized true
                }
            }
            false
        }
    }

    private fun same(left: Saved?, right: Saved?) = left?.candidate?.encoded() == right?.candidate?.encoded() &&
        left?.available == right?.available && left?.previous?.encoded() == right?.previous?.encoded() &&
        left?.submitted == right?.submitted
}

/** One in-flight operation per wallet/post/kind, including other mounted node views. */
internal object SocialActionOperations {
    private data class Key(val owner: String, val backend: SaitoBackend, val post: PostActionKey)
    private data class Active(val token: Any, var queuedLikes: Int = 0, var likePublishingAuthorized: Boolean = false)
    data class LikeEnqueueResult(val shouldStart: Boolean, val publishingAuthorized: Boolean)
    private val active = mutableStateMapOf<Key, Active>()

    fun hasWork(owner: String): Boolean = synchronized(SaitoLocalState.lock) {
        active.keys.any { it.owner == owner }
    }
    // Snapshot membership is reactive and safe to read without waiting for durable I/O.
    // Actual operation claims still serialize with identity changes and journal writes.
    fun busy(owner: String, backend: SaitoBackend, key: PostActionKey): Boolean =
        active.containsKey(Key(owner, backend, key))
    fun acquire(owner: String, backend: SaitoBackend, key: PostActionKey, token: Any): Boolean = synchronized(SaitoLocalState.lock) {
        val full = Key(owner, backend, key)
        if (active.containsKey(full)) false else { active[full] = Active(token); true }
    }
    /** Every tap joins the existing worker or starts one, so a slow socket never drops Like input. */
    fun enqueueLike(owner: String, backend: SaitoBackend, key: PostActionKey, token: Any): LikeEnqueueResult = synchronized(SaitoLocalState.lock) {
        val full = Key(owner, backend, key)
        val current = active[full]
        if (current == null) {
            active[full] = Active(token, queuedLikes = 1)
            LikeEnqueueResult(shouldStart = true, publishingAuthorized = false)
        } else {
            current.queuedLikes = Math.addExact(current.queuedLikes, 1)
            LikeEnqueueResult(shouldStart = false, publishingAuthorized = current.likePublishingAuthorized)
        }
    }
    fun authorizeQueuedLikes(owner: String, backend: SaitoBackend, key: PostActionKey, token: Any): Int = synchronized(SaitoLocalState.lock) {
        val current = active[Key(owner, backend, key)]?.takeIf { it.token === token } ?: return@synchronized 0
        current.likePublishingAuthorized = true
        current.queuedLikes
    }
    fun takeLike(owner: String, backend: SaitoBackend, key: PostActionKey, token: Any): Boolean = synchronized(SaitoLocalState.lock) {
        val full = Key(owner, backend, key)
        val current = active[full]?.takeIf { it.token === token } ?: return@synchronized false
        if (current.queuedLikes == 0) {
            active.remove(full)
            false
        } else {
            current.queuedLikes--
            true
        }
    }
    fun release(owner: String, backend: SaitoBackend, key: PostActionKey, token: Any) = synchronized(SaitoLocalState.lock) {
        val full = Key(owner, backend, key)
        if (active[full]?.token === token) active.remove(full)
        Unit
    }
}
