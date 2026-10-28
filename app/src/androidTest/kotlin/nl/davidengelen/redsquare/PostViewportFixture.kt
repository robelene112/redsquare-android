package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.uiautomator.UiDevice
import com.google.gson.JsonObject
import java.io.File

/** Real fixed-width/theme/font journeys, restoring platform state after the Activity closes. */
internal fun ComposeTestRule.withPostViewports(activity: ActivityScenario<MainActivity>, folder: File,
    prefix: String, scales: List<Float> = listOf(1f, 1.3f, 2f), widthDp: Int = 320, cell: (String, String) -> Unit) {
    require(scales.isNotEmpty() && widthDp in listOf(320, 412))
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
    // Observe configuration-driven Activity replacement without changing the test's lifecycle or verdict.
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    fun recordLifecycle(event: String, changed: MainActivity? = null) {
        runCatching {
            folder.mkdirs()
            File(folder, "$prefix-activity-lifecycle.jsonl").appendText(JsonObject().apply {
                addProperty("event", event)
                addProperty("elapsedMillis", android.os.SystemClock.elapsedRealtime())
                if (changed == null) addProperty("scenarioState", activity.state.name)
                addProperty("widthDp", context.resources.configuration.screenWidthDp)
                addProperty("fontScale", context.resources.configuration.fontScale)
                changed?.let {
                    addProperty("activity", System.identityHashCode(it))
                    addProperty("finishing", it.isFinishing)
                    addProperty("changingConfigurations", it.isChangingConfigurations)
                }
            }.toString() + "\n")
        }
    }
    val lifecycle = ActivityLifecycleCallback { changed, stage ->
        if (changed is MainActivity) recordLifecycle(stage.name, changed)
    }
    instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(lifecycle) }
    var primary: Throwable? = null
    try {
        recordLifecycle("before-display-configuration")
        device.executeShellCommand("wm density 160")
        device.executeShellCommand("wm size ${widthDp}x${if (widthDp == 320) 640 else 915}")
        for (scale in scales) {
            font.apply(scale)
            recordLifecycle("before-explicit-recreation")
            recreateHomeViewport(activity, widthDp, scale)
            recordLifecycle("after-explicit-recreation")
            for (theme in listOf("Light", "Dark")) {
                val label = "$prefix-$widthDp-font${(scale * 100).toInt()}-${theme.lowercase()}"
                withFailureCapture({ captureNativeScreen(File(folder, "$label-failure.png")) }) {
                    onNode(hasText("Settings") and hasClickAction()).performClick()
                    onNodeWithText("Appearance").performScrollTo().performClick()
                    onNodeWithText(theme).performScrollTo().performClick()
                    waitUntil(10_000) { onNode(hasText(theme) and isSelected()).isDisplayed() }
                    onNodeWithText("Done").performClick()
                    waitUntil(15_000) {
                        var focused = false
                        activity.onActivity { focused = it.window.decorView.hasWindowFocus() }
                        focused && onNode(hasText("Home") and isSelected()).isDisplayed()
                    }
                    check(context.resources.configuration.screenWidthDp == widthDp)
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
        cleanupAfterTest(primary, { activity.close() },
            { instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(lifecycle) } },
            { font.restore() },
            { device.executeShellCommand("wm size ${sizeOverride ?: "reset"}")
                check(device.executeShellCommand("wm size").trim() == originalSize.trim()) },
            { device.executeShellCommand("wm density ${densityOverride ?: "reset"}")
                check(device.executeShellCommand("wm density").trim() == originalDensity.trim()) },
            { check(appearance.edit().apply { if (originalAppearance == null) remove("mode")
                else putString("mode", originalAppearance) }.commit()) })
    }
}

/** Recreate the current instance after platform display/font changes, within the original15s bound. */
internal fun ComposeTestRule.recreateHomeViewport(activity: ActivityScenario<MainActivity>, widthDp: Int, scale: Float) {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    // Scenario.recreate() snapshots an instance before resuming it, which can race a
    // configuration relaunch. Request recreation on the current main-thread instance.
    var previous: MainActivity? = null
    activity.onActivity { current -> previous = current; current.recreate() }
    waitUntil(15_000) {
        var replaced = false
        if (activity.state == androidx.lifecycle.Lifecycle.State.RESUMED) activity.onActivity { current ->
            replaced = current !== previous && !current.isChangingConfigurations &&
                current.window.decorView.hasWindowFocus() && current.resources.configuration.screenWidthDp == widthDp &&
                kotlin.math.abs(current.resources.configuration.fontScale - scale) < 0.01f
        }
        replaced && device.displayWidth == widthDp && onNode(hasText("Home") and isSelected()).isDisplayed()
    }
}
