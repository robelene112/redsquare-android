package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test

/** Actual signed archive results and failures, never a synthetic screen state. */
class ActivityAvailabilityTest : ActivityAvailabilityFixture(320)
class ActivityAvailabilityPixelTest : ActivityAvailabilityFixture(412)

abstract class ActivityAvailabilityFixture(private val widthDp: Int) {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun failedAndPartialReadsNeverClaimAnEmptyOrFullyReadInbox() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "activity-availability-evidence").apply { mkdirs() }
        val empty = context.getString(R.string.activity_empty)
        withActivityInbox("availability", emptyList(), evidenceFolder = "activity-availability-evidence") { activity, peer ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
            ui.withPostViewports(activity, folder, "availability", scales = listOf(1f, 1.3f, 2f), widthDp = widthDp) { label, _ ->
                peer.goOffline()
                ui.onNode(hasText("Notifications") and hasClickAction()).performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("Activity unavailable").isDisplayed() }
                ui.onAllNodesWithText(empty).assertCountEquals(0)
                ui.onAllNodesWithText("No unread activity").assertCountEquals(0)
                ui.onNodeWithText("Retry").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                captureNativeScreen(File(folder, "$label-first-failure.png"), "Activity unavailable")
                peer.rejectNewConnections = false
                peer.rejectedArchiveKinds = setOf("RedSquareLike")
                ui.onNodeWithText("Retry").performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("Some activity could not be loaded.").isDisplayed() }
                ui.onAllNodesWithText(empty).assertCountEquals(0)
                ui.onAllNodesWithText("No unread activity").assertCountEquals(0)
                captureNativeScreen(File(folder, "$label-partial.png"), "Some activity could not be loaded.")
                peer.rejectedArchiveKinds = emptySet()
                ui.onNodeWithText("Retry").performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText(empty).isDisplayed() }
                ui.onNodeWithText("No unread activity").assertIsDisplayed()
                ui.onAllNodesWithText("Activity unavailable").assertCountEquals(0)
                captureNativeScreen(File(folder, "$label-empty.png"), empty)
                ui.onNode(hasText("Home") and hasClickAction()).performClick()
            }
        }
    }

    @Test fun retainedReadEventsRemainAvailableDuringAnOutageAndRecoverWithoutReset() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "activity-availability-evidence").apply { mkdirs() }
        withActivityInbox("retained-read", evidenceFolder = "activity-availability-evidence") { activity, peer ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
            ui.withPostViewports(activity, folder, "retained-read", scales = listOf(1f, 2f), widthDp = widthDp) { label, _ ->
                ui.onNode(hasText("Notifications") and hasClickAction()).performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("Liked your post").isDisplayed() && ui.refreshableContentReady() }
                ui.onNodeWithContentDescription("Activity options for Anon-pncSjs").performClick()
                if (ui.onNode(hasText("Mark as read") and isEnabled()).isDisplayed()) ui.onNodeWithText("Mark as read").performClick()
                else androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
                ui.waitUntil(15_000) { ui.onNodeWithText("No unread activity").isDisplayed() }
                peer.goOffline()
                ui.refreshVisibleView()
                ui.waitUntil(15_000) { ui.onNodeWithText("Could not refresh activity.").isDisplayed() }
                ui.onNodeWithText("In available history").assertIsDisplayed()
                ui.onNodeWithText("Liked your post").assertIsDisplayed()
                ui.onAllNodesWithText("Unread").assertCountEquals(0)
                captureNativeScreen(File(folder, "$label-cached-outage.png"), "Could not refresh activity.")
                activity.recreate()
                ui.waitUntil(15_000) { ui.onNodeWithText("Could not refresh activity.").isDisplayed() &&
                    ui.onNodeWithText("In available history").isDisplayed() }
                ui.onAllNodesWithText("Unread").assertCountEquals(0)
                peer.rejectNewConnections = false
                ui.onNodeWithText("Retry").performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("No unread activity").isDisplayed() && ui.refreshableContentReady() &&
                    ui.onAllNodesWithText("Could not refresh activity.").fetchSemanticsNodes().isEmpty() &&
                    ui.onAllNodesWithText("Some activity could not be loaded.").fetchSemanticsNodes().isEmpty() }
                ui.onNodeWithText("Liked your post").assertIsDisplayed()
                captureNativeScreen(File(folder, "$label-recovered.png"), "Liked your post")
                ui.onNode(hasText("Home") and hasClickAction()).performClick()
            }
        }
    }
}
