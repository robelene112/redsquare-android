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
        val expectedPost = arguments.getString("reviewCachedPost")?.let(::JSONObject)
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
            fun postCard(node: UiObject2): UiObject2 =
                requireNotNull(generateSequence(node.parent) { it.parent }.take(5).firstOrNull {
                    it.hasObject(By.descStartsWith("Post actions for ")) &&
                        it.hasObject(By.descStartsWith("View profile for "))
                }) { "A caption must belong to one actual post card" }
            val caption: String
            val card: UiObject2
            if (phase == "prepare") {
                find(By.descStartsWith("View profile for "), 30_000)
                val authors = device.findObjects(By.pkg(application).descStartsWith("View profile for "))
                val candidates = authors.mapNotNull { author ->
                    val header = requireNotNull(author.parent)
                    val metadata = header.findObjects(By.clazz("android.widget.TextView")).mapNotNull { it.text }.toSet()
                    val post = postCard(author)
                    val caption = post.findObjects(By.clazz("android.widget.TextView")).mapNotNull { it.text }
                        .filter { it !in metadata }.mapNotNull { it.lineSequence().firstOrNull { line -> line.isNotBlank() } }
                        .firstOrNull { it.length >= 8 && !it.startsWith("http", ignoreCase = true) &&
                            !it.matches(Regex("[0-9]+ (likes?|reposts?)")) }
                    caption?.let { it to post }
                }
                val candidate = requireNotNull(candidates.firstOrNull()) {
                    "An authored public caption, separate from author metadata and raw URLs, is required"
                }
                caption = candidate.first
                card = candidate.second
            } else {
                find(text("No internet connection"))
                caption = requireNotNull(expectedPost).getString("caption")
                card = postCard(find(By.textStartsWith(caption)))
            }
            val actualText = requireNotNull(find(By.textStartsWith(caption)).text)
            val observedCaption = actualText.lineSequence().first { it.isNotBlank() }
            check(observedCaption == caption) { "The actual authored caption must remain exact" }
            captureNativeScreen(File(folder, "$phase-feed.png"), actualText)
            // Presentation can move a trailing URL into a link card. Check the
            // authored caption AND public identity/time, not old text layout.
            requireNotNull(card.findObject(By.descStartsWith("Post actions for "))).click()
            find(text("Public identity")).click()
            val author = find(By.text(java.util.regex.Pattern.compile("[1-9A-HJ-NP-Za-km-z]{44}"))).text
            val published = find(By.textStartsWith("Published ")).text
            val cachedPost = JSONObject().put("caption", observedCaption).put("author", author).put("published", published)
            if (expectedPost != null) {
                check(author == expectedPost.getString("author") && published == expectedPost.getString("published"))
            }
            captureNativeScreen(File(folder, "$phase-cached-identity.png"), requireNotNull(author))
            find(text("Done")).click()
            feed()
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
                .put("cachedPost", cachedPost).put("draft", draft).put("draftRemainsUnsent", true)
                .put("offlineCacheRequired", phase == "verify")
            File(folder, "$phase.json").writeText(observation.toString(2))
            instrumentation.sendStatus(0, Bundle().apply { putString("reviewWorkObservation", observation.toString()) })
        }
    }
}
