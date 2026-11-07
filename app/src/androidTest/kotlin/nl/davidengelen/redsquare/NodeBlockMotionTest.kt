package nl.davidengelen.redsquare

import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.gson.JsonParser
import java.io.File
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import mockwebserver3.*
import org.junit.Assert.*
import org.junit.Test

/** Real packet arrival, physical input and display frames at actual system scales0/1. */
class NodeBlockMotionTest {
    @Test fun normalAndZeroMotionKeepChecksInterruptibleAndDoNotRevealAReadingHeader() {
        for (scale in listOf(0, 1)) withNativeModuleMotionScale(scale) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val device = UiDevice.getInstance(instrumentation)
            val folder = File(context.cacheDir, "node-motion-evidence").apply { mkdirs() }
            val mode = AtomicReference("fresh")
            val advancing = CountDownLatch(1)
            val advanceStarted = AtomicLong()
            val rows = instrumentation.context.assets.open("native-feed-scroll-reference.json").reader().use {
                JsonParser.parseReader(it).asJsonObject.get("initialRows").toString()
            }
            fun click(text: String) = requireNotNull(device.wait(Until.findObject(By.text(text).enabled(true)), 10_000)).click()
            fun awaitText(text: String) { check(device.wait(Until.hasObject(By.text(text)), 10_000)) { text } }
            fun movie(name: String, seconds: Int = 5, action: () -> Unit) {
                val file = File(folder, "scale-$scale-$name.mp4")
                val openWindow = if (name == "reading") null else Rect().apply {
                    requireNotNull(nativeWindowNodes().firstOrNull()).getBoundsInScreen(this)
                }
                var actionFailure: Throwable? = null
                try {
                    val recording = recordNativeTransition(file, recordingSeconds = seconds) {
                        try {
                            action()
                            if (openWindow != null) {
                                val after = Rect().apply { requireNotNull(nativeWindowNodes().firstOrNull()).getBoundsInScreen(this) }
                                assertEquals("Changing block information must not resize the open details window", openWindow, after)
                            }
                        } catch (failure: Throwable) { actionFailure = failure }
                    }
                    val frames = retainNativeMotionFrames(file); frames.add("recording", recording)
                    frames.addProperty("actionAssertionsPassed", actionFailure == null)
                    File(folder, "scale-$scale-$name.json").writeText(frames.toString())
                } catch (captureFailure: Throwable) {
                    val primary = actionFailure
                    if (primary == null) throw captureFailure else primary.addSuppressed(captureFailure)
                }
                actionFailure?.let { throw it }
            }
            fun headerVisible(): Boolean = nativeWindowNodes().any { node ->
                val bounds = Rect().apply { node.getBoundsInScreen(this) }
                node.contentDescription?.toString() == "Connection details" && node.isVisibleToUser && !bounds.isEmpty
            }
            fun readingAnchor(): Pair<String, Rect> = requireNotNull(nativeWindowNodes().mapNotNull { node ->
                val text = node.text?.toString() ?: return@mapNotNull null
                if (!text.contains("Reference post ") || !node.isVisibleToUser) return@mapNotNull null
                val bounds = Rect().apply { node.getBoundsInScreen(this) }
                if (bounds.isEmpty) null else text to bounds
            }.minByOrNull { it.second.top })
            MockWebServer().use { node ->
                node.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        check(request.target == "/options")
                        val current = mode.get()
                        if (current == "offline") return MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()
                        val height = when (current) { "old" -> 8888; "recovered" -> 9999; "advance" -> 10001; else -> 7777 }
                        val timestamp = System.currentTimeMillis() - if (current == "old") 30 * 60_000 else 1_000
                        if (current == "advance") { advanceStarted.set(SystemClock.elapsedRealtime()); advancing.countDown() }
                        return MockResponse.Builder().body("""{"blockchain":{"last_block_id":$height,"last_timestamp":$timestamp,"last_block_hash":"${"bc".repeat(32)}"}}""")
                            .bodyDelay(if (current == "advance") 4_000 else 700, TimeUnit.MILLISECONDS).build()
                    }
                }
                node.start(InetAddress.getByName("127.0.0.1"), 0)
                withAppFrameFixture("motion-$scale", "node-motion-evidence", rows,
                    nodeBlockApiUrl = "http://127.0.0.1:${node.port}/options") { activity ->
                    awaitText("Reference post 20"); click("Later"); awaitText("Block 7,777")
                    requireNotNull(device.findObject(By.desc("Connection details"))).click()
                    awaitText("Connection details")
                    movie("old") { mode.set("old"); click("Check again"); awaitText("Older block information"); awaitText("Block 8,888") }
                    movie("disconnected") { mode.set("offline"); click("Check again"); awaitText("Could not reach this node") }
                    movie("reconnected") { mode.set("recovered"); click("Check again"); awaitText("Block 9,999") }
                    val checkBounds = requireNotNull(device.findObject(By.text("Check again"))).visibleBounds
                    movie("reading", 5) {
                        mode.set("advance"); tap(checkBounds)
                        check(advancing.await(2, TimeUnit.SECONDS))
                        val doneBounds = currentWorkingActionBounds("Done")
                        File(folder, "scale-$scale-working-done-target.json").writeText(
                            "{\"left\":${doneBounds.left},\"top\":${doneBounds.top},\"right\":${doneBounds.right},\"bottom\":${doneBounds.bottom}}")
                        tap(doneBounds)
                        check(device.wait(Until.gone(By.text("Connection details")), 3_000))
                        val list = nativeVerticalScrollBounds()
                        // Stop the physical drag before releasing it into the reading interval.
                        // An ordinary swipe injects a fling, whose native bounds can lag the display.
                        dragToReadingPosition(list)
                        val hiddenDeadline = SystemClock.elapsedRealtime() + 3_000
                        while (headerVisible() && SystemClock.elapsedRealtime() < hiddenDeadline) SystemClock.sleep(32)
                        assertFalse("Reading must collapse the actual block-status header", headerVisible())
                        val hiddenAfter = SystemClock.elapsedRealtime() - advanceStarted.get()
                        assertTrue("Collapse occurs before the controlled4s response arrives", hiddenAfter < 4_000)
                        val firstAnchor = readingAnchor()
                        var anchor = firstAnchor
                        var stableSince = SystemClock.elapsedRealtime()
                        val settledDeadline = advanceStarted.get() + 3_500
                        while (SystemClock.elapsedRealtime() < settledDeadline &&
                            SystemClock.elapsedRealtime() - stableSince < 200) {
                            SystemClock.sleep(32)
                            val current = readingAnchor()
                            if (current != anchor) { anchor = current; stableSince = SystemClock.elapsedRealtime() }
                        }
                        assertTrue("The reading gesture must settle before the original4s response arrives",
                            SystemClock.elapsedRealtime() - stableSince >= 200 && SystemClock.elapsedRealtime() < settledDeadline)
                        File(folder, "scale-$scale-reading-start.json").writeText(com.google.gson.JsonObject().apply {
                            addProperty("firstObservedText", firstAnchor.first)
                            addProperty("firstObservedBounds", firstAnchor.second.toShortString())
                            addProperty("settledText", anchor.first)
                            addProperty("settledBounds", anchor.second.toShortString())
                            addProperty("settledAfterMillis", SystemClock.elapsedRealtime() - advanceStarted.get())
                        }.toString())
                        // Observe the real delayed response interval, with no new input or test clock.
                        SystemClock.sleep(4_500)
                        assertFalse("A new block must not reveal a collapsed header", headerVisible())
                        assertEquals("Block arrival preserves the exact visible reading anchor", anchor, readingAnchor())
                        captureNativeScreen(File(folder, "scale-$scale-block-arrived-collapsed.png"))
                        check(device.swipe(list.centerX(), list.top + list.height() / 5,
                            list.centerX(), list.bottom - list.height() / 5, 16))
                        awaitText("Block 10,001")
                        File(folder, "scale-$scale-reading-timing.json").writeText("{\"collapsedAfterMillis\":$hiddenAfter,\"responseDelayMillis\":4000}")
                    }
                    observeModuleWindowMotionScale(activity, scale, folder)
                    captureNativeScreen(File(folder, "scale-$scale-returned.png"))
                }
            }
        }
    }

    /** Observe the current native target after the working notice changes dialog geometry.
     * UiDevice idle waits would consume the held request, so sample the actual accessibility
     * window directly and still inject physical input inside the original response interval. */
    private fun currentWorkingActionBounds(label: String): Rect {
        val deadline = SystemClock.elapsedRealtime() + 1_000
        var previous: Rect? = null
        var stableSince = 0L
        while (SystemClock.elapsedRealtime() < deadline) {
            val nodes = nativeWindowNodes()
            val working = nodes.any { it.text?.toString() == "Checking block info" && it.isVisibleToUser }
            val text = nodes.singleOrNull { it.text?.toString() == label && it.isVisibleToUser && it.isEnabled }
            val target = if (text?.isClickable == true) text else text?.parent?.takeIf { it.isClickable && it.isEnabled }
            val bounds = target?.let { Rect().apply { it.getBoundsInScreen(this) } }?.takeUnless { it.isEmpty }
            val now = SystemClock.elapsedRealtime()
            if (working && bounds != null) {
                if (bounds == previous && now - stableSince >= 100) return bounds
                if (bounds != previous) { previous = bounds; stableSince = now }
            } else { previous = null; stableSince = now }
            SystemClock.sleep(16)
        }
        error("The actual working dialog must expose its stable enabled $label target inside1s")
    }

    private fun dragToReadingPosition(list: Rect) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val started = SystemClock.uptimeMillis()
        val x = list.exactCenterX()
        val from = (list.bottom - list.height() / 5).toFloat()
        val to = (list.top + list.height() / 5).toFloat()
        fun send(action: Int, y: Float) {
            val event = MotionEvent.obtain(started, SystemClock.uptimeMillis(), action, x, y, 0)
            try { event.source = InputDevice.SOURCE_TOUCHSCREEN; assertTrue(automation.injectInputEvent(event, true)) }
            finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN, from)
        var y = from
        try {
            for (step in 1..16) {
                SystemClock.sleep(8)
                y = from + (to - from) * step / 16
                send(MotionEvent.ACTION_MOVE, y)
            }
            // Release at rest. This models stopping to read, not continuing a kinetic scroll.
            SystemClock.sleep(120)
            send(MotionEvent.ACTION_MOVE, y)
        } finally { send(MotionEvent.ACTION_UP, y) }
    }

    private fun tap(bounds: Rect) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val started = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(started, SystemClock.uptimeMillis(), action,
                bounds.exactCenterX(), bounds.exactCenterY(), 0)
            try { event.source = InputDevice.SOURCE_TOUCHSCREEN; assertTrue(automation.injectInputEvent(event, true)) }
            finally { event.recycle() }
        }
    }
}
