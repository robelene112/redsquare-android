package nl.davidengelen.redsquare.feed

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import nl.davidengelen.redsquare.media.GiphyAttribution

/** Retains unfinished edits during screen recreation, without putting text in a saved-state bundle. */
internal class PostComposerMemory : ViewModel() {
    val imagePreparation = SelectedImagePreparation().also { addCloseable("selected-images", it) }
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

}

/** The Activity receives the result even while its owning composer is being restored. */
internal class PostImagePickerState(private val state: SavedStateHandle) : ViewModel() {
    data class Selection(val session: String, val uris: List<Uri>)

    private val sessionKey = "post_image_picker_session"
    val pickingFor = state.getStateFlow<String?>(sessionKey, null)
    var selection by mutableStateOf<Selection?>(null)
        private set

    fun launch(session: String, open: () -> Unit) {
        if (pickingFor.value != null) return
        selection = null
        state[sessionKey] = session
        try { open() }
        catch (failure: Exception) {
            state.set<String>(sessionKey, null)
            throw failure
        }
    }

    fun receive(uris: List<Uri>) {
        val session = pickingFor.value ?: return
        state.set<String>(sessionKey, null)
        selection = uris.takeIf { it.isNotEmpty() }?.let { Selection(session, it.toList()) }
    }

    fun consume(current: Selection): Boolean {
        if (selection !== current) return false
        selection = null
        return true
    }
}
