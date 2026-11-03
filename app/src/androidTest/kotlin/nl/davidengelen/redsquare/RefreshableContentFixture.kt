package nl.davidengelen.redsquare

import android.app.UiAutomation
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule

private fun availableRefreshControl() = SemanticsMatcher("Available user Refresh action") { node ->
    node.config.getOrNull(SemanticsActions.CustomActions)?.any { it.label == "Refresh" } == true
} or (hasText("Refresh") and hasClickAction() and isEnabled())

/** Observe actual available user actions. Signed-content/request assertions remain at their callers. */
internal fun ComposeTestRule.refreshableContentReady(): Boolean {
    val controls = onAllNodes(availableRefreshControl())
    return controls.fetchSemanticsNodes(atLeastOneRootRequired = false).indices.any { controls[it].isDisplayed() }
}

/** Real pull at list start, or the genuine explicit control on other current destinations. */
internal fun ComposeTestRule.refreshVisibleView() {
    val tag = listOf("feed-post-list", "conversation-post-list").firstOrNull { onNodeWithTag(it).isDisplayed() }
    if (tag != null) {
        onNodeWithTag(tag).performScrollToIndex(0)
        pullToRefresh(tag)
    } else onNode(hasText("Refresh") and hasClickAction() and isEnabled()).performClick()
}

/** The actual accessibility action preserves a retained reading anchor and reader focus. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeTestRule.refreshKeepingReadingPosition() {
    onNode(availableRefreshControl()).performCustomAccessibilityActionWithLabel("Refresh")
}

/** Native observation keeps normal system motion/TalkBack, without a replacement Compose clock. */
internal fun refreshNativeContent(automation: UiAutomation, timeoutMillis: Long = 5_000) {
    fun refresh(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val action = node.actionList.firstOrNull { it.label?.toString() == "Refresh" }
        if (node.isEnabled && action != null) {
            check(node.performAction(action.id)) { "Native Refresh action was rejected" }
            return true
        }
        return (0 until node.childCount).any { refresh(node.getChild(it)) }
    }
    val end = SystemClock.elapsedRealtime() + timeoutMillis
    do {
        if (refresh(automation.rootInActiveWindow)) return
        SystemClock.sleep(50)
    } while (SystemClock.elapsedRealtime() < end)
    error("Native Refresh action was not available within its existing deadline")
}
