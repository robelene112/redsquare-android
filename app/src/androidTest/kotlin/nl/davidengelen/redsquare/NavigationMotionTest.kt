package nl.davidengelen.redsquare

import android.os.Build
import android.provider.Settings
import android.view.ViewGroup
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.findViewTreeCompositionContext
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Real window motion preference and original caption transitions, without a Compose test clock. */
class NavigationMotionTest {
    @Test fun adaptiveCaptionUsesNormalSystemMotion() = verifyMotion(1)
    @Test fun adaptiveCaptionRespectsDisabledSystemMotion() = verifyMotion(0)

    private fun verifyMotion(scale: Int) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val folder = File(context.cacheDir, "navigation-motion-evidence").apply { mkdirs() }
        val font = EmulatorFontScale(context, folder, "navigation-$scale")
        val originals = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
            .associateWith { device.executeShellCommand("settings get global $it").trim() }
        check(originals.values.all { it == "null" || it.toFloatOrNull()?.let { value -> value.isFinite() && value >= 0 } == true })
        var primary: Throwable? = null
        try {
            font.apply(2f)
            originals.keys.forEach { device.executeShellCommand("settings put global $it $scale") }
            withAppFrameFixture("navigation-motion-$scale", "navigation-motion-evidence") { activity ->
                check(device.wait(Until.hasObject(By.text("Later")), 15_000))
                device.findObject(By.text("Later")).click()
                check(device.wait(Until.hasObject(By.text("Native Red Square reference post")), 15_000))
                captureNativeScreen(File(folder, "navigation-$scale-before.png"))
                val movie = File(folder, "navigation-$scale.mp4")
                val recording = recordNativeTransition(movie) {
                    device.findObject(By.text("Search")).click()
                    check(device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 10_000))
                }
                captureNativeScreen(File(folder, "navigation-$scale-after.png"))
                val observed = retainNativeMotionFrames(movie)
                observed.add("recording", recording)
                observed.addProperty("requestedMotionScale", scale)
                observed.addProperty("fontScale", context.resources.configuration.fontScale)
                activity.onActivity { screen ->
                    val host = requireNotNull(screen.findViewById<ViewGroup>(android.R.id.content).getChildAt(0))
                    val parent = requireNotNull(host.findViewTreeCompositionContext())
                    val actual = parent.effectCoroutineContext[MotionDurationScale]?.scaleFactor
                    observed.addProperty("isRecomposer", parent is Recomposer)
                    observed.addProperty("actualMotionScale", actual)
                    assertTrue(parent is Recomposer)
                    assertEquals(scale.toFloat(), actual)
                    assertEquals(scale.toFloat(), Settings.Global.getFloat(screen.contentResolver,
                        Settings.Global.ANIMATOR_DURATION_SCALE, 1f), 0f)
                }
                File(folder, "navigation-$scale.json").writeText(observed.toString())
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, { font.restore() }, *originals.map { (key, value) -> {
                device.executeShellCommand(if (value == "null") "settings delete global $key" else "settings put global $key $value")
                assertEquals(value, device.executeShellCommand("settings get global $key").trim())
            } }.toTypedArray())
        }
    }
}
