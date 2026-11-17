package nl.davidengelen.redsquare.prototype

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import androidx.test.uiautomator.UiDevice

/** Bounded checks for the native design study, not evidence of functional Spaces. */
class SpacesPrototypeTest {
    @get:Rule val ui = createAndroidComposeRule<SpacesPrototypeActivity>()
    private fun content(label:String,substring:Boolean=false)=ui.onNode(hasText(label,substring=substring) and hasAnyAncestor(hasTestTag("prototype-content")))
    private fun drawerText(label:String)=ui.onNode(hasText(label) and hasAnyAncestor(hasTestTag("channel-switcher")))
    private fun nav(label:String) {ui.waitUntil(5_000){ui.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()};ui.onNodeWithText(label).performClick()}
    @Before fun verifyActualViewport() {
        val args=InstrumentationRegistry.getArguments();val config=ui.activity.resources.configuration
        assertEquals(checkNotNull(args.getString("expectedWidthDp")).toInt(),config.screenWidthDp)
        assertEquals(checkNotNull(args.getString("expectedDensity")).toInt(),config.densityDpi)
        assertEquals(checkNotNull(args.getString("expectedFont")).toFloat(),config.fontScale,0.01f)
    }
    private fun click(label:String) {
        ui.onNodeWithTag("prototype-content").performScrollToNode(hasText(label))
        content(label).performClick()
    }
    private fun jump(label:String) {
        ui.onNodeWithText("Scenarios").performClick()
        click(label)
        ui.waitForIdle()
    }
    private fun shot(name:String) {
        ui.waitForIdle()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle(1_000)
        val directory=File(ui.activity.getExternalFilesDir(null),"prototype-evidence").apply{mkdirs()}
        check(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(File(directory,"$name.png")))
    }
    @Test fun coreJourney() {
        shot("01-home")
        click("Independent Voices")
        content("general").assertExists()
        shot("02-chat")
        click("3 replies")
        ui.onNodeWithText("Replies").assertExists()
        shot("03-thread")
        jump("articles"); shot("04-articles")
        click("Write an article"); shot("05-editor")
        click("Markdown"); ui.onNodeWithText("Markdown source").assertExists()
        click("Review article"); shot("06-review")
        ui.onNodeWithTag("prototype-content").performScrollToNode(hasText("Publish preview article"));ui.onNodeWithText("Publish preview article").assertIsDisplayed()
        jump("files"); shot("07-files")
        jump("room"); shot("08-prejoin")
        click("Join preview room")
        ui.onNodeWithTag("prototype-content").performScrollToNode(hasText("Unmute"));ui.onNodeWithText("Unmute").assertExists()
        ui.onNodeWithText("Camera on").assertExists()
        shot("09-call")
        click("Browse while in the call")
        ui.onNodeWithContentDescription("Return to call in Independent Voices").assertExists()
        shot("10-call-bar")
        ui.onNodeWithContentDescription("End preview call").performClick()
        jump("members"); shot("11-members")
        jump("settings"); shot("12-settings")
        jump("backup"); shot("13-recovery")
    }
    @Test fun offlineQueueAndRoleRestrictions() {
        ui.onNodeWithText("Scenarios").performClick()
        click("Offline"); click("chat")
        ui.onNode(hasSetTextAction()).performTextInput("Keep my offline draft")
        ui.onNodeWithContentDescription("Send preview message").performClick()
        ui.onNodeWithText("Preview: waiting for connection").assertExists()
        shot("14-offline-queue")
        ui.onNodeWithTag("prototype-content").performScrollToNode(hasContentDescription("Cancel queued message 100"))
        ui.onNodeWithContentDescription("Cancel queued message 100").performClick()
        ui.onNodeWithText("Keep my offline draft").assertExists()
        ui.onNodeWithText("Scenarios").performClick()
        click("Visitor");click("Normal");click("chat")
        ui.onNodeWithTag("prototype-content").performScrollToNode(hasText("Join this Space"));content("Join this Space").assertExists()
        ui.onNodeWithContentDescription("Send preview message").assertIsNotEnabled()
        shot("15-visitor")
        ui.onNodeWithText("Scenarios").performClick()
        click("Access denied");click("chat")
        ui.onNodeWithText("This channel is private").assertExists()
        ui.onNodeWithText("A small corner of the open web. Welcome to Independent Voices.").assertDoesNotExist()
        shot("16-denied")
    }
    @Test fun stateAndThemeGallery() {
        ui.onNodeWithText("Scenarios").performClick();click("light");click("chat");shot("17-light-chat")
        ui.onNodeWithText("Scenarios").performClick();click("Member");click("Loading");click("chat")
        ui.onNodeWithTag("prototype-content").performScrollToNode(hasText("A small corner of the open web. Welcome to Independent Voices."));content("A small corner of the open web. Welcome to Independent Voices.").assertExists()
        shot("18-loading-preserves-content")
        ui.onNodeWithText("Scenarios").performClick();click("Storage full");click("files")
        ui.onNodeWithTag("prototype-content").performScrollToNode(hasText("Upload a file"));content("Upload a file").assertIsNotEnabled();shot("19-storage-full")
        ui.onNodeWithText("Scenarios").performClick();click("Expired");click("files");shot("20-storage-expired")
    }

    @Test fun separateOfflineMessagesSurviveCancellation() {
        ui.onNodeWithText("Scenarios").performClick();click("Offline");click("chat")
        ui.onNode(hasSetTextAction()).performTextInput("First queued text")
        ui.onNodeWithContentDescription("Send preview message").performClick()
        ui.onNode(hasSetTextAction()).performTextInput("Second queued text")
        ui.onNodeWithContentDescription("Send preview message").performClick()
        ui.onNodeWithTag("prototype-content").performScrollToNode(hasText("First queued text"));ui.onNodeWithText("First queued text").assertExists()
        ui.onNodeWithTag("prototype-content").performScrollToNode(hasText("Second queued text"));ui.onNodeWithText("Second queued text").assertExists()
        ui.onNode(hasSetTextAction()).performTextInput("Fresh unsent text")
        ui.onNodeWithTag("prototype-content").performScrollToNode(hasContentDescription("Cancel queued message 100"));ui.onNodeWithContentDescription("Cancel queued message 100").performClick()
        ui.onNode(hasSetTextAction()).assertTextContains("First queued text",substring=true)
        ui.onNode(hasSetTextAction()).assertTextContains("Fresh unsent text",substring=true)
        ui.onNodeWithTag("prototype-content").performScrollToNode(hasText("Second queued text"));ui.onNodeWithText("Second queued text").assertExists()
        shot("21-independent-queue-items")
    }
    @Test fun backRestoresChannelAndSpace() {
        click("Independent Voices")
        ui.onNodeWithContentDescription("Switch Space or channel").performClick()
        drawerText("reading room").performScrollTo().performClick()
        ui.onNodeWithText("Articles").assertExists()
        ui.onNodeWithContentDescription("Back").performClick()
        content("general").assertExists()
        ui.onNodeWithContentDescription("Switch Space or channel").performClick()
        drawerText("Saito Builders").performScrollTo().performClick()
        content("Saito Builders").assertExists()
        ui.onNodeWithContentDescription("Back").performClick()
        content("Independent Voices").assertExists()
        content("general").assertExists()
        shot("22-back-restores-destination")
    }
    @Test fun ownerControlsAndArchivedRoom() {
        ui.onNodeWithText("Scenarios").performClick();click("Admin");click("settings")
        ui.onNodeWithText("Only the owner can change visibility and discoverability.").assertExists()
        ui.onNodeWithContentDescription("Private Space").assertDoesNotExist()
        jump("room");jump("settings")
        ui.onNodeWithTag("prototype-content").performScrollToNode(hasContentDescription("Archive current channel"))
        ui.onNodeWithContentDescription("Archive current channel").performClick()
        jump("room")
        ui.onNodeWithTag("prototype-content").performScrollToNode(hasText("Join preview room"));content("Join preview room").assertIsNotEnabled()
        shot("23-archived-room")
    }

    @Test fun threadActionsKeepDraftDestination() {
        jump("chat");click("3 replies")
        ui.onNode(hasSetTextAction()).performTextInput("A thread draft worth keeping")
        ui.onNodeWithTag("prototype-content").performScrollToNode(hasContentDescription("Message actions for Maya Chen"))
        ui.onNodeWithContentDescription("Message actions for Maya Chen").performClick()
        ui.onNodeWithText("Cancel").performClick()
        ui.onNode(hasSetTextAction()).assertTextContains("A thread draft worth keeping")
        ui.onNodeWithTag("prototype-content").performScrollToNode(hasText("Replies"));content("Replies").assertExists();shot("24-thread-draft")
    }
    @Test fun queuedTextDoesNotSendAfterMembershipLoss() {
        ui.onNodeWithText("Scenarios").performClick();click("Offline");click("chat")
        ui.onNode(hasSetTextAction()).performTextInput("Wait for my permission")
        ui.onNodeWithContentDescription("Send preview message").performClick()
        ui.onNodeWithText("Scenarios").performClick();click("Visitor");click("Normal");click("chat")
        ui.onNodeWithTag("prototype-content").performScrollToNode(hasText("Preview: waiting for connection"))
        ui.onNodeWithText("Preview: waiting for connection").assertExists()
        ui.onNodeWithContentDescription("Send preview message").assertIsNotEnabled();shot("25-revoked-queue")
    }
    @Test fun timeoutAllowsListeningOnly() {
        ui.onNodeWithText("Scenarios").performClick();click("Timeout");click("room")
        click("Join preview room")
        ui.onNodeWithTag("prototype-content").performScrollToNode(hasText("Unmute"))
        ui.onNodeWithText("Unmute").assertIsNotEnabled()
        ui.onNodeWithText("Camera on").assertIsNotEnabled();shot("26-listen-only")
    }
    @Test fun discoveryDoesNotRemoveExistingMembership() {
        nav("Discover");click("Independent Voices")
        ui.onNodeWithText("Continue to join").assertDoesNotExist()
        nav("My Spaces")
        content("Independent Voices").assertExists();shot("27-discovery-preserves-membership")
    }
    @Test fun anotherRoomDoesNotStealActiveCall() {
        jump("room");click("Join preview room");click("Browse while in the call")
        ui.onNodeWithContentDescription("Switch Space or channel").performClick()
        drawerText("Saito Builders").performScrollTo().performClick()
        ui.onNodeWithContentDescription("Switch Space or channel").performClick()
        drawerText("the lounge").performScrollTo().performClick()
        ui.onNodeWithTag("prototype-content").performScrollToNode(hasText("Return to existing call"))
        ui.onNodeWithText("Return to existing call").assertExists();shot("28-another-room")
        ui.onNodeWithText("Return to existing call").performClick()
        content("Saito Builders").assertDoesNotExist()
        ui.onNodeWithTag("prototype-content").performScrollToNode(hasText("You're in the lounge"))
        ui.onNodeWithText("You're in the lounge").assertExists()
    }

    @Test fun announcementRepliesNeverEnableMemberAnnouncements() {
        ui.onNodeWithText("Scenarios").performClick();click("Member");click("chat")
        ui.onNodeWithContentDescription("Switch Space or channel").performClick()
        drawerText("announcements").performScrollTo().performClick()
        click("Community call this Friday")
        ui.onNodeWithText("Community call this Friday. Join us in the lounge at 19:00.").assertExists()
        ui.onNodeWithContentDescription("Send preview message").assertIsNotEnabled()
        ui.onNode(hasSetTextAction()).performTextInput("A member can reply here")
        ui.onNodeWithContentDescription("Send preview message").assertIsEnabled()
        ui.onNodeWithText("Scenarios").performClick();click("Normal")
        nav("My Spaces")
        click("Independent Voices")
        ui.onNodeWithTag("prototype-content").performScrollToNode(hasText("Announcements"));content("Announcements").assertExists()
        ui.onNodeWithContentDescription("Send preview message").assertDoesNotExist()
        shot("29-announcement-restoration")
    }
}
