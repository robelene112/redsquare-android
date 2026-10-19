package nl.davidengelen.redsquare.policy

import android.content.Context
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream

/** Installation-local revision only. Wallet imports and exports do not carry agreement. */
internal class PublishingAgreementStore(context: Context,
    private val commitDirectorySync: (FileDescriptor) -> Unit = Os::fsync) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "publishing-agreement.v1"))
    private val pending = File(context.noBackupFilesDir, "publishing-agreement.pending")

    fun accepted(revision: String): Boolean = synchronized(lock) {
        try { !pending.exists() && readRevision() == revision }
        catch (_: Exception) { false }
    }

    private fun readRevision(): String? {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
        val bytes = file.openRead().use { input ->
            val buffer = ByteArray(123)
            var count = 0
            while (count < buffer.size) {
                val size = input.read(buffer, count, buffer.size - count)
                if (size < 0) break
                check(size > 0)
                count += size
            }
            require(count in 3..122)
            buffer.copyOf(count)
        }
        val record = String(bytes, Charsets.US_ASCII)
        require(record.matches(Regex("1\n[a-zA-Z0-9._-]{1,120}")))
        return record.substring(2)
    }

    fun accept(revision: String) = synchronized(lock) {
        require(revision.matches(Regex("[a-zA-Z0-9._-]{1,120}")))
        require(!file.baseFile.exists() || file.baseFile.isFile)
        require(!pending.exists() || (pending.isFile && pending.length() == 0L))
        // A late failed commit must not become permission after reopening the app.
        FileOutputStream(pending).use { it.fd.sync() }
        syncDirectory(Os::fsync)
        val output = file.startWrite()
        try {
            output.write("1\n$revision".toByteArray(Charsets.US_ASCII))
            output.fd.sync()
            file.finishWrite(output)
        } catch (failure: Exception) { file.failWrite(output); throw failure }
        syncDirectory(commitDirectorySync)
        check(readRevision() == revision)
        // No fallible operation follows publication. A reappearing marker after
        // power loss conservatively requires deliberate agreement again.
        check(pending.delete())
    }

    private fun syncDirectory(sync: (FileDescriptor) -> Unit) {
        val descriptor = Os.open(file.baseFile.parentFile!!.path, OsConstants.O_RDONLY, 0)
        try { sync(descriptor) } finally { Os.close(descriptor) }
    }

    companion object { private val lock = Any() }
}
