package nl.davidengelen.redsquare

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Public UI only. Evidence failure must not hide the original assertion. */
internal fun captureFeedEvidence(path: String) {
    try {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            try {
                val file = File(instrumentation.targetContext.cacheDir, path)
                file.parentFile?.mkdirs()
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { bitmap.recycle() }
        }
    } catch (_: Exception) { }
}
