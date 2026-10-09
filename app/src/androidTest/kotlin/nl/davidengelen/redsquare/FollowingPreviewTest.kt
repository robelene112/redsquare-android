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
import org.junit.Before

class FollowingPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Before fun preparePublicState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".dev"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        context.noBackupFilesDir.listFiles()?.filter {
            it.name.startsWith("feed.") || it.name.startsWith("conversation.") || it.name.startsWith("profile.") || it.name.startsWith("following.")
        }?.forEach { check(it.delete()) }
    }

    @Test fun followingChoicesSurviveBackendSwitchAndOfflineRestartWithSeparateHistory() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = instrumentation.context.assets.open("native-following-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject
        }
        val paging = instrumentation.context.assets.open("native-profile-paging-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject
        }
        val root = fixture.getAsJsonObject("root")
        val other = fixture.getAsJsonObject("otherRoot")
        val newer = paging.getAsJsonArray("recentPosts")[0].asJsonObject
        val author = root.get("field2").asString
        fun controlledPeer(post: com.google.gson.JsonObject) = ControlledFeedPeer("[$post,$other]") { criteria ->
            check(criteria.get("field1").asString == "RedSquare" && criteria.get("flagged_ne").asInt == 1)
            if (criteria.has("field2")) {
                check(criteria.get("field2").asString == author)
                "[$post]"
            } else "[$post,$other]"
        }
        val owner = controlledPeer(root)
        val official = controlledPeer(newer)
        owner.start().use { ownerServer ->
            official.start().use { officialServer ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${ownerServer.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${officialServer.port}/wsopen")).use { scenario ->
                    try {
                        waitFor("Native Red Square reference post")
                        ui.onNodeWithText(author).performClick()
                        waitFor("Follow")
                        ui.onNodeWithText("Follow").performClick()
                        waitFor("Unfollow")
                        ui.onNodeWithText("Back").performClick()
                        ui.onNodeWithText("Following").performClick()
                        waitFor("Native Red Square reference post")
                        ui.onNodeWithText("Settings").performClick()
                        waitFor("saito.io")
                        ui.onNodeWithText("saito.io").performClick()
                        waitFor("Profile newer post 20")
                        ui.onNodeWithText("Following").performClick()
                        waitFor("Profile newer post 20")
                        ui.onNodeWithText("Another account reference post").assertDoesNotExist()
                        ui.onNodeWithText("Native Red Square reference post").assertDoesNotExist()
                        ui.onNodeWithText(author).performClick()
                        waitFor("Unfollow")
                        ui.onNodeWithText("Back").performClick()
                        waitFor("Refresh")
                        ui.waitUntil(10_000) { ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                        owner.assertHealthy()
                        official.assertHealthy()
                        officialServer.close()
                        scenario.recreate()
                        waitFor("Server: saito.io")
                        waitFor("Profile newer post 20")
                        waitFor("Server unavailable")
                        ui.onNodeWithText("Another account reference post").assertDoesNotExist()
                        ui.onNodeWithText("Native Red Square reference post").assertDoesNotExist()
                        captureFeedEvidence("following-evidence/official-following-after-offline-restart.png")
                        ui.onNodeWithText("Settings").performClick()
                        waitFor("saito.davidengelen.nl")
                        ui.onNodeWithText("saito.davidengelen.nl").performClick()
                        ui.onNodeWithText("Following").performClick()
                        waitFor("Native Red Square reference post")
                        ui.onNodeWithText("Profile newer post 20").assertDoesNotExist()
                        ui.onNodeWithText("Another account reference post").assertDoesNotExist()
                        ui.onNodeWithText(author).performClick()
                        waitFor("Unfollow")
                        owner.assertHealthy()
                    } catch (failure: Throwable) {
                        captureFeedEvidence("following-evidence/backend-following-first-failure.png")
                        throw failure
                    }
                }
            }
        }
    }

    @Test fun failedFollowSaveKeepsTheActionUnconfirmedAndAllowsRetry() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = instrumentation.context.assets.open("native-following-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject
        }
        val root = fixture.getAsJsonObject("root")
        val author = root.get("field2").asString
        val peer = ControlledFeedPeer("[$root]") { criteria ->
            check(criteria.get("field1").asString == "RedSquare" && criteria.get("flagged_ne").asInt == 1)
            if (criteria.has("field2")) check(criteria.get("field2").asString == author)
            "[$root]"
        }
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                waitFor("Native Red Square reference post")
                ui.onNodeWithText("Wallet").performClick()
                val address = displayedWalletAddress(ui)
                ui.onNodeWithText("Done").performClick()
                val blocked = java.io.File(context.noBackupFilesDir, "following.$address.v1")
                check(blocked.mkdir())
                val obstacle = java.io.File(blocked, "public-write-fault").apply { writeText("synthetic test only") }
                try {
                    ui.onNodeWithText(author).performClick()
                    waitFor("Follow")
                    ui.onNodeWithText("Follow").performClick()
                    waitFor(context.getString(R.string.following_error))
                    ui.onNodeWithText("Unfollow").assertDoesNotExist()
                    ui.onNodeWithText("Follow").assertIsEnabled()
                    captureFeedEvidence("following-evidence/unconfirmed-follow-write-failure.png")
                    check(obstacle.delete())
                    check(blocked.delete())
                    ui.onNodeWithText("Follow").performClick()
                    waitFor("Unfollow")
                    ui.onNodeWithText(context.getString(R.string.following_error)).assertDoesNotExist()
                    ui.onNodeWithText("Back").performClick()
                    ui.onNodeWithText("Following").performClick()
                    waitFor("Native Red Square reference post")
                    peer.assertHealthy()
                    captureFeedEvidence("following-evidence/follow-write-retry.png")
                } catch (failure: Throwable) {
                    captureFeedEvidence("following-evidence/follow-write-first-failure.png")
                    throw failure
                } finally {
                    if (obstacle.exists()) check(obstacle.delete())
                    if (blocked.isDirectory) check(blocked.delete())
                }
            }
        }
    }

    @Test fun followingLoadsAuthorHistoryBeyondTheGeneralPageAndRejectsOtherSigners() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = instrumentation.context.assets.open("native-following-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject
        }
        val paging = instrumentation.context.assets.open("native-profile-paging-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject
        }
        val root = fixture.getAsJsonObject("root")
        val other = fixture.getAsJsonObject("otherRoot")
        val author = root.get("field2").asString
        val newer = paging.getAsJsonArray("recentPosts")[0]
        val misleading = other.deepCopy().apply { addProperty("field2", author) }
        val peer = ControlledFeedPeer("[$root,$other]") { criteria ->
            check(criteria.get("field1").asString == "RedSquare" && criteria.get("flagged_ne").asInt == 1)
            when {
                criteria.has("field2") -> {
                    check(criteria.get("field2").asString == author)
                    "[$newer,$misleading]"
                }
                else -> "[$root,$other]"
            }
        }
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                try {
                    waitFor("Native Red Square reference post")
                    ui.onNodeWithText("Profile newer post 20").assertDoesNotExist()
                    ui.onNodeWithText(author).performClick()
                    waitFor("Follow")
                    ui.onNodeWithText("Follow").performClick()
                    waitFor("Unfollow")
                    ui.onNodeWithText("Back").performClick()
                    ui.onNodeWithText("Following").performClick()
                    waitFor("Profile newer post 20")
                    ui.onNodeWithText("Profile newer post 20").assertIsDisplayed()
                    ui.onNodeWithText("Another account reference post").assertDoesNotExist()
                    peer.assertHealthy()
                    captureFeedEvidence("following-evidence/available-author-history.png")
                } catch (failure: Throwable) {
                    captureFeedEvidence("following-evidence/author-history-first-failure.png")
                    throw failure
                }
            }
        }
    }

    @Test fun followingAnAccountRestrictsTheFeedAndUnfollowingRemovesItsPosts() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
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
