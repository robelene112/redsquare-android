package nl.davidengelen.redsquare

import android.content.Context
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.gson.JsonObject
import java.io.File

/** Actual disposable-emulator rendering configuration, with independent restoration. */
internal class EmulatorFontScale(private val context: Context, private val folder: File, private val label: String) {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val originalScale = context.resources.configuration.fontScale
    private val originalSetting = device.executeShellCommand("settings get system font_scale").trim()

    init {
        check(context.packageName == "nl.davidengelen.redsquare.dev" && android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        check(originalSetting == "null" || (originalSetting.matches(Regex("[0-9]+([.][0-9]+)?")) &&
            originalSetting.toFloatOrNull()?.let { it.isFinite() && it > 0f } == true))
    }

    fun apply(scale: Float) {
        check(scale.isFinite() && scale > 0f)
        device.executeShellCommand("settings put system font_scale $scale")
        await(scale)
        record("font-scale", scale)
    }

    fun restore() {
        device.executeShellCommand(if (originalSetting == "null") "settings delete system font_scale"
            else "settings put system font_scale $originalSetting")
        await(originalScale)
        val restored = device.executeShellCommand("settings get system font_scale").trim()
        record("font-restored", originalScale)
        // API29 can persist the implicit default while restoring configuration.
        check(restored == originalSetting || (android.os.Build.VERSION.SDK_INT == 29 &&
            originalSetting == "null" && originalScale == 1f && restored == "1.0"))
    }

    private fun await(expected: Float) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (kotlin.math.abs(context.resources.configuration.fontScale - expected) >= 0.01f &&
            SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        check(kotlin.math.abs(context.resources.configuration.fontScale - expected) < 0.01f)
    }

    private fun record(stage: String, expected: Float) {
        folder.mkdirs()
        File(folder, "$label-$stage.json").writeText(JsonObject().apply {
            addProperty("expected", expected); addProperty("observed", context.resources.configuration.fontScale)
            addProperty("originalScale", originalScale); addProperty("originalSetting", originalSetting)
            addProperty("currentSetting", device.executeShellCommand("settings get system font_scale").trim())
        }.toString())
    }
}
