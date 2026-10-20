package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.PrivateChat
import nl.davidengelen.redsquare.saito.PrivateChatLike
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean

/** User-facing publication gates use the real app and an owned signed-transaction peer. */
class PublishingSurfacesPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun prepare() {
        resetSyntheticRecords()
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
    }

    @After fun resetSyntheticRecords() {
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        context.noBackupFilesDir.listFiles()?.filter {
            it.name.startsWith("social.") || it.name.startsWith("feed.") ||
                it.name.startsWith("conversation.") || it.name.startsWith("profile.") ||
                it.name.startsWith("profilemeta.") || it.name.startsWith("profiledraft.") ||
                it.name.startsWith("namerequest.") || it.name.startsWith("namemap.")
        }?.forEach { check(it.delete()) }
    }

    @Test fun privateChatCancellationKeepsTheDraftAndAcceptanceDoesNotSendIt() {
        val fixture = File(context.noBackupFilesDir, "publishing-policy-fixture.json")
        val previous = fixture.takeIf { it.exists() }?.readBytes()
        val wallets = AndroidWalletStore(context)
        val initialWallet = wallets.loadOrCreate()
        val recipient = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixture only.
        val conversation = PrivateChat.conversationId(initialWallet.publicKey, recipient.publicKey)
        val names = listOf("chat", "chatwork", "chatreactions").map {
            "$it.owner.${initialWallet.publicKey}.$conversation.v1"
        }
        val retained = ChatFixtureState(context, names, initialWallet.publicKey)
        val revision = "owned-private-${java.util.UUID.randomUUID()}"
        fixture.writeText("""{"revision":"$revision","ready":true,"enforce":true,
            "terms":"Controlled private Chat terms. No real publisher approval.",
            "community":"Controlled Chat rules. Accepting does not send your message."}""")
        var primary: Throwable? = null
        try {
            retained.prepare()
            val message = "Keep my private draft 😀 until I deliberately send"
            val messageId = AtomicReference<String?>()
            fun verifyOriginal(envelope: String) {
                val signed = SaitoEnvelope.decodePayload(envelope)
                if (signed.body.isJsonPrimitive) {
                    val received = PrivateChat.read(recipient, envelope, initialWallet.publicKey)
                    assertEquals(message, received.text)
                    assertEquals(initialWallet.publicKey, received.author)
                    messageId.compareAndSet(null, signed.id)
                    assertEquals(messageId.get(), signed.id)
                } else {
                    val reaction = PrivateChatLike.read(recipient, initialWallet.publicKey, envelope)
                    assertEquals(initialWallet.publicKey, reaction.author)
                    assertEquals(initialWallet.publicKey, reaction.targetAuthor)
                    assertEquals(requireNotNull(messageId.get()), reaction.target)
                }
            }
            val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = { "[]" },
                onTransaction = { frame -> verifyOriginal(postingTransaction(frame).envelope()) }, relayResponse = { original ->
                    verifyOriginal(SaitoEnvelope.fromSignedJson(original))
                    "{\"err\":\"\",\"success\":1}"
                })
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                    withFailureCapture({ capture("private-first-failure.png") }) {
                        waitForEnabled("Chat")
                        if (ui.onNodeWithText("Later").isDisplayed()) ui.onNodeWithText("Later").performClick()
                        ui.onNodeWithText("Chat").performClick()
                        waitFor("Public key")
                        ui.onNodeWithText("Public key").performTextReplacement(recipient.publicKey)
                        androidx.test.espresso.Espresso.closeSoftKeyboard()
                        waitForEnabled("Open chat")
                        ui.onNodeWithText("Open chat").performClick()
                        waitFor("Message")
                        ui.waitUntil(20_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                        val originalWallet = wallets.loadExisting().exportJson()
                        ui.onNode(hasSetTextAction()).performTextReplacement(message)
                        androidx.test.espresso.Espresso.closeSoftKeyboard()
                        repeat(2) { attempt ->
                            ui.onNodeWithText("Send").performClick()
                            waitFor("Review publishing terms")
                            assertTrue(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                            if (attempt == 0) {
                                ui.onNodeWithText("Cancel").performClick()
                                ui.onNode(hasSetTextAction()).assertIsEnabled().assert(
                                    SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(message)))
                                capture("private-cancel-keeps-editable-message.png")
                            }
                        }
                        ui.onNodeWithText("Accept").performClick()
                        waitFor("Agreement saved. New content stays unsent. Queued messages may resume.")
                        assertTrue("Agreement must not transmit either Chat leg", peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                        ui.onNodeWithText("Send").performClick()
                        waitFor("Forwarded. Delivery is not confirmed.")
                        assertEquals(1, peer.transactions.size)
                        assertEquals(1, peer.relayedMessages.size)
                        assertTrue(originalWallet == wallets.loadExisting().exportJson())
                        assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
                        capture("private-deliberately-forwarded-after-agreement.png")
                        fixture.writeText("""{"revision":"$revision-reaction","ready":true,"enforce":true,
                            "terms":"Updated controlled Chat terms include reactions. No real publisher approval.",
                            "community":"Updated controlled Chat rules. Agree separately before reacting."}""")
                        repeat(2) { attempt ->
                            waitForEnabled("Like")
                            waitForScrollable("Like")
                            ui.onNodeWithText("Like").performClick()
                            waitFor("Review publishing terms")
                            ui.onNodeWithText("Updated controlled Chat terms include reactions. No real publisher approval.").assertIsDisplayed()
                            assertEquals(1, peer.transactions.size)
                            assertEquals(1, peer.relayedMessages.size)
                            if (attempt == 0) {
                                ui.onNodeWithText("Cancel").performClick()
                                capture("private-reaction-cancel-keeps-message-unliked.png")
                            }
                        }
                        ui.onNodeWithText("Accept").performClick()
                        waitFor("Agreement saved. New content stays unsent. Queued messages may resume.")
                        assertEquals(1, peer.transactions.size)
                        assertEquals(1, peer.relayedMessages.size)
                        waitForScrollable("Like")
                        ui.onNodeWithText("Like").performClick()
                        waitFor("Reaction forwarded. Delivery is not confirmed.")
                        capture("private-reaction-forwarded-before-scroll.png")
                        waitForScrollable("Unlike")
                        assertEquals(2, peer.transactions.size)
                        assertEquals(2, peer.relayedMessages.size)
                        assertTrue(originalWallet == wallets.loadExisting().exportJson())
                        assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
                        capture("private-reaction-deliberately-forwarded-after-new-revision.png", "Unlike")
                        peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, { retained.restoreFiles() },
                { wallets.replace(wallets.loadExisting(), initialWallet) },
                { retained.restorePreferences() },
                { if (previous == null) check(!fixture.exists() || fixture.delete()) else fixture.writeBytes(previous) })
        }
    }

    @Test fun checkingANameStaysAvailableButRegistrationRequiresSeparateAgreement() {
        val fixture = File(context.noBackupFilesDir, "publishing-policy-fixture.json")
        val previous = fixture.takeIf { it.exists() }?.readBytes()
        val revision = "owned-registry-${java.util.UUID.randomUUID()}"
        fixture.writeText("""{"revision":"$revision","ready":true,"enforce":true,
            "terms":"Controlled registration terms. No real publisher approval.",
            "community":"Controlled registration rules. Checking a name does not publish it."}""")
        try {
            val wallet = AndroidWalletStore(context).loadOrCreate()
            val originalWallet = wallet.exportJson()
            // Public unsafe scalar2 registrar, confined to the existing emulator/loopback authority seam.
            val registrar = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
            val reference = InstrumentationRegistry.getInstrumentation().context.assets
                .open("native-registry-reference.json").bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
            assertEquals(reference.get("registrar").asString, registrar.publicKey)
            val identifier = "ConsentCase1@saito"
            val blockHash = "a".repeat(64)
            val proof = JsonObject().apply {
                addProperty("identifier", identifier); addProperty("publickey", wallet.publicKey)
                addProperty("bid", 123); addProperty("bsh", blockHash); addProperty("signer", registrar.publicKey)
                addProperty("sig", registrar.sign((identifier + wallet.publicKey + "123" + blockHash).toByteArray(Charsets.UTF_8)))
                addProperty("lc", 1)
            }
            val registered = AtomicBoolean(false)
            val peer = ControlledFeedPeer("[]", registryResponse = { criteria ->
                if (criteria.get("request").asString == "registry query") "{}" else {
                    assertEquals("registry namecheck", criteria.get("request").asString)
                    assertEquals(identifier, criteria.get("identifier").asString)
                    if (registered.get()) "[$proof]" else "[]"
                }
            }, onTransaction = { frame ->
                val signed = SaitoEnvelope.decode(postingTransaction(frame).envelope())
                assertEquals(wallet.publicKey, signed.author)
                assertEquals(listOf(registrar.publicKey, wallet.publicKey), signed.recipients)
                assertEquals("Registry", signed.body.get("module").asString)
                assertEquals("register", signed.body.get("request").asString)
                assertEquals(identifier, signed.body.get("identifier").asString)
                registered.set(true)
            }, archiveResponse = { "[]" })
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("feedDisabled", true).putExtra("usernameFixtureRegistrar", true)).use {
                    withFailureCapture({ capture("registry-first-failure.png") }) {
                        waitForEnabled("Settings")
                        if (ui.onNodeWithText("Later").isDisplayed()) ui.onNodeWithText("Later").performClick()
                        ui.onNodeWithText("Settings").performClick()
                        ui.onNodeWithText("Saito username").performScrollTo().performClick()
                        waitForEnabled("Check name")
                        ui.onNode(hasSetTextAction()).performTextReplacement("ConsentCase1")
                        androidx.test.espresso.Espresso.closeSoftKeyboard()
                        ui.onNodeWithText("Check name").performScrollTo().performClick()
                        waitForScrollable("This username is currently available. Checking does not reserve it.")
                        assertTrue("Checking availability does not require agreement or publish", peer.transactions.isEmpty())
                        repeat(2) { attempt ->
                            ui.onNodeWithText("Register name").performScrollTo().performClick()
                            waitFor("Review publishing terms")
                            assertTrue(peer.transactions.isEmpty())
                            if (attempt == 0) {
                                ui.onNodeWithText("Cancel").performClick()
                                ui.onNode(hasSetTextAction()).assertIsEnabled().assert(
                                    SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("ConsentCase1")))
                                capture("registry-cancel-keeps-name-and-availability.png")
                            }
                        }
                        ui.onNodeWithText("Accept").performClick()
                        waitFor("Agreement saved. New content stays unsent. Queued messages may resume.")
                        assertTrue("Agreement is separate from registration", peer.transactions.isEmpty())
                        ui.onNodeWithText("Register name").performScrollTo().performClick()
                        waitForScrollable("This username is registered to your wallet.")
                        ui.onNodeWithText(identifier).assertIsDisplayed()
                        assertEquals(1, peer.transactions.size)
                        assertTrue(originalWallet == AndroidWalletStore(context).loadExisting().exportJson())
                        assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
                        capture("registry-deliberately-registered-after-agreement.png")
                        peer.assertHealthy()
                    }
                }
            }
        } finally {
            if (previous == null) check(!fixture.exists() || fixture.delete()) else fixture.writeBytes(previous)
        }
    }

    @Test fun profilePublishingRequiresAgreementAndCancellationKeepsTheEditableBio() {
        val fixture = File(context.noBackupFilesDir, "publishing-policy-fixture.json")
        val previous = fixture.takeIf { it.exists() }?.readBytes()
        val revision = "owned-profile-${java.util.UUID.randomUUID()}"
        fixture.writeText("""{"revision":"$revision","ready":true,"enforce":true,
            "terms":"Controlled profile terms. No real publisher approval.",
            "community":"Controlled profile rules. Keep your editable draft."}""")
        try {
            val indexed = AtomicReference<JsonObject?>()
            val bio = "Keep my profile draft until I choose to publish"
            val peer = ControlledFeedPeer("[]", archiveResponse = { "[]" }, profileArchiveResponse = { criteria ->
                val row = indexed.get()
                if (row != null && (!criteria.has("sig") || criteria.get("sig").asString == row.get("sig").asString))
                    "[$row]" else "[]"
            }, onTransaction = { frame ->
                val transaction = postingTransaction(frame)
                val signed = SaitoEnvelope.decode(transaction.envelope())
                assertEquals("Profile", signed.body.get("module").asString)
                assertEquals("update profile", signed.body.get("request").asString)
                assertEquals(bio, signed.body.getAsJsonObject("data").get("description").asString)
                indexed.set(JsonObject().apply {
                    addProperty("sig", signed.id); addProperty("created_at", signed.createdAt)
                    addProperty("field1", "Profile"); addProperty("field2", signed.author)
                    addProperty("tx", transaction.envelope())
                })
            })
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                    withFailureCapture({ capture("profile-first-failure.png") }) {
                        waitFor("New post")
                        val originalWallet = AndroidWalletStore(context).loadExisting().exportJson()
                        if (ui.onNodeWithText("Later").isDisplayed()) ui.onNodeWithText("Later").performClick()
                        ui.onNodeWithText("Your profile").performClick()
                        waitFor("Edit profile")
                        ui.onNodeWithText("Edit profile").performClick()
                        ui.waitUntil(20_000) {
                            ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty()
                        }
                        ui.onNode(hasSetTextAction()).performTextReplacement(bio)
                        androidx.test.espresso.Espresso.closeSoftKeyboard()
                        repeat(2) { attempt ->
                            ui.onNodeWithText("Save profile").performClick()
                            waitFor("Review publishing terms")
                            assertTrue(peer.transactions.isEmpty())
                            if (attempt == 0) {
                                ui.onNodeWithText("Cancel").performClick()
                                ui.onNode(hasSetTextAction()).assertIsEnabled().assert(
                                    SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(bio)))
                                capture("profile-cancel-keeps-editable-bio.png")
                            }
                        }
                        ui.onNodeWithText("Accept").performClick()
                        waitFor("Agreement saved. New content stays unsent. Queued messages may resume.")
                        assertTrue("Agreement does not publish the profile", peer.transactions.isEmpty())
                        ui.onNodeWithText("Save profile").performClick()
                        waitFor("Profile available on this node.")
                        assertEquals(1, peer.transactions.size)
                        assertTrue(originalWallet == AndroidWalletStore(context).loadExisting().exportJson())
                        assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
                        capture("profile-deliberately-published-after-agreement.png")
                        peer.assertHealthy()
                    }
                }
            }
        } finally {
            if (previous == null) check(!fixture.exists() || fixture.delete()) else fixture.writeBytes(previous)
        }
    }

    @Test fun likingRequiresAgreementAndCancellingDoesNotSaveOrTransmitTheAction() {
        val fixture = File(context.noBackupFilesDir, "publishing-policy-fixture.json")
        val previous = fixture.takeIf { it.exists() }?.readBytes()
        val revision = "owned-like-${java.util.UUID.randomUUID()}"
        fixture.writeText("""{"revision":"$revision","ready":true,"enforce":true,
            "terms":"Controlled social terms. No real publisher approval.",
            "community":"Controlled social rules. Publishing requires your choice."}""")
        try {
            val root = InstrumentationRegistry.getInstrumentation().context.assets.open("native-feed-reference.json")
                .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow") }
            val liked = AtomicReference<JsonObject?>()
            val peer = ControlledFeedPeer("[]", archiveResponse = { criteria ->
                if (criteria.get("field1")?.asString == "RedSquareLike") liked.get()?.let { "[$it]" } ?: "[]"
                else "[$root]"
            }, onTransaction = { frame ->
                val transaction = postingTransaction(frame)
                val action = SaitoEnvelope.decode(transaction.envelope())
                assertEquals("RedSquare", action.body.get("module").asString)
                assertEquals("like tweet", action.body.get("request").asString)
                assertEquals(root.get("sig").asString, action.body.getAsJsonObject("data").get("signature").asString)
                liked.set(JsonObject().apply {
                    addProperty("sig", action.id); addProperty("field1", "RedSquareLike")
                    addProperty("field2", action.author); addProperty("field4", root.get("sig").asString)
                    addProperty("created_at", action.createdAt); addProperty("tx", transaction.envelope())
                })
            })
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                    withFailureCapture({ capture("like-first-failure.png") }) {
                        waitFor("Native Red Square reference post")
                        val originalWallet = AndroidWalletStore(context).loadExisting().exportJson()
                        ui.clickVisiblePostContent("Like")
                        waitFor("Review publishing terms")
                        assertTrue(peer.transactions.isEmpty())
                        ui.onNodeWithText("Cancel").performClick()
                        ui.clickVisiblePostContent("Like")
                        waitFor("Review publishing terms")
                        assertTrue("Cancellation must not publish or turn Like into an uncertain retry", peer.transactions.isEmpty())
                        capture("like-cancelled-and-reviewed-again.png")
                        ui.onNodeWithText("Accept").performClick()
                        waitFor("Agreement saved. New content stays unsent. Queued messages may resume.")
                        assertTrue("Agreement is separate from Like", peer.transactions.isEmpty())
                        ui.clickVisiblePostContent("Like")
                        waitFor("Liked")
                        assertEquals(1, peer.transactions.size)
                        assertTrue(originalWallet == AndroidWalletStore(context).loadExisting().exportJson())
                        assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
                        capture("like-deliberately-published-after-agreement.png")
                        peer.assertHealthy()
                    }
                }
            }
        } finally {
            if (previous == null) check(!fixture.exists() || fixture.delete()) else fixture.writeBytes(previous)
        }
    }

    private fun waitFor(text: String) = ui.waitUntil(20_000) { ui.onNodeWithText(text).isDisplayed() }
    private fun waitForEnabled(text: String) = ui.waitUntil(20_000) {
        ui.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
    }
    private fun waitForScrollable(text: String) {
        ui.waitUntil(20_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }
    private fun capture(name: String, expectedText: String? = null) {
        val file = File(context.cacheDir, "agreement-surfaces-evidence/$name")
        captureNativeScreen(file, expectedText)
        File(file.parentFile, file.nameWithoutExtension + ".semantics.txt")
            .writeText(ui.onAllNodes(isRoot(), useUnmergedTree = true).printToString(maxDepth = Int.MAX_VALUE))
    }
}
