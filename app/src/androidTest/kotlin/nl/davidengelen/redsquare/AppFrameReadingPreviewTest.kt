package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import android.graphics.Rect
import android.os.SystemClock
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
            val setupBy = SystemClock.elapsedRealtime() + 15_000
            fun remainingSetup(): Long = (setupBy - SystemClock.elapsedRealtime()).also {
                check(it > 0) { "The original15s reading setup must finish before recording its baseline" }
            }
            ui.waitUntil(remainingSetup()) { ui.onNodeWithText("Reference post 20").isDisplayed() &&
                ui.onNodeWithText("Back up your wallet").isDisplayed() }
            val list = ui.onNode(hasVerticalScrollAction())
            list.performScrollToNode(hasText("Reference post 05"))
            // Use the same expanded chrome that the return/recovery assertions inspect.
            // Place the reader in the middle, clear of transient status/header regions.
            ui.revealAppNavigation()
            ui.waitUntil(remainingSetup()) {
                ui.onAllNodes(appNavigationRevealAction()).fetchSemanticsNodes().isEmpty() &&
                    ui.onNodeWithText("Back up your wallet").isDisplayed()
            }
            val target = ui.onNodeWithText("Reference post 05")
            val viewport = list.getUnclippedBoundsInRoot()
            val targetTop = target.getUnclippedBoundsInRoot().top
            val readingTop = viewport.top + (viewport.bottom - viewport.top) / 2
            list.performSemanticsAction(SemanticsActions.ScrollBy) { scroll ->
                check(scroll(0f, (targetTop - readingTop).value * instrumentation.targetContext.resources.displayMetrics.density))
            }
            var previous: Pair<androidx.compose.ui.geometry.Rect, androidx.compose.ui.geometry.Rect>? = null
            var stableSince = SystemClock.elapsedRealtime()
            ui.waitUntil(remainingSetup()) {
                val frame = list.fetchSemanticsNode().boundsInRoot to target.fetchSemanticsNode().boundsInRoot
                val now = SystemClock.elapsedRealtime()
                if (frame != previous) { previous = frame; stableSince = now }
                val native = nativeWindowNodes().firstOrNull { it.text?.toString() == "Reference post 05" }
                    ?.takeIf { it.refresh() && it.isVisibleToUser }
                    ?.let { Rect().apply { it.getBoundsInScreen(this) } }
                frame.second.top >= frame.first.top && frame.second.bottom <= frame.first.bottom &&
                    native != null && kotlin.math.abs(native.top - frame.second.top) <= 1 &&
                    now - stableSince >= 200 &&
                    ui.onAllNodes(appNavigationRevealAction()).fetchSemanticsNodes().isEmpty()
            }
            val before = target.assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
            File(instrumentation.targetContext.cacheDir, "app-frame-reading-evidence/reading-baseline.json").apply {
                parentFile?.mkdirs()
                writeText(com.google.gson.JsonObject().apply {
                    addProperty("post", "Reference post 05"); addProperty("top", before)
                    addProperty("viewport", list.fetchSemanticsNode().boundsInRoot.toString())
                    addProperty("setupElapsedMillis", 15_000 - remainingSetup())
                }.toString())
            }
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
        // Reading now retains collapsed chrome. Use the actual accessible reveal
        // before observing backup guidance, keeping the original exact reader anchor.
        ui.revealAppNavigation()
        ui.waitUntil(15_000) { ui.onNodeWithText("Reference post 05").isDisplayed() &&
            ui.onNodeWithText("Back up your wallet").isDisplayed() && ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
        assertEquals("Return to the same visible reading position", expected,
            ui.onNodeWithText("Reference post 05").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    private fun capture(name: String) = captureNativeScreen(File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "app-frame-reading-evidence/$name"))
}
