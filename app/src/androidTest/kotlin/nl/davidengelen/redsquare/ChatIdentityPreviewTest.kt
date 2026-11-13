package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.chat.GroupChatStore
import nl.davidengelen.redsquare.registry.REGISTERED_NAME_FRESHNESS_MILLIS
import nl.davidengelen.redsquare.registry.RegisteredNameStore
import nl.davidengelen.redsquare.registry.registeredNameIsCurrent
import nl.davidengelen.redsquare.saito.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Verifies long Registry names and exact public identities in cached private and group rows. */
class ChatIdentityPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var originalBackend: SaitoBackend? = null

    @Before fun prepare() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val backend = AndroidBackendStore(context)
        originalBackend = backend.load()
        backend.save(SaitoBackend.OWNER)
    }

    @After fun restoreBackend() {
        originalBackend?.let { AndroidBackendStore(context).save(it) }
    }

    @Test fun cachedNamesMustBeFreshAndCheckedByTheSelectedBackend() {
        val registrar = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "1").publicKey
        val displayName = "FreshIdentity@saito"
        val name = requireNotNull(SaitoRegistry.readNamecheck(
            signedRegistryRows(displayName, owner), displayName, registrar.publicKey))
        val now = System.currentTimeMillis()
        val current = RegisteredNameStore.Saved(name, SaitoBackend.OWNER, now)

        assertTrue(registeredNameIsCurrent(current, SaitoBackend.OWNER, now))
        assertTrue(registeredNameIsCurrent(current.copy(checkedAt = now - REGISTERED_NAME_FRESHNESS_MILLIS),
            SaitoBackend.OWNER, now))
        assertFalse(registeredNameIsCurrent(current.copy(checkedAt = now - REGISTERED_NAME_FRESHNESS_MILLIS - 1),
            SaitoBackend.OWNER, now))
        assertFalse(registeredNameIsCurrent(current.copy(backend = SaitoBackend.entries.single { it != SaitoBackend.OWNER }),
            SaitoBackend.OWNER, now))
        assertFalse(registeredNameIsCurrent(current.copy(checkedAt = now + 1), SaitoBackend.OWNER, now))
    }

    @Test fun privateChatShowsLongVerifiedNameAndExactIdentityAfterGoingOffline() = withRegistryWallet(
        SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
    ) { reader ->
        val sender = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe identity fixture only.
        val displayName = "NativeLongVerifiedDisplayNameForChat@saito"
        val message = PrivateChat.create(sender, reader.publicKey, "Verified private identity preview", 1700000123456)
        val conversation = PrivateChat.conversationId(reader.publicKey, sender.publicKey)
        val rows = signedRegistryRows(displayName, sender.publicKey)
        val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||",
            registryResponse = { criteria ->
                when (criteria.get("request").asString) {
                    "registry query" -> JsonObject().apply {
                        val keys = criteria.getAsJsonArray("keys").map { it.asString }
                        if (sender.publicKey in keys) addProperty(sender.publicKey, displayName)
                    }.toString()
                    "registry namecheck" -> {
                        assertEquals(displayName, criteria.get("identifier").asString)
                        rows
                    }
                    else -> error("Unexpected Registry request")
                }
            }, archiveResponse = { criteria ->
                if (criteria.get("field3")?.asString == conversation)
                    JsonArray().apply { add(JsonObject().apply { addProperty("tx", message.envelope()) }) }.toString()
                else "[]"
            })
        val walletStore = AndroidWalletStore(context)
        val before = walletStore.loadExisting()
        val fixtureState = ChatFixtureState(context, listOf(
            "chat.${SaitoBackend.OWNER.id}.${reader.publicKey}.$conversation.",
            "namemap.${sender.publicKey}.", "namerequest.${sender.publicKey}."), reader.publicKey)
        var primary: Throwable? = null
        try {
            fixtureState.prepare()
            walletStore.replace(reader, reader.withChatContact(sender.publicKey))
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("usernameFixtureRegistrar", true)).use { scenario ->
                    withFailureCapture({ capture("private-chat-failure.png") }) {
                        waitForEnabled("Chat")
                        if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
                        selectChatTab()
                        waitForText(displayName)
                        ui.onNodeWithText(displayName).performClick()
                        waitForText("Verified private identity preview")
                        ui.onNodeWithContentDescription("View identity details for $displayName").performClick()
                        ui.onNodeWithText("Identity details").assertIsDisplayed()
                        ui.onNodeWithText(sender.publicKey).assertIsDisplayed()
                        ui.onNodeWithText("Done").performClick()
                        assertTrue(peer.registryQueries.any { it.get("request").asString == "registry query" &&
                            it.getAsJsonArray("keys").any { key -> key.asString == sender.publicKey } })
                        assertTrue(peer.registryQueries.any { it.get("request").asString == "registry namecheck" })
                        capture("private-chat-online.png", displayName)

                        // A new Activity must use the verified local name and cached message without a node.
                        peer.goOffline()
                        scenario.recreate()
                        waitForEnabled("Chat")
                        selectChatTab()
                        // The selected conversation now survives Activity recreation.
                        // Verify its cached content directly, without reopening a list row.
                        waitForText("Verified private identity preview")
                        ui.onNodeWithContentDescription("View identity details for $displayName").performClick()
                        ui.onNodeWithText(sender.publicKey).assertIsDisplayed()
                        capture("private-chat-offline.png", displayName)
                        check(peer.transactions.isEmpty() && peer.reports.isEmpty() && peer.relayedMessages.isEmpty())
                        peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary,
                { fixtureState.restoreFiles() }, { fixtureState.restorePreferences() },
                { walletStore.replace(walletStore.loadExisting(), before); check(walletStore.loadExisting().exportJson() == before.exportJson()) })
        }
    }

    @Test fun groupChatShowsLongVerifiedNameAndExactIdentityFromCachedMessagesOffline() = withRegistryWallet(
        SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Public unsafe identity fixture only.
    ) { reader ->
        val sender = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
        val groupKey = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
        val displayName = "NativeLongVerifiedDisplayNameForChat@saito"
        val groupName = "Verified identity group"
        val creation = GroupChat.create(sender, groupKey.publicKey, groupName, listOf(reader.publicKey), 1700000123455)
        val group = GroupChat.readCreation(creation.envelope())
        val message = GroupChat.message(sender, group, "Verified cached group identity preview", 1700000123456)
        val rows = signedRegistryRows(displayName, sender.publicKey)
        val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||",
            registryResponse = { criteria ->
                when (criteria.get("request").asString) {
                    "registry query" -> JsonObject().apply {
                        val keys = criteria.getAsJsonArray("keys").map { it.asString }
                        if (sender.publicKey in keys) addProperty(sender.publicKey, displayName)
                    }.toString()
                    "registry namecheck" -> {
                        assertEquals(displayName, criteria.get("identifier").asString)
                        rows
                    }
                    else -> error("Unexpected Registry request")
                }
            }, archiveResponse = { "[]" })
        val walletStore = AndroidWalletStore(context)
        val fixtureState = ChatFixtureState(context, listOf(
            "groupchat.${SaitoBackend.OWNER.id}.${reader.publicKey}.",
            "namemap.${sender.publicKey}.", "namerequest.${sender.publicKey}."), reader.publicKey)
        var primary: Throwable? = null
        try {
            fixtureState.prepare()
            val wallet = reader.withNamedChatGroup(group.id, groupName)
            walletStore.replace(reader, wallet)
            val groups = GroupChatStore(context, wallet, SaitoBackend.OWNER)
            groups.receive(creation.envelope())
            check(!groups.receiveAvailable(group.id, listOf(message.envelope())))
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("usernameFixtureRegistrar", true)).use { scenario ->
                    withFailureCapture({ capture("group-chat-failure.png") }) {
                        waitForEnabled("Chat")
                        if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
                        selectChatTab()
                        waitForText("Groups")
                        ui.onNodeWithText("Groups").performClick()
                        waitForText("Group messages are unencrypted.")
                        val groupRow = hasClickAction() and hasAnyDescendant(hasText(groupName))
                        ui.waitUntil(20_000) { ui.onAllNodes(groupRow, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
                        ui.onNode(groupRow, useUnmergedTree = true).performClick()
                        waitForText("Verified cached group identity preview")
                        waitForText(displayName)
                        ui.onNodeWithContentDescription("View identity details for $displayName").performClick()
                        ui.onNodeWithText("Identity details").assertIsDisplayed()
                        ui.onNodeWithText(sender.publicKey).assertIsDisplayed()
                        ui.onNodeWithText("Done").performClick()
                        assertTrue(peer.registryQueries.any { it.get("request").asString == "registry query" &&
                            it.getAsJsonArray("keys").any { key -> key.asString == sender.publicKey } })
                        assertTrue(peer.registryQueries.any { it.get("request").asString == "registry namecheck" })

                        // Cached signed group content and its verified local name remain readable offline.
                        peer.goOffline()
                        scenario.recreate()
                        waitForEnabled("Chat")
                        selectChatTab()
                        // Group selection is restored too. The same signed message and
                        // verified author must be readable in the retained destination.
                        waitForText("Verified cached group identity preview")
                        waitForText(displayName)
                        ui.onNodeWithText(displayName).assertIsDisplayed()
                        ui.onNodeWithContentDescription("View identity details for $displayName").performClick()
                        ui.onNodeWithText(sender.publicKey).assertIsDisplayed()
                        capture("group-chat-offline.png", displayName)
                        check(peer.transactions.isEmpty() && peer.reports.isEmpty() && peer.relayedMessages.isEmpty())
                        peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, { fixtureState.restoreFiles() }, { fixtureState.restorePreferences() })
        }
    }

    private fun signedRegistryRows(identifier: String, owner: String): String {
        val registrar = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Public unsafe fixture only.
        val bid = 17L
        val blockHash = "a".repeat(64)
        val signature = registrar.sign((identifier + owner + bid + blockHash).toByteArray(Charsets.UTF_8))
        return JsonArray().apply {
            add(JsonObject().apply {
                addProperty("identifier", identifier)
                addProperty("publickey", owner)
                addProperty("bid", bid)
                addProperty("bsh", blockHash)
                addProperty("signer", registrar.publicKey)
                addProperty("lc", 1)
                addProperty("sig", signature)
            })
        }.toString()
    }

    private fun withRegistryWallet(fixture: SaitoWallet, block: (SaitoWallet) -> Unit) {
        val wallets = AndroidWalletStore(context)
        val original = wallets.loadOrCreate()
        if (original.publicKey != fixture.publicKey) wallets.replace(original, fixture)
        var primary: Throwable? = null
        try { block(fixture) }
        catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, {
                val active = wallets.loadExisting()
                check(active.publicKey == fixture.publicKey)
                if (original.publicKey != fixture.publicKey) wallets.replace(active, original)
            })
        }
    }

    private fun selectChatTab() = ui.onNode(hasText("Chat") and hasClickAction() and isEnabled()).performClick()

    private fun waitForText(text: String) {
        ui.waitUntil(20_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForEnabled(text: String) {
        ui.waitUntil(20_000) { ui.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun capture(name: String, expectedText: String? = null) =
        captureNativeScreen(File(context.cacheDir, "chat-identity-evidence/$name"), expectedText)
}
