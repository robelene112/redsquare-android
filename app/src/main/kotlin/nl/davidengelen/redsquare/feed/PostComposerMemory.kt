package nl.davidengelen.redsquare.feed

import androidx.lifecycle.ViewModel

/** Retains unfinished edits during screen recreation, without putting text in a saved-state bundle. */
internal class PostComposerMemory : ViewModel() {
    var text: String? = null
}
