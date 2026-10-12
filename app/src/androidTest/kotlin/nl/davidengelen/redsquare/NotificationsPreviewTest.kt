package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class NotificationsPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private enum class Mode { LIKE, REPLY, MENTION, USERNAME, REPOST, QUOTE, MISSING, MISSING_REPOST, MISSING_QUOTE_LIKE, HISTORY, PRUNED, PARTIAL, REJECTED, NULL_HISTORY, READ, READ_SAVE_FAULT, MISSING_REPLY, MISSING_QUOTE, FEED_MENTION, CONTACT, CONTACT_QUOTE, PAGING, CONTACT_CONTINUATION, BACKEND, PARENT_REPLY, CACHE_BUDGET }

    @Test fun anIncomingSignedLikeOpensItsOriginalPostAndVerifiedActorProfile() {
        withNotifications("incoming-like", mode = Mode.LIKE) { actor, _, _ ->
            waitFor("Liked your post")
            ui.onNodeWithText(actor).assertIsDisplayed()
            ui.onNodeWithText("Liked your post").performClick()
            waitFor("Conversation")
            ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
            ui.onNodeWithText("Back").performClick()
            waitFor("Liked your post")
            ui.onNodeWithText(actor).performClick()
            waitFor("Profile")
            ui.onNodeWithText(actor).assertIsDisplayed()
        }
    }

    @Test fun anIncomingSignedReplyOpensItsConversationAndVerifiedActorProfile() {
        withNotifications("incoming-reply", mode = Mode.REPLY) { actor, _, _ ->
            waitFor("Replied to your post")
            captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "notification-evidence/incoming-reply.png"), "Replied to your post")
            ui.onNodeWithText("Replied to your post").assertIsDisplayed().performClick()
            waitFor("Conversation")
            waitFor("Reply notification reference")
            ui.onNodeWithText("Reply notification reference").assertIsDisplayed()
            waitFor("Native Red Square reference post")
            ui.onNode(hasScrollAction()).performScrollToNode(hasText("Native Red Square reference post"))
            ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
            ui.onNodeWithText("Back").performClick()
            waitFor("Replied to your post")
            // The quote sorts first, then this reply, then the like; all have the same signer.
            ui.onAllNodesWithText(actor).assertCountEquals(3)[1].assertIsDisplayed().performClick()
            waitFor("Profile")
            ui.onNodeWithText(actor).assertIsDisplayed()
        }
    }

    @Test fun anIncomingSignedMentionOpensItsPostAndVerifiedActorProfile() {
        mentionJourney(Mode.MENTION, "incoming-mention")
    }

    @Test fun anAvailableFeedMentionAppearsWithoutAnOwnerRecipientIndex() {
        mentionJourney(Mode.FEED_MENTION, "feed-mention")
    }

    private fun mentionJourney(mode: Mode, evidenceName: String) {
        withNotifications(evidenceName, mode) { actor, _, _ ->
            waitFor("Mentioned you")
            ui.onAllNodesWithText("Mentioned you").assertCountEquals(1)
            ui.onAllNodesWithText("Replied to your post").assertCountEquals(0)
            captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "notification-evidence/$evidenceName.png"), "Mentioned you")
            ui.onNodeWithText("Mentioned you").assertIsDisplayed().performClick()
            waitFor("Conversation")
            waitFor("Hello @jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR, mention reference")
            ui.onNodeWithText("Hello @jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR, mention reference").assertIsDisplayed()
            ui.onNodeWithText("Back").performClick()
            waitFor("Mentioned you")
            ui.onAllNodesWithText(actor).assertCountEquals(2)[0].assertIsDisplayed().performClick()
            waitFor("Profile")
            ui.onNodeWithText(actor).assertIsDisplayed()
        }
    }

    @Test fun anIncomingVerifiedUsernameMentionOpensItsPostAndActorProfile() {
        withNotifications("incoming-username-mention", mode = Mode.USERNAME) { actor, _, _ ->
            waitFor("Mentioned you")
            ui.onAllNodesWithText("Mentioned you").assertCountEquals(1)
            captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "notification-evidence/incoming-username-mention.png"), "Mentioned you")
            ui.onNodeWithText("Mentioned you").assertIsDisplayed().performClick()
            waitFor("Conversation")
            waitFor("Hello @NativeCase7@saito, username mention reference")
            ui.onNodeWithText("Hello @NativeCase7@saito, username mention reference").assertIsDisplayed()
            ui.onNodeWithText("Back").performClick()
            waitFor("Mentioned you")
            ui.onAllNodesWithText(actor).assertCountEquals(2)[0].assertIsDisplayed().performClick()
            waitFor("Profile")
            ui.onNodeWithText(actor).assertIsDisplayed()
        }
    }

    @Test fun anIncomingSignedRepostOpensItsOriginalAndVerifiedActorProfile() {
        withNotifications("incoming-repost", mode = Mode.REPOST) { actor, _, _ ->
            waitFor("Reposted your post")
            ui.onAllNodesWithText("Reposted your post").assertCountEquals(1)
            captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "notification-evidence/incoming-repost.png"), "Reposted your post")
            ui.onNodeWithText("Reposted your post").assertIsDisplayed().performClick()
            waitFor("Conversation")
            ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
            ui.onNodeWithText("Back").performClick()
            waitFor("Reposted your post")
            ui.onAllNodesWithText(actor).assertCountEquals(3)[0].assertIsDisplayed().performClick()
            waitFor("Profile")
            ui.onNodeWithText(actor).assertIsDisplayed()
        }
    }

    @Test fun incomingCurrentAndLegacyQuotesOpenTheirCommentaryOriginalAndActor() {
        withNotifications("incoming-quote", mode = Mode.QUOTE) { actor, _, _ ->
            waitFor("Quoted your post")
            ui.onAllNodesWithText("Quoted your post").assertCountEquals(2)
            ui.onAllNodesWithText("Reposted your post").assertCountEquals(0)
            captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "notification-evidence/incoming-quote.png"), "Quoted your post")
            for ((index, text) in listOf("Legacy quote notification reference", "Quote notification reference").withIndex()) {
                ui.onAllNodesWithText("Quoted your post")[index].assertIsDisplayed().performClick()
                waitFor("Conversation")
                waitFor(text)
                ui.onNode(hasScrollAction()).performScrollToNode(hasText(text))
                ui.onNodeWithText(text).assertIsDisplayed()
                waitFor("Native Red Square reference post")
                ui.onNode(hasScrollAction()).performScrollToNode(hasText("Native Red Square reference post"))
                ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
                ui.onNodeWithText("Back").performClick()
                waitFor("Quoted your post")
            }
            ui.onAllNodesWithText(actor).assertCountEquals(3)[0].assertIsDisplayed().performClick()
            waitFor("Profile")
            ui.onNodeWithText(actor).assertIsDisplayed()
        }
    }

    @Test fun missingOriginalActivitySurvivesUnavailableLookupAndExplicitRetry() {
        missingReferenceJourney("missing-original", Mode.MISSING, "Liked a post", "Liked your post", 1)
    }

    @Test fun missingRepostOriginalSurvivesUnavailableLookupAndExplicitRetry() {
        missingReferenceJourney("missing-repost-original", Mode.MISSING_REPOST, "Reposted a post", "Reposted your post", 2)
    }

    @Test fun aLikeOfACurrentQuoteRecoversFromItsRetweetArchiveOnExplicitRetry() {
        missingReferenceJourney("missing-quote-like-original", Mode.MISSING_QUOTE_LIKE,
            "Liked a post", "Liked your post", 1, "Own quote recovered after retry")
    }

    @Test fun availableNotificationsSurviveRecreationWhenTheSelectedServerCannotRefresh() {
        withNotifications("cached-history", Mode.HISTORY) { actor, scenario, responses ->
            waitFor("Liked your post")
            ui.onNodeWithText("Liked your post").assertIsDisplayed().performClick()
            waitFor("Conversation")
            ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
            ui.onNodeWithText("Back").performClick()
            waitFor("Liked your post")
            responses.change()
            scenario.recreate()
            waitFor("Could not load notifications. Check your connection and try Refresh.")
            captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "notification-evidence/cached-history-recreated.png"), "Notifications")
            waitFor("Liked your post")
            ui.onNodeWithText("Liked your post").assertIsDisplayed()
            ui.onNodeWithText("Server: saito.davidengelen.nl").assertIsDisplayed()
            ui.onNodeWithText(actor).assertIsDisplayed().performClick()
            waitFor("Profile")
            ui.onNodeWithText(actor).assertIsDisplayed()
        }
    }

    @Test fun savedActivityRemainsAvailableWhenTheServerNoLongerReturnsIt() {
        withNotifications("pruned-history", Mode.PRUNED) { actor, scenario, responses ->
            waitFor("Liked your post")
            ui.onNodeWithText("Liked your post").assertIsDisplayed().performClick()
            waitFor("Conversation")
            ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
            ui.onNodeWithText("Back").performClick()
            waitFor("Liked your post")
            responses.change()
            scenario.recreate()
            ui.waitUntil(20_000) { responses.ownerReads.get() > 0 && ui.onAllNodes(hasText("Refresh") and isEnabled())
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "notification-evidence/pruned-history-recreated.png"), "Notifications")
            waitFor("Liked your post")
            ui.onAllNodesWithText("Liked your post").assertCountEquals(1)
            ui.onAllNodesWithText("Could not load notifications. Check your connection and try Refresh.").assertCountEquals(0)
            ui.onNodeWithText(actor).assertIsDisplayed().performClick()
            waitFor("Profile")
            ui.onNodeWithText(actor).assertIsDisplayed()
        }
    }

    @Test fun healthyRepliesRemainAvailableWhenTheLikesArchiveFails() {
        withNotifications("partial-history", Mode.PARTIAL) { actor, _, _ ->
            waitFor("Could not load notifications. Check your connection and try Refresh.")
            waitFor("Replied to your post")
            ui.onAllNodesWithText("Replied to your post").assertCountEquals(1)
            captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "notification-evidence/partial-history.png"), "Replied to your post")
            ui.onNodeWithText("Replied to your post").assertIsDisplayed().performClick()
            waitFor("Conversation")
            waitFor("Reply notification reference")
            ui.onNodeWithText("Reply notification reference").assertIsDisplayed()
            ui.onNodeWithText("Back").performClick()
            waitFor("Replied to your post")
            ui.onNodeWithText(actor).assertIsDisplayed().performClick()
            waitFor("Profile")
            ui.onNodeWithText(actor).assertIsDisplayed()
        }
    }

    @Test fun rejectedActivityRemainsExplainedWhileVerifiedDuplicatesAppearOnlyOnce() {
        withNotifications("rejected-history", Mode.REJECTED) { actor, _, _ ->
            waitFor("Some activity could not be verified.")
            waitFor("Replied to your post")
            waitFor("Liked your post")
            ui.onAllNodesWithText("Replied to your post").assertCountEquals(1)
            ui.onAllNodesWithText("Liked your post").assertCountEquals(1)
            ui.onAllNodesWithText("Could not load notifications. Check your connection and try Refresh.").assertCountEquals(0)
            captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "notification-evidence/rejected-history.png"), "Some activity could not be verified.")
            ui.onNodeWithText("Replied to your post").assertIsDisplayed().performClick()
            waitFor("Conversation")
            waitFor("Reply notification reference")
            ui.onNodeWithText("Reply notification reference").assertIsDisplayed()
            ui.onNodeWithText("Back").performClick()
            waitFor("Some activity could not be verified.")
            ui.onAllNodesWithText(actor).assertCountEquals(2)[0].assertIsDisplayed().performClick()
            waitFor("Profile")
            ui.onNodeWithText(actor).assertIsDisplayed()
        }
    }

    @Test fun anOrdinaryRepostWithNullCommentaryIsSavedAndRecoveredWithoutACacheWarning() {
        withNotifications("null-repost-history", Mode.NULL_HISTORY) { actor, scenario, responses ->
            waitFor("Reposted your post")
            ui.waitUntil(20_000) { ui.onAllNodes(hasText("Refresh") and isEnabled())
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onAllNodesWithText("Some notification history could not be read or saved on this phone. Available activity still appears here.")
                .assertCountEquals(0)
            responses.change()
            scenario.recreate()
            waitFor("Could not load notifications. Check your connection and try Refresh.")
            waitFor("Reposted your post")
            ui.onAllNodesWithText("Reposted your post").assertCountEquals(1)
            ui.onAllNodesWithText("Some notification history could not be read or saved on this phone. Available activity still appears here.")
                .assertCountEquals(0)
            captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "notification-evidence/null-repost-history.png"), "Reposted your post")
            ui.onAllNodesWithText(actor).assertCountEquals(2)[0].assertIsDisplayed().performClick()
            waitFor("Profile")
            ui.onNodeWithText(actor).assertIsDisplayed()
        }
    }

    @Test fun readActivityKeepsItsReadStatusWhenRecreatedUnderFailingServerReads() {
        withNotifications("read-history", Mode.READ) { actor, scenario, responses ->
            waitFor("Liked your post")
            waitFor("1 unread")
            ui.onNodeWithText("Mark all as read").assertIsDisplayed().assertIsEnabled().performClick()
            waitFor("No unread activity")
            ui.waitUntil(20_000) { ui.onAllNodes(hasText("Refresh") and isEnabled())
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            responses.change()
            scenario.recreate()
            waitFor("Could not load notifications. Check your connection and try Refresh.")
            waitFor("Liked your post")
            waitFor("No unread activity")
            ui.onAllNodesWithText("1 unread").assertCountEquals(0)
            captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "notification-evidence/read-history-recreated.png"), "No unread activity")
            ui.onNodeWithText(actor).assertIsDisplayed().performClick()
            waitFor("Profile")
            ui.onNodeWithText(actor).assertIsDisplayed()
        }
    }

    @Test fun aFailedReadStatusSaveKeepsActivityUnreadUntilTheUserRetries() {
        withNotifications("read-save-failure", Mode.READ_SAVE_FAULT) { actor, _, control ->
            waitFor("1 unread")
            ui.waitUntil(20_000) { ui.onAllNodes(hasText("Refresh") and isEnabled())
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            control.blockReadSaving()
            ui.onNodeWithText("Mark all as read").assertIsDisplayed().assertIsEnabled().performClick()
            waitFor("Read status could not be read or saved. Available notifications still appear here.")
            ui.onNodeWithText("1 unread").assertIsDisplayed()
            ui.onAllNodesWithText("No unread activity").assertCountEquals(0)
            ui.onNodeWithText("Liked your post").assertIsDisplayed()
            captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "notification-evidence/read-save-failure.png"), "1 unread")
            control.allowReadSaving()
            ui.waitUntil(20_000) { ui.onAllNodes(hasText("Mark all as read") and isEnabled())
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNodeWithText("Mark all as read").performClick()
            waitFor("No unread activity")
            ui.onAllNodesWithText("Read status could not be read or saved. Available notifications still appear here.").assertCountEquals(0)
            captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "notification-evidence/read-save-recovered.png"), "No unread activity")
            ui.onNodeWithText(actor).assertIsDisplayed().performClick()
            waitFor("Profile")
            ui.onNodeWithText(actor).assertIsDisplayed()
        }
    }

    @Test fun anAddressedReplyKeepsItsOwnContentWhenItsParentIsUnavailable() {
        withNotifications("missing-reply-parent", Mode.MISSING_REPLY) { actor, _, _ ->
            waitFor("Replied to a post")
            ui.onAllNodesWithText("Replied to a post").assertCountEquals(1)
            ui.onAllNodesWithText("Replied to your post").assertCountEquals(0)
            ui.onNodeWithText("Replied to a post").assertIsDisplayed().performClick()
            waitFor("Reply with unavailable parent")
            ui.onNodeWithText("Reply with unavailable parent").assertIsDisplayed()
            waitFor("The parent post is not available here. Try Refresh.")
            captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "notification-evidence/missing-reply-parent.png"), "The parent post is not available here. Try Refresh.")
            ui.onNodeWithText("Refresh").assertIsEnabled().performClick()
            waitFor("Original recovered after retry")
            ui.onNode(hasScrollAction()).performScrollToNode(hasText("Original recovered after retry"))
            ui.onNodeWithText("Original recovered after retry").assertIsDisplayed()
            ui.onAllNodesWithText("The parent post is not available here. Try Refresh.").assertCountEquals(0)
            ui.onNodeWithText("Back").performClick()
            waitFor("Replied to a post")
            ui.onAllNodesWithText(actor).assertCountEquals(2)[0].assertIsDisplayed().performClick()
            waitFor("Profile")
            ui.onNodeWithText(actor).assertIsDisplayed()
        }
    }

    @Test fun anAddressedQuoteKeepsItsCommentaryWhenItsOriginalIsUnavailable() {
        withNotifications("missing-quote-original", Mode.MISSING_QUOTE) { actor, _, _ ->
            waitFor("Quoted a post")
            ui.onAllNodesWithText("Quoted a post").assertCountEquals(1)
            ui.onAllNodesWithText("Quoted your post").assertCountEquals(0)
            ui.onNodeWithText("Quoted a post").assertIsDisplayed().performClick()
            waitFor("Quote with unavailable original")
            ui.onNodeWithText("Quote with unavailable original").assertIsDisplayed()
            waitFor("Original unavailable. Commentary is still available.")
            captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "notification-evidence/missing-quote-original.png"), "Original unavailable. Commentary is still available.")
            ui.onNodeWithText("Retry").assertIsDisplayed().performClick()
            waitFor("Original recovered after retry")
            ui.onNode(hasScrollAction()).performScrollToNode(hasText("Original recovered after retry"))
            ui.onNodeWithText("Original recovered after retry").assertIsDisplayed()
            ui.onAllNodesWithText("Original unavailable. Commentary is still available.").assertCountEquals(0)
            ui.onNodeWithText("Back").performClick()
            waitFor("Quoted a post")
            ui.onAllNodesWithText(actor).assertCountEquals(2)[0].assertIsDisplayed().performClick()
            waitFor("Profile")
            ui.onNodeWithText(actor).assertIsDisplayed()
        }
    }

    @Test fun followingAnAccountDiscoversItsAvailablePostsButVisitingDoesNot() {
        followedJourney(Mode.CONTACT, "followed-post", "A known post by another account", actorRow = 1)
    }

    @Test fun followedCurrentQuotesAreDiscoveredInTheirOwnArchiveNamespace() {
        followedJourney(Mode.CONTACT_QUOTE, "followed-quote", "Quote of a known other account", actorRow = 0)
    }

    private fun followedJourney(mode: Mode, evidenceName: String, postText: String, actorRow: Int) {
        withNotifications(evidenceName, mode) { actor, _, responses ->
            fun assertNoFollowedActivity() {
                waitFor("Liked your post")
                ui.waitUntil(15_000) { ui.onAllNodes(hasText("Refresh") and isEnabled())
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                ui.onAllNodesWithText("Posted").assertCountEquals(0)
            }
            assertNoFollowedActivity()
            ui.onNodeWithText(actor).performClick()
            waitFor("Follow")
            ui.onNodeWithText("Back").performClick()
            assertNoFollowedActivity()
            ui.onNodeWithText(actor).performClick()
            waitFor("Follow")
            ui.onNodeWithText("Follow").performClick()
            waitFor("Unfollow")
            responses.makeFollowedPostsAvailable()
            ui.onNodeWithText("Back").performClick()
            waitFor("Posted")
            ui.onAllNodesWithText("Posted").assertCountEquals(1)
            ui.onAllNodesWithText("Quoted your post").assertCountEquals(0)
            assertTrue("A followed account's history must be discovered", responses.followedReads.get() > 0)
            captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "notification-evidence/$evidenceName.png"), "Posted")
            ui.onNodeWithText("Posted").assertIsDisplayed().performClick()
            waitFor("Conversation")
            waitFor(postText)
            ui.onNodeWithText(postText).assertIsDisplayed()
            ui.onNodeWithText("Back").performClick()
            waitFor("Posted")
            ui.onAllNodesWithText(actor).assertCountEquals(2)[actorRow].assertIsDisplayed().performClick()
            waitFor("Unfollow")
            ui.onNodeWithText("Unfollow").performClick()
            waitFor("Follow")
            ui.onNodeWithText("Back").performClick()
            assertNoFollowedActivity()
        }
    }

    @Test fun olderActivityOverlapsBoundariesWithoutDuplicatesAndSurvivesFailedRefresh() {
        val olderActor = InstrumentationRegistry.getInstrumentation().context.assets
            .open("native-notification-reference.json").bufferedReader().use {
                JsonParser.parseReader(it).asJsonObject.getAsJsonObject("olderIncomingLike").get("field2").asString
            }
        withNotifications("older-activity", Mode.PAGING) { _, scenario, responses ->
            waitFor("2 unread")
            waitFor("Load more activity")
            ui.waitUntil(15_000) { ui.onAllNodes(hasText("Load more activity") and isEnabled())
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNodeWithText("Load more activity").assertIsEnabled().performClick()
            waitFor("3 unread")
            ui.waitUntil(15_000) { ui.onAllNodes(hasText("Refresh") and isEnabled())
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNodeWithText("Load more activity").assertIsNotEnabled()
            assertTrue("Older history must overlap the timestamp boundary", responses.olderReads.get() > 0)
            ui.onNode(hasScrollAction()).performScrollToNode(hasText(olderActor))
            ui.onNodeWithText(olderActor).assertIsDisplayed()
            captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "notification-evidence/older-activity.png"), "3 unread")
            ui.onNodeWithText(olderActor).performClick()
            waitFor("Profile")
            ui.onNodeWithText(olderActor).assertIsDisplayed()
            ui.onNodeWithText("Back").performClick()
            waitFor("3 unread")
            responses.change()
            scenario.recreate()
            waitFor("Could not load notifications. Check your connection and try Refresh.")
            ui.waitUntil(15_000) { responses.ownerReads.get() > 0 &&
                ui.onAllNodes(hasText("Refresh") and isEnabled())
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            waitFor("3 unread")
            ui.onNode(hasScrollAction()).performScrollToNode(hasText(olderActor))
            ui.onNodeWithText(olderActor).assertIsDisplayed()
            captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "notification-evidence/older-activity-recreated.png"), "3 unread")
        }
    }

    @Test fun remainingFollowedAccountsAreDiscoveredAfterTheFirstBatchIsExhausted() {
        val actor = InstrumentationRegistry.getInstrumentation().context.assets
            .open("native-notification-reference.json").bufferedReader().use {
                JsonParser.parseReader(it).asJsonObject.getAsJsonObject("farContactPost").get("field2").asString
            }
        withNotifications("remaining-contacts", Mode.CONTACT_CONTINUATION) { _, _, responses ->
            waitFor("1 unread")
            ui.waitUntil(15_000) { ui.onAllNodes(hasText("Refresh") and isEnabled())
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onAllNodesWithText("Posted").assertCountEquals(0)
            ui.onNodeWithText("Load more activity").assertIsEnabled().performClick()
            waitFor("2 unread")
            waitFor("Posted")
            ui.onAllNodesWithText("Posted").assertCountEquals(1)
            assertTrue("The remaining followed account must be read", responses.followedReads.get() > 0)
            captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "notification-evidence/remaining-contacts.png"), "Posted")
            ui.onNodeWithText("Posted").assertIsDisplayed().performClick()
            waitFor("Conversation")
            waitFor("Activity from the eleventh followed account")
            ui.onNodeWithText("Activity from the eleventh followed account").assertIsDisplayed()
            ui.onNodeWithText("Back").performClick()
            waitFor("Posted")
            ui.onNodeWithText(actor).assertIsDisplayed().performClick()
            waitFor("Profile")
            ui.onNodeWithText(actor).assertIsDisplayed()
        }
    }

    @Test fun muteAndBlockHideActivityAndUnreadCountsThroughOutagesUntilExplicitRemoval() {
        withNotifications("personal-controls", Mode.HISTORY) { actor, scenario, responses ->
            for ((control, confirmation, removal) in listOf(
                Triple("Mute account", "Mute", "Unmute account"),
                Triple("Block account", "Block", "Unblock account"))) {
                waitFor("1 unread")
                waitFor("Liked your post")
                ui.waitUntil(15_000) { ui.onAllNodes(hasText("Refresh") and isEnabled())
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                ui.onNodeWithText(actor).assertIsDisplayed().performClick()
                waitFor("Profile")
                ui.waitUntil(15_000) { ui.onAllNodes(hasText(control) and isEnabled())
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                ui.onNodeWithText(control).performClick()
                waitFor(confirmation)
                ui.onNodeWithText(confirmation, substring = false).performClick()
                waitFor(removal)
                ui.onNodeWithText("Back").performClick()
                waitFor("No unread activity")
                ui.onAllNodesWithText("Liked your post").assertCountEquals(0)
                ui.onAllNodesWithText(actor).assertCountEquals(0)
                ui.waitUntil(15_000) { ui.onAllNodes(hasText("Refresh") and isEnabled())
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                responses.change()
                scenario.recreate()
                waitFor("Could not load notifications. Check your connection and try Refresh.")
                ui.waitUntil(15_000) { ui.onAllNodes(hasText("Refresh") and isEnabled())
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                waitFor("No unread activity")
                ui.onAllNodesWithText("Liked your post").assertCountEquals(0)
                ui.onAllNodesWithText(actor).assertCountEquals(0)
                captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                    "notification-evidence/personal-${confirmation.lowercase()}-recreated.png"), "No unread activity")
                ui.onNodeWithText("Settings").performClick()
                waitFor("Muted and blocked accounts")
                ui.onNodeWithText("Muted and blocked accounts").performClick()
                waitFor(removal)
                ui.onNodeWithText(removal).performClick()
                waitFor("No muted or blocked accounts.")
                ui.onNodeWithText("Done").performClick()
                waitFor("1 unread")
                waitFor("Liked your post")
                ui.onNodeWithText(actor).assertIsDisplayed()
            }
            captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "notification-evidence/personal-controls-restored.png"), "1 unread")
        }
    }

    @Test fun backendSwitchesKeepActivityAndReadMarkersSeparateThroughFailedRefresh() {
        val officialActor = InstrumentationRegistry.getInstrumentation().context.assets
            .open("native-notification-reference.json").bufferedReader().use {
                JsonParser.parseReader(it).asJsonObject.getAsJsonObject("boundaryIncomingLike").get("field2").asString
            }
        withNotifications("backend-isolation", Mode.BACKEND) { ownerActor, _, responses ->
            waitFor("2 unread")
            waitFor("Liked your post")
            ui.waitUntil(15_000) { ui.onAllNodes(hasText("Refresh") and isEnabled())
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNodeWithText("Mark all as read").performClick()
            waitFor("No unread activity")
            switchNotificationBackend("saito.io")
            waitFor("2 unread")
            waitFor(officialActor)
            ui.waitUntil(15_000) { ui.onAllNodes(hasText("Refresh") and isEnabled())
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onAllNodesWithText(ownerActor).assertCountEquals(0)
            captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "notification-evidence/backend-official.png"), "2 unread")
            ui.onNodeWithText("Mark all as read").performClick()
            waitFor("No unread activity")
            switchNotificationBackend("saito.davidengelen.nl", returningToNotifications = true)
            waitFor("No unread activity")
            waitFor(ownerActor)
            ui.waitUntil(15_000) { ui.onAllNodes(hasText("Refresh") and isEnabled())
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onAllNodesWithText(officialActor).assertCountEquals(0)
            responses.change()
            ui.onNodeWithText("Refresh").performClick()
            waitFor("Could not load notifications. Check your connection and try Refresh.")
            ui.waitUntil(15_000) { ui.onAllNodes(hasText("Refresh") and isEnabled())
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNodeWithText("Server: saito.davidengelen.nl").assertIsDisplayed()
            ui.onNodeWithText("No unread activity").assertIsDisplayed()
            ui.onNodeWithText(ownerActor).assertIsDisplayed()
            ui.onAllNodesWithText("Liked your post").assertCountEquals(2)
            ui.onAllNodesWithText(officialActor).assertCountEquals(0)
            captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "notification-evidence/backend-owner-failed.png"), "No unread activity")
        }
    }

    private fun switchNotificationBackend(domain: String, returningToNotifications: Boolean = false) {
        ui.onNodeWithText("Settings").performClick()
        waitFor(domain)
        ui.onNodeWithText(domain).performClick()
        waitFor("Server: $domain")
        if (returningToNotifications) {
            waitFor("No unread activity")
            ui.onNodeWithText("Back").assertIsDisplayed()
            ui.onNodeWithText("Notifications").assertIsDisplayed()
        } else {
            waitFor("Native Red Square reference post")
            ui.waitUntil(15_000) { ui.onAllNodes(hasText("Refresh") and isEnabled())
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNodeWithText("Notifications").assertIsDisplayed().performClick()
        }
    }

    @Test fun repliesToKnownOwnPostsAreFoundWhenTheRecipientIndexBelongsToAnotherAccount() {
        withNotifications("parent-indexed-reply", Mode.PARENT_REPLY) { actor, _, responses ->
            waitFor("Replied to your post")
            waitFor("2 unread")
            ui.onAllNodesWithText("Replied to your post").assertCountEquals(1)
            assertTrue("Notification discovery must read the own-post parent index before Conversation",
                responses.parentIndexedReads.get() > 0)
            captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "notification-evidence/parent-indexed-reply.png"), "Replied to your post")
            ui.onNodeWithText("Replied to your post").assertIsDisplayed().performClick()
            waitFor("Conversation")
            waitFor("Reply indexed by its own-post parent")
            ui.onNode(hasScrollAction()).performScrollToNode(hasText("Reply indexed by its own-post parent"))
            ui.onNodeWithText("Reply indexed by its own-post parent").assertIsDisplayed()
            ui.onNodeWithText("Back").performClick()
            waitFor("Replied to your post")
            ui.onAllNodesWithText(actor).assertCountEquals(2)[0].assertIsDisplayed().performClick()
            waitFor("Profile")
            ui.onNodeWithText(actor).assertIsDisplayed()
        }
    }

    @Test fun retainedHistoryBeyondOneArchivePageKeepsItsUnreadCountThroughRecreation() {
        withNotifications("cache-budget", Mode.CACHE_BUDGET) { _, scenario, _ ->
            waitFor("150 unread")
            ui.waitUntil(15_000) { ui.onAllNodes(hasText("Refresh") and isEnabled())
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "notification-evidence/cache-budget.png"), "150 unread")
            ui.onNodeWithText("Mark all as read").assertIsEnabled().performClick()
            waitFor("No unread activity")
            scenario.recreate()
            waitFor("No unread activity")
            ui.waitUntil(15_000) { ui.onAllNodes(hasText("Refresh") and isEnabled())
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onAllNodesWithText("Liked your post")[0].assertIsDisplayed()
            captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "notification-evidence/cache-budget-recreated.png"), "No unread activity")
        }
    }

    private fun missingReferenceJourney(evidenceName: String, mode: Mode, caption: String, ownCaption: String, actorRows: Int, recoveryText: String = "Original recovered after retry") {
        withNotifications(evidenceName, mode) { actor, _, _ ->
            waitFor(caption)
            ui.onAllNodesWithText(caption).assertCountEquals(1)
            ui.onAllNodesWithText(ownCaption).assertCountEquals(0)
            // A verified non-own post remains ineligible despite signed owner output addressing.
            if (mode == Mode.MISSING_REPOST) ui.onAllNodesWithText("Liked a post").assertCountEquals(0)
            captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "notification-evidence/$evidenceName.png"), caption)
            ui.onNodeWithText(caption).assertIsDisplayed().performClick()
            waitFor("This post is unavailable on this server. You can retry or return to notifications.")
            ui.onNodeWithText("Retry").assertIsDisplayed().performClick()
            waitFor(recoveryText)
            ui.onNode(hasScrollAction()).performScrollToNode(hasText(recoveryText))
            ui.onNodeWithText(recoveryText).assertIsDisplayed()
            if (mode == Mode.MISSING_QUOTE_LIKE) {
                waitFor("Native Red Square reference post")
                ui.onNode(hasScrollAction()).performScrollToNode(hasText("Native Red Square reference post"))
                ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
            }
            ui.onNodeWithText("Back").performClick()
            waitFor(caption)
            ui.onAllNodesWithText(actor).assertCountEquals(actorRows)[0].assertIsDisplayed().performClick()
            waitFor("Profile")
            ui.onNodeWithText(actor).assertIsDisplayed()
        }
    }

    private fun withNotifications(evidenceName: String, mode: Mode, journey: (String, ActivityScenario<MainActivity>, FixtureControl) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val fixture = instrumentation.context.assets.open("native-notification-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val root = fixture.getAsJsonObject("root")
        val owner = "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR"
        val actor = "pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU"
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Published unsafe fixture only.
        val store = AndroidWalletStore(context)
        val prior = store.loadOrCreate()
        val priorJson = prior.exportJson()
        val priorBackend = AndroidBackendStore(context).load()
        val permission = context.getSharedPreferences("notification-permission", 0)
        val priorOffer: Boolean? = if (permission.contains("offered")) permission.getBoolean("offered", false) else null
        val registry = instrumentation.context.assets.open("native-registry-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val missing = mode in listOf(Mode.MISSING, Mode.MISSING_REPOST, Mode.MISSING_QUOTE_LIKE, Mode.MISSING_REPLY, Mode.MISSING_QUOTE)
        val publicBase = if (missing) "quoteoriginal.${SaitoBackend.OWNER.id}.v1" else "namemap.$owner.v1"
        val publicLock = if (missing) nl.davidengelen.redsquare.feed.PublicStringStore.lock
            else nl.davidengelen.redsquare.saito.SaitoLocalState.lock
        val publicFiles = FixtureFiles(context.noBackupFilesDir, publicBase, publicLock)
        val historyFiles = FixtureFiles(context.noBackupFilesDir, "notifications.${SaitoBackend.OWNER.id}.$owner.v1",
            nl.davidengelen.redsquare.feed.PublicStringStore.lock)
        val personalFiles = FixtureFiles(context.noBackupFilesDir, "personal.$owner",
            nl.davidengelen.redsquare.saito.SaitoLocalState.lock)
        val followingFiles = FixtureFiles(context.noBackupFilesDir, "following.$owner.v1",
            nl.davidengelen.redsquare.feed.PublicStringStore.lock)
        val feedFiles = FixtureFiles(context.noBackupFilesDir, "feed.${SaitoBackend.OWNER.id}.v1",
            nl.davidengelen.redsquare.feed.PublicStringStore.lock)
        val readFiles = FixtureFiles(context.noBackupFilesDir, "notificationread.${SaitoBackend.OWNER.id}.$owner.v1",
            nl.davidengelen.redsquare.feed.PublicStringStore.lock)
        val officialHistoryFiles = FixtureFiles(context.noBackupFilesDir, "notifications.${SaitoBackend.OFFICIAL.id}.$owner.v1",
            nl.davidengelen.redsquare.feed.PublicStringStore.lock)
        val officialReadFiles = FixtureFiles(context.noBackupFilesDir, "notificationread.${SaitoBackend.OFFICIAL.id}.$owner.v1",
            nl.davidengelen.redsquare.feed.PublicStringStore.lock)
        val officialFeedFiles = FixtureFiles(context.noBackupFilesDir, "feed.${SaitoBackend.OFFICIAL.id}.v1",
            nl.davidengelen.redsquare.feed.PublicStringStore.lock)
        val conversationFiles = FixtureFiles(context.noBackupFilesDir,
            "conversation.${SaitoBackend.OWNER.id}.${fixture.getAsJsonObject("recoveredOriginal").get("sig").asString}.v1",
            nl.davidengelen.redsquare.feed.PublicStringStore.lock)
        val parentReads = AtomicInteger()
        val responses = FixtureControl(readFiles)
        val likeReads = AtomicInteger()
        val replyReads = AtomicInteger()
        val repostReads = AtomicInteger()
        val targetReads = AtomicInteger()
        val quoteOrdinaryReads = AtomicInteger()
        val quoteTargetReads = AtomicInteger()
        val peer = ControlledFeedPeer("[$root]", quoteArchiveResponse = { criteria ->
            // Initial feed and actor profiles also read this namespace without an owner filter.
            if (mode == Mode.CONTACT_QUOTE && criteria.get("field2")?.asString == actor) {
                require(criteria.get("flagged_ne").asInt == 1 && criteria.get("limit").asInt in 1..100)
                if (responses.followedAvailable.get()) {
                    responses.followedReads.incrementAndGet()
                    "[${fixture.get("knownOtherQuote")}]"
                } else "[]"
            } else if (!criteria.has("field3")) "[]" else {
                require(criteria.get("field3").asString == owner)
                require(criteria.get("flagged_ne").asInt == 1)
                require(criteria.get("limit").asInt in 1..100)
                repostReads.incrementAndGet()
                when (mode) {
                    Mode.NULL_HISTORY -> if (responses.changed.get()) "invalid retweet response" else "[${fixture.get("incomingNullRepost")}]"
                    Mode.REPOST -> "[${fixture.get("incomingRepost")},${fixture.get("parentedQuote")}]"
                    Mode.QUOTE -> "[${fixture.get("incomingQuote")}]"
                    Mode.MISSING_REPOST -> "[${fixture.get("missingOriginalRepost")}]"
                    Mode.MISSING_QUOTE -> "[${fixture.get("missingOriginalQuote")},${fixture.get("knownOtherQuote")}]"
                    else -> "[]"
                }
            }
        }, registryResponse = { criteria ->
            if (mode != Mode.USERNAME) {
                if (criteria.get("request").asString == "registry namecheck") "{\"err\":\"no response\"}" else "{}"
            } else when (criteria.get("request").asString) {
                "registry query" -> if (criteria.getAsJsonArray("keys").any { it.asString == owner })
                    "{\"$owner\":\"NativeCase7@saito\"}" else "{}"
                "registry namecheck" -> if (criteria.get("identifier").asString == "NativeCase7@saito") registry.get("rows").toString() else "[]"
                else -> error("Unexpected Registry fixture request")
            }
        }, archiveResponse = { criteria ->
            if (responses.changed.get()) {
                if (criteria.get("field1")?.asString == "RedSquareLike" && criteria.get("field3")?.asString == owner) {
                    require(criteria.get("flagged_ne").asInt == 1 && criteria.get("limit").asInt in 1..100)
                    responses.ownerReads.incrementAndGet()
                }
                if (mode == Mode.PRUNED) "[]" else "invalid archive response"
            }
            else when (criteria.get("field1")?.asString) {
                "RedSquareLike" -> if (criteria.get("field3")?.asString == owner) {
                    require(criteria.get("flagged_ne").asInt == 1)
                    require(criteria.get("limit").asInt in 1..100)
                    likeReads.incrementAndGet()
                    when (mode) {
                        Mode.CACHE_BUDGET -> "[]"
                        Mode.BACKEND -> "[${fixture.get("incomingLike")},${fixture.get("olderIncomingLike")}]"
                        Mode.PAGING -> {
                            val before = criteria.get("created_earlier_than").asLong
                            if (before > 1700000000051L) com.google.gson.JsonArray().apply {
                                repeat(99) { add(fixture.get("incomingLike")) }
                                add(fixture.get("boundaryIncomingLike"))
                            }.toString() else {
                                require(before == 1700000000051L)
                                responses.olderReads.incrementAndGet()
                                "[${fixture.get("incomingLike")},${fixture.get("boundaryIncomingLike")},${fixture.get("olderIncomingLike")}]"
                            }
                        }
                        Mode.PARTIAL -> "invalid likes response"
                        Mode.REJECTED -> "[{\"tx\":\"not-a-signed-envelope\"},${fixture.get("incomingLike")},${fixture.get("incomingLike")}]"
                        Mode.MISSING -> "[${fixture.get("missingOriginalLike")},${fixture.get("knownOtherLike")}]"
                        Mode.MISSING_REPOST -> "[${fixture.get("incomingLike")},${fixture.get("knownOtherLike")}]"
                        Mode.MISSING_QUOTE_LIKE -> "[${fixture.get("incomingQuoteLike")},${fixture.get("knownOtherLike")}]"
                        else -> "[${fixture.get("incomingLike")}]"
                    }
                } else "[]"
                "RedSquareRetweet" -> {
                    if (mode == Mode.MISSING_QUOTE_LIKE && criteria.get("sig")?.asString == fixture.getAsJsonObject("ownCurrentQuote").get("sig").asString) {
                        require(criteria.get("flagged_ne").asInt == 1 && criteria.get("limit").asInt == 1)
                        if (quoteTargetReads.incrementAndGet() == 1) "[]" else "[${fixture.get("ownCurrentQuote")}]"
                    } else "[]"
                }
                "RedSquare" -> when {
                    criteria.get("sig")?.asString == fixture.getAsJsonObject("ownCurrentQuote").get("sig").asString -> {
                        require(criteria.get("flagged_ne").asInt == 1 && criteria.get("limit").asInt == 1)
                        quoteOrdinaryReads.incrementAndGet()
                        "[]"
                    }
                    criteria.get("sig")?.asString == fixture.getAsJsonObject("recoveredOriginal").get("sig").asString -> {
                        require(criteria.get("flagged_ne").asInt == 1 && criteria.get("limit").asInt == 1)
                        if (targetReads.incrementAndGet() == 1) "[]" else "[${fixture.get("recoveredOriginal")}]"
                    }
                    criteria.get("field5")?.asString == fixture.getAsJsonObject("recoveredOriginal").get("sig").asString -> {
                        if (mode == Mode.MISSING_REPLY) {
                            require(criteria.get("flagged_ne").asInt == 1 && criteria.get("limit").asInt == 100)
                            if (parentReads.incrementAndGet() == 1) "[]" else "[${fixture.get("recoveredOriginal")},${fixture.get("missingOriginalReply")}]"
                        } else "[${fixture.get("recoveredOriginal")}]"
                    }
                    criteria.get("field3")?.asString == owner -> {
                        require(criteria.get("flagged_ne").asInt == 1)
                        require(criteria.get("limit").asInt in 1..100)
                        replyReads.incrementAndGet()
                        when (mode) {
                            Mode.REPLY -> "[${fixture.get("incomingReply")},${fixture.get("parentedQuote")}]"
                            Mode.MISSING_REPLY -> "[${fixture.get("missingOriginalReply")},${fixture.get("knownOtherReply")}]"
                            Mode.PARTIAL -> "[${fixture.get("incomingReply")}]"
                            Mode.REJECTED -> "[${fixture.get("incomingReply")},${fixture.get("incomingReply")}]"
                            Mode.MENTION -> "[${fixture.get("incomingMention")},${fixture.get("addressedPost")}]"
                            Mode.USERNAME -> "[${fixture.get("usernameMention")}]"
                            Mode.QUOTE -> "[${fixture.get("legacyQuote")}]"
                            Mode.LIKE, Mode.REPOST, Mode.MISSING, Mode.MISSING_REPOST, Mode.MISSING_QUOTE_LIKE, Mode.HISTORY, Mode.PRUNED, Mode.NULL_HISTORY, Mode.READ, Mode.READ_SAVE_FAULT, Mode.MISSING_QUOTE, Mode.FEED_MENTION, Mode.CONTACT, Mode.CONTACT_QUOTE, Mode.PAGING, Mode.CONTACT_CONTINUATION, Mode.BACKEND, Mode.PARENT_REPLY, Mode.CACHE_BUDGET -> "[]"
                        }
                    }
                    criteria.get("field5")?.asString == fixture.getAsJsonObject("missingOriginalQuote").get("sig").asString ->
                        "[${fixture.get("missingOriginalQuote")}]"
                    criteria.get("field5")?.asString == fixture.getAsJsonObject("incomingQuote").get("sig").asString ->
                        "[${fixture.get("incomingQuote")}]"
                    criteria.get("field5")?.asString == fixture.getAsJsonObject("legacyQuote").get("sig").asString ->
                        "[${fixture.get("legacyQuote")}]"
                    criteria.get("field5")?.asString == fixture.getAsJsonObject("usernameMention").get("sig").asString ->
                        "[${fixture.get("usernameMention")}]"
                    criteria.get("field5")?.asString == fixture.getAsJsonObject("incomingMention").get("sig").asString ->
                        "[${fixture.get("incomingMention")}]"
                    mode == Mode.PARENT_REPLY && criteria.get("field4")?.asString == root.get("sig").asString -> {
                        responses.parentIndexedReads.incrementAndGet()
                        require(criteria.get("flagged_ne").asInt == 1 && criteria.get("limit").asInt == 100)
                        "[${fixture.get("parentIndexedReply")}]"
                    }
                    criteria.get("field5")?.asString == root.get("sig").asString -> when (mode) {
                        Mode.REPLY -> "[$root,${fixture.get("incomingReply")}]"
                        Mode.PARENT_REPLY -> "[$root,${fixture.get("parentIndexedReply")}]"
                        else -> "[$root]"
                    }
                    mode == Mode.CONTACT_QUOTE && criteria.get("field5")?.asString == fixture.getAsJsonObject("knownOtherQuote").get("sig").asString -> "[]"
                    mode == Mode.CONTACT && criteria.get("field5")?.asString == fixture.getAsJsonObject("knownOtherPost").get("sig").asString ->
                        "[${fixture.get("knownOtherPost")}]"
                    mode == Mode.CONTACT_CONTINUATION && criteria.get("field5")?.asString == fixture.getAsJsonObject("farContactPost").get("sig").asString ->
                        "[${fixture.get("farContactPost")}]"
                    mode == Mode.CONTACT_CONTINUATION && criteria.get("field2")?.asString == fixture.getAsJsonObject("farContactPost").get("field2").asString -> {
                        require(criteria.get("flagged_ne").asInt == 1 && criteria.get("limit").asInt in 1..100)
                        responses.followedReads.incrementAndGet()
                        "[${fixture.get("farContactPost")}]"
                    }
                    mode == Mode.CONTACT && criteria.get("field2")?.asString == actor -> {
                        require(criteria.get("flagged_ne").asInt == 1 && criteria.get("limit").asInt in 1..100)
                        if (responses.followedAvailable.get()) {
                            responses.followedReads.incrementAndGet()
                            "[${fixture.get("knownOtherPost")}]"
                        } else "[]"
                    }
                    criteria.get("field2")?.asString in listOf(null, owner) ->
                        if (mode == Mode.FEED_MENTION && !criteria.has("field2"))
                            "[$root,${fixture.get("incomingMention")},${fixture.get("addressedPost")}]"
                        else if (missing && !criteria.has("field2")) "[$root,${fixture.get("knownOtherPost")}]" else "[$root]"
                    else -> "[]"
                }
                else -> "[]"
            }
        })
        val official = if (mode == Mode.BACKEND) ControlledFeedPeer("[$root]", archiveResponse = { criteria ->
            require(criteria.get("flagged_ne").asInt == 1 && criteria.get("limit").asInt in 1..100)
            when (criteria.get("field1").asString) {
                "RedSquareLike" -> {
                    require(criteria.get("field3").asString == owner)
                    "[${fixture.get("boundaryIncomingLike")},${fixture.get("olderIncomingLike")}]"
                }
                "RedSquare" -> if (criteria.has("field3")) {
                    require(criteria.get("field3").asString == owner); "[]"
                } else "[$root]"
                else -> "[]"
            }
        }, quoteArchiveResponse = { "[]" }) else null
        fun retainQuoteTargetReads() {
            if (mode != Mode.MISSING_QUOTE_LIKE) return
            val evidence = File(context.cacheDir, "notification-evidence/$evidenceName-requests.json")
            check(evidence.parentFile!!.isDirectory || evidence.parentFile!!.mkdirs())
            evidence.writeText("{\"ordinaryTargetReads\":${quoteOrdinaryReads.get()},\"retweetTargetReads\":${quoteTargetReads.get()}}\n")
        }
        var primary: Throwable? = null
        try {
            historyFiles.reset()
            if (mode == Mode.CACHE_BUDGET) {
                nl.davidengelen.redsquare.feed.PublicStringStore(context,
                    "notifications.${SaitoBackend.OWNER.id}.$owner.v1", 300)
                    .save(fixture.getAsJsonArray("cachedLikes").reversed().map { it.asJsonObject.get("tx").asString })
            }
            readFiles.reset()
            feedFiles.reset()
            followingFiles.reset()
            personalFiles.reset()
            if (mode == Mode.BACKEND) {
                officialHistoryFiles.reset(); officialReadFiles.reset(); officialFeedFiles.reset()
            }
            if (mode == Mode.CONTACT_CONTINUATION) {
                nl.davidengelen.redsquare.feed.PublicStringStore(context, "following.$owner.v1", 1000)
                    .save(fixture.getAsJsonArray("continuationContacts").map { it.asString })
            }
            if (mode == Mode.MISSING_REPLY) conversationFiles.reset()
            if (mode == Mode.USERNAME || missing) publicFiles.reset()
            if (prior.publicKey != owner) store.replace(prior, wallet)
            AndroidBackendStore(context).save(SaitoBackend.OWNER)
            check(permission.edit().remove("offered").commit())
            official?.start().use { officialServer ->
              peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("usernameFixtureRegistrar", mode == Mode.USERNAME).apply {
                        officialServer?.let { putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${it.port}/wsopen") }
                    }).use { scenario ->
                    withFailureCapture({ retainQuoteTargetReads(); captureNativeScreen(File(context.cacheDir,
                        "notification-evidence/$evidenceName-first-failure.png")) }) {
                        if (mode == Mode.FEED_MENTION) {
                            waitFor("An addressed post without a mention")
                            ui.onNode(hasScrollAction()).performScrollToNode(hasText("Native Red Square reference post"))
                        }
                        waitFor("Native Red Square reference post")
                        ui.waitUntil(20_000) { ui.onAllNodes(hasText("Refresh") and isEnabled())
                            .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                        peer.assertRequestObserved()
                        if (mode == Mode.USERNAME) waitFor("NativeCase7@saito")
                        if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty())
                            ui.onNodeWithText("Later").performClick()
                        ui.onAllNodesWithText("Not now").assertCountEquals(0)
                        waitFor("Notifications")
                        ui.onNodeWithText("Notifications").assertIsDisplayed().performClick()
                        waitFor("Not now")
                        ui.onNodeWithText("Not now").assertIsDisplayed().performClick()
                        if (mode == Mode.LIKE) {
                            waitFor("Liked your post")
                            captureNativeScreen(File(context.cacheDir, "notification-evidence/incoming-like.png"), "Liked your post")
                        }
                        journey(actor, scenario, responses)
                        retainQuoteTargetReads()
                        peer.assertHealthy()
                        assertTrue("Incoming likes must be read for this wallet", likeReads.get() > 0)
                        if (mode in listOf(Mode.REPLY, Mode.MENTION, Mode.USERNAME, Mode.QUOTE, Mode.PARTIAL, Mode.REJECTED, Mode.MISSING_REPLY)) assertTrue("Incoming posts must be read for this wallet", replyReads.get() > 0)
                        if (mode in listOf(Mode.REPOST, Mode.QUOTE, Mode.MISSING_REPOST, Mode.NULL_HISTORY, Mode.MISSING_QUOTE)) assertTrue("Incoming reposts must be read for this wallet", repostReads.get() > 0)
                        if (mode == Mode.MISSING_QUOTE_LIKE) {
                            assertTrue("A quote target also checks ordinary post history", quoteOrdinaryReads.get() > 0)
                            assertTrue("Retry must look up the current quote's actual namespace", quoteTargetReads.get() >= 2)
                        } else if (mode == Mode.MISSING_REPLY) assertTrue("Refresh must reread the unavailable parent thread", parentReads.get() >= 2)
                        else if (missing) assertTrue("Retry must look up the actual original again", targetReads.get() >= 2)
                        if (mode == Mode.USERNAME) assertTrue("A label hint alone must not be ownership evidence",
                            peer.registryQueries.any { it.get("request").asString == "registry namecheck" &&
                                it.get("identifier").asString == "NativeCase7@saito" })
                        if (mode in listOf(Mode.HISTORY, Mode.PRUNED, Mode.NULL_HISTORY, Mode.READ)) assertTrue("Recreation must receive changed selected-server responses", responses.ownerReads.get() > 0)
                        if (mode == Mode.NULL_HISTORY) assertTrue("Recreated notification stream must reread repost history", repostReads.get() >= 2)
                        assertTrue(peer.clientKeys.isNotEmpty())
                        assertTrue(peer.clientKeys.all { it == owner })
                        assertTrue("Reading notifications must not publish a transaction", peer.transactions.isEmpty())
                        assertTrue(peer.reports.isEmpty())
                        official?.let {
                            it.assertHealthy()
                            assertTrue(it.clientKeys.isNotEmpty() && it.clientKeys.all { key -> key == owner })
                            assertTrue(it.transactions.isEmpty() && it.reports.isEmpty())
                        }
                    }
                }
              }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, {
                val active = store.loadExisting()
                check(active.exportJson() == priorJson || active.exportJson() == wallet.exportJson())
                if (active.exportJson() != priorJson) store.replace(active, prior)
                check(store.loadExisting().exportJson() == priorJson)
            }, { AndroidBackendStore(context).save(priorBackend) }, {
                val edit = permission.edit()
                if (priorOffer == null) edit.remove("offered") else edit.putBoolean("offered", priorOffer)
                check(edit.commit())
                check(if (priorOffer == null) !permission.contains("offered") else permission.getBoolean("offered", !priorOffer) == priorOffer)
            }, {
                publicFiles.restore()
            }, {
                historyFiles.restore()
            }, {
                readFiles.restore()
            }, {
                feedFiles.restore()
            }, {
                followingFiles.restore()
            }, {
                personalFiles.restore()
            }, {
                officialHistoryFiles.restore()
            }, {
                officialReadFiles.restore()
            }, {
                officialFeedFiles.restore()
            }, {
                conversationFiles.restore()
            })
        }
    }

    private class FixtureControl(private val readFiles: FixtureFiles) {
        fun blockReadSaving() = readFiles.blockSaving()
        fun allowReadSaving() = readFiles.allowSaving()
        val followedAvailable = java.util.concurrent.atomic.AtomicBoolean(false)
        val followedReads = AtomicInteger()
        fun makeFollowedPostsAvailable() { followedAvailable.set(true) }
        val changed = java.util.concurrent.atomic.AtomicBoolean(false)
        val ownerReads = AtomicInteger()
        val olderReads = AtomicInteger()
        val parentIndexedReads = AtomicInteger()
        fun change() { changed.set(true) }
    }

    /** Only the named public fixture and its atomic companions, guarded by the owning store. */
    private class FixtureFiles(private val directory: File, private val base: String, private val lock: Any) {
        private var prior: Map<String, ByteArray>? = null
        private var blocked: File? = null
        private fun files() = directory.listFiles().orEmpty()
            .filter { it.name == base || it.name.startsWith("$base.") }

        fun reset() = synchronized(lock) {
            check(prior == null)
            val captured = files().associate { file -> check(file.isFile); file.name to file.readBytes() }
            // Acquire restoration ownership before the first mutation, including partial failures.
            prior = captured
            captured.keys.forEach { check(File(directory, it).delete()) }
        }

        fun blockSaving() = synchronized(lock) {
            check(prior != null && blocked == null && files().isEmpty())
            val obstruction = File(directory, base)
            blocked = obstruction
            check(obstruction.mkdir())
        }

        fun allowSaving() = synchronized(lock) { releaseBlock() }

        private fun releaseBlock() {
            blocked?.let { obstruction ->
                if (obstruction.exists()) check(obstruction.isDirectory &&
                    obstruction.listFiles()?.isEmpty() == true && obstruction.delete())
                blocked = null
            }
        }

        fun restore() = synchronized(lock) {
            releaseBlock()
            prior?.let { saved ->
                files().forEach { check(it.isFile && it.delete()) }
                saved.forEach { (name, bytes) -> File(directory, name).writeBytes(bytes) }
                check(files().map { it.name }.toSet() == saved.keys)
                files().forEach { check(it.readBytes().contentEquals(saved.getValue(it.name))) }
            }
        }
    }

    private fun waitFor(text: String) {
        ui.waitUntil(15_000) { ui.onAllNodesWithText(text)
            .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
    }
}
