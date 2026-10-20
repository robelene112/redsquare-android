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

/** Real window animation scale and font rendering, without a replacement Compose test clock. */
class StatusMotionPreviewTest {
    @Test fun normalMotionAndLargeDarkTextKeepStatusRecoveryUsable() = verifyRecovery(1, 2f, "DARK")
    @Test fun zeroMotionAndLargerLightTextKeepStatusRecoveryUsable() = verifyRecovery(0, 1.3f, "LIGHT")

    private fun verifyRecovery(scale: Int, textScale: Float, theme: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
        val device = UiDevice.getInstance(instrumentation)
        val folder = File(context.cacheDir, "status-motion-evidence").apply { mkdirs() }
        val name = "status-motion-$scale-font${(textScale * 100).toInt()}-${theme.lowercase()}"
        val originals = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
            .associateWith { device.executeShellCommand("settings get global $it").trim() }
        check(originals.values.all { value -> value == "null" || value.toFloatOrNull()?.let { it.isFinite() && it >= 0 } == true })
        val appearance = context.getSharedPreferences("appearance", 0)
        val originalAppearance = appearance.getString("mode", null)
        val font = EmulatorFontScale(context, folder, name)
        lateinit var peer: ControlledFeedPeer
        var primary: Throwable? = null
        try {
            originals.keys.forEach { setting -> device.executeShellCommand("settings put global $setting $scale") }
            font.apply(textScale)
            check(appearance.edit().putString("mode", theme).commit())
            withAppFrameFixture(name, "status-motion-evidence", configurePeer = { peer = it }) { activity ->
                check(device.wait(Until.hasObject(By.text("Later")), 15_000))
                device.findObject(By.text("Later")).click()
                check(device.wait(Until.hasObject(By.text("Native Red Square reference post")), 15_000))
                peer.rejectNewConnections = true
                device.findObject(By.text("Refresh")).click()
                check(device.wait(Until.hasObject(By.text("Node unavailable")), 20_000))
                captureNativeScreen(File(folder, "$name-outage.png"))
                device.findObject(By.desc("Details: Node unavailable")).click()
                check(device.wait(Until.hasObject(By.text("Posts could not be refreshed. Try again.")), 5_000))
                captureNativeScreen(File(folder, "$name-details.png"))
                device.findObject(By.text("Done")).click()
                var retryNode = device.wait(Until.findObject(By.text("Retry").enabled(true)), 5_000)
                while (retryNode != null && !retryNode.isClickable) retryNode = retryNode.parent
                val retry = requireNotNull(retryNode)
                assertTrue("Retry must be an enabled native action", retry.isEnabled)
                val bounds = retry.visibleBounds
                val minimum = 48 * context.resources.displayMetrics.density - 1
                assertTrue("Retry stays fully reachable with larger text", bounds.width() >= minimum && bounds.height() >= minimum &&
                    bounds.top >= 0 && bounds.bottom <= device.displayHeight && bounds.left >= 0 && bounds.right <= device.displayWidth)
                val movie = File(folder, "$name.mp4")
                val recording = recordNativeTransition(movie, recordingSeconds = 5) {
                    peer.rejectNewConnections = false
                    retry.click()
                    check(device.wait(Until.hasObject(By.text("Back online")), 10_000))
                    captureNativeScreen(File(folder, "$name-recovered.png"))
                }
                check(device.wait(Until.gone(By.text("Back online")), 10_000))
                check(device.hasObject(By.text("Native Red Square reference post")))
                captureNativeScreen(File(folder, "$name-after-removal.png"))
                val observed = retainNativeMotionFrames(movie)
                observed.add("recording", recording)
                activity.onActivity { screen ->
                    val host = requireNotNull(screen.findViewById<ViewGroup>(android.R.id.content).getChildAt(0))
                    val parent = requireNotNull(host.findViewTreeCompositionContext())
                    observed.add("windowMotion", JsonObject().apply {
                        addProperty("isRecomposer", parent is Recomposer)
                        addProperty("scaleFactor", parent.effectCoroutineContext[MotionDurationScale]?.scaleFactor)
                        addProperty("setting", Settings.Global.getFloat(screen.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
                    })
                }
                observed.addProperty("fontScale", context.resources.configuration.fontScale)
                observed.addProperty("widthDp", context.resources.configuration.screenWidthDp)
                observed.addProperty("theme", theme)
                observed.addProperty("retryBounds", bounds.toShortString())
                observed.addProperty("minimumTargetPixels", minimum)
                File(folder, "$name.json").writeText(observed.toString())
                assertEquals(scale.toFloat(), observed.getAsJsonObject("windowMotion").get("scaleFactor").asFloat, 0f)
                originals.keys.forEach { setting -> assertEquals(scale.toFloat(),
                    device.executeShellCommand("settings get global $setting").trim().toFloat(), 0f) }
                assertEquals(textScale, context.resources.configuration.fontScale, 0.01f)
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            val restorations = originals.map { (setting, value) -> {
                device.executeShellCommand(if (value == "null") "settings delete global $setting" else "settings put global $setting $value")
                check(device.executeShellCommand("settings get global $setting").trim() == value)
            } }
            cleanupAfterTest(primary, { font.restore() }, {
                check(appearance.edit().apply { if (originalAppearance == null) remove("mode") else putString("mode", originalAppearance) }.commit())
                check(appearance.getString("mode", null) == originalAppearance)
            }, *restorations.toTypedArray())
        }
    }
}
