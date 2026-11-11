package nl.davidengelen.redsquare

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.gson.JsonParser
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Additive real keyboard/menu capture, independent of the original reading-motion180s budget. */
class ModuleMenuKeyboardMotionTest {
    @Test fun menuKeyboardAndImmediateEscapeWorkAtNormalAndZeroMotion() {
        for (scale in listOf(0, 1)) verify(scale)
    }

    private fun verify(scale: Int) = withNativeModuleMotionScale(scale) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val folder = File(context.cacheDir, "module-menu-motion-evidence").apply { mkdirs() }
        val rows = instrumentation.context.assets.open("native-feed-scroll-reference.json").reader().use {
            JsonParser.parseReader(it).asJsonObject.get("initialRows").toString()
        }
        fun await(timeout: Long, message: String, condition: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + timeout
            while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
            check(condition()) { message }
        }
        fun foregroundHas(text: String? = null, description: String? = null): Boolean =
            nativeWindowNodes().any { node ->
                node.refresh() && node.packageName?.toString() == context.packageName && node.isVisibleToUser &&
                    node.isEnabled && (text == null || node.text?.toString() == text) &&
                    (description == null || node.contentDescription?.toString() == description)
            }
        fun focusedMenuText(): String? {
            // The active native hierarchy exports focus on the clickable row,
            // with its text on a child. Observe that explicit input-focus flag.
            val root = instrumentation.uiAutomation.windows.firstOrNull { it.isActive && it.isFocused }?.root
                ?: return null
            if (root.packageName?.toString() != context.packageName) return null
            fun focused(node: android.view.accessibility.AccessibilityNodeInfo): android.view.accessibility.AccessibilityNodeInfo? {
                // The direct UiAutomation cache can retain the previous row's
                // input-focus flag after D-pad movement. Require a live native
                // refresh before accepting that flag; vanished nodes stay unknown.
                if (!node.refresh()) return null
                if (node.isFocused && node.isClickable && node.isVisibleToUser) return node
                return (0 until node.childCount).firstNotNullOfOrNull { index -> node.getChild(index)?.let(::focused) }
            }
            fun text(node: android.view.accessibility.AccessibilityNodeInfo): String? = node.text?.toString()?.takeIf { it.isNotBlank() }
                ?: (0 until node.childCount).firstNotNullOfOrNull { index -> node.getChild(index)?.let(::text) }
            return focused(root)?.let(::text)
        }
        fun movie(name: String, action: () -> Unit) {
            val file = File(folder, "scale-$scale-$name.mp4")
            val recording = recordNativeTransition(file, recordingSeconds = 5, dismiss = action)
            val observed = retainNativeMotionFrames(file)
            observed.add("recording", recording)
            File(folder, "scale-$scale-$name.json").writeText(observed.toString())
        }
        withAppFrameFixture("menu-$scale", "module-menu-motion-evidence", rows, stackDesignFixture = true) { activity ->
            check(device.wait(Until.hasObject(By.text("Reference post 20")), 15_000))
            requireNotNull(device.wait(Until.findObject(By.text("Later")), 5_000)).click()
            check(device.wait(Until.gone(By.text("Back up your wallet")), 5_000))
            val navigation = requireNotNull(device.findObject(By.text("Home"))).visibleBounds
            movie("menu-keyboard") {
                requireNotNull(device.findObject(By.desc("Switch app: Red Square"))).click()
                check(device.wait(Until.hasObject(By.text("Stack")), 5_000))
                // Preserve the original Escape-first regression without a prior
                // Tab supplying focus on the host's behalf.
                check(device.pressKeyCode(android.view.KeyEvent.KEYCODE_ESCAPE))
                await(5_000, "Escape returns ownership to the actual app switcher") {
                    !device.hasObject(By.text("Stack")) && foregroundHas(description = "Switch app: Red Square")
                }
                requireNotNull(device.findObject(By.desc("Switch app: Red Square"))).click()
                check(device.wait(Until.hasObject(By.text("Stack")), 5_000))
                check(device.pressKeyCode(android.view.KeyEvent.KEYCODE_TAB))
                await(5_000, "Tab must reach a real clickable menu row") {
                    focusedMenuText() in setOf("Red Square", "Stack")
                }
                val first = requireNotNull(focusedMenuText())
                val next = if (first == "Red Square") "Stack" else "Red Square"
                // Retain the actual starting row even if directional navigation fails.
                File(folder, "scale-$scale-menu-focus.txt").writeText("start=$first expected=$next\n")
                check(if (first == "Red Square") device.pressDPadDown() else device.pressDPadUp())
                withFailureCapture({
                    File(folder, "scale-$scale-menu-focus.txt").appendText("observed=${focusedMenuText()}\n")
                }) {
                    await(5_000, "Directional navigation moves focus without activating a module") { focusedMenuText() == next }
                }
                assertFalse(device.hasObject(By.text("Stack design preview")))
                File(folder, "scale-$scale-menu-focus.txt").writeText("$first -> $next\n")
                check(device.pressKeyCode(android.view.KeyEvent.KEYCODE_ESCAPE))
                await(5_000, "Escape returns ownership to the actual Account control") {
                    !device.hasObject(By.text("Stack")) && foregroundHas(description = "Account")
                }
                requireNotNull(device.findObject(By.desc("Account"))).click()
                check(device.wait(Until.hasObject(By.text("Wallet")), 5_000))
                check(device.pressKeyCode(android.view.KeyEvent.KEYCODE_ESCAPE))
                await(5_000, "Escape returns ownership to the same module navigation") {
                    !device.hasObject(By.text("Wallet")) && foregroundHas(text = "Home")
                }
                assertEquals(navigation, requireNotNull(device.findObject(By.text("Home"))).visibleBounds)
            }
            observeModuleWindowMotionScale(activity, scale, folder)
        }
    }
}
