package nl.davidengelen.redsquare

import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry

/** Snapshot native node data without retaining UiObject2 handles while the window moves.
 * Traverse actual virtual descendants; provider text-search support is not assumed. */
internal fun nativeWindowNodes(): List<AccessibilityNodeInfo> {
    val nodes = mutableListOf<AccessibilityNodeInfo>()
    fun visit(node: AccessibilityNodeInfo) {
        nodes.add(node)
        for (index in 0 until node.childCount) node.getChild(index)?.let(::visit)
    }
    InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow?.let(::visit)
    return nodes
}
