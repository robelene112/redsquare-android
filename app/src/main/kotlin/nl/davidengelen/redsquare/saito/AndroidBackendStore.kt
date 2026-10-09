package nl.davidengelen.redsquare.saito

import android.content.Context
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import java.io.File
import java.io.FileDescriptor

/** Selection is local app state, independent of portable wallet options. */
class AndroidBackendStore(context: Context, private val commitDirectorySync: (FileDescriptor) -> Unit = Os::fsync) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "backend.v1"))

    fun load(): SaitoBackend = synchronized(lock) {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) {
            check(!File(file.baseFile.path + ".new").exists())
            return@synchronized SaitoBackend.OWNER
        }
        val identifier = file.openRead().use { input ->
            val bytes = ByteArray(17)
            var count = 0
            while (count < bytes.size) {
                val read = input.read(bytes, count, bytes.size - count)
                if (read < 0) break
                count += read
            }
            require(count in 1..16)
            String(bytes, 0, count, Charsets.US_ASCII)
        }
        requireNotNull(SaitoBackend.entries.find { it.id == identifier })
    }

    fun save(backend: SaitoBackend) = synchronized(lock) {
        require(!file.baseFile.exists() || file.baseFile.isFile)
        val output = file.startWrite()
        try {
            output.write(backend.id.toByteArray(Charsets.US_ASCII))
            output.fd.sync()
            file.finishWrite(output)
        } catch (failure: Exception) {
            file.failWrite(output)
            throw failure
        }
        val directory = Os.open(file.baseFile.parentFile!!.path, OsConstants.O_RDONLY, 0)
        try { commitDirectorySync(directory) } finally { Os.close(directory) }
        check(load() == backend)
    }

    companion object { private val lock = Any() }
}
