package nl.davidengelen.redsquare

import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Configuration observations only; the normal Activity journey proves UI behavior. */
class SpacesViewportConfigurationTest {
    @Test fun observeOwnedConfiguration() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val arguments = InstrumentationRegistry.getArguments()
        assertEquals("nl.davidengelen.redsquare.dev", context.packageName)
        assertTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        val stage = requireNotNull(arguments.getString("spacesViewportStage"))
        require(stage in setOf("baseline", "prepared", "restored"))
        val expectedFont = arguments.getString("spacesExpectedFont")?.toFloat()
        val expectedWidth = arguments.getString("spacesExpectedWidth")?.toInt()
        val expectedDensity = arguments.getString("spacesExpectedDensity")?.toInt()
        fun matches(): Boolean = context.resources.configuration.let {
            (expectedFont == null || kotlin.math.abs(it.fontScale - expectedFont) < .01f) &&
                (expectedWidth == null || it.screenWidthDp == expectedWidth) &&
                (expectedDensity == null || it.densityDpi == expectedDensity)
        }
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (!matches() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        val configuration = context.resources.configuration
        val device = UiDevice.getInstance(instrumentation)
        val value = JsonObject().apply {
            addProperty("stage", stage); addProperty("pid", Process.myPid()); addProperty("sdk", Build.VERSION.SDK_INT)
            addProperty("fontScale", configuration.fontScale); addProperty("screenWidthDp", configuration.screenWidthDp)
            addProperty("densityDpi", configuration.densityDpi)
            addProperty("fontSetting", device.executeShellCommand("settings get system font_scale").trim())
        }
        val folder = File(context.cacheDir, "spaces-discovery-evidence").apply { mkdirs() }
        File(folder, "viewport-$stage.json").writeText(value.toString())
        assertTrue("Actual resource configuration must match the owned viewport", matches())
    }
}
