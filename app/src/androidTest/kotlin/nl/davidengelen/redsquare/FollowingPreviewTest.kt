package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import org.junit.Rule
import org.junit.Test

class FollowingPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun followingAnAccountRestrictsTheFeedAndUnfollowingRemovesItsPosts() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".dev"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        context.noBackupFilesDir.listFiles()?.filter {
            it.name.startsWith("feed.") || it.name.startsWith("conversation.") || it.name.startsWith("profile.") || it.name.startsWith("following.")
        }?.forEach { check(it.delete()) }
        val fixture = instrumentation.context.assets.open("native-following-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject
        }
        val root = fixture.getAsJsonObject("root")
        val other = fixture.getAsJsonObject("otherRoot")
        val author = root.get("field2").asString
        val peer = ControlledFeedPeer("[$root,$other]") { criteria ->
            check(criteria.get("field1").asString == "RedSquare" && criteria.get("flagged_ne").asInt == 1)
            if (criteria.has("field2")) {
                check(criteria.get("field2").asString == author)
                "[$root]"
            } else "[$root,$other]"
        }
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                try {
                    waitFor("Native Red Square reference post")
                    ui.onNodeWithText("Another account reference post").assertIsDisplayed()
                    ui.onNodeWithText(author).performClick()
                    waitFor("Profile")
                    ui.onNodeWithText("Follow").performClick()
                    waitFor("Unfollow")
                    ui.onNodeWithText("Back").performClick()
                    ui.onNodeWithText("Following").performClick()
                    waitFor("Native Red Square reference post")
                    ui.onNodeWithText("Another account reference post").assertDoesNotExist()
                    ui.onNodeWithText(author).performClick()
                    waitFor("Unfollow")
                    ui.onNodeWithText("Unfollow").performClick()
                    waitFor("Follow")
                    ui.onNodeWithText("Back").performClick()
                    waitFor("Follow accounts to see their posts here.")
                    ui.onNodeWithText("Native Red Square reference post").assertDoesNotExist()
                    peer.assertHealthy()
                    captureFeedEvidence("following-evidence/unfollowed-empty-feed.png")
                } catch (failure: Throwable) {
                    captureFeedEvidence("following-evidence/following-first-failure.png")
                    throw failure
                }
            }
        }
    }

    private fun waitFor(text: String) = ui.waitUntil(15_000) {
        ui.onAllNodes(hasText(text)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }
}
