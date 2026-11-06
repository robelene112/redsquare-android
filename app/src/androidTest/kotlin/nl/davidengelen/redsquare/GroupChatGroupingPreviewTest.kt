package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.chat.GroupChatStore
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Admitted group originals keep their sender distinctions and individual controls. */
class GroupChatGroupingPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun consecutiveGroupMessagesShareAHeaderAndAnotherVerifiedSenderStartsANewOne() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "group-chat-grouping-evidence").apply { mkdirs() }
        val groupKey = SaitoWallet.fromPrivateKey("0".repeat(63) + "3") // Published unsafe fixture only.
        withAvatarJourney("Dark", watchedGroup = groupKey.publicKey to "Signed grouping room") { activity, peer, _, _, sender ->
            val reader = AndroidWalletStore(context).loadExisting()
            val creation = GroupChat.create(sender, groupKey.publicKey, "Signed grouping room",
                listOf(reader.publicKey), 1_700_000_000_000L)
            val group = GroupChat.readCreation(creation.envelope())
            val bodies = listOf("First group message", "Second group message", "Another group sender")
            val originals = listOf(
                GroupChat.message(sender, group, bodies[0], 1_700_000_001_000L).envelope(),
                GroupChat.message(sender, group, bodies[1], 1_700_000_061_000L).envelope(),
                GroupChat.message(reader, group, bodies[2], 1_700_000_121_000L).envelope())
            val store = GroupChatStore(context, reader, SaitoBackend.OWNER)
            store.receive(creation.envelope())
            check(!store.receiveAvailable(group.id, originals))
            ui.withPostViewports(activity, folder, "group-senders", scales = listOf(1f), widthDp = 412) { label, _ ->
                if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
                ui.onNode(hasText("Chat") and hasClickAction() and isEnabled()).performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("Groups").isDisplayed() }
                ui.onNodeWithText("Groups").performClick()
                val groupRow = hasClickAction() and hasAnyDescendant(hasText("Signed grouping room"))
                ui.waitUntil(15_000) { ui.onNode(groupRow, useUnmergedTree = true).isDisplayed() }
                ui.onNode(groupRow, useUnmergedTree = true).performClick()
                ui.waitUntil(15_000) { bodies.all { ui.onNodeWithText(it).isDisplayed() } }
                for (author in listOf(sender.publicKey, reader.publicKey)) {
                    val identity = "View identity details for Anon-${author.take(6)}"
                    assertEquals("Each consecutive verified sender group has one identity header", 1,
                        ui.onAllNodesWithContentDescription(identity).fetchSemanticsNodes().size)
                    ui.onNodeWithContentDescription(identity).performClick()
                    ui.onNodeWithText("Identity details").assertIsDisplayed()
                    ui.onNodeWithText(author).assertIsDisplayed()
                    ui.onNodeWithText("Done").performClick()
                }
                assertEquals("Every signed group message keeps its own actions", 3,
                    ui.onAllNodesWithContentDescription("Message actions").fetchSemanticsNodes().size)
                captureNativeScreen(File(folder, "$label-grouped.png"))
                assertEquals(originals, requireNotNull(store.load(group.id)).originals)
                assertEquals(3, requireNotNull(store.load(group.id)).messages.size)
                assertTrue(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                ui.onNodeWithText("Back").performClick()
                ui.waitUntil(15_000) { ui.onNode(hasText("Create group") and isEnabled()).isDisplayed() }
                ui.onNodeWithText("Back").performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("New chat").isDisplayed() }
                ui.onNodeWithText("Back").performClick()
                ui.waitUntil(15_000) { ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
            }
        }
    }
}
