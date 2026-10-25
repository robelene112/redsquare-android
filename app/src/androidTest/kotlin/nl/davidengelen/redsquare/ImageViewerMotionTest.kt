package nl.davidengelen.redsquare

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.provider.Settings
import android.view.ViewGroup
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.findViewTreeCompositionContext
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.google.gson.JsonParser
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Native rendered transition and real pinch input under normal and zero system motion. */
class ImageViewerMotionTest {
    @Test fun imageChangesRespectSystemMotionAndPinchRemainsAvailable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
        val device = UiDevice.getInstance(instrumentation)
        val folder = File(context.cacheDir, "image-viewer-motion-evidence").apply { mkdirs() }
        val settings = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
            .associateWith { device.executeShellCommand("settings get global $it").trim() }
        check(settings.values.all { it == "null" || it.toFloatOrNull()?.let { value -> value.isFinite() && value >= 0 } == true })
        val appearance = context.getSharedPreferences("appearance", 0)
        val previousAppearance = appearance.getString("mode", null)
        val fixture = JsonParser.parseReader(instrumentation.context.assets.open("native-images-reference.json").reader()).asJsonObject
        var primary: Throwable? = null
        try {
            check(appearance.edit().putString("mode", "LIGHT").commit())
            for (scale in listOf(1, 0)) {
                settings.keys.forEach { device.executeShellCommand("settings put global $it $scale") }
                withAppFrameFixture("motion-$scale", "image-viewer-motion-evidence", "[${fixture.getAsJsonObject("ordinary")}]") { activity ->
                    check(device.wait(Until.hasObject(By.text("Later")), 15_000))
                    device.findObject(By.text("Later")).click()
                    val origin = requireNotNull(device.wait(Until.findObject(By.desc("Image 1 of 2")), 15_000))
                    origin.click()
                    val image = requireNotNull(device.wait(Until.findObject(By.desc("Full screen image 1 of 2")), 10_000))
                    val bounds = image.visibleBounds
                    if (scale == 1) {
                        check(device.findObject(UiSelector().description("Full screen image 1 of 2")).pinchOut(70, 20))
                        check(device.wait(Until.gone(By.text("100%")), 5_000))
                        captureNativeScreen(File(folder, "pinch-zoom.png"))
                        device.findObject(By.text("Move image")).click()
                        check(device.wait(Until.hasObject(By.text("View right side")), 5_000))
                        device.findObject(By.text("View right side")).click()
                        check(device.wait(Until.gone(By.text("View right side")), 5_000))
                        captureNativeScreen(File(folder, "pan-menu-result.png"))
                        device.findObject(By.text("Reset zoom")).click()
                        check(device.wait(Until.hasObject(By.text("100%")), 5_000))
                    }
                    val movie = File(folder, "image-change-$scale.mp4")
                    val recording = recordNativeTransition(movie) {
                        device.findObject(By.text("Next image")).click()
                        check(device.wait(Until.hasObject(By.text("Image 2 of 2")), 5_000))
                    }
                    captureNativeScreen(File(folder, "image-change-$scale-final.png"), "Image 2 of 2")
                    val observed = retainNativeMotionFrames(movie)
                    observed.add("recording", recording)
                    var intermediate = 0
                    var blue = 0
                    for (entry in observed.getAsJsonArray("decodedOriginalFrames")) {
                        val file = File(folder, entry.asJsonObject.get("file").asString)
                        val bitmap = requireNotNull(BitmapFactory.decodeFile(file.path))
                        try {
                            val color = bitmap.getPixel(bounds.centerX(), bounds.centerY())
                            if (Color.blue(color) > 205 && Color.green(color) in 80..220) intermediate++
                            if (kotlin.math.abs(Color.red(color) - 20) < 15 &&
                                kotlin.math.abs(Color.green(color) - 60) < 15 && kotlin.math.abs(Color.blue(color) - 220) < 15) blue++
                        } finally { bitmap.recycle() }
                    }
                    observed.addProperty("intermediateBlueFadeFrames", intermediate)
                    observed.addProperty("finalBlueFrames", blue)
                    observed.addProperty("scale", scale)
                    activity.onActivity { screen ->
                        val host = requireNotNull(screen.findViewById<ViewGroup>(android.R.id.content).getChildAt(0))
                        val parent = requireNotNull(host.findViewTreeCompositionContext())
                        val actual = parent.effectCoroutineContext[MotionDurationScale]?.scaleFactor
                        observed.addProperty("actualMotionScale", actual)
                        assertEquals(scale.toFloat(), actual)
                        assertEquals(scale.toFloat(), Settings.Global.getFloat(screen.contentResolver,
                            Settings.Global.ANIMATOR_DURATION_SCALE, 1f), 0f)
                    }
                    File(folder, "image-change-$scale.json").writeText(observed.toString())
                    assertTrue("Native recording contains the independently known blue image", blue > 0)
                    if (scale == 1) assertTrue("Normal motion contains a visible fade", intermediate > 0)
                    else assertEquals("Zero motion has no intermediate fade", 0, intermediate)
                    device.findObject(By.text("Close image")).click()
                    check(device.wait(Until.gone(By.text("Close image")), 5_000))
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, {
                check(appearance.edit().apply { if (previousAppearance == null) remove("mode") else putString("mode", previousAppearance) }.commit())
            }, *settings.map { (key, value) -> {
                device.executeShellCommand(if (value == "null") "settings delete global $key" else "settings put global $key $value")
                assertEquals(value, device.executeShellCommand("settings get global $key").trim())
            } }.toTypedArray())
        }
    }
}
