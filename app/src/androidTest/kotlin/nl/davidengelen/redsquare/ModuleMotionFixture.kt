package nl.davidengelen.redsquare

import android.os.Build
import android.view.ViewGroup
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.findViewTreeCompositionContext
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.gson.JsonObject
import java.io.File
import org.junit.Assert.*

/** Actual system preferences, guarded disposable runtime and independent restoration. */
internal fun withNativeModuleMotionScale(scale: Int, block: () -> Unit) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    check(instrumentation.targetContext.packageName == "nl.davidengelen.redsquare.dev" &&
        Build.HARDWARE in listOf("ranchu", "goldfish"))
    val device = UiDevice.getInstance(instrumentation)
    val originals = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
        .associateWith { device.executeShellCommand("settings get global $it").trim() }
    check(originals.values.all { it == "null" || it.toFloatOrNull()?.let { value -> value.isFinite() && value >= 0 } == true })
    var primary: Throwable? = null
    try {
        originals.keys.forEach { device.executeShellCommand("settings put global $it $scale") }
        block()
    } catch (failure: Throwable) { primary = failure; throw failure }
    finally {
        cleanupAfterTest(primary, *originals.map { (key, value) -> {
            device.executeShellCommand(if (value == "null") "settings delete global $key" else "settings put global $key $value")
            check(device.executeShellCommand("settings get global $key").trim() == value)
        } }.toTypedArray())
    }
}

internal fun observeModuleWindowMotionScale(activity: ActivityScenario<MainActivity>, scale: Int, folder: File) {
    activity.onActivity { screen ->
        val host = requireNotNull(screen.findViewById<ViewGroup>(android.R.id.content).getChildAt(0))
        val parent = requireNotNull(host.findViewTreeCompositionContext())
        val observedScale = parent.effectCoroutineContext[MotionDurationScale]?.scaleFactor
        assertEquals("The real window must follow this system motion setting", scale.toFloat(), observedScale)
        File(folder, "scale-$scale-window.json").writeText(JsonObject().apply {
            addProperty("isRecomposer", parent is Recomposer)
            addProperty("windowMotionScale", observedScale)
            addProperty("systemMotionScale", android.provider.Settings.Global.getFloat(screen.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
        }.toString())
    }
}
