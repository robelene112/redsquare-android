package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class ChatImagesPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun aCompatiblePrivatePhotoIsVisibleExpandableAndRetainedWhenTheServerNoLongerHasIt() = exercisePhoto(group = false)

    @Test fun aCompatibleGroupPhotoIsVisibleExpandableAndRetainedWhenTheServerNoLongerHasIt() = exercisePhoto(group = true)

    private fun exercisePhoto(group: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val prefix = if (group) "group" else "private"
        val reference = instrumentation.context.assets.open("native-$prefix-chat-image-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject }
        val contact = reference.get(if (group) "author" else "sender").asString
        val groupId = reference.get("groupId").asString
        val groupName = "Compatible photo group"
        val identity = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Public unsafe fixture only.
        val wallet = if (group) identity.withNamedChatGroup(groupId, groupName) else identity.withChatContact(contact)
        val wallets = AndroidWalletStore(context); val original = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context); val originalBackend = backends.load()
        val prefixes = SaitoBackend.entries.flatMap { backend -> listOf("chat.${backend.id}.${wallet.publicKey}.",
            "chatwork.${backend.id}.${wallet.publicKey}.", "chatreactions.${backend.id}.${wallet.publicKey}.",
            "groupchat.${backend.id}.${wallet.publicKey}.") } + "personal.${wallet.publicKey}."
        val retained = ChatFixtureState(context, prefixes, wallet.publicKey)
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        val originalAlerts = manager.activeNotifications.map { it.tag to it.id }.toSet()
        val reads = AtomicInteger()
        val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = { criteria ->
            if (criteria.get("field3")?.asString == reference.get("groupId").asString) reads.incrementAndGet()
            "[]"
        })
        val incoming = if (group) {
            val detached = JsonParser.parseString(reference.get("envelope").asString).asJsonObject
            val message = java.util.Base64.getDecoder().decode(detached.get("m").asString)
            (java.util.Base64.getDecoder().decode(detached.get("t").asString) + message).also {
                java.nio.ByteBuffer.wrap(it).putInt(8, message.size)
            }
        } else java.util.Base64.getDecoder().decode(reference.get("networkBase64").asString)
        val draft = "Keep this text while receiving a photo 😀"
        var primary: Throwable? = null
        try {
            retained.prepare()
            wallets.replace(original, wallet); backends.save(SaitoBackend.OWNER)
            if (group) {
                val admin = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixture only.
                val creation = GroupChat.create(admin, groupId, groupName, listOf(wallet.publicKey), 1700000123400)
                nl.davidengelen.redsquare.chat.GroupChatStore(context, wallet, SaitoBackend.OWNER).receive(creation.envelope())
            }
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { scenario ->
                    withFailureCapture({ capture("$prefix-photo-failure") }) {
                        fun open() {
                            val chat = hasText("Chat") and hasClickAction() and isEnabled()
                            ui.waitUntil(20_000) { displayed(hasText("Groups")) || displayed(chat) }
                            if (!displayed(hasText("Groups"))) ui.onNode(chat).performClick()
                            val before = reads.get()
                            if (group) {
                                ui.waitUntil(20_000) { displayed(hasText("Groups") and hasClickAction() and isEnabled()) }
                                ui.onNode(hasText("Groups") and hasClickAction()).performClick()
                            }
                            val row = hasClickAction() and isEnabled() and hasAnyDescendant(hasText(if (group) groupName else contact))
                            ui.waitUntil(20_000) {
                                try { ui.onNode(row, useUnmergedTree = true).assertIsDisplayed(); true }
                                catch (_: AssertionError) { false }
                            }
                            ui.onNode(row, useUnmergedTree = true).performClick()
                            ui.waitUntil(20_000) { displayed(hasSetTextAction() and isEnabled()) && reads.get() > before }
                        }
                        fun assertRedRaster(target: SemanticsMatcher) {
                            val bitmap = ui.onNode(target).assertIsDisplayed().captureToImage().asAndroidBitmap()
                            assertEquals(0xffdc1e28.toInt(), bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
                        }
                        fun photo() {
                            val image = hasContentDescription("Image 1 of 1")
                            ui.waitUntil(20_000) {
                                if (!displayed(image)) {
                                    try { ui.onNode(hasScrollAction()).performScrollToNode(image) }
                                    catch (_: AssertionError) { /* Receipt can precede UI publication. */ }
                                }
                                displayed(image)
                            }
                            assertRedRaster(image)
                        }
                        fun assertDraft() {
                            val expected = hasSetTextAction() and isEnabled() and
                                SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(draft))
                            ui.waitUntil(20_000) { displayed(expected) }; ui.onNode(expected).assertIsDisplayed()
                        }
                        open()
                        ui.onNode(hasSetTextAction() and isEnabled()).performTextReplacement(draft)
                        androidx.test.espresso.Espresso.closeSoftKeyboard()
                        peer.sendIncoming(incoming, requestId = 960)
                        ui.waitUntil(20_000) { peer.incomingAcknowledgements.any {
                            it[0] == 13.toByte() && java.nio.ByteBuffer.wrap(it, 1, 4).int == 960
                        } }
                        photo(); assertDraft(); capture("$prefix-compatible-photo")
                        ui.onNodeWithContentDescription("Image 1 of 1").performClick()
                        ui.waitUntil(20_000) { displayed(hasText("Close image")) }
                        ui.waitUntil(20_000) {
                            try { assertRedRaster(isDialog()); true }
                            catch (_: AssertionError) { false } // Dialog controls precede asynchronous raster loading.
                        }
                        assertRedRaster(isDialog())
                        capture("$prefix-photo-expanded")
                        ui.onNodeWithText("Close image").performClick()
                        scenario.recreate(); open()
                        photo(); assertDraft(); capture("$prefix-photo-cached-after-reopening")
                        check(wallets.loadExisting().exportJson() == wallet.exportJson() && backends.load() == SaitoBackend.OWNER)
                        check(peer.clientKeys.isNotEmpty() && peer.clientKeys.all { it == wallet.publicKey })
                        check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty() && peer.reports.isEmpty())
                        peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, {
                manager.activeNotifications.filter { (it.tag to it.id) !in originalAlerts }.forEach { manager.cancel(it.tag, it.id) }
            }, {
                retained.restoreFiles()
            }, { wallets.replace(wallets.loadOrCreate(), original) }, { backends.save(originalBackend) }, {
                retained.restorePreferences()
            })
        }
    }

    private fun displayed(target: SemanticsMatcher) = try { ui.onNode(target).assertIsDisplayed(); true } catch (_: AssertionError) { false }
    private fun capture(name: String) = captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
        "chat-images-evidence/$name.png"))
}
