package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test

/** Independent signed actors, one exact post, and an individual substantive reply. */
class ActivityGroupingPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun severalPeopleLikingOnePostShareOneDirectDestinationWithoutSwallowingReplies() {
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "activity-group-evidence").apply { mkdirs() }
        withActivityInbox("grouped-likes", listOf("incomingLike", "boundaryIncomingLike", "olderIncomingLike", "incomingReply"),
            evidenceFolder = "activity-group-evidence") { _, _ ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
            ui.onNode(hasText("Notifications") and hasClickAction()).performClick()
            ui.waitUntil(15_000) { ui.refreshableContentReady() &&
                ui.onAllNodesWithText("Liked your post").fetchSemanticsNodes().isNotEmpty() }
            captureNativeScreen(File(folder, "one-target-before-assertion.png"))
            ui.onAllNodesWithText("Liked your post").assertCountEquals(1)
            ui.onNodeWithText("2 unread").assertIsDisplayed()
            ui.onNodeWithText("Replied to your post").assertIsDisplayed()
            ui.onNode(hasText("Liked your post") and hasClickAction()).performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Conversation").isDisplayed() }
            ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
        }
    }
}
