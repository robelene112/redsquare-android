package nl.davidengelen.redsquare

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File

/** Separate screen observations, not an atomic screenshot/hierarchy pair. */
fun captureNativeScreen(file: File, expectedText: String? = null) {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    expectedText?.let { check(device.wait(Until.hasObject(By.text(it)), 10_000)) }
    file.parentFile?.mkdirs()
    device.dumpWindowHierarchy(File(file.parentFile, file.nameWithoutExtension + ".xml"))
    check(device.takeScreenshot(file))
}

fun withFailureCapture(capture: () -> Unit, block: () -> Unit) {
    try { block() }
    catch (failure: Throwable) {
        try { capture() }
        catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
        throw failure
    }
}

// [DEBUG-87-a11y] Temporary public-platform probe; remove after the native mismatch is diagnosed.
internal fun accessibilityTargetSnapshot(automation: android.app.UiAutomation): String = buildString {
    val owner = InstrumentationRegistry.getInstrumentation().targetContext.packageName
    val labels = setOf("Account", "Switch app: Red Square", "Red Square", "Wallet", "Settings")
    appendLine("[DEBUG-87-a11y] globalBefore=" + automation.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_ACCESSIBILITY))
    fun describe(node: android.view.accessibility.AccessibilityNodeInfo): String {
        val bounds = android.graphics.Rect().also(node::getBoundsInScreen)
        return "window=${node.windowId} text=${node.text} description=${node.contentDescription} visible=${node.isVisibleToUser} inputFocus=${node.isFocused} a11yFocus=${node.isAccessibilityFocused} clickable=${node.isClickable} bounds=$bounds"
    }
    fun walk(node: android.view.accessibility.AccessibilityNodeInfo) {
        if (node.text?.toString() in labels || node.contentDescription?.toString() in labels || node.isAccessibilityFocused) {
            appendLine("before " + describe(node))
            appendLine("refresh=${node.refresh()} after " + describe(node))
            node.parent?.let { parent ->
                appendLine("parentBefore " + describe(parent))
                appendLine("parentRefresh=${parent.refresh()} parentAfter " + describe(parent))
            }
        }
        for (index in 0 until node.childCount) node.getChild(index)?.let(::walk)
    }
    automation.windows.forEach { window ->
        val root = window.root
        val bounds = android.graphics.Rect().also(window::getBoundsInScreen)
        appendLine("window id=${window.id} active=${window.isActive} focused=${window.isFocused} bounds=$bounds package=${root?.packageName}")
        if (root?.packageName?.toString() == owner) {
            appendLine("rootBefore " + describe(root))
            appendLine("rootRefresh=${root.refresh()} rootAfter " + describe(root))
            appendLine("rootFindFocus=" + root.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_ACCESSIBILITY))
            walk(root)
        }
    }
    appendLine("globalAfter=" + automation.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_ACCESSIBILITY))
}
