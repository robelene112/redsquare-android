package nl.davidengelen.redsquare.talk

import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import nl.davidengelen.redsquare.saito.TalkLink

/** One Android delivery identity, independent of its URL and the original launch configuration. */
internal class TalkLaunchRoutes(private val saved: SavedStateHandle) : ViewModel() {
    val pending = saved.getStateFlow<Bundle?>("talk.pending", null)

    fun initialize(uri: Uri?) {
        if (saved.get<Boolean>("talk.initialized") == true) return
        saved["talk.initialized"] = true
        enqueue(uri)
    }

    fun enqueue(uri: Uri?) {
        if (!accepts(uri)) return
        val generation = (saved.get<Long>("talk.generation") ?: 0L) + 1L
        check(generation > 0)
        saved["talk.generation"] = generation
        saved["talk.pending"] = Bundle().apply {
            putString("uri", uri.toString()); putLong("generation", generation)
        }
    }

    fun current(delivery: Bundle) = pending.value?.getLong("generation") == delivery.getLong("generation")

    fun uri(delivery: Bundle): Uri? = delivery.getString("uri")?.let(Uri::parse)?.takeIf { accepts(it) }

    fun consume(delivery: Bundle) {
        if (current(delivery)) saved["talk.pending"] = null
    }

    companion object {
        fun accepts(uri: Uri?): Boolean = uri != null &&
            (uri.scheme == "redsquare-talk" || TalkLink.parse(uri.toString()) != null)
    }
}
