package nl.davidengelen.redsquare

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Real upstream TalkBack service output, not an AccessibilityEvent proxy or a speech mock. */
class TalkBackStatusPreviewTest {
    @Test fun importantOutageAnnouncesOnceWithoutMovingAccessibilityFocus() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = File(instrumentation.targetContext.cacheDir, "status-talkback-evidence").apply { mkdirs() }
        NativeTalkBackObservation(folder).observe {
            val rows = instrumentation.context.assets.open("native-feed-scroll-reference.json").reader().use {
                JsonParser.parseReader(it).asJsonObject.get("initialRows").toString()
            }
            lateinit var peer: ControlledFeedPeer
            withAppFrameFixture("talkback-outage", "status-talkback-evidence", rows, configurePeer = { peer = it }) {
                check(device.wait(Until.hasObject(By.text("Reference post 20")), 15_000))
                click(automation, "Later")
                check(device.wait(Until.gone(By.text("Back up your wallet")), 5_000))
                device.waitForIdle(5_000)
                // TalkBack handles initial window focus asynchronously after Android becomes idle.
                // Observe its actual first header focus before making the single reader-focus action.
                waitFor(5_000) { focusedText(automation) == "Wallet" }
                File(folder, "startup-speech.txt").writeText(speechLog(device))
                val post = node(automation, "Reference post 20")
                assertTrue(post.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
                waitFor(5_000) { focusedText(automation) == "Reference post 20" }
                SystemClock.sleep(1_000) // A quiet baseline after the deliberate focus announcement.
                check(device.wait(Until.hasObject(
                    By.clickable(true).enabled(true).hasDescendant(By.text("Refresh"))), 5_000))
                device.executeShellCommand("logcat -c")
                peer.rejectNewConnections = true
                click(automation, "Refresh")
                check(device.wait(Until.hasObject(By.text("Node unavailable")), 20_000))
                waitFor(10_000) { fragments(device, "Node unavailable").isNotEmpty() }
                waitFor(10_000) { completedFragments(device, "Node unavailable").size == 1 }
                assertEquals("Important outage announces once", 1, fragments(device, "Node unavailable").size)
                assertEquals("A live status must not steal the reader's focus", "Reference post 20", focusedText(automation))
                capture(automation, File(folder, "outage.png"))
                val requests = peer.connectionAttempts.get()
                SystemClock.sleep(12_000) // Observe at least two real background retry intervals.
                assertTrue("Quiet observation must include two actual retry attempts", peer.connectionAttempts.get() >= requests + 2)
                assertEquals("Polling the same failure must not repeat speech", 1, fragments(device, "Node unavailable").size)
                assertEquals("Reference post 20", focusedText(automation))
                assertEquals("Keep the same real TalkBack process throughout the observation", serviceProcess, talkbackProcess(device))
                peer.rejectNewConnections = false
                check(device.wait(Until.hasObject(By.text("Back online")), 20_000))
                waitFor(10_000) { fragments(device, "Back online").isNotEmpty() }
                waitFor(10_000) { completedFragments(device, "Back online").size == 1 }
                assertEquals("Recovery announces once", 1, fragments(device, "Back online").size)
                assertEquals("Reference post 20", focusedText(automation))
                assertEquals(serviceProcess, talkbackProcess(device))
                capture(automation, File(folder, "recovered.png"))
                File(folder, "speech-transcript.txt").writeText(speechLog(device))
                File(folder, "accessibility-after.txt").writeText(device.executeShellCommand("dumpsys accessibility"))
                File(folder, "result.json").writeText(JsonObject().apply {
                    addProperty("tool", "google/talkback229212fdf5842191d0a93fc95d9ca1423b346866")
                    addProperty("outageFragments", fragments(device, "Node unavailable").size)
                    addProperty("recoveryFragments", fragments(device, "Back online").size)
                    addProperty("outageEngineCompletions", completedFragments(device, "Node unavailable").joinToString())
                    addProperty("recoveryEngineCompletions", completedFragments(device, "Back online").joinToString())
                    addProperty("focus", focusedText(automation))
                    addProperty("physicalAudibility", false)
                    addProperty("serviceProcess", serviceProcess)
                    addProperty("serviceLabel", serviceLabel)
                }.toString())
            }
        }
    }
}
