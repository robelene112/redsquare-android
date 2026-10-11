package nl.davidengelen.redsquare

import android.content.Intent
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PersonalControlsPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also {
        check(it.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
    }

    @Before fun prepareAvailableHistory() {
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        val prefixes = listOf("feed.", "conversation.", "profile.", "following.", "followingfeed.", "search.", "personal.")
        context.noBackupFilesDir.listFiles()?.filter { file -> prefixes.any(file.name::startsWith) }
            ?.forEach { check(it.delete()) }
    }

    @After fun clearPersonalChoices() {
        context.noBackupFilesDir.listFiles()?.filter { it.name.startsWith("personal.") }
            ?.forEach { check(it.delete()) }
    }

    @Test fun mutingSurvivesBackendChangesAndOfflineReopeningAndUnmutingRestoresCachedPosts() {
        val reference = readReference("native-following-reference.json")
        val root = reference.getAsJsonObject("root")
        val other = reference.getAsJsonObject("otherRoot")
        val newer = readReference("native-profile-paging-reference.json").getAsJsonArray("recentPosts")[0].asJsonObject
        val author = root.get("field2").asString
        fun peer(post: JsonObject) = ControlledFeedPeer("[$post,$other]", archiveResponse = { criteria ->
            check(criteria.get("field1").asString == "RedSquare" && criteria.get("flagged_ne").asInt == 1)
            when (criteria.get("field2")?.asString) {
                null -> "[$post,$other]"
                author -> "[$post]"
                other.get("field2").asString -> "[$other]"
                else -> error("Unexpected author")
            }
        })
        val owner = peer(root)
        val official = peer(newer)
        owner.start().use { ownerServer ->
            official.start().use { officialServer ->
                val intent = Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${ownerServer.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${officialServer.port}/wsopen")
                var address = ""
                withFailureCapture({ captureNativeScreen(File(context.cacheDir, "personal-controls-evidence/mute-first-failure.png")) }) {
                    ActivityScenario.launch<MainActivity>(intent).use {
                        waitForEnabled("Refresh")
                        showPost("Native Red Square reference post")
                        ui.onNodeWithText("Wallet").performClick()
                        address = displayedWalletAddress(ui)
                        ui.onNodeWithText("Done").performClick()
                        ui.onNode(hasScrollAction()).performScrollToNode(hasText(author))
                        ui.onNodeWithText(author).assertIsDisplayed().performClick()
                        waitFor("Mute account")
                        ui.onNodeWithText("Mute account").performClick()
                        waitFor("Mute hides this account's posts and social notifications. Chat messages remain available.")
                        ui.onNodeWithText("Mute", substring = false).performClick()
                        waitFor("Unmute account")
                        ui.onNodeWithText("Back").performClick()
                        showPost("Another account reference post")
                        assertPostAbsent("Native Red Square reference post", "Another account reference post")
                        ui.onNodeWithText("Settings").performClick()
                        waitFor("saito.io")
                        ui.onNodeWithText("saito.io").performClick()
                        waitFor("Server: saito.io")
                        waitForEnabled("Refresh")
                        showPost("Another account reference post")
                        assertPostAbsent("Profile newer post 20", "Another account reference post")
                        captureNativeScreen(File(context.cacheDir, "personal-controls-evidence/muted-official.png"), "Another account reference post")
                    }
                    owner.assertHealthy()
                    official.assertHealthy()
                    ownerServer.close()
                    officialServer.close()
                    ActivityScenario.launch<MainActivity>(intent).use {
                        waitFor("Server: saito.io")
                        waitFor("Server unavailable")
                        showPost("Another account reference post")
                        assertPostAbsent("Profile newer post 20", "Another account reference post")
                        ui.onNodeWithText("Settings").performClick()
                        waitFor("Muted and blocked accounts")
                        ui.onNodeWithText("Muted and blocked accounts").performClick()
                        waitFor(author)
                        waitFor("Unmute account")
                        captureNativeScreen(File(context.cacheDir, "personal-controls-evidence/muted-account-offline.png"), author)
                        ui.onNodeWithText("Unmute account").performClick()
                        waitFor("No muted or blocked accounts.")
                        ui.onNodeWithText("Done").performClick()
                        showPost("Profile newer post 20")
                        captureNativeScreen(File(context.cacheDir, "personal-controls-evidence/unmuted-cached-post.png"), "Profile newer post 20")
                        ui.onNodeWithText("Wallet").performClick()
                        assertEquals(address, displayedWalletAddress(ui))
                        ui.onNodeWithText("Done").performClick()
                    }
                    assertTrue(owner.clientKeys.isNotEmpty() && official.clientKeys.isNotEmpty())
                    assertTrue((owner.clientKeys + official.clientKeys).all { it == address })
                    assertTrue(owner.transactions.isEmpty() && official.transactions.isEmpty())
                    owner.assertHealthy()
                    official.assertHealthy()
                }
            }
        }
    }

    private fun readReference(name: String): JsonObject =
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject
        }

    private fun waitFor(text: String) {
        ui.waitUntil(15_000) {
            ui.onAllNodes(hasText(text)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    private fun waitForEnabled(text: String) {
        ui.waitUntil(15_000) {
            ui.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    private fun showPost(text: String) {
        ui.waitUntil(15_000) { ui.onAllNodes(hasScrollAction()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        ui.onNode(hasScrollAction()).performScrollToNode(hasText(text))
        ui.onNodeWithText(text).assertIsDisplayed()
    }

    private fun assertPostAbsent(text: String, returnTo: String) {
        val list = ui.onNode(hasScrollAction())
        val failure = assertThrows(AssertionError::class.java) { list.performScrollToNode(hasText(text)) }
        assertTrue("The complete list must be searched", failure.message?.contains("No node found that matches") == true)
        list.performScrollToNode(hasText(returnTo))
        ui.onNodeWithText(returnTo).assertIsDisplayed()
    }
}
