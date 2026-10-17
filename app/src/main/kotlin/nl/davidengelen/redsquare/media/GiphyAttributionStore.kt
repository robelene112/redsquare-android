package nl.davidengelen.redsquare.media

import android.content.Context
import androidx.compose.runtime.*
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.saito.EncryptedLocalFile
import nl.davidengelen.redsquare.saito.SaitoLocalState

internal val LocalGiphyAttributions = staticCompositionLocalOf<GiphyAttributionStore?> { null }

/** Wallet-local presentation references for explicitly sent GIFs, never search results or media bytes. */
internal class GiphyAttributionStore(context: Context, owner: String) {
    init { require(owner.matches(Regex("[1-9A-HJ-NP-Za-km-z]{43,45}"))) }
    private val file = EncryptedLocalFile(context, "giphycredits.$owner.v1", "redsquare.giphycredits.v1", "giphycredits.")
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()

    fun read(sources: List<String>): Map<String, GiphyAttribution> = synchronized(SaitoLocalState.lock) {
        load().filterKeys { it in sources }
    }

    /** Commit before the signed draft can be transmitted or its disposable composer cleared. */
    fun retain(credits: Map<String, GiphyAttribution>) = synchronized(SaitoLocalState.lock) {
        if (credits.isEmpty()) return@synchronized
        require(credits.size <= 4 && credits.all { (source, credit) ->
            source.length <= 8192 && GiphyAttribution.fromJson(credit.toJson()) == credit
        })
        val previous = load()
        val merged = previous.toMutableMap()
        credits.forEach { (source, credit) -> merged.remove(source); merged[source] = credit }
        val record = JsonObject()
        var remaining = MAX_BYTES - 2
        for ((source, credit) in merged.entries.toList().asReversed().take(MAX_ENTRIES)) {
            val value = credit.toJson()
            val entryBytes = JsonObject().apply { add(source, value) }.toString().toByteArray(Charsets.UTF_8).size - 2
            val cost = entryBytes + if (record.size() == 0) 0 else 1
            if (cost > remaining) break
            record.add(source, value)
            remaining -= cost
        }
        // Oldest-first on disk, so later sends retain the most recent references.
        val ordered = JsonObject().apply { record.entrySet().toList().asReversed().forEach { add(it.key, it.value) } }
        if (previous == decode(ordered)) return@synchronized
        file.save(EncryptedLocalFile.Contents(1, ordered.toString()))
        changes.value += 1
    }

    private fun load(): Map<String, GiphyAttribution> {
        val contents = file.load() ?: return emptyMap()
        require(contents.version == 1.toByte() && contents.text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        return decode(gson.fromJson(contents.text, JsonObject::class.java))
    }

    private fun decode(record: JsonObject): Map<String, GiphyAttribution> {
        require(record.size() <= MAX_ENTRIES)
        return record.entrySet().mapNotNull { (source, value) ->
            if (source.length > 8192) null else GiphyAttribution.fromJson(value)?.let { source to it }
        }.toMap()
    }

    companion object {
        private const val MAX_ENTRIES = 128
        private const val MAX_BYTES = 2 * 1024 * 1024
        val changes = MutableStateFlow(0L)
    }
}

@Composable
internal fun retainedGiphyAttributions(sources: List<String>): Map<String, GiphyAttribution> {
    if (sources.isEmpty()) return emptyMap()
    val store = LocalGiphyAttributions.current ?: return emptyMap()
    val version by GiphyAttributionStore.changes.collectAsState()
    return key(store, sources) {
        val credits by produceState(emptyMap<String, GiphyAttribution>(), version) {
            value = withContext(Dispatchers.IO) {
                try { store.read(sources) } catch (_: Exception) { emptyMap() }
            }
        }
        credits
    }
}
