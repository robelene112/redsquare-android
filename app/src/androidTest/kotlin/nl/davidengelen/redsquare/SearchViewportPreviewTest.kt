package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import nl.davidengelen.redsquare.saito.RedSquarePosts
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real input, complete identity and longer results at 320dp in both themes and text scales. */
class SearchViewportPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun invalidInputAndKeyboardSearchRemainReadableAcrossThemesAndTextSizes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val folder = File(context.cacheDir, "search-viewport-evidence").apply { mkdirs() }
        val registry = instrumentation.context.assets.open("native-registry-reference.json").reader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonArray("rows").toString()
        }
        val longPost = "Native search preview: a longer post remains readable while you explore people and their public identity."
        val author = SaitoWallet.create()
        val row = postingArchiveRow(SaitoTransaction.create(author, RedSquarePosts.message(longPost), 1_700_000_000_000))
        val person = "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR"
        val name = "NativeCase7@saito"
        val posts = AtomicInteger()
        val quotes = AtomicInteger()
        withAppFrameFixture("search-viewport", "search-viewport-evidence", "[]",
            registryResponse = { if (it.get("request").asString == "registry namecheck") registry else "{}" },
            archiveResponse = { criteria ->
                if (criteria.get("limit").asInt == 100 && !criteria.has("field4")) {
                    posts.incrementAndGet(); "[$row]"
                } else "[]"
            }, quoteArchiveResponse = { criteria ->
                if (criteria.get("limit").asInt == 100 && !criteria.has("field4")) quotes.incrementAndGet()
                "[]"
            }) { activity ->
            ui.waitUntil(15_000) { ui.onNode(hasText("Later") and hasClickAction()).isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.withPostViewports(activity, folder, "search") { label, _ ->
                ui.onNode(hasText("Search") and hasClickAction()).performClick()
                val editor = hasSetTextAction() and isEnabled()
                val search = hasContentDescription("Search history") and hasClickAction()
                ui.waitUntil(15_000) { ui.onNode(editor).isDisplayed() }
                val beforePosts = posts.get()
                val beforeQuotes = quotes.get()
                ui.onNode(editor).performTextReplacement("   ")
                ui.onNode(search).assertIsNotEnabled()
                val invalid = "x".repeat(257)
                ui.onNode(editor).performTextReplacement(invalid)
                ui.onNode(editor).assertTextContains(invalid)
                ui.onNodeWithText("Use 256 characters or fewer.").assertIsDisplayed()
                ui.onNode(search).assertIsNotEnabled()
                ui.onNode(editor).performImeAction()
                captureNativeScreen(File(folder, "$label-invalid-query.png"), "Use 256 characters or fewer.")
                assertEquals("Invalid input does not search posts", beforePosts, posts.get())
                assertEquals("Invalid input does not search quotes", beforeQuotes, quotes.get())
                activity.recreate()
                ui.waitUntil(15_000) { ui.onNodeWithText("Use 256 characters or fewer.").isDisplayed() }
                ui.onNode(editor).assertTextContains(invalid)
                ui.onNodeWithContentDescription("Clear search").performClick()
                ui.onNode(editor).assert(hasText(""))
                ui.onNodeWithText("Use 256 characters or fewer.").assertDoesNotExist()
                ui.onNode(search).assertIsNotEnabled()
                assertEquals(beforePosts, posts.get())
                assertEquals(beforeQuotes, quotes.get())

                ui.onNode(editor).performClick().performTextInput(name)
                ui.waitUntil(10_000) { focusedWindowImeBottom() > 0 }
                ui.onNode(editor).assertTextContains(name)
                val clear = ui.onNodeWithContentDescription("Clear search")
                clear.assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
                ui.onNode(search).assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
                assertTrue(clear.getUnclippedBoundsInRoot().right <= ui.onNode(search).getUnclippedBoundsInRoot().left)
                captureNativeScreen(File(folder, "$label-real-keyboard.png"), name)
                ui.waitUntil(15_000) { ui.onNode(search and isEnabled()).isDisplayed() }
                ui.onNode(editor).performImeAction()
                ui.waitUntil(15_000) { ui.onNodeWithContentDescription("Identity details for $name").isDisplayed() &&
                    ui.onNode(search and isEnabled()).isDisplayed() }
                ui.waitUntil(10_000) { focusedWindowImeBottom() == 0 &&
                    ui.onAllNodesWithText("Searching available history…").fetchSemanticsNodes().isEmpty() }
                ui.onNodeWithText("People").assertIsDisplayed()
                captureNativeScreen(File(folder, "$label-people.png"), "People")
                val profile = ui.onNodeWithContentDescription("View profile for $name")
                val details = ui.onNodeWithContentDescription("Identity details for $name")
                profile.assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
                details.assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
                assertTrue(profile.getUnclippedBoundsInRoot().right <= details.getUnclippedBoundsInRoot().left)
                details.performClick()
                ui.onNodeWithText(person).performScrollTo().assertIsDisplayed()
                captureNativeScreen(File(folder, "$label-exact-identity.png"), person)
                ui.onNodeWithText("Done").performClick()

                ui.onNode(editor).performTextReplacement("search preview")
                ui.onNode(editor).performImeAction()
                ui.waitUntil(15_000) { ui.onNode(search and isEnabled()).isDisplayed() }
                ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(longPost))
                ui.onNodeWithText(longPost).assertIsDisplayed()
                captureNativeScreen(File(folder, "$label-long-post.png"), longPost)
                val lastAction = hasContentDescription("Quote post by Anon-${author.publicKey.take(6)}") and hasClickAction()
                ui.onNode(hasScrollToIndexAction()).performScrollToNode(lastAction)
                ui.onNode(lastAction).assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
                captureNativeScreen(File(folder, "$label-post-actions.png"))
                ui.onNode(hasText("Home") and hasClickAction()).performClick()
                ui.waitUntil(15_000) { ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
            }
        }
    }
}
