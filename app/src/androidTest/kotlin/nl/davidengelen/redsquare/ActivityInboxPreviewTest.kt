package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ActivityInboxPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun anActivityHasDistinctAuthorAndEventTargetsAndDurableIndividualReadFeedback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "activity-inbox-evidence").apply { mkdirs() }
        withActivityInbox("individual-read") { activity, _ ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
            ui.onNode(hasText("Notifications") and hasClickAction()).performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("1 unread").isDisplayed() }
            ui.onNodeWithText("Unread").assertIsDisplayed()
            val author = ui.onNodeWithContentDescription("View profile for Anon-pncSjs")
            val event = ui.onNode(hasText("Liked your post") and hasClickAction())
            val authorBounds = author.getUnclippedBoundsInRoot()
            val eventBounds = event.getUnclippedBoundsInRoot()
            assertTrue((authorBounds.right - authorBounds.left).value >= 48 && (authorBounds.bottom - authorBounds.top).value >= 48)
            assertTrue((eventBounds.right - eventBounds.left).value >= 48 && (eventBounds.bottom - eventBounds.top).value >= 48)
            assertTrue(authorBounds.right <= eventBounds.left || eventBounds.right <= authorBounds.left ||
                authorBounds.bottom <= eventBounds.top || eventBounds.bottom <= authorBounds.top)
            captureNativeScreen(File(folder, "unread-activity.png"), "Liked your post")
            ui.onNodeWithContentDescription("Activity options for Anon-pncSjs").performClick()
            ui.onNodeWithText("Mark as read").assertIsDisplayed().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("No unread activity").isDisplayed() }
            ui.onAllNodesWithText("Unread").assertCountEquals(0)
            captureNativeScreen(File(folder, "activity-marked-read.png"))
            activity.recreate()
            ui.waitUntil(15_000) { ui.onNodeWithText("No unread activity").isDisplayed() }
            ui.onAllNodesWithText("Unread").assertCountEquals(0)
            ui.onNode(hasText("Liked your post") and hasClickAction()).performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Conversation").isDisplayed() }
            ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
            ui.onNodeWithText("Back").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("No unread activity").isDisplayed() }
            ui.onNodeWithContentDescription("View profile for Anon-pncSjs").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Profile").isDisplayed() }
            ui.inspectProfileIdentity("pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU")
        }
    }
}
