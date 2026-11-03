package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test

/** A populated signed Like moves through real recipient and author history requests. */
class LikeHistoryContractPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun activityActorProfileLoadsItsSignedLikeAndAvailableTarget() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "like-history-contract-evidence").apply { mkdirs() }
        withActivityInbox("recipient-and-author-history", evidenceFolder = "like-history-contract-evidence") { _, peer ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
            ui.onNode(hasText("Notifications") and hasClickAction()).performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("1 unread").isDisplayed() }
            ui.onNodeWithText("Liked your post").assertIsDisplayed()
            captureNativeScreen(File(folder, "recipient-history.png"))
            ui.onNodeWithContentDescription("View profile for Anon-pncSjs").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Profile").isDisplayed() }
            ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Likes") and hasClickAction())
            ui.onNodeWithText("Likes").performScrollTo().performClick()
            ui.revealProfileHistoryItem("Native Red Square reference post")
            captureNativeScreen(File(folder, "author-like-history.png"))
            peer.assertHealthy()
        }
    }
}
