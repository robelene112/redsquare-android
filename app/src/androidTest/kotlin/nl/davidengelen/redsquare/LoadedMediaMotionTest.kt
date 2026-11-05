package nl.davidengelen.redsquare

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
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

/** Hold actual media bytes beyond destination entry, then inspect original display interpolation. */
class LoadedMediaMotionTest {
    @Test fun profileBannerRevealsDeliveredPixelsWithoutChangingItsPlaceholderGeometry() {
        for (scale in listOf(1, 0)) withNativeModuleMotionScale(scale) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val device = UiDevice.getInstance(instrumentation)
            val folder = File(instrumentation.targetContext.cacheDir, "loaded-media-evidence").apply { mkdirs() }
            val requested = CountDownLatch(1)
            val release = CountDownLatch(1)
            MockWebServer().use { media ->
                media.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        check(request.url.encodedPath == "/banner-$scale.png")
                        requested.countDown(); check(release.await(10, TimeUnit.SECONDS))
                        return MockResponse.Builder().body(Buffer().write(linkPreviewPng())).build()
                    }
                }
                media.start(InetAddress.getByName("127.0.0.1"), 0)
                try {
                    withAvatarJourney("Dark") { activity, _, response, _, author ->
                        check(device.wait(Until.hasObject(By.text("Shared avatar journey")), 15_000))
                        val tx = SaitoTransaction.create(author, JsonObject().apply {
                            addProperty("module", "Profile"); addProperty("request", "update profile"); addProperty("snapshot", 1)
                            add("data", JsonObject().apply { addProperty("banner", media.url("/banner-$scale.png").toString()) })
                        }, 1_900_000_000_000L + scale)
                        response.set(JsonObject().apply { addProperty("tx", tx.envelope()) }.let { "[$it]" })
                        requireNotNull(device.wait(Until.findObject(By.desc("View profile for Anon-${author.publicKey.take(6)}")), 5_000)).click()
                        check(requested.await(5, TimeUnit.SECONDS))
                        val loading = requireNotNull(device.wait(Until.findObject(By.text("Loading image")), 5_000)).visibleBounds
                        // The existing180ms destination reveal must finish before testing this independent media result.
                        SystemClock.sleep(250)
                        captureNativeScreen(File(folder, "scale-$scale-banner-loading.png"))
                        recordArrival("banner", scale, folder, loading, activity) { release.countDown() }
                        val banner = requireNotNull(device.wait(Until.findObject(By.desc("Profile banner")), 5_000)).visibleBounds
                        assertTrue("The raster retains the profile's3:1 banner container", kotlin.math.abs(banner.width() - banner.height() * 3) <= 3)
                        assertTrue("The actual loading placeholder stays inside the eventual banner", banner.contains(loading.centerX(), loading.centerY()))
                        captureNativeScreen(File(folder, "scale-$scale-banner-loaded.png"))
                    }
                } finally { release.countDown() }
            }
        }
    }

    @Test fun providerDrawableRevealsDeliveredPixelsInsideItsOriginalImageContainer() {
        for (scale in listOf(1, 0)) withNativeModuleMotionScale(scale) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val device = UiDevice.getInstance(instrumentation)
            val folder = File(instrumentation.targetContext.cacheDir, "loaded-media-evidence").apply { mkdirs() }
            val requested = CountDownLatch(1)
            val release = CountDownLatch(1)
            MockWebServer().use { media ->
                media.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        check(request.url.encodedPath == "/media/arrival-$scale.png")
                        requested.countDown(); check(release.await(10, TimeUnit.SECONDS))
                        return MockResponse.Builder().body(Buffer().write(linkPreviewPng())).build()
                    }
                }
                media.start(InetAddress.getByName("127.0.0.1"), 0)
                val author = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Published unsafe fixture only.
                val post = SaitoTransaction.create(author, JsonObject().apply {
                    addProperty("module", "RedSquare"); addProperty("request", "create tweet")
                    add("data", JsonObject().apply {
                        addProperty("text", "An owned provider image arrives")
                        add("images", JsonArray().apply { add("http://127.0.0.1:${media.port}/media/arrival-$scale.png") })
                    })
                }, 1_700_000_000_000L)
                try {
                    withAppFrameFixture("drawable-$scale", "loaded-media-evidence", "[${postingArchiveRow(post)}]",
                        giphyFixtureUrl = "http://127.0.0.1:${media.port}") { activity ->
                        requireNotNull(device.wait(Until.findObject(By.text("Later")), 15_000)).click()
                        check(requested.await(5, TimeUnit.SECONDS))
                        val placeholder = requireNotNull(device.wait(Until.findObject(By.text("Loading image")), 5_000))
                        val image = placeholder.parent.visibleBounds
                        val loading = placeholder.visibleBounds
                        SystemClock.sleep(250)
                        captureNativeScreen(File(folder, "scale-$scale-drawable-loading.png"))
                        recordArrival("drawable", scale, folder, loading, activity) { release.countDown() }
                        assertEquals("Drawable replacement retains its actual native container", image,
                            requireNotNull(device.wait(Until.findObject(By.clazz("android.widget.ImageView")
                                .pkg(instrumentation.targetContext.packageName)), 5_000)).visibleBounds)
                        assertTrue(device.hasObject(By.text("An owned provider image arrives")))
                        captureNativeScreen(File(folder, "scale-$scale-drawable-loaded.png"))
                    }
                } finally { release.countDown() }
            }
        }
    }

    private fun recordArrival(name: String, scale: Int, folder: File, point: Rect,
        activity: ActivityScenario<MainActivity>, deliver: () -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val before = File(folder, "before-$name").apply { mkdirs() }
        observeModuleWindowMotionScale(activity, scale, before)
        val samples = JsonArray()
        var deliveryFailure: Throwable? = null
        val movie = File(folder, "scale-$scale-$name.mp4")
        var deliveryDeadline = 0L
        val recording = recordNativeTransition(movie, recordingSeconds = 4) {
            deliveryDeadline = SystemClock.elapsedRealtime() + 10_000
            try { deliver() } catch (failure: Throwable) { deliveryFailure = failure }
        }
        // Keep SurfaceFlinger screenshot capture out of the recorded transition itself.
        // The original10s delivery budget still starts when the packet is released.
        if (deliveryFailure == null) try {
            var stable = 0
            while (stable < 2 && SystemClock.elapsedRealtime() < deliveryDeadline) {
                val screen = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                val pixel = try { screen.getPixel(point.centerX(), point.centerY()) } finally { screen.recycle() }
                val matches = Color.red(pixel) > 235 && Color.green(pixel) < 20 && Color.blue(pixel) > 235
                samples.add(JsonObject().apply {
                    addProperty("elapsedMillis", SystemClock.elapsedRealtime()); addProperty("argb", pixel)
                    addProperty("matches", matches)
                })
                stable = if (matches) stable + 1 else 0
                if (stable < 2) SystemClock.sleep(40)
            }
            assertEquals("Delivered media must actually replace loading pixels", 2, stable)
        } catch (failure: Throwable) { deliveryFailure = failure }
        cleanupAfterTest(deliveryFailure, {
            val frames = retainNativeMotionFrames(movie)
            val colors = JsonArray()
            val intermediate = mutableListOf<Double>()
            var deliveredFrames = 0
            for (entry in frames.getAsJsonArray("decodedOriginalFrames")) {
                val value = entry.asJsonObject
                val frame = requireNotNull(BitmapFactory.decodeFile(File(folder, value.get("file").asString).path))
                try {
                    val pixel = frame.getPixel(point.centerX(), point.centerY())
                    val chroma = (Color.red(pixel) + Color.blue(pixel)) / 2.0 - Color.green(pixel)
                    if (chroma in 20.0..230.0) intermediate.add(chroma)
                    if (Color.red(pixel) > 235 && Color.green(pixel) < 20 && Color.blue(pixel) > 235) deliveredFrames++
                    colors.add(JsonObject().apply {
                        addProperty("file", value.get("file").asString)
                        addProperty("presentationTimeUs", value.get("presentationTimeUs").asLong)
                        addProperty("argb", pixel); addProperty("magentaChroma", chroma)
                    })
                } finally { frame.recycle() }
            }
            frames.add("recording", recording); frames.add("nativeDeliverySamples", samples); frames.add("originalFramePixels", colors)
            frames.addProperty("samplePoint", point.toShortString()); frames.addProperty("intermediateFrames", intermediate.size)
            frames.addProperty("deliveredFrames", deliveredFrames)
            File(folder, "scale-$scale-$name.json").writeText(frames.toString())
            observeModuleWindowMotionScale(activity, scale, folder)
            // A zero-motion static replacement may emit just one final damage-driven encoded frame.
            // Two independent native endpoint samples above still establish sampled stability.
            assertTrue("The original recording contains the actual delivered media", deliveredFrames >= if (scale == 0) 1 else 2)
            if (scale == 1) assertTrue("Normal motion reveals delivered media across distinct intermediate display frames",
                intermediate.size >= 2 && intermediate.max() - intermediate.min() >= 10)
            else assertTrue("Zero motion does not retain a media fade", intermediate.size < 2)
        })
        deliveryFailure?.let { throw it }
    }
}
