package nl.davidengelen.redsquare

import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

/** Uses Android's real window recomposer; a Compose rule would replace its motion scale. */
class AppFrameMotionPreviewTest {
    @Test fun normalSystemMotionKeepsDismissalAndRestoredNavigationUsable() = verifyDismissal(1)
    @Test fun disabledSystemMotionKeepsDismissalAndRestoredNavigationUsable() = verifyDismissal(0)

    private fun verifyDismissal(scale: Int) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
        val device = UiDevice.getInstance(instrumentation)
        val original = device.executeShellCommand("settings get global animator_duration_scale").trim()
        check(original == "null" || original.toFloatOrNull()?.let { it.isFinite() && it >= 0 } == true)
        val folder = File(context.cacheDir, "app-frame-motion-evidence").apply { mkdirs() }
        val name = "motion-$scale"
        var primary: Throwable? = null
        try {
            device.executeShellCommand("settings put global animator_duration_scale $scale")
            check(device.executeShellCommand("settings get global animator_duration_scale").trim().toFloat() == scale.toFloat())
            withAppFrameFixture(name, "app-frame-motion-evidence") { activity ->
                check(device.wait(Until.hasObject(By.text("Native Red Square reference post")), 15_000))
                check(device.wait(Until.hasObject(By.text("Later")), 10_000))
                val before = device.findObject(By.text("Native Red Square reference post")).visibleBounds
                val later = device.findObject(By.text("Later")).visibleBounds
                captureNativeScreen(File(folder, "$name-before.png"))
                val movie = File(folder, "$name.mp4")
                val recording = recordDismissal(movie) { check(device.click(later.centerX(), later.centerY())) }
                check(device.wait(Until.gone(By.text("Back up your wallet")), 10_000))
                val after = device.findObject(By.text("Native Red Square reference post")).visibleBounds
                captureNativeScreen(File(folder, "$name-after.png"))
                assertTrue("Dismissing backup guidance must recover reading space", before.top > after.top + 24)
                val observation = retainFrames(movie)
                observation.add("recording", recording)
                val observedScale = device.executeShellCommand("settings get global animator_duration_scale").trim()
                check(observedScale.toFloat() == scale.toFloat())
                observation.addProperty("observedAnimatorScale", observedScale)
                observation.addProperty("animatorScale", scale)
                observation.addProperty("originalSetting", original)
                observation.addProperty("beforePostBounds", before.toShortString())
                observation.addProperty("afterPostBounds", after.toShortString())
                observation.addProperty("acceptance", "Native endpoints/persistence only. Motion requires inspection of the original movie and actual decoded frames.")
                File(folder, "$name.json").writeText(observation.toString())
                activity.recreate()
                check(device.wait(Until.hasObject(By.text("Native Red Square reference post")), 15_000))
                assertFalse(device.hasObject(By.text("Back up your wallet")))
                device.findObject(By.text("Native Red Square reference post")).click()
                check(device.wait(Until.hasObject(By.text("Conversation")), 10_000))
                device.pressBack()
                check(device.wait(Until.hasObject(By.text("Native Red Square reference post")), 10_000))
                assertFalse(device.hasObject(By.text("Back up your wallet")))
                captureNativeScreen(File(folder, "$name-persisted-after-navigation.png"))
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, {
                device.executeShellCommand(if (original == "null") "settings delete global animator_duration_scale"
                    else "settings put global animator_duration_scale $original")
                check(device.executeShellCommand("settings get global animator_duration_scale").trim() == original)
            })
        }
    }

    private fun recordDismissal(file: File, dismiss: () -> Unit): JsonObject {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val remote = "/data/local/tmp/redsquare-${java.util.UUID.randomUUID()}.mp4"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val script = File(requireNotNull(context.externalCacheDir), "frame-motion-${java.util.UUID.randomUUID()}.sh")
        val executor = Executors.newSingleThreadExecutor()
        var primary: Throwable? = null
        val started = SystemClock.elapsedRealtimeNanos()
        var tapped = 0L
        var tapReturned = 0L
        try {
            // UiAutomation starts a process directly. Execute an owned script so shell status is evaluated as shell code.
            script.writeText("timeout 6 screenrecord --time-limit 3 $remote 2>&1\nframe_record_exit=\$?\necho REDSQUARE_RECORDER_EXIT=\$frame_record_exit\n")
            automation.executeShellCommand("sh ${script.absolutePath}").use { recording ->
                val completed = executor.submit<String> {
                    FileInputStream(recording.fileDescriptor).bufferedReader().use { it.readText() }
                }
                // Establish an initial segment; decoded initial/final geometry must independently confirm capture coverage.
                SystemClock.sleep(400)
                var recordingFailure: Throwable? = null
                try { tapped = SystemClock.elapsedRealtimeNanos(); dismiss(); tapReturned = SystemClock.elapsedRealtimeNanos() }
                catch (failure: Throwable) { recordingFailure = failure; throw failure }
                finally {
                    cleanupAfterTest(recordingFailure, {
                        val output = completed.get(10, TimeUnit.SECONDS)
                        File(file.parentFile, "${file.nameWithoutExtension}-recorder.txt").writeText(output)
                        check(output.lineSequence().any { it == "REDSQUARE_RECORDER_EXIT=0" }) { "Native recorder failed" }
                    })
                }
            }
            automation.executeShellCommand("cat $remote").use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { input -> file.outputStream().use { output -> input.copyTo(output) } }
            }
            check(file.length() > 0)
            return JsonObject().apply {
                addProperty("startElapsedNanos", started); addProperty("tapElapsedNanos", tapped)
                addProperty("tapReturnedElapsedNanos", tapReturned)
                addProperty("endElapsedNanos", SystemClock.elapsedRealtimeNanos())
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, { executor.shutdownNow() }, { check(!script.exists() || script.delete()) }, {
                automation.executeShellCommand("rm -f $remote").use { descriptor ->
                    FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
                }
            })
        }
    }

    /** Full original decoded display frames and actual sample times, for independent visual motion review. */
    private fun retainFrames(movie: File): JsonObject {
        val decoder = MediaMetadataRetriever()
        val extractor = MediaExtractor()
        var primary: Throwable? = null
        try {
            decoder.setDataSource(movie.path)
            extractor.setDataSource(movie.path)
            val videoTrack = (0 until extractor.trackCount).single { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            }
            extractor.selectTrack(videoTrack)
            val timestamps = mutableListOf<Long>()
            while (extractor.sampleTime >= 0) { timestamps.add(extractor.sampleTime); extractor.advance() }
            val count = requireNotNull(decoder.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)).toInt()
            check(count == timestamps.size && count >= 2) { "Insufficient or inconsistent native recording frames" }
            val frames = JsonArray()
            // Keep the whole recording; decode the opening second plus endpoints without altering frame pixels.
            for (index in timestamps.indices.filter { timestamps[it] <= 1_000_000 || it == count - 1 }) {
                val frame = requireNotNull(decoder.getFrameAtIndex(index))
                try {
                    check(frame.width == UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).displayWidth)
                    val name = "${movie.nameWithoutExtension}-frame-$index.png"
                    File(movie.parentFile, name).outputStream().use { check(frame.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    frames.add(JsonObject().apply {
                        addProperty("index", index); addProperty("presentationTimeUs", timestamps[index]); addProperty("file", name)
                    })
                } finally { frame.recycle() }
            }
            return JsonObject().apply { addProperty("frameCount", count); add("decodedOriginalFrames", frames) }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally { cleanupAfterTest(primary, { decoder.release() }, { extractor.release() }) }
    }
}
