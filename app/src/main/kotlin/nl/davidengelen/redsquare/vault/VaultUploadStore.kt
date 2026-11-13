package nl.davidengelen.redsquare.vault

import android.content.Context
import android.util.AtomicFile
import android.system.Os
import android.system.OsConstants
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import java.io.File

internal data class VaultUploadRecord(val candidate: SaitoVaultMint.Candidate, val mime: String, val size: Int,
    val mayHaveSent: Boolean = false, val uploaded: Boolean = false, val owned: Boolean = false) {
    val complete: Boolean get() = uploaded && owned
}

/** Small operation journal. It shares the physical-send lock and never takes VaultDisk's content lock. */
internal class VaultUploadStore(private val context: Context, private val owner: String, backend: SaitoBackend) {
    private val name = "vault.$owner.${backend.id}.operation"
    private fun encrypted() = EncryptedLocalFile(context, name, "redsquare.vault.$owner", "vault.$owner.")
    fun hasWork(): Boolean = listOf(name, "$name.bak", "$name.new").any { File(context.noBackupFilesDir, it).exists() }
    fun read(): VaultUploadRecord? = synchronized(SaitoLocalState.lock) {
        if (!hasWork()) return@synchronized null
        val saved = requireNotNull(encrypted().load()).also { require(it.version == 1.toByte()) }
        val row = JsonParser.parseString(saved.text).asJsonObject
        require(row.keySet() == setOf("candidate", "mime", "size", "mayHaveSent", "uploaded", "owned"))
        val candidate = SaitoVaultMint.readCandidate(row.get("candidate").asString)
        require(candidate.author == owner)
        val mime = row.get("mime").asString.also { require(it.matches(Regex("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+"))) }
        val size = row.get("size").asInt.also { require(it in 0..SaitoVault.MAX_FILE_BYTES) }
        fun flag(name: String) = row.get(name).asJsonPrimitive.also { require(it.isBoolean) }.asBoolean
        VaultUploadRecord(candidate, mime, size, flag("mayHaveSent"), flag("uploaded"), flag("owned"))
    }
    fun save(record: VaultUploadRecord, guard: () -> Unit = {}): VaultUploadRecord = synchronized(SaitoLocalState.lock) {
        require(record.candidate.author == owner)
        guard()
        val prior = read()
        require(prior == null || prior.candidate.transaction.encoded() == record.candidate.transaction.encoded())
        val retained = record.copy(mayHaveSent = record.mayHaveSent || prior?.mayHaveSent == true,
            uploaded = record.uploaded || prior?.uploaded == true)
        val row = JsonObject().apply {
            addProperty("candidate", retained.candidate.transaction.encoded()); addProperty("mime", retained.mime); addProperty("size", retained.size)
            addProperty("mayHaveSent", retained.mayHaveSent); addProperty("uploaded", retained.uploaded); addProperty("owned", retained.owned)
        }
        encrypted().save(EncryptedLocalFile.Contents(1, row.toString()))
        requireNotNull(read()).also { written ->
            check(written.candidate.transaction.encoded() == retained.candidate.transaction.encoded() &&
                written.mime == retained.mime && written.size == retained.size && written.mayHaveSent == retained.mayHaveSent &&
                written.uploaded == retained.uploaded && written.owned == retained.owned)
        }
    }
    fun remove() = synchronized(SaitoLocalState.lock) {
        AtomicFile(File(context.noBackupFilesDir, name)).delete(); check(!hasWork())
        val descriptor = Os.open(context.noBackupFilesDir.path, OsConstants.O_RDONLY, 0)
        try { Os.fsync(descriptor) } finally { Os.close(descriptor) }
    }
}
