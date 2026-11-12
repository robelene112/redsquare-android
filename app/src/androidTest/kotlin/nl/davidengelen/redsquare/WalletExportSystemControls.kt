package nl.davidengelen.redsquare

import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import java.util.regex.Pattern

// Disposable emulator credential. Never used by a real device or app build.
internal const val EXPORT_TEST_PIN = "246813"

internal fun waitForSystemPin(device: UiDevice) {
    // Compose exposes a bare test-tag resource, unlike the legacy XML controls.
    check(device.wait(Until.hasObject(By.pkg(Pattern.compile("com\\.android\\.(?:settings|systemui)"))
        .res(Pattern.compile(
            "(?:com\\.android\\.settings:id/password_entry|com\\.android\\.systemui:id/lockPassword|cred_pin_pad)"
        ))), 15_000))
}

internal fun cancelRealSystemAuthentication(device: UiDevice, appPackage: String) {
    // Back can first dismiss the system keyboard or another credential layer.
    // Stop as soon as the app returns; never send Back into the app itself.
    val deadline = SystemClock.uptimeMillis() + 10_000
    while (device.currentPackageName != appPackage && SystemClock.uptimeMillis() < deadline) {
        device.pressBack()
        SystemClock.sleep(100)
    }
    check(device.currentPackageName == appPackage)
}

internal fun chooseTestBackup(device: UiDevice, name: String) {
    check(device.wait(Until.hasObject(By.res("android", "title")), 15_000))
    if (device.findObject(By.desc("Show roots")) != null) {
        operateSystemControl(device, By.desc("Show roots")) { click() }
    }
    val rootsDrawer = By.res(Pattern.compile(".*:id/roots_list"))
    clickTestBackupRoot()
    // The selected provider remains in the toolbar after the drawer closes.
    check(device.wait(Until.gone(rootsDrawer), 10_000))
    operateSystemControl(device, By.res("android", "title").clazz("android.widget.EditText")) { text = name }
    operateSystemControl(device, By.res("android", "button1")
        .pkg(Pattern.compile("com\\.(?:android|google\\.android)\\.documentsui")).enabled(true)) { click() }
}

/** Use the real clickable provider row, rather than a coordinate tap on its text leaf. */
private fun clickTestBackupRoot() {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    val deadline = SystemClock.uptimeMillis() + 20_000 // Original provider-control budget.
    fun find(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var remaining = 512
        fun visit(node: AccessibilityNodeInfo, inRoots: Boolean): AccessibilityNodeInfo? {
            if (remaining-- <= 0) return null
            val inside = inRoots || node.viewIdResourceName?.endsWith(":id/roots_list") == true
            val provider = node.packageName?.toString()
            if (inside && node.isVisibleToUser && node.text?.toString() == ExportTestDocumentsProvider.TITLE &&
                node.viewIdResourceName == "android:id/title" &&
                provider in setOf("com.android.documentsui", "com.google.android.documentsui")) {
                var action: AccessibilityNodeInfo? = node
                while (action != null && action.viewIdResourceName?.endsWith(":id/roots_list") != true) {
                    if (action.isVisibleToUser && action.isEnabled && action.isClickable) return action
                    action = action.parent
                }
            }
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                visit(child, inside)?.let { return it }
            }
            return null
        }
        return visit(root, false)
    }
    while (SystemClock.uptimeMillis() < deadline) {
        automation.rootInActiveWindow?.let(::find)?.let { row ->
            check(SystemClock.uptimeMillis() < deadline) { "Provider lookup exceeded its original budget" }
            check(row.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "Provider row rejected native Click" }
            check(SystemClock.uptimeMillis() <= deadline) { "Provider selection exceeded its original budget" }
            return
        }
        SystemClock.sleep(50)
    }
    error("Test backup provider row unavailable within its original budget")
}

internal fun enterRealSystemPin(device: UiDevice, pin: String = EXPORT_TEST_PIN) {
    val credential = By.res(Pattern.compile("(?:com\\.android\\.settings:id/password_entry|com\\.android\\.systemui:id/lockPassword)"))
    val deadline = SystemClock.uptimeMillis() + 15_000
    while (SystemClock.uptimeMillis() < deadline) {
        val entry = device.findObject(credential)
            ?: device.findObject(By.pkg("com.android.systemui").clazz("android.widget.EditText"))
        if (entry != null) { entry.text = pin; device.pressEnter(); return }
        device.findObject(By.text("Use PIN"))?.click()
        if (device.findObject(By.pkg("com.android.systemui").res("cred_pin_pad")) != null) {
            saveCredentialControlMetadata()
            // Observed Android 17 Compose PIN pad, not a legacy EditText.
            // Hardware digits go to its focused credential input.
            pin.forEach { device.pressKeyCode(KeyEvent.KEYCODE_0 + (it - '0')) }
            device.pressEnter()
            return
        }
        SystemClock.sleep(100)
    }
    error("Credential entry unavailable in ${device.currentPackageName}")
}

private fun operateSystemControl(device: UiDevice, selector: BySelector, action: UiObject2.() -> Unit) {
    val deadline = SystemClock.uptimeMillis() + 20_000
    while (SystemClock.uptimeMillis() < deadline) {
        val control = device.wait(Until.findObject(selector), 1_000) ?: continue
        try { control.action(); return }
        catch (_: StaleObjectException) { /* Query a fresh node after system layout changes. */ }
    }
    error("Android system control unavailable")
}

private fun saveCredentialControlMetadata() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val publicControls = StringBuilder()
    var remaining = 500
    fun visit(node: AccessibilityNodeInfo) {
        if (remaining-- <= 0) return
        if (node.packageName?.toString() == "com.android.systemui") {
            publicControls.append(node.className).append(" id=").append(node.viewIdResourceName)
                .append(" editable=").append(node.isEditable).append(" password=").append(node.isPassword)
                .append(" focused=").append(node.isFocused).append('\n')
        }
        for (index in 0 until node.childCount) node.getChild(index)?.let(::visit)
    }
    instrumentation.uiAutomation.rootInActiveWindow?.let(::visit)
    val output = File(instrumentation.targetContext.cacheDir, "wallet-export-evidence/credential-controls.txt")
    output.parentFile!!.mkdirs()
    output.writeText(publicControls.toString())
}

