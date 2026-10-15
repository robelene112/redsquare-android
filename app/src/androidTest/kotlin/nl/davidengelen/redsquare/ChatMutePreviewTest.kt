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

    @Test fun privateConversationMuteKeepsIncomingMessagesAndTheDraftAfterReopening() = exercisePrivateMute()

    @Test fun anUnmutedPrivateMessageShowsAGenericAndroidNotification() = exercisePrivateMute(alert = true)

    private fun exercisePrivateMute(alert: Boolean = false) {
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
        val retained = MutingFixtureState(context, prefixes, wallet.publicKey)
        val notifications = context.getSystemService(android.app.NotificationManager::class.java)
        val existingAlerts = notifications.activeNotifications.map { it.tag to it.id }.toSet()
        fun newAlerts() = notifications.activeNotifications.filter { (it.tag to it.id) !in existingAlerts }
        fun assertNoNewAlerts() {
            val until = android.os.SystemClock.elapsedRealtime() + 2_000
            do {
                check(newAlerts().isEmpty())
                android.os.SystemClock.sleep(50)
            } while (android.os.SystemClock.elapsedRealtime() < until)
        }
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
        fun assertUnchangedIdentityAndNoOutgoingMessages() {
            check(wallets.loadExisting().exportJson() == wallet.exportJson() && backends.load() == SaitoBackend.OWNER)
            check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty() && peer.reports.isEmpty())
            peer.assertHealthy()
        }
        var primary: Throwable? = null
        try {
            retained.prepare()
            wallets.replace(original, wallet); backends.save(SaitoBackend.OWNER)
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { scenario ->
                    withFailureCapture({ capture(if (alert) "private-alert-failure" else "private-mute-failure") }) {
                        fun open() {
                            val chat = hasText("Chat") and hasClickAction() and isEnabled()
                            ui.waitUntil(20_000) { displayed(hasText("Groups")) || displayed(chat) }
                            if (!displayed(hasText("Groups"))) ui.onNode(chat).performClick()
                            click(hasText(contact))
                            ui.waitUntil(20_000) { displayed(hasSetTextAction() and isEnabled()) && reads.get() > 0 }
                        }
                        open()
                        if (alert) {
                            peer.sendIncoming(incoming)
                            ui.waitUntil(20_000) { displayed(hasText(text)) && peer.incomingAcknowledgements.any { it[0] == 13.toByte() } }
                            ui.waitUntil(20_000) { newAlerts().size == 1 }
                            val notification = newAlerts().single().notification
                            check(notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() == "New message")
                            check(notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()?.contains(text) != true)
                            check(notifications.getNotificationChannel(notification.channelId).name.toString() == "Private chats")
                            check(notification.contentIntent != null)
                            val device = androidx.test.uiautomator.UiDevice.getInstance(instrumentation)
                            check(device.openNotification())
                            check(device.wait(androidx.test.uiautomator.Until.hasObject(
                                androidx.test.uiautomator.By.text("New message")), 20_000))
                            capture("private-generic-system-notification")
                            device.pressBack()
                            assertUnchangedIdentityAndNoOutgoingMessages()
                            return@withFailureCapture
                        }
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
                        assertNoNewAlerts()
                        capture("private-muted-incoming-readable")
                        scenario.recreate(); open()
                        ui.onNodeWithContentDescription("Unmute conversation").assertIsDisplayed()
                        ui.onNodeWithText(text).assertIsDisplayed()
                        ui.waitUntil(20_000) { displayed(hasSetTextAction() and isEnabled() and
                            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(draft))) }
                        ui.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText))
                            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(draft)))
                        check(newAlerts().isEmpty())
                        capture("private-muted-reopened")
                        click(hasContentDescription("Unmute conversation")); click(hasText("Unmute"))
                        ui.waitUntil(20_000) { displayed(hasContentDescription("Mute conversation")) }
                        ui.onNodeWithText(text).assertIsDisplayed()
                        assertNoNewAlerts()
                        capture("private-unmuted-history-retained")
                        assertUnchangedIdentityAndNoOutgoingMessages()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, {
                newAlerts().forEach { notifications.cancel(it.tag, it.id) }
            }, {
                retained.restoreFiles()
            }, { wallets.replace(wallets.loadOrCreate(), original) }, { backends.save(originalBackend) }, {
                retained.restorePreferences()
            })
        }
    }

    @Test fun groupConversationMuteKeepsCompatibleMessagesAndTheDraftAfterReopening() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val reference = instrumentation.context.assets.open("native-group-chat-message-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject }
        val id = reference.get("groupId").asString
        val name = "Conversation mute group"
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "4").withNamedChatGroup(id, name) // Public unsafe fixture only.
        val admin = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
        val creation = GroupChat.create(admin, id, name, listOf(wallet.publicKey), 1700000123400)
        val wallets = AndroidWalletStore(context); val original = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context); val originalBackend = backends.load()
        val prefixes = SaitoBackend.entries.map { "groupchat.${it.id}.${wallet.publicKey}." } + "personal.${wallet.publicKey}."
        val retained = MutingFixtureState(context, prefixes, wallet.publicKey)
        val reads = AtomicInteger()
        val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = { criteria ->
            if (criteria.get("field3")?.asString == id) reads.incrementAndGet()
            "[]"
        })
        val detached = JsonParser.parseString(reference.get("envelope").asString).asJsonObject
        val messageBytes = java.util.Base64.getDecoder().decode(detached.get("m").asString)
        val incoming = java.util.Base64.getDecoder().decode(detached.get("t").asString) + messageBytes
        java.nio.ByteBuffer.wrap(incoming).putInt(8, messageBytes.size)
        val text = reference.get("text").asString
        val draft = "Keep this group draft while muting 😀"
        var primary: Throwable? = null
        try {
            retained.prepare()
            wallets.replace(original, wallet); backends.save(SaitoBackend.OWNER)
            nl.davidengelen.redsquare.chat.GroupChatStore(context, wallet, SaitoBackend.OWNER).receive(creation.envelope())
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { scenario ->
                    withFailureCapture({ capture("group-mute-failure") }) {
                        fun open() {
                            val chat = hasText("Chat") and hasClickAction() and isEnabled()
                            ui.waitUntil(20_000) { displayed(hasText("Groups")) || displayed(chat) }
                            if (!displayed(hasText("Groups"))) click(chat)
                            click(hasText("Groups")); click(hasText(name))
                            ui.waitUntil(20_000) { displayed(hasSetTextAction() and isEnabled()) && reads.get() > 0 }
                        }
                        open()
                        ui.onNode(hasSetTextAction() and isEnabled()).performTextReplacement(draft)
                        androidx.test.espresso.Espresso.closeSoftKeyboard()
                        click(hasContentDescription("Mute conversation"))
                        ui.onNodeWithText("Messages stay visible.", substring = true).assertIsDisplayed()
                        click(hasText("Mute"))
                        ui.waitUntil(20_000) { displayed(hasContentDescription("Unmute conversation")) }
                        peer.sendIncoming(incoming)
                        ui.waitUntil(20_000) { displayed(hasText(text)) && peer.incomingAcknowledgements.any { it[0] == 13.toByte() } }
                        ui.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText))
                            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(draft)))
                        capture("group-muted-incoming-readable")
                        scenario.recreate(); open()
                        ui.onNodeWithContentDescription("Unmute conversation").assertIsDisplayed()
                        ui.onNodeWithText(text).assertIsDisplayed()
                        ui.waitUntil(20_000) { displayed(hasSetTextAction() and isEnabled() and
                            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(draft))) }
                        capture("group-muted-reopened")
                        click(hasContentDescription("Unmute conversation")); click(hasText("Unmute"))
                        ui.waitUntil(20_000) { displayed(hasContentDescription("Mute conversation")) }
                        ui.onNodeWithText(text).assertIsDisplayed()
                        capture("group-unmuted-history-retained")
                        check(wallets.loadExisting().exportJson() == wallet.exportJson() && backends.load() == SaitoBackend.OWNER)
                        check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty() && peer.reports.isEmpty())
                        peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, {
                retained.restoreFiles()
            }, { wallets.replace(wallets.loadOrCreate(), original) }, { backends.save(originalBackend) }, {
                retained.restorePreferences()
            })
        }
    }

    /** Preserve only the explicit fixture scopes; cleanup actions remain independent of identity restoration. */
    private class MutingFixtureState(private val context: android.content.Context,
        private val prefixes: List<String>, owner: String) {
        private val files = synchronized(SaitoLocalState.lock) {
            context.noBackupFilesDir.listFiles()!!.filter { file -> prefixes.any(file.name::startsWith) }
                .associate { check(it.isFile); it.name to it.readBytes() }
        }
        private val preferences = listOf("notification-permission" to "offered", "wallet-backup-reminders" to "hidden:$owner")
            .map { (name, key) -> val prefs = context.getSharedPreferences(name, 0)
                Triple(prefs, key, if (prefs.contains(key)) prefs.getBoolean(key, false) else null) }
        fun prepare() {
            synchronized(SaitoLocalState.lock) { files.keys.forEach { check(File(context.noBackupFilesDir, it).delete()) } }
            preferences.forEach { (prefs, key, _) -> check(prefs.edit().putBoolean(key, true).commit()) }
        }
        fun restoreFiles() {
            synchronized(SaitoLocalState.lock) {
                context.noBackupFilesDir.listFiles()!!.filter { file -> prefixes.any(file.name::startsWith) }
                    .forEach { check(it.isFile && it.delete()) }
                files.forEach { (name, bytes) -> File(context.noBackupFilesDir, name).writeBytes(bytes) }
            }
        }
        fun restorePreferences() {
            preferences.forEach { (prefs, key, value) -> check(prefs.edit().apply {
                if (value == null) remove(key) else putBoolean(key, value)
            }.commit()) }
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
