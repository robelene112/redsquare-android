package nl.davidengelen.redsquare

import android.graphics.Rect
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.feed.PublicStringStore
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** Actual system motion, native input and every original frame, without a Compose test clock. */
class QuoteRecyclingMotionTest {
    @Test fun loadedQuoteKeepsItsRenderedGeometryAcrossNativeScrollsAtNormalAndZeroMotion() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val fixture = instrumentation.context.assets.open("native-quote-reference.json").reader().use {
            JsonParser.parseReader(it).asJsonObject
        }
        val original = fixture.getAsJsonObject("original")
        val current = fixture.getAsJsonObject("current").deepCopy().apply {
            addProperty("field1", "RedSquareRetweet"); addProperty("field4", original.get("sig").asString)
        }
        val rows = instrumentation.context.assets.open("native-feed-scroll-reference.json").reader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonArray("initialRows").toString()
        }
        val text = "Native Red Square reference post"
        val commentary = "Current commentary retweet"
        for (scale in listOf(1, 0)) withNativeModuleMotionScale(scale) {
            val reads = AtomicInteger()
            val ownedPeer = AtomicReference<ControlledFeedPeer>()
            withAppFrameFixture("quote-recycling-$scale", "quote-motion-evidence", rows,
                configurePeer = { ownedPeer.set(it) }, quoteArchiveResponse = { "[$current]" },
                archiveResponse = { criteria ->
                    when {
                        criteria.get("sig")?.asString == original.get("sig").asString -> {
                            reads.incrementAndGet(); "[$original]"
                        }
                        criteria.get("field1")?.asString == "RedSquare" -> rows
                        else -> "[]"
                    }
                }) { activity ->
                val folder = File(context.cacheDir, "quote-motion-evidence").apply { mkdirs() }
                val gestures = com.google.gson.JsonArray()
                fun moveTo(label: String, forward: Boolean, budgetMillis: Long,
                    requiredPostHeight: Int = 0, bodyOffset: Int = 0) {
                    val deadline = android.os.SystemClock.elapsedRealtime() + budgetMillis
                    var previous: Rect? = null
                    var stableSince = 0L
                    while (android.os.SystemClock.elapsedRealtime() < deadline) {
                        val bounds = requireNotNull(device.findObject(By.scrollable(true))).visibleBounds
                        val target = device.findObject(By.text(label))?.visibleBounds?.takeUnless { it.isEmpty }
                        val top = target?.top?.minus(bodyOffset)
                        val fitted = target != null && (requiredPostHeight == 0 ||
                            (requireNotNull(top) >= bounds.top && top + requiredPostHeight <= bounds.bottom))
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (fitted) {
                            if (target != previous) { previous = target; stableSince = now }
                            if (now - stableSince >= 100) return
                            android.os.SystemClock.sleep(20)
                            continue
                        }
                        previous = null; stableSince = 0
                        val high = bounds.top + bounds.height() / 5
                        val low = bounds.bottom - bounds.height() / 5
                        val started = android.os.SystemClock.elapsedRealtime()
                        assertTrue(device.swipe(bounds.centerX(), if (forward) low else high,
                            bounds.centerX(), if (forward) high else low, 6))
                        device.wait(Until.hasObject(By.text(label)), 75)
                        gestures.add(JsonObject().apply {
                            addProperty("target", label); addProperty("forward", forward)
                            addProperty("started", started); addProperty("returned", android.os.SystemClock.elapsedRealtime())
                            addProperty("bounds", bounds.toShortString()); addProperty("targetBounds", target?.toShortString())
                        })
                    }
                    fail("Real native swipes must reach stable $label within the declared input budget")
                }
                requireNotNull(device.wait(Until.findObject(By.text("Later")), 15_000)).click()
                moveTo(commentary, forward = false, budgetMillis = 20_000)
                requireNotNull(device.wait(Until.findObject(By.text(text)), 20_000))
                assertTrue("The real peer supplied this original before recycling", reads.get() > 0)
                fun cardBounds(): Rect {
                    val quote = requireNotNull(device.findObject(By.text(text))).visibleBounds
                    var node = requireNotNull(device.findObject(By.text(commentary)))
                    repeat(8) {
                        val bounds = node.visibleBounds
                        if (node.isClickable && bounds.contains(quote.centerX(), quote.centerY()) &&
                            bounds.width() >= device.displayWidth * 0.8) return bounds
                        node = requireNotNull(node.parent)
                    }
                    error("The native quote and commentary must belong to one visible post action")
                }
                val before = cardBounds()
                val bodyOffset = requireNotNull(device.findObject(By.text(commentary))).visibleBounds.top - before.top
                val relativeTop = requireNotNull(device.findObject(By.text(text))).visibleBounds.top -
                    requireNotNull(device.findObject(By.text(commentary))).visibleBounds.top
                observeModuleWindowMotionScale(activity, scale, folder)
                captureNativeScreen(File(folder, "scale-$scale-before.png"))
                repeat(3) { round ->
                    moveTo("Reference post 01", forward = true, budgetMillis = 10_000)
                    assertFalse("The native viewport must leave the quoted post", device.hasObject(By.text(commentary)))
                    val entered = CountDownLatch(1)
                    val release = CountDownLatch(1)
                    val holding = AtomicBoolean(false)
                    val workerFailure = AtomicReference<Throwable?>()
                    val worker = thread(name = "owned-native-quote-cache", isDaemon = true) {
                        try {
                            synchronized(PublicStringStore.lock) {
                                holding.set(true); entered.countDown()
                                check(release.await(8, TimeUnit.SECONDS))
                            }
                        } catch (failure: Throwable) { workerFailure.set(failure); entered.countDown() }
                        finally { holding.set(false) }
                    }
                    var primary: Throwable? = null
                    try {
                        assertTrue(entered.await(2, TimeUnit.SECONDS)); workerFailure.get()?.let { throw it }
                        val movie = File(folder, "scale-$scale-round-$round.mp4")
                        val frames = recordNativeCheckpointTransition(activity, movie) {
                            moveTo(commentary, forward = false, budgetMillis = 3_000,
                                requiredPostHeight = before.height(), bodyOffset = bodyOffset)
                            assertTrue("The native original remains visible on re-entry", device.hasObject(By.text(text)))
                            assertFalse(device.hasObject(By.text("Loading original.")))
                            val after = cardBounds()
                            assertEquals("The real post retains its complete rendered height", before.height(), after.height())
                            val afterRelativeTop = requireNotNull(device.findObject(By.text(text))).visibleBounds.top -
                                requireNotNull(device.findObject(By.text(commentary))).visibleBounds.top
                            assertEquals("The original keeps its position within the post", relativeTop, afterRelativeTop)
                            assertTrue("The cache remains held through the native observation",
                                holding.get() && worker.isAlive && release.count == 1L)
                            release.countDown()
                        }
                        frames.addProperty("post", current.get("sig").asString)
                        frames.addProperty("original", original.get("sig").asString)
                        frames.addProperty("height", before.height())
                        frames.addProperty("relativeOriginalTop", relativeTop)
                        frames.addProperty("publicationCount", ownedPeer.get().transactions.size)
                        File(folder, "scale-$scale-round-$round.json").writeText(frames.toString())
                    } catch (failure: Throwable) { primary = failure; throw failure }
                    finally {
                        cleanupAfterTest(primary, { release.countDown() }, {
                            worker.join(2_000); check(!worker.isAlive); workerFailure.get()?.let { throw it }
                        }, { File(folder, "scale-$scale-native-gestures.json").writeText(gestures.toString()) })
                    }
                }
                assertTrue(ownedPeer.get().transactions.isEmpty()); ownedPeer.get().assertHealthy()
                captureNativeScreen(File(folder, "scale-$scale-after.png"))
            }
        }
    }
}
