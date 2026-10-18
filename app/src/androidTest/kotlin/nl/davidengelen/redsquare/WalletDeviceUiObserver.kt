package nl.davidengelen.redsquare

import android.os.Build
import android.os.Bundle
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
                if (application.endsWith(".dev")) " --ez feedDisabled true" else "")
            // UiObject2 resolves the live object's bounds at click time, rather than using
            // coordinates from an earlier host-side hierarchy dump during startup.
            var action = device.wait(Until.findObject(By.pkg(application).text("Wallet").enabled(true)), 1_000)
            repeat(19) {
                if (action == null) {
                    // Dismiss only the explicitly identified emulator System UI dialog.
                    // An app ANR remains a failure.
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
