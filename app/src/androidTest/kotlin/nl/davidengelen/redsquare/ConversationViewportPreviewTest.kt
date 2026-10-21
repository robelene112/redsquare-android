package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.chat.ChatHistoryStore
import nl.davidengelen.redsquare.saito.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Conversation reading through the real UI and protected cache, without a scroll-to operator. */
class ConversationViewportPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun openingACachedPrivateConversationExposesItsLatestMessage() =
        CachedPrivateConversationFixture(ui).exercise(filtering = false)
}

internal class CachedPrivateConversationFixture(private val ui: ComposeTestRule) {
    fun exercise(filtering: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val contact = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixture only.
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2").withChatContact(contact.publicKey)
        val wallets = AndroidWalletStore(context)
        val originalWallet = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context)
        val originalBackend = backends.load()
        val prefixes = SaitoBackend.entries.flatMap { backend -> listOf(
            "chat.${backend.id}.${wallet.publicKey}.", "chatwork.${backend.id}.${wallet.publicKey}.",
            "chatreactions.${backend.id}.${wallet.publicKey}.", "groupchat.${backend.id}.${wallet.publicKey}.")
        } + "personal.${wallet.publicKey}."
        val retained = ChatFixtureState(context, prefixes, wallet.publicKey)
        val bodies = (0..69).map { "Cached private message ${it.toString().padStart(2, '0')}. Keep my latest message visible." }
        val originals = bodies.mapIndexed { index, body ->
            if (filtering && index < 68) PrivateChat.create(wallet, contact.publicKey, body, 1700000123400L + index).envelope()
            else PrivateChat.create(contact, wallet.publicKey, body, 1700000123400L + index).envelope()
        }
        val prefix = if (filtering) "conversation-filtering" else "conversation-viewport"
        val folder = File(context.cacheDir, "$prefix-evidence").apply { mkdirs() }
        val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = { "[]" })
        var primary: Throwable? = null
        try {
            retained.prepare()
            if (filtering) {
                // A muted identity is available in the actual personal-controls dialog.
                val controls = nl.davidengelen.redsquare.moderation.AndroidPersonalControls(context, wallet.publicKey)
                val original = controls.load()
                val muted = original.copy(muted = original.muted + contact.publicKey)
                check(controls.save(original, muted) == muted)
            }
            wallets.replace(originalWallet, wallet)
            backends.save(SaitoBackend.OWNER)
            val cache = ChatHistoryStore(context, wallet, SaitoBackend.OWNER, contact.publicKey)
            check(cache.save(originals).entries.map { it.message.text } == bodies)
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { activity ->
                    ui.withPostViewports(activity, folder, if (filtering) prefix else "conversation-private-latest",
                        scales = if (filtering) listOf(1.3f) else listOf(1f, 1.3f, 2f)) { label, _ ->
                        val chat = hasText("Chat") and hasClickAction() and isEnabled()
                        ui.waitUntil(15_000) { ui.onNode(chat).isDisplayed() }
                        ui.onNode(chat).performClick()
                        val row = hasClickAction() and isEnabled() and hasAnyDescendant(hasText(contact.publicKey))
                        ui.waitUntil(15_000) { ui.onNode(row, useUnmergedTree = true).isDisplayed() }
                        ui.onNode(row, useUnmergedTree = true).performClick()
                        ui.waitUntil(15_000) {
                            ui.onNodeWithText(bodies.first()).isDisplayed() || ui.onNodeWithText(bodies.last()).isDisplayed()
                        }
                        ui.onNodeWithContentDescription("Private messages").assertIsDisplayed()
                        captureNativeScreen(File(folder, "$label-opened.png"))
                        // Readiness above allows either current layout. This acceptance assertion
                        // independently requires the newest original, without moving the viewport.
                        ui.onNodeWithText(bodies.last()).assertIsDisplayed()
                        if (filtering) inspectFiltering(folder, label, bodies[20])
                        else ui.inspectConversationControls(activity, folder, label, contact.publicKey, group = false)
                        check(cache.load().entries.map { it.envelope } == originals)
                        check(wallets.loadExisting().exportJson() == wallet.exportJson() && backends.load() == SaitoBackend.OWNER)
                        check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty() && peer.reports.isEmpty())
                        ui.onNodeWithText("Back").performClick()
                        ui.waitUntil(15_000) { ui.onNodeWithText("Groups").isDisplayed() }
                        ui.onNodeWithText("Back").performClick()
                        ui.waitUntil(15_000) { ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
                    }
                }
                check(peer.clientKeys.isNotEmpty() && peer.clientKeys.all { it == wallet.publicKey })
                peer.assertHealthy()
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, { retained.restoreFiles() },
                { wallets.replace(wallets.loadOrCreate(), originalWallet) },
                { backends.save(originalBackend) }, { retained.restorePreferences() },
                { check(wallets.loadExisting().exportJson() == originalWallet.exportJson() && backends.load() == originalBackend) })
        }
    }
    private fun inspectFiltering(folder: File, label: String, older: String) {
        val list = ui.onNodeWithContentDescription("Private messages")
        list.performScrollToNode(hasText(older))
        ui.onNodeWithText(older).performScrollTo().assertIsDisplayed()
        val before = ui.onNodeWithText(older).getUnclippedBoundsInRoot().top.value
        captureNativeScreen(File(folder, "$label-older-before-filtering.png"))
        for (block in listOf(true, false)) {
            ui.onNodeWithText("Settings").performClick()
            val personal = hasText("Muted and blocked accounts") and hasClickAction() and isEnabled()
            ui.waitUntil(15_000) { ui.onAllNodes(personal).fetchSemanticsNodes().size == 1 }
            ui.onNode(personal).performScrollTo().assertIsDisplayed().performClick()
            val action = hasText(if (block) "Block account" else "Unblock account") and hasClickAction() and isEnabled()
            ui.waitUntil(15_000) { ui.onAllNodes(action).fetchSemanticsNodes().size == 1 }
            ui.onNode(action).performScrollTo().assertIsDisplayed().performClick()
            if (block) ui.onNodeWithText("Block", substring = false).performClick()
            val changed = hasText(if (block) "Unblock account" else "Block account") and hasClickAction() and isEnabled()
            ui.waitUntil(15_000) { ui.onAllNodes(changed).fetchSemanticsNodes().size == 1 }
            ui.onNode(changed).performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("Done").performClick()
            ui.waitForIdle()
            val stage = if (block) "blocked" else "revealed"
            captureNativeScreen(File(folder, "$label-$stage-without-arrival.png"))
            ui.onNodeWithText("New messages").assertDoesNotExist()
            ui.onNodeWithText(older).assertIsDisplayed()
            val after = ui.onNodeWithText(older).getUnclippedBoundsInRoot().top.value
            check(kotlin.math.abs(after - before) <= 1f) { "Filtering preserves the older reading anchor" }
            File(folder, "$label-$stage-anchor.json").writeText(com.google.gson.JsonObject().apply {
                addProperty("older", older); addProperty("beforeTop", before); addProperty("afterTop", after)
                addProperty("newMessagesVisible", false)
            }.toString())
        }
    }

}
