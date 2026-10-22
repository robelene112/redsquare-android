package nl.davidengelen.redsquare

import android.content.Intent
import android.view.inspector.WindowInspector
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import nl.davidengelen.redsquare.chat.ChatHistoryStore
import nl.davidengelen.redsquare.saito.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Actual local contacts and deliberate key entry, without a directory or public write. */
class ChatEntryPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun knownContactsAndValidatedKeyEntryKeepExactIdentitiesAccessibleWithLargeTextAndKeyboard() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val contact = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixture only.
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2").withChatContact(contact.publicKey)
        val pasted = SaitoWallet.fromPrivateKey("0".repeat(63) + "3").publicKey
        val wallets = AndroidWalletStore(context)
        val originalWallet = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context)
        val originalBackend = backends.load()
        val prefixes = SaitoBackend.entries.flatMap { backend -> listOf(
            "chat.${backend.id}.${wallet.publicKey}.", "chatwork.${backend.id}.${wallet.publicKey}.",
            "chatreactions.${backend.id}.${wallet.publicKey}.", "groupchat.${backend.id}.${wallet.publicKey}.")
        } + "personal.${wallet.publicKey}."
        val retained = ChatFixtureState(context, prefixes, wallet.publicKey)
        val body = "My existing private conversation stays available."
        val original = PrivateChat.create(contact, wallet.publicKey, body, 1700000123400L).envelope()
        val cache = ChatHistoryStore(context, wallet, SaitoBackend.OWNER, contact.publicKey)
        val folder = File(context.cacheDir, "chat-entry-evidence").apply { mkdirs() }
        val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = { "[]" })
        var primary: Throwable? = null
        try {
            retained.prepare()
            wallets.replace(originalWallet, wallet)
            backends.save(SaitoBackend.OWNER)
            check(cache.save(listOf(original)).entries.single().message.text == body)
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { activity ->
                    ui.withPostViewports(activity, folder, "chat-entry") { label, _ ->
                        click("Chat")
                        waitFor("New chat")
                        captureNativeScreen(File(folder, "$label-known-contacts.png"))
                        val known = hasClickAction() and isEnabled() and hasAnyDescendant(hasText(contact.publicKey))
                        ui.waitUntil(15_000) { ui.onNode(known, useUnmergedTree = true).isDisplayed() }
                        ui.onNode(known, useUnmergedTree = true).performClick()
                        waitFor(body)
                        inspectIdentity(contact.publicKey)
                        click("Back")
                        click("New chat")
                        waitFor("New private chat")
                        val beforeInvalid = wallets.loadExisting().exportJson()
                        ui.onNodeWithText("Public key").performClick().performTextReplacement("not-a-public-key")
                        waitForIme()
                        physicalOpen()
                        waitFor("Enter a valid public key.")
                        check(wallets.loadExisting().exportJson() == beforeInvalid)
                        check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                        captureNativeScreen(File(folder, "$label-invalid-key-with-ime.png"))
                        ui.onNodeWithText("Public key").performTextReplacement(wallet.publicKey)
                        physicalOpen()
                        waitFor("Choose another person's public key.")
                        check(wallets.loadExisting().exportJson() == beforeInvalid)
                        ui.onNodeWithText("Public key").performTextReplacement("  $pasted  ")
                        physicalOpen()
                        ui.waitUntil(15_000) { ui.onNode(hasText("Message") and isEnabled()).isDisplayed() }
                        inspectIdentity(pasted)
                        click("Back")
                        waitFor("New chat")
                        check(wallets.loadExisting().exportJson() == wallet.withChatContact(pasted).exportJson())
                        check(cache.load().entries.map { it.envelope } == listOf(original))
                        check(backends.load() == SaitoBackend.OWNER)
                        check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty() && peer.reports.isEmpty())
                        click("Back")
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

    private fun inspectIdentity(key: String) {
        ui.onNodeWithContentDescription("Conversation details").performClick()
        ui.onNodeWithText(key).performScrollTo().assertIsDisplayed()
        click("Done")
    }

    private fun waitFor(text: String) = ui.waitUntil(15_000) { ui.onNodeWithText(text).isDisplayed() }

    private fun click(text: String) {
        val matcher = hasText(text) and hasClickAction() and isEnabled()
        val target = ui.onNode(matcher)
        ui.waitUntil(15_000) { target.isDisplayed() }
        target.assertIsDisplayed().performClick()
    }

    private fun keyboardBottom(): Int {
        var result = 0
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val root = WindowInspector.getGlobalWindowViews().singleOrNull { it.isShown && it.hasWindowFocus() }
            val insets = root?.let { ViewCompat.getRootWindowInsets(it) }
            if (insets?.isVisible(WindowInsetsCompat.Type.ime()) == true)
                result = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        }
        return result
    }

    private fun waitForIme() = ui.waitUntil(10_000) { keyboardBottom() > 0 }

    private fun physicalOpen() {
        waitFor("Open chat")
        waitForIme()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        var target = requireNotNull(device.findObject(By.text("Open chat")))
        while (!target.isClickable) target = requireNotNull(target.parent)
        val bounds = target.visibleBounds
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val minimum = kotlin.math.round(48 * density).toInt()
        check(bounds.width() >= minimum && bounds.height() >= minimum && bounds.top >= 0 &&
            bounds.bottom <= device.displayHeight - keyboardBottom()) { "The complete Open chat target stays above the real keyboard" }
        check(device.click(bounds.centerX(), bounds.centerY()))
    }
}
