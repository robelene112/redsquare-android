package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import nl.davidengelen.redsquare.chat.GroupChatStore
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Separate bounded group journeys preserve cancellation before membership/history changes. */
class PublishingGroupPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun creatingAGroupRequiresAgreementAndKeepsItsUnpublishedDetailsOnCancel() = exercise(Action.CREATE)
    @Test fun previewingAnInvitationIsIndependentButJoiningRequiresAgreement() = exercise(Action.JOIN)
    @Test fun cancellingGroupPublicationKeepsTheEditableMessage() = exercise(Action.MESSAGE)
    @Test fun cancellingAGroupReactionKeepsTheMessageUnliked() = exercise(Action.REACTION)
    @Test fun promotingAMemberRequiresAgreementAndKeepsConfirmationOnCancel() = exercise(Action.PROMOTION)
    @Test fun removingAMemberRemainsAvailableWithUnapprovedPolicies() = exercise(Action.REMOVAL)
    @Test fun leavingAGroupRemainsAvailableWithUnapprovedPolicies() = exercise(Action.LEAVE)

    private enum class Action(val request: String, val button: String, val requiresAgreement: Boolean = true) {
        CREATE("chat group", "Create"), JOIN("chat join", "Join"),
        MESSAGE("chat message", "Send"), REACTION("chat like", "Like"),
        PROMOTION("chat update", "Confirm promotion"), REMOVAL("chat remove", "Confirm removal", false),
        LEAVE("chat remove", "Leave", false)
    }

    private fun exercise(action: Action) {
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallets = AndroidWalletStore(context)
        val original = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context)
        val previousBackend = backends.load()
        val key = SaitoWallet.fromPrivateKey("0".repeat(63) + "9") // Public unsafe group fixture only.
        val member = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe member fixture only.
        val name = "Policy group ${action.name.lowercase()}"
        val existing = action != Action.CREATE && action != Action.JOIN
        val wallet = if (existing) original.withNamedChatGroup(key.publicKey, name, key) else original
        val retained = ChatFixtureState(context,
            listOf("groupchat.owner.${wallet.publicKey}.", "personal.${wallet.publicKey}."), wallet.publicKey)
        val fixture = File(context.noBackupFilesDir, "publishing-policy-fixture.json")
        val previousPolicy = fixture.takeIf { it.exists() }?.readBytes()
        val revision = "owned-group-${action.name}-${java.util.UUID.randomUUID()}"
        val creator = if (action == Action.JOIN) member else wallet
        val creation = GroupChat.create(creator, key.publicKey, name,
            if (action == Action.JOIN) emptyList() else listOf(member.publicKey), 1700000123400)
        val group = GroupChat.readCreation(creation.envelope())
        val sourceText = "Available controlled group message"
        val source = if (existing) GroupChat.message(member, group, sourceText, 1700000123401) else null
        val message = "Keep my group draft 😀 until deliberate Send"
        val raw = AtomicReference<String?>()
        val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = { "[]" },
            onTransaction = { frame ->
                val envelope = postingTransaction(frame).envelope()
                val signed = SaitoEnvelope.decode(envelope)
                assertEquals(wallet.publicKey, signed.author)
                assertEquals("Chat", signed.body.get("module").asString)
                assertEquals(action.request, signed.body.get("request").asString)
                if (action == Action.CREATE) {
                    val created = GroupChat.readCreation(envelope)
                    assertEquals(name, created.name)
                    assertEquals(setOf(wallet.publicKey, member.publicKey), created.memberRoles.keys)
                } else {
                    assertEquals(key.publicKey, signed.body.get("group_id").asString)
                    when (action) {
                        Action.JOIN -> assertEquals(member.publicKey, signed.body.get("invited_by").asString)
                        Action.MESSAGE -> assertEquals(message, signed.body.get("message").asString)
                        Action.REACTION -> {
                            assertEquals(requireNotNull(source).id, signed.body.get("signature").asString)
                            assertEquals(member.publicKey, signed.body.get("mentioned").asString)
                        }
                        Action.PROMOTION -> assertEquals("admin", signed.body.getAsJsonObject("member_ids").get(member.publicKey).asString)
                        Action.REMOVAL, Action.LEAVE -> {
                            val target = if (action == Action.LEAVE) wallet.publicKey else member.publicKey
                            assertEquals(target, signed.body.get("member_id").asString)
                            assertEquals(if (action == Action.LEAVE) ChatGroupRole.LEFT else ChatGroupRole.REMOVED,
                                GroupChat.applyControl(group, envelope).memberRoles[target])
                        }
                        Action.CREATE -> error("Creation validated separately")
                    }
                }
                raw.set(envelope)
            }, relayResponse = { signed ->
                assertEquals(requireNotNull(raw.get()), SaitoEnvelope.fromSignedJson(signed))
                "{\"err\":\"\",\"success\":1}"
            })
        var primary: Throwable? = null
        try {
            retained.prepare()
            wallets.replace(original, wallet)
            backends.save(SaitoBackend.OWNER)
            fixture.writeText("""{"revision":"$revision","ready":${action.requiresAgreement},"enforce":true,
                "terms":"Controlled group terms. No real publisher approval.",
                "community":"Controlled group rules. Agreement is separate from your action."}""")
            if (existing) GroupChatStore(context, wallet, SaitoBackend.OWNER).apply {
                receive(creation.envelope())
                receive(requireNotNull(source).envelope())
            }
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                    withFailureCapture({
                        runCatching { File(context.cacheDir, "agreement-group-evidence/${action.name.lowercase()}-receipt-counts.json")
                            .apply { parentFile?.mkdirs() }.writeText(JsonObject().apply {
                                addProperty("transactions", peer.transactions.size)
                                addProperty("relayedMessages", peer.relayedMessages.size)
                            }.toString()) }
                        capture("${action.name.lowercase()}-first-failure.png")
                    }) {
                        click("Chat")
                        click("Groups")
                        when (action) {
                            Action.CREATE -> {
                                click("Create group")
                                ui.onNodeWithText("Group name").performTextReplacement(name)
                                ui.onNodeWithText("Invitees (optional)").performTextReplacement(member.publicKey)
                                androidx.test.espresso.Espresso.closeSoftKeyboard()
                            }
                            Action.JOIN -> {
                                click("Join group")
                                ui.onNodeWithText("Invitation link").performTextReplacement(
                                    GroupInvitation.fromGroup(group, member.publicKey).link(SaitoBackend.OWNER))
                                androidx.test.espresso.Espresso.closeSoftKeyboard()
                                click("Preview")
                                waitFor("Join")
                                ui.onNodeWithText(name).assertIsDisplayed()
                                assertTrue("Preview does not require agreement or join", peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                            }
                            Action.MESSAGE, Action.REACTION, Action.PROMOTION, Action.REMOVAL, Action.LEAVE -> {
                                click(name)
                                waitFor(sourceText)
                                if (action == Action.MESSAGE) {
                                    waitFor("Message")
                                    ui.waitUntil(20_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                                    ui.onNodeWithText("Message").performTextReplacement(message)
                                    androidx.test.espresso.Espresso.closeSoftKeyboard()
                                } else if (action == Action.PROMOTION || action == Action.REMOVAL) {
                                    click("Members")
                                    val description = "${if (action == Action.PROMOTION) "Promote" else "Remove"} member ${member.publicKey}"
                                    ui.waitUntil(20_000) { ui.onAllNodes(hasContentDescription(description) and isEnabled()).fetchSemanticsNodes().size == 1 }
                                    ui.onNodeWithContentDescription(description).assertIsDisplayed().performClick()
                                } else if (action == Action.LEAVE) {
                                    click("Leave group")
                                }
                            }
                        }
                        if (action.requiresAgreement) {
                            repeat(2) { attempt ->
                                click(action.button, scrollIntoView = action == Action.REACTION)
                                waitFor("Review publishing terms")
                                assertTrue(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                                assertTrue(wallet.exportJson() == wallets.loadExisting().exportJson())
                                if (attempt == 0) {
                                    ui.onNode(hasText("Cancel") and hasClickAction() and
                                        hasAnyAncestor(hasAnyDescendant(hasText("Review publishing terms")))).performClick()
                                    when (action) {
                                        Action.CREATE -> {
                                            assertEditable("Group name", name)
                                            assertEditable("Invitees (optional)", member.publicKey)
                                        }
                                        Action.JOIN -> ui.onNodeWithText("Join").assertIsEnabled()
                                        Action.MESSAGE -> assertEditable("Message", message)
                                        Action.REACTION -> {
                                            ui.withMessageAction("Like") { assertIsEnabled() }
                                            ui.withMessageAction("Unlike") { assertDoesNotExist() }
                                        }
                                        Action.PROMOTION, Action.REMOVAL, Action.LEAVE -> ui.onNodeWithText(action.button).assertIsEnabled()
                                    }
                                    capture("${action.name.lowercase()}-cancel-keeps-work.png")
                                }
                            }
                            click("Accept")
                            waitFor("Agreement saved. New content stays unsent. Queued messages may resume.")
                            assertTrue("Acceptance does not execute the group action", peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                            assertTrue(wallet.exportJson() == wallets.loadExisting().exportJson())
                        }
                        click(action.button, scrollIntoView = action == Action.REACTION)
                        waitFor("Forwarded. Delivery is not confirmed.")
                        assertEquals(1, peer.transactions.size)
                        assertEquals(1, peer.relayedMessages.size)
                        assertEquals(wallet.publicKey, wallets.loadExisting().publicKey)
                        assertEquals(SaitoBackend.OWNER, backends.load())
                        if (existing) assertTrue(wallet.exportJson() == wallets.loadExisting().exportJson())
                        else {
                            val signed = SaitoEnvelope.decode(requireNotNull(raw.get()))
                            val id = signed.body.get(if (action == Action.CREATE) "id" else "group_id").asString
                            assertTrue(id in wallets.loadExisting().namedChatGroupIds())
                        }
                        if (action == Action.REACTION) ui.withMessageAction("Unlike") { performScrollTo().assertIsDisplayed() }
                        if (!action.requiresAgreement) {
                            ui.onNodeWithText("Review publishing terms").assertDoesNotExist()
                            ui.onNodeWithText("Publishing terms are being prepared").assertDoesNotExist()
                        }
                        capture("${action.name.lowercase()}-deliberately-forwarded-${if (action.requiresAgreement) "after-agreement" else "without-content-agreement"}.png")
                        peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, { retained.restoreFiles() },
                { wallets.replace(wallets.loadExisting(), original) }, { backends.save(previousBackend) },
                { retained.restorePreferences() },
                { if (previousPolicy == null) check(!fixture.exists() || fixture.delete()) else fixture.writeBytes(previousPolicy) })
        }
    }

    private fun assertEditable(label: String, text: String) = ui.onNodeWithText(label).assertIsEnabled().assert(
        SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(text)))
    private fun waitFor(text: String) = ui.waitUntil(20_000) { ui.onNodeWithText(text).isDisplayed() }
    private fun click(text: String, scrollIntoView: Boolean = false) {
        val action: SemanticsNodeInteraction.() -> Unit = {
            ui.waitUntil(20_000) { ui.onAllNodes(hasText(text) and isEnabled() and hasClickAction()).fetchSemanticsNodes().size == 1 }
            if (scrollIntoView) performScrollTo().assertIsDisplayed()
            performClick()
        }
        when (text) {
            "Like", "Unlike" -> ui.withMessageAction(text, action)
            "Members", "Leave group" -> ui.withConversationControl(text, action)
            else -> ui.onNodeWithText(text).action()
        }
    }
    private fun capture(name: String) {
        val file = File(context.cacheDir, "agreement-group-evidence/$name")
        captureNativeScreen(file)
        runCatching { File(file.parentFile, file.nameWithoutExtension + ".semantics.txt")
            .writeText(ui.onAllNodes(isRoot(), useUnmergedTree = true).printToString(maxDepth = Int.MAX_VALUE)) }
    }
}
