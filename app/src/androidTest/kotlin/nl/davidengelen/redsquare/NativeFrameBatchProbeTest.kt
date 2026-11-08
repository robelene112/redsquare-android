package nl.davidengelen.redsquare

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Test

/** External diagnostic branch only; uses the same Android process as the real evidence helper. */
class NativeFrameBatchProbeTest {
    @Test fun compareEveryOriginalFrameInBoundedBatches() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
        val folder = File(context.cacheDir, "frame-batch-probe-evidence").apply { mkdirs() }
        val paths = listOf("scale-0-article-reading.mp4", "scale-1-module.mp4").map { name ->
            File(folder, name).also { file ->
                instrumentation.context.assets.open("frame-batch-probe/$name").use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
            }.absolutePath
        }.toTypedArray()
        File(folder, "result.json").writeText(NativeFrameBatchProbe.compare(paths).toString())
    }
}
