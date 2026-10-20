package nl.davidengelen.redsquare

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.util.Base64
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual text layout and reachable native controls, with signed identity and node-supplied counts. */
class PostPresentationViewportTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun longRegisteredIdentityAndNodeCountsRemainReadableAcrossTextSizesAndThemes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val author = SaitoWallet.create()
        // Existing public unsafe debug registrar, never a production authority.
        val registrar = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val name = "LongNativeRegisteredPostAuthorForLargeText123@saito"
        val text = "Long post viewport preview. " + "Readable paragraphs keep their complete original text. ".repeat(8)
        val row = postingArchiveRow(SaitoTransaction.create(author, RedSquarePosts.message(text), 1_700_000_000_000))
        val envelope = JsonParser.parseString(row.get("tx").asString).asJsonObject
        envelope.addProperty("opt", Base64.getEncoder().encodeToString(
            "{\"num_likes\":9223372036854775807,\"num_retweets\":12345}".toByteArray()))
        row.addProperty("tx", envelope.toString())
        val hash = "b".repeat(64)
        val proof = JsonObject().apply {
            addProperty("identifier", name); addProperty("publickey", author.publicKey)
            addProperty("bid", 1); addProperty("bsh", hash); addProperty("lc", 1)
            addProperty("signer", registrar.publicKey)
            addProperty("sig", registrar.sign((name + author.publicKey + "1" + hash).toByteArray()))
        }
        var observedPeer: ControlledFeedPeer? = null
        withAppFrameFixture("post-viewport", "post-viewport-evidence", feedRows = "[$row]",
            configurePeer = { observedPeer = it }, registryResponse = { request ->
                when (request.get("request").asString) {
                    "registry query" -> JsonObject().apply {
                        if (request.getAsJsonArray("keys").any { it.asString == author.publicKey })
                            addProperty(author.publicKey, name)
                    }.toString()
                    "registry namecheck" -> {
                        assertEquals(name, request.get("identifier").asString)
                        "[$proof]"
                    }
                    else -> error("Unexpected viewport Registry request")
                }
            }) { activity ->
            val device = UiDevice.getInstance(instrumentation)
            val folder = File(context.cacheDir, "post-viewport-evidence").apply { mkdirs() }
            val profile = hasContentDescription("View profile for $name") and hasClickAction()
            val more = hasContentDescription("Post actions for $name") and hasClickAction()
            ui.waitUntil(20_000) { ui.onNode(profile).isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.withPostViewports(activity, folder, "post") { label, theme ->
                ui.onNode(hasScrollAction()).performScrollToNode(profile)
                ui.onNode(profile).assertIsDisplayed()
                capture("$label-identity-body.png")
                val actions = listOf("Like", "Repost", "Quote").map { action ->
                    hasContentDescription("$action post by $name") and hasClickAction()
                }
                ui.onNode(hasScrollAction()).performScrollToNode(actions.last())
                // Use the existing trailing space to bring the entire footer above the floating composer.
                ui.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { scroll ->
                    check(scroll(0f, 88 * context.resources.displayMetrics.density))
                }
                ui.waitForIdle()
                val viewport = ui.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
                val composer = ui.onNode(hasText("New post") and hasClickAction())
                    .assertIsDisplayed().fetchSemanticsNode().touchBoundsInRoot
                val minimum = 48 * context.resources.displayMetrics.density - 1
                val bounds = actions.map { target ->
                    val node = ui.onNode(target).assertIsDisplayed().assertIsEnabled().fetchSemanticsNode()
                    node.touchBoundsInRoot.also { rect ->
                        assertTrue("Complete reachable 48dp post action at $label", rect.width >= minimum &&
                            rect.height >= minimum && rect.left >= viewport.left && rect.right <= viewport.right &&
                            rect.top >= viewport.top && rect.bottom <= viewport.bottom)
                        assertFalse("Floating composer must not obscure the reachable action at $label",
                            rect.overlaps(composer))
                    }
                }
                bounds.forEachIndexed { index, left -> bounds.drop(index + 1).forEach { right ->
                    assertFalse("Post action targets must not overlap at $label", left.overlaps(right))
                } }
                for (count in listOf("9223372036854775807 likes", "12345 reposts")) {
                    val results = mutableListOf<TextLayoutResult>()
                    ui.onNodeWithText(count, useUnmergedTree = true).assertIsDisplayed()
                        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
                    assertEquals("Count must have an actual rendered text layout", 1, results.size)
                    val layout = results.single()
                    File(folder, "$label-${if (count.endsWith("likes")) "likes" else "reposts"}-layout.json")
                        .writeText(JsonObject().apply {
                            addProperty("count", count)
                            addProperty("size", layout.size.toString())
                            addProperty("constraints", layout.layoutInput.constraints.toString())
                            addProperty("paragraphWidth", layout.multiParagraph.width)
                            addProperty("paragraphHeight", layout.multiParagraph.height)
                            addProperty("didOverflowWidth", layout.didOverflowWidth)
                            addProperty("didOverflowHeight", layout.didOverflowHeight)
                            addProperty("didExceedMaxLines", layout.multiParagraph.didExceedMaxLines)
                            addProperty("lineCount", layout.lineCount)
                            addProperty("lineBounds", (0 until layout.lineCount).joinToString { line ->
                                "${layout.getLineLeft(line)},${layout.getLineTop(line)},${layout.getLineRight(line)},${layout.getLineBottom(line)}"
                            })
                        }.toString())
                    // Paragraph allocation can exceed shrink-wrapped Text width even when every glyph fits.
                    // Measure the actual rendered lines and completeness rather than the unused paragraph space.
                    assertFalse("Complete observed $count must fit vertically at $label", layout.didOverflowHeight)
                    assertFalse(layout.multiParagraph.didExceedMaxLines)
                    assertTrue(layout.lineCount > 0)
                    for (line in 0 until layout.lineCount) {
                        assertFalse("Observed count must not be ellipsized", layout.isLineEllipsized(line))
                        assertTrue("Complete observed $count must fit horizontally at $label",
                            layout.getLineLeft(line) >= 0f && layout.getLineRight(line) <= layout.size.width)
                    }
                    assertEquals("Every observed count character must remain rendered", count.length,
                        layout.getLineEnd(layout.lineCount - 1))
                }
                File(folder, "$label.json").writeText(JsonObject().apply {
                    addProperty("widthPixels", device.displayWidth)
                    addProperty("widthDp", context.resources.configuration.screenWidthDp)
                    addProperty("fontScale", context.resources.configuration.fontScale)
                    addProperty("selectedAppearance", theme)
                    addProperty("touchBounds", bounds.joinToString())
                    addProperty("scrollViewport", viewport.toString())
                    addProperty("floatingComposer", composer.toString())
                }.toString())
                capture("$label-actions-counts.png")
                ui.onNode(hasScrollAction()).performScrollToNode(more)
                ui.onNode(more).assertIsDisplayed().performClick()
                ui.onNodeWithText("Public identity").performClick()
                ui.onNode(hasText(name) and hasAnyAncestor(isDialog())).performScrollTo().assertIsDisplayed()
                ui.onNode(hasText(author.publicKey) and hasAnyAncestor(isDialog())).performScrollTo().assertIsDisplayed()
                capture("$label-full-public-identity.png")
                ui.onNodeWithText("Done").performClick()
            }
            assertTrue(requireNotNull(observedPeer).registryQueries.any {
                it.get("request").asString == "registry namecheck" && it.get("identifier").asString == name
            })
        }
    }

    private fun capture(name: String) = captureNativeScreen(File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "post-viewport-evidence/$name"))
}
