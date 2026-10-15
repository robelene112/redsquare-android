package nl.davidengelen.redsquare

import android.content.Intent
import android.content.ContentValues
import android.app.Activity
import android.app.Instrumentation
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import org.hamcrest.CoreMatchers.anyOf
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class ChatImagesPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private enum class PhotoFlow { VIEW, DRAFT, SEND }

    @Test fun aCompatiblePrivatePhotoIsVisibleExpandableAndRetainedWhenTheServerNoLongerHasIt() = exercisePhoto(group = false)

    @Test fun aCompatibleGroupPhotoIsVisibleExpandableAndRetainedWhenTheServerNoLongerHasIt() = exercisePhoto(group = true)

    @Test fun aSelectedPrivatePhotoAndItsTextSurviveReopeningBeforeExplicitSend() = exercisePhoto(group = false, flow = PhotoFlow.DRAFT)

    @Test fun aSelectedGroupPhotoAndItsTextSurviveReopeningBeforeExplicitSend() = exercisePhoto(group = true, flow = PhotoFlow.DRAFT)

    @Test fun aRecoveredPrivatePhotoAndItsCaptionUseTheExistingSignedChatSendPath() = exercisePhoto(group = false, flow = PhotoFlow.SEND)

    @Test fun aRecoveredGroupPhotoAndItsCaptionUseTheExistingSignedChatSendPath() = exercisePhoto(group = true, flow = PhotoFlow.SEND)

    private fun exercisePhoto(group: Boolean, flow: PhotoFlow = PhotoFlow.VIEW) {
        val composing = flow != PhotoFlow.VIEW
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val kind = if (group) "group" else "private"
        val prefix = when (flow) { PhotoFlow.VIEW -> kind; PhotoFlow.DRAFT -> "$kind-draft"; PhotoFlow.SEND -> "$kind-send" }
        val reference = instrumentation.context.assets.open("native-${if (group) "group" else "private"}-chat-image-reference.json").bufferedReader()
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
        val draft = if (composing) "Photo caption 😀 <&> \"quoted\"\nA second line" else "Keep this text while receiving a photo 😀"
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
                            val row = hasClickAction() and isEnabled() and hasAnyDescendant(hasText(if (group) groupName else contact))
                            if (group) {
                                val groups = hasText("Groups") and hasClickAction() and isEnabled()
                                ui.waitUntil(20_000) { displayed(groups) || displayed(row, unmerged = true) }
                                if (displayed(groups)) ui.onNode(groups).performClick()
                            }
                            ui.waitUntil(20_000) { displayed(row, unmerged = true) }
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
                                SemanticsMatcher("Exact composed text") {
                                    it.config.getOrNull(SemanticsProperties.EditableText)?.text == draft
                                }
                            try {
                                ui.waitUntil(20_000) { displayed(expected) }; ui.onNode(expected).assertIsDisplayed()
                            } catch (failure: Throwable) {
                                try {
                                    val folder = File(context.cacheDir, "chat-images-evidence").apply { mkdirs() }
                                    File(folder, "$prefix-draft-semantics.txt").writeText(ui.onRoot(useUnmergedTree = true).printToString())
                                } catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
                                throw failure
                            }
                        }
                        open()
                        ui.onNode(hasSetTextAction() and isEnabled()).performTextReplacement(draft)
                        androidx.test.espresso.Espresso.closeSoftKeyboard()
                        if (composing) {
                            val source = instrumentation.context.assets.open("native-images-reference.json").bufferedReader()
                                .use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("images").get("red").asString }
                            val uri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                ContentValues().apply {
                                    put(MediaStore.Images.Media.DISPLAY_NAME, "redsquare-chat-photo-reference.png")
                                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                                }))
                            try {
                                context.contentResolver.openOutputStream(uri)!!.use {
                                    it.write(android.util.Base64.decode(source.substringAfter(','), android.util.Base64.NO_WRAP))
                                }
                                Intents.init()
                                try {
                                    Intents.intending(anyOf(hasAction(Intent.ACTION_OPEN_DOCUMENT), hasAction(Intent.ACTION_GET_CONTENT),
                                        hasAction("android.provider.action.PICK_IMAGES"), hasAction("androidx.activity.result.contract.action.PICK_IMAGES"),
                                        hasAction("com.google.android.gms.provider.action.PICK_IMAGES")))
                                        .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
                                    val add = hasText("Add photo") and hasClickAction() and isEnabled()
                                    ui.waitUntil(20_000) { displayed(add) }; ui.onNode(add).performClick()
                                    val attached = hasContentDescription("Image 1 of 1")
                                    fun assertPreparedPhoto() {
                                        ui.waitUntil(20_000) {
                                            try {
                                                val bitmap = ui.onNode(attached).assertIsDisplayed().captureToImage().asAndroidBitmap()
                                                val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
                                                kotlin.math.abs(android.graphics.Color.red(pixel) - 220) <= 5 &&
                                                    kotlin.math.abs(android.graphics.Color.green(pixel) - 30) <= 5 &&
                                                    kotlin.math.abs(android.graphics.Color.blue(pixel) - 40) <= 5
                                            } catch (_: AssertionError) { false }
                                        }
                                    }
                                    assertPreparedPhoto(); assertDraft()
                                    check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                                    capture("$prefix-photo-selected-unsent")
                                    ui.onNode(hasText("Back") and hasClickAction()).performClick()
                                    open(); scenario.recreate(); open()
                                    assertPreparedPhoto(); assertDraft()
                                    check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                                    capture("$prefix-photo-draft-reopened")
                                    if (flow == PhotoFlow.SEND) {
                                        ui.onNode(hasText("Send") and hasClickAction() and isEnabled()).performClick()
                                        ui.waitUntil(20_000) { peer.transactions.size == 1 && peer.relayedMessages.size == 1 }
                                        val sent = postingTransaction(peer.transactions.single())
                                        check(SaitoEnvelope.fromSignedJson(peer.relayedMessages.single()) == sent.envelope())
                                        check(peer.relayRecipients.single() == if (group) listOf(contact, wallet.publicKey) else listOf(wallet.publicKey, contact))
                                        val author = if (group) GroupChat.readMessage(requireNotNull(
                                            nl.davidengelen.redsquare.chat.GroupChatStore(context, wallet, SaitoBackend.OWNER).load(groupId)
                                        ).group, sent.envelope()).author else PrivateChat.read(wallet, sent.envelope(), contact).author
                                        check(author == wallet.publicKey)
                                        val caption = hasText(draft)
                                        ui.waitUntil(20_000) {
                                            if (!displayed(caption)) {
                                                try { ui.onNode(hasScrollAction()).performScrollToNode(caption) }
                                                catch (_: AssertionError) { /* Delivery can precede UI publication. */ }
                                            }
                                            displayed(caption)
                                        }
                                        ui.waitUntil(20_000) { displayed(hasSetTextAction() and isEnabled() and
                                            SemanticsMatcher("Empty sent composer") {
                                                it.config.getOrNull(SemanticsProperties.EditableText)?.text == ""
                                            }) }
                                        File(context.cacheDir, "chat-images-evidence/native-$kind-photo-envelope.json").writeText(sent.envelope())
                                        capture("$kind-photo-explicitly-sent")
                                    }
                                } finally { Intents.release() }
                            } finally { check(context.contentResolver.delete(uri, null, null) == 1) }
                            check(wallets.loadExisting().exportJson() == wallet.exportJson() && backends.load() == SaitoBackend.OWNER)
                            check(peer.clientKeys.isNotEmpty() && peer.clientKeys.all { it == wallet.publicKey })
                            check(peer.transactions.size == (if (flow == PhotoFlow.SEND) 1 else 0) &&
                                peer.relayedMessages.size == (if (flow == PhotoFlow.SEND) 1 else 0) && peer.reports.isEmpty())
                            peer.assertHealthy()
                            return@withFailureCapture
                        }
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

    private fun displayed(target: SemanticsMatcher, unmerged: Boolean = false) =
        try { ui.onNode(target, useUnmergedTree = unmerged).assertIsDisplayed(); true } catch (_: AssertionError) { false }
    private fun capture(name: String) = captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
        "chat-images-evidence/$name.png"))
}
