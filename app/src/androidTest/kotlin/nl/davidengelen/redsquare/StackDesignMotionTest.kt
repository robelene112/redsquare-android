package nl.davidengelen.redsquare

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Test

/** Actual window clocks and original movies, without a Compose test clock. */
class StackDesignMotionTest {
    @Test fun editorSourceAndReviewFollowNormalAndDisabledSystemMotion() {
        for (scale in listOf(0, 1)) withNativeModuleMotionScale(scale) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val device = UiDevice.getInstance(instrumentation)
            val folder = File(instrumentation.targetContext.cacheDir, "stack-design-motion-evidence").apply { mkdirs() }
            fun click(text: String) { requireNotNull(device.wait(Until.findObject(By.text(text)), 5_000)).click() }
            fun movie(name: String, action: () -> Unit) {
                val file = File(folder, "scale-$scale-$name.mp4")
                val recording = recordNativeTransition(file, recordingSeconds = 5, dismiss = action)
                val frames = retainNativeMotionFrames(file)
                frames.add("recording", recording)
                File(folder, "scale-$scale-$name.json").writeText(frames.toString())
            }
            withAppFrameFixture("stack-motion-$scale", "stack-design-motion-evidence") { activity ->
                click("Later")
                requireNotNull(device.wait(Until.findObject(By.desc("Switch app: Red Square")), 5_000)).click()
                click("Stack")
                check(device.wait(Until.hasObject(By.text("Stack design preview")), 5_000))
                movie("editor-open") {
                    requireNotNull(device.wait(Until.findObject(By.desc("Write an article")), 5_000)).click()
                    check(device.wait(Until.hasObject(By.text("Write")), 5_000))
                }
                val end = SystemClock.elapsedRealtime() + 5_000
                while (!device.hasObject(By.text("Markdown")) && SystemClock.elapsedRealtime() < end) {
                    // The outer native reading viewport, excluding an editor's own scroll surface.
                    val bounds = nativeWindowNodes().filter { node -> node.isVisibleToUser && !node.isEditable &&
                        node.isScrollable && node.actionList.any { it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id } }
                        .map { node -> android.graphics.Rect().apply { node.getBoundsInScreen(this) } }
                        .maxBy { it.width() * it.height() }
                    check(device.swipe(bounds.centerX(), bounds.bottom - bounds.height() / 5,
                        bounds.centerX(), bounds.top + bounds.height() / 5, 20))
                }
                check(device.hasObject(By.text("Markdown")))
                movie("source-toggle") {
                    click("Markdown")
                    check(device.wait(Until.hasObject(By.text("Markdown source")), 5_000))
                }
                movie("publication-review") {
                    click("Review")
                    check(device.wait(Until.hasObject(By.text("Review & publish")), 5_000))
                }
                observeModuleWindowMotionScale(activity, scale, folder)
                captureNativeScreen(File(folder, "scale-$scale-review.png"))
            }
        }
    }
}
