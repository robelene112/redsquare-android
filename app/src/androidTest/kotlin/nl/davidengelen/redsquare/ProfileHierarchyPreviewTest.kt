package nl.davidengelen.redsquare

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real profile identity, selected history and confirmation journeys through the native app. */
class ProfileHierarchyPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun identityDetailsHistoryAndSecondaryActionsStayReadableAcrossThemesAndTextSizes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val feed = instrumentation.context.assets.open("native-feed-reference.json").reader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow")
        }
        val profile = instrumentation.context.assets.open("native-profile-reference.json").reader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("snapshot")
        }
        val author = profile.get("field2").asString
        assertEquals(author, feed.get("field2").asString)
        val folder = File(context.cacheDir, "profile-hierarchy-evidence").apply { mkdirs() }
        withAppFrameFixture("profile-hierarchy", "profile-hierarchy-evidence", "[$feed]",
            profileArchiveResponse = { criteria ->
                assertEquals(author, criteria.get("field2").asString)
                "[$profile]"
            }) { activity ->
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            ui.waitUntil(15_000) { ui.onNode(hasText("Later") and hasClickAction()).isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.withPostViewports(activity, folder, "profile-hierarchy") { label, _ ->
                withFailureCapture({
                    File(folder, "$label-failure.semantics.txt").writeText(
                        ui.onAllNodes(isRoot(), useUnmergedTree = true).printToString(maxDepth = Int.MAX_VALUE))
                }) {
                ui.clickVisiblePostProfile("Anon-${author.take(6)}")
                waitFor("Signed bio 🌱\nSecond line")
                capture(folder, "$label-default.png")
                val posts = tab("Posts")
                // At200% this lazy item is below the identity header and not composed yet.
                ui.onNode(hasScrollToIndexAction()).performScrollToNode(posts)
                ui.onNode(posts).performScrollTo().assertIsSelected()
                for (name in listOf("Replies", "Likes", "Posts")) {
                    val selected = tab(name)
                    ui.onNode(selected).performScrollTo().assertIsDisplayed().performClick()
                    ui.onNode(selected).assertIsSelected()
                    listOf("Posts", "Replies", "Likes").filter { it != name }.forEach {
                        ui.onNode(tab(it)).assertIsNotSelected()
                    }
                    capture(folder, "$label-${name.lowercase()}-selected.png")
                }
                // The header menu inspects identity without moving the history viewport.
                ui.onNode(hasText("Profile actions") and hasClickAction()).performClick()
                ui.onNodeWithText("Public identity").assertIsDisplayed().performClick()
                ui.onNodeWithText(author).performScrollTo().assertIsDisplayed()
                capture(folder, "$label-full-identity.png")
                var originalClipboard: ClipData? = null
                ui.runOnIdle { originalClipboard = clipboard.primaryClip }
                var copyFailure: Throwable? = null
                try {
                    ui.onNodeWithText("Copy public key").assertIsEnabled().performClick()
                    waitFor("Public key copied.")
                    ui.runOnIdle { assertEquals(author, clipboard.primaryClip?.getItemAt(0)?.text?.toString()) }
                } catch (failure: Throwable) { copyFailure = failure; throw failure }
                finally { cleanupAfterTest(copyFailure, { ui.runOnIdle {
                    originalClipboard?.let(clipboard::setPrimaryClip) ?: clipboard.clearPrimaryClip()
                } }) }
                ui.onNodeWithText("Done").performClick()
                ui.onNode(hasText("Profile actions") and hasClickAction()).performClick()
                waitFor("Mute account")
                ui.onNodeWithText("Mute account").performClick()
                waitFor(context.getString(R.string.personal_mute_detail))
                capture(folder, "$label-mute-confirmation.png")
                ui.onNodeWithText("Cancel").performClick()
                waitFor("Mute account")
                ui.onNodeWithText("Block account").assertIsDisplayed()
                ui.onNodeWithText("Report user to publisher").assertIsDisplayed()
                ui.onNodeWithText("Done").performClick()
                ui.waitUntil(15_000) { ui.onNode(hasText("Refresh") and hasClickAction() and isEnabled()).isDisplayed() }
                ui.onNode(hasText("Refresh") and hasClickAction() and isEnabled()).performClick()
                waitFor("Signed bio 🌱\nSecond line")
                ui.onNode(hasText("Back") and hasClickAction()).performClick()
                ui.waitUntil(15_000) { ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
                }
            }
        }
    }

    private fun tab(name: String) = hasText(name) and hasClickAction() and
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun waitFor(text: String) = ui.waitUntil(15_000) { ui.onNodeWithText(text).isDisplayed() }

    private fun capture(folder: File, name: String) = captureNativeScreen(File(folder, name))
}
