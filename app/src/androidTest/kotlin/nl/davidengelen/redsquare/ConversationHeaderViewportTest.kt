package nl.davidengelen.redsquare

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import android.graphics.Rect
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class ConversationHeaderNarrowViewportTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun narrowThreadsKeepHeaderReadingAndRealReplyKeyboard() = ConversationHeaderViewportFixture(ui).exercise(320)
}

class ConversationHeaderPixelViewportTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun pixelSizedThreadsKeepHeaderReadingAndRealReplyKeyboard() = ConversationHeaderViewportFixture(ui).exercise(412)
}

internal class ConversationHeaderViewportFixture(private val ui: ComposeTestRule) {
    fun exercise(width: Int) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val folder = File(context.cacheDir, "conversation-viewport-evidence").apply { mkdirs() }
        val root = instrumentation.context.assets.open("native-thread-reference.json").reader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("root")
        }
        val rootPost = RedSquareFeed.readTarget("[$root]", root.get("sig").asString).posts.single()
        val author = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Published unsafe fixture only.
        val texts = (1..12).map { "Reading reply $it. Available conversation text keeps its complete final sentence at the real system text size." }
        val replies = texts.mapIndexed { index, text -> postingArchiveRow(SaitoTransaction.create(author,
            RedSquarePosts.message(text, parent = rootPost), 1_700_000_000_010L + index)) }
        val longThread = AtomicBoolean(false)
        lateinit var peer: ControlledFeedPeer
        withAppFrameFixture("viewport-$width", "conversation-viewport-evidence", "[$root]", configurePeer = { peer = it }, archiveResponse = { query ->
            if (query.has("field5") && longThread.get()) "[$root,${replies.joinToString(",")}]" else "[$root]"
        }) { activity ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.withPostViewports(activity, folder, "thread", widthDp = width) { label, theme ->
                longThread.set(context.resources.configuration.fontScale > 1.1f)
                ui.clickVisiblePostBody(rootPost.text)
                ui.waitUntil(15_000) { ui.onNodeWithTag("conversation-post-list").isDisplayed() && ui.refreshableContentReady() }
                ui.onNodeWithText("Conversation").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
                val device = UiDevice.getInstance(instrumentation)
                fun observeHeaderTargets(stage: String, replyPost: FeedPost) {
                    val targets = JsonObject()
                    val rectangles = mutableListOf<Rect>()
                    for (action in listOf("Back", "Conversation information")) {
                        val control = ui.onNode(hasText(action) and hasClickAction())
                        control.assertIsDisplayed().assertIsEnabled()
                        var native = requireNotNull(device.findObject(By.text(action)))
                        while (!native.isClickable) native = requireNotNull(native.parent)
                        val bounds = native.visibleBounds
                        val density = context.resources.displayMetrics.density
                        assertTrue("$action keeps its48dp native target", bounds.width() >= 48 * density - 1 && bounds.height() >= 48 * density - 1)
                        assertTrue("Header targets do not overlap", rectangles.none { Rect.intersects(it, bounds) })
                        rectangles += bounds
                        targets.addProperty(action, bounds.flattenToString())
                    }
                    val replyAction = ui.onNode(postReplyAction(replyPost.id)).assertIsDisplayed().assertIsEnabled()
                    val description = replyAction.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
                    var ancestor = requireNotNull(device.findObject(By.text(replyPost.text)))
                    var nativeReply = ancestor.findObject(By.desc(description))
                    while (nativeReply == null) {
                        ancestor = requireNotNull(ancestor.parent)
                        nativeReply = ancestor.findObject(By.desc(description))
                    }
                    var target = requireNotNull(nativeReply)
                    while (!target.isClickable) target = requireNotNull(target.parent)
                    val bounds = target.visibleBounds
                    val density = context.resources.displayMetrics.density
                    assertTrue("Inline Reply keeps its48dp native target", bounds.width() >= 48 * density - 1 && bounds.height() >= 48 * density - 1)
                    assertTrue("Reply and header targets do not overlap", rectangles.none { Rect.intersects(it, bounds) })
                    targets.addProperty("Reply", bounds.flattenToString())
                    targets.addProperty("replyPostId", replyPost.id)
                    File(folder, if (stage == "initial") "$label-header-targets.json" else "$label-$stage-header-targets.json").writeText(targets.toString())
                }
                ui.revealPostReply(rootPost.id)
                observeHeaderTargets("initial", rootPost)
                ui.onNodeWithText(context.getString(R.string.conversation_history)).assertDoesNotExist()
                ui.onNode(hasText("Conversation information") and hasClickAction()).performClick()
                ui.onNodeWithText(context.getString(R.string.conversation_history)).assertIsDisplayed()
                captureNativeScreen(File(folder, "$label-history.png"))
                ui.onNodeWithText("Done").performClick()
                val anchorText = if (longThread.get()) texts[5] else rootPost.text
                val replyPost = if (longThread.get()) RedSquareFeed.decode(replies[5].get("tx").asString) else rootPost
                ui.onNodeWithTag("conversation-post-list").performScrollToNode(hasText(anchorText))
                ui.revealAppNavigation()
                val anchor = ui.onNodeWithText(anchorText, useUnmergedTree = true)
                anchor.assertIsDisplayed()
                val layouts = mutableListOf<TextLayoutResult>()
                anchor.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { check(it(layouts)) }
                val layout = layouts.single()
                assertEquals("Public Conversation keeps its approved14sp body", 14f, layout.layoutInput.style.fontSize.value, 0.01f)
                assertEquals("The full authentic text remains available", anchorText.length, layout.getLineEnd(layout.lineCount - 1))
                ui.revealPostReply(replyPost.id)
                anchor.assertIsDisplayed()
                val before = anchor.fetchSemanticsNode().boundsInRoot.top
                observeHeaderTargets("reading", replyPost)
                captureNativeScreen(File(folder, "$label-reading.png"))
                ui.replyToPost(replyPost.id)
                val editor = ui.onNode(hasSetTextAction() and isEnabled())
                ui.waitUntil(15_000) {
                    if (!editor.isDisplayed()) false else {
                        val visible = editor.fetchSemanticsNode().boundsInRoot.height
                        val complete = editor.getUnclippedBoundsInRoot().let { (it.bottom - it.top).value } * context.resources.displayMetrics.density
                        visible >= 80 * context.resources.displayMetrics.density - 1 && complete <= visible + 1
                    }
                }
                captureNativeScreen(File(folder, "$label-reply-entry.png"))
                val draft = "A real reply for $label. Preserve this exact unsent text."
                editor.performClick().performTextReplacement(draft)
                ui.waitUntil(10_000) { focusedWindowImeBottom() > 0 }
                ui.acknowledgeKeyboardFontEducation()
                editor.performScrollTo().assertTextContains(draft)
                ui.onNodeWithText(replyPost.text, useUnmergedTree = true).assertExists()
                captureNativeScreen(File(folder, "$label-reply-ime.png"))
                if (width == 320 && context.resources.configuration.fontScale > 1.5f && theme == "Light") {
                    val samples = com.google.gson.JsonArray()
                    val started = android.os.SystemClock.elapsedRealtime()
                    for (pause in listOf(250L, 500L)) {
                        android.os.SystemClock.sleep(pause)
                        editor.assertTextContains(draft)
                        val bounds = editor.fetchSemanticsNode().boundsInRoot
                        samples.add(JsonObject().apply {
                            addProperty("elapsedMillis", android.os.SystemClock.elapsedRealtime() - started)
                            addProperty("editorBounds", bounds.toString())
                            addProperty("imeBottomPixels", focusedWindowImeBottom())
                        })
                        captureNativeScreen(File(folder, "$label-reply-ime-observed-$pause.png"))
                    }
                    File(folder, "$label-settled-ime-observations.json").writeText(samples.toString())
                }
                ui.onNode(hasText("Back") and hasClickAction()).performClick()
                ui.waitUntil(15_000) { ui.onNodeWithTag("conversation-post-list").isDisplayed() && ui.refreshableContentReady() }
                ui.revealAppNavigation()
                anchor.assertIsDisplayed()
                assertEquals("Unsent reply return keeps the same post and pixel", before, anchor.fetchSemanticsNode().boundsInRoot.top, 1f)
                captureNativeScreen(File(folder, "$label-return.png"))
                ui.replyToPost(replyPost.id)
                ui.waitUntil(15_000) { editor.isDisplayed() }
                editor.assertTextContains(draft)
                ui.onNode(hasText("Back") and hasClickAction()).performClick()
                ui.waitUntil(15_000) { ui.onNodeWithTag("conversation-post-list").isDisplayed() && ui.refreshableContentReady() }
                peer.goOffline()
                ui.refreshVisibleView()
                ui.waitUntil(20_000) { ui.onNodeWithText("Could not refresh replies").isDisplayed() }
                val retry = ui.onNode(hasText("Retry") and hasClickAction() and hasAnyAncestor(hasTestTag("conversation-status")))
                retry.assertIsDisplayed().assertIsEnabled()
                captureNativeScreen(File(folder, "$label-cached-outage.png"))
                ui.onNodeWithContentDescription(context.getString(R.string.status_details_for, "Could not refresh replies")).performClick()
                ui.onNodeWithText(context.getString(R.string.conversation_error)).performScrollTo().assertIsDisplayed()
                captureNativeScreen(File(folder, "$label-error-details.png"))
                ui.onNodeWithText("Done").performClick()
                peer.rejectNewConnections = false
                retry.performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText(context.getString(R.string.conversation_refresh_success)).isDisplayed() }
                ui.waitUntil(5_000) { !ui.onNodeWithText(context.getString(R.string.conversation_refresh_success)).isDisplayed() }
                ui.onNode(hasText("Back") and hasClickAction()).performClick()
                ui.waitUntil(15_000) { ui.onNodeWithTag("feed-post-list").isDisplayed() }
                ui.revealAppNavigation()
            }
        }
    }
}
