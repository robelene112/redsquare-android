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
import com.google.gson.JsonObject
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Uses Android's real window recomposer; a Compose rule would replace its motion scale. */
class AppFrameMotionPreviewTest {
    @Test fun normalSystemMotionKeepsDismissalAndRestoredNavigationUsable() {
        verifyDismissal(0, "zero-before-normal")
        verifyDismissal(1)
    }
    @Test fun disabledSystemMotionKeepsDismissalAndRestoredNavigationUsable() = verifyDismissal(0)

    private fun verifyDismissal(scale: Int, name: String = "motion-$scale") {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
        val device = UiDevice.getInstance(instrumentation)
        val originals = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
            .associateWith { device.executeShellCommand("settings get global $it").trim() }
        check(originals.values.all { value -> value == "null" || value.toFloatOrNull()?.let { it.isFinite() && it >= 0 } == true })
        val folder = File(context.cacheDir, "app-frame-motion-evidence").apply { mkdirs() }
        var primary: Throwable? = null
        try {
            originals.keys.forEach { setting ->
                device.executeShellCommand("settings put global $setting $scale")
                check(device.executeShellCommand("settings get global $setting").trim().toFloat() == scale.toFloat())
            }
            withAppFrameFixture(name, "app-frame-motion-evidence") { activity ->
                check(device.wait(Until.hasObject(By.text("Native Red Square reference post")), 15_000))
                check(device.wait(Until.hasObject(By.text("Later")), 10_000))
                val before = device.findObject(By.text("Native Red Square reference post")).visibleBounds
                val later = device.findObject(By.text("Later")).visibleBounds
                captureNativeScreen(File(folder, "$name-before.png"))
                val movie = File(folder, "$name.mp4")
                val recording = recordNativeTransition(movie) { check(device.click(later.centerX(), later.centerY())) }
                check(device.wait(Until.gone(By.text("Back up your wallet")), 10_000))
                val after = device.findObject(By.text("Native Red Square reference post")).visibleBounds
                captureNativeScreen(File(folder, "$name-after.png"))
                assertTrue("Dismissing backup guidance must recover reading space", before.top > after.top + 24)
                val observation = retainNativeMotionFrames(movie)
                observation.add("recording", recording)
                // Read only after the original recording, without creating or replacing a window context.
                activity.onActivity { screen ->
                    val host = requireNotNull(screen.findViewById<ViewGroup>(android.R.id.content).getChildAt(0))
                    val parent = requireNotNull(host.findViewTreeCompositionContext())
                    val windowScale = parent.effectCoroutineContext[MotionDurationScale]
                    observation.add("windowMotion", JsonObject().apply {
                        addProperty("contextClass", parent.javaClass.name)
                        addProperty("isRecomposer", parent is Recomposer)
                        addProperty("scaleClass", windowScale?.javaClass?.name)
                        addProperty("scaleFactor", windowScale?.scaleFactor)
                        addProperty("settingsAnimatorScale", Settings.Global.getFloat(screen.contentResolver,
                            Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
                    })
                }
                val observedScales = JsonObject()
                originals.keys.forEach { setting ->
                    val observed = device.executeShellCommand("settings get global $setting").trim()
                    check(observed.toFloat() == scale.toFloat()); observedScales.addProperty(setting, observed)
                }
                observation.add("observedSystemScales", observedScales)
                observation.add("originalSystemScales", JsonObject().apply { originals.forEach { (setting, value) -> addProperty(setting, value) } })
                observation.addProperty("requestedSystemScale", scale)
                observation.addProperty("beforePostBounds", before.toShortString())
                observation.addProperty("afterPostBounds", after.toShortString())
                observation.addProperty("acceptance", "Native endpoints/persistence only. Motion requires inspection of the original movie and actual decoded frames.")
                File(folder, "$name.json").writeText(observation.toString())
                val observedWindow = observation.getAsJsonObject("windowMotion")
                assertEquals("Window animation scale must match the current system preference",
                    scale.toFloat(), observedWindow.get("scaleFactor").asFloat, 0f)
                activity.recreate()
                check(device.wait(Until.hasObject(By.text("Native Red Square reference post")), 15_000))
                assertFalse(device.hasObject(By.text("Back up your wallet")))
                device.findObject(By.text("Native Red Square reference post")).click()
                check(device.wait(Until.hasObject(By.text("Conversation")), 10_000))
                device.pressBack()
                check(device.wait(Until.hasObject(By.text("Native Red Square reference post")), 10_000))
                assertFalse(device.hasObject(By.text("Back up your wallet")))
                captureNativeScreen(File(folder, "$name-persisted-after-navigation.png"))
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, *originals.map { (setting, value) -> {
                device.executeShellCommand(if (value == "null") "settings delete global $setting" else "settings put global $setting $value")
                check(device.executeShellCommand("settings get global $setting").trim() == value)
            } }.toTypedArray())
        }
    }

}
