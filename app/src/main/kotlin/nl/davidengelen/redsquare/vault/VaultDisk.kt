package nl.davidengelen.redsquare.vault

import android.content.Context
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import java.io.File
import java.security.MessageDigest
import java.util.Base64

internal data class VaultCopy(val fileId: String, val name: String, val mime: String, val size: Int)
internal data class VaultCopies(val items: List<VaultCopy>, val bytes: Long, val warning: Boolean)

/** Private, encrypted device copies. Filenames are locators, never the user's filename. */
internal class VaultDisk(private val context: Context, owner: String, backend: SaitoBackend) {
    private val family = "vault.$owner."
    private val prefix = "$family${backend.id}."
    private val alias = "redsquare.vault.$owner"
    private val id = Regex("[0-9a-f]{128}")
    init { require(SaitoWallet.isPublicKey(owner)) }
    private fun path(suffix: String) = File(context.noBackupFilesDir, prefix + suffix)
    private fun encrypted(suffix: String) = EncryptedLocalFile(context, prefix + suffix, alias, family)
    private fun checkId(value: String) = require(id.matches(value))

    fun copies(): VaultCopies = synchronized(lock) {
        val files = context.noBackupFilesDir.listFiles().orEmpty().filter { it.name.startsWith(prefix) }
        val names = files.filter { it.name.matches(Regex("${Regex.escape(prefix)}copy\\.[0-9a-f]{128}\\.info(?:\\.bak)?")) }
            .map { it.name.removePrefix(prefix).removeSuffix(".bak") }.distinct()
        require(names.size <= MAX_COPIES)
        var warning = false
        val records = names.mapNotNull { suffix ->
            try {
                val record = requireNotNull(encrypted(suffix).load()).also { require(it.version == 1.toByte()) }
                val value = JsonParser.parseString(record.text).asJsonObject
                val copy = metadata(value)
                require(suffix == "copy.${copy.fileId}.info")
                require(path("copy.${copy.fileId}.data").isFile || path("copy.${copy.fileId}.data.bak").isFile)
                copy
            } catch (_: Exception) { warning = true; null }
        }
        VaultCopies(records, files.sumOf { it.length() }, warning)
    }

    fun save(file: VaultFile) = synchronized(lock) {
        checkId(file.id)
        val current = copies()
        require(current.items.any { it.fileId == file.id } || current.items.size < MAX_COPIES)
        val bytes = file.bytes
        val metadata = JsonObject().apply {
            addProperty("id", file.id); addProperty("name", file.name); addProperty("mime", file.mime)
            addProperty("size", bytes.size); addProperty("sha256", digest(bytes))
        }
        encrypted("copy.${file.id}.data").save(EncryptedLocalFile.Contents(1, Base64.getEncoder().encodeToString(bytes)))
        encrypted("copy.${file.id}.info").save(EncryptedLocalFile.Contents(1, metadata.toString()))
        check(read(file.id).bytes.contentEquals(bytes))
    }

    fun read(fileId: String): VaultFile = synchronized(lock) {
        checkId(fileId)
        val info = requireNotNull(encrypted("copy.$fileId.info").load()).also { require(it.version == 1.toByte()) }
        val body = JsonParser.parseString(info.text).asJsonObject
        val copy = metadata(body).also { require(it.fileId == fileId) }
        val saved = requireNotNull(encrypted("copy.$fileId.data").load()).also { require(it.version == 1.toByte()) }
        val bytes = Base64.getDecoder().decode(saved.text)
        require(bytes.size == copy.size && digest(bytes) == body.get("sha256").asString)
        localFile(copy.fileId, copy.name, copy.mime, bytes)
    }

    fun remove(fileId: String) = synchronized(lock) {
        checkId(fileId)
        erase("copy.$fileId.data")
        erase("copy.$fileId.info")
    }

    fun keys(): Map<String, String> = synchronized(lock) {
        val saved = encrypted("keys").load() ?: return@synchronized emptyMap()
        require(saved.version == 1.toByte())
        val rows = JsonParser.parseString(saved.text).asJsonArray.also { require(it.size() <= 100) }
        rows.associate { entry ->
            val value = entry.asJsonObject
            value.get("id").asString.also { require(it.matches(Regex("[0-9a-f]{66}"))) } to value.get("envelope").asString
        }
    }

    fun saveKeys(keys: List<VaultKey>) = synchronized(lock) {
        val rows = JsonArray()
        var bytes = 0
        for (key in keys.distinctBy { it.holding.id }.take(100)) {
            val row = JsonObject().apply { addProperty("id", key.holding.id); addProperty("envelope", key.envelope) }
            bytes += row.toString().toByteArray().size
            if (bytes > 4 * 1024 * 1024) break
            rows.add(row)
        }
        encrypted("keys").save(EncryptedLocalFile.Contents(1, rows.toString()))
    }

    /** One deliberate document export can survive recreation without making it a permanent offline copy. */
    fun prepareExport(file: VaultFile) = synchronized(lock) {
        val value = JsonObject().apply {
            addProperty("id", file.id); addProperty("name", file.name); addProperty("mime", file.mime)
            addProperty("bytes", Base64.getEncoder().encodeToString(file.bytes))
        }
        encrypted("export").save(EncryptedLocalFile.Contents(1, value.toString()))
    }
    fun export(): VaultFile? = synchronized(lock) {
        val saved = encrypted("export").load() ?: return@synchronized null
        require(saved.version == 1.toByte())
        val value = JsonParser.parseString(saved.text).asJsonObject
        localFile(value.get("id").asString, value.get("name").asString, value.get("mime").asString,
            Base64.getDecoder().decode(value.get("bytes").asString))
    }
    fun finishExport() = synchronized(lock) { erase("export") }

    private fun erase(suffix: String) {
        val target = path(suffix)
        AtomicFile(target).delete()
        check(!target.exists() && !File(target.path + ".bak").exists())
        val directory = Os.open(context.noBackupFilesDir.path, OsConstants.O_RDONLY, 0)
        try { Os.fsync(directory) } finally { Os.close(directory) }
    }
    private fun metadata(body: JsonObject): VaultCopy {
        val fileId = body.get("id").asString.also(::checkId)
        val name = body.get("name").asString.also { require(it.isNotBlank() && it.length <= 1024) }
        val mime = body.get("mime").asString.also { require(it.matches(Regex("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+"))) }
        val size = body.get("size").asString.toInt().also { require(it in 0..SaitoVault.MAX_FILE_BYTES) }
        return VaultCopy(fileId, name, mime, size)
    }
    private fun localFile(id: String, name: String, mime: String, bytes: ByteArray): VaultFile =
        VaultFile(id, name, mime, bytes)

    companion object {
        private const val MAX_COPIES = 128
        private val lock = Any()
        private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}
