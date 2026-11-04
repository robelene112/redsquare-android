package nl.davidengelen.redsquare

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Signed empty/short/long biographies at actual system text sizes and both approved widths. */
class ProfileRedesignViewportTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun narrowProfileHasDistinctIdentityAndHistoryAtAllTextSizes() = matrix(320)
    @Test fun pixelSizedProfileHasDistinctIdentityAndHistoryAtAllTextSizes() = matrix(412)

    private fun matrix(width: Int) {
        val author = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Published unsafe fixture only.
        val rows = AtomicReference("[]")
        val folder = File(context.cacheDir, "profile-redesign-evidence").apply { mkdirs() }
        var revision = 0L
        var previousBio = ""
        withAppFrameFixture("profile-$width", "profile-redesign-evidence", profileArchiveResponse = {
            if (it.get("field2").asString == author.publicKey) rows.get() else "[]"
        }) { activity ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.withPostViewports(activity, folder, "profile-section", widthDp = width) { label, theme ->
                val scale = context.resources.configuration.fontScale
                val bio = when {
                    scale > 1.5f -> ("A longer public biography for $label. Writing and building in public.\n").repeat(3).trim()
                    scale > 1.1f -> ""
                    else -> "A short public biography for $label."
                }
                val color = when { bio.isEmpty() -> Color.BLUE; scale > 1.5f -> Color.GREEN; else -> Color.RED }
                rows.set(profile(author, bio, color, ++revision))
                ui.clickVisiblePostProfile("Anon-${author.publicKey.take(6)}")
                val list = ui.onNode(hasScrollToIndexAction())
                ui.waitUntil(15_000) { list.isDisplayed() }
                list.performScrollToIndex(0)
                val picture = ui.onNodeWithContentDescription("Profile picture")
                ui.waitUntil(15_000) { runCatching {
                    val bitmap = picture.captureToImage().asAndroidBitmap()
                    bitmap.getPixel(bitmap.width / 2, bitmap.height / 2) == color
                }.getOrDefault(false) }
                ui.onNode(hasText("Anon-${author.publicKey.take(6)}") and
                    SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertExists()
                if (bio.isNotEmpty()) ui.onNodeWithText(bio).assertExists()
                else if (previousBio.isNotEmpty()) ui.onNodeWithText(previousBio).assertDoesNotExist()
                previousBio = bio
                captureNativeScreen(File(folder, "$label-identity.png"))
                val boundary = hasTestTag("profile-history-divider")
                list.performScrollToNode(boundary)
                ui.onNode(boundary).performScrollTo().assertIsDisplayed()
                val pixels = ui.onNode(boundary).captureToImage().asAndroidBitmap()
                val expected = if (theme == "Dark") Color.rgb(43, 50, 60) else Color.rgb(221, 226, 236)
                assertEquals("The divider uses the shared theme's visible line colour", expected,
                    pixels.getPixel(pixels.width / 2, pixels.height / 2))
                for (name in listOf("Posts", "Replies", "Likes", "Posts")) {
                    val target = hasText(name) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
                    ui.onNode(target).performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
                    val bounds = ui.onNode(target).getUnclippedBoundsInRoot()
                    assertTrue("Profile history remains accessible", (bounds.bottom - bounds.top).value >= 48)
                }
                captureNativeScreen(File(folder, "$label-history-boundary.png"))
                ui.onNode(hasText("Back") and hasClickAction()).performClick()
                ui.waitUntil(15_000) { ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
            }
        }
    }

    @Test fun realFollowCountAndOwnUnsentEditorRetainTheirSeparatePublicState() {
        withAppFrameFixture("own-profile", "profile-redesign-evidence") {
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.clickVisiblePostProfile("Anon-jesTu2")
            ui.waitUntil(15_000) { ui.onNode(hasText("Follow") and isEnabled()).isDisplayed() }
            ui.onNode(hasText("Follow") and hasClickAction()).performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("Unfollow") and isEnabled()).isDisplayed() }
            ui.onNode(hasText("Back") and hasClickAction()).performClick()
            ui.onNode(hasText("Your profile") and hasClickAction()).performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("1 following").isDisplayed() }
            captureNativeScreen(File(context.cacheDir, "profile-redesign-evidence/own-following.png"))
            ui.onNode(hasText("Edit profile") and hasClickAction() and isEnabled()).performClick()
            val editor = ui.onNode(hasSetTextAction() and isEnabled())
            ui.waitUntil(15_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            editor.performScrollTo().assertIsDisplayed()
            editor.performClick().performTextReplacement("My separate unsent profile biography")
            ui.waitUntil(10_000) { focusedWindowImeBottom() > 0 }
            ui.onNodeWithText("Save draft").performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("Edit profile") and hasClickAction() and isEnabled()).isDisplayed() }
            ui.onNodeWithText("My separate unsent profile biography").assertDoesNotExist()
            ui.onNode(hasText("Edit profile") and hasClickAction() and isEnabled()).performClick()
            ui.waitUntil(15_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            editor.performScrollTo().assertIsDisplayed()
            editor.assertTextContains("My separate unsent profile biography")
            captureNativeScreen(File(context.cacheDir, "profile-redesign-evidence/own-editor-restored.png"))
        }
    }

    private fun profile(author: SaitoWallet, bio: String, color: Int, revision: Long): String {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        val png = try { ByteArrayOutputStream().also { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }.toByteArray() }
            finally { bitmap.recycle() }
        val tx = SaitoTransaction.create(author, JsonObject().apply {
            addProperty("module", "Profile"); addProperty("request", "update profile"); addProperty("snapshot", 1)
            add("data", JsonObject().apply {
                addProperty("description", bio); addProperty("image", "data:image/png;base64," + Base64.encodeToString(png, Base64.NO_WRAP))
            })
        }, 1_900_000_000_000L + revision)
        return JsonObject().apply { addProperty("tx", tx.envelope()) }.let { "[$it]" }
    }
}
