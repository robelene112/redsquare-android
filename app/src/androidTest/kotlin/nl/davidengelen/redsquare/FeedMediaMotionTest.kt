package nl.davidengelen.redsquare

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.gson.JsonObject
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual platform motion and original display frames, with no Compose test clock. */
class FeedMediaMotionTest {
    @Test fun deliveredPreviewPixelsReplaceLoadingAtNormalAndZeroSystemMotion() {
        for (scale in listOf(0, 1)) withNativeModuleMotionScale(scale) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val device = UiDevice.getInstance(instrumentation)
            val folder = File(context.cacheDir, "feed-media-motion-evidence").apply { mkdirs() }
            val requested = CountDownLatch(1)
            val release = CountDownLatch(1)
            MockWebServer().apply {
                dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        check(request.url.encodedPath == "/cover.png")
                        requested.countDown()
                        check(release.await(10, TimeUnit.SECONDS))
                        return MockResponse.Builder().body(Buffer().write(linkPreviewPng())).build()
                    }
                }
                start(InetAddress.getByName("127.0.0.1"), 0)
            }.use { media ->
                val metadata = JsonObject().apply { add("link_properties", JsonObject().apply {
                    addProperty("og:title", "A delivered thumbnail")
                    addProperty("og:image", media.url("/cover.png").toString())
                }) }
                val row = linkedFeedRow("Motion thumbnail proof", metadata = metadata.toString())
                withAppFrameFixture("media-motion-$scale", "feed-media-motion-evidence", "[$row]") { activity ->
                    try {
                        check(device.wait(Until.hasObject(By.text("Later")), 15_000))
                        device.findObject(By.text("Later")).click()
                        check(requested.await(5, TimeUnit.SECONDS))
                        val loading = requireNotNull(device.wait(Until.findObject(By.text(context.getString(R.string.image_loading))), 5_000))
                        val point = loading.visibleBounds
                        observeModuleWindowMotionScale(activity, scale, folder)
                        val samples = com.google.gson.JsonArray()
                        val movie = File(folder, "scale-$scale-thumbnail.mp4")
                        val recording = recordNativeTransition(movie, recordingSeconds = 4) {
                            release.countDown()
                            val deadline = SystemClock.elapsedRealtime() + 10_000
                            var stable = 0
                            while (stable < 2 && SystemClock.elapsedRealtime() < deadline) {
                                val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                                val pixel = try { screenshot.getPixel(point.centerX(), point.centerY()) } finally { screenshot.recycle() }
                                val match = Color.red(pixel) > 235 && Color.green(pixel) < 20 && Color.blue(pixel) > 235
                                samples.add(JsonObject().apply {
                                    addProperty("elapsedMillis", SystemClock.elapsedRealtime()); addProperty("argb", pixel); addProperty("matches", match)
                                })
                                stable = if (match) stable + 1 else 0
                                if (stable < 2) SystemClock.sleep(40)
                            }
                            assertEquals("The real delivered image replaces the loading pixels", 2, stable)
                        }
                        val frames = retainNativeMotionFrames(movie)
                        val last = frames.getAsJsonArray("decodedOriginalFrames").last().asJsonObject.get("file").asString
                        val bitmap = BitmapFactory.decodeFile(File(folder, last).path)
                        try {
                            var pixels = 0
                            for (y in 0 until bitmap.height step 4) for (x in 0 until bitmap.width step 4) {
                                val value = bitmap.getPixel(x, y)
                                if (Color.red(value) > 235 && Color.green(value) < 20 && Color.blue(value) > 235) pixels++
                            }
                            assertTrue("The recording itself contains the resulting preview image", pixels > 1000)
                        } finally { bitmap.recycle() }
                        frames.add("recording", recording); frames.add("nativeSamples", samples)
                        File(folder, "scale-$scale-thumbnail.json").writeText(frames.toString())
                    } finally { release.countDown() }
                }
            }
        }
    }
}
