package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.SaitoTransaction
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Real post actions and identity through the native UI; the peer remains read-only. */
class PostPresentationPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun postActionsKeepReportSecondaryAndFullIdentityReachable() =
        withAppFrameFixture("post-actions", "post-presentation-evidence") {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val row = instrumentation.context.assets.open("native-feed-reference.json").reader().use {
                JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow")
            }
            val author = row.get("field2").asString
            ui.waitUntil(15_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.onNodeWithText("Report").assertDoesNotExist()
            val more = (hasText("Post actions for Anon-jesTu2") or
                hasContentDescription("Post actions for Anon-jesTu2")) and hasClickAction()
            val bounds = ui.onNode(more).assertIsDisplayed().fetchSemanticsNode().touchBoundsInRoot
            val minimum = 48 * context.resources.displayMetrics.density - 1
            assertTrue("Post actions must retain a full 48dp target", bounds.width >= minimum && bounds.height >= minimum)
            capture("feed-actions-secondary.png")
            ui.onNode(more).performClick()
            ui.onNodeWithText("Public identity").assertIsDisplayed().performClick()
            ui.onNodeWithText(author).assertIsDisplayed()
            capture("feed-full-public-identity.png")
            ui.onNodeWithText("Done").performClick()
            ui.onNode(more).performClick()
            ui.onNodeWithText("Report").assertIsDisplayed().performClick()
            ui.onNodeWithText(context.getString(R.string.report_confirm, "saito.davidengelen.nl")).assertIsDisplayed()
            capture("report-confirmation-after-menu-closed.png")
            ui.onNodeWithText(context.getString(R.string.report_cancel)).performClick()
            ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
        }

    @Test fun samePostKeepsIdentityTimeAndSecondaryActionsAcrossAllBrowseSurfaces() =
        withAppFrameFixture("post-contexts", "post-presentation-evidence") {
            ui.waitUntil(15_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            assertPostPresentation("feed")
            ui.onNodeWithText("Native Red Square reference post").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Conversation").isDisplayed() &&
                ui.onNode(hasText("Refresh") and isEnabled()).isDisplayed() }
            assertPostPresentation("thread")
            ui.onNodeWithText("Back").performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("New post") and hasClickAction()).isDisplayed() }
            ui.onNodeWithContentDescription("View profile for Anon-jesTu2").performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("Profile") and !hasClickAction()).isDisplayed() &&
                ui.onNode(hasText("Refresh") and isEnabled()).isDisplayed() }
            ui.onNode(hasScrollAction()).performScrollToNode(hasText("Quote") and hasClickAction())
            assertPostPresentation("profile")
            ui.onNode(hasText("Home") and hasClickAction()).performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("New post") and hasClickAction()).isDisplayed() }
            ui.onNode(hasText("Search") and hasClickAction()).performClick()
            ui.onNode(hasSetTextAction()).performTextInput("reference")
            ui.waitUntil(15_000) { ui.onNode(hasText("Search history") and isEnabled()).isDisplayed() }
            ui.onNodeWithText("Search history").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() &&
                ui.onNode(hasText("Search history") and isEnabled()).isDisplayed() }
            ui.onNode(hasScrollAction()).performScrollToNode(hasText("Quote") and hasClickAction())
            assertPostPresentation("search")
        }

    @Test fun socialActionsAssociateNodeCountsWithContextAndKeepCompactTargets() =
        withAppFrameFixture("post-social-targets", "post-presentation-evidence") {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            ui.waitUntil(15_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            val viewport = ui.onRoot().fetchSemanticsNode().boundsInRoot
            val minimum = 48 * context.resources.displayMetrics.density - 1
            val targets = listOf(
                Triple("Like post by Anon-jesTu2", "Like", "2 likes"),
                Triple("Repost post by Anon-jesTu2", "Repost", "1 repost"),
                Triple("Quote post by Anon-jesTu2", "Quote", null)
            ).map { (description, label, count) ->
                val action = ui.onNode(hasContentDescription(description) and hasClickAction())
                    .assertIsDisplayed().assertTextContains(label)
                if (count != null) action.assertTextContains(count)
                val bounds = action.fetchSemanticsNode().touchBoundsInRoot
                assertTrue("$label must have a complete non-clipped48dp target", bounds.width >= minimum &&
                    bounds.height >= minimum && bounds.left >= viewport.left && bounds.right <= viewport.right &&
                    bounds.top >= viewport.top && bounds.bottom <= viewport.bottom)
                bounds
            }
            assertTrue("Default-size post actions must share one deliberate row",
                targets.maxOf { it.top } - targets.minOf { it.top } < 1)
            targets.zipWithNext().forEach { (left, right) ->
                assertTrue("Adjacent post action targets must not overlap", left.right <= right.left)
            }
            ui.onNodeWithText("0 likes").assertDoesNotExist()
            ui.onNodeWithText("0 reposts").assertDoesNotExist()
            capture("compact-social-controls-with-node-counts.png")
        }

    @Test fun ownPostActionsOpenFromEveryBrowseSurfaceWithoutPublishing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallet = AndroidWalletStore(context).loadOrCreate()
        val body = JsonObject().apply {
            addProperty("module", "RedSquare"); addProperty("request", "create tweet")
            add("data", JsonObject().apply { addProperty("text", "Own post action preview") })
        }
        val row = postingArchiveRow(SaitoTransaction.create(wallet, body, 1_700_000_000_000))
        val author = "Anon-${wallet.publicKey.take(6)}"
        withAppFrameFixture("post-author-actions", "post-presentation-evidence", feedRows = "[$row]") {
            ui.waitUntil(15_000) { ui.onNodeWithText("Own post action preview").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            assertOwnPostActions(author, "feed")
            ui.onNodeWithText("Own post action preview").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Conversation").isDisplayed() &&
                ui.onNode(hasText("Refresh") and isEnabled()).isDisplayed() }
            assertOwnPostActions(author, "thread")
            ui.onNodeWithText("Back").performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("New post") and hasClickAction()).isDisplayed() }
            ui.onNodeWithContentDescription("View profile for $author").performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("Profile") and !hasClickAction()).isDisplayed() &&
                ui.onNode(hasText("Refresh") and isEnabled()).isDisplayed() }
            ui.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Post actions for $author"))
            assertOwnPostActions(author, "profile")
            ui.onNode(hasText("Home") and hasClickAction()).performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("New post") and hasClickAction()).isDisplayed() }
            ui.onNode(hasText("Search") and hasClickAction()).performClick()
            ui.onNode(hasSetTextAction()).performTextInput("Own post action preview")
            ui.waitUntil(15_000) { ui.onNode(hasText("Search history") and isEnabled()).isDisplayed() }
            ui.onNodeWithText("Search history").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Own post action preview").isDisplayed() &&
                ui.onNode(hasText("Search history") and isEnabled()).isDisplayed() }
            ui.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Post actions for $author"))
            assertOwnPostActions(author, "search")
        }
    }

    private fun assertOwnPostActions(author: String, surface: String) {
        val more = hasContentDescription("Post actions for $author") and hasClickAction()
        val edit = hasContentDescription("Edit post by $author") and hasClickAction()
        val delete = hasContentDescription("Delete post by $author") and hasClickAction()
        ui.onNode(more).assertIsDisplayed().performClick()
        ui.onNode(edit).assertIsDisplayed().assertIsEnabled().assertTextContains("Edit")
        ui.onNode(delete).assertIsDisplayed().assertIsEnabled().assertTextContains("Delete")
        ui.onNodeWithText("Report").assertDoesNotExist()
        capture("own-actions-$surface.png")
        ui.onNode(edit).performClick()
        ui.waitUntil(15_000) { ui.onNode(hasSetTextAction() and isEnabled()).isDisplayed() }
        ui.onNodeWithText("Edit post").assertIsDisplayed()
        ui.onNode(hasSetTextAction()).assertTextEquals("Own post action preview")
        ui.onNodeWithText("Back").performClick()
        ui.waitUntil(15_000) { ui.onNode(more).isDisplayed() }
        ui.onNode(more).performClick()
        ui.onNode(delete).assertIsDisplayed().assertIsEnabled().performClick()
        ui.waitUntil(15_000) { ui.onNode(hasText("Confirm delete") and isEnabled()).isDisplayed() }
        ui.onNodeWithText("Delete post").assertIsDisplayed()
        ui.onNodeWithText("Remove this post from normal views on the selected node and this device? Historical and independent copies may remain.")
            .assertIsDisplayed()
        capture("own-delete-confirmation-$surface.png")
        ui.onNodeWithText("Back").performClick()
        ui.waitUntil(15_000) { ui.onNodeWithText("Own post action preview").isDisplayed() }
    }

    private fun assertPostPresentation(surface: String) {
        ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
        ui.onNodeWithContentDescription("View profile for Anon-jesTu2").assertIsDisplayed().assertHasClickAction()
        // Independent fixture timestamp1700000000000, rendered on the UTC emulator.
        ui.onNode(hasText("11/14/23", substring = true) and hasText("10:13 PM", substring = true)).assertIsDisplayed()
        ui.onNodeWithContentDescription("Post actions for Anon-jesTu2").assertIsDisplayed().assertHasClickAction()
        for (action in listOf("Like", "Repost", "Quote"))
            ui.onNode(hasText(action) and hasClickAction()).assertIsDisplayed()
        ui.onNodeWithText("Report").assertDoesNotExist()
        capture("same-post-$surface.png")
    }

    private fun capture(name: String) = captureNativeScreen(File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "post-presentation-evidence/$name"))
}
