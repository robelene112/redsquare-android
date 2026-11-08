package nl.davidengelen.redsquare

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import androidx.compose.ui.graphics.asAndroidBitmap
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

/** Same signed identity, actual native routes, no public writes or external services. */
@OptIn(ExperimentalTestApi::class)
class AvatarIdentityJourneyTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val red = Color.rgb(220, 30, 40)
    private val blue = Color.rgb(20, 60, 220)

    @Test fun lightPictureAndSignedRemovalFollowTheIdentityAcrossAllDestinations() = journey("Light")
    @Test fun darkPictureAndSignedRemovalFollowTheIdentityAcrossAllDestinations() = journey("Dark")

    private fun journey(theme: String) = withAvatarJourney(theme) { _, _, response, requests, author ->
        ui.waitUntil(15_000) { ui.onNodeWithText("Shared avatar journey").isDisplayed() }
        response.set(profile(author, red, 1))
        refresh()
        tour(theme, "custom", red, author.publicKey)
        val afterTour = requests.get()
        assertTrue("Repeated destinations must share discovery rather than request every row", afterTour in 1..3)
        response.set(profile(author, blue, 2))
        refresh()
        assertPicture(authorRow(author.publicKey), blue)
        capture("$theme-changed")
        response.set(profile(author, null, 3))
        refresh()
        tour(theme, "removed", null, author.publicKey)
        assertTrue("Removal must come from a fresh signed observation", requests.get() > afterTour)
        assertTrue("Visible rows must not create a discovery flood", requests.get() <= afterTour + 3)
    }

    @Test fun cachedPictureSurvivesOfflineRecreationAndDoesNotCrossNodes() = withAvatarJourney("Dark") { activity, peer, response, _, author ->
        ui.waitUntil(15_000) { ui.onNodeWithText("Shared avatar journey").isDisplayed() }
        response.set(profile(author, red, 10))
        refresh()
        assertPicture(authorRow(author.publicKey), red)
        peer.rejectNewConnections = true
        activity.recreate()
        ui.waitUntil(15_000) { ui.onNodeWithText("Shared avatar journey").isDisplayed() }
        assertPicture(authorRow(author.publicKey), red)
        capture("offline-recreated")
        // Same signer, other node: no signed observation has been received there.
        AndroidBackendStore(context).save(SaitoBackend.OFFICIAL)
        activity.recreate()
        ui.waitUntil(15_000) { ui.onNodeWithText(context.getString(R.string.selected_server, "saito.io")).isDisplayed() }
        ui.onNode(hasText("Chat") and hasClickAction()).performClick()
        val contact = ui.onNode(hasText("Anon-${author.publicKey.take(6)}") and hasClickAction())
        ui.waitUntil(15_000) { contact.isDisplayed() }
        assertPicture(contact, null)
        capture("other-node-generated")
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        activity.recreate()
        ui.waitUntil(15_000) { ui.onNodeWithText("Shared avatar journey").isDisplayed() }
        assertPicture(authorRow(author.publicKey), red)
        capture("original-node-cached")
    }

    private fun tour(theme: String, stage: String, color: Int?, key: String) {
        assertPicture(authorRow(key), color)
        capture("$theme-$stage-feed")
        ui.onNodeWithText("Shared avatar journey").performClick()
        ui.waitUntil(15_000) { ui.onNodeWithText("Conversation").isDisplayed() }
        assertPicture(authorRow(key), color)
        capture("$theme-$stage-thread")
        tab("Search")
        ui.onNodeWithText("People").performClick()
        val input = ui.onNode(hasSetTextAction() and isEnabled())
        ui.waitUntil(15_000) { input.isDisplayed() }
        input.performTextReplacement(key)
        input.performImeAction()
        ui.waitUntil(15_000) { ui.onAllNodesWithContentDescription("View profile for Anon-${key.take(6)}").fetchSemanticsNodes().isNotEmpty() }
        ui.waitUntil(10_000) { focusedWindowImeBottom() == 0 }
        assertPicture(ui.onAllNodesWithContentDescription("View profile for Anon-${key.take(6)}")[0], color)
        capture("$theme-$stage-people")
        tab("Notifications")
        ui.waitUntil(15_000) { ui.onNodeWithText("Liked a post").isDisplayed() }
        assertPicture(authorRow(key), color)
        capture("$theme-$stage-activity")
        tab("Chat")
        val contact = ui.onNode(hasText("Anon-${key.take(6)}") and hasClickAction())
        ui.waitUntil(15_000) { contact.isDisplayed() }
        assertPicture(contact, color)
        capture("$theme-$stage-chat")
        tab("Home")
        ui.waitUntil(15_000) { ui.onNodeWithText("Shared avatar journey").isDisplayed() }
    }

    private fun tab(label: String) {
        ui.revealAppNavigation()
        ui.onNode(hasText(label) and hasClickAction()).performClick()
    }
    private fun authorRow(key: String) = ui.onNodeWithContentDescription("View profile for Anon-${key.take(6)}")
    private fun refresh() {
        ui.waitUntil(15_000) { runCatching {
            ui.onNodeWithTag("feed-post-list").performCustomAccessibilityActionWithLabel("Refresh"); true
        }.getOrDefault(false) }
    }
    private fun assertPicture(node: SemanticsNodeInteraction, color: Int?) {
        ui.waitUntil(10_000) {
            val bitmap = node.captureToImage().asAndroidBitmap()
            var matches = 0
            for (y in 0 until bitmap.height step 2) for (x in 0 until bitmap.width step 2) {
                val pixel = bitmap.getPixel(x, y)
                val expected = color ?: (if (Color.blue(pixel) > Color.red(pixel)) blue else red)
                if (kotlin.math.abs(Color.red(pixel) - Color.red(expected)) < 10 &&
                    kotlin.math.abs(Color.green(pixel) - Color.green(expected)) < 10 &&
                    kotlin.math.abs(Color.blue(pixel) - Color.blue(expected)) < 10) matches++
            }
            if (color != null) matches >= 50 else matches == 0
        }
    }
    private fun capture(name: String) = captureNativeScreen(File(context.cacheDir, "avatar-evidence/$name.png"))
    private fun profile(author: SaitoWallet, color: Int?, revision: Int): String {
        val source = color?.let {
            val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(it) }
            try {
                val output = ByteArrayOutputStream()
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                "data:image/png;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            } finally { bitmap.recycle() }
        }
        val tx = SaitoTransaction.create(author, JsonObject().apply {
            addProperty("module", "Profile"); addProperty("request", "update profile"); addProperty("snapshot", 1)
            add("data", JsonObject().apply { if (source != null) addProperty("image", source) })
        }, 1_900_000_000_000L + revision)
        return JsonObject().apply { addProperty("tx", tx.envelope()) }.let { "[$it]" }
    }
}
