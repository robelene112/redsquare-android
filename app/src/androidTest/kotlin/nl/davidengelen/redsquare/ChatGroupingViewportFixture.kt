package nl.davidengelen.redsquare

import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.chat.*
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import java.io.ByteArrayOutputStream
import java.io.File

/** Actual signed history, Registry answers, inline photo, menus and system IME at native text scales. */
internal fun ComposeTestRule.inspectGroupedChatViewports(group: Boolean, width: Int) {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val prefix = "chat-grouping-${if (group) "group" else "private"}-$width"
    val folder = File(context.cacheDir, "$prefix-evidence").apply { mkdirs() }
    val name = "NativeLongVerifiedDisplayNameForChat@saito"
    withAvatarJourney("Dark", verifiedName = name) { activity, peer, _, _, sender ->
        val reader = AndroidWalletStore(context).loadExisting()
        val firstBody = "A multiline message.\nThe body remains readable at the system text size."
        val photoText = "Reply with photo"
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(0xffb65b67.toInt()) }
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        val photo = composeChatPhoto("data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP), photoText)
        val firstAt = 1_700_000_000_000L
        // One millisecond past the inclusive five-minute boundary starts a separate sender header.
        val reply = chatReplyPrefix(sender.publicKey, firstAt, firstBody)!! + photo
        val groupKey = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
        val creation = GroupChat.create(sender, groupKey.publicKey, "Grouped viewport room", listOf(reader.publicKey), firstAt - 1)
        val room = GroupChat.readCreation(creation.envelope())
        val originals = if (group) listOf(
            GroupChat.message(sender, room, firstBody, firstAt).envelope(),
            GroupChat.message(sender, room, reply, firstAt + 300_001L).envelope())
        else listOf(PrivateChat.create(sender, reader.publicKey, firstBody, firstAt).envelope(),
            PrivateChat.create(sender, reader.publicKey, reply, firstAt + 300_001L).envelope())
        val privateStore = ChatHistoryStore(context, reader, SaitoBackend.OWNER, sender.publicKey)
        val groupStore = GroupChatStore(context, reader, SaitoBackend.OWNER)
        if (group) { groupStore.receive(creation.envelope()); check(!groupStore.receiveAvailable(room.id, originals)) }
        else privateStore.save(originals)
        withPostViewports(activity, folder, prefix, widthDp = width) { label, _ ->
            if (onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) onNodeWithText("Later").performClick()
            onNode(hasText("Chat") and hasClickAction() and isEnabled()).performClick()
            val row = if (group) {
                waitUntil(15_000) { onNodeWithText("Groups").isDisplayed() }
                onNodeWithText("Groups").performClick()
                hasClickAction() and hasAnyDescendant(hasText("Grouped viewport room"))
            } else hasClickAction() and hasAnyDescendant(hasText(sender.publicKey))
            waitUntil(15_000) { onNode(row, useUnmergedTree = true).isDisplayed() }
            onNode(row, useUnmergedTree = true).performClick()
            val list = onNodeWithContentDescription(if (group) "Group messages" else "Private messages")
            waitUntil(15_000) { list.isDisplayed() }
            list.performScrollToIndex(0)
            val identity = "View identity details for $name"
            waitUntil(15_000) { onAllNodesWithContentDescription(identity).fetchSemanticsNodes().isNotEmpty() }
            onAllNodesWithContentDescription(identity)[0].assertHeightIsAtLeast(48.dp).performClick()
            onNodeWithText("Identity details").assertIsDisplayed()
            onNodeWithText(sender.publicKey).performScrollTo().assertIsDisplayed()
            onNodeWithText("Done").performClick()
            list.performScrollToNode(hasText(firstBody))
            onNodeWithText(firstBody).assertIsDisplayed()
            captureNativeScreen(File(folder, "$label-text.png"))
            list.performScrollToNode(hasText(photoText))
            onNodeWithText(photoText).assertIsDisplayed()
            captureNativeScreen(File(folder, "$label-photo-reply.png"))
            withLastMessageAction("Identity details") {
                performClick()
                onNodeWithText(sender.publicKey).performScrollTo().assertIsDisplayed()
                onNodeWithText("Done").performClick()
            }
            inspectConversationControls(activity, folder, label, if (group) room.id else sender.publicKey, group)
            assertEquals(originals, if (group) requireNotNull(groupStore.load(room.id)).originals
                else privateStore.load().entries.map { it.envelope })
            assertTrue(peer.registryQueries.any { it.get("request").asString == "registry namecheck" })
            onNodeWithText("Back").performClick()
            if (group) { waitUntil(15_000) { onNodeWithText("Grouped viewport room").isDisplayed() }; onNodeWithText("Back").performClick() }
            waitUntil(15_000) { onNodeWithText("New chat").isDisplayed() }
            onNodeWithText("Back").performClick()
            waitUntil(15_000) { onNode(hasText("Home") and isSelected()).isDisplayed() }
        }
    }
}
