package nl.davidengelen.redsquare

import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.gson.JsonObject
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Actual navigation, rapid touch and focus while incoming content is still being revealed. */
class DestinationJourneyMotionTest {
    @Test fun primaryAndSecondaryJourneysRemainInterruptibleAtNormalAndZeroMotion() {
        for (scale in listOf(0, 1)) withNativeModuleMotionScale(scale) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val device = UiDevice.getInstance(instrumentation)
            val folder = File(context.cacheDir, "destination-journey-evidence").apply { mkdirs() }
            fun awaitText(text: String) { check(device.wait(Until.hasObject(By.text(text)), 10_000)) { text } }
            fun click(text: String) = requireNotNull(device.wait(Until.findObject(By.text(text).enabled(true)), 5_000)).click()
            fun waitEditor(focused: Boolean = false): AccessibilityNodeInfo {
                val deadline = SystemClock.elapsedRealtime() + 1_000
                do {
                    editor(focused)?.let { return it }
                    SystemClock.sleep(8)
                } while (SystemClock.elapsedRealtime() < deadline)
                error("The current app editor must be immediately reachable, focused=$focused")
            }
            fun movie(name: String, action: () -> Unit) {
                val file = File(folder, "scale-$scale-$name.mp4")
                var actionFailure: Throwable? = null
                val recording = recordNativeTransition(file, recordingSeconds = 5) {
                    try { action() } catch (failure: Throwable) { actionFailure = failure }
                }
                cleanupAfterTest(actionFailure, {
                    val frames = retainNativeMotionFrames(file)
                    frames.add("recording", recording)
                    frames.addProperty("actionAssertionsPassed", actionFailure == null)
                    File(folder, "scale-$scale-$name.json").writeText(frames.toString())
                })
                actionFailure?.let { throw it }
                captureNativeScreen(File(folder, "scale-$scale-$name-returned.png"))
            }
            withAvatarJourney("Dark") { activity, _, _, _, author ->
                awaitText("Shared avatar journey")
                fun home() { click("Home"); awaitText("All posts"); awaitText("Shared avatar journey") }
                movie("search-and-back") {
                    click("Search"); waitEditor()
                    // Keep both observed destinations in the recording, without delaying input readiness.
                    SystemClock.sleep(250)
                    home()
                }
                movie("focus-during-entry") {
                    val search = requireNotNull(device.findObject(By.text("Search"))).visibleBounds
                    val entered = SystemClock.elapsedRealtime()
                    tap(search)
                    val field = waitEditor()
                    val bounds = Rect().apply { field.getBoundsInScreen(this) }
                    val tapped = SystemClock.elapsedRealtime()
                    tap(bounds)
                    waitEditor(focused = true)
                    File(folder, "scale-$scale-editor-focus.json").writeText(JsonObject().apply {
                        addProperty("fieldReadyAfterRouteMillis", tapped - entered)
                        addProperty("focusObservedAfterTapMillis", SystemClock.elapsedRealtime() - tapped)
                        addProperty("focusObservedAfterRouteMillis", SystemClock.elapsedRealtime() - entered)
                        addProperty("fieldBounds", bounds.toShortString())
                    }.toString())
                }
                var ime = false
                activity.onActivity { screen -> ime = ViewCompat.getRootWindowInsets(screen.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == true }
                if (ime) {
                    // The original goal is actual IME dismissal. UiAutomator's
                    // Boolean reports an accessibility event, not that outcome.
                    instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                    val deadline = SystemClock.elapsedRealtime() + 5_000
                    do {
                        activity.onActivity { screen -> ime = ViewCompat.getRootWindowInsets(screen.window.decorView)
                            ?.isVisible(WindowInsetsCompat.Type.ime()) == true }
                        if (ime) SystemClock.sleep(16)
                    } while (ime && SystemClock.elapsedRealtime() < deadline)
                    assertFalse("The keyboard closes before using the module dock", ime)
                }
                home()
                val search = requireNotNull(device.findObject(By.text("Search"))).visibleBounds
                val chat = requireNotNull(device.findObject(By.text("Chat"))).visibleBounds
                val home = requireNotNull(device.findObject(By.text("Home"))).visibleBounds
                movie("rapid-primary") {
                    tap(search); SystemClock.sleep(35)
                    tap(chat); SystemClock.sleep(35)
                    tap(home)
                    awaitText("All posts"); awaitText("Shared avatar journey")
                    assertNull("An exited editor must not retain input focus", editor(focused = true))
                }
                movie("thread-and-back") {
                    click("Shared avatar journey"); awaitText("Conversation")
                    SystemClock.sleep(250)
                    click("Back"); awaitText("All posts")
                }
                movie("private-and-back") {
                    click("Chat"); awaitText("New chat")
                    click(author.publicKey); waitEditor()
                    SystemClock.sleep(250)
                    click("Back"); awaitText("New chat")
                }
                movie("groups-and-back") {
                    click(context.getString(R.string.group_title))
                    awaitText(context.getString(R.string.group_create))
                    SystemClock.sleep(250)
                    click("Back"); awaitText("New chat")
                    home()
                }
                observeModuleWindowMotionScale(activity, scale, folder)
            }
        }
    }

    /** Query the application's window even when the input-method window is active. */
    private fun editor(focused: Boolean): AccessibilityNodeInfo? {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val roots = listOfNotNull(automation.rootInActiveWindow) + automation.windows.mapNotNull { it.root }
        fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.className?.toString() == "android.widget.EditText" && node.isEnabled && node.isVisibleToUser &&
                (!focused || node.isFocused)) return node
            for (index in 0 until node.childCount) node.getChild(index)?.let { find(it)?.let { result -> return result } }
            return null
        }
        for (root in roots) if (root.packageName?.toString() == instrumentation.targetContext.packageName)
            find(root)?.let { return it }
        return null
    }

    private fun tap(bounds: Rect) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val started = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(started, SystemClock.uptimeMillis(), action,
                bounds.exactCenterX(), bounds.exactCenterY(), 0)
            try { event.source = InputDevice.SOURCE_TOUCHSCREEN; assertTrue(automation.injectInputEvent(event, true)) }
            finally { event.recycle() }
        }
    }
}
