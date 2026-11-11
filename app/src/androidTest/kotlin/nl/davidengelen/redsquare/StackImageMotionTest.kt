package nl.davidengelen.redsquare

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import mockwebserver3.*
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

/** Native Markdown image delivery after the independent route transition has settled. */
class StackImageMotionTest {
    @Test fun loadedArticleImagesKeepTheirGeometryAndRespectNormalAndZeroMotion() {
        for (scale in listOf(1, 0)) withNativeModuleMotionScale(scale) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val device = UiDevice.getInstance(instrumentation)
            val folder = File(instrumentation.targetContext.cacheDir, "stack-image-motion-evidence").apply { mkdirs() }
            val requested = CountDownLatch(1)
            val release = CountDownLatch(1)
            MockWebServer().use { media ->
                media.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        check(request.url.encodedPath == "/article-$scale.png")
                        requested.countDown()
                        check(release.await(10, TimeUnit.SECONDS))
                        return MockResponse.Builder().body(Buffer().write(linkPreviewPng())).build()
                    }
                }
                media.start(InetAddress.getByName("127.0.0.1"), 0)
                // Published unsafe fixture only, never a real wallet or live publication.
                val author = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
                val article = SaitoTransaction.create(author, JsonObject().apply {
                    addProperty("module", "Stack"); addProperty("request", "create stack post request")
                    add("data", JsonObject().apply {
                        addProperty("type", "stack_post"); addProperty("title", "A loaded Stack image")
                        addProperty("content", "![A field photograph](${media.url("/article-$scale.png")})")
                    })
                }, 1_700_000_000_000L + scale)
                val row = JsonObject().apply {
                    addProperty("created_at", 1_700_000_000_000L + scale)
                    addProperty("tx", article.envelope())
                }
                try {
                    withAppFrameFixture("stack-image-$scale", "stack-image-motion-evidence", archiveResponse = { criteria ->
                        if (criteria.get("field1").asString == "Stack") "[$row]" else "[]"
                    }) { activity ->
                        requireNotNull(device.wait(Until.findObject(By.text("Later")), 15_000)).click()
                        requireNotNull(device.wait(Until.findObject(By.desc("Switch app: Red Square")), 5_000)).click()
                        requireNotNull(device.wait(Until.findObject(By.text("Stack")), 5_000)).click()
                        check(device.wait(Until.hasObject(By.desc("Switch app: Stack")), 5_000))
                        awaitNativeStackArticleCard(device, "A loaded Stack image", 15_000, File(folder, "scale-$scale-card-ready.json"))
                        requireNotNull(device.findObject(By.text("A loaded Stack image"))).click()
                        check(requested.await(5, TimeUnit.SECONDS))
                        val placeholder = requireNotNull(device.wait(Until.findObject(By.desc("Loading image")), 5_000)).visibleBounds
                        assertFalse("The Markdown loading image is visible", placeholder.isEmpty)
                        // Route entry and image delivery are independent transitions.
                        SystemClock.sleep(250)
                        captureNativeScreen(File(folder, "scale-$scale-loading.png"))
                        observeModuleWindowMotionScale(activity, scale, folder)
                        val movie = File(folder, "scale-$scale-article.mp4")
                        val started = SystemClock.elapsedRealtime()
                        val recording = recordNativeTransition(movie, recordingSeconds = 4) { release.countDown() }
                        val image = requireNotNull(device.wait(Until.findObject(By.desc("A field photograph")), 5_000)).visibleBounds
                        assertTrue("Real image delivery stays within ten seconds", SystemClock.elapsedRealtime() - started < 10_000)
                        assertEquals("The actual Markdown image keeps its loading geometry", placeholder, image)
                        captureNativeScreen(File(folder, "scale-$scale-loaded.png"))
                        val frames = retainNativeMotionFrames(movie)
                        val pixels = JsonArray()
                        val intermediate = mutableListOf<Double>()
                        var delivered = 0
                        for (entry in frames.getAsJsonArray("decodedOriginalFrames")) {
                            val frame = BitmapFactory.decodeFile(File(folder, entry.asJsonObject.get("file").asString).path)
                            try {
                                val pixel = frame.getPixel(image.centerX(), image.centerY())
                                val chroma = (Color.red(pixel) + Color.blue(pixel)) / 2.0 - Color.green(pixel)
                                if (chroma in 20.0..230.0) intermediate.add(chroma)
                                if (Color.red(pixel) > 235 && Color.green(pixel) < 20 && Color.blue(pixel) > 235) delivered++
                                pixels.add(entry.deepCopy().asJsonObject.apply { addProperty("argb", pixel); addProperty("magentaChroma", chroma) })
                            } finally { frame.recycle() }
                        }
                        frames.add("recording", recording); frames.add("originalFramePixels", pixels)
                        frames.addProperty("imageBounds", image.toShortString())
                        File(folder, "scale-$scale-article.json").writeText(frames.toString())
                        assertTrue("Original display frames contain the delivered article image", delivered >= 1)
                        if (scale == 1) assertTrue("Normal motion has distinct intermediate image frames",
                            intermediate.size >= 2 && intermediate.max() - intermediate.min() >= 10)
                        else assertTrue("Zero motion does not retain an image fade", intermediate.size < 2)
                    }
                } finally { release.countDown() }
            }
        }
    }
}
