package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/** More than a head page of new verified Likes, while older paging is already in progress. */
class ActivityGroupingBurstTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun catchingUpANewBurstDoesNotSkipItsMiddleOrForgetTheOlderContinuation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = File(instrumentation.targetContext.cacheDir, "activity-group-burst-evidence").apply { mkdirs() }
        val reference = instrumentation.context.assets.open("native-notification-reference.json").reader().use {
            JsonParser.parseReader(it).asJsonObject
        }
        val oldTime = 1_700_000_100_000L
        val newTime = oldTime + 1_000
        fun like(time: Long) = signedActivityLike(reference, time)
        val old = (0L..9L).flatMap { index -> val row = like(oldTime + index); List(30) { row } }
        val burst = (0L..149L).map { like(newTime + it) }
        val additional = AtomicReference(old)
        val bounds = CopyOnWriteArrayList<Long>()
        fun expectTotal(count: Int) {
            ui.onNodeWithContentDescription("Activity options for Anon-pncSjs").performClick()
            ui.onNodeWithText("People who liked this post").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("$count likes from 1 person").isDisplayed() }
            captureNativeScreen(File(folder, "observed-$count.png"), "$count likes from 1 person")
            ui.onNodeWithText("Done").performClick()
        }
        withActivityInbox("burst-catchup", emptyList(), evidenceFolder = "activity-group-burst-evidence",
            additionalEvents = { additional.get() }, onArchiveRead = { query ->
                if (query.get("field1")?.asString == "RedSquareLike" && query.has("created_earlier_than"))
                    bounds.add(query.get("created_earlier_than").asLong)
            }) { _, _ ->
            try {
                ui.waitUntil(15_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
                ui.onNode(hasText("Notifications") and hasClickAction()).performClick()
                ui.waitUntil(15_000) { ui.refreshableContentReady() && ui.onNodeWithText("1 unread").isDisplayed() }
                expectTotal(4)
                ui.onNodeWithText("Load more activity").performClick()
                ui.waitUntil(15_000) { bounds.contains(oldTime + 7) && ui.refreshableContentReady() }
                expectTotal(7)
                ui.onNodeWithText("Mark all as read").performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("No unread activity").isDisplayed() }
                additional.set(old + burst)
                ui.waitUntil(15_000) { ui.onNodeWithText("1 unread").isDisplayed() && ui.refreshableContentReady() }
                expectTotal(107)
                ui.onNodeWithText("Load more activity").performClick()
                ui.waitUntil(15_000) { ui.refreshableContentReady() }
                expectTotal(157)
                assertTrue("Catch up above the retained tail before continuing older history", bounds.contains(newTime + 51))
                ui.onNodeWithText("Load more activity").performClick()
                ui.waitUntil(15_000) { bounds.contains(oldTime + 4) && ui.refreshableContentReady() }
                expectTotal(160)
                ui.onNodeWithText("Mark all as read").performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("No unread activity").isDisplayed() }
                ui.refreshVisibleView()
                ui.waitUntil(15_000) { ui.refreshableContentReady() }
                ui.onNodeWithText("Load more activity").performClick()
                ui.waitUntil(15_000) { bounds.contains(oldTime + 1) && ui.refreshableContentReady() }
                ui.onNodeWithText("No unread activity").assertIsDisplayed()
                ui.onAllNodesWithText("Unread").assertCountEquals(0)
                expectTotal(160)
            } finally { File(folder, "query-bounds.json").writeText(bounds.joinToString(",", "[", "]")) }
        }
    }
}
