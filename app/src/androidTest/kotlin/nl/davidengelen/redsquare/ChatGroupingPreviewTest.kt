package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.chat.ChatHistoryStore
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Real signed private history and individually reachable actions, without a sample chat renderer. */
class ChatGroupingPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun privateMessagesAtTheFiveMinuteBoundaryShareOneSenderHeader() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "chat-grouping-evidence").apply { mkdirs() }
        withAvatarJourney("Dark") { activity, peer, _, _, sender ->
            val reader = AndroidWalletStore(context).loadExisting()
            val first = PrivateChat.create(sender, reader.publicKey, "First individual message", 1_700_000_000_000L)
            val second = PrivateChat.create(sender, reader.publicKey, "Second individual message", 1_700_000_300_000L)
            val originals = listOf(first.envelope(), second.envelope())
            val history = ChatHistoryStore(context, reader, SaitoBackend.OWNER, sender.publicKey)
            history.save(originals)
            ui.withPostViewports(activity, folder, "private-boundary", scales = listOf(1f), widthDp = 412) { label, _ ->
                if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
                ui.onNode(hasText("Chat") and hasClickAction() and isEnabled()).performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText(sender.publicKey).isDisplayed() }
                ui.onNodeWithText(sender.publicKey).performClick()
                ui.waitUntil(15_000) {
                    ui.onNodeWithText("First individual message").isDisplayed() &&
                        ui.onNodeWithText("Second individual message").isDisplayed()
                }
                val identity = "View identity details for Anon-${sender.publicKey.take(6)}"
                assertEquals("Consecutive authenticated messages five minutes apart have one visual sender header", 1,
                    ui.onAllNodesWithContentDescription(identity).fetchSemanticsNodes().size)
                assertEquals("Both individual messages keep their own action menu", 2,
                    ui.onAllNodesWithContentDescription("Message actions").fetchSemanticsNodes().size)
                for (time in listOf(1_700_000_000_000L, 1_700_000_300_000L)) {
                    val labelTime = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.ENGLISH).format(Date(time))
                    ui.onNodeWithText(labelTime).assertIsDisplayed()
                }
                captureNativeScreen(File(folder, "$label-grouped.png"))
                ui.onNodeWithContentDescription(identity).performClick()
                ui.onNodeWithText("Identity details").assertIsDisplayed()
                ui.onNodeWithText(sender.publicKey).assertIsDisplayed()
                ui.onNodeWithText("Done").performClick()
                assertEquals(originals, history.load().entries.map { it.envelope })
                assertTrue(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                ui.onNodeWithText("Back").performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("New chat").isDisplayed() }
                ui.onNodeWithText("Back").performClick()
                ui.waitUntil(15_000) { ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
            }
        }
    }
}
