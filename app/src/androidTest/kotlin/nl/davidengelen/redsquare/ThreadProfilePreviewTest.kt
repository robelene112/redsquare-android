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

    @Test fun fetchedProfilePostsRemainVisibleAfterCacheWriteFailureAndOfflineRefresh() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = prepareContext()
        val fixture = instrumentation.context.assets.open("native-thread-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject
        }
        val root = fixture.getAsJsonObject("root")
        val author = root.get("field2").asString
        val blockedCache = java.io.File(context.noBackupFilesDir, "profile.owner.v1")
        check(blockedCache.mkdir())
        val obstacle = java.io.File(blockedCache, "prevent-file-replacement").apply { writeText("public test fault") }
        val peer = ControlledFeedPeer("[$root]") { criteria ->
            check(criteria.get("field1").asString == "RedSquare")
            if (criteria.has("field2")) check(criteria.get("field2").asString == author)
            "[$root]"
        }
        val server = peer.start()
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                try {
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText(author)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.onNodeWithText(author).performClick()
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Posts")).fetchSemanticsNodes().isNotEmpty() }
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    scrollProfileTo("Native Red Square reference post")
                    scrollProfileTo(context.getString(R.string.feed_cache_warning))
                    peer.assertHealthy()
                    server.close()
                    ui.onNodeWithText("Refresh").performClick()
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText(context.getString(R.string.profile_error))).fetchSemanticsNodes().isNotEmpty() }
                    scrollProfileTo("Native Red Square reference post")
                    captureProfileObservation("profile-memory-after-write-failure", "Native Red Square reference post")
                } catch (failure: Throwable) {
                    captureProfileFailure(failure, "profile-memory-first-failure")
                    throw failure
                }
            }
        } finally {
            server.close()
            check(obstacle.delete())
            check(blockedCache.delete())
        }
    }

    @Test fun replyAndLikedPostAuthorsOpenProfilesAndBackReturnsToTheOrigin() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = prepareContext()
        val thread = instrumentation.context.assets.open("native-thread-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject
        }
        val likes = instrumentation.context.assets.open("native-profile-like-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject
        }
        val root = thread.getAsJsonObject("root")
        val author = root.get("field2").asString
        val reply = thread.getAsJsonObject("firstReply")
        val replyAuthor = reply.get("field2").asString
        val peer = ControlledFeedPeer("[$root]") { criteria ->
            when {
                criteria.has("field4") -> "[$root]"
                criteria.has("field5") -> {
                    check(criteria.get("field5").asString == root.get("sig").asString)
                    "[$root,$reply,${thread.get("nestedReply")}]"
                }
                criteria.has("field2") -> when (criteria.get("field2").asString) {
                    author -> when (criteria.get("field1").asString) {
                        "RedSquare" -> "[$root,${thread.get("nestedReply")}]"
                        "RedSquareLike" -> "[${likes.get("likedReply")}]"
                        else -> error("Unexpected origin-profile module")
                    }
                    replyAuthor -> {
                        check(criteria.get("field1").asString == "RedSquare")
                        "[$reply]"
                    }
                    else -> error("Unexpected explored identity")
                }
                criteria.has("sig") -> { check(criteria.get("sig").asString == reply.get("sig").asString); "[$reply]" }
                else -> error("Unexpected identity-navigation query")
            }
        }
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                try {
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Native Red Square reference post")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.onNodeWithText("Native Red Square reference post").performClick()
                    waitForProfileHistory()
                    ui.onNode(hasScrollAction()).performScrollToNode(hasText("Independent Saito reply"))
                    ui.onNodeWithText("Independent Saito reply").assertIsDisplayed()
                    ui.onNodeWithText("Anon-${replyAuthor.take(6)}").performClick()
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText(replyAuthor)).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Profile").assertIsDisplayed()
                    ui.onNodeWithText("Back").performClick()
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Conversation")).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Back").performClick()
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText(author)).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText(author).performClick()
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Likes")).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Likes").performClick()
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Independent Saito reply")).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Anon-${replyAuthor.take(6)}").performClick()
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText(replyAuthor)).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Back").performClick()
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText(author)).fetchSemanticsNodes().isNotEmpty() }
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Independent Saito reply")).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Profile").assertIsDisplayed()
                    ui.onNodeWithText("Independent Saito reply").assertIsDisplayed()
                    peer.assertHealthy()
                    captureFeedEvidence("browse-evidence/profile-origin-restored.png")
                } catch (failure: Throwable) {
                    captureFeedEvidence("browse-evidence/identity-navigation-first-failure.png")
                    throw failure
                }
            }
        }
    }

    @Test fun olderProfileRepliesAndLikesRemainReachablePastANewerFullPage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = prepareContext()
        val fixture = instrumentation.context.assets.open("native-profile-paging-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject
        }
        val root = fixture.getAsJsonArray("olderAuthorPage")[1].asJsonObject
        val author = root.get("field2").asString
        val recentPosts = fixture.getAsJsonArray("recentPosts")
        val peer = ControlledFeedPeer("[$root]") { criteria ->
            check(criteria.get("field1").asString == "RedSquareLike" || criteria.get("flagged_ne").asInt == 1)
            when {
                criteria.has("field4") -> "[$root]"
                criteria.has("field2") -> {
                    check(criteria.get("field2").asString == author && criteria.get("limit").asInt == 20)
                    val cursor = criteria.get("created_earlier_than").asLong
                    when (criteria.get("field1").asString) {
                        "RedSquare" -> when {
                            cursor > 1700000001001L -> recentPosts.toString()
                            cursor > 1700000000000L -> fixture.get("olderAuthorPage").toString()
                            else -> "[]"
                        }
                        "RedSquareLike" -> when {
                            cursor > 1700000002001L -> fixture.get("recentLikes").toString()
                            cursor > 1700000000035L -> fixture.get("olderLikes").toString()
                            else -> "[]"
                        }
                        else -> error("Unexpected paged profile module")
                    }
                }
                criteria.has("sig") -> {
                    val target = criteria.get("sig").asString
                    val post = (recentPosts.map { it.asJsonObject } + fixture.getAsJsonObject("likedReply"))
                        .singleOrNull { it.get("sig").asString == target }
                        ?: error("Unexpected paged liked target")
                    "[$post]"
                }
                else -> error("Unexpected paged profile query")
            }
        }
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                try {
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText(author)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.onNodeWithText(author).performClick()
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Profile newer post 20")).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Replies").performClick()
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.waitUntil(15_000) {
                        ui.onAllNodes(hasText("Load older") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
                    }
                    ui.onNodeWithText("Load older").assertIsEnabled().performClick()
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Nested Saito reply")).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Nested Saito reply").assertIsDisplayed()
                    ui.onNodeWithText("Likes").performClick()
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Profile newer post 20")).fetchSemanticsNodes().isNotEmpty() }
                    ui.waitUntil(15_000) {
                        ui.onAllNodes(hasText("Load older") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
                    }
                    ui.onNodeWithText("Load older").assertIsEnabled().performClick()
                    ui.waitUntil(15_000) {
                        runCatching { ui.onNode(hasScrollAction()).performScrollToNode(hasText("Independent Saito reply")) }.isSuccess
                    }
                    ui.onNodeWithText("Independent Saito reply").assertIsDisplayed()
                    peer.assertHealthy()
                    captureFeedEvidence("browse-evidence/older-profile-like.png")
                } catch (failure: Throwable) {
                    captureFeedEvidence("browse-evidence/profile-paging-first-failure.png")
                    throw failure
                }
            }
        }
    }

    @Test fun malformedSignedThreadIsRejectedWithoutHidingAnAuthenticConversation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = prepareContext()
        val fixture = instrumentation.context.assets.open("native-thread-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject
        }
        val root = fixture.getAsJsonObject("root")
        val peer = ControlledFeedPeer("[$root,${fixture.get("malformedThread")}]") { criteria ->
            check(criteria.get("field1").asString == "RedSquare" && criteria.get("flagged_ne").asInt == 1)
            if (criteria.has("field4")) "[$root,${fixture.get("malformedThread")}]"
            else {
                check(criteria.get("field5").asString == root.get("sig").asString)
                "[$root,${fixture.get("firstReply")},${fixture.get("nestedReply")}]"
            }
        }
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                try {
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Native Red Square reference post")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.onNodeWithText("Malformed reference thread").assertDoesNotExist()
                    ui.onNodeWithText("Native Red Square reference post").performClick()
                    waitForProfileHistory()
                    ui.onNode(hasScrollAction()).performScrollToNode(hasText("Independent Saito reply"))
                    ui.onNodeWithText("Independent Saito reply").assertIsDisplayed()
                    ui.onNode(hasScrollAction()).performScrollToNode(hasText("Independent Saito reply"))
                    ui.onNodeWithText("Independent Saito reply").assertIsDisplayed()
                    peer.assertHealthy()
                    captureFeedEvidence("browse-evidence/malformed-thread-rejected.png")
                } catch (failure: Throwable) {
                    captureFeedEvidence("browse-evidence/malformed-thread-first-failure.png")
                    throw failure
                }
            }
        }
    }

    @Test fun profilePostsRepliesAndLikesRemainReadableAfterRestartWithoutTheirServer() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = prepareContext()
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
                    check(criteria.get("field1").asString == "RedSquare" && criteria.get("field4").asString.isEmpty())
                    "[$root]"
                }
                criteria.has("field2") -> {
                    check(criteria.get("field2").asString == author)
                    when (criteria.get("field1").asString) {
                        "RedSquare" -> "[$root,${thread.get("nestedReply")}]"
                        "RedSquareLike" -> "[${likes.get("likedReply")},${likes.get("unavailableLike")}]"
                        else -> error("Unexpected cached-profile archive module")
                    }
                }
                criteria.has("sig") -> when (criteria.get("sig").asString) {
                    thread.getAsJsonObject("firstReply").get("sig").asString -> "[${thread.get("firstReply")}]"
                    "a".repeat(128) -> "[]"
                    else -> error("Unexpected cached-profile target")
                }
                else -> error("Unexpected cached-profile query")
            }
        }
        peer.start().use { server ->
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
            try {
                ActivityScenario.launch<MainActivity>(intent).use {
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText(author)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.onNodeWithText(author).performClick()
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Posts")).fetchSemanticsNodes().isNotEmpty() }
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Native Red Square reference post")).fetchSemanticsNodes().isNotEmpty() }
                    scrollProfileTo("Replies")
                    ui.onNodeWithText("Replies").performClick()
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Nested Saito reply")).fetchSemanticsNodes().isNotEmpty() }
                    scrollProfileTo("Likes")
                    ui.onNodeWithText("Likes").performClick()
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Independent Saito reply")).fetchSemanticsNodes().isNotEmpty() }
                    ui.waitUntil(10_000) { ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    peer.assertHealthy()
                    server.close()
                }
                // Fresh activity, without retained navigation/history ViewModels.
                ActivityScenario.launch<MainActivity>(intent).use {
                    try {
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText(author)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.onNodeWithText(author).performClick()
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Posts")).fetchSemanticsNodes().isNotEmpty() }
                    // The offline header can fill a small screen before this lazy row is composed.
                    // Enabled Profile Refresh also confirms cached history has finished loading.
                    waitForProfileHistory()
                    scrollProfileTo("Native Red Square reference post")
                    captureProfileObservation("cached-profile-posts-after-restart", "Native Red Square reference post")
                    scrollProfileTo("Replies")
                    ui.onNodeWithText("Replies").performClick()
                    waitForProfileHistory()
                    scrollProfileTo("Nested Saito reply")
                    scrollProfileTo("Likes")
                    ui.onNodeWithText("Likes").performClick()
                    waitForProfileHistory()
                    scrollProfileTo("Independent Saito reply")
                    captureProfileObservation("cached-profile-likes-after-restart", "Independent Saito reply")
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("Profile history could not be refreshed. Available posts remain visible.")).fetchSemanticsNodes().isNotEmpty() }
                    scrollProfileTo("Only available history is shown. Older posts and likes may be missing.")
                    captureProfileObservation("cached-profile-history-warning-after-restart", context.getString(R.string.profile_history))
                    } catch (failure: Throwable) {
                        captureProfileFailure(failure, "cached-profile-recovery-first-failure")
                        throw failure
                    }
                }
            } catch (failure: Throwable) {
                captureProfileFailure(failure, "cached-profile-first-failure")
                throw failure
            }
        }
    }

    @Test fun openingAnIdentityShowsItsSignedPostsRepliesAndAvailableLikedPosts() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = prepareContext()
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
        val context = prepareContext()
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
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
            try {
                ActivityScenario.launch<MainActivity>(intent).use {
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Native Red Square reference post")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.onNodeWithText("Native Red Square reference post").performClick()
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasScrollAction()).performScrollToNode(hasText("Independent Saito reply"))
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Independent Saito reply")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.waitUntil(10_000) { ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    peer.assertHealthy()
                    server.close()
                }
                // Fresh activity, without retained navigation/history ViewModels.
                ActivityScenario.launch<MainActivity>(intent).use {
                    try {
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Native Red Square reference post")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
                    ui.onNodeWithText("Native Red Square reference post").performClick()
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Conversation")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasScrollAction()).performScrollToNode(hasText("Independent Saito reply"))
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Independent Saito reply")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.onNodeWithText("Independent Saito reply").assertIsDisplayed()
                    ui.onNode(hasScrollAction()).performScrollToNode(hasText("Nested Saito reply"))
                    ui.onNodeWithText("Nested Saito reply").assertIsDisplayed()
                    ui.onNodeWithText("Only available history is shown. Older replies may be missing.").assertIsDisplayed()
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("Replies could not be refreshed. Available posts remain visible.")).fetchSemanticsNodes().isNotEmpty() }
                    captureFeedEvidence("browse-evidence/cached-conversation-after-restart.png")
                    } catch (failure: Throwable) {
                        captureFeedEvidence("browse-evidence/cached-conversation-recovery-first-failure.png")
                        throw failure
                    }
                }
            } catch (failure: Throwable) {
                captureFeedEvidence("browse-evidence/cached-conversation-first-failure.png")
                throw failure
            }
        } finally { server.close() }
    }

    @Test fun openingAFeedPostShowsAvailableVerifiedConversationReplies() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = prepareContext()
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
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasScrollAction()).performScrollToNode(hasText("Independent Saito reply"))
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

    private fun waitForProfileHistory() {
        ui.waitUntil(15_000) { ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun scrollProfileTo(text: String) {
        // Search the lazy list itself: an offscreen row need not have a composed node yet.
        ui.onNode(hasScrollAction()).performScrollToNode(hasText(text))
        ui.onNodeWithText(text).assertIsDisplayed()
    }

    private fun captureProfileObservation(name: String, expectedText: String? = null) {
        ui.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        captureNativeScreen(java.io.File(context.cacheDir, "browse-evidence/$name.png"), expectedText)
    }

    private fun captureProfileFailure(failure: Throwable, name: String) {
        try { captureProfileObservation(name) }
        catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
    }

    private fun prepareContext(): android.content.Context {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        nl.davidengelen.redsquare.saito.AndroidBackendStore(context)
            .save(nl.davidengelen.redsquare.saito.SaitoBackend.OWNER)
        context.noBackupFilesDir.listFiles()?.filter {
            it.name.startsWith("feed.") || it.name.startsWith("conversation.") || it.name.startsWith("profile.")
        }?.forEach { check(it.delete()) { "Fixture public cache could not be cleared" } }
        return context
    }
}
