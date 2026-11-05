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
                val recording = recordNativeTransition(file, recordingSeconds = seconds, dismiss = action)
                val frames = retainNativeMotionFrames(file); frames.add("recording", recording)
                File(folder, "scale-$scale-$name.json").writeText(frames.toString())
            }
            fun headerVisible(): Boolean = device.findObject(By.desc("Connection details"))?.visibleBounds?.isEmpty == false
            fun readingAnchor(): Pair<String, Rect> = requireNotNull(device.findObjects(By.textContains("Reference post "))
                .filter { !it.visibleBounds.isEmpty }.minByOrNull { it.visibleBounds.top }).let { it.text to it.visibleBounds }
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
                    val doneBounds = requireNotNull(device.findObject(By.text("Done"))).visibleBounds
                    movie("reading", 5) {
                        mode.set("advance"); tap(checkBounds)
                        check(advancing.await(2, TimeUnit.SECONDS))
                        tap(doneBounds)
                        check(device.wait(Until.gone(By.text("Connection details")), 3_000))
                        val list = requireNotNull(device.findObject(By.scrollable(true))).visibleBounds
                        check(device.swipe(list.centerX(), list.bottom - list.height() / 5,
                            list.centerX(), list.top + list.height() / 5, 16))
                        val hiddenDeadline = SystemClock.elapsedRealtime() + 3_000
                        while (headerVisible() && SystemClock.elapsedRealtime() < hiddenDeadline) SystemClock.sleep(32)
                        assertFalse("Reading must collapse the actual block-status header", headerVisible())
                        val hiddenAfter = SystemClock.elapsedRealtime() - advanceStarted.get()
                        assertTrue("Collapse occurs before the controlled4s response arrives", hiddenAfter < 4_000)
                        val anchor = readingAnchor()
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
