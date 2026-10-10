package nl.davidengelen.redsquare.feed

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import java.io.File
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.RedSquareFeed
import nl.davidengelen.redsquare.saito.FeedPost
import nl.davidengelen.redsquare.saito.AuthorActionKind
import nl.davidengelen.redsquare.saito.RedSquareAuthorActions
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness

/** Unpublished text stays encrypted on this device, scoped to its wallet and backend. */
internal class PostDraftStore private constructor(private val context: Context, private val owner: String, private val backend: SaitoBackend,
    private val parentId: String, private val parentThread: String?, private val quoteId: String = "",
    private val authorTarget: String = "", private val authorKind: AuthorActionKind? = null) {
    constructor(context: Context, owner: String, backend: SaitoBackend, parent: FeedPost? = null, quote: FeedPost? = null) :
        this(context, owner, backend, parent?.id.orEmpty(), parent?.threadId, quote?.id.orEmpty())
    init { require(owner.matches(Regex("[1-9A-HJ-NP-Za-km-z]{43,45}"))) }
    private val file = AtomicFile(File(context.noBackupFilesDir,
        "postdraft.${backend.id}.$owner${if (authorKind != null) ".author.${authorKind.storageId}.$authorTarget" else if (quoteId.isNotEmpty()) ".quote.$quoteId" else parentId.takeIf { it.isNotEmpty() }?.let { ".$it" }.orEmpty()}.v1"))
    private val associatedData = "${context.packageName}:${file.baseFile.name}".toByteArray(Charsets.UTF_8)

    data class Saved(val text: String, val transaction: SaitoTransaction? = null, val queued: Boolean = false,
        val available: Boolean = false, val automatic: Boolean = false, val authorBaseline: String? = null)
    data class AuthorWork(val target: String, val kind: AuthorActionKind, val saved: Saved)
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()

    fun load(): Saved = synchronized(lock) {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@synchronized Saved("")
        val key = requireNotNull(existingKey()) { "Draft storage key unavailable" }
        val encrypted = file.openRead().use { input ->
            val buffer = ByteArray(MAX_BYTES + 30)
            var size = 0
            while (size < buffer.size) {
                val count = input.read(buffer, size, buffer.size - size)
                if (count < 0) break
                size += count
            }
            require(size in 29 until buffer.size && buffer[0] in 1.toByte()..2.toByte())
            buffer.copyOf(size)
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, encrypted.copyOfRange(1, 13)))
            updateAAD(associatedData)
        }
        val text = cipher.doFinal(encrypted.copyOfRange(13, encrypted.size))
        try {
            val decoded = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(text)).toString()
            if (encrypted[0] == 1.toByte()) Saved(decoded)
            else {
                val record = gson.fromJson(decoded, JsonObject::class.java)
                val content = record.get("text").asString
                val transaction = record.get("transaction")?.takeUnless { it.isJsonNull }?.asString?.let(SaitoTransaction::restore)
                validateTransaction(transaction, content)
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
                Saved(content, transaction, queued, available, automatic, baseline)
            }
        } finally { text.fill(0) }
    }

    fun save(saved: Saved) = synchronized(lock) {
        validateTransaction(saved.transaction, saved.text)
        validateBaseline(saved.authorBaseline)
        require(authorKind == null || (!saved.queued && !saved.automatic))
        require(!saved.queued || saved.transaction != null)
        require(!saved.available || (saved.transaction != null && !saved.queued))
        require(!saved.automatic || saved.transaction != null)
        require(saved.text.toByteArray(Charsets.UTF_8).size <= 1024 * 1024)
        val record = JsonObject().apply {
            addProperty("text", saved.text)
            saved.transaction?.let { addProperty("transaction", it.encoded()) }
            addProperty("queued", saved.queued)
            addProperty("available", saved.available)
            addProperty("automatic", saved.automatic)
            saved.authorBaseline?.let { addProperty("authorBaseline", it) }
        }
        val encoded = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(record.toString()))
        val plaintext = ByteArray(encoded.remaining()).also { encoded.get(it) }
        val encrypted = try {
            require(plaintext.size <= MAX_BYTES)
            val key = existingKey() ?: run {
                require(context.noBackupFilesDir.listFiles()?.none { it.name.startsWith("postdraft.") } == true) {
                    "Existing drafts require recovery"
                }
                KeyGenerator.getInstance("AES", "AndroidKeyStore").run {
                    init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
                    generateKey()
                }
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, key)
                updateAAD(associatedData)
            }
            byteArrayOf(2) + cipher.iv + cipher.doFinal(plaintext)
        } finally { plaintext.fill(0) }
        require(!file.baseFile.exists() || file.baseFile.isFile)
        val output = file.startWrite()
        try {
            output.write(encrypted)
            output.fd.sync()
            file.finishWrite(output)
        } catch (failure: Exception) { file.failWrite(output); throw failure }
        val directory = Os.open(file.baseFile.parentFile!!.path, OsConstants.O_RDONLY, 0)
        try { Os.fsync(directory) } finally { Os.close(directory) }
        val reopened = load()
        check(same(reopened, saved)) {
            "Draft persistence verification failed"
        }
        changes.value += 1
    }

    private fun validateTransaction(transaction: SaitoTransaction?, content: String) {
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
        update(expected, Saved(expected.text))
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
        private const val KEY_ALIAS = "redsquare.postdraft.v1"
        private const val MAX_BYTES = 5 * 1024 * 1024
        private val lock = nl.davidengelen.redsquare.saito.SaitoLocalState.lock
        val changes = kotlinx.coroutines.flow.MutableStateFlow(0L)

        private fun same(first: Saved, second: Saved) = first.text == second.text &&
            first.transaction?.encoded() == second.transaction?.encoded() &&
            first.queued == second.queued && first.available == second.available && first.automatic == second.automatic &&
            first.authorBaseline == second.authorBaseline

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

    private fun existingKey(): SecretKey? = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        .getKey(KEY_ALIAS, null) as? SecretKey

}
