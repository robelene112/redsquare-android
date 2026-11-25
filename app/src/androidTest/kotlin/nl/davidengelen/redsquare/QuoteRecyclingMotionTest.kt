package nl.davidengelen.redsquare

import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
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
                val positions = com.google.gson.JsonArray()
                fun moveTo(label: String, forward: Boolean, budgetMillis: Long,
                    requiredPostHeight: Int = 0, bodyOffset: Int? = null) {
                    val deadline = SystemClock.elapsedRealtime() + budgetMillis
                    val waypoint = referenceNumber(label)
                    var previous: NativePostGeometry? = null
                    var stableSince = 0L
                    var primary: Throwable? = null
                    try {
                        while (SystemClock.elapsedRealtime() < deadline) {
                            val geometry = nativePostGeometry(device, label)
                            val bounds = geometry.viewport
                            val target = geometry.body
                            // A partially visible ancestor's raw bounds can be
                            // clipped above its text. Return using the known
                            // text offset from the fully visible baseline.
                            val top = if (bodyOffset == null) geometry.card?.top else target?.top?.minus(bodyOffset)
                            val fitted = bounds != null && target != null &&
                                (requiredPostHeight == 0 || (top != null &&
                                    top >= bounds.top && top + requiredPostHeight <= bounds.bottom))
                            val now = SystemClock.elapsedRealtime()
                            positions.add(JsonObject().apply {
                                addProperty("target", label); addProperty("elapsed", now)
                                addProperty("viewport", bounds?.toShortString()); addProperty("body", target?.toShortString())
                                addProperty("card", geometry.card?.toShortString()); addProperty("fitted", fitted)
                                addProperty("requiredHeight", requiredPostHeight)
                                addProperty("visibleReferencePosts", geometry.referencePosts?.toString())
                            })
                            if (fitted) {
                                if (geometry != previous) { previous = geometry; stableSince = now }
                                if (now - stableSince >= 100) return
                                SystemClock.sleep(20)
                                continue
                            }
                            previous = null; stableSince = 0
                            if (bounds == null) { SystemClock.sleep(20); continue }
                            val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop
                            val nearby = geometry.referencePosts
                            val scrollForward = if (waypoint == null || nearby == null) forward else when {
                                waypoint > nearby.last -> false
                                waypoint < nearby.first -> true
                                else -> forward
                            }
                            // If a large drag skipped the numbered waypoint, use
                            // the rows actually visible to reverse and approach it.
                            val nearWaypoint = waypoint != null && nearby != null &&
                                waypoint in (nearby.first - 2)..(nearby.last + 2)
                            val edgeDivisor = if (nearWaypoint) 3 else 8
                            val margin = maxOf(2 * touchSlop, bounds.height() / edgeDivisor)
                            val high = bounds.top + margin
                            val low = bounds.bottom - margin
                            val correction = if (target != null && top != null && requiredPostHeight > 0) when {
                                top < bounds.top -> bounds.top - top
                                top + requiredPostHeight > bounds.bottom -> bounds.bottom - top - requiredPostHeight
                                else -> 0
                            } else 0
                            val startY = if (correction != 0) bounds.centerY() else if (scrollForward) low else high
                            val fittedDrag = correction + if (correction > 0) touchSlop + 1 else -touchSlop - 1
                            val endY = if (correction != 0) (startY + fittedDrag).coerceIn(high, low)
                                else if (scrollForward) high else low
                            val started = SystemClock.elapsedRealtime()
                            dragToRest(bounds.exactCenterX(), startY.toFloat(), endY.toFloat())
                            // Let the actual accessibility publication catch up
                            // before deciding whether another gesture is needed.
                            // This remains inside the caller's original budget.
                            device.wait(Until.hasObject(By.text(label)), 75)
                            gestures.add(JsonObject().apply {
                                addProperty("target", label); addProperty("forward", scrollForward)
                                addProperty("started", started); addProperty("returned", SystemClock.elapsedRealtime())
                                addProperty("startY", startY); addProperty("endY", endY)
                                addProperty("bounds", bounds.toShortString()); addProperty("targetBounds", target?.toShortString())
                            })
                        }
                        fail("Real native swipes must reach stable $label within the declared input budget")
                    } catch (failure: Throwable) { primary = failure; throw failure }
                    finally {
                        cleanupAfterTest(primary,
                            { File(folder, "scale-$scale-native-gestures.json").writeText(gestures.toString()) },
                            { File(folder, "scale-$scale-native-positions.json").writeText(positions.toString()) })
                    }
                }
                requireNotNull(device.wait(Until.findObject(By.text("Later")), 15_000)).click()
                val positionBy = android.os.SystemClock.elapsedRealtime() + 20_000
                fun remainingPosition() = (positionBy - android.os.SystemClock.elapsedRealtime()).also { check(it > 0) }
                moveTo(commentary, forward = false, budgetMillis = remainingPosition())
                requireNotNull(device.wait(Until.findObject(By.text(text)), 20_000))
                assertTrue("The real peer supplied this original before recycling", reads.get() > 0)
                val initial = requireNotNull(nativePostGeometry(device, commentary).card)
                moveTo(commentary, forward = false, budgetMillis = remainingPosition(),
                    requiredPostHeight = initial.height())
                val beforeGeometry = nativePostGeometry(device, commentary, text, refresh = true)
                val before = requireNotNull(beforeGeometry.card)
                assertTrue("The entire baseline card must be inside its native viewport",
                    requireNotNull(beforeGeometry.viewport).contains(before))
                assertTrue("The commentary and original must belong to the same native post",
                    before.contains(requireNotNull(beforeGeometry.original)))
                val bodyOffset = requireNotNull(beforeGeometry.body).top - before.top
                val relativeTop = requireNotNull(beforeGeometry.original).top - requireNotNull(beforeGeometry.body).top
                observeModuleWindowMotionScale(activity, scale, folder)
                captureNativeScreen(File(folder, "scale-$scale-before.png"))
                repeat(3) { round ->
                    // Leave the quote by multiple visible rows, then record its
                    // complete return within the existing three-second window.
                    // The semantic journey separately traverses all twenty rows.
                    moveTo("Reference post 18", forward = true, budgetMillis = 10_000)
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
                            val afterGeometry = nativePostGeometry(device, commentary, text, refresh = true)
                            val after = requireNotNull(afterGeometry.card)
                            assertTrue("The entire returned card must be inside its native viewport",
                                requireNotNull(afterGeometry.viewport).contains(after))
                            assertTrue("The returned original must belong to the same native post",
                                after.contains(requireNotNull(afterGeometry.original)))
                            assertEquals("The real post retains its complete rendered height", before.height(), after.height())
                            val afterRelativeTop = requireNotNull(afterGeometry.original).top -
                                requireNotNull(afterGeometry.body).top
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

    private data class NativePostGeometry(val viewport: Rect? = null, val body: Rect? = null,
        val card: Rect? = null, val original: Rect? = null, val referencePosts: IntRange? = null)

    private fun referenceNumber(text: String?) = text?.removePrefix("Reference post ")
        ?.takeIf { it != text }?.toIntOrNull()

    /** One native tree for coordinates; never mix UiObject2's clipped/cache-adjusted rectangles with it. */
    @Suppress("DEPRECATION")
    private fun nativePostGeometry(device: UiDevice, label: String, original: String? = null,
        refresh: Boolean = false): NativePostGeometry {
        val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
            ?: return NativePostGeometry()
        val nodes = mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
        try {
            fun collect(node: android.view.accessibility.AccessibilityNodeInfo) {
                nodes.add(node)
                for (index in 0 until node.childCount) node.getChild(index)?.let(::collect)
            }
            collect(root)
            // Native child infos are cached independently. Final measurements
            // refresh their exact nodes through Android's cache-bypassing API.
            fun bounds(node: android.view.accessibility.AccessibilityNodeInfo): Rect? =
                if (!refresh || node.refresh()) Rect().also(node::getBoundsInScreen) else null
            val viewport = nodes.firstOrNull { it.isScrollable && it.isVisibleToUser }?.let(::bounds)
                ?: return NativePostGeometry()
            val references = if (referenceNumber(label) == null) emptyList() else nodes.mapNotNull { node ->
                referenceNumber(node.text?.toString())?.takeIf {
                    node.isVisibleToUser && bounds(node)?.let { Rect.intersects(it, viewport) } == true
                }
            }
            val range = references.minOrNull()?.let { it..requireNotNull(references.maxOrNull()) }
            val geometry = NativePostGeometry(viewport, referencePosts = range)
            val bodyNode = nodes.firstOrNull { it.text?.toString() == label }
                ?: return geometry
            val body = bounds(bodyNode)?.takeIf { bodyNode.isVisibleToUser && Rect.intersects(it, viewport) }
                ?: return geometry
            val originalNode = original?.let { text -> nodes.firstOrNull { it.text?.toString() == text } }
            val quoted = originalNode?.let { node -> bounds(node)?.takeIf { node.isVisibleToUser } }
            val visible = geometry.copy(body = body, original = quoted)
            var parent = bodyNode
            repeat(8) {
                val box = bounds(parent) ?: return visible
                if (parent.isClickable && box.width() >= device.displayWidth * 0.8 && box.contains(body))
                    return visible.copy(card = box)
                parent = parent.parent?.also(nodes::add) ?: return visible
            }
            return visible
        } finally { nodes.forEach { it.recycle() } }
    }

    /** Fast native movement followed by stopping the finger, as when returning to read. */
    private fun dragToRest(x: Float, from: Float, to: Float) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, y: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            try {
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                assertTrue("The actual native gesture must be accepted", automation.injectInputEvent(event, true))
            } finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN, from)
        var y = from
        var primary: Throwable? = null
        try {
            val samples = 8
            for (step in 1..samples) {
                SystemClock.sleep(8)
                y = from + (to - from) * step / samples
                send(MotionEvent.ACTION_MOVE, y)
            }
            SystemClock.sleep(120)
            send(MotionEvent.ACTION_MOVE, y)
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally { cleanupAfterTest(primary, { send(MotionEvent.ACTION_UP, y) }) }
    }
}
