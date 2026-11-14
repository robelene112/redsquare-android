package nl.davidengelen.redsquare

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Real window recomposer and system motion, without a Compose test-clock replacement. */
class VaultRentalMotionPreviewTest {
    @Test fun nativeRentalDetailsOpenAndCloseAtNormalAndReducedMotion() {
        val device = androidx.test.uiautomator.UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
        for (scale in listOf(0, 1)) withNativeModuleMotionScale(scale) {
            withVaultReadingFixture("rental-motion-$scale", creatorRental = true) { scene ->
                val later = requireNotNull(device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.text("Later")), 15_000))
                later.click()
                requireNotNull(device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.desc("Switch app: Red Square")), 5_000)).click()
                val vault = requireNotNull(device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.text("Vault")), 5_000))
                vault.click()
                check(device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.text("Upload notes.txt")), 15_000))
                observeModuleWindowMotionScale(scene.activity, scale, scene.evidence)
                fun movie(name: String, action: () -> Unit) {
                    val file = File(scene.evidence, "rental-scale-$scale-$name.mp4")
                    val recording = recordNativeTransition(file, recordingSeconds = 5, dismiss = action)
                    val observed = retainNativeMotionFrames(file)
                    observed.add("recording", recording)
                    File(scene.evidence, "rental-scale-$scale-$name.json").writeText(observed.toString())
                }
                movie("open") {
                    requireNotNull(device.findObject(androidx.test.uiautomator.By.text("Rental details"))).click()
                    check(device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.text("Done")), 5_000))
                }
                movie("close") {
                    requireNotNull(device.findObject(androidx.test.uiautomator.By.text("Done"))).click()
                    check(device.wait(androidx.test.uiautomator.Until.gone(androidx.test.uiautomator.By.text("Done")), 5_000))
                }
                check(device.hasObject(androidx.test.uiautomator.By.text("Rental details")))
                assertTrue(scene.peer.transactions.isEmpty()); assertTrue(scene.peer.vaultUploads.isEmpty())
            }
        }
    }

}
