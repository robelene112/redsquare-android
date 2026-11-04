package nl.davidengelen.redsquare

import android.os.Build
import android.os.SystemClock
import android.view.ViewGroup
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.findViewTreeCompositionContext
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Real gestures and window motion, with no Compose clock substituting for system preferences. */
class ModuleHeaderMotionTest {
    @Test fun readingChromeCollapsesRevealsAndChangesModulesAtNormalAndZeroMotion() {
        for (scale in listOf(0, 1)) verify(scale)
    }

    private fun verify(scale: Int) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
        val device = UiDevice.getInstance(instrumentation)
        val originals = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
            .associateWith { device.executeShellCommand("settings get global $it").trim() }
        check(originals.values.all { it == "null" || it.toFloatOrNull()?.let { value -> value.isFinite() && value >= 0 } == true })
        val folder = File(context.cacheDir, "module-header-motion-evidence").apply { mkdirs() }
        val rows = instrumentation.context.assets.open("native-feed-scroll-reference.json").reader().use {
            JsonParser.parseReader(it).asJsonObject.get("initialRows").toString()
        }
        fun accountVisible(): Boolean? = try {
            device.findObject(By.desc("Account"))?.visibleBounds?.let { !it.isEmpty } == true
        } catch (_: androidx.test.uiautomator.StaleObjectException) {
            // The header is moving between platform snapshots. Neither visibility
            // nor absence is established; retry the same condition within its original budget.
            null
        }
        fun await(timeout: Long, message: String, condition: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + timeout
            while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
            check(condition()) { message }
        }
        fun focusedMenuText(): String? {
            // The active native hierarchy exports focus on the clickable row,
            // with its text on a child. Observe that explicit input-focus flag.
            val root = instrumentation.uiAutomation.windows.firstOrNull { it.isActive && it.isFocused }?.root
                ?: return null
            if (root.packageName?.toString() != context.packageName) return null
            fun focused(node: android.view.accessibility.AccessibilityNodeInfo): android.view.accessibility.AccessibilityNodeInfo? {
                if (node.isFocused && node.isClickable && node.isVisibleToUser) return node
                return (0 until node.childCount).firstNotNullOfOrNull { index -> node.getChild(index)?.let(::focused) }
            }
            fun text(node: android.view.accessibility.AccessibilityNodeInfo): String? = node.text?.toString()?.takeIf { it.isNotBlank() }
                ?: (0 until node.childCount).firstNotNullOfOrNull { index -> node.getChild(index)?.let(::text) }
            return focused(root)?.let(::text)
        }
        fun swipe(up: Boolean) {
            val list = requireNotNull(device.findObject(By.scrollable(true))).visibleBounds
            check(list.height() >= 100)
            val upper = list.top + list.height() / 5
            val lower = list.bottom - list.height() / 5
            check(device.swipe(list.centerX(), if (up) lower else upper, list.centerX(), if (up) upper else lower, 20))
        }
        fun movie(name: String, action: () -> Unit) {
            val file = File(folder, "scale-$scale-$name.mp4")
            val recording = recordNativeTransition(file, recordingSeconds = 5, dismiss = action)
            val observed = retainNativeMotionFrames(file)
            observed.add("recording", recording)
            File(folder, "scale-$scale-$name.json").writeText(observed.toString())
        }
        var primary: Throwable? = null
        try {
            originals.keys.forEach { device.executeShellCommand("settings put global $it $scale") }
            withAppFrameFixture("header-$scale", "module-header-motion-evidence", rows) { activity ->
                check(device.wait(Until.hasObject(By.text("Reference post 20")), 15_000))
                requireNotNull(device.wait(Until.findObject(By.text("Later")), 5_000)).click()
                check(device.wait(Until.gone(By.text("Back up your wallet")), 5_000))
                await(5_000, "Expanded app navigation must be visible") { accountVisible() == true }
                val navigation = requireNotNull(device.findObject(By.text("Home"))).visibleBounds
                captureNativeScreen(File(folder, "scale-$scale-expanded.png"))
                movie("collapse") {
                    swipe(up = true)
                    await(5_000, "Reading down must collapse the actual app header") { accountVisible() == false }
                }
                assertEquals("Module navigation remains anchored during reading", navigation,
                    requireNotNull(device.findObject(By.text("Home"))).visibleBounds)
                assertTrue(device.findObjects(By.textContains("Reference post ")).any { !it.visibleBounds.isEmpty })
                captureNativeScreen(File(folder, "scale-$scale-collapsed.png"))
                movie("keyboard") {
                    check(device.pressKeyCode(android.view.KeyEvent.KEYCODE_TAB))
                    await(5_000, "Keyboard navigation must reveal the collapsed shared controls") { accountVisible() == true }
                    val focused = requireNotNull(device.findObject(By.focused(true)))
                    assertFalse("Keyboard focus must be visible", focused.visibleBounds.isEmpty)
                }
                swipe(up = true)
                await(5_000, "Touch reading can collapse chrome again after keyboard navigation") { accountVisible() == false }
                check(device.pressDPadUp())
                await(5_000, "D-pad navigation must reveal the collapsed shared controls") { accountVisible() == true }
                captureNativeScreen(File(folder, "scale-$scale-keyboard-revealed.png"))
                swipe(up = true)
                await(5_000, "Reading can collapse chrome before the upward gesture") { accountVisible() == false }
                movie("reveal") {
                    swipe(up = false)
                    await(5_000, "Reading up must reveal the actual app header") { accountVisible() == true }
                }
                assertEquals(navigation, requireNotNull(device.findObject(By.text("Home"))).visibleBounds)
                captureNativeScreen(File(folder, "scale-$scale-revealed.png"))
                movie("menu-keyboard") {
                    requireNotNull(device.findObject(By.desc("Switch app: Red Square"))).click()
                    check(device.wait(Until.hasObject(By.text("Stack")), 5_000))
                    // Preserve the original Escape-first regression without a prior
                    // Tab supplying focus on the host's behalf.
                    check(device.pressKeyCode(android.view.KeyEvent.KEYCODE_ESCAPE))
                    check(device.wait(Until.gone(By.text("Stack")), 5_000))
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
                    check(device.wait(Until.gone(By.text("Stack")), 5_000))
                    requireNotNull(device.findObject(By.desc("Account"))).click()
                    check(device.wait(Until.hasObject(By.text("Wallet")), 5_000))
                    check(device.pressKeyCode(android.view.KeyEvent.KEYCODE_ESCAPE))
                    check(device.wait(Until.gone(By.text("Wallet")), 5_000))
                    assertEquals(navigation, requireNotNull(device.findObject(By.text("Home"))).visibleBounds)
                }
                movie("module") {
                    requireNotNull(device.findObject(By.desc("Switch app: Red Square"))).click()
                    requireNotNull(device.wait(Until.findObject(By.text("Stack")), 5_000)).click()
                    check(device.wait(Until.hasObject(By.text("Stack design preview")), 5_000))
                    // Rapidly reopen account controls during module/menu transitions.
                    // Each real popup owns an independent native composition lifetime.
                    await(5_000, "The new module exposes the actual account control") { accountVisible() == true }
                    requireNotNull(device.findObject(By.desc("Account"))).click()
                    check(device.wait(Until.hasObject(By.text("Wallet")), 5_000))
                    check(device.pressKeyCode(android.view.KeyEvent.KEYCODE_ESCAPE))
                    check(device.wait(Until.gone(By.text("Wallet")), 5_000))
                    check(device.hasObject(By.text("Stack design preview")))
                }
                movie("article") {
                    requireNotNull(device.findObject(By.textContains("A Node in a Browser Tab"))).click()
                    check(device.wait(Until.hasObject(By.text("Article preview")), 5_000))
                }
                val stackNavigation = requireNotNull(device.findObject(By.text("Discover"))).visibleBounds
                movie("article-reading") {
                    swipe(up = true)
                    await(5_000, "Stack article reading also collapses the actual shared header") { accountVisible() == false }
                    assertEquals(stackNavigation, requireNotNull(device.findObject(By.text("Discover"))).visibleBounds)
                    swipe(up = false)
                    await(5_000, "Stack article reading reveals the same shared controls") { accountVisible() == true }
                    assertEquals(stackNavigation, requireNotNull(device.findObject(By.text("Discover"))).visibleBounds)
                }
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
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, *originals.map { (key, value) -> {
                device.executeShellCommand(if (value == "null") "settings delete global $key" else "settings put global $key $value")
                check(device.executeShellCommand("settings get global $key").trim() == value)
            } }.toTypedArray())
        }
    }
}
