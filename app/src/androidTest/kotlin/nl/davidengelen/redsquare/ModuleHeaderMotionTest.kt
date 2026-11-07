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

/** Real gestures and window motion, with no Compose clock substituting for system preferences. */
class ModuleHeaderMotionTest {
    @Test fun readingChromeCollapsesRevealsAndChangesModulesAtNormalAndZeroMotion() {
        for (scale in listOf(0, 1)) verify(scale)
    }

    private fun verify(scale: Int) = withNativeModuleMotionScale(scale) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
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
        fun swipe(up: Boolean) {
            val list = nativeVerticalScrollBounds()
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
            observeModuleWindowMotionScale(activity, scale, folder)
        }
    }
}
