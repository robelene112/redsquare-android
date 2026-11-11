package nl.davidengelen.redsquare

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import mockwebserver3.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

/** Physical repeated input during result replacement must not activate an exiting Retry. */
class ImageRetryOwnershipTest {
    @Test fun retryStartsOneReplacementReadAtNormalAndZeroMotion() {
        for (scale in listOf(1, 0)) withNativeModuleMotionScale(scale) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val device = UiDevice.getInstance(instrumentation)
            val folder = File(context.cacheDir, "image-retry-evidence").apply { mkdirs() }
            val calls = AtomicInteger()
            MockWebServer().use { media ->
                media.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        check(request.url.encodedPath == "/retry-$scale.png")
                        if (calls.incrementAndGet() == 1) return MockResponse.Builder().code(500).build()
                        return MockResponse.Builder().body(Buffer().write(linkPreviewPng()))
                            .bodyDelay(500, TimeUnit.MILLISECONDS).build()
                    }
                }
                media.start(InetAddress.getByName("127.0.0.1"), 0)
                val metadata = JsonObject().apply { add("link_properties", JsonObject().apply {
                    addProperty("og:title", "Retry ownership proof")
                    addProperty("og:image", media.url("/retry-$scale.png").toString())
                }) }
                val row = linkedFeedRow("One deliberate image retry", metadata = metadata.toString())
                withInterceptedPostLinks {
                withAppFrameFixture("retry-$scale", "image-retry-evidence", "[$row]") { activity ->
                    activity.onActivity { it.intent.putExtra("feedOfficialPeerUrl", it.intent.getStringExtra("feedPeerUrl")) }
                    requireNotNull(device.wait(Until.findObject(By.text("Later")), 15_000)).click()
                    check(device.wait(Until.hasObject(By.text(context.getString(R.string.image_unavailable))), 10_000))
                    val retry = requireNotNull(device.wait(Until.findObject(By.text("Retry").enabled(true)), 5_000)).visibleBounds
                    assertEquals(1, calls.get())
                    val taps = JsonArray()
                    val movie = File(folder, "scale-$scale-retry.mp4")
                    val recording = recordNativeTransition(movie, recordingSeconds = 4) {
                        repeat(3) {
                            val start = SystemClock.elapsedRealtime()
                            check(device.click(retry.centerX(), retry.centerY()))
                            taps.add(JsonObject().apply {
                                addProperty("startMillis", start)
                                addProperty("returnedMillis", SystemClock.elapsedRealtime())
                            })
                            SystemClock.sleep(35)
                        }
                    }
                    val frames = retainNativeMotionFrames(movie)
                    frames.add("recording", recording); frames.add("physicalTaps", taps)
                    frames.addProperty("httpRequests", calls.get())
                    File(folder, "scale-$scale-retry.json").writeText(frames.toString())
                    observeModuleWindowMotionScale(activity, scale, folder)
                    assertEquals("The exiting Retry must not start another read after the deliberate replacement starts", 2, calls.get())
                    check(device.wait(Until.gone(By.text(context.getString(R.string.image_unavailable))), 5_000))
                    check(device.wait(Until.gone(By.text(context.getString(R.string.image_loading))), 5_000))
                    assertTrue(device.hasObject(By.text("One deliberate image retry")))
                    assertTrue("Image recovery must not open a browser", androidx.test.espresso.intent.Intents.getIntents()
                        .none { it.action == android.content.Intent.ACTION_VIEW })
                    captureNativeScreen(File(folder, "scale-$scale-loaded.png"))
                    // Once loaded, the image remains an ordinary way to open its linked article.
                    requireNotNull(device.wait(Until.findObject(By.desc(context.getString(R.string.post_link_image))), 5_000)).click()
                    check(device.wait(Until.hasObject(By.desc("Switch app: Stack")), 5_000))
                }
                }
            }
        }
    }
}
