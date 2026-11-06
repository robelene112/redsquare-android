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
    val groupKey = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
    withAvatarJourney("Dark", verifiedName = name,
        watchedGroup = if (group) groupKey.publicKey to "Grouped viewport room" else null) { activity, peer, _, _, sender ->
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
            waitUntil(15_000) { list.isDisplayed() && onNodeWithText(photoText).isDisplayed() }
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
            inspectConversationControls(activity, folder, label, if (group) room.id else sender.publicKey, group) {
                val device = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                check(context.resources.configuration.densityDpi == 160)
                val observed = com.google.gson.JsonObject()
                val targets = listOf("Back" to androidx.test.uiautomator.By.pkg(context.packageName).text("Back"),
                    "Mute conversation" to androidx.test.uiautomator.By.pkg(context.packageName).desc("Mute conversation"),
                    "Conversation details" to androidx.test.uiautomator.By.pkg(context.packageName).desc("Conversation details"))
                for ((targetName, selector) in targets) {
                    var target = requireNotNull(device.findObject(selector))
                    while (!target.isClickable) target = requireNotNull(target.parent)
                    val rect = target.visibleBounds
                    observed.add(targetName, com.google.gson.JsonObject().apply {
                        addProperty("bounds", rect.flattenToString()); addProperty("width", rect.width()); addProperty("height", rect.height())
                    })
                }
                File(folder, "$label-native-header-targets.json").writeText(observed.toString())
                for ((targetName, values) in observed.entrySet()) {
                    val rect = values.asJsonObject
                    // The real48dp controls allow one physical pixel of native-bound rounding.
                    assertTrue("Native accessible $targetName retains its complete48dp target at $label: $rect",
                        rect.get("width").asInt >= 47 && rect.get("height").asInt >= 47)
                }
            }
            assertEquals(originals, if (group) requireNotNull(groupStore.load(room.id)).originals
                else privateStore.load().entries.map { it.envelope })
            assertTrue(peer.registryQueries.any { it.get("request").asString == "registry namecheck" })
            onNodeWithText("Back").performClick()
            if (group) { waitUntil(15_000) { onNode(hasText("Create group") and isEnabled()).isDisplayed() }; onNodeWithText("Back").performClick() }
            waitUntil(15_000) { onNodeWithText("New chat").isDisplayed() }
            onNodeWithText("Back").performClick()
            waitUntil(15_000) { onNode(hasText("Home") and isSelected()).isDisplayed() }
        }
    }
}
