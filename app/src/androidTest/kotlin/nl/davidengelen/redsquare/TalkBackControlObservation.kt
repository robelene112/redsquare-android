package nl.davidengelen.redsquare

import android.view.accessibility.AccessibilityNodeInfo
import com.google.gson.JsonObject
import java.io.File
import org.junit.Assert.*

/** Shared actual focus and completed contextual speech observation for native controls. */
internal fun NativeTalkBackObservation.focusAndHear(label: String, description: String,
    folder: File, clickLabel: Boolean = false, completionTimeoutMillis: Long = 10_000, textLabel: Boolean = false, requireClickAction: Boolean = true) {
    val target = actionNode(description, clickLabel, textLabel, requireClickAction)
    target.performAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
    device.executeShellCommand("logcat -c")
    assertTrue("Actual focus action for $description",
        target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
    captureFocusedSpeech(label, description, folder, target, completionTimeoutMillis)
}

/** Observe service-owned navigation without requesting duplicate focus or speech. */
internal fun NativeTalkBackObservation.captureFocusedSpeech(label: String, description: String,
    folder: File, target: AccessibilityNodeInfo, completionTimeoutMillis: Long = 10_000) {
    waitFor(5_000) {
        appRoots(automation).any { it.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY) == target }
    }
    waitFor(completionTimeoutMillis) { completedFragments(device, description).isNotEmpty() }
    assertEquals("The same real service must produce contextual speech", serviceProcess, talkbackProcess(device))
    File(folder, "$label-speech.txt").writeText(speechLog(device))
    File(folder, "$label-result.json").writeText(JsonObject().apply {
        addProperty("expectedContext", description)
        addProperty("actualAccessibilityFocus", true)
        addProperty("serviceProcess", serviceProcess)
        addProperty("engineCompletions", completedFragments(device, description).joinToString())
        addProperty("physicalAudibility", false)
    }.toString())
    capture(automation, File(folder, "$label.png"))
    device.dumpWindowHierarchy(File(folder, "$label.xml"))
}

internal fun NativeTalkBackObservation.actionNode(description: String,
    clickLabel: Boolean = false, textLabel: Boolean = false, requireClickAction: Boolean = true): AccessibilityNodeInfo {
    fun find(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isVisibleToUser && if (textLabel) root.text?.toString() == description else if (clickLabel) root.actionList.any {
                it.id == AccessibilityNodeInfo.ACTION_CLICK && it.label?.toString() == description
            } else root.contentDescription?.toString() == description) return root
        return (0 until root.childCount).firstNotNullOfOrNull { index -> root.getChild(index)?.let(::find) }
    }
    var found: AccessibilityNodeInfo? = null
    waitFor(5_000) { found = appRoots(automation).firstNotNullOfOrNull(::find); found != null }
    var target = requireNotNull(found)
    // Compose can expose a descriptive child beneath the actual adjustable native control.
    // Focus that control, as ordinary screen-reader traversal does, not its text-only child.
    while (if (requireClickAction) !target.isClickable else !target.isFocusable) target = requireNotNull(target.parent)
    check(target.isVisibleToUser && target.isEnabled)
    return target
}
