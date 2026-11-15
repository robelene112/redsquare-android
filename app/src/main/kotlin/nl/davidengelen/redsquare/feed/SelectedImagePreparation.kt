package nl.davidengelen.redsquare.feed

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.*
import kotlinx.coroutines.*

/** One selected batch owns its provider read across Activity replacement.
 * Only the owning wallet/node draft ViewModel retains this resource. Results stay
 * private in memory until the existing encrypted draft commits them.
 */
internal class SelectedImagePreparation : AutoCloseable {
    class Selection internal constructor(val uris: List<Uri>, val cropRatio: Int?) {
        internal var result: Deferred<List<String>>? = null
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    var pending by mutableStateOf<Selection?>(null)
        private set
    var failed by mutableStateOf(false)
        private set
    val preparing: Boolean get() = pending != null && !failed

    fun select(uris: List<Uri>, cropRatio: Int? = null) {
        if (preparing || uris.isEmpty()) return
        pending?.result?.cancel()
        pending = Selection(uris.toList(), cropRatio)
        failed = false
    }

    suspend fun prepared(selection: Selection, resolver: ContentResolver, maxChars: Int): List<String> {
        check(pending === selection)
        val result = selection.result ?: scope.async {
            selection.uris.map { PostImagePreparation.normalize(resolver, it, maxChars, selection.cropRatio) }
        }.also { selection.result = it }
        // Cancelling a visible waiter does not cancel the selected operation.
        return result.await()
    }

    fun complete(selection: Selection) {
        if (pending === selection) { pending = null; failed = false }
    }

    fun fail(selection: Selection) {
        if (pending === selection) failed = true
    }

    override fun close() {
        scope.cancel()
        pending = null
    }
}
