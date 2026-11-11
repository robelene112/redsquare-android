package nl.davidengelen.redsquare.stack

import android.content.Context
import androidx.compose.runtime.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.feed.PublicStringStore
import nl.davidengelen.redsquare.saito.SaitoWallet

internal class StackLocalDraftMemory : androidx.lifecycle.ViewModel() {
    var state: StackLocalDraft? = null
}

/** One actual local draft per wallet. Publication remains an explicit Stack website action. */
internal class StackLocalDraft(context: Context, owner: String) {
    init { require(SaitoWallet.isPublicKey(owner)) }
    private val storage = PublicStringStore(context, "stackdraft.$owner.v1", 1)
    private val writes = Mutex()
    val visual = StackVisualBuffer()
    var draft by mutableStateOf(StackDraft("", "", ""))
        private set
    var ready by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf(false)
        private set
    private var revision = 0L
    private var savedRevision = 0L
    val hasWork get() = draft.title.isNotBlank() || draft.subtitle.isNotBlank() || draft.body.isNotBlank()

    suspend fun load() {
        if (ready || loading) return
        loading = true
        try {
            draft = withContext(Dispatchers.IO) {
                val saved = storage.load()
                check(!saved.unreadable)
                saved.values.singleOrNull()?.let { raw ->
                    val data = JsonParser.parseString(raw).asJsonObject
                    require(data.get("version").asInt == 1)
                    fun text(key: String): String = data.get(key).also {
                        require(it.isJsonPrimitive && it.asJsonPrimitive.isString)
                    }.asString
                    StackDraft(text("title"), text("subtitle"), text("body"), data.get("sourceMode").asBoolean)
                } ?: StackDraft("", "", "")
            }
            ready = true
            error = false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
        finally { loading = false }
    }

    fun edit(value: StackDraft) {
        if (!ready || saving || value == draft) return
        draft = value
        revision++
    }

    fun editVisual(markdown: String) { edit(visual.applyVisual(draft, markdown)) }

    private fun snapshot(): StackDraft = visual.snapshot(draft).also {
        if (it != draft) { draft = it; revision++ }
    }

    suspend fun autosave() {
        if (!ready || saving) return
        val value = snapshot()
        val version = revision
        if (version == savedRevision) return
        writes.withLock {
            if (saving || version != revision) return@withLock
            try { write(value); savedRevision = version; error = false }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
        }
    }

    suspend fun persist(commit: suspend () -> Boolean = { true }): Boolean {
        if (saving) return false
        if (!ready) return if (!loading && !hasWork) commit() else false
        saving = true
        try {
            // Capture the final visual character on main, before the mutex or IO can suspend.
            val value = snapshot()
            val version = revision
            return writes.withLock {
                write(value)
                savedRevision = version
                error = false
                commit()
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true; return false }
        finally { saving = false }
    }

    private suspend fun write(value: StackDraft) = withContext(Dispatchers.IO) {
        val json = JsonObject().apply {
            addProperty("version", 1); addProperty("title", value.title); addProperty("subtitle", value.subtitle)
            addProperty("body", value.body); addProperty("sourceMode", value.sourceMode)
        }.toString()
        require(json.toByteArray(Charsets.UTF_8).size <= 1024 * 1024)
        storage.save(listOf(json))
    }
}
