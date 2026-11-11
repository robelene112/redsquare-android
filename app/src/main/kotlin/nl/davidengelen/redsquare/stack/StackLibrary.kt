package nl.davidengelen.redsquare.stack

import android.content.Context
import androidx.compose.runtime.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.feed.PublicStringStore
import nl.davidengelen.redsquare.saito.*

/** Public articles only. Original signed envelopes bind both cache and the resumable reading window. */
internal class StackLibrary(context: Context, private val wallet: SaitoWallet, backend: SaitoBackend,
    private val endpoint: String, private val author: String? = null) {
    private val storage = PublicStringStore(context, "stack.${backend.id}.v1", 100)
    private val window = PublicStringStore(context, "stackwindow.${backend.id}.${wallet.publicKey}.${author ?: "all"}.v1", 101)
    var articles by mutableStateOf<List<StackArticle>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    var cacheWarning by mutableStateOf(false)
        private set
    var cacheReady by mutableStateOf(false)
        private set
    var successfulRead by mutableStateOf(false)
        private set
    var more by mutableStateOf(true)
        private set
    private var before = Long.MAX_VALUE
    private var initialized = false
    private var started = false
    private var restoredWindow = false

    suspend fun start() { if (!started) load(initial = true) }

    suspend fun load(reset: Boolean = true, initial: Boolean = false) {
        if (loading || (!reset && !more)) return
        loading = true
        failed = false
        try {
            if (!initialized) {
                val saved = withContext(Dispatchers.IO) {
                    val cached = storage.load()
                    val known = decode(cached.values)
                    val reading = window.load()
                    val metadata = try { reading.values.firstOrNull()?.let { JsonParser.parseString(it).asJsonObject } }
                        catch (_: Exception) { null }
                    val retained = decode(reading.values.drop(1))
                    val usable = runCatching { metadata != null && metadata.get("version")?.asInt == 1 &&
                        metadata.get("before")?.asString?.toLongOrNull()?.let { it >= 0 } == true &&
                        metadata.get("more")?.let { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean } == true &&
                        !reading.unreadable && retained.size == reading.values.size - 1 &&
                        retained.all { author == null || it.author == author } }.getOrDefault(false)
                    Triple(known, if (usable) metadata!! to retained else null,
                        cached.unreadable || known.size != cached.values.size || reading.unreadable ||
                            (reading.values.isNotEmpty() && !usable))
                }
                val reading = saved.second
                restoredWindow = reading != null && reading.second.isNotEmpty()
                articles = if (restoredWindow) reading!!.second else SaitoStack.newest(saved.first).filter { author == null || it.author == author }
                if (reading != null) { before = reading.first.get("before").asLong; more = reading.first.get("more").asBoolean }
                cacheWarning = saved.third
                initialized = true
                cacheReady = true
            }
            val resume = initial && restoredWindow
            val cursor = if (reset || resume) System.currentTimeMillis() else before
            val page = withContext(Dispatchers.IO) {
                SaitoPeer(wallet, endpoint).use { peer ->
                    SaitoStack.readArchive(peer.request(SaitoStack.archiveRequest(cursor, author).toByteArray(Charsets.UTF_8))
                        .toString(Charsets.UTF_8))
                }
            }
            val received = page.articles.filter { author == null || it.author == author }
            if (page.rowCount > 0 && received.isEmpty()) cacheWarning = true
            val retainedIds = articles.map { it.author to it.id }.toSet()
            val merged = SaitoStack.newest((if (resume) received.filter { it.author to it.id in retainedIds } else received) + articles)
            var bytes = 0
            val retained = (if (reset || resume) merged else merged.asReversed()).takeWhile {
                bytes += it.envelope.length
                bytes <= 12 * 1024 * 1024
            }.take(100)
            val next = if (reset || resume) retained else retained.asReversed()
            val nextBefore = if (resume) before else page.before ?: cursor
            val nextMore = if (resume) more else page.rowCount >= SaitoStack.PAGE_SIZE && page.before != null && nextBefore < cursor && nextBefore > 0
            val warning = withContext(Dispatchers.IO) {
                var failedSave = false
                try {
                    storage.update { saved -> SaitoStack.newest(received + decode(saved.values)).take(100).map { it.envelope } }
                } catch (_: Exception) { failedSave = true }
                try {
                    val metadata = JsonObject().apply {
                        addProperty("version", 1); addProperty("before", nextBefore); addProperty("more", nextMore)
                    }.toString()
                    val values = listOf(metadata) + next.map { it.envelope }
                    check(window.fittingEntryCount(values.asSequence()) == values.size)
                    window.save(values)
                } catch (_: Exception) { failedSave = true }
                failedSave
            }
            cacheWarning = cacheWarning || warning
            articles = next
            before = nextBefore
            more = nextMore
            successfulRead = true
            started = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true; started = true }
        finally { loading = false }
    }

    private fun decode(values: List<String>): List<StackArticle> = values.mapNotNull { envelope ->
        try { SaitoStack.decode(envelope) }
        catch (_: Exception) { null }
    }
}
