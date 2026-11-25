package nl.davidengelen.redsquare

import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Test
import java.io.File

/** Observe only the public wallet UI of known app packages on a disposable emulator. */
class WalletDeviceUiObserver {
    @Test fun openTheCurrentPublicWalletAddress() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        check(instrumentation.targetContext.packageName == "nl.davidengelen.redsquare.dev" &&
            Build.HARDWARE in listOf("ranchu", "goldfish"))
        val application = requireNotNull(InstrumentationRegistry.getArguments().getString("walletApplication"))
        check(application in listOf("nl.davidengelen.redsquare", "nl.davidengelen.redsquare.dev", "nl.davidengelen.redsquare.performance"))
        val device = UiDevice.getInstance(instrumentation)
        val capture = File(instrumentation.targetContext.cacheDir, "wallet-device-observation/screen.png")
        withFailureCapture({ captureNativeScreen(capture) }) {
            device.executeShellCommand("am start -W -n $application/nl.davidengelen.redsquare.MainActivity" +
                if (application.endsWith(".dev")) " --ez feedDisabled true --es balanceApiUrl http://127.0.0.1:1/balance/" else "")
            // Use the currently visible named accessibility action itself.
            // An older beta can insert its update banner and move the Wallet
            // button between hierarchy lookup and delivery of a screen tap.
            fun activate(node: AccessibilityNodeInfo, label: String, description: Boolean): Boolean {
                if (node.packageName?.toString() != application || !node.isVisibleToUser) return false
                val actual = if (description) node.contentDescription else node.text
                if (actual?.toString() == label) {
                    val target = generateSequence(node) { it.parent }.firstOrNull { it.isClickable && it.isEnabled }
                    return target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                }
                return (0 until node.childCount).any { node.getChild(it)?.let { child -> activate(child, label, description) } == true }
            }
            fun activate(label: String, description: Boolean = false): Boolean =
                instrumentation.uiAutomation.rootInActiveWindow?.let { activate(it, label, description) } == true

            var opened = false
            repeat(20) {
                if (opened) return@repeat
                if (device.hasObject(By.text("System UI isn't responding"))) device.findObject(By.text("Wait"))?.click()
                opened = activate("Wallet")
                if (!opened) {
                    activate("Account", description = true)
                    device.wait(Until.hasObject(By.pkg(application).text("Wallet").enabled(true)), 1_000)
                    opened = activate("Wallet")
                }
            }
            check(opened) { "The native Wallet action could not be opened" }
            val address = requireNotNull(device.wait(Until.findObject(By.pkg(application).desc("Wallet address")), 20_000)).text
            check(address != null && address.matches(Regex("[1-9A-HJ-NP-Za-km-z]{44}")))
            captureNativeScreen(capture)
            instrumentation.sendStatus(0, Bundle().apply { putString("walletPublicAddress", address) })
        }
    }
}
