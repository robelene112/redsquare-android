package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Real feed, controlled read-only socket, actual status recovery and reading anchor. */
class StatusRecoveryPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun nodeOutageDetailsAndRecoveryPreserveTheScrolledFeed() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val rows = instrumentation.context.assets.open("native-feed-scroll-reference.json").reader().use {
            JsonParser.parseReader(it).asJsonObject.get("initialRows").toString()
        }
        lateinit var peer: ControlledFeedPeer
        withAppFrameFixture("status-recovery", "status-recovery-evidence", rows, configurePeer = { peer = it }) {
            ui.waitUntil(15_000) { ui.onNodeWithText("Reference post 20").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.waitUntil(5_000) { !ui.onNodeWithText("Back up your wallet").isDisplayed() }
            ui.onNode(hasScrollAction()).performScrollToNode(hasText("Reference post 05"))
            val before = ui.onNodeWithText("Reference post 05").fetchSemanticsNode().boundsInRoot.top
            capture("reading-before-outage.png")
            ui.waitUntil(5_000) { ui.onNode(hasText("Refresh") and isEnabled() and hasClickAction()).isDisplayed() }
            peer.rejectNewConnections = true
            ui.onNodeWithText("Refresh").performClick()
            ui.waitUntil(20_000) { ui.onNodeWithText("Node unavailable").isDisplayed() }
            ui.onNodeWithText("Details").assertHasClickAction().performClick()
            ui.onNodeWithText("Posts could not be refreshed. Try again.").assertIsDisplayed()
            capture("outage-details.png")
            ui.onNodeWithText("Done").performClick()
            assertPosition(before)
            capture("reading-during-outage.png")
            peer.rejectNewConnections = false
            ui.waitUntil(20_000) { ui.onNodeWithText("Back online").isDisplayed() }
            assertPosition(before)
            capture("reading-recovered.png")
            ui.waitUntil(5_000) { !ui.onNodeWithText("Back online").isDisplayed() }
            assertPosition(before)
            capture("reading-after-recovery.png")
        }
    }

    @Test fun openedDetailsStayReadableAfterRecoveryAndBannerRemoval() {
        lateinit var peer: ControlledFeedPeer
        withAppFrameFixture("details-recovery", "status-recovery-evidence", configurePeer = { peer = it }) {
            ui.waitUntil(15_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.waitUntil(5_000) { ui.onNode(hasText("Refresh") and isEnabled() and hasClickAction()).isDisplayed() }
            peer.rejectNewConnections = true
            ui.onNodeWithText("Refresh").performClick()
            ui.waitUntil(20_000) { ui.onNodeWithText("Node unavailable").isDisplayed() }
            ui.onNodeWithContentDescription("Details: Node unavailable").performClick()
            peer.rejectNewConnections = false
            ui.waitUntil(20_000) { ui.onNodeWithText("Back online").isDisplayed() }
            ui.waitUntil(5_000) { !ui.onNodeWithText("Back online").isDisplayed() }
            ui.onNodeWithText("Posts could not be refreshed. Try again.").assertIsDisplayed()
            capture("details-remain-open-after-recovery.png")
            ui.onNodeWithText("Done").performClick()
            ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
            ui.onNodeWithText("Node unavailable").assertDoesNotExist()
            capture("details-closed-after-recovery.png")
        }
    }

    private fun assertPosition(expected: Float) {
        ui.waitForIdle()
        assertEquals("Status insertion and removal retain the visible reading anchor", expected,
            ui.onNodeWithText("Reference post 05").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    private fun capture(name: String) = captureNativeScreen(File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "status-recovery-evidence/$name"))
}
