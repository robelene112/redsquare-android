package nl.davidengelen.redsquare

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import nl.davidengelen.redsquare.fileshare.*
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Actual Android window/recomposer and native input, without a Compose test-rule replacement. */
class ReceivedFilesMotionTest {
    @Test fun nativeRemovalRespondsAtActualNormalAndZeroWindowMotion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallet = AndroidWalletStore(context).loadOrCreate()
        val contact = SaitoWallet.fromPrivateKey("0".repeat(63) + "1").publicKey
        val retained = ChatFixtureState(context, listOf("fileshare."), wallet.publicKey)
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        var primary: Throwable? = null
        try {
            retained.prepare()
            for (scale in listOf(0, 1)) withNativeModuleMotionScale(scale) {
                val folderName = "received-storage-evidence/motion-$scale"
                val folder = File(context.cacheDir, folderName).apply { mkdirs() }
                withAppFrameFixture("storage-motion-$scale", folderName, "[]", archiveResponse = { "[]" }) { activity ->
                    val store = FileTransferStore(context, wallet.publicKey, SaitoBackend.OWNER, contact)
                    val value = FileTransfer(UUID.randomUUID().toString(), wallet.publicKey, SaitoBackend.OWNER, contact,
                        "ws://127.0.0.1:1/wsopen", Fileshare.Offer("motion130", "Motion report.txt", 5, "text/plain"), true, FilePhase.RECEIVING)
                    store.save(value)
                    store.begin(value).use { it.append(byteArrayOf(1, 2, 3, 4, 5)); it.finish(5) }
                    store.save(value.copy(phase = FilePhase.COMPLETE, bytes = 5))
                    fun click(text: String) = requireNotNull(device.wait(Until.findObject(By.text(text).enabled(true)), 5_000)).click()
                    val later = device.wait(Until.findObject(By.text("Later")), 15_000)
                    later?.click()
                    requireNotNull(device.wait(Until.findObject(By.desc("Account")), 5_000)).click()
                    click("Settings"); click("Storage")
                    check(device.wait(Until.hasObject(By.text("Motion report.txt")), 5_000))
                    observeModuleWindowMotionScale(activity, scale, folder)
                    click("Remove"); click("Cancel")
                    assertEquals(41L, store.retainedBytes(value.token))
                    assertEquals(LocalFileCopy.AVAILABLE, store.load().single { it.token == value.token }.localCopy)
                    val movie = File(folder, "confirmed-removal.mp4")
                    val recording = recordNativeTransition(movie, recordingSeconds = 5) {
                        click("Remove"); click("Remove copy")
                        check(device.wait(Until.hasObject(By.text("No received files stored")), 5_000))
                    }
                    val frames = retainNativeMotionFrames(movie)
                    frames.add("recording", recording)
                    File(folder, "confirmed-removal-frames.json").writeText(frames.toString())
                    assertEquals(0L, store.retainedBytes(value.token))
                    assertEquals(LocalFileCopy.REMOVED, store.load().single { it.token == value.token }.localCopy)
                    assertEquals(FilePhase.COMPLETE, store.load().single { it.token == value.token }.phase)
                    captureNativeScreen(File(folder, "removed-at-actual-window-scale.png"))
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally { cleanupAfterTest(primary, { retained.restoreFiles() }, { retained.restorePreferences() }) }
    }
}
