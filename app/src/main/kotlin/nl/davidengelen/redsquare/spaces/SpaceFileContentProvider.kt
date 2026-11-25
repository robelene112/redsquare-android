package nl.davidengelen.redsquare.spaces

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.SystemClock
import android.os.storage.StorageManager
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.OsConstants
import java.io.FileNotFoundException
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Explicitly opened copies are served as seekable descriptors without plaintext files on disk. */
class SpaceFileContentProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException()
        val entry = get(uri)
        val source = RandomAccessFile(entry.copy.path, "r")
        val reader = try { SpaceFileCipher.Reader(entry.copy.scope, entry.copy.descriptor.sealed, source) }
            catch (problem: Exception) { source.close(); throw FileNotFoundException().apply { initCause(problem) } }
        try {
            val storage = requireNotNull(requireNotNull(context).getSystemService(StorageManager::class.java))
            return storage.openProxyFileDescriptor(
                ParcelFileDescriptor.MODE_READ_ONLY, object : ProxyFileDescriptorCallback() {
                    override fun onGetSize(): Long = reader.size
                    override fun onRead(offset: Long, size: Int, data: ByteArray): Int =
                        try { reader.read(offset, size, data) }
                        catch (_: Exception) { throw ErrnoException("read", OsConstants.EIO) }
                    override fun onRelease() = reader.close()
                }, worker)
        } catch (problem: Exception) {
            reader.close()
            throw FileNotFoundException().apply { initCause(problem) }
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?,
        sortOrder: String?): Cursor {
        val file = get(uri).copy.descriptor
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply {
            addRow(columns.map { column ->
                when (column) {
                    OpenableColumns.DISPLAY_NAME -> file.name
                    OpenableColumns.SIZE -> file.sealed.size
                    else -> null
                }
            })
        }
    }

    override fun getType(uri: Uri): String = get(uri).copy.descriptor.mime
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    private fun get(uri: Uri): Entry {
        val id = uri.pathSegments.singleOrNull() ?: throw FileNotFoundException()
        val entry = active[id]?.takeIf { SystemClock.elapsedRealtime() < it.expires }
        return entry ?: throw FileNotFoundException()
    }

    private data class Entry(val copy: SpaceFileLocal.Copy, val expires: Long, val ephemeral: Boolean)

    companion object {
        private const val OPEN_MILLIS = 15 * 60_000L
        private val active = ConcurrentHashMap<String, Entry>()
        private val worker by lazy { Handler(HandlerThread("Space file viewer").also { it.start() }.looper) }

        internal fun share(context: Context, copy: SpaceFileLocal.Copy, ephemeral: Boolean): Uri {
            val token = UUID.randomUUID().toString()
            val entry = Entry(copy, SystemClock.elapsedRealtime() + OPEN_MILLIS, ephemeral)
            active[token] = entry
            worker.postDelayed({
                if (active.remove(token, entry) && entry.ephemeral) entry.copy.path.delete()
            }, OPEN_MILLIS)
            return Uri.Builder().scheme("content").authority("${context.packageName}.space-files").appendPath(token).build()
        }
    }
}
