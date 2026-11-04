package nl.davidengelen.redsquare

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Real windows, physical pull and original normal/zero motion frames; no Compose test clock. */
class ConversationHeaderMotionTest {
    @Test fun sharedThreadDialogsAndRefreshStayReadableAtNormalAndZeroMotion() {
        for (scale in listOf(1, 0)) withNativeModuleMotionScale(scale) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val device = UiDevice.getInstance(instrumentation)
            val folder = File(context.cacheDir, "conversation-motion-evidence").apply { mkdirs() }
            val appearance = context.getSharedPreferences("appearance", 0)
            val original = appearance.getString("mode", null)
            val gate = AtomicReference<CountDownLatch?>(null)
            val rows = instrumentation.context.assets.open("native-feed-reference.json").reader().use {
                com.google.gson.JsonParser.parseReader(it).asJsonObject.get("archiveRow").let { "[$it]" }
            }
            var primary: Throwable? = null
            try {
                check(appearance.edit().putString("mode", if (scale == 1) "DARK" else "LIGHT").commit())
                withAppFrameFixture("motion-$scale", "conversation-motion-evidence", rows, archiveResponse = { query ->
                    if (query.has("field5")) gate.get()?.let { check(it.await(10, TimeUnit.SECONDS)) }
                    rows
                }) { activity ->
                    try {
                        check(device.wait(Until.hasObject(By.text("Later")), 15_000))
                        device.findObject(By.text("Later")).click()
                        requireNotNull(device.wait(Until.findObject(By.text("Native Red Square reference post")), 15_000)).click()
                        check(device.wait(Until.hasObject(By.text("Conversation")), 15_000))
                        check(device.wait(Until.gone(By.text("Loading replies")), 15_000))
                        observeModuleWindowMotionScale(activity, scale, folder)
                        val references = linkedMapOf<String, Pair<Rect, Bitmap>>()
                        fun retainText(name: String, bounds: Rect) {
                            val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                            references[name] = bounds to image
                            File(folder, "scale-$scale-$name-reference.png").outputStream().use {
                                check(image.compress(Bitmap.CompressFormat.PNG, 100, it))
                            }
                        }
                        val movie = File(folder, "scale-$scale-thread.mp4")
                        try {
                            val recording = recordNativeTransition(movie, recordingSeconds = 5) {
                                requireNotNull(device.findObject(By.text("Conversation information"))).click()
                                val title = requireNotNull(device.wait(Until.findObject(By.text("Available history")), 5_000))
                                check(device.hasObject(By.text(context.getString(R.string.conversation_history))))
                                retainText("dialog", title.visibleBounds)
                                SystemClock.sleep(160)
                                requireNotNull(device.findObject(By.text("Done"))).click()
                                check(device.wait(Until.gone(By.text("Available history")), 5_000))
                                val pending = CountDownLatch(1)
                                gate.set(pending)
                                val body = requireNotNull(device.findObject(By.text("Native Red Square reference post"))).visibleBounds
                                val navigation = requireNotNull(device.findObject(By.text("Home"))).visibleBounds
                                check(navigation.top - 50 > body.top + 80)
                                check(device.swipe(body.centerX(), body.top + 4, body.centerX(), navigation.top - 50, 30))
                                val loading = requireNotNull(device.wait(Until.findObject(By.text("Loading replies")), 5_000))
                                retainText("loading", loading.visibleBounds)
                                pending.countDown()
                                val success = requireNotNull(device.wait(Until.findObject(By.text("Conversation updated.")), 5_000))
                                retainText("success", success.visibleBounds)
                            }
                            check(device.hasObject(By.text("Conversation")))
                            check(device.hasObject(By.text("Native Red Square reference post")))
                            val frames = retainNativeMotionFrames(movie)
                            val matchedFrames = references.keys.associateWith { 0 }.toMutableMap()
                            var lastShowsDialog = false
                            for (entry in frames.getAsJsonArray("decodedOriginalFrames")) {
                                val value = entry.asJsonObject
                                val frame = requireNotNull(BitmapFactory.decodeFile(File(folder, value.get("file").asString).path))
                                try {
                                    references.forEach { (name, reference) ->
                                        val shown = matchesText(reference.second, frame, reference.first)
                                        if (shown) matchedFrames[name] = matchedFrames.getValue(name) + 1
                                        if (name == "dialog") lastShowsDialog = shown
                                        value.addProperty("showsOriginal-$name", shown)
                                    }
                                } finally { frame.recycle() }
                            }
                            matchedFrames.forEach { (name, count) ->
                                assertTrue("The original recording contains the actual $name state", count > 0)
                            }
                            assertFalse("The original recording contains the return to reading", lastShowsDialog)
                            frames.add("recording", recording)
                            val observations = com.google.gson.JsonObject()
                            references.forEach { (name, reference) -> observations.add(name, com.google.gson.JsonObject().apply {
                                addProperty("bounds", reference.first.toShortString())
                                addProperty("matchingOriginalFrames", matchedFrames.getValue(name))
                            }) }
                            frames.add("nativeTextObservations", observations)
                            frames.addProperty("appearance", if (scale == 1) "DARK" else "LIGHT")
                            File(folder, "scale-$scale-thread.json").writeText(frames.toString())
                            captureNativeScreen(File(folder, "scale-$scale-returned.png"))
                        } finally { references.values.forEach { it.second.recycle() } }
                    } finally { gate.get()?.countDown() }
                }
            } catch (failure: Throwable) { primary = failure; throw failure }
            finally { cleanupAfterTest(primary, {
                check(appearance.edit().apply { if (original == null) remove("mode") else putString("mode", original) }.commit())
            }) }
        }
    }

    private fun matchesText(template: Bitmap, image: Bitmap, region: Rect): Boolean {
        var foreground = 0
        var matching = 0
        // Compare the actual text's contrasting pixels, not the dialog's mostly empty background.
        val background = template.getPixel(region.left, region.top)
        for (y in region.top until region.bottom) for (x in region.left until region.right) {
            val expected = template.getPixel(x, y)
            fun distance(a: Int, b: Int) = maxOf(kotlin.math.abs(Color.red(a) - Color.red(b)),
                kotlin.math.abs(Color.green(a) - Color.green(b)), kotlin.math.abs(Color.blue(a) - Color.blue(b)))
            if (distance(expected, background) > 90) {
                foreground++
                if (distance(expected, image.getPixel(x, y)) < 35) matching++
            }
        }
        check(foreground > 30)
        return matching.toDouble() / foreground > 0.8
    }
}
