package nl.davidengelen.redsquare.spaces

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.system.Os
import android.system.OsConstants
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.EncryptedLocalFile
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex

/** Original encrypted upload and opt-in offline copies. Provider keys never enter a filename. */
internal class SpaceFileLocal(private val context: Context, owner: String, backend: String, private val service: String) {
    private val namespace = hash("SpacesFiles/v1:$owner:$backend:$service")
    private val root = File(context.noBackupFilesDir, "space-files-$namespace")
    data class Upload(val file: SpaceFileDescriptor, val operation: String? = null) {
        override fun toString() = "Prepared Space upload"
    }

    data class Setup(val terms: String, val operation: String) {
        override fun toString() = "Prepared Space file storage consent"
    }

    data class Copy(val scope: SpaceFileCipher.Scope, val descriptor: SpaceFileDescriptor, val path: File) {
        override fun toString() = "Encrypted offline Space file"
    }

    fun pending(target: SpacesAccount.Target): Upload? =
        record(target, "upload").load()?.let { saved ->
            require(saved.version == 1.toByte())
            val fields = JsonParser.parseString(saved.text).asJsonObject
            if (!fields.has("file")) return@let null
            Upload(SpaceFileDescriptor.read(fields.get("file").asString), fields.get("operation")?.asString)
        }

    fun notePublication(target: SpacesAccount.Target, upload: Upload, operation: String) =
        savePending(target, upload.copy(operation = operation))

    fun clearPending(target: SpacesAccount.Target, upload: Upload) {
        require(pending(target)?.file?.sealed?.id == upload.file.sealed.id)
        record(target, "upload").save(EncryptedLocalFile.Contents(1, "{}"))
        val bytes = upload(target, upload)
        check(!bytes.exists() || bytes.delete())
    }

    fun setup(space: String): Setup? = record(SpacesAccount.Target(space, space), "storage").load()?.let { saved ->
        require(saved.version == 1.toByte())
        val fields = JsonParser.parseString(saved.text).asJsonObject
        if (!fields.has("consent")) null else Setup(fields.get("terms").asString, fields.get("consent").asString)
    }

    fun setupGate(space: String): Mutex = setupGates.computeIfAbsent("$namespace:$space") { Mutex() }

    fun saveSetup(space: String, setup: Setup?) {
        val fields = JsonObject().apply {
            setup?.let {
                addProperty("terms", it.terms)
                addProperty("consent", it.operation)
            }
        }
        record(SpacesAccount.Target(space, space), "storage").save(EncryptedLocalFile.Contents(1, fields.toString()))
    }

    fun prepare(target: SpacesAccount.Target, uri: Uri): Upload {
        require(pending(target) == null)
        val resolver = context.contentResolver
        val fields = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
            if (!it.moveToFirst()) null else {
                val name = it.getColumnIndex(OpenableColumns.DISPLAY_NAME).let { position ->
                    if (position < 0 || it.isNull(position)) null else it.getString(position)
                }
                val bytes = it.getColumnIndex(OpenableColumns.SIZE).let { position ->
                    if (position < 0 || it.isNull(position)) null else it.getLong(position).takeIf { size -> size >= 0 }
                }
                name to bytes
            }
        }
        val name = fields?.first?.let { value ->
            value.replace("/", "_").replace("\\", "_").filterNot(Char::isISOControl).trim()
                .takeIf { it.isNotBlank() && it != "." && it != ".." }
                ?.take(255)?.let { short ->
                    var end = short.length
                    while (short.substring(0, end).toByteArray(Charsets.UTF_8).size > 255) end--
                    short.substring(0, end)
                }
        } ?: "Attachment"
        val mime = resolver.getType(uri)?.lowercase(java.util.Locale.ROOT)?.takeIf { it.length <= 127 && it.matches(MIME) }
            ?: "application/octet-stream"
        val size = fields?.second ?: requireNotNull(resolver.openInputStream(uri)).use(::measure)
        val encryptedBytes = SpaceFileCipher.ciphertextSize(size)
        val directory = directory(target)
        require(directory.usableSpace >= encryptedBytes + 1024 * 1024)
        val temporary = File.createTempFile("upload-", ".part", directory)
        try {
            val identity = requireNotNull(resolver.openInputStream(uri)).use { source ->
                FileOutputStream(temporary).use { sink ->
                    SpaceFileCipher.seal(scope(target), size, source, sink).also { sink.fd.sync() }
                }
            }
            val prepared = Upload(SpaceFileDescriptor(name, mime, identity))
            move(temporary, upload(target, prepared))
            savePending(target, prepared)
            return prepared
        } finally { temporary.delete() }
    }

    fun upload(target: SpacesAccount.Target, upload: Upload): File =
        File(directory(target), "${upload.file.sealed.id}.outgoing")

    fun offlineCopy(target: SpacesAccount.Target, record: String, file: SpaceFileDescriptor): Copy? {
        val copy = Copy(scope(target), file, localFile(target, record))
        return copy.takeIf { copy.path.isFile && copy.path.length() == file.sealed.bytes }
    }

    fun offlineDescriptor(target: SpacesAccount.Target, id: String): SpaceFileDescriptor? {
        localFile(target, id)
        val saved = record(target, "offline.${hash(id)}").load() ?: return null
        require(saved.version == 1.toByte())
        return if (saved.text == "{}") null else SpaceFileDescriptor.read(saved.text)
    }

    /** Prune orphaned staging after a killed process without touching indexed copies. */
    fun removeAbandonedStaging(target: SpacesAccount.Target, current: Upload?, now: Long) {
        val directory = directory(target)
        directory.listFiles().orEmpty().forEach { file ->
            if (!file.isFile || file.lastModified() > now - STAGING_MAX_AGE) return@forEach
            val candidate = file.name
            val untracked = when {
                TEMPORARY.matches(candidate) -> true
                OUTGOING.matches(candidate) -> candidate.substringBefore('.') != current?.file?.sealed?.id
                OFFLINE.matches(candidate) -> offlineDescriptor(target, candidate.substringBefore('.')) == null
                else -> false
            }
            if (untracked) file.delete()
        }
    }

    fun removal(target: SpacesAccount.Target): String? = record(target, "remove").load()?.text?.takeIf { it.isNotEmpty() }

    fun saveRemoval(target: SpacesAccount.Target, id: String?) {
        if (id != null) require(id.matches(Regex("[0-9a-f]{128}")))
        record(target, "remove").save(EncryptedLocalFile.Contents(1, id.orEmpty()))
    }

    fun removeOffline(target: SpacesAccount.Target, record: String) {
        val file = localFile(target, record)
        this.record(target, "offline.${hash(record)}").save(EncryptedLocalFile.Contents(1, "{}"))
        check(!file.exists() || file.delete())
    }

    fun stageDownload(target: SpacesAccount.Target, file: SpaceFileDescriptor, source: InputStream): Copy {
        val directory = directory(target)
        require(directory.usableSpace >= file.sealed.bytes + 1024 * 1024)
        val temporary = File.createTempFile("download-", ".part", directory)
        val copy = Copy(scope(target), file, temporary)
        try {
            FileOutputStream(temporary).use { sink ->
                val buffer = ByteArray(64 * 1024)
                var remaining = file.sealed.bytes
                while (remaining > 0) {
                    val count = source.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                    require(count > 0) { "File transfer ended early" }
                    sink.write(buffer, 0, count)
                    remaining -= count
                }
                require(source.read() == -1)
                sink.fd.sync()
            }
            verify(copy)
            return copy
        } catch (failure: Exception) {
            temporary.delete()
            throw failure
        }
    }

    fun saveOffline(target: SpacesAccount.Target, record: String, copy: Copy) {
        require(copy.scope == scope(target))
        move(copy.path, localFile(target, record))
        this.record(target, "offline.${hash(record)}").save(EncryptedLocalFile.Contents(1, copy.descriptor.encode()))
    }

    fun verify(copy: Copy) {
        val discard = object : OutputStream() {
            override fun write(b: Int) = Unit
            override fun write(b: ByteArray, off: Int, len: Int) = Unit
        }
        copy.path.inputStream().use { SpaceFileCipher.open(copy.scope, copy.descriptor.sealed, it, discard) }
    }

    private fun move(source: File, destination: File) {
        Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        val descriptor = Os.open(destination.parentFile!!.path, OsConstants.O_RDONLY, 0)
        try { Os.fsync(descriptor) } finally { Os.close(descriptor) }
    }

    private fun savePending(target: SpacesAccount.Target, upload: Upload) {
        val fields = JsonObject().apply {
            addProperty("file", upload.file.encode())
            upload.operation?.let { addProperty("operation", it) }
        }
        record(target, "upload").save(EncryptedLocalFile.Contents(1, fields.toString()))
    }

    private fun scope(target: SpacesAccount.Target) = SpaceFileCipher.Scope(service, target.space, target.channel)

    private fun directory(target: SpacesAccount.Target): File = File(root, hash("${target.space}:${target.channel}")).apply {
        check(isDirectory || mkdirs() || isDirectory)
    }

    private fun localFile(target: SpacesAccount.Target, id: String): File {
        require(id.matches(Regex("[0-9a-f]{128}")))
        return File(directory(target), "$id.offline")
    }

    private fun record(target: SpacesAccount.Target, kind: String) = EncryptedLocalFile(context,
        "$PREFIX$namespace.${hash("${target.space}:${target.channel}")}.$kind", "spaces.file.records.v1", PREFIX)

    private fun measure(source: InputStream): Long {
        val buffer = ByteArray(64 * 1024)
        var seen = 0L
        while (true) {
            val count = source.read(buffer)
            if (count == -1) return seen
            require(count > 0 && seen <= SpaceFileCipher.MAX_FILE_BYTES - count)
            seen += count
        }
    }

    private companion object {
        val setupGates = ConcurrentHashMap<String, Mutex>()
        const val PREFIX = "spaces-file."
        const val STAGING_MAX_AGE = 2 * 60 * 60_000L
        val TEMPORARY = Regex("(upload|download)-[0-9]+\\.part")
        val OUTGOING = Regex("[0-9a-f]{64}\\.outgoing")
        val OFFLINE = Regex("[0-9a-f]{128}\\.offline")
        val MIME = Regex("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+")
        fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
