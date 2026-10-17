package nl.davidengelen.redsquare.feed

import android.content.Context
import nl.davidengelen.redsquare.media.GiphyAttribution
import nl.davidengelen.redsquare.saito.EncryptedLocalFile
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.RedSquareFeed
import nl.davidengelen.redsquare.saito.FeedPost
import nl.davidengelen.redsquare.saito.AuthorActionKind
import nl.davidengelen.redsquare.saito.RedSquareAuthorActions
import nl.davidengelen.redsquare.saito.RedSquareImages
import nl.davidengelen.redsquare.saito.RedSquarePosts
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness

/** Unpublished text and normalized media stay encrypted, scoped to wallet and backend. */
internal class PostDraftStore private constructor(private val context: Context, private val owner: String, private val backend: SaitoBackend,
    private val parentId: String, private val parentThread: String?, private val quoteId: String = "",
    private val authorTarget: String = "", private val authorKind: AuthorActionKind? = null) {
    constructor(context: Context, owner: String, backend: SaitoBackend, parent: FeedPost? = null, quote: FeedPost? = null) :
        this(context, owner, backend, parent?.id.orEmpty(), parent?.threadId, quote?.id.orEmpty())
    init { require(owner.matches(Regex("[1-9A-HJ-NP-Za-km-z]{43,45}"))) }
    private val file = EncryptedLocalFile(context,
        "postdraft.${backend.id}.$owner${if (authorKind != null) ".author.${authorKind.storageId}.$authorTarget" else if (quoteId.isNotEmpty()) ".quote.$quoteId" else parentId.takeIf { it.isNotEmpty() }?.let { ".$it" }.orEmpty()}.v1", "redsquare.postdraft.v1", "postdraft.")

    data class Saved(val text: String, val transaction: SaitoTransaction? = null, val queued: Boolean = false,
        val available: Boolean = false, val automatic: Boolean = false, val authorBaseline: String? = null,
        val images: List<String> = emptyList(), val gifAttributions: Map<String, GiphyAttribution> = emptyMap())
    data class AuthorWork(val target: String, val kind: AuthorActionKind, val saved: Saved)
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()

    fun load(): Saved = synchronized(lock) {
        val contents = file.load() ?: return@synchronized Saved("")
        if (contents.version == 1.toByte()) Saved(contents.text)
        else {
            val record = gson.fromJson(contents.text, JsonObject::class.java)
            val content = record.get("text").asString
            val transaction = record.get("transaction")?.takeUnless { it.isJsonNull }?.asString?.let(SaitoTransaction::restore)
            val images = record.get("images")?.let { values ->
                require(values.isJsonArray)
                values.asJsonArray.map { value ->
                    require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
                    value.asString
                }
            }.orEmpty()
            validateTransaction(transaction, content, images)
            val queued = record.get("queued")?.let {
                require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean)
                it.asBoolean
            } ?: false
            val available = record.get("available")?.let {
                require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean)
                it.asBoolean
            } ?: false
            require((!queued && !available) || transaction != null)
            require(!queued || !available)
            val automatic = record.get("automatic")?.let {
                require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean)
                it.asBoolean
            } ?: false
            require(!automatic || transaction != null)
            require(authorKind == null || (!queued && !automatic))
            val baseline = record.get("authorBaseline")?.let {
                require(it.isJsonPrimitive && it.asJsonPrimitive.isString)
                it.asString
            }
            validateBaseline(baseline)
            val credits = record.get("gifAttributions")?.takeIf { it.isJsonObject }?.asJsonObject
                ?.entrySet()?.asSequence()?.filter { it.key in images }?.mapNotNull { entry ->
                    GiphyAttribution.fromJson(entry.value)?.let { entry.key to it }
                }?.take(RedSquareImages.MAX_COUNT)?.toMap().orEmpty()
            Saved(content, transaction, queued, available, automatic, baseline, images, credits)
        }
    }

    fun save(saved: Saved) = synchronized(lock) {
        validateTransaction(saved.transaction, saved.text, saved.images)
        validateBaseline(saved.authorBaseline)
        require(saved.gifAttributions.size <= RedSquareImages.MAX_COUNT && saved.gifAttributions.all { (source, credit) ->
            source in saved.images && GiphyAttribution.fromJson(credit.toJson()) == credit
        })
        require(authorKind == null || (!saved.queued && !saved.automatic))
        require(!saved.queued || saved.transaction != null)
        require(!saved.available || (saved.transaction != null && !saved.queued))
        require(!saved.automatic || saved.transaction != null)
        require(saved.text.toByteArray(Charsets.UTF_8).size <= 1024 * 1024)
        val record = JsonObject().apply {
            addProperty("text", saved.text)
            add("images", com.google.gson.JsonArray().apply { saved.images.forEach(::add) })
            if (saved.gifAttributions.isNotEmpty()) add("gifAttributions", JsonObject().apply {
                saved.gifAttributions.forEach { (source, credit) -> add(source, credit.toJson()) }
            })
            saved.transaction?.let { addProperty("transaction", it.encoded()) }
            addProperty("queued", saved.queued)
            addProperty("available", saved.available)
            addProperty("automatic", saved.automatic)
            saved.authorBaseline?.let { addProperty("authorBaseline", it) }
        }
        if (saved.transaction != null && saved.gifAttributions.isNotEmpty()) {
            nl.davidengelen.redsquare.media.GiphyAttributionStore(context, owner).retain(saved.gifAttributions)
        }
        file.save(EncryptedLocalFile.Contents(2, record.toString()))
        val reopened = load()
        check(same(reopened, saved)) {
            "Draft persistence verification failed"
        }
        changes.value += 1
    }

    private fun validateTransaction(transaction: SaitoTransaction?, content: String, images: List<String>) {
        require(images.size <= RedSquareImages.MAX_COUNT && images.all {
            it.length <= RedSquarePosts.MAX_IMAGE_CHARS && RedSquareImages.safeSource(it) == it
        })
        require(authorKind == null || images.isEmpty())
        transaction?.let {
            if (authorKind != null) {
                val action = RedSquareAuthorActions.decode(it.envelope())
                require(action.author == owner && action.target == authorTarget && action.kind == authorKind)
                require(action.text == content)
            } else {
                val post = RedSquareFeed.decode(it.envelope())
                require(post.author == owner && post.text == content)
                require(post.parentId == parentId && (parentThread == null || post.threadId == parentThread))
                require(post.quoted?.id.orEmpty() == quoteId)
                val signedImages = SaitoEnvelope.decode(it.envelope()).body.getAsJsonObject("data").get("images")
                    ?: com.google.gson.JsonArray()
                require(signedImages == com.google.gson.JsonArray().apply { images.forEach(::add) })
            }
        }
    }

    private fun validateBaseline(envelope: String?) {
        if (envelope == null) return
        require(authorKind == AuthorActionKind.EDIT)
        val baseline = RedSquareFeed.decode(envelope)
        require(baseline.author == owner && baseline.id == authorTarget)
    }

    /** A mounted editor must preserve delivery progress recorded by another operation. */
    fun saveComposer(saved: Saved): Saved = synchronized(lock) {
        val current = load()
        val replacement = if (current.transaction == null) saved else {
            check(saved.transaction?.id == current.transaction.id)
            if (saved.available || current.available) Saved("") else current
        }
        save(replacement)
        replacement
    }

    /** Compare durable state under the same lock as wallet/backend mutation. */
    fun update(expected: Saved, replacement: Saved): Boolean = synchronized(lock) {
        if (!same(load(), expected)) return@synchronized false
        save(replacement)
        true
    }

    fun claim(expected: Saved): Boolean = synchronized(lock) {
        if (nl.davidengelen.redsquare.saito.AndroidBackendStore(context).load() != backend ||
            nl.davidengelen.redsquare.AndroidWalletStore(context).loadExisting().publicKey != owner) return@synchronized false
        update(expected, expected.copy(queued = false))
    }

    fun confirmAvailable(id: String): Boolean = synchronized(lock) {
        val saved = load()
        if (saved.transaction?.id != id) return@synchronized false
        update(saved, saved.copy(queued = false, available = true))
    }

    fun cancelQueued(expected: Saved): Boolean = synchronized(lock) {
        if (!expected.queued) return@synchronized false
        update(expected, Saved(expected.text, images = expected.images, gifAttributions = expected.gifAttributions))
    }

    companion object {
        fun authorWork(context: Context, owner: String, backend: SaitoBackend): List<AuthorWork> {
            val prefix = "postdraft.${backend.id}.$owner.author."
            val pattern = Regex("${Regex.escape(prefix)}(edit|delete)\\.([0-9a-f]{128})\\.v1")
            return requireNotNull(context.noBackupFilesDir.listFiles()).map { it.name.removeSuffix(".bak") }
                .distinct().mapNotNull { name ->
                    val match = pattern.matchEntire(name) ?: return@mapNotNull null
                    val kind = AuthorActionKind.entries.single { it.storageId == match.groupValues[1] }
                    val target = match.groupValues[2]
                    val saved = author(context, owner, backend, target, kind).load()
                    if (!saved.available && (saved.transaction != null || saved.text.isNotEmpty())) AuthorWork(target, kind, saved)
                    else null
                }
        }
        fun author(context: Context, owner: String, backend: SaitoBackend, target: String, kind: AuthorActionKind): PostDraftStore {
            require(target.matches(Regex("[0-9a-f]{128}")))
            return PostDraftStore(context, owner, backend, "", null, authorTarget = target, authorKind = kind)
        }
        private val lock = nl.davidengelen.redsquare.saito.SaitoLocalState.lock
        val changes = kotlinx.coroutines.flow.MutableStateFlow(0L)

        private fun same(first: Saved, second: Saved) = first.text == second.text &&
            first.transaction?.encoded() == second.transaction?.encoded() &&
            first.queued == second.queued && first.available == second.available && first.automatic == second.automatic &&
            first.authorBaseline == second.authorBaseline && first.images == second.images && first.gifAttributions == second.gifAttributions

        fun pendingDelivery(context: Context, owner: String, backend: SaitoBackend): List<PostDraftStore> {
            val prefix = "postdraft.${backend.id}.$owner"
            return requireNotNull(context.noBackupFilesDir.listFiles()).map { it.name.removeSuffix(".bak") }
                .distinct().filter { it == "$prefix.v1" || it.matches(Regex("${Regex.escape(prefix)}\\.(quote\\.)?[0-9a-f]{128}\\.v1")) }
                .map { name ->
                    val parent = name.removePrefix(prefix).removeSuffix(".v1").removePrefix(".")
                    if (parent.startsWith("quote.")) PostDraftStore(context, owner, backend, "", null, parent.removePrefix("quote."))
                    else PostDraftStore(context, owner, backend, parent, null)
                }.filter { store -> store.load().let { !it.available && (it.queued || it.automatic) } }
        }
    }

}
