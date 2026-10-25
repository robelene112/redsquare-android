package nl.davidengelen.redsquare

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Real pinned TalkBack speech and modal focus through viewer controls and return. */
class TalkBackImageViewerPreviewTest {
    @Test fun imagePositionZoomControlsAndCloseHaveCompletedSpeechAndReturnFocus() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val folder = File(context.cacheDir, "image-viewer-talkback-evidence").apply { mkdirs() }
        val fixture = JsonParser.parseReader(instrumentation.context.assets.open("native-images-reference.json").reader()).asJsonObject
        NativeTalkBackObservation(folder).observe {
            withAppFrameFixture("viewer-talkback", "image-viewer-talkback-evidence", "[${fixture.getAsJsonObject("ordinary")}]") {
                check(device.wait(Until.hasObject(By.text("Later")), 15_000))
                click(automation, "Later")
                // TalkBack restores its own navigation history when a modal window closes.
                // Direct ACTION_ACCESSIBILITY_FOCUS bypasses that history. Reach the
                // attachment with the pinned service's actual Meta+Right navigation (NewKeyComboModel).
                waitFor(10_000) { focusedText(automation) == "Wallet" && completedFragments(device, "Wallet").isNotEmpty() }
                device.executeShellCommand("logcat -c")
                // UiAutomation key injection skips Android's accessibility input filter.
                // Feed the disposable emulator's real virtual keyboard instead; the
                // observation guard excludes physical devices and the ordinary app.
                File(folder, "keyboard-tool-preferences.xml").writeText(device.executeShellCommand(
                    "run-as com.android.talkback cat /data/user_de/0/com.android.talkback/shared_prefs/com.android.talkback_preferences.xml"))
                val inputDevices = device.executeShellCommand("su 0 getevent -lp")
                File(folder, "keyboard-devices.txt").writeText(inputDevices)
                val keyboard = inputDevices.split(Regex("(?=add device )")).first {
                    it.contains("KEY_RIGHT") && it.contains("KEY_LEFTMETA")
                }.let { requireNotNull(Regex("add device [0-9]+: (/dev/input/event[0-9]+)").find(it)).groupValues[1] }
                val next = listOf("1 125 1", "0 0 0", "1 106 1", "0 0 0", "1 106 0", "0 0 0", "1 125 0", "0 0 0")
                    .joinToString(" && ") { "sendevent $keyboard $it" }
                val release = "sendevent $keyboard 1 106 0; sendevent $keyboard 1 125 0; sendevent $keyboard 0 0 0"
                val script = File(folder, "keyboard-next.sh").apply {
                    // UiAutomation starts a process directly; it does not parse shell quotes.
                    writeText("exec 2>&1\ntrap '$release' EXIT\n$next && echo sent\n")
                }
                var reachedOrigin = false
                for (step in 0 until 16) {
                    val previous = appRoots(automation).firstNotNullOfOrNull {
                        it.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
                    }
                    val output = device.executeShellCommand("su 0 sh ${script.absolutePath}")
                    File(folder, "keyboard-step-$step.txt").writeText(output)
                    check(output.trim() == "sent")
                    waitFor(5_000) {
                        appRoots(automation).any { root ->
                            root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)?.let { it != previous } == true
                        }
                    }
                    reachedOrigin = appRoots(automation).any { root ->
                        root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)?.contentDescription?.toString() == "Image 1 of 2"
                    }
                    if (reachedOrigin) break
                }
                assertTrue("TalkBack navigation reaches the original attachment", reachedOrigin)
                waitFor(10_000) { completedFragments(device, "Image 1 of 2").isNotEmpty() }
                File(folder, "origin-navigation-speech.txt").writeText(speechLog(device))
                captureFocusedSpeech("origin", "Image 1 of 2", folder, actionNode("Image 1 of 2"))
                assertTrue(actionNode("Image 1 of 2").performAction(AccessibilityNodeInfo.ACTION_CLICK))
                check(device.wait(Until.hasObject(By.text("Close image")), 10_000))
                val bounds = JsonObject()
                for ((label, name) in listOf("zoom-in" to "Zoom in", "next" to "Next image", "close" to "Close image")) {
                    focusAndHear(label, name, folder, textLabel = true)
                    val rectangle = Rect().also { actionNode(name, textLabel = true).getBoundsInScreen(it) }
                    val minimum = 48 * context.resources.displayMetrics.density
                    assertTrue(rectangle.width() >= minimum && rectangle.height() >= minimum)
                    bounds.addProperty(label, rectangle.flattenToString())
                }
                click(automation, "Zoom in")
                check(device.wait(Until.hasObject(By.text("150%")), 5_000))
                focusAndHear("zoomed-image", "Full screen image 1 of 2", folder,
                    completionTimeoutMillis = 15_000, requireClickAction = false)
                assertTrue(completedFragments(device, "Zoom 150 percent").isNotEmpty())
                focusAndHear("reset", "Reset zoom", folder, textLabel = true)
                click(automation, "Reset zoom")
                check(device.wait(Until.hasObject(By.text("100%")), 5_000))
                click(automation, "Next image")
                check(device.wait(Until.hasObject(By.text("Image 2 of 2")), 5_000))
                // Let the platform finish its automatic focus transfer before an explicit
                // focus-and-speech observation of the newly created image control.
                device.waitForIdle(5_000)
                focusAndHear("second-image", "Full screen image 2 of 2", folder,
                    completionTimeoutMillis = 15_000, requireClickAction = false)
                assertTrue(completedFragments(device, "Zoom 100 percent").isNotEmpty())
                click(automation, "Close image")
                check(device.wait(Until.gone(By.text("Close image")), 10_000))
                waitFor(10_000) {
                    appRoots(automation).any { root ->
                        root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)?.contentDescription?.toString() == "Image 1 of 2"
                    }
                }
                capture(automation, File(folder, "returned-focus.png"))
                device.dumpWindowHierarchy(File(folder, "returned-focus.xml"))
                File(folder, "result.json").writeText(JsonObject().apply {
                    addProperty("returnedToOrigin", true)
                    addProperty("serviceProcess", serviceProcess)
                    add("controls", bounds)
                    addProperty("physicalAudibility", false)
                }.toString())
            }
        }
    }
}
