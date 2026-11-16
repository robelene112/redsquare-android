package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Independent result sections and public identity access through ordinary search. */
class SearchPresentationPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun peopleAndPostsHaveIndependentResultsWithReadableExactIdentity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val folder = File(context.cacheDir, "search-presentation-evidence").apply { mkdirs() }
        val rows = instrumentation.context.assets.open("native-feed-reference.json").reader().use {
            JsonParser.parseReader(it).asJsonObject.get("archiveRow").let { row -> "[$row]" }
        }
        val registry = instrumentation.context.assets.open("native-registry-reference.json").reader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonArray("rows").toString()
        }
        val person = "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR"
        val name = "NativeCase7@saito"
        withAppFrameFixture("search-sections", "search-presentation-evidence", "[]",
            registryResponse = { if (it.get("request").asString == "registry namecheck") registry else "{}" },
            archiveResponse = { if (it.get("limit").asInt == 100 && !it.has("field4")) rows else "[]" }) { activity ->
            ui.waitUntil(15_000) { ui.onNode(hasText("Later") and hasClickAction()).isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.onNode(hasText("Search") and hasClickAction()).performClick()
            val editor = hasSetTextAction() and isEnabled()
            fun search(query: String) {
                ui.onNode(editor).performTextReplacement(query)
                ui.waitUntil(15_000) { ui.onNode(hasContentDescription("Search history") and isEnabled()).isDisplayed() }
                ui.onNode(editor).performImeAction()
                ui.waitUntil(15_000) { ui.onNode(hasContentDescription("Search history") and isEnabled()).isDisplayed() }
            }
            ui.onNodeWithText("Posts").assertIsSelected()
            ui.onNodeWithText("People").performClick().assertIsSelected()
            search(name)
            ui.onNodeWithText("People").assertIsDisplayed()
            val profile = ui.onNodeWithContentDescription("View profile for $name")
            val details = ui.onNodeWithContentDescription("Identity details for $name")
            profile.assertIsDisplayed().assertWidthIsAtLeast(androidx.compose.ui.unit.Dp(48f))
                .assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(48f))
            details.assertIsDisplayed().assertWidthIsAtLeast(androidx.compose.ui.unit.Dp(48f))
                .assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(48f))
            assertTrue(profile.getUnclippedBoundsInRoot().right <= details.getUnclippedBoundsInRoot().left)
            captureNativeScreen(File(folder, "people-only.png"), name)
            details.performClick()
            ui.onNodeWithText(person).performScrollTo().assertIsDisplayed()
            captureNativeScreen(File(folder, "exact-identity.png"), person)
            ui.onNodeWithText("Done").performClick()
            settleIdentityDismissal(activity, folder)
            ui.onNodeWithText("Posts").performClick().assertIsSelected()
            ui.onNodeWithText("Posts").assertIsDisplayed()
            ui.onNodeWithText(context.getString(R.string.search_empty)).assertIsDisplayed()

            search("Native")
            ui.onNodeWithText("People").performClick().assertIsSelected()
            ui.onNodeWithContentDescription("Identity details for $name").assertIsDisplayed()
            ui.onNodeWithText("Native Red Square reference post").assertDoesNotExist()
            ui.onNodeWithText("Posts").performClick().assertIsSelected()
            ui.onNodeWithContentDescription("Identity details for $name").assertDoesNotExist()
            ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Native Red Square reference post"))
            ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
            captureNativeScreen(File(folder, "mixed-results.png"), "Native Red Square reference post")

            ui.onNodeWithText("People").performClick()
            search("reference post")
            ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("No matching people in known accounts."))
            ui.onNodeWithText("No matching people in known accounts.").assertIsDisplayed()
            ui.onNodeWithText("Posts").performClick()
            ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Native Red Square reference post"))
            ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
            captureNativeScreen(File(folder, "posts-only.png"), "Native Red Square reference post")

            search("Unfindable example")
            ui.onNodeWithText("People").performClick()
            ui.onNodeWithText("No matching people in known accounts.").assertIsDisplayed()
            ui.onNodeWithText("Posts").performClick()
            ui.onNodeWithText(context.getString(R.string.search_empty)).assertIsDisplayed()
            captureNativeScreen(File(folder, "no-results.png"))
        }
    }

    private fun settleIdentityDismissal(activity: androidx.test.core.app.ActivityScenario<MainActivity>, folder: File) {
        val started = android.os.SystemClock.elapsedRealtime()
        lateinit var activityRoot: android.view.View
        activity.onActivity { activityRoot = it.window.decorView }
        var previous: androidx.compose.ui.geometry.Rect? = null
        var stableSince = android.os.SystemClock.elapsedRealtime()
        var activityFocused = false
        val posts = ui.onNode(hasText("Posts") and isEnabled())
        // The next original gesture targets the Activity, not the retiring dialog.
        val remaining = 5_000 - (android.os.SystemClock.elapsedRealtime() - started)
        check(remaining > 0) { "Native tab input setup exceeded five seconds" }
        ui.waitUntil(remaining) {
            var focused: android.view.View? = null
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                focused = android.view.inspector.WindowInspector.getGlobalWindowViews()
                    .singleOrNull { it.isShown && it.hasWindowFocus() }
            }
            val dialogGone = ui.onAllNodesWithText("Done").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
            val bounds = if (posts.isDisplayed()) posts.fetchSemanticsNode().boundsInRoot else null
            activityFocused = focused === activityRoot
            val ready = activityFocused && dialogGone && bounds != null
            val now = android.os.SystemClock.elapsedRealtime()
            if (!ready || bounds != previous) stableSince = now
            previous = bounds
            ready && now - stableSince >= 200
        }
        posts.assertIsDisplayed().assertIsEnabled()
        check(android.os.SystemClock.elapsedRealtime() - started <= 5_000)
        File(folder, "identity-dismissal-input-setup.json").writeText(com.google.gson.JsonObject().apply {
            addProperty("activityWindowFocused", activityFocused)
            addProperty("settledPostsBounds", requireNotNull(previous).toString())
            addProperty("setupMillis", android.os.SystemClock.elapsedRealtime() - started)
            addProperty("stableMillis", android.os.SystemClock.elapsedRealtime() - stableSince)
            addProperty("qualification", "Test input setup only; the original single Posts tap and immediate selected assertion follow. Original failed gesture cause remains unproved.")
        }.toString())
    }
}
