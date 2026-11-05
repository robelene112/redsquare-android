package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test

/** Independent signed actors, one exact post, and an individual substantive reply. */
class ActivityGroupingPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun severalPeopleLikingOnePostShareOneDirectDestinationWithoutSwallowingReplies() {
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "activity-group-evidence").apply { mkdirs() }
        withActivityInbox("grouped-likes", listOf("incomingLike", "boundaryIncomingLike", "olderIncomingLike", "incomingReply"),
            evidenceFolder = "activity-group-evidence") { _, _ ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
            ui.onNode(hasText("Notifications") and hasClickAction()).performClick()
            ui.waitUntil(15_000) { ui.refreshableContentReady() &&
                ui.onAllNodesWithText("Liked your post").fetchSemanticsNodes().isNotEmpty() }
            captureNativeScreen(File(folder, "one-target-before-assertion.png"))
            ui.onAllNodesWithText("Liked your post").assertCountEquals(1)
            ui.onNodeWithText("2 unread").assertIsDisplayed()
            ui.onNodeWithText("Replied to your post").assertIsDisplayed()
            ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Liked your post"))
            ui.onNode(hasText("Liked your post") and hasClickAction()).performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Conversation").isDisplayed() }
            ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
        }
    }

    @Test fun aNewLikeRaisesItsReadGroupAutomaticallyWhileDuplicateReloadKeepsItRead() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val folder = File(context.cacheDir, "activity-group-evidence").apply { mkdirs() }
        val reference = instrumentation.context.assets.open("native-notification-reference.json").reader().use {
            com.google.gson.JsonParser.parseReader(it).asJsonObject
        }
        val root = reference.getAsJsonObject("root").get("sig").asString
        val owner = reference.getAsJsonObject("incomingLike").get("field3").asString
        val actor = nl.davidengelen.redsquare.saito.SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Public unsafe fixture.
        val body = com.google.gson.JsonObject().apply {
            addProperty("module", "RedSquare"); addProperty("request", "like tweet")
            add("data", com.google.gson.JsonObject().apply { addProperty("signature", root) })
        }
        val time = 1_700_000_010_000L
        val tx = nl.davidengelen.redsquare.saito.SaitoTransaction.create(actor, body, time, listOf(owner))
        val incoming = com.google.gson.JsonObject().apply {
            addProperty("tx", tx.envelope()); addProperty("sig", tx.id)
            addProperty("field1", "RedSquareLike"); addProperty("field2", actor.publicKey)
            addProperty("field3", owner); addProperty("field4", root); addProperty("created_at", time)
        }
        val additional = java.util.concurrent.atomic.AtomicReference<List<com.google.gson.JsonObject>>(emptyList())
        withActivityInbox("read-to-new", listOf("incomingLike", "missingOriginalLike", "incomingReply"),
            evidenceFolder = "activity-group-evidence", additionalEvents = { additional.get() }) { activity, peer ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
            ui.onNode(hasText("Notifications") and hasClickAction()).performClick()
            ui.waitUntil(15_000) { ui.refreshableContentReady() && ui.onNodeWithText("3 unread").isDisplayed() }
            ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Liked your post"))
            ui.onAllNodesWithContentDescription("Activity options for Anon-pncSjs").onLast().performClick()
            ui.onNodeWithText("Mark as read").performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("Liked your post") and hasText("Read") and hasClickAction()).isDisplayed() }
            ui.onNodeWithText("2 unread").assertIsDisplayed()
            ui.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
            additional.set(listOf(incoming))
            // No Refresh or New activity activation: the mounted inbox must discover and apply the signed event.
            ui.waitUntil(15_000) { ui.onNodeWithText("3 unread").isDisplayed() &&
                ui.onNode(hasText("Liked your post") and hasText("Unread") and hasClickAction()).isDisplayed() }
            val like = ui.onNode(hasText("Liked your post") and hasClickAction()).getUnclippedBoundsInRoot()
            val reply = ui.onNode(hasText("Replied to your post") and hasClickAction()).getUnclippedBoundsInRoot()
            org.junit.Assert.assertTrue("New activity automatically raises the same target above the reply", like.top < reply.top)
            ui.onAllNodesWithText("Liked your post").assertCountEquals(1)
            captureNativeScreen(File(folder, "automatically-resurfaced.png"), "3 unread")
            ui.onAllNodesWithContentDescription("Activity options for Anon-pncSjs").onFirst().performClick()
            ui.onNodeWithText("People who liked this post").performClick()
            ui.onNodeWithText("2 likes from 1 person").assertIsDisplayed()
            captureNativeScreen(File(folder, "repeat-actor-is-one-person.png"), "2 likes from 1 person")
            ui.onNodeWithText("Done").performClick()
            ui.onNodeWithText("Mark all as read").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("No unread activity").isDisplayed() }
            ui.refreshVisibleView()
            ui.waitUntil(15_000) { ui.refreshableContentReady() && ui.onNodeWithText("No unread activity").isDisplayed() }
            ui.onAllNodesWithText("Unread").assertCountEquals(0)
            activity.recreate()
            ui.waitUntil(15_000) { ui.refreshableContentReady() && ui.onNodeWithText("No unread activity").isDisplayed() }
            peer.rejectNewConnections = true
            ui.refreshVisibleView()
            ui.waitUntil(15_000) { ui.onNodeWithText("Could not refresh activity.").isDisplayed() }
            ui.onNodeWithText("No unread activity").assertIsDisplayed()
            ui.onAllNodesWithText("Unread").assertCountEquals(0)
            captureNativeScreen(File(folder, "group-read-cached-outage.png"), "Could not refresh activity.")
        }
    }
}
