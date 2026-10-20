package nl.davidengelen.redsquare

import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal fun recordNativeTransition(file: File, recordingSeconds: Int = 3, dismiss: () -> Unit): JsonObject {
    require(recordingSeconds in 1..5)
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
        script.writeText("timeout ${recordingSeconds + 3} screenrecord --verbose --time-limit $recordingSeconds $remote 2>&1\nframe_record_exit=\$?\necho REDSQUARE_RECORDER_EXIT=\$frame_record_exit\n")
        automation.executeShellCommand("sh ${script.absolutePath}").use { recording ->
            val ready = java.util.concurrent.CountDownLatch(1)
            val completed = executor.submit<String> {
                FileInputStream(recording.fileDescriptor).bufferedReader().useLines { lines ->
                    buildString { lines.forEach { line ->
                        appendLine(line)
                        if (line.startsWith("Content area is")) ready.countDown()
                    } }
                }
            }
            var recordingFailure: Throwable? = null
            try {
                // Native screenrecord flushes this message after encoder setup, before projection starts.
                check(ready.await(5, TimeUnit.SECONDS)) { "Native recorder did not become ready" }
                SystemClock.sleep(200)
                tapped = SystemClock.elapsedRealtimeNanos(); dismiss(); tapReturned = SystemClock.elapsedRealtimeNanos()
            }
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
internal fun retainNativeMotionFrames(movie: File): JsonObject {
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
        check(timestamps.zipWithNext().all { (left, right) -> right > left }) { "Reordered recording samples need separate timestamp reconciliation" }
        val count = requireNotNull(decoder.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)).toInt()
        check(count == timestamps.size && count >= 2) { "Insufficient or inconsistent native recording frames" }
        val frames = JsonArray()
        // Decode every actual frame of the bounded recording, including a late input transition.
        for (index in timestamps.indices) {
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
