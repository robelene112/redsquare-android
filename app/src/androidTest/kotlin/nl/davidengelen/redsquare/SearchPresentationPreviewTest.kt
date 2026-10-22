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
            archiveResponse = { if (it.get("limit").asInt == 100 && !it.has("field4")) rows else "[]" }) {
            ui.waitUntil(15_000) { ui.onNode(hasText("Later") and hasClickAction()).isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.onNode(hasText("Search") and hasClickAction()).performClick()
            val editor = hasSetTextAction() and isEnabled()
            fun search(query: String) {
                ui.onNode(editor).performTextReplacement(query)
                ui.waitUntil(15_000) { ui.onNode(hasText("Search history") and isEnabled()).isDisplayed() }
                ui.onNode(editor).performImeAction()
                ui.waitUntil(15_000) { ui.onNode(hasText("Search history") and isEnabled()).isDisplayed() }
            }
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
            ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Posts"))
            ui.onNodeWithText("Posts").assertIsDisplayed()
            ui.onNodeWithText(context.getString(R.string.search_empty)).assertIsDisplayed()

            search("Native")
            ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("People"))
            ui.onNodeWithContentDescription("View profile for $name").assertIsDisplayed()
            ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Native Red Square reference post"))
            ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
            captureNativeScreen(File(folder, "mixed-results.png"), "Native Red Square reference post")

            search("reference post")
            ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("No matching people in known accounts."))
            ui.onNodeWithText("No matching people in known accounts.").assertIsDisplayed()
            ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Native Red Square reference post"))
            ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
            captureNativeScreen(File(folder, "posts-only.png"), "Native Red Square reference post")

            search("Unfindable example")
            ui.onNodeWithText("No matching people in known accounts.").assertIsDisplayed()
            ui.onNodeWithText(context.getString(R.string.search_empty)).assertIsDisplayed()
            captureNativeScreen(File(folder, "no-results.png"))
        }
    }
}
