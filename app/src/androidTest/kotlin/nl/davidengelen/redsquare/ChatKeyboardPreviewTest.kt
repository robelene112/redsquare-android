package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import nl.davidengelen.redsquare.chat.composeChatPhoto
import nl.davidengelen.redsquare.chat.ChatWorkStore
import nl.davidengelen.redsquare.chat.GroupChatStore
import nl.davidengelen.redsquare.saito.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Real IME and physical Send at the agreed Android/controlled-peer boundary. */
class ChatKeyboardPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun privateChatCanSendAMultilineDraftWithTheSystemKeyboardOpen() = exercise(group = false)
    @Test fun groupChatCanSendAMultilineDraftWithTheSystemKeyboardOpen() = exercise(group = true)

    @Test fun privateChatCanSendAPhotoCaptionWithTheSystemKeyboardOpen() = exercise(group = false, photo = true)
    @Test fun groupChatCanSendAPhotoCaptionWithTheSystemKeyboardOpen() = exercise(group = true, photo = true)

    private fun exercise(group: Boolean, photo: Boolean = false) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val kind = (if (group) "group" else "private") + (if (photo) "-photo" else "-text")
        val contact = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixture only.
        val identity = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val groupId = SaitoWallet.fromPrivateKey("0".repeat(63) + "3").publicKey
        val groupName = "Keyboard test group"
        val wallet = if (group) identity.withNamedChatGroup(groupId, groupName)
            else identity.withChatContact(contact.publicKey)
        val wallets = AndroidWalletStore(context)
        val original = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context)
        val originalBackend = backends.load()
        val prefixes = SaitoBackend.entries.flatMap { backend -> listOf("chat.${backend.id}.${wallet.publicKey}.",
            "chatwork.${backend.id}.${wallet.publicKey}.", "chatreactions.${backend.id}.${wallet.publicKey}.",
            "groupchat.${backend.id}.${wallet.publicKey}.") } + "personal.${wallet.publicKey}."
        val retained = ChatFixtureState(context, prefixes, wallet.publicKey)
        val reads = AtomicInteger()
        val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = {
            reads.incrementAndGet(); "[]"
        })
        val draft = "A message with the keyboard open 😀\nSecond line\nThird line"
        val photoSource = if (photo) instrumentation.context.assets.open("native-images-reference.json").bufferedReader()
            .use { com.google.gson.JsonParser.parseReader(it).asJsonObject.getAsJsonObject("images").get("red").asString }
            else null
        val composed = photoSource?.let { composeChatPhoto(it, draft) } ?: draft
        val folder = File(context.cacheDir, "chat-keyboard-evidence")
        var primary: Throwable? = null
        try {
            retained.prepare()
            // Exercise first-use text composition without dismissing its backup reminder.
            // Photo cases keep the established reminder-dismissed recovered-draft setup.
            if (!photo) check(context.getSharedPreferences("wallet-backup-reminders", 0)
                .edit().putBoolean("hidden:${wallet.publicKey}", false).commit())
            wallets.replace(original, wallet)
            backends.save(SaitoBackend.OWNER)
            if (group) GroupChatStore(context, wallet, SaitoBackend.OWNER).receive(
                GroupChat.create(contact, groupId, groupName, listOf(wallet.publicKey), 1700000123400).envelope())
            // Seed a recovered attachment through the protected draft API. Real picker
            // selection/preparation is covered by ChatImagesPreviewTest, not bypassed here.
            if (photo) {
                if (group) GroupChatStore(context, wallet, SaitoBackend.OWNER).saveDraft(groupId, composed)
                else ChatWorkStore(context, wallet, SaitoBackend.OWNER, contact.publicKey).saveDraft(composed)
            }
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { scenario ->
                    withFailureCapture({ captureNativeScreen(File(folder, "$kind-failure.png")) }) {
                        val chat = hasText("Chat") and hasClickAction() and isEnabled()
                        ui.waitUntil(20_000) { displayed(chat) }
                        ui.onNode(chat).performClick()
                        val groups = hasText("Groups") and hasClickAction() and isEnabled()
                        ui.waitUntil(20_000) { displayed(groups) }
                        if (group) ui.onNode(groups).performClick()
                        val row = hasClickAction() and isEnabled() and hasAnyDescendant(
                            hasText(if (group) groupName else contact.publicKey))
                        ui.waitUntil(20_000) { displayed(row, true) }
                        ui.onNode(row, useUnmergedTree = true).performClick()
                        val editor = hasSetTextAction() and isEnabled()
                        ui.waitUntil(20_000) { displayed(editor) && reads.get() > 0 }
                        if (photo) ui.waitUntil(20_000) {
                            try {
                                val bitmap = ui.onNodeWithContentDescription("Image 1 of 1")
                                    .assertIsDisplayed().captureToImage().asAndroidBitmap()
                                bitmap.getPixel(bitmap.width / 2, bitmap.height / 2) == 0xffdc1e28.toInt()
                            } catch (_: AssertionError) { false }
                        }
                        ui.onNode(editor).performTextReplacement(draft)
                        ui.onNode(editor).performClick()
                        val imeBottom = AtomicInteger()
                        val statusTop = AtomicInteger()
                        ui.waitUntil(10_000) {
                            scenario.onActivity { activity ->
                                val insets = ViewCompat.getRootWindowInsets(activity.window.decorView)
                                statusTop.set(insets?.getInsets(WindowInsetsCompat.Type.systemBars())?.top ?: 0)
                                imeBottom.set(if (insets?.isVisible(WindowInsetsCompat.Type.ime()) == true)
                                    insets.getInsets(WindowInsetsCompat.Type.ime()).bottom else 0)
                            }
                            imeBottom.get() > 0
                        }
                        val exactDraft = editor and SemanticsMatcher("Exact multiline draft") {
                            it.config.getOrNull(SemanticsProperties.EditableText)?.text == draft
                        }
                        ui.waitUntil(10_000) { displayed(exactDraft) }
                        ui.onNode(exactDraft).assertIsDisplayed()
                        val send = hasText("Send") and hasClickAction() and isEnabled()
                        ui.waitUntil(10_000) { displayed(send) }
                        ui.onNode(send).assertIsDisplayed()
                        val device = UiDevice.getInstance(instrumentation)
                        val bounds = requireNotNull(device.findObject(androidx.test.uiautomator.By.text("Send"))).visibleBounds
                        check(bounds.width() > 0 && bounds.height() > 0 && bounds.top >= 0 &&
                            bounds.bottom <= device.displayHeight - imeBottom.get()) {
                            "Send must remain above the visible system keyboard: $bounds, keyboard=${imeBottom.get()}"
                        }
                        val privacy = requireNotNull(device.findObject(androidx.test.uiautomator.By.text(
                            context.getString(if (group) R.string.group_privacy else R.string.chat_private)))).visibleBounds
                        check(privacy.height() > 0 && privacy.top >= statusTop.get()) {
                            "Chat privacy indication must remain below the status bar: $privacy"
                        }
                        captureNativeScreen(File(folder, "$kind-keyboard-open.png"))
                        scenario.onActivity { activity ->
                            check(ViewCompat.getRootWindowInsets(activity.window.decorView)
                                ?.isVisible(WindowInsetsCompat.Type.ime()) == true)
                        }
                        check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                        check(device.click(bounds.centerX(), bounds.centerY()))
                        ui.waitUntil(20_000) { peer.transactions.size == 1 && peer.relayedMessages.size == 1 }
                        val sent = postingTransaction(peer.transactions.single())
                        check(SaitoEnvelope.fromSignedJson(peer.relayedMessages.single()) == sent.envelope())
                        val text = if (group) GroupChat.readMessage(requireNotNull(
                            GroupChatStore(context, wallet, SaitoBackend.OWNER).load(groupId)).group, sent.envelope()).text
                            else PrivateChat.read(wallet, sent.envelope(), contact.publicKey).text
                        check(text == composed)
                        ui.waitUntil(20_000) { displayed(editor and SemanticsMatcher("Empty sent draft") {
                            it.config.getOrNull(SemanticsProperties.EditableText)?.text == ""
                        }) }
                        captureNativeScreen(File(folder, "$kind-explicitly-sent.png"))
                        check(wallets.loadExisting().exportJson() == wallet.exportJson() && backends.load() == SaitoBackend.OWNER)
                        check(peer.clientKeys.isNotEmpty() && peer.clientKeys.all { it == wallet.publicKey })
                        check(peer.transactions.size == 1 && peer.relayedMessages.size == 1 && peer.reports.isEmpty())
                        peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, { retained.restoreFiles() },
                { wallets.replace(wallets.loadOrCreate(), original) }, { backends.save(originalBackend) },
                { retained.restorePreferences() })
        }
    }

    private fun displayed(target: SemanticsMatcher, unmerged: Boolean = false) =
        try { ui.onNode(target, useUnmergedTree = unmerged).assertIsDisplayed(); true }
        catch (_: AssertionError) { false }
}
