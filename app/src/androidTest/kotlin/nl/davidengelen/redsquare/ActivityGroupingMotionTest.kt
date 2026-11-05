package nl.davidengelen.redsquare

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Actual automatic group replacement and People windows, at real normal/zero system motion. */
class ActivityGroupingMotionTest {
    @Test fun aReadGroupResurfacesAndPeopleOpenAndCloseAtNormalAndZeroMotion() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val folder = File(context.cacheDir, "activity-group-motion-evidence").apply { mkdirs() }
        val reference = instrumentation.context.assets.open("native-notification-reference.json").reader().use {
            JsonParser.parseReader(it).asJsonObject
        }
        for (scale in listOf(1, 0)) withNativeModuleMotionScale(scale) {
            val additional = AtomicReference<List<JsonObject>>(emptyList())
            val gate = AtomicReference<CountDownLatch?>(null)
            val entered = CountDownLatch(1)
            withActivityInbox("motion-$scale", listOf("incomingLike", "incomingReply"),
                evidenceFolder = "activity-group-motion-evidence", additionalEvents = { additional.get() },
                onArchiveRead = { query ->
                    if (query.get("field1")?.asString == "RedSquareLike" && query.has("created_earlier_than"))
                        gate.get()?.let { entered.countDown(); check(it.await(10, TimeUnit.SECONDS)) }
                }) { activity, _ ->
                try {
                    requireNotNull(device.wait(Until.findObject(By.text("Notifications")), 15_000)).click()
                    check(device.wait(Until.hasObject(By.text("2 unread")), 15_000))
                    observeModuleWindowMotionScale(activity, scale, folder)
                    requireNotNull(device.findObject(By.text("Mark all as read"))).click()
                    check(device.wait(Until.hasObject(By.text("No unread activity")), 15_000))
                    val pending = CountDownLatch(1)
                    gate.set(pending)
                    additional.set(listOf(signedActivityLike(reference, 1_700_000_010_000L)))
                    check(entered.await(15, TimeUnit.SECONDS))
                    val movie = File(folder, "scale-$scale-resurface.mp4")
                    val recording = recordNativeTransition(movie, recordingSeconds = 5) {
                        gate.set(null); pending.countDown()
                        check(device.wait(Until.hasObject(By.text("1 unread")), 5_000))
                        check(device.wait(Until.findObject(By.text("Liked your post")), 5_000) != null)
                    }
                    val like = requireNotNull(device.findObject(By.text("Liked your post")))
                    val reply = requireNotNull(device.findObject(By.text("Replied to your post")))
                    assertTrue("New group is automatically first", like.visibleBounds.top < reply.visibleBounds.top)
                    val frames = retainNativeMotionFrames(movie)
                    frames.add("recording", recording)
                    File(folder, "scale-$scale-resurface.json").writeText(frames.toString())
                    captureNativeScreen(File(folder, "scale-$scale-resurfaced.png"), "1 unread")
                    val peopleMovie = File(folder, "scale-$scale-people.mp4")
                    val peopleRecording = recordNativeTransition(peopleMovie, recordingSeconds = 5) {
                        requireNotNull(device.findObjects(By.desc("Activity options for Anon-pncSjs")).firstOrNull()).click()
                        requireNotNull(device.wait(Until.findObject(By.text("People who liked this post")), 5_000)).click()
                        check(device.wait(Until.hasObject(By.text("2 likes from 1 person")), 5_000))
                        captureNativeScreen(File(folder, "scale-$scale-people-open.png"), "2 likes from 1 person")
                        SystemClock.sleep(200)
                        requireNotNull(device.findObject(By.text("Done"))).click()
                        check(device.wait(Until.gone(By.text("2 likes from 1 person")), 5_000))
                    }
                    val peopleFrames = retainNativeMotionFrames(peopleMovie)
                    peopleFrames.add("recording", peopleRecording)
                    File(folder, "scale-$scale-people.json").writeText(peopleFrames.toString())
                    captureNativeScreen(File(folder, "scale-$scale-people-closed.png"), "1 unread")
                } finally { gate.get()?.countDown() }
            }
        }
    }
}
