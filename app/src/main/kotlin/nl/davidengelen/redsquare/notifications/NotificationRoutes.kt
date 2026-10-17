package nl.davidengelen.redsquare.notifications

import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import nl.davidengelen.redsquare.chat.ChatAlertReference
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet

/** Public reference state only. Saved state handles recreation, not atomic process-death delivery. */
internal class NotificationRoutes(private val saved: SavedStateHandle) : ViewModel() {
    val pending = saved.getStateFlow<Bundle?>("social.pending", null)

    fun initialize(uri: Uri?) {
        if (saved.get<Boolean>("social.initialized") == true) return
        saved["social.initialized"] = true
        enqueue(uri)
    }

    fun enqueue(uri: Uri?) {
        if (parse(uri) == null && ChatAlertReference.parse(uri) == null) return
        val generation = (saved.get<Long>("social.generation") ?: 0L) + 1L
        check(generation > 0)
        saved["social.generation"] = generation
        saved["social.pending"] = Bundle().apply {
            putString("uri", uri.toString()); putLong("generation", generation)
        }
    }

    fun current(delivery: Bundle) = pending.value?.getLong("generation") == delivery.getLong("generation")

    fun consume(delivery: Bundle) {
        if (current(delivery)) saved["social.pending"] = null
    }

    companion object {
        // Preserve the existing SavedStateHandle owner/key while the router gains Chat destinations.
        const val VIEW_MODEL_KEY = "androidx.lifecycle.ViewModelProvider.DefaultKey:nl.davidengelen.redsquare.notifications.SocialPushRoutes"

        fun chatReference(delivery: Bundle): ChatAlertReference? = ChatAlertReference.parse(delivery.getString("uri")?.let(Uri::parse))

        fun socialReference(delivery: Bundle): SocialPushReference? = parse(delivery.getString("uri")?.let(Uri::parse))

        private fun parse(uri: Uri?): SocialPushReference? = try {
            requireNotNull(uri)
            require(uri.toString().toByteArray(Charsets.UTF_8).size <= 1024)
            require(uri.scheme == "redsquare" && uri.authority == "social" && uri.query == null && uri.fragment == null)
            val parts = uri.pathSegments
            require(parts.size == 3)
            val backend = SaitoBackend.entries.single { it.id == parts[0] }
            require(SaitoWallet.isPublicKey(parts[1]) && parts[2].matches(Regex("[0-9a-f]{128}")))
            require(uri.toString() == Uri.Builder().scheme("redsquare").authority("social")
                .appendPath(parts[0]).appendPath(parts[1]).appendPath(parts[2]).build().toString())
            SocialPushReference(parts[1], backend, parts[2])
        } catch (_: Exception) { null }
    }
}
