package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.gson.JsonObject
import java.io.File

/** Real fixed-width/theme/font journeys, restoring platform state after the Activity closes. */
internal fun ComposeTestRule.withPostViewports(activity: ActivityScenario<MainActivity>, folder: File,
    prefix: String, cell: (String, String) -> Unit) {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val font = EmulatorFontScale(context, folder, prefix)
    val originalSize = device.executeShellCommand("wm size")
    val originalDensity = device.executeShellCommand("wm density")
    check(Regex("Physical size: [0-9]+x[0-9]+").containsMatchIn(originalSize))
    check(Regex("Physical density: [0-9]+").containsMatchIn(originalDensity))
    val sizeOverride = Regex("Override size: ([0-9]+x[0-9]+)").find(originalSize)?.groupValues?.get(1)
    val densityOverride = Regex("Override density: ([0-9]+)").find(originalDensity)?.groupValues?.get(1)
    val appearance = context.getSharedPreferences("appearance", 0)
    val originalAppearance = appearance.getString("mode", null)
    var primary: Throwable? = null
    try {
        device.executeShellCommand("wm density 160")
        device.executeShellCommand("wm size 320x640")
        for (scale in listOf(1f, 1.3f, 2f)) {
            font.apply(scale)
            activity.recreate()
            waitUntil(15_000) { device.displayWidth == 320 && onNode(hasText("Home") and isSelected()).isDisplayed() }
            for (theme in listOf("Light", "Dark")) {
                val label = "$prefix-320-font${(scale * 100).toInt()}-${theme.lowercase()}"
                withFailureCapture({ captureNativeScreen(File(folder, "$label-failure.png")) }) {
                    onNode(hasText("Settings") and hasClickAction()).performClick()
                    onNodeWithText(theme).performScrollTo().performClick()
                    waitUntil(10_000) { onNode(hasText(theme) and isSelected()).isDisplayed() }
                    onNodeWithText("Done").performClick()
                    waitUntil(15_000) {
                        var focused = false
                        activity.onActivity { focused = it.window.decorView.hasWindowFocus() }
                        focused && onNode(hasText("Home") and isSelected()).isDisplayed()
                    }
                    check(context.resources.configuration.screenWidthDp == 320)
                    File(folder, "$label-configuration.json").writeText(JsonObject().apply {
                        addProperty("widthPixels", device.displayWidth)
                        addProperty("widthDp", context.resources.configuration.screenWidthDp)
                        addProperty("fontScale", context.resources.configuration.fontScale)
                        addProperty("selectedAppearance", theme)
                    }.toString())
                    cell(label, theme)
                }
            }
        }
    } catch (failure: Throwable) { primary = failure; throw failure }
    finally {
        cleanupAfterTest(primary, { activity.close() }, { font.restore() },
            { device.executeShellCommand("wm size ${sizeOverride ?: "reset"}")
                check(device.executeShellCommand("wm size").trim() == originalSize.trim()) },
            { device.executeShellCommand("wm density ${densityOverride ?: "reset"}")
                check(device.executeShellCommand("wm density").trim() == originalDensity.trim()) },
            { check(appearance.edit().apply { if (originalAppearance == null) remove("mode")
                else putString("mode", originalAppearance) }.commit()) })
    }
}
