package nl.davidengelen.redsquare.stack

import android.content.Context
import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.feed.PublicStringStore
import nl.davidengelen.redsquare.saito.*

/** Public articles only. Cache is backend-scoped and contains original signed envelopes. */
internal class StackLibrary(context: Context, private val wallet: SaitoWallet, backend: SaitoBackend,
    private val endpoint: String, private val author: String? = null) {
    private val storage = PublicStringStore(context, "stack.${backend.id}.v1", 100)
    var articles by mutableStateOf<List<StackArticle>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    var cacheWarning by mutableStateOf(false)
        private set
    var more by mutableStateOf(true)
        private set
    private var before = Long.MAX_VALUE
    private var initialized = false

    suspend fun load(reset: Boolean = true) {
        if (loading || (!reset && !more)) return
        loading = true
        failed = false
        try {
            if (!initialized) {
                val saved = withContext(Dispatchers.IO) {
                    storage.load().let { saved ->
                        saved.values.mapNotNull { try { SaitoStack.decode(it) } catch (_: Exception) { null } } to saved
                    }
                }
                articles = SaitoStack.newest(saved.first).filter { author == null || it.author == author }
                cacheWarning = saved.second.unreadable || saved.first.size != saved.second.values.size
                initialized = true
            }
            val cursor = if (reset) System.currentTimeMillis() else before
            val page = withContext(Dispatchers.IO) {
                SaitoPeer(wallet, endpoint).use { peer ->
                    val response = peer.request(SaitoStack.archiveRequest(cursor, author).toByteArray(Charsets.UTF_8))
                    SaitoStack.readArchive(response.toString(Charsets.UTF_8))
                }
            }
            val received = page.articles.filter { author == null || it.author == author }
            if (page.rowCount > 0 && received.isEmpty()) {
                // Never replace authentic cached content with unreadable or out-of-scope rows.
                cacheWarning = true
            }
            val merged = SaitoStack.newest(received + articles)
            // Page through a bounded reading window. Older continuation may release newer rows
            // above it; the independently bounded disk cache still retains the newest history.
            var bytes = 0
            val window = (if (reset) merged else merged.asReversed()).takeWhile {
                bytes += it.envelope.length
                bytes <= 12 * 1024 * 1024
            }.take(100)
            articles = if (reset) window else window.asReversed()
            before = page.before ?: cursor
            more = page.rowCount >= SaitoStack.PAGE_SIZE && page.before != null && before < cursor && before > 0
            // Keep the current reading list even when its optional disk cache cannot be saved.
            cacheWarning = withContext(Dispatchers.IO) {
                try {
                    storage.update { saved ->
                        val previous = saved.values.mapNotNull { try { SaitoStack.decode(it) } catch (_: Exception) { null } }
                        SaitoStack.newest(received + previous).take(100).map { it.envelope }
                    }
                    cacheWarning
                } catch (_: Exception) { true }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
}
