package nl.davidengelen.redsquare

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.google.gson.JsonObject
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Actual screen-reader names, read feedback and disjoint native action bounds. */
class TalkBackActivityPreviewTest {
    @Test fun activityAuthorEventAndOptionsSpeakTheirContextAndCommittedReadState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "activity-talkback-evidence").apply { mkdirs() }
        NativeTalkBackObservation(folder).observe {
            withActivityInbox("talkback", evidenceFolder = "activity-talkback-evidence") { _, _ ->
                check(device.wait(Until.hasObject(By.text("Native Red Square reference post")), 15_000))
                click(automation, "Notifications")
                val author = "View profile for Anon-pncSjs"
                val event = "Open activity from Anon-pncSjs"
                val options = "Activity options for Anon-pncSjs"
                check(device.wait(Until.hasObject(By.text("1 unread")), 15_000))
                val authorBounds = Rect().also { actionNode(author).getBoundsInScreen(it) }
                val eventBounds = Rect().also { actionNode(event, clickLabel = true).getBoundsInScreen(it) }
                val optionsBounds = Rect().also { actionNode(options).getBoundsInScreen(it) }
                val minimum = 48 * context.resources.displayMetrics.density
                for (bounds in listOf(authorBounds, eventBounds, optionsBounds)) {
                    assertTrue(bounds.width() >= minimum && bounds.height() >= minimum)
                }
                assertFalse(Rect.intersects(authorBounds, eventBounds))
                assertFalse(Rect.intersects(authorBounds, optionsBounds))
                assertFalse(Rect.intersects(eventBounds, optionsBounds))
                focusAndHear("author", author, folder)
                focusAndHear("event", event, folder, clickLabel = true)
                assertTrue("Actual event speech includes the event type", completedFragments(device, "Liked your post").isNotEmpty())
                assertTrue("Actual event speech includes unread state", completedFragments(device, "Unread").isNotEmpty())
                focusAndHear("options", options, folder)
                assertTrue(actionNode(options).performAction(AccessibilityNodeInfo.ACTION_CLICK))
                click(automation, "Mark as read")
                check(device.wait(Until.hasObject(By.text("No unread activity")), 15_000))
                focusAndHear("read-event", event, folder, clickLabel = true)
                assertTrue("Committed read speech completes", completedFragments(device, "Read").isNotEmpty())
                assertTrue("Committed read state is spoken", fragments(device, "Read").any {
                    Regex("\\bRead\\b").containsMatchIn(it)
                })
                File(folder, "targets.json").writeText(JsonObject().apply {
                    addProperty("authorBounds", authorBounds.flattenToString())
                    addProperty("eventBounds", eventBounds.flattenToString())
                    addProperty("optionsBounds", optionsBounds.flattenToString())
                    addProperty("minimumPixels", minimum)
                    addProperty("disjoint", true)
                    addProperty("serviceProcess", serviceProcess)
                }.toString())
            }
        }
    }
}
