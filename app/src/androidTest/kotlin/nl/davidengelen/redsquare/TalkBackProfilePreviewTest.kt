package nl.davidengelen.redsquare

import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.google.gson.JsonObject
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Real upstream TalkBack selected-tab speech and completed English synthesis. */
class TalkBackProfilePreviewTest {
    @Test fun selectedProfileHistoryTabsSpeakTheirLabelAndSelectedState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "profile-talkback-evidence").apply { mkdirs() }
        NativeTalkBackObservation(folder).observe {
            withAppFrameFixture("talkback-profile", "profile-talkback-evidence", "[]",
                profileArchiveResponse = { "[]" }) {
                check(device.wait(Until.hasObject(By.text("Later")), 15_000))
                click(automation, "Later")
                click(automation, "Your profile")
                check(device.wait(Until.hasObject(By.text("Posts")), 15_000))
                for (label in listOf("Posts", "Replies", "Likes")) {
                    if (label != "Posts") click(automation, label)
                    var selected: AccessibilityNodeInfo? = null
                    waitFor(5_000) {
                        var current: AccessibilityNodeInfo? = node(automation, label)
                        while (current != null && !current.isSelected) current = current.parent
                        selected = current
                        current?.isSelected == true && current.isVisibleToUser
                    }
                    val target = requireNotNull(selected)
                    target.performAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
                    device.executeShellCommand("logcat -c")
                    assertTrue(target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
                    waitFor(5_000) { appRoots(automation).any {
                        it.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY) == target
                    } }
                    var completions = emptySet<String>()
                    waitFor(10_000) {
                        val selectedIds = fragments(device, label).filter {
                            Regex("\\b${Regex.escape(label)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(it) &&
                                Regex("\\bselected\\b", RegexOption.IGNORE_CASE).containsMatchIn(it) &&
                                !Regex("\\bnot\\s+selected\\b", RegexOption.IGNORE_CASE).containsMatchIn(it)
                        }.mapNotNull { Regex("utteranceId=([^,\\s]+)").find(it)?.groupValues?.get(1) }.toSet()
                        completions = selectedIds.intersect(completedFragments(device, label).toSet())
                        completions.isNotEmpty()
                    }
                    assertEquals(serviceProcess, talkbackProcess(device))
                    File(folder, "${label.lowercase()}-speech.txt").writeText(speechLog(device))
                    File(folder, "${label.lowercase()}-result.json").writeText(JsonObject().apply {
                        addProperty("label", label)
                        addProperty("nativeSelected", target.isSelected)
                        addProperty("actualAccessibilityFocus", true)
                        addProperty("completedSelectedUtterances", completions.joinToString())
                        addProperty("serviceProcess", serviceProcess)
                        addProperty("physicalAudibility", false)
                    }.toString())
                    capture(automation, File(folder, "${label.lowercase()}-selected.png"))
                }
                click(automation, "Back")
            }
            assertEquals(serviceProcess, talkbackProcess(device))
        }
    }
}
