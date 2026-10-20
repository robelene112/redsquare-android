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
            val font = EmulatorFontScale(context, folder, "post")
            val originalSize = device.executeShellCommand("wm size")
            val originalDensity = device.executeShellCommand("wm density")
            val sizeOverride = Regex("Override size: ([0-9]+x[0-9]+)").find(originalSize)?.groupValues?.get(1)
            val densityOverride = Regex("Override density: ([0-9]+)").find(originalDensity)?.groupValues?.get(1)
            val appearance = context.getSharedPreferences("appearance", 0)
            val originalAppearance = appearance.getString("mode", null)
            val profile = hasContentDescription("View profile for $name") and hasClickAction()
            val more = hasContentDescription("Post actions for $name") and hasClickAction()
            var primary: Throwable? = null
            try {
                ui.waitUntil(20_000) { ui.onNode(profile).isDisplayed() }
                ui.onNodeWithText("Later").performClick()
                device.executeShellCommand("wm density 160")
                device.executeShellCommand("wm size 320x640")
                for (scale in listOf(1f, 1.3f, 2f)) {
                    font.apply(scale)
                    activity.recreate()
                    ui.waitUntil(15_000) { device.displayWidth == 320 &&
                        ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
                    for (theme in listOf("Light", "Dark")) {
                        val label = "post-320-font${(scale * 100).toInt()}-${theme.lowercase()}"
                        withFailureCapture({ capture("$label-failure.png") }) {
                            ui.onNode(hasText("Settings") and hasClickAction()).performClick()
                            ui.onNodeWithText(theme).performScrollTo().performClick()
                            ui.waitUntil(10_000) { ui.onNode(hasText(theme) and isSelected()).isDisplayed() }
                            ui.onNodeWithText("Done").performClick()
                            ui.waitUntil(15_000) { ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
                            ui.onNode(hasScrollAction()).performScrollToNode(profile)
                            ui.onNode(profile).assertIsDisplayed()
                            capture("$label-identity-body.png")
                            val actions = listOf("Like", "Repost", "Quote").map { action ->
                                hasContentDescription("$action post by $name") and hasClickAction()
                            }
                            ui.onNode(hasScrollAction()).performScrollToNode(actions.last())
                            val viewport = ui.onRoot().fetchSemanticsNode().boundsInRoot
                            val minimum = 48 * context.resources.displayMetrics.density - 1
                            val bounds = actions.map { target ->
                                val node = ui.onNode(target).assertIsDisplayed().assertIsEnabled().fetchSemanticsNode()
                                node.touchBoundsInRoot.also { rect ->
                                    assertTrue("Complete reachable 48dp post action at $label", rect.width >= minimum &&
                                        rect.height >= minimum && rect.left >= viewport.left && rect.right <= viewport.right &&
                                        rect.top >= viewport.top && rect.bottom <= viewport.bottom)
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
                                assertFalse("Complete observed $count must fit at $label", layout.hasVisualOverflow)
                            }
                            File(folder, "$label.json").writeText(JsonObject().apply {
                                addProperty("widthPixels", device.displayWidth)
                                addProperty("widthDp", context.resources.configuration.screenWidthDp)
                                addProperty("fontScale", context.resources.configuration.fontScale)
                                addProperty("selectedAppearance", theme)
                                addProperty("touchBounds", bounds.joinToString())
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
                    }
                }
                assertTrue(requireNotNull(observedPeer).registryQueries.any {
                    it.get("request").asString == "registry namecheck" && it.get("identifier").asString == name
                })
            } catch (failure: Throwable) { primary = failure; throw failure }
            finally {
                cleanupAfterTest(primary, { activity.close() }, { font.restore() },
                    { device.executeShellCommand("wm size ${sizeOverride ?: "reset"}")
                        check(device.executeShellCommand("wm size").trim() == originalSize.trim()) },
                    { device.executeShellCommand("wm density ${densityOverride ?: "reset"}")
                        check(device.executeShellCommand("wm density").trim() == originalDensity.trim()) },
                    { check(appearance.edit().apply { if (originalAppearance == null) remove("mode")
                        else putString("mode", originalAppearance) }.commit()) })
            }
        }
    }

    private fun capture(name: String) = captureNativeScreen(File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "post-viewport-evidence/$name"))
}
