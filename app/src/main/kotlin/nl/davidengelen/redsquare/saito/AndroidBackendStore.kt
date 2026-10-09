package nl.davidengelen.redsquare.saito

import android.content.Context
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.IOException

/** Selection is local app state, independent of portable wallet options. */
class AndroidBackendStore(context: Context, private val commitDirectorySync: (FileDescriptor) -> Unit = Os::fsync) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "backend.v1"))
    private val pending = File(context.noBackupFilesDir, "backend.pending")

    fun load(): SaitoBackend = synchronized(lock) {
        check(!pending.exists()) { "Server selection requires an explicit recovery choice" }
        readSelection()
    }

    private fun readSelection(): SaitoBackend {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) {
            check(!File(file.baseFile.path + ".new").exists())
            return SaitoBackend.OWNER
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
        return requireNotNull(SaitoBackend.entries.find { it.id == identifier })
    }

    fun save(backend: SaitoBackend) = synchronized(lock) {
        require(!file.baseFile.exists() || file.baseFile.isFile)
        require(!pending.exists() || (pending.isFile && pending.length() == 0L))
        try {
            // Publish a recovery barrier before any selection mutation. A crash
            // or post-write error must not silently connect to either server.
            FileOutputStream(pending).use { it.fd.sync() }
            syncDirectory(Os::fsync)
            val output = file.startWrite()
            try {
                output.write(backend.id.toByteArray(Charsets.US_ASCII))
                output.fd.sync()
                file.finishWrite(output)
            } catch (failure: Exception) {
                file.failWrite(output)
                throw failure
            }
            syncDirectory(commitDirectorySync)
            check(readSelection() == backend)
            // No fallible work follows publication. If this deletion reappears
            // after a power loss, startup conservatively asks for a choice.
            check(pending.delete())
        } catch (failure: Exception) {
            if (pending.exists()) throw UncertainCommit(failure)
            throw failure
        }
    }

    private fun syncDirectory(sync: (FileDescriptor) -> Unit) {
        val directory = Os.open(file.baseFile.parentFile!!.path, OsConstants.O_RDONLY, 0)
        try { sync(directory) } finally { Os.close(directory) }
    }

    class UncertainCommit(cause: Exception) : IOException("Server selection requires explicit recovery", cause)

    companion object { private val lock = Any() }
}
