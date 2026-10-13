package nl.davidengelen.redsquare.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import nl.davidengelen.redsquare.R

/** Explicit device clipboard action; the signed/cache original is never changed. */
internal fun copyPrivateMessage(context: Context, original: String): Boolean = try {
    val clip = ClipData.newPlainText(context.getString(R.string.chat_message), privateMessageBody(original)).apply {
        description.extras = PersistableBundle().apply {
            // Official compatible string form also works below API33.
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
    }
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, R.string.chat_copied, Toast.LENGTH_SHORT).show()
    true
} catch (_: Exception) { false }
