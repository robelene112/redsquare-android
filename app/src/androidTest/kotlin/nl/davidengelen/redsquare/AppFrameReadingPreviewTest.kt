package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppFrameReadingPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun returningFromChatRetainsTheScrolledFeedThroughRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val rows = instrumentation.context.assets.open("native-feed-scroll-reference.json").reader().use {
            JsonParser.parseReader(it).asJsonObject.get("initialRows").toString()
        }
        lateinit var peer: ControlledFeedPeer
        withAppFrameFixture("reading-position", "app-frame-reading-evidence", rows, configurePeer = { peer = it }) { activity ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Reference post 20").isDisplayed() }
            ui.onNode(hasScrollAction()).performScrollToNode(hasText("Reference post 05"))
            val before = ui.onNodeWithText("Reference post 05").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
            capture("scrolled-feed-before-chat.png")
            ui.onNode(hasText("Chat") and hasClickAction()).performClick()
            ui.waitUntil(10_000) { ui.onNodeWithText("New chat").isDisplayed() }
            ui.onNode(hasText("Chat") and isSelected()).assertIsDisplayed()
            ui.onNodeWithText("Back up your wallet").assertDoesNotExist()
            ui.onNode(hasText("Home") and hasClickAction()).performClick()
            assertReadingPosition(before)
            capture("scrolled-feed-after-chat.png")
            activity.recreate()
            assertReadingPosition(before)
            capture("scrolled-feed-after-recreation.png")
            ui.waitUntil(5_000) { ui.refreshableContentReady() }
            peer.rejectNewConnections = true
            ui.refreshKeepingReadingPosition()
            assertOutageDetails()
            assertReadingPosition(before)
            capture("scrolled-outage-before-recreation.png")
            activity.recreate()
            assertOutageDetails()
            assertReadingPosition(before)
            capture("scrolled-outage-after-recreation.png")
            // A physical pull starts at the top after the user's deliberate scroll.
            // The earlier anchor assertions remain unchanged for accessible retained-position refresh.
            ui.onNodeWithTag("feed-post-list").performScrollToIndex(0)
            ui.onNodeWithText("Reference post 20").assertIsDisplayed()
            ui.waitUntil(5_000) { ui.refreshableContentReady() }
            // Delay the actual connection rejection so manual feedback is observable. Automatic outage
            // polling never activates the pull indicator, and cannot satisfy this assertion.
            peer.rejectionDelayMillis = 2_000
            val pullAnchor = ui.onNodeWithText("Reference post 20").fetchSemanticsNode().boundsInRoot.top
            val attempts = peer.connectionAttempts.get()
            val spinner = SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate)
            ui.onAllNodes(spinner).assertCountEquals(0)
            ui.pullToRefresh("feed-post-list")
            ui.waitUntil(5_000) { ui.onNode(spinner).isDisplayed() }
            ui.waitUntil(20_000) { peer.connectionAttempts.get() > attempts && ui.refreshableContentReady() }
            assertOutageDetails()
            assertEquals("Physical pull keeps the user-selected top reading anchor after connection rejection", pullAnchor,
                ui.onNodeWithText("Reference post 20").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top, 1f)
            capture("physical-pull-after-retained-reading.png")
        }
    }

    private fun assertOutageDetails() {
        ui.waitUntil(20_000) { ui.onNodeWithText("Node unavailable").isDisplayed() }
        ui.onNodeWithContentDescription("Details: Node unavailable").performClick()
        ui.onNodeWithText("Posts could not be refreshed. Try again.").assertIsDisplayed()
        ui.onNodeWithText("Done").performClick()
    }

    private fun assertReadingPosition(expected: Float) {
        ui.waitUntil(15_000) { ui.onNodeWithText("Reference post 05").isDisplayed() &&
            ui.onNodeWithText("Back up your wallet").isDisplayed() && ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
        assertEquals("Return to the same visible reading position", expected,
            ui.onNodeWithText("Reference post 05").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    private fun capture(name: String) = captureNativeScreen(File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "app-frame-reading-evidence/$name"))
}
