package nl.davidengelen.redsquare

import android.os.Build
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject
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
        fun dismissKnownLauncherDialog() {
            // API29's system launcher can show its own ANR over a healthy target.
            // Never dismiss a target-app ANR or an arbitrary system dialog.
            if (Build.VERSION.SDK_INT != 29) return
            val root = instrumentation.uiAutomation.rootInActiveWindow ?: return
            if (root.packageName?.toString() != "android") return
            fun nodes(node: AccessibilityNodeInfo): Sequence<AccessibilityNodeInfo> = sequence {
                yield(node)
                for (index in 0 until node.childCount) node.getChild(index)?.let { yieldAll(nodes(it)) }
            }
            val contents = nodes(root).toList()
            if (contents.none { it.packageName?.toString() == "android" &&
                    it.text?.toString() == "Quickstep isn't responding" }) return
            val close = requireNotNull(contents.singleOrNull { it.packageName?.toString() == "android" &&
                it.text?.toString() == "Close app" && it.isEnabled && it.isClickable })
            val folder = File(instrumentation.targetContext.cacheDir, "review-update-observation").apply { mkdirs() }
            captureNativeScreen(File(folder, "known-launcher-anr.png"))
            check(close.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "The known launcher dialog must accept Close app" }
            val observation = JSONObject().put("sdk", 29).put("systemPackage", "android")
                .put("title", "Quickstep isn't responding").put("action", "Close app")
                .put("elapsedMillis", android.os.SystemClock.elapsedRealtime())
            File(folder, "known-launcher-anr.json").writeText(observation.toString(2))
            instrumentation.sendStatus(0, Bundle().apply { putString("walletSystemInterference", observation.toString()) })
        }
        withFailureCapture({ captureNativeScreen(capture) }) {
            device.executeShellCommand("am start -W -n $application/nl.davidengelen.redsquare.MainActivity" +
                if (application.endsWith(".dev")) " --ez feedDisabled true --es balanceApiUrl http://127.0.0.1:1/balance/" else "")
            // UiObject2 resolves the live object's bounds at click time, rather than using
            // coordinates from an earlier host-side hierarchy dump during startup.
            var action: androidx.test.uiautomator.UiObject2? = null
            repeat(20) {
                if (action == null) {
                    dismissKnownLauncherDialog()
                    // Legacy signed previews have a direct Wallet control. Newer
                    // builds expose the same public address through Account.
                    try {
                        device.findObject(By.pkg(application).desc("Account").enabled(true))?.click()
                    } catch (_: StaleObjectException) {
                        // Startup can replace the accessibility node before its click.
                        // Re-query in the existing bounded loop, never reuse that node.
                    }
                    if (device.hasObject(By.text("System UI isn't responding")))
                        device.findObject(By.text("Wait"))?.click()
                    action = device.wait(Until.findObject(By.pkg(application).text("Wallet").enabled(true)), 1_000)
                }
            }
            requireNotNull(action).click()
            val address = requireNotNull(device.wait(Until.findObject(By.pkg(application).desc("Wallet address")), 20_000)).text
            check(address != null && address.matches(Regex("[1-9A-HJ-NP-Za-km-z]{44}")))
            captureNativeScreen(capture)
            instrumentation.sendStatus(0, Bundle().apply { putString("walletPublicAddress", address) })
        }
    }
}
