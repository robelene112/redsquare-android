package nl.davidengelen.redsquare

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.talk.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class TalkActiveViewportTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun videoCallControlsStayVisibleAndReachableAt320dpAcrossThemesAndLargeText() = active(320)
    @Test fun videoCallControlsStayVisibleAndReachableAt412dpAcrossThemesAndLargeText() = active(412)
    private fun active(width: Int) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
        var originalClipboard: android.content.ClipData? = null
        var clipboardObserved = false
        withAppFrameFixture("talk-active-$width", "talk-active-evidence", advertisedServices = "archive||;relay||",
            beforeStateRestore = {
                if (clipboardObserved) {
                    val before = originalClipboard
                    if (before == null) clipboard.clearPrimaryClip() else clipboard.setPrimaryClip(before)
                }
            }) { activity ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            val wallet = AndroidWalletStore(context).loadExisting()
            val backend = AndroidBackendStore(context).load()
            var endpoint: String? = null
            activity.onActivity { endpoint = it.intent.getStringExtra("feedPeerUrl") }
            val account = TalkRuntime.account(context, wallet, backend, requireNotNull(endpoint)).also { it.attach() }
            var token: String? = null
            var primary: Throwable? = null
            val folder = File(context.cacheDir, "talk-active-evidence").apply { mkdirs() }
            try {
                assertTrue(runBlocking { account.start(TalkLink(backend, TalkRoom(SaitoWallet.create().publicKey, wallet.publicKey)), null, true, true) })
                val call = requireNotNull(TalkRuntime.call.value).also { token = it.token }
                val uri = Uri.Builder().scheme("redsquare-talk").authority("call").appendPath(call.token)
                    .appendQueryParameter("owner", wallet.publicKey).appendQueryParameter("backend", backend.id).build()
                activity.onActivity { it.startActivity(Intent(it, MainActivity::class.java).setData(uri)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)) }
                ui.waitUntil(5000) { ui.onNodeWithContentDescription("Switch app: Saito Talk").isDisplayed() }
                ui.waitUntil(5000) { ui.onNodeWithText("Your room is ready").isDisplayed() }
                ui.onNodeWithText("Share invitation").assertIsDisplayed()
                ui.runOnIdle { originalClipboard = clipboard.primaryClip; clipboardObserved = true }
                ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Copy invitation"))
                ui.onNodeWithText("Copy invitation").performClick()
                ui.waitUntil(5000) { ui.onNodeWithText("Invitation copied").isDisplayed() }
                var copiedLink: String? = null
                ui.runOnIdle {
                    copiedLink = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                }
                val copiedRoom = requireNotNull(TalkLink.parse(requireNotNull(copiedLink)))
                assertEquals(call.link.room.id, copiedRoom.room.id)
                assertEquals(wallet.publicKey, copiedRoom.room.host)
                assertEquals(backend, copiedRoom.backend)
                fun home() {
                    ui.onNodeWithContentDescription("Switch app: Saito Talk").performClick()
                    ui.onNode(hasText("Red Square") and hasClickAction()).performClick()
                    ui.waitUntil(5000) { ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
                }
                home()
                ui.withPostViewports(activity, folder, "active-$width", widthDp = width) { label, theme ->
                    ui.onNodeWithText("Return to call").performClick()
                    ui.waitUntil(5000) { ui.onNodeWithContentDescription("Switch app: Saito Talk").isDisplayed() }
                    if (label.contains("-font100-")) {
                        ui.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
                        ui.onNodeWithText("Your room is ready").assertIsDisplayed()
                        ui.onNodeWithText("Share invitation").assertIsDisplayed()
                        captureNativeScreen(File(folder, "$label-entry.png"))
                    }
                    val controlBounds = mutableListOf<androidx.compose.ui.geometry.Rect>()
                    for (text in listOf("Mute", "Camera off", "Switch camera", "Audio output", "End call")) {
                        val node = ui.onNode(hasText(text) and hasClickAction()).assertIsDisplayed().fetchSemanticsNode()
                        assertTrue("$text remains a48dp action in $label", node.boundsInRoot.height / context.resources.displayMetrics.density >= 48f)
                        assertTrue("$text retains a48dp touch width in $label", node.boundsInRoot.width / context.resources.displayMetrics.density >= 48f)
                        controlBounds.add(node.boundsInRoot)
                        assertWholeWords(hasText(text))
                    }
                    val density = context.resources.displayMetrics.density
                    for (first in 0..2) for (second in first + 1..3) {
                        val a = controlBounds[first]; val b = controlBounds[second]
                        assertTrue("Call controls have12dp of space in $label",
                            maxOf(b.left - a.right, a.left - b.right, b.top - a.bottom, a.top - b.bottom) / density >= 11.5f)
                    }
                    assertTrue("End call is separated by16dp in $label",
                        (controlBounds.last().top - controlBounds.take(4).maxOf { it.bottom }) / density >= 15.5f)
                    assertEquals(call.token, TalkRuntime.call.value?.token)
                    assertTrue(requireNotNull(TalkRuntime.call.value).phase.started)
                    captureNativeScreen(File(folder, "$label-active.png"))
                    ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("talk-invitation-actions"))
                    ui.onNodeWithText("Share invitation").assertIsDisplayed()
                    val share = ui.onNode(hasText("Share invitation") and hasClickAction()).fetchSemanticsNode().boundsInRoot
                    val copy = ui.onNode((hasText("Copy invitation") or hasText("Invitation copied")) and hasClickAction())
                        .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                    assertTrue("Invitation actions have12dp of space in $label",
                        maxOf(copy.left - share.right, share.left - copy.right, copy.top - share.bottom, share.top - copy.bottom) / density >= 11.5f)
                    assertTrue("Both invitation actions retain48dp targets in $label", minOf(share.height, copy.height) / density >= 48f)
                    assertWholeWords(hasText("Share invitation"))
                    assertWholeWords(hasText("Copy invitation") or hasText("Invitation copied"))
                    captureNativeScreen(File(folder, "$label-invitation.png"))
                    home()
                }
                TalkRuntime.end(call.token)
                ui.waitUntil(5000) { TalkRuntime.call.value?.phase == CallPhase.ENDED }
            } catch (failure: Throwable) { primary = failure; throw failure }
            finally {
                cleanupAfterTest(primary, { token?.let(TalkRuntime::end) }, { account.detach() })
            }
        }
    }

    private fun assertWholeWords(matcher: SemanticsMatcher) {
        val layouts = mutableListOf<TextLayoutResult>()
        ui.onNode(matcher, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            assertTrue("The visible action exposes its actual text layout", it(layouts))
        }
        val result = layouts.single()
        val text = result.layoutInput.text.text
        for (line in 0 until result.lineCount - 1) {
            val end = result.getLineEnd(line, visibleEnd = true)
            assertFalse("Action words remain whole: $text", end in 1 until text.length && text[end - 1].isLetter() && text[end].isLetter())
        }
    }
}
