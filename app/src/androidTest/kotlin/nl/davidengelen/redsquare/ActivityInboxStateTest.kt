package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertTrue
import java.io.File
import org.junit.Rule
import org.junit.Test

/** Loading, empty, unavailable and retained activity through an ordinary node outage. */
class ActivityInboxStateTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun emptyAndUnavailableActivityKeepRecoveryReachableWithLargeText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "activity-state-evidence").apply { mkdirs() }
        val empty = context.getString(R.string.activity_empty)
        withActivityInbox("empty", emptyList(), evidenceFolder = "activity-state-evidence") { activity, peer ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
            peer.withholdArchiveReply = true
            ui.onNode(hasText("Notifications") and hasClickAction()).performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Loading notifications...").isDisplayed() }
            ui.onAllNodesWithText("No unread activity").assertCountEquals(0)
            captureNativeScreen(File(folder, "loading.png"), "Loading notifications...")
            peer.withholdArchiveReply = false
            peer.disconnectAuthenticatedSockets()
            ui.waitUntil(15_000) { ui.onNode(hasText("Refresh") and isEnabled()).isDisplayed() }
            ui.onNodeWithText("Refresh").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText(empty).isDisplayed() }
            ui.onNode(hasText("Home") and hasClickAction()).performClick()
            ui.withPostViewports(activity, folder, "empty-activity", scales = listOf(2f)) { label, _ ->
                ui.onNode(hasText("Notifications") and hasClickAction()).performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText(empty).isDisplayed() }
                ui.onNodeWithText("No unread activity").assertIsDisplayed()
                captureNativeScreen(File(folder, "$label-empty.png"), empty)
                peer.rejectNewConnections = true
                ui.onNodeWithText("Refresh").performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("Could not refresh activity.").isDisplayed() }
                val details = context.getString(R.string.status_details_for, "Could not refresh activity.")
                ui.onNodeWithContentDescription(details).performClick()
                ui.onNodeWithText(context.getString(R.string.notifications_read_error_detail)).performScrollTo().assertIsDisplayed()
                captureNativeScreen(File(folder, "$label-offline-details.png"))
                ui.onNodeWithText("Done").performClick()
                ui.onNode(hasText("Refresh") and hasClickAction()).assertIsDisplayed()
                // Material's40dp painted icon is inside a48dp native touch/accessibility target.
                val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                val refresh = requireNotNull(device.wait(Until.findObject(By.text("Refresh")), 5_000))
                val minimum = 48 * context.resources.displayMetrics.density
                assertTrue(refresh.isClickable && refresh.visibleBounds.width() >= minimum &&
                    refresh.visibleBounds.height() >= minimum)
                peer.rejectNewConnections = false
                ui.onNodeWithText("Refresh").performClick()
                ui.waitUntil(15_000) { ui.onNode(hasText("Refresh") and isEnabled()).isDisplayed() &&
                    ui.onAllNodesWithText("Could not refresh activity.").fetchSemanticsNodes().isEmpty() }
                captureNativeScreen(File(folder, "$label-recovered.png"), empty)
                ui.onNode(hasText("Home") and hasClickAction()).performClick()
            }
        }
        withActivityInbox("unavailable", listOf("missingOriginalLike"), evidenceFolder = "activity-state-evidence") { activity, peer ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
            ui.withPostViewports(activity, folder, "unavailable-activity", scales = listOf(2f)) { label, _ ->
                ui.onNode(hasText("Notifications") and hasClickAction()).performClick()
                ui.waitUntil(15_000) { ui.onNode(hasText("Refresh") and isEnabled()).isDisplayed() &&
                    ui.onNodeWithText("Liked a post").isDisplayed() &&
                    ui.onNodeWithText("Post preview unavailable").isDisplayed() }
                ui.onNodeWithText("Post preview unavailable").assertIsDisplayed()
                captureNativeScreen(File(folder, "$label-missing-preview.png"), "Liked a post")
                peer.rejectNewConnections = true
                ui.onNodeWithText("Refresh").performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("Could not refresh activity.").isDisplayed() }
                ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Liked a post"))
                ui.onNodeWithText("Post preview unavailable").assertIsDisplayed()
                captureNativeScreen(File(folder, "$label-cached-offline.png"), "Post preview unavailable")
                peer.rejectNewConnections = false
                ui.onNodeWithText("Liked a post").performClick()
                val unavailable = context.getString(R.string.notifications_target_unavailable)
                ui.waitUntil(15_000) { ui.onNodeWithText(unavailable).isDisplayed() }
                ui.onNodeWithText("Retry").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                captureNativeScreen(File(folder, "$label-unavailable-target.png"), unavailable)
                ui.onNodeWithText("Back").performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("Liked a post").isDisplayed() }
                ui.onNode(hasText("Home") and hasClickAction()).performClick()
            }
        }
    }
}
