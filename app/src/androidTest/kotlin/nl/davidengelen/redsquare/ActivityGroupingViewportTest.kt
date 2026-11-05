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
                ui.onNode(hasText("Notifications") and hasClickAction()).performClick()
                ui.waitUntil(15_000) { ui.refreshableContentReady() && ui.onNodeWithText("2 unread").isDisplayed() }
                ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Liked your post"))
                ui.onAllNodesWithText("Liked your post").assertCountEquals(1)
                ui.onNode(hasText("Liked your post") and hasClickAction()).assertIsDisplayed()
                    .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
                captureNativeScreen(File(folder, "$label-group.png"), "Liked your post")
                // Align the avatar strip separately at large text, where a complete row is taller than the viewport.
                ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription(labels[2]))
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
                File(folder, "$label-avatar-targets.json").writeText(targets.toString())
                captureNativeScreen(File(folder, "$label-people-strip.png"))
                // The latest fixture actor can vary only through the documented signed tie ordering.
                ui.onAllNodes(hasContentDescription("Activity options for", substring = true)).onLast().performClick()
                ui.onNodeWithText("People who liked this post").performClick()
                ui.onNodeWithText("3 likes from 3 people").assertIsDisplayed()
                for (actorLabel in labels) ui.onNodeWithContentDescription(actorLabel).performScrollTo()
                    .assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                ui.onNodeWithContentDescription(labels[0]).performScrollTo()
                captureNativeScreen(File(folder, "$label-people-dialog.png"), "3 likes from 3 people")
                ui.onNodeWithText("Done").performClick()
                ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Replied to your post"))
                ui.onNode(hasText("Replied to your post") and hasText("Reply notification reference") and hasClickAction()).assertIsDisplayed()
                ui.onNode(hasText("Home") and hasClickAction()).performClick()
            }
        }
    }
}
