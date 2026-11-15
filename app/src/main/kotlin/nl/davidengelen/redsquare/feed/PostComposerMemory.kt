package nl.davidengelen.redsquare.feed

import androidx.lifecycle.ViewModel
import nl.davidengelen.redsquare.media.GiphyAttribution

/** Retains unfinished edits during screen recreation, without putting text in a saved-state bundle. */
internal class PostComposerMemory : ViewModel() {
    val writes = kotlinx.coroutines.sync.Mutex()
    var unfinished: UnfinishedTextRecovery.EditingSession? = null
    var text: String? = null
    var images: List<String>? = null
    var gifAttributions: Map<String, GiphyAttribution>? = null
    var transactionId: String? = null

    fun clearSubmitted() {
        unfinished?.close()
        unfinished = null
        text = null
        images = null
        gifAttributions = null
        transactionId = null
    }

    override fun onCleared() { unfinished?.close() }
}
