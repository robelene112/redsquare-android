package nl.davidengelen.redsquare.feed

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.davidengelen.redsquare.saito.EncryptedLocalFile
import nl.davidengelen.redsquare.saito.SaitoLocalState
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Private text checkpoints never replace an explicit draft or become publication candidates. */
internal class UnfinishedTextRecovery(context: Context, draftName: String,
    private val source: () -> EncryptedLocalFile.Contents?) {
    private val name = "editrecovery.${digest(draftName.toByteArray())}.v1"
    private val key = File(context.noBackupFilesDir, name).path
    private val file = EncryptedLocalFile(context, name, "redsquare.editrecovery.v1", "editrecovery.")
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()
    private data class Checkpoint(val anchor: String, val text: String)
    private data class Edit(val generation: Long, val version: Long, val text: String)

    fun open(): EditingSession = synchronized(SaitoLocalState.lock) {
        var unreadable = false
        val saved = try { read() } catch (_: Exception) { unreadable = true; null }
        val anchor = anchor()
        EditingSession(anchor, saved?.takeIf { it.anchor == anchor }?.text, unreadable).also { session ->
            active.put(key, session)?.close()
        }
    }

    /** Call only after an explicit draft operation commits, under the same storage lock. */
    fun retire() = synchronized(SaitoLocalState.lock) {
        if (File(key).exists() || File("$key.bak").exists()) file.save(EncryptedLocalFile.Contents(1, "{}"))
        active[key]?.advance(anchor())
    }

    private fun anchor(): String = source()?.let { digest(byteArrayOf(it.version) + it.text.toByteArray()) }
        ?: digest(byteArrayOf())

    private fun read(): Checkpoint? {
        val contents = file.load() ?: return null
        require(contents.version == 1.toByte())
        val record = requireNotNull(gson.fromJson(contents.text, JsonObject::class.java))
        if (record.size() == 0) return null
        require(record.keySet() == setOf("anchor", "text"))
        require(record.get("anchor").isJsonPrimitive && record.getAsJsonPrimitive("anchor").isString)
        require(record.get("text").isJsonPrimitive && record.getAsJsonPrimitive("text").isString)
        val anchor = record.get("anchor").asString
        val text = record.get("text").asString
        require(anchor.matches(Regex("[0-9a-f]{64}")) && text.toByteArray().size <= 1024 * 1024)
        return Checkpoint(anchor, text)
    }

    /** One editor owns the writer across Activity recreation; superseded editors cannot reacquire it. */
    inner class EditingSession internal constructor(@Volatile private var baseline: String,
        val restored: String?, unreadable: Boolean) : AutoCloseable {
        var failed by mutableStateOf(unreadable)
            private set
        private val generation = AtomicLong()
        private val version = AtomicLong()
        @Volatile private var latestText: String? = null
        private val pending = Channel<Edit>(Channel.CONFLATED)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        init {
            scope.launch {
                for (edit in pending) {
                    delay(150)
                    if (!current(edit)) continue
                    try {
                        synchronized(SaitoLocalState.lock) {
                            if (!current(edit)) return@synchronized
                            check(anchor() == baseline) { "Draft changed before checkpoint" }
                            read() // Never silently replace unreadable earlier recovery work.
                            val record = JsonObject().apply {
                                addProperty("anchor", baseline); addProperty("text", edit.text)
                            }
                            require(edit.text.toByteArray().size <= 1024 * 1024)
                            file.save(EncryptedLocalFile.Contents(1, record.toString()))
                        }
                        if (current(edit)) failed = false
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { if (current(edit)) failed = true }
                }
            }
        }

        private fun current(edit: Edit) = active[key] === this && edit.generation == generation.get() && edit.version == version.get()

        fun update(text: String) {
            latestText = text
            if (active[key] !== this) { failed = true; return }
            if (!pending.trySend(Edit(generation.get(), version.incrementAndGet(), text)).isSuccess) failed = true
        }

        fun retry() { latestText?.let(::update) }

        internal fun advance(anchor: String) {
            generation.incrementAndGet()
            baseline = anchor
            latestText = null
            failed = false
        }

        override fun close() {
            active.remove(key, this)
            scope.cancel()
            pending.close()
        }
    }

    companion object {
        private val active = ConcurrentHashMap<String, EditingSession>()
        private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}
