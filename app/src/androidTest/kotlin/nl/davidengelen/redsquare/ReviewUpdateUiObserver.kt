package nl.davidengelen.redsquare

import android.os.Build
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.json.JSONObject
import org.junit.Test
import java.io.File

/** Public UI only, for the ordinary signed app on a freshly provisioned emulator. */
class ReviewUpdateUiObserver {
    @Test fun observeRetainedLocalWorkAndPreferences() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        check(instrumentation.targetContext.packageName == "nl.davidengelen.redsquare.dev" &&
            Build.HARDWARE in listOf("ranchu", "goldfish"))
        val arguments = InstrumentationRegistry.getArguments()
        val phase = requireNotNull(arguments.getString("reviewUpdatePhase"))
        check(phase in listOf("prepare", "verify"))
        val application = "nl.davidengelen.redsquare"
        val device = UiDevice.getInstance(instrumentation)
        val folder = File(instrumentation.targetContext.cacheDir, "review-update-observation").apply { mkdirs() }
        val draft = "Redesign update check. This is an unsent local draft."
        val expectedBody = arguments.getString("reviewCachedBody")
        fun find(selector: BySelector, timeout: Long = 20_000): UiObject2 =
            requireNotNull(device.wait(Until.findObject(selector.pkg(application)), timeout))
        fun text(value: String) = By.text(value)
        fun feed() { find(text("All posts")); find(text("New post")) }
        fun hideKeyboard() {
            for (keyboard in listOf("com.android.inputmethod.latin", "com.google.android.inputmethod.latin")) {
                if (device.hasObject(By.pkg(keyboard))) {
                    device.executeShellCommand("input keyevent 4")
                    check(device.wait(Until.gone(By.pkg(keyboard)), 5_000))
                }
            }
        }
        fun settings() {
            val direct = device.findObject(By.pkg(application).text("Settings").enabled(true))
            if (direct != null) direct.click() else {
                find(By.desc("Account")).click()
                find(text("Settings")).click()
            }
            find(text("Appearance")).click()
        }
        fun selected(value: String): Boolean {
            var node: UiObject2? = device.findObject(text(value).pkg(application))
            repeat(3) {
                val current = node ?: return false
                if (current.isCheckable) return current.isChecked
                if (current.isSelected) return true
                node = current.parent
            }
            return false
        }
        withFailureCapture({ captureNativeScreen(File(folder, "$phase-failure.png")) }) {
            device.executeShellCommand("am start -W -n $application/nl.davidengelen.redsquare.MainActivity")
            feed()
            settings()
            if (phase == "prepare") find(text("Light")).click()
            val preferenceDeadline = android.os.SystemClock.elapsedRealtime() + 5_000
            var lightSelected = selected("Light")
            while (!lightSelected && android.os.SystemClock.elapsedRealtime() < preferenceDeadline) {
                android.os.SystemClock.sleep(50)
                lightSelected = selected("Light")
            }
            check(lightSelected && android.os.SystemClock.elapsedRealtime() <= preferenceDeadline) {
                "Saved Light preference must be observed selected within five seconds"
            }
            captureNativeScreen(File(folder, "$phase-preference.png"), "Light")
            find(text("Done")).click()
            feed()
            val body = if (phase == "prepare") {
                find(By.descStartsWith("View profile for "), 30_000)
                val candidates = device.findObjects(By.pkg(application).descStartsWith("View profile for "))
                val bodies = candidates.mapNotNull { author ->
                    val header = requireNotNull(author.parent)
                    val metadata = header.findObjects(By.clazz("android.widget.TextView")).mapNotNull { it.text }.toSet()
                    val card = generateSequence(header.parent) { it.parent }.take(4).firstOrNull {
                        it.hasObject(By.descStartsWith("Post actions for ")) &&
                            it.hasObject(By.descStartsWith("Repost post by "))
                    } ?: return@mapNotNull null
                    card.findObjects(By.clazz("android.widget.TextView")).mapNotNull { it.text }
                        .filter { it.length >= 8 && it !in metadata &&
                            !it.matches(Regex("[0-9]+ (likes?|reposts?)")) }
                        .maxByOrNull { it.length }
                }
                requireNotNull(bodies.firstOrNull()) {
                    "An actual loaded public post body, separate from author metadata, is required for cache continuity"
                }
            } else {
                find(text("No internet connection"))
                requireNotNull(expectedBody).also { find(text(it)) }
            }
            captureNativeScreen(File(folder, "$phase-feed.png"), body)
            find(text("New post")).click()
            val editor = find(By.clazz("android.widget.EditText"))
            if (phase == "prepare") editor.text = draft
            else check(editor.text == draft) { "The exact unsent draft must survive the signed update" }
            // Hide the real IME so the physically tapped Save action is unobscured.
            hideKeyboard()
            find(text("Save draft")).click()
            feed()
            captureNativeScreen(File(folder, "$phase-draft-saved.png"))
            find(text("New post")).click()
            check(find(By.clazz("android.widget.EditText")).text == draft)
            captureNativeScreen(File(folder, "$phase-draft-reopened.png"), draft)
            hideKeyboard()
            find(text("Save draft")).click()
            feed()
            val observation = JSONObject().put("phase", phase).put("appearance", "LIGHT")
                .put("cachedBody", body).put("draft", draft).put("draftRemainsUnsent", true)
                .put("offlineCacheRequired", phase == "verify")
            File(folder, "$phase.json").writeText(observation.toString(2))
            instrumentation.sendStatus(0, Bundle().apply { putString("reviewWorkObservation", observation.toString()) })
        }
    }
}
