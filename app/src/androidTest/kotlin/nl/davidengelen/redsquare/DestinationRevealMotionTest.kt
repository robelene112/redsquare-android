package nl.davidengelen.redsquare

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Real destination pixels at actual system scale1/0, without a Compose test clock. */
class DestinationRevealMotionTest {
    @Test fun searchArrivesWithBriefMotionAndDisabledMotionRemainsImmediate() {
        for (scale in listOf(1, 0)) withNativeModuleMotionScale(scale) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val device = UiDevice.getInstance(instrumentation)
            val folder = File(context.cacheDir, "destination-reveal-evidence").apply { mkdirs() }
            withAppFrameFixture("search-$scale", "destination-reveal-evidence") { activity ->
                check(device.wait(Until.hasObject(By.text("Native Red Square reference post")), 15_000))
                requireNotNull(device.wait(Until.findObject(By.text("Later")), 5_000)).click()
                val action = requireNotNull(device.findObject(By.text("Search"))).visibleBounds
                val file = File(folder, "scale-$scale-search.mp4")
                var titleDeadline = 0L
                val recording = recordNativeTransition(file, recordingSeconds = 4) {
                    titleDeadline = SystemClock.elapsedRealtime() + 5_000
                    check(device.click(action.centerX(), action.centerY()))
                }
                // Native hierarchy snapshots can block the main thread. Observe readiness
                // after recording, within the original budget measured from the actual tap.
                var title = searchTitle(device.displayHeight)
                while (title == null && SystemClock.elapsedRealtime() < titleDeadline) {
                    SystemClock.sleep(16)
                    title = searchTitle(device.displayHeight)
                }
                val bounds = requireNotNull(title)
                val template = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                try {
                    File(folder, "scale-$scale-search-template.png").outputStream().use {
                        check(template.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                    }
                    val background = template.getPixel(0, bounds.centerY())
                    data class Ink(val x: Int, val y: Int, val r: Double, val g: Double, val b: Double, val energy: Double)
                    val ink = mutableListOf<Ink>()
                    for (y in bounds.top until bounds.bottom) for (x in bounds.left until bounds.right) {
                        val value = template.getPixel(x, y)
                        val r = (Color.red(value) - Color.red(background)).toDouble()
                        val g = (Color.green(value) - Color.green(background)).toDouble()
                        val b = (Color.blue(value) - Color.blue(background)).toDouble()
                        if (maxOf(kotlin.math.abs(r), kotlin.math.abs(g), kotlin.math.abs(b)) > 90)
                            ink.add(Ink(x, y, r, g, b, r * r + g * g + b * b))
                    }
                    assertTrue("Observe the actual destination title's contrasting ink", ink.size > 30)
                    val frames = retainNativeMotionFrames(file)
                    val observations = JsonArray()
                    val intermediate = mutableListOf<Double>()
                    for (entry in frames.getAsJsonArray("decodedOriginalFrames")) {
                        val frame = entry.asJsonObject
                        val bitmap = requireNotNull(BitmapFactory.decodeFile(File(folder, frame.get("file").asString).path))
                        try {
                            val base = bitmap.getPixel(0, bounds.centerY())
                            val opacity = ink.map { point ->
                                val value = bitmap.getPixel(point.x, point.y)
                                ((Color.red(value) - Color.red(base)) * point.r +
                                    (Color.green(value) - Color.green(base)) * point.g +
                                    (Color.blue(value) - Color.blue(base)) * point.b) / point.energy
                            }
                            val mean = opacity.average()
                            val variance = opacity.sumOf { (it - mean) * (it - mean) } / opacity.size
                            if (mean in 0.2..0.95 && variance < 0.03) intermediate.add(mean)
                            observations.add(JsonObject().apply {
                                addProperty("file", frame.get("file").asString)
                                addProperty("presentationTimeUs", frame.get("presentationTimeUs").asLong)
                                addProperty("titleInkMean", mean); addProperty("titleInkVariance", variance)
                            })
                        } finally { bitmap.recycle() }
                    }
                    frames.add("recording", recording); frames.add("titleInkObservations", observations)
                    frames.addProperty("titleBounds", bounds.toShortString())
                    frames.addProperty("intermediateFrames", intermediate.size)
                    File(folder, "scale-$scale-search.json").writeText(frames.toString())
                    observeModuleWindowMotionScale(activity, scale, folder)
                    if (scale == 1) assertTrue("Normal motion reveals the actual destination across distinct intermediate display frames",
                        intermediate.size >= 2 && intermediate.max() - intermediate.min() >= 0.04)
                    else assertTrue("Zero motion does not retain a destination fade", intermediate.size < 2)
                } finally { template.recycle() }
            }
        }
    }

    private fun searchTitle(height: Int): Rect? = nativeWindowNodes().mapNotNull { node ->
        if (node.text?.toString() != "Search" || !node.isVisibleToUser) return@mapNotNull null
        Rect().apply { node.getBoundsInScreen(this) }.takeIf { !it.isEmpty && it.bottom < height / 2 }
    }.minByOrNull { it.top }
}
