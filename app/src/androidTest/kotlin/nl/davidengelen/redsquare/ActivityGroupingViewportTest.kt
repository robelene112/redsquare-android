package nl.davidengelen.redsquare

import android.graphics.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ActivityGroupingNarrowViewportTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun narrowGroupedActivityKeepsPeopleAndPostTargetsReadable() = ActivityGroupingViewport(ui).exercise(320)
}

class ActivityGroupingPixelViewportTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun pixelGroupedActivityKeepsPeopleAndPostTargetsReadable() = ActivityGroupingViewport(ui).exercise(412)
}

private class ActivityGroupingViewport(private val ui: ComposeTestRule) {
    fun exercise(width: Int) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val folder = File(context.cacheDir, "activity-group-viewport-evidence").apply { mkdirs() }
        val name = "LongActivityIdentity".repeat(3).take(45) + "@saito"
        val reference = instrumentation.context.assets.open("native-notification-reference.json").reader().use {
            JsonParser.parseReader(it).asJsonObject
        }
        val actors = listOf("incomingLike", "boundaryIncomingLike", "olderIncomingLike").map {
            reference.getAsJsonObject(it).get("field2").asString
        }
        val labels = actors.mapIndexed { index, key -> "View profile for ${if (index == 0) name else "Anon-${key.take(6)}"}" }
        withActivityInbox("group-viewport-$width", listOf("incomingLike", "boundaryIncomingLike", "olderIncomingLike", "incomingReply"),
            name, "activity-group-viewport-evidence") { activity, _ ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
            ui.withPostViewports(activity, folder, "groups", widthDp = width) { label, _ ->
              withFailureCapture({
                File(folder, "$label-roots.txt").writeText(ui.onAllNodes(isRoot(), useUnmergedTree = true)
                    .printToString(maxDepth = Int.MAX_VALUE))
              }) {
                ui.onNode(hasText("Notifications") and hasClickAction()).performClick()
                ui.waitUntil(15_000) { ui.refreshableContentReady() && ui.onNodeWithText("2 unread").isDisplayed() }
                ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Liked your post"))
                ui.onAllNodesWithText("Liked your post").assertCountEquals(1)
                ui.onNode(hasText("Liked your post") and hasClickAction()).assertIsDisplayed()
                    .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
                captureNativeScreen(File(folder, "$label-group.png"), "Liked your post")
                // Align the avatar strip separately at large text, where a complete row is taller than the viewport.
                ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription(labels[2]))
                ui.waitUntil(15_000) {
                    labels.all { ui.onAllNodesWithContentDescription(it).onLast().isDisplayed() }
                }
                val rectangles = mutableListOf<Rect>()
                val targets = JsonObject()
                for (actorLabel in labels) {
                    ui.onAllNodesWithContentDescription(actorLabel).onLast().assertIsDisplayed()
                        .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
                    var native = requireNotNull(device.findObjects(By.desc(actorLabel)).lastOrNull())
                    while (!native.isClickable) native = requireNotNull(native.parent)
                    val bounds = native.visibleBounds
                    assertTrue("Avatar profile targets are at least48dp", bounds.width() >= 48 && bounds.height() >= 48)
                    assertTrue("Distinct people keep separate native targets", rectangles.none { Rect.intersects(it, bounds) })
                    rectangles += bounds
                    targets.addProperty(actorLabel, bounds.flattenToString())
                }
                ui.onNodeWithText("and 2 others").assertIsDisplayed()
                File(folder, "$label-avatar-targets.json").writeText(targets.toString())
                captureNativeScreen(File(folder, "$label-people-strip.png"))
                // The latest fixture actor can vary only through the documented signed tie ordering.
                ui.onAllNodes(hasContentDescription("Activity options for", substring = true)).onLast().performClick()
                ui.onNodeWithText("People who liked this post").performClick()
                ui.onNodeWithText("3 likes from 3 people").assertIsDisplayed()
                for (actorLabel in labels) ui.onNode(hasContentDescription(actorLabel) and hasAnyAncestor(isDialog())).performScrollTo()
                    .assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                ui.onNode(hasContentDescription(labels[0]) and hasAnyAncestor(isDialog())).performScrollTo()
                captureNativeScreen(File(folder, "$label-people-dialog.png"), "3 likes from 3 people")
                ui.onNodeWithText("Done").performClick()
                ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Replied to your post"))
                val reply = hasText("Replied to your post") and hasText("Reply notification reference") and hasClickAction()
                val before = ui.onNode(reply).assertIsDisplayed().getUnclippedBoundsInRoot().top
                fun observedScroll() = ui.onNode(hasScrollToIndexAction()).fetchSemanticsNode().config[
                    androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
                val beforeScroll = observedScroll()
                if (context.resources.configuration.fontScale >= 1.99f)
                    assertTrue("Large-text return exercises a genuinely nonzero Activity reading position", beforeScroll > 0f)
                ui.onNode(reply).performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("Conversation").isDisplayed() }
                ui.onNode(hasText("Back") and hasClickAction()).performClick()
                ui.waitUntil(15_000) { ui.refreshableContentReady() && ui.onNode(reply).isDisplayed() }
                val after = ui.onNode(reply).getUnclippedBoundsInRoot().top
                assertEquals("Returning from the post retains the scrolled Activity row without a corrective scroll",
                    before.value, after.value, 1f)
                val afterScroll = observedScroll()
                assertEquals("The actual Activity scroll position is retained", beforeScroll, afterScroll, 0.01f)
                File(folder, "$label-retained-reading.json").writeText(JsonObject().apply {
                    addProperty("beforeRowTopDp", before.value); addProperty("afterRowTopDp", after.value)
                    addProperty("beforeScroll", beforeScroll); addProperty("afterScroll", afterScroll)
                    addProperty("fontScale", context.resources.configuration.fontScale)
                    addProperty("widthDp", width)
                }.toString())
                captureNativeScreen(File(folder, "$label-retained-reading.png"), "Reply notification reference")
                ui.onNode(hasText("Home") and hasClickAction()).performClick()
              }
            }
        }
    }
}
