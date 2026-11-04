package nl.davidengelen.redsquare

import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.gson.JsonObject
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Real input interrupts compact-reminder dismissal without waiting for an animation. */
class FirstUseMotionTest {
    @Test fun largeTextReminderDismissalCanBeInterruptedWithoutReturningAfterRecreation() {
        for (scale in listOf(0, 1)) withNativeModuleMotionScale(scale) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val device = UiDevice.getInstance(instrumentation)
            val folder = File(context.cacheDir, "first-use-motion-evidence").apply { mkdirs() }
            val font = EmulatorFontScale(context, folder, "first-use-motion-$scale")
            var primary: Throwable? = null
            try {
                font.apply(2f)
                withAppFrameFixture("interrupt-$scale", "first-use-motion-evidence") { activity ->
                    check(device.wait(Until.hasObject(By.text("Back up your wallet")), 15_000))
                    val later = requireNotNull(device.findObject(By.text("Later"))).visibleBounds
                    val chat = requireNotNull(device.findObject(By.text("Chat"))).visibleBounds
                    val movie = File(folder, "scale-$scale-interrupted.mp4")
                    var interval = 0L
                    val recording = recordNativeTransition(movie, recordingSeconds = 5) {
                        tap(later)
                        val dismissed = SystemClock.elapsedRealtime()
                        // Exercise input after two nominal frames, during the180ms normal transition.
                        SystemClock.sleep(32)
                        interval = SystemClock.elapsedRealtime() - dismissed
                        tap(chat)
                        if (scale == 1) assertTrue("Input starts before normal dismissal can settle", interval < 180)
                        check(device.wait(Until.hasObject(By.text("New chat")), 10_000))
                    }
                    val frames = retainNativeMotionFrames(movie)
                    frames.add("recording", recording); frames.addProperty("interruptDelayMillis", interval)
                    File(folder, "scale-$scale-interrupted.json").writeText(frames.toString())
                    assertFalse(device.hasObject(By.text("Back up your wallet")))
                    requireNotNull(device.findObject(By.text("Home"))).click()
                    check(device.wait(Until.hasObject(By.text("Native Red Square reference post")), 10_000))
                    assertFalse(device.hasObject(By.text("Back up your wallet")))
                    observeModuleWindowMotionScale(activity, scale, folder)
                    activity.recreate()
                    check(device.wait(Until.hasObject(By.text("Native Red Square reference post")), 15_000))
                    assertFalse(device.hasObject(By.text("Back up your wallet")))
                    captureNativeScreen(File(folder, "scale-$scale-retained.png"))
                    File(folder, "scale-$scale-configuration.json").writeText(JsonObject().apply {
                        addProperty("fontScale", context.resources.configuration.fontScale)
                        addProperty("widthDp", context.resources.configuration.screenWidthDp)
                    }.toString())
                }
            } catch (failure: Throwable) { primary = failure; throw failure }
            finally { cleanupAfterTest(primary, { font.restore() }) }
        }
    }

    private fun tap(bounds: Rect) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                bounds.exactCenterX(), bounds.exactCenterY(), 0)
            try {
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                assertTrue("Real native touch input must be accepted", automation.injectInputEvent(event, true))
            } finally { event.recycle() }
        }
    }
}
