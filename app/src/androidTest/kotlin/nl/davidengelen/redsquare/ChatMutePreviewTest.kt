package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class ChatMutePreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun privateConversationMuteKeepsIncomingMessagesAndTheDraftAfterReopening() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val reference = instrumentation.context.assets.open("native-private-chat-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject }
        val contact = reference.get("sender").asString
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2").withChatContact(contact) // Public unsafe fixture only.
        val wallets = AndroidWalletStore(context)
        val original = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context)
        val originalBackend = backends.load()
        val prefixes = SaitoBackend.entries.flatMap { backend -> listOf("chat.${backend.id}.${wallet.publicKey}.",
            "chatwork.${backend.id}.${wallet.publicKey}.", "chatreactions.${backend.id}.${wallet.publicKey}.") } + "personal.${wallet.publicKey}."
        val retained = synchronized(SaitoLocalState.lock) {
            context.noBackupFilesDir.listFiles()!!.filter { file -> prefixes.any(file.name::startsWith) }
                .associate { check(it.isFile); it.name to it.readBytes() }
        }
        val preferences = listOf("notification-permission" to "offered", "wallet-backup-reminders" to "hidden:${wallet.publicKey}")
            .map { (name, key) -> val prefs = context.getSharedPreferences(name, 0)
                Triple(prefs, key, if (prefs.contains(key)) prefs.getBoolean(key, false) else null) }
        val reads = AtomicInteger()
        val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = { criteria ->
            if (criteria.get("field3")?.asString == reference.get("groupId").asString) reads.incrementAndGet()
            "[]"
        })
        val relay = JsonObject().apply {
            addProperty("module", "Relay"); addProperty("request", "chat relay"); add("data", reference.getAsJsonObject("signedInner"))
        }
        val incoming = SaitoWire.applicationRequest(wallet.publicKey, relay.toString().toByteArray(Charsets.UTF_8),
            1, reference.get("timestamp").asLong).drop(5).toByteArray()
        val text = reference.getAsJsonObject("plaintext").get("message").asString
        val draft = "Keep this unsent draft while muting 😀"
        var primary: Throwable? = null
        try {
            synchronized(SaitoLocalState.lock) { retained.keys.forEach { check(File(context.noBackupFilesDir, it).delete()) } }
            preferences.forEach { (prefs, key, _) -> check(prefs.edit().putBoolean(key, true).commit()) }
            wallets.replace(original, wallet); backends.save(SaitoBackend.OWNER)
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { scenario ->
                    withFailureCapture({ capture("private-mute-failure") }) {
                        fun open() {
                            val chat = hasText("Chat") and hasClickAction() and isEnabled()
                            ui.waitUntil(20_000) { displayed(hasText("Groups")) || displayed(chat) }
                            if (!displayed(hasText("Groups"))) ui.onNode(chat).performClick()
                            click(hasText(contact))
                            ui.waitUntil(20_000) { displayed(hasSetTextAction() and isEnabled()) && reads.get() > 0 }
                        }
                        open()
                        ui.onNode(hasSetTextAction() and isEnabled()).performTextReplacement(draft)
                        androidx.test.espresso.Espresso.closeSoftKeyboard()
                        click(hasContentDescription("Mute conversation"))
                        ui.onNodeWithText("Conversation notifications").assertIsDisplayed()
                        ui.onNodeWithText("Messages stay visible.", substring = true).assertIsDisplayed()
                        ui.onNodeWithText("Account", substring = true).assertIsDisplayed()
                        click(hasText("Mute"))
                        ui.waitUntil(20_000) { displayed(hasContentDescription("Unmute conversation")) }
                        peer.sendIncoming(incoming)
                        ui.waitUntil(20_000) { displayed(hasText(text)) && peer.incomingAcknowledgements.any { it[0] == 13.toByte() } }
                        ui.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText))
                            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(draft)))
                        capture("private-muted-incoming-readable")
                        scenario.recreate(); open()
                        ui.onNodeWithContentDescription("Unmute conversation").assertIsDisplayed()
                        ui.onNodeWithText(text).assertIsDisplayed()
                        ui.waitUntil(20_000) { displayed(hasSetTextAction() and isEnabled() and
                            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(draft))) }
                        ui.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText))
                            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(draft)))
                        capture("private-muted-reopened")
                        click(hasContentDescription("Unmute conversation")); click(hasText("Unmute"))
                        ui.waitUntil(20_000) { displayed(hasContentDescription("Mute conversation")) }
                        ui.onNodeWithText(text).assertIsDisplayed()
                        capture("private-unmuted-history-retained")
                        check(wallets.loadExisting().exportJson() == wallet.exportJson() && backends.load() == SaitoBackend.OWNER)
                        check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty() && peer.reports.isEmpty())
                        peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, {
                synchronized(SaitoLocalState.lock) {
                    context.noBackupFilesDir.listFiles()!!.filter { file -> prefixes.any(file.name::startsWith) }
                        .forEach { check(it.isFile && it.delete()) }
                    retained.forEach { (name, bytes) -> File(context.noBackupFilesDir, name).writeBytes(bytes) }
                }
            }, { wallets.replace(wallets.loadOrCreate(), original) }, { backends.save(originalBackend) }, {
                preferences.forEach { (prefs, key, value) -> check(prefs.edit().apply {
                    if (value == null) remove(key) else putBoolean(key, value)
                }.commit()) }
            })
        }
    }

    private fun click(target: SemanticsMatcher) {
        val action = target and hasClickAction() and isEnabled()
        ui.waitUntil(20_000) { displayed(action) }
        ui.onNode(action).performClick()
    }
    private fun displayed(target: SemanticsMatcher) = try { ui.onNode(target).assertIsDisplayed(); true } catch (_: AssertionError) { false }
    private fun capture(name: String) = captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
        "chat-mute-evidence/$name.png"))
}
