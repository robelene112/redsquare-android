package nl.davidengelen.redsquare

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Native windows and category replacements follow real normal/zero system motion. */
class AccountDesignMotionTest {
    @Test fun accountTasksAndCategoriesRemainInterruptibleAndRetainTheActiveIdentity() {
        for (scale in listOf(0, 1)) withNativeModuleMotionScale(scale) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val device = UiDevice.getInstance(instrumentation)
            val folder = File(context.cacheDir, "account-design-motion-evidence").apply { mkdirs() }
            fun click(text: String) = requireNotNull(device.wait(Until.findObject(By.text(text)), 5_000)).click()
            fun movie(name: String, action: () -> Unit) {
                val file = File(folder, "scale-$scale-$name.mp4")
                val recording = recordNativeTransition(file, recordingSeconds = 5, dismiss = action)
                val frames = retainNativeMotionFrames(file)
                frames.add("recording", recording)
                File(folder, "scale-$scale-$name.json").writeText(frames.toString())
            }
            withAppFrameFixture("motion-$scale", "account-design-motion-evidence") { activity ->
                check(device.wait(Until.hasObject(By.text("Native Red Square reference post")), 15_000))
                click("Later")
                requireNotNull(device.wait(Until.findObject(By.desc("Account")), 5_000)).click()
                movie("settings-open") { click("Settings"); check(device.wait(Until.hasObject(By.text("Privacy and safety")), 5_000)) }
                movie("categories") {
                    click("Appearance")
                    check(device.wait(Until.hasObject(By.text("System default")), 5_000))
                    click("Back to Settings")
                    click("Node")
                    check(device.wait(Until.hasObject(By.text("saito.davidengelen.nl")), 5_000))
                }
                captureNativeScreen(File(folder, "scale-$scale-node.png"))
                click("Close settings")
                requireNotNull(device.wait(Until.findObject(By.desc("Account")), 5_000)).click()
                movie("wallet-open") { click("Wallet"); check(device.wait(Until.hasObject(By.text("Your wallet")), 5_000)) }
                captureNativeScreen(File(folder, "scale-$scale-wallet.png"))
                movie("wallet-return") {
                    click("Close wallet")
                    check(device.wait(Until.hasObject(By.text("Native Red Square reference post")), 5_000))
                }
                assertFalse(device.hasObject(By.text("Your wallet")))
                observeModuleWindowMotionScale(activity, scale, folder)
            }
        }
    }
}
