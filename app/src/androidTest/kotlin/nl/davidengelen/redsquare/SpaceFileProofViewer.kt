package nl.davidengelen.redsquare

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.LinearLayout
import android.widget.TextView
import java.security.MessageDigest

/** Isolated consumer of the actual Android viewing grant. Only packaged in the test APK. */
class SpaceFileProofViewer : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val views = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 24)
        }
        val title = TextView(this).apply { text = "Reading shared file" }
        val outcome = TextView(this).apply { setTextIsSelectable(true) }
        views.addView(title)
        views.addView(outcome)
        setContentView(views)
        Thread({
            val digest = runCatching {
                val uri = requireNotNull(intent.data)
                val handle = requireNotNull(contentResolver.openFileDescriptor(uri, "r"))
                ParcelFileDescriptor.AutoCloseInputStream(handle).use { input ->
                    input.channel.position(12)
                    val jumped = input.read()
                    require(jumped >= 0)
                    input.channel.position(0)
                    val hasher = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(8192)
                    var first = true
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (first) {
                            require(count > 12 && (buffer[12].toInt() and 255) == jumped)
                            first = false
                        }
                        hasher.update(buffer, 0, count)
                    }
                    hasher.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
                }
            }
            runOnUiThread {
                title.text = if (digest.isSuccess) "File bytes verified" else "Could not read shared file"
                outcome.text = digest.getOrElse { it.javaClass.simpleName }
            }
        }, "Owned file viewer").start()
    }
}
