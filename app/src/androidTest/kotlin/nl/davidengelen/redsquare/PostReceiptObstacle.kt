package nl.davidengelen.redsquare

import android.content.Context
import android.os.Build
import nl.davidengelen.redsquare.saito.SaitoWallet
import java.io.Closeable
import java.io.File

/** Real receipt-write failure at the existing storage seam, never an invented transport timeout. */
internal class PostReceiptObstacle private constructor(private val directory: File) : Closeable {
    override fun close() { remove(directory) }

    companion object {
        private const val MARKER = "owned-test-obstacle"
        private const val CONTENT = "Prevent receipt replacement"
        private fun path(context: Context, owner: String, signature: String): File {
            check(context.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
            require(SaitoWallet.isPublicKey(owner) && signature.matches(Regex("[0-9a-f]{128}")))
            return File(context.noBackupFilesDir, "postdraft.owner.$owner.submitted.$signature.v1")
        }
        fun create(context: Context, owner: String, signature: String): PostReceiptObstacle {
            val directory = path(context, owner, signature)
            check(!directory.exists() && directory.mkdir())
            try {
                File(directory, MARKER).writeText(CONTENT)
                return PostReceiptObstacle(directory)
            } catch (failure: Throwable) {
                runCatching { File(directory, MARKER).delete(); check(directory.delete()) }
                    .exceptionOrNull()?.let(failure::addSuppressed)
                throw failure
            }
        }
        fun removeOwned(context: Context, owner: String, signature: String) = remove(path(context, owner, signature))
        private fun remove(directory: File) {
            check(directory.isDirectory && directory.list()?.toSet() == setOf(MARKER))
            val marker = File(directory, MARKER)
            check(marker.readText() == CONTENT)
            check(marker.delete() && directory.delete())
        }
    }
}
