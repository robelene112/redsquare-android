package nl.davidengelen.redsquare

import android.view.accessibility.AccessibilityNodeInfo
import com.google.gson.JsonObject
import java.io.File
import org.junit.Assert.*

/** Shared actual focus and completed contextual speech observation for native controls. */
internal fun NativeTalkBackObservation.focusAndHear(label: String, description: String,
    folder: File, clickLabel: Boolean = false) {
    val target = actionNode(description, clickLabel)
    target.performAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
    device.executeShellCommand("logcat -c")
    assertTrue("Actual focus action for $description",
        target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
    waitFor(5_000) {
        appRoots(automation).any { it.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY) == target }
    }
    waitFor(10_000) { completedFragments(device, description).isNotEmpty() }
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
}

internal fun NativeTalkBackObservation.actionNode(description: String,
    clickLabel: Boolean = false): AccessibilityNodeInfo {
    fun find(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isVisibleToUser && if (clickLabel) root.actionList.any {
                it.id == AccessibilityNodeInfo.ACTION_CLICK && it.label?.toString() == description
            } else root.contentDescription?.toString() == description) return root
        return (0 until root.childCount).firstNotNullOfOrNull { index -> root.getChild(index)?.let(::find) }
    }
    var found: AccessibilityNodeInfo? = null
    waitFor(5_000) { found = appRoots(automation).firstNotNullOfOrNull(::find); found != null }
    var target = requireNotNull(found)
    while (!target.isClickable) target = requireNotNull(target.parent)
    check(target.isVisibleToUser && target.isEnabled)
    return target
}
