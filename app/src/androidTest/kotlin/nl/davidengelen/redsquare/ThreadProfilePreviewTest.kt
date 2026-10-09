package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import org.junit.Rule
import org.junit.Test

class ThreadProfilePreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun openingAnIdentityShowsItsSignedPostsRepliesAndAvailableLikedPosts() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        nl.davidengelen.redsquare.saito.AndroidBackendStore(context)
            .save(nl.davidengelen.redsquare.saito.SaitoBackend.OWNER)
        listOf("feed.owner.v1", "feed.owner.v1.bak", "feed.owner.v1.new").forEach {
            java.io.File(context.noBackupFilesDir, it).delete()
        }
        val thread = instrumentation.context.assets.open("native-thread-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject
        }
        val likes = instrumentation.context.assets.open("native-profile-like-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject
        }
        val root = thread.getAsJsonObject("root")
        val author = root.get("field2").asString
        val peer = ControlledFeedPeer("[$root]") { criteria ->
            when {
                criteria.has("field4") -> {
                    check(criteria.get("field4").asString.isEmpty())
                    check(criteria.get("field1").asString == "RedSquare" && criteria.get("flagged_ne").asInt == 1)
                    "[$root]"
                }
                criteria.has("field2") -> {
                    check(criteria.get("field2").asString == author)
                    check(criteria.get("limit").asInt in 1..100)
                    check(criteria.get("created_earlier_than").asLong > 0)
                    when (criteria.get("field1").asString) {
                        "RedSquare" -> {
                            check(criteria.get("flagged_ne").asInt == 1)
                            "[$root,${thread.get("nestedReply")},${thread.get("firstReply")},${thread.get("tamperedReply")}]"
                        }
                        "RedSquareLike" -> "[${likes.get("likedReply")},${likes.get("unavailableLike")},${likes.get("otherAuthorLike")},${likes.get("alteredLike")}]"
                        else -> error("Unexpected profile archive module")
                    }
                }
                criteria.has("sig") -> {
                    check(criteria.get("field1").asString == "RedSquare" && criteria.get("flagged_ne").asInt == 1)
                    when (criteria.get("sig").asString) {
                        thread.getAsJsonObject("firstReply").get("sig").asString -> "[${thread.get("firstReply")}]"
                        "a".repeat(128) -> "[]"
                        else -> error("Unverified or other-identity like target requested")
                    }
                }
                else -> error("Unexpected profile archive criteria")
            }
        }
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                try {
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText(author)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.onNodeWithText(author).performClick()
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Posts")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Native Red Square reference post")).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
                    ui.onNodeWithText("Independent Saito reply").assertDoesNotExist()
                    ui.onNodeWithText("Nested Saito reply").assertDoesNotExist()
                    ui.onNodeWithText("Replies").performClick()
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Nested Saito reply")).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Nested Saito reply").assertIsDisplayed()
                    ui.onNodeWithText("Independent Saito reply").assertDoesNotExist()
                    ui.onNodeWithText("Native Red Square reference post").assertDoesNotExist()
                    ui.onNodeWithText("Likes").performClick()
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Independent Saito reply")).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Independent Saito reply").assertIsDisplayed()
                    ui.onNodeWithText("Native Red Square reference post").assertDoesNotExist()
                    ui.onNodeWithText("Forged reply").assertDoesNotExist()
                    ui.onNodeWithText("Some liked posts are unavailable from this server.").assertIsDisplayed()
                    peer.assertHealthy()
                    captureFeedEvidence("browse-evidence/profile-available-likes.png")
                } catch (failure: Throwable) {
                    captureFeedEvidence("browse-evidence/profile-first-failure.png")
                    throw failure
                }
            }
        }
    }

    @Test fun availableConversationRemainsReadableAfterRestartWithoutItsServer() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        nl.davidengelen.redsquare.saito.AndroidBackendStore(context)
            .save(nl.davidengelen.redsquare.saito.SaitoBackend.OWNER)
        listOf("feed.owner.v1", "feed.owner.v1.bak", "feed.owner.v1.new").forEach {
            java.io.File(context.noBackupFilesDir, it).delete()
        }
        val fixture = instrumentation.context.assets.open("native-thread-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject
        }
        val root = fixture.getAsJsonObject("root")
        val peer = ControlledFeedPeer("[$root]") { criteria ->
            check(criteria.get("field1").asString == "RedSquare")
            check(criteria.get("flagged_ne").asInt == 1)
            if (criteria.has("field4")) {
                check(criteria.get("field4").asString.isEmpty())
                "[$root]"
            } else {
                check(criteria.get("field5").asString == root.get("sig").asString)
                "[$root,${fixture.get("firstReply")},${fixture.get("nestedReply")}]"
            }
        }
        val server = peer.start()
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { scenario ->
                try {
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Native Red Square reference post")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.onNodeWithText("Native Red Square reference post").performClick()
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Independent Saito reply")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.waitUntil(10_000) { ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    peer.assertHealthy()
                    server.close()
                    scenario.recreate()
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Native Red Square reference post")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.onNodeWithText("Native Red Square reference post").performClick()
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Independent Saito reply")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.onNodeWithText("Independent Saito reply").assertIsDisplayed()
                    ui.onNode(hasScrollAction()).performScrollToNode(hasText("Nested Saito reply"))
                    ui.onNodeWithText("Nested Saito reply").assertIsDisplayed()
                    ui.onNodeWithText("Only available history is shown. Older replies may be missing.").assertIsDisplayed()
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("Replies could not be refreshed. Available posts remain visible.")).fetchSemanticsNodes().isNotEmpty() }
                    captureFeedEvidence("browse-evidence/cached-conversation-after-restart.png")
                } catch (failure: Throwable) {
                    captureFeedEvidence("browse-evidence/cached-conversation-first-failure.png")
                    throw failure
                }
            }
        } finally { server.close() }
    }

    @Test fun openingAFeedPostShowsAvailableVerifiedConversationReplies() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        nl.davidengelen.redsquare.saito.AndroidBackendStore(context)
            .save(nl.davidengelen.redsquare.saito.SaitoBackend.OWNER)
        listOf("feed.owner.v1", "feed.owner.v1.bak", "feed.owner.v1.new").forEach {
            java.io.File(context.noBackupFilesDir, it).delete()
        }
        val fixture = instrumentation.context.assets.open("native-thread-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject
        }
        val root = fixture.getAsJsonObject("root")
        val rootId = root.get("sig").asString
        val peer = ControlledFeedPeer("[$root]") { criteria ->
            check(criteria.get("field1").asString == "RedSquare")
            check(criteria.get("flagged_ne").asInt == 1)
            when {
                criteria.has("field4") -> { check(criteria.get("field4").asString.isEmpty()); "[$root]" }
                criteria.has("field5") -> {
                    check(criteria.get("field5").asString == rootId)
                    "[$root,${fixture.get("firstReply")},${fixture.get("nestedReply")},${fixture.get("tamperedReply")}]"
                }
                criteria.has("sig") -> { check(criteria.get("sig").asString == rootId); "[$root]" }
                else -> error("Unexpected conversation archive criteria")
            }
        }
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                try {
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Native Red Square reference post")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.onNodeWithText("Native Red Square reference post").performClick()
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Independent Saito reply")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.onNodeWithText("Independent Saito reply").assertIsDisplayed()
                    ui.onNode(hasScrollAction()).performScrollToNode(hasText("Nested Saito reply"))
                    ui.onNodeWithText("Nested Saito reply").assertIsDisplayed()
                    ui.onNodeWithText("Forged reply").assertDoesNotExist()
                    ui.onNodeWithText("Back").assertIsDisplayed()
                    peer.assertHealthy()
                    captureFeedEvidence("browse-evidence/verified-conversation.png")
                } catch (failure: Throwable) {
                    captureFeedEvidence("browse-evidence/conversation-first-failure.png")
                    throw failure
                }
            }
        }
    }
}
