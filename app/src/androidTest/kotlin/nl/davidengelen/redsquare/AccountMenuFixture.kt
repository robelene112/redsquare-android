package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.unit.dp

private fun appNavigationRevealAction() = SemanticsMatcher("Available app navigation action") { node ->
    node.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.CustomActions)
        ?.any { it.label == "Show app navigation" } == true
}

/** Follow the actual account menu rather than restoring old header shortcuts for tests. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeTestRule.revealAppNavigation() {
    val navigation = appNavigationRevealAction()
    if (onAllNodes(navigation).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty())
        onNode(navigation).performCustomAccessibilityActionWithLabel("Show app navigation")
}

internal fun ComposeTestRule.openAccountMenu() {
    val menu = (hasText("Wallet") or hasText("Settings")) and hasClickAction() and hasAnyAncestor(isPopup())
    val settings = hasText("Settings") and menu
    val deadline = android.os.SystemClock.elapsedRealtime() + 5_000
    fun remaining(): Long = (deadline - android.os.SystemClock.elapsedRealtime()).also {
        check(it > 0) { "The actual Account popup must be observed within five seconds" }
    }
    val observations = com.google.gson.JsonArray()
    fun retain(stage: String, visible: Boolean, settled: Boolean, bounds: String?) {
        if (observations.size() < 64) observations.add(com.google.gson.JsonObject().apply {
            addProperty("stage", stage); addProperty("elapsedMillis", android.os.SystemClock.elapsedRealtime())
            addProperty("visible", visible); addProperty("navigationSettled", settled); addProperty("bounds", bounds)
        })
    }
    try {
        // Semantic presence alone does not establish a usable popup.
        // Observe a visible menu or its complete removal before another native tap.
        waitUntil(remaining()) {
            onAllNodes(menu).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty() ||
                onNode(settings).isDisplayed()
        }
        if (onNode(settings).isDisplayed()) {
            check(android.os.SystemClock.elapsedRealtime() <= deadline)
            return
        }
        revealAppNavigation()
        val account = onNode(hasContentDescription("Account") and hasClickAction() and isEnabled())
        var previous: androidx.compose.ui.unit.DpRect? = null
        waitUntil(remaining()) {
            val settled = onAllNodes(appNavigationRevealAction()).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
            val visible = account.isDisplayed()
            if (!settled || !visible) {
                previous = null; retain("revealing", visible, settled, null); false
            } else {
                val bounds = account.getUnclippedBoundsInRoot()
                val node = account.fetchSemanticsNode()
                val clipped = node.boundsInRoot
                val complete = clipped.width == node.size.width.toFloat() &&
                    clipped.height == node.size.height.toFloat() &&
                    bounds.right - bounds.left >= 48.dp && bounds.bottom - bounds.top >= 48.dp
                val stable = complete && previous == bounds
                previous = if (complete) bounds else null
                retain("ready", visible, settled, bounds.toString())
                stable
            }
        }
        account.assertIsDisplayed().assertIsEnabled().performClick()
        waitUntil(remaining()) { onNode(settings).isDisplayed() }
        check(android.os.SystemClock.elapsedRealtime() <= deadline)
    } catch (failure: Throwable) {
        try {
            retain("failed", onNode(settings).isDisplayed(),
                onAllNodes(appNavigationRevealAction()).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty(),
                runCatching { onNodeWithContentDescription("Account").getUnclippedBoundsInRoot().toString() }.getOrNull())
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendStatus(0,
                android.os.Bundle().apply { putString("stream", "\nAccount activation observations: $observations\n") })
        } catch (_: Throwable) { /* Preserve the original failure if diagnostics cannot be collected. */ }
        throw failure
    }
}

internal fun ComposeTestRule.openWallet() {
    openAccountMenu()
    onNode(hasText("Wallet") and hasClickAction() and hasAnyAncestor(isPopup())).assertIsDisplayed().assertIsEnabled().performClick()
}

internal fun ComposeTestRule.openSettings() {
    openAccountMenu()
    onNode(hasText("Settings") and hasClickAction() and hasAnyAncestor(isPopup())).assertIsDisplayed().assertIsEnabled().performClick()
}

/** Retain the caller's original first-use/keyboard readiness deadline. */
internal fun ComposeTestRule.waitForAccountMenu(timeoutMillis: Long) {
    waitUntil(timeoutMillis) { onNode(hasContentDescription("Account") and hasClickAction() and isEnabled()).isDisplayed() }
}
