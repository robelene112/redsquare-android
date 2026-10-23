package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual mixed signed events, maximum registered name, themes and large text. */
class ActivityInboxViewportTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun mixedActivityPreviewsAndExactIdentityRemainReadableAcrossThemesAndTextSizes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "activity-inbox-viewport-evidence").apply { mkdirs() }
        val name = "LongActivityIdentity".repeat(3).take(45) + "@saito"
        val actor = "pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU"
        withActivityInbox("mixed-viewport", listOf("incomingLike", "incomingReply", "incomingMention",
            "incomingRepost", "incomingQuote", "knownOtherPost"), name, "activity-inbox-viewport-evidence") { activity, _ ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
            ui.onNode(hasText("Notifications") and hasClickAction()).performClick()
            val profile = hasContentDescription("View profile for $name")
            ui.waitUntil(15_000) { ui.onAllNodes(profile).fetchSemanticsNodes().isNotEmpty() }
            ui.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
            ui.onAllNodes(profile)[0].performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("Follow") and isEnabled()).isDisplayed() }
            ui.onNodeWithText("Follow").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Unfollow").isDisplayed() }
            ui.onNode(hasText("Home") and hasClickAction()).performClick()
            ui.withPostViewports(activity, folder, "activity") { label, _ ->
                ui.onNode(hasText("Notifications") and hasClickAction()).performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("6 unread").isDisplayed() }
                for ((index, pair) in listOf(
                    "Quoted your post" to "Quote notification reference",
                    "Reposted your post" to "Native Red Square reference post",
                    "Mentioned you" to "Hello @jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR, mention reference",
                    "Replied to your post" to "Reply notification reference",
                    "Liked your post" to "Native Red Square reference post",
                    "Posted" to "A known post by another account").withIndex()) {
                    val (caption, preview) = pair
                    ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(caption))
                    ui.onNode(hasText(caption) and hasClickAction()).assertIsDisplayed()
                        .assert(hasText(preview)).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
                    captureNativeScreen(File(folder, "$label-event$index.png"), caption)
                }
                ui.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
                val author = ui.onAllNodes(profile)[0]
                author.assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
                captureNativeScreen(File(folder, "$label-long-name.png"), name)
                ui.onAllNodesWithContentDescription("Activity options for $name")[0].performClick()
                ui.onNodeWithText("Identity details").performClick()
                ui.onNodeWithText(actor).performScrollTo().assertIsDisplayed()
                captureNativeScreen(File(folder, "$label-exact-identity.png"), actor)
                ui.onNodeWithText("Done").performClick()
                ui.onNode(hasText("Home") and hasClickAction()).performClick()
            }
        }
    }
}
