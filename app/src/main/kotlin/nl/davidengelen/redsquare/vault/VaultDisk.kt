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
import java.util.UUID

internal data class VaultCopy(val fileId: String, val name: String, val mime: String, val size: Int)
internal data class VaultCopies(val items: List<VaultCopy>, val bytes: Long, val warning: Boolean)

/** Encrypted device copies and one pending export. Metadata commits only after every data part. */
internal class VaultDisk(private val context: Context, owner: String, backend: SaitoBackend) {
    private val family = "vault.$owner."
    private val prefix = "$family${backend.id}."
    private val alias = "redsquare.vault.$owner"
    private val id = Regex("[0-9a-f]{128}")
    init { require(SaitoWallet.isPublicKey(owner)) }
    private fun path(suffix: String) = File(context.noBackupFilesDir, prefix + suffix)
    private fun encrypted(suffix: String) = EncryptedLocalFile(context, prefix + suffix, alias, family)
    private fun checkId(value: String) = require(id.matches(value))
    private fun exists(suffix: String) = path(suffix).isFile || path("$suffix.bak").isFile
    private fun informationFiles(files: List<File>): List<String> = files
        .filter { it.name.matches(Regex("${Regex.escape(prefix)}copy\\.[0-9a-f]{128}\\.info(?:\\.bak)?")) }
        .map { it.name.removePrefix(prefix).removeSuffix(".bak") }.distinct()

    fun copies(): VaultCopies = synchronized(lock) {
        val files = context.noBackupFilesDir.listFiles().orEmpty().filter { it.name.startsWith(prefix) }
        val names = informationFiles(files)
        require(names.size <= MAX_COPIES)
        var warning = false
        val records = names.mapNotNull { suffix ->
            try {
                val value = readInfo(suffix)
                val copy = metadata(value)
                require(suffix == "copy.${copy.fileId}.info")
                require(parts("copy.${copy.fileId}", value).all(::exists))
                copy
            } catch (_: Exception) { warning = true; null }
        }
        VaultCopies(records, files.sumOf { it.length() }, warning)
    }

    /** True means the copy committed but obsolete encrypted parts still need cleanup. */
    fun save(file: VaultFile): Boolean = synchronized(lock) {
        checkId(file.id)
        val files = context.noBackupFilesDir.listFiles().orEmpty().toList()
        require(exists("copy.${file.id}.info") || informationFiles(files).size < MAX_COPIES)
        writeRecord("copy.${file.id}", file)
    }
    fun read(fileId: String): VaultFile = synchronized(lock) { checkId(fileId); readRecord("copy.$fileId") }
    fun remove(fileId: String) = synchronized(lock) { checkId(fileId); removeRecord("copy.$fileId") }

    private fun writeRecord(stem: String, file: VaultFile): Boolean {
        require(!path("$stem.info").exists() || path("$stem.info").isFile)
        val bytes = file.bytes
        val generation = UUID.randomUUID().toString().replace("-", "")
        val count = ((bytes.size + CHUNK_BYTES - 1) / CHUNK_BYTES).coerceAtLeast(1)
        val names = List(count) { "$stem.$generation.$it.data" }
        try {
            names.forEachIndexed { index, name ->
                val from = index * CHUNK_BYTES
                val part = bytes.copyOfRange(from, minOf(bytes.size, from + CHUNK_BYTES))
                encrypted(name).save(EncryptedLocalFile.Contents(1, Base64.getEncoder().encodeToString(part)))
            }
            val info = JsonObject().apply {
                addProperty("id", file.id); addProperty("name", file.name); addProperty("mime", file.mime)
                addProperty("size", bytes.size); addProperty("sha256", digest(bytes))
                addProperty("generation", generation); addProperty("chunks", count)
            }
            encrypted("$stem.info").save(EncryptedLocalFile.Contents(1, info.toString()))
            check(readRecord(stem).bytes.contentEquals(bytes))
        } catch (failure: Exception) {
            // AtomicFile can throw after committing. Never erase data referenced by the actual metadata.
            val committed = try { encrypted("$stem.info").load()?.let {
                JsonParser.parseString(it.text).asJsonObject.get("generation")?.asString == generation
            } == true } catch (_: Exception) { true }
            if (!committed) names.forEach { try { erase(it) } catch (cleanup: Exception) { failure.addSuppressed(cleanup) } }
            throw failure
        }
        var cleanupWarning = false
        dataNames(stem).filter { it !in names }.forEach { try { erase(it) } catch (_: Exception) { cleanupWarning = true } }
        return cleanupWarning
    }
    private fun readRecord(stem: String): VaultFile {
        val info = readInfo("$stem.info")
        val copy = metadata(info)
        require(stem in setOf("export", "draft") || stem == "copy.${copy.fileId}")
        val bytes = ByteArray(copy.size)
        var offset = 0
        for (name in parts(stem, info)) {
            val saved = requireNotNull(encrypted(name).load()).also { require(it.version == 1.toByte()) }
            val part = Base64.getDecoder().decode(saved.text)
            val expected = minOf(CHUNK_BYTES, copy.size - offset)
            // The original single-part device format is retained for an interrupted development update.
            val legacy = !info.has("generation")
            require(part.size == if (legacy) copy.size else expected)
            part.copyInto(bytes, offset); offset += part.size
        }
        require(offset == copy.size && digest(bytes) == info.get("sha256").asString)
        return VaultFile(copy.fileId, copy.name, copy.mime, bytes)
    }
    private fun readInfo(suffix: String): JsonObject {
        val saved = requireNotNull(encrypted(suffix).load()).also { require(it.version == 1.toByte()) }
        return JsonParser.parseString(saved.text).asJsonObject
    }
    private fun parts(stem: String, info: JsonObject): List<String> {
        if (!info.has("generation")) return listOf("$stem.data")
        val generation = info.get("generation").asString.also { require(it.matches(Regex("[0-9a-f]{32}"))) }
        val size = metadata(info).size
        val count = info.get("chunks").asString.toInt()
        require(count == ((size + CHUNK_BYTES - 1) / CHUNK_BYTES).coerceAtLeast(1))
        return List(count) { "$stem.$generation.$it.data" }
    }
    private fun dataNames(stem: String): List<String> {
        val pattern = Regex("${Regex.escape(prefix + stem)}(?:\\.[0-9a-f]{32}\\.[0-9]{1,3})?\\.data(?:\\.bak|\\.new)?")
        return context.noBackupFilesDir.listFiles().orEmpty().filter { pattern.matches(it.name) }
            .map { it.name.removePrefix(prefix).removeSuffix(".bak").removeSuffix(".new") }.distinct()
    }
    private fun removeRecord(stem: String) {
        dataNames(stem).forEach(::erase)
        erase("$stem.info")
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
    fun saveKeys(newKeys: List<VaultKey>) = synchronized(lock) {
        val previous = try { keys() } catch (_: Exception) { emptyMap() }
        val merged = linkedMapOf<String, String>()
        newKeys.forEach { merged[it.holding.id] = it.envelope }
        previous.forEach { (id, envelope) -> if (id !in merged) merged[id] = envelope }
        val rows = JsonArray()
        var bytes = 0
        for ((id, envelope) in merged.entries.take(100)) {
            val row = JsonObject().apply { addProperty("id", id); addProperty("envelope", envelope) }
            bytes += row.toString().toByteArray().size
            if (bytes > 4 * 1024 * 1024) break
            rows.add(row)
        }
        encrypted("keys").save(EncryptedLocalFile.Contents(1, rows.toString()))
    }

    /** A reviewed local source has a content digest, not remote access authority. */
    fun saveDraft(file: VaultFile) = synchronized(lock) {
        val cleanup = writeRecord("draft", file)
        saveDraftRules(file.id, VaultDraftRules())
        cleanup
    }
    fun saveDraftRules(fileId: String, rules: VaultDraftRules) = synchronized(lock) {
        checkId(fileId)
        require(readInfo("draft.info").get("id").asString == fileId)
        val row = JsonObject().apply {
            addProperty("file", fileId); addProperty("mode", rules.mode.name); addProperty("source", rules.source)
        }
        encrypted("draft.rules").save(EncryptedLocalFile.Contents(1, row.toString()))
    }
    fun draftRules(fileId: String): VaultDraftRules = synchronized(lock) {
        val saved = encrypted("draft.rules").load() ?: return@synchronized VaultDraftRules()
        require(saved.version == 1.toByte())
        val row = JsonParser.parseString(saved.text).asJsonObject
        require(row.get("file").asString == fileId)
        VaultDraftRules(VaultRuleMode.valueOf(row.get("mode").asString), row.get("source").asString)
    }
    fun draft(): VaultFile? = synchronized(lock) { if (exists("draft.info")) readRecord("draft") else null }
    fun hasDraftData(): Boolean = synchronized(lock) { exists("draft.info") || dataNames("draft").isNotEmpty() }
    fun discardDraft() = synchronized(lock) { removeRecord("draft"); erase("draft.rules") }

    fun prepareExport(file: VaultFile) = synchronized(lock) { writeRecord("export", file) }
    fun export(): VaultFile? = synchronized(lock) {
        if (exists("export.info")) return@synchronized readRecord("export")
        val saved = encrypted("export").load() ?: return@synchronized null
        require(saved.version == 1.toByte())
        val value = JsonParser.parseString(saved.text).asJsonObject
        VaultFile(value.get("id").asString, value.get("name").asString, value.get("mime").asString,
            Base64.getDecoder().decode(value.get("bytes").asString))
    }
    fun finishExport() = synchronized(lock) { removeRecord("export"); erase("export") }

    private fun erase(suffix: String) {
        val target = path(suffix)
        AtomicFile(target).delete()
        check(!target.exists() && !File(target.path + ".bak").exists() && !File(target.path + ".new").exists())
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
    companion object {
        private const val MAX_COPIES = 128
        private const val CHUNK_BYTES = 2 * 1024 * 1024
        private val lock = Any()
        private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}
