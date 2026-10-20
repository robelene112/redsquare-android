package nl.davidengelen.redsquare

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import java.io.File
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Verified content and actual image pixels through the same native post presentation. */
class PostContentViewportTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun multipleImagesAndQuotedOriginalStayReadableWithoutInventedCountsAcrossTextSizesAndThemes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val imageFixture = instrumentation.context.assets.open("native-images-reference.json").reader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("images")
        }
        val originalAuthor = SaitoWallet.create()
        val author = SaitoWallet.create()
        val original = RedSquareFeed.decode(SaitoTransaction.create(originalAuthor,
            RedSquarePosts.message("Quoted viewport original"), 1_700_000_000_000).envelope())
        val row = postingArchiveRow(SaitoTransaction.create(author,
            RedSquarePosts.message("Media and quote viewport preview",
                images = listOf(imageFixture.get("red").asString, imageFixture.get("blue").asString),
                quote = original), 1_700_000_060_000))
        val authorName = "Anon-${author.publicKey.take(6)}"
        val originalName = "Anon-${originalAuthor.publicKey.take(6)}"
        val countPattern = Regex("[0-9]+ (?:likes?|reposts?)")
        val countText = SemanticsMatcher("an observed social count") { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { countPattern.matches(it.text) }
        }
        withAppFrameFixture("post-content", "post-content-evidence", feedRows = "[$row]") { activity ->
            val folder = File(context.cacheDir, "post-content-evidence").apply { mkdirs() }
            val profile = hasContentDescription("View profile for $authorName") and hasClickAction()
            ui.waitUntil(20_000) { ui.onNode(profile).isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.withPostViewports(activity, folder, "content") { label, _ ->
                ui.onNode(hasScrollAction()).performScrollToNode(profile)
                ui.onNode(profile).assertIsDisplayed()
                ui.onNodeWithText("Media and quote viewport preview", useUnmergedTree = true).assertExists()
                capture("$label-identity-body.png")
                for ((index, expected) in listOf(0xffdc1e28.toInt(), 0xff143cdc.toInt()).withIndex()) {
                    val image = hasContentDescription("Image ${index + 1} of 2")
                    ui.waitUntil(15_000) { ui.onAllNodes(image).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasScrollAction()).performScrollToNode(image)
                    val bitmap = ui.onNode(image).assertIsDisplayed().captureToImage().asAndroidBitmap()
                    try { assertEquals("Signed image order and actual decoded color must remain intact", expected,
                        bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)) }
                    finally { bitmap.recycle() }
                    capture("$label-image-${index + 1}.png")
                }
                val quoted = hasAnyAncestor(hasContentDescription("Quoted original"))
                val originalText = hasText("Quoted viewport original") and quoted
                ui.onNode(hasScrollAction(), useUnmergedTree = true).performScrollToNode(originalText)
                ui.onNode(originalText, useUnmergedTree = true).assertIsDisplayed()
                ui.onNode(hasText(originalName) and quoted, useUnmergedTree = true).assertIsDisplayed()
                capture("$label-verified-quote.png")
                val actions = listOf("Like", "Repost", "Quote").map {
                    hasContentDescription("$it post by $authorName") and hasClickAction()
                }
                ui.onNode(hasScrollAction()).performScrollToNode(actions.last())
                ui.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { scroll ->
                    check(scroll(0f, 88 * context.resources.displayMetrics.density))
                }
                ui.waitForIdle()
                actions.forEach { action ->
                    val node = ui.onNode(action).assertIsDisplayed().assertIsEnabled().fetchSemanticsNode()
                    assertFalse("Missing counts must remain absent from the accessible action state",
                        countPattern.containsMatchIn(node.config.getOrNull(SemanticsProperties.StateDescription).orEmpty()))
                }
                ui.onAllNodes(countText, useUnmergedTree = true).assertCountEquals(0)
                capture("$label-actions-without-counts.png")
            }
        }
    }

    private fun capture(name: String) = captureNativeScreen(File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "post-content-evidence/$name"))
}
