package nl.davidengelen.redsquare

import android.content.Intent
import android.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.espresso.intent.Intents
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Base64

/** Signed post targets and unsigned node hints at the existing native/peer boundary. */
class FeedLinkPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun aRealLinkedPostShowsItsAvailablePreviewAndOpensOnlyItsOriginalTarget() = withInterceptedPostLinks {
        val metadata = JsonObject().apply { add("link_properties", JsonObject().apply {
            addProperty("og:title", "Independent writing, together")
            addProperty("og:description", "A controlled article preview supplied by this node.")
            addProperty("og:url", "https://example.invalid/metadata-is-not-the-post-target")
            addProperty("og:image", "data:image/png;base64," + Base64.getEncoder().encodeToString(linkPreviewPng()))
        }) }
        val row = linkedFeedRow("A linked essay worth reading.", metadata = metadata.toString())
        withAppFrameFixture("article-card", "feed-redesign-evidence", "[$row]") {
            ui.waitUntil(15_000) { ui.onNodeWithText("A linked essay worth reading.").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            val article = hasText("Independent writing, together") and hasClickAction()
            ui.onNode(hasScrollToIndexAction()).performScrollToNode(article)
            ui.onNode(article).performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("A controlled article preview supplied by this node.", useUnmergedTree = true).assertExists()
            val picture = ui.onNodeWithContentDescription("Link preview image")
            ui.waitUntil(15_000) { runCatching {
                val bitmap = picture.captureToImage().asAndroidBitmap()
                bitmap.getPixel(bitmap.width / 2, bitmap.height / 2) == Color.MAGENTA
            }.getOrDefault(false) }
            ui.onNodeWithText("Design sample · 6 min read").assertDoesNotExist()
            captureNativeScreen(File(context.cacheDir, "feed-redesign-evidence/available-stack-preview.png"))
            ui.onNode(article).performClick()
            assertOriginalTarget()
        }
    }

    @Test fun absentOrUnreadableHintsKeepTheActualTextLinkWithoutInventedArticleMetadata() = withInterceptedPostLinks {
        val row = linkedFeedRow("A caption before its standalone link.\n$referenceArticleLink", link = "", metadata = "not JSON")
        withAppFrameFixture("article-without-hints", "feed-redesign-evidence", "[$row]") {
            ui.waitUntil(15_000) { ui.onNodeWithText("A caption before its standalone link.", useUnmergedTree = true).isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            val article = hasText("Open link at saito.io") and hasClickAction()
            ui.onNode(hasScrollToIndexAction()).performScrollToNode(article)
            ui.onNode(article).performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("Independent writing, together").assertDoesNotExist()
            ui.onNodeWithText("A Node in a Browser Tab").assertDoesNotExist()
            ui.onNodeWithContentDescription("Link preview image").assertDoesNotExist()
            captureNativeScreen(File(context.cacheDir, "feed-redesign-evidence/stack-target-without-metadata.png"))
            ui.onNode(article).performClick()
            assertOriginalTarget()
        }
    }

    @Test fun standaloneTextTargetsKeepPunctuationThatBelongsToTheirPathsAndQueries() {
        for (link in listOf("https://example.org/article!", "https://example.org/a.", "https://example.org/read?ref=a,b;!")) {
            withInterceptedPostLinks {
                val row = linkedFeedRow("A complete punctuation-bearing target.\n$link", link = "", metadata = "{}")
                withAppFrameFixture("punctuation-target", "feed-redesign-evidence", "[$row]") {
                    ui.waitUntil(15_000) { ui.onNode(hasText("A complete punctuation-bearing target.", substring = true)).isDisplayed() }
                    ui.onNodeWithText("Later").performClick()
                    val article = hasText("Open link at example.org") and hasClickAction()
                    ui.onNode(hasScrollToIndexAction()).performScrollToNode(article)
                    ui.onNode(article).performScrollTo().assertIsDisplayed().performClick()
                    assertOriginalTarget(link)
                }
            }
        }
    }

    @Test fun verifiedTextEditsCannotAttachOldPreviewHintsToANewTargetOrRemoveExplicitAttachments() {
        val hints = """{"link_properties":{"og:title":"The original linked article"}}"""
        for ((suffix, explicit, edited, destination) in listOf(
            listOf("changed", "", "Edited text target.\nhttps://example.org/new", "https://example.org/new"),
            listOf("removed", "", "The text link was removed.", ""),
            listOf("attachment", referenceArticleLink, "Edited commentary keeps its original attachment.", referenceArticleLink)
        )) withInterceptedPostLinks {
            val row = linkedFeedRow("Original text target.\nhttps://example.org/old", link = explicit,
                metadata = hints, editedText = edited)
            withAppFrameFixture("edited-link-$suffix", "feed-redesign-evidence", "[$row]") {
                ui.waitUntil(15_000) { ui.onNode(hasText(edited.substringBefore('\n'), substring = true)).isDisplayed() }
                ui.onNodeWithText("Later").performClick()
                if (explicit.isEmpty()) ui.onNodeWithText("The original linked article").assertDoesNotExist()
                else ui.onNodeWithText("The original linked article", useUnmergedTree = true).assertExists()
                if (destination.isEmpty()) ui.onNodeWithText("Open link at example.org", useUnmergedTree = true).assertDoesNotExist()
                else {
                    val article = hasText("Open link at ${if (explicit.isEmpty()) "example.org" else "saito.io"}") and hasClickAction()
                    ui.onNode(hasScrollToIndexAction()).performScrollToNode(article)
                    ui.onNode(article).performScrollTo().assertIsDisplayed().performClick()
                    assertOriginalTarget(destination)
                }
                captureNativeScreen(File(context.cacheDir, "feed-redesign-evidence/edited-link-$suffix.png"))
            }
        }
    }

    private fun assertOriginalTarget(expected: String = referenceArticleLink) {
        ui.waitUntil(5_000) { Intents.getIntents().any { it.action == Intent.ACTION_VIEW } }
        val opened = Intents.getIntents().filter { it.action == Intent.ACTION_VIEW }
        assertEquals(1, opened.size)
        assertEquals(expected, opened.single().dataString)
    }
}
