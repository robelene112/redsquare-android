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
    // The selected root also appears in the toolbar. Select its drawer row.
    val root = By.text(ExportTestDocumentsProvider.TITLE).res("android", "title")
    operateSystemControl(device, root) { click() }
    check(device.wait(Until.gone(root), 10_000))
    operateSystemControl(device, By.res("android", "title").clazz("android.widget.EditText")) { text = name }
    operateSystemControl(device, By.res("android", "button1")
        .pkg(Pattern.compile("com\\.(?:android|google\\.android)\\.documentsui")).enabled(true)) { click() }
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

