package nl.davidengelen.redsquare.vault

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import nl.davidengelen.redsquare.R
import java.util.UUID

internal data class SharedVaultFile(val uri: Uri, val token: String = UUID.randomUUID().toString())

/** Activity-independent delivery, with URI grants still owned by Android. Never accepts node settings or publication authority. */
internal class VaultIncomingFiles : ViewModel() {
    private val mutable = MutableStateFlow<SharedVaultFile?>(null)
    val pending = mutable.asStateFlow()
    fun receive(context: Context, intent: Intent): Boolean {
        if (intent.action != Intent.ACTION_SEND) return false
        val uri = try {
            require((intent.clipData?.itemCount ?: 0) <= 1)
            val stream = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else { @Suppress("DEPRECATION") intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) }
            (stream ?: intent.clipData?.getItemAt(0)?.uri)?.takeIf { it.scheme == "content" }
        } catch (_: Exception) { null }
        if (uri == null) Toast.makeText(context, R.string.vault_share_unavailable, Toast.LENGTH_LONG).show()
        else mutable.value = SharedVaultFile(uri)
        return true
    }
    fun consumed(file: SharedVaultFile) { mutable.compareAndSet(file, null) }
}
