package nl.davidengelen.redsquare

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Base64

/** Real input, signed peer receipts and original display frames; no Compose test clock. */
class LikePulseMotionTest {
    @Test fun repeatedHeartsStaySmallAndReturnToOutlineAcrossRealSurfaces() {
        for (scale in listOf(1, 0)) withNativeModuleMotionScale(scale) {
            withLikeMotionFixture { activity, node ->
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                val context = instrumentation.targetContext
                val device = UiDevice.getInstance(instrumentation)
                val folder = File(context.cacheDir, "like-pulse-evidence").apply { mkdirs() }
                val author = "Anon-${node.postAuthor.take(6)}"
                val selector = By.desc("Like post by $author").enabled(true)
                val density = context.resources.displayMetrics.density
                observeModuleWindowMotionScale(activity, scale, folder)
                var expected = 0
                for (surface in listOf("feed", "thread", "profile")) {
                    if (surface == "thread") {
                        requireNotNull(device.wait(Until.findObject(By.text("Native Red Square reference post")), 15_000)).click()
                        check(device.wait(Until.hasObject(By.text("Conversation")), 15_000))
                    } else if (surface == "profile") {
                        requireNotNull(device.wait(Until.findObject(By.desc("View profile for $author")), 15_000)).click()
                        check(device.wait(Until.hasObject(By.text("Profile")), 15_000))
                    }
                    if (surface == "thread") check(device.wait(Until.gone(By.text("Loading replies")), 15_000))
                    if (surface == "profile") {
                        check(device.wait(Until.gone(By.text("Loading profile history")), 15_000))
                        check(device.wait(Until.gone(By.text("Loading profile details...")), 15_000))
                        androidx.test.uiautomator.UiScrollable(androidx.test.uiautomator.UiSelector().scrollable(true))
                            .scrollIntoView(androidx.test.uiautomator.UiSelector().description("Like post by $author"))
                    }
                    val labelled = requireNotNull(device.wait(Until.findObject(selector), 15_000))
                    // Compose exposes the label below the focusable click action in the platform tree.
                    val control = if (labelled.isClickable) labelled else requireNotNull(labelled.parent).also {
                        assertTrue("The labelled heart belongs to the same enabled action", it.isEnabled && it.visibleBounds == labelled.visibleBounds)
                    }
                    captureNativeScreen(File(folder, "scale-$scale-$surface-ready.png"))
                    assertTrue("The positively enabled heart must expose a clickable accessibility action", control.isClickable)
                    val bounds = control.visibleBounds
                    assertTrue("The accessible heart keeps its48dp target", bounds.width() >= 48 * density && bounds.height() >= 48 * density)
                    val imageRegion = Rect(bounds.left, bounds.top, bounds.right, bounds.bottom - (14 * density).toInt())
                    captureNativeScreen(File(folder, "scale-$scale-$surface-before.png"))
                    val movie = File(folder, "scale-$scale-$surface.mp4")
                    val taps = JsonArray()
                    val samples = JsonArray()
                    val recording = recordNativeTransition(movie, recordingSeconds = 4) {
                        // One isolated tap proves the pulse settles quickly, without an archive wait.
                        taps.add(tap(bounds.centerX(), bounds.centerY()))
                        SystemClock.sleep(450)
                        val settled = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                        try {
                            val pixels = pinkPixels(settled, imageRegion)
                            samples.add(JsonObject().apply { addProperty("singleTapAfter450msPinkPixels", pixels) })
                            assertEquals("A short Like pulse must already be back to the outline", 0, pixels)
                        } finally { settled.recycle() }
                        // Input lands faster than the pulse, exercising cancellation and immediate restart.
                        repeat(5) { taps.add(tap(bounds.centerX(), bounds.centerY())); SystemClock.sleep(65) }
                        SystemClock.sleep(450)
                        val end = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                        try { assertEquals("Repeated feedback cannot leave a filled toggle", 0, pinkPixels(end, imageRegion)) }
                        finally { end.recycle() }
                    }
                    expected += 6
                    check(device.wait(Until.hasObject(By.text("${4 + expected} likes")), 20_000))
                    val deadline = SystemClock.elapsedRealtime() + 20_000
                    while (node.peer.transactions.size < expected && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(40)
                    assertEquals("Every physical tap reaches the compatible peer as a distinct signed Like", expected, node.peer.transactions.size)
                    val signed = node.peer.transactions.map(::postingTransaction).map { SaitoEnvelope.decode(it.envelope()) }
                    assertEquals(expected, signed.map { it.id }.toSet().size)
                    assertEquals(1, signed.map { it.author }.toSet().size)
                    assertTrue(signed.all { it.body.get("request").asString == "like tweet" })
                    assertFalse(device.hasObject(By.text("Like pending")))
                    assertFalse(device.hasObject(By.text("Unlike")))
                    assertFalse(device.hasObject(By.text("Like")))
                    val afterBounds = requireNotNull(device.findObject(selector)).visibleBounds
                    assertEquals("Feedback cannot move the action row", bounds.top, afterBounds.top)
                    assertEquals("Feedback cannot grow the action row", bounds.height(), afterBounds.height())
                    assertEquals(bounds.left, afterBounds.left)
                    assertTrue("A wider truthful count still keeps the accessible target", afterBounds.width() >= 48 * density)
                    val frames = retainNativeMotionFrames(movie)
                    var pinkFrames = 0
                    var largestWidth = 0
                    var smallestPinkWidth = Int.MAX_VALUE
                    for (entry in frames.getAsJsonArray("decodedOriginalFrames")) {
                        val metadata = entry.asJsonObject
                        val image = requireNotNull(BitmapFactory.decodeFile(File(folder, metadata.get("file").asString).path))
                        try {
                            val pixels = pinkPixels(image, imageRegion)
                            if (pixels > 0) pinkFrames++
                            val width = pinkWidth(image, imageRegion)
                            largestWidth = maxOf(largestWidth, width)
                            if (width > 0) smallestPinkWidth = minOf(smallestPinkWidth, width)
                            metadata.addProperty("heartPinkPixels", pixels); metadata.addProperty("heartPinkWidthPixels", width)
                        } finally { image.recycle() }
                    }
                    assertTrue("Original recorded frames must show immediate pink feedback, including zero-motion", pinkFrames > 0)
                    assertTrue("The pulse stays local, without an oversized heart or ripple", largestWidth <= 26 * density)
                    if (scale == 1) assertTrue("Normal-motion original frames contain an actual size pulse", largestWidth - smallestPinkWidth >= 2)
                    else assertTrue("Zero-motion feedback stays still", largestWidth - smallestPinkWidth <= 2)
                    assertEquals(0, frames.getAsJsonArray("decodedOriginalFrames").last().asJsonObject.get("heartPinkPixels").asInt)
                    frames.add("recording", recording); frames.add("taps", taps); frames.add("samples", samples)
                    frames.addProperty("pinkFrames", pinkFrames); frames.addProperty("largestPinkWidthPixels", largestWidth)
                    frames.addProperty("smallestPinkWidthPixels", smallestPinkWidth)
                    frames.addProperty("density", density); frames.addProperty("targetBounds", bounds.toShortString())
                    File(folder, "scale-$scale-$surface.json").writeText(frames.toString())
                    captureNativeScreen(File(folder, "scale-$scale-$surface-after.png"))
                }
                node.peer.transactions.forEachIndexed { index, bytes -> File(folder, "signed-like-$scale-$index.base64").writeText(Base64.getEncoder().encodeToString(bytes)) }
                node.peer.assertHealthy()
            }
        }
    }

    private fun tap(x: Int, y: Int): Long {
        val start = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, x.toFloat(), y.toFloat(), 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            try { check(InstrumentationRegistry.getInstrumentation().uiAutomation.injectInputEvent(event, true)) }
            finally { event.recycle() }
        }
        return start
    }

    private fun pink(pixel: Int) = Color.red(pixel) > 120 && Color.red(pixel) > Color.green(pixel) * 1.35 && Color.blue(pixel) > Color.green(pixel) * 1.05
    private fun pinkPixels(image: Bitmap, region: Rect): Int {
        var count = 0
        for (y in region.top until region.bottom) for (x in region.left until region.right) if (pink(image.getPixel(x, y))) count++
        return count
    }
    private fun pinkWidth(image: Bitmap, region: Rect): Int {
        var left = region.right; var right = region.left - 1
        for (y in region.top until region.bottom) for (x in region.left until region.right) if (pink(image.getPixel(x, y))) { left = minOf(left, x); right = maxOf(right, x) }
        return maxOf(0, right - left + 1)
    }
}

private fun withLikeMotionFixture(block: (ActivityScenario<MainActivity>, PostLikePeer) -> Unit) {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    check(context.packageName == "nl.davidengelen.redsquare.dev" && android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
    val wallets = AndroidWalletStore(context)
    val wallet = wallets.loadOrCreate()
    val backends = AndroidBackendStore(context)
    val original = backends.load()
    val retained = ChatFixtureState(context, listOf("social.", "socialrate.", "feed.", "conversation.", "profile.", "profilemeta.", "authorposts.", "following.", "namemap."), wallet.publicKey)
    var primary: Throwable? = null
    try {
        retained.prepare(); backends.save(SaitoBackend.OWNER)
        val node = PostLikePeer()
        node.peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { activity ->
                withFailureCapture({ captureNativeScreen(File(context.cacheDir, "like-pulse-evidence/failure.png")) }) { block(activity, node) }
                assertEquals(SaitoBackend.OWNER, backends.load())
                assertTrue(wallet.exportJson() == wallets.loadExisting().exportJson())
                assertTrue(node.peer.reports.isEmpty())
            }
        }
    } catch (failure: Throwable) { primary = failure; throw failure }
    finally { cleanupAfterTest(primary, { retained.restoreFiles() }, { retained.restorePreferences() },
        { backends.save(original); check(backends.load() == original) }, { check(wallet.exportJson() == wallets.loadExisting().exportJson()) }) }
}
