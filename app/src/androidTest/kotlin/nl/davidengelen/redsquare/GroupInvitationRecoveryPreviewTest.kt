package nl.davidengelen.redsquare

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.google.gson.JsonObject
import nl.davidengelen.redsquare.chat.GroupChatStore
import nl.davidengelen.redsquare.saito.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Base64

/** Unknown imported IDs recover only through an inspected invitation and deliberate signed Join. */
class GroupInvitationRecoveryPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun unavailableGroupsUsePastedInvitationsWithErrorsCancelAndExplicitJoinWithoutChangingNode() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val admin = SaitoWallet.fromPrivateKey("0".repeat(63) + "1").publicKey // Public unsafe fixtures only.
        val sender = SaitoWallet.fromPrivateKey("0".repeat(63) + "2").publicKey
        val ids = listOf("3", "5", "6", "7", "8", "9").map { SaitoWallet.fromPrivateKey("0".repeat(63) + it).publicKey }
        val wallet = ids.fold(SaitoWallet.fromPrivateKey("0".repeat(63) + "4")) { result, id ->
            result.withNamedChatGroup(id, "Untrusted saved name")
        }
        fun invitation(id: String, name: String): String {
            val claims = JsonObject().apply {
                addProperty("id", id); addProperty("name", name); addProperty("sender", sender); addProperty("admin", admin)
            }
            // Literal compatible shape, independent of production link generation. Host must not choose the node.
            return "https://saito.io/chat?chat_id=" + Base64.getEncoder().encodeToString(claims.toString().toByteArray(Charsets.UTF_8))
        }
        val wallets = AndroidWalletStore(context)
        val originalWallet = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context)
        val originalBackend = backends.load()
        val retained = ChatFixtureState(context, SaitoBackend.entries.flatMap { backend -> listOf(
            "chat.${backend.id}.${wallet.publicKey}.", "chatwork.${backend.id}.${wallet.publicKey}.",
            "chatreactions.${backend.id}.${wallet.publicKey}.", "groupchat.${backend.id}.${wallet.publicKey}.")
        } + "personal.${wallet.publicKey}.", wallet.publicKey)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = { "[]" })
        val folder = File(context.cacheDir, "group-invitation-recovery-evidence").apply { mkdirs() }
        var primary: Throwable? = null
        try {
            retained.prepare()
            wallets.replace(originalWallet, wallet)
            backends.save(SaitoBackend.OWNER)
            val groups = GroupChatStore(context, wallet, SaitoBackend.OWNER)
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { activity ->
                    var cell = 0
                    ui.withPostViewports(activity, folder, "group-invitation-recovery") { label, _ ->
                        withFailureCapture({
                            File(folder, "$label-failure.semantics.txt").writeText(
                                ui.onAllNodes(isRoot(), useUnmergedTree = true).printToString(maxDepth = Int.MAX_VALUE))
                        }) {
                            val index = cell++
                            val id = ids[index]
                            val name = "Recovered crew ${index + 1}"
                            val link = invitation(id, name)
                            val before = wallets.loadExisting()
                            val count = peer.transactions.size
                            check(peer.relayedMessages.size == count && groups.load(id) == null)
                            click("Chat")
                            click("Groups")
                            recover(id, ids.size)
                            captureNativeScreen(File(folder, "$label-entry.png"))
                            ui.onNodeWithText("Invitation link").performClick().performTextReplacement("not-an-invitation")
                            physicalPreview()
                            waitFor("This invitation could not be read. Check the link and try again.")
                            captureNativeScreen(File(folder, "$label-invalid-with-ime.png"))
                            assertUnchanged(before, count, peer, groups, id, wallets, backends)
                            ui.onNodeWithText("Invitation link").performTextReplacement(invitation(ids[(index + 1) % ids.size], "Wrong group"))
                            physicalPreview()
                            waitFor("This invitation is for a different group. Use a link for the group you selected.")
                            assertUnchanged(before, count, peer, groups, id, wallets, backends)
                            var previousClip: ClipData? = null
                            ui.runOnIdle { previousClip = clipboard.primaryClip }
                            var clipFailure: Throwable? = null
                            try {
                                ui.runOnIdle { clipboard.setPrimaryClip(ClipData.newPlainText("Fixture invitation", link)) }
                                pasteAndPreview(link)
                                inspectPreview(folder, label, name, admin, sender, id)
                                assertUnchanged(before, count, peer, groups, id, wallets, backends)
                                click("Cancel")
                                assertUnchanged(before, count, peer, groups, id, wallets, backends)
                                recover(id, ids.size)
                                pasteAndPreview(link)
                                waitFor(name)
                                assertUnchanged(before, count, peer, groups, id, wallets, backends)
                                click("Join")
                                ui.waitForGroupStatus("Forwarded. Delivery is not confirmed.")
                                ui.waitUntil(15_000) { peer.transactions.size == count + 1 && peer.relayedMessages.size == count + 1 }
                                val envelope = SaitoEnvelope.fromSignedJson(peer.relayedMessages.last())
                                check(postingTransaction(peer.transactions.last()).envelope() == envelope)
                                val signed = SaitoEnvelope.decode(envelope)
                                check(signed.author == wallet.publicKey && signed.body.asJsonObject.keySet() ==
                                    setOf("module", "request", "group_id", "invited_by"))
                                check(signed.body.get("module").asString == "Chat" && signed.body.get("request").asString == "chat join")
                                check(signed.body.get("group_id").asString == id && signed.body.get("invited_by").asString == sender)
                                check(signed.recipients == listOf(id, wallet.publicKey, admin, sender))
                                check(peer.relayRecipients.last() == listOf(wallet.publicKey, admin))
                                val saved = requireNotNull(groups.load(id))
                                check(saved.group.id == id && saved.group.name == name && saved.group.creationEnvelope == null)
                                check(saved.group.memberRoles == mapOf(admin to ChatGroupRole.ADMIN, wallet.publicKey to ChatGroupRole.MEMBER))
                                check(saved.group.confirmedInvitation?.sender == sender && saved.group.confirmedInvitation?.admin == admin)
                                check(wallets.loadExisting().exportJson() == before.withNamedChatGroup(id, name).exportJson())
                                check(backends.load() == SaitoBackend.OWNER && peer.reports.isEmpty())
                                captureNativeScreen(File(folder, "$label-joined.png"))
                            } catch (failure: Throwable) { clipFailure = failure; throw failure }
                            finally { cleanupAfterTest(clipFailure, { ui.runOnIdle {
                                previousClip?.let(clipboard::setPrimaryClip) ?: clipboard.clearPrimaryClip()
                            } }) }
                            click("Back")
                            waitFor("Create group")
                            click("Back")
                            waitFor("New chat")
                            click("Back")
                            ui.waitUntil(15_000) { ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
                        }
                    }
                }
                check(peer.transactions.size == 6 && peer.relayedMessages.size == 6)
                check(peer.clientKeys.isNotEmpty() && peer.clientKeys.all { it == wallet.publicKey })
                peer.assertHealthy()
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally { cleanupAfterTest(primary, { retained.restoreFiles() },
            { wallets.replace(wallets.loadOrCreate(), originalWallet) }, { backends.save(originalBackend) },
            { retained.restorePreferences() },
            { check(wallets.loadExisting().exportJson() == originalWallet.exportJson() && backends.load() == originalBackend) }) }
    }

    private fun assertUnchanged(before: SaitoWallet, count: Int, peer: ControlledFeedPeer, groups: GroupChatStore,
        id: String, wallets: AndroidWalletStore, backends: AndroidBackendStore) {
        check(wallets.loadExisting().exportJson() == before.exportJson() && backends.load() == SaitoBackend.OWNER)
        check(peer.transactions.size == count && peer.relayedMessages.size == count && groups.load(id) == null)
    }

    private fun recover(id: String, groupCount: Int) {
        ui.waitUntil(15_000) {
            ui.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().singleOrNull()
                ?.config?.getOrNull(SemanticsProperties.CollectionInfo)?.rowCount == groupCount
        }
        val list = ui.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText(id))
        val action = hasContentDescription("Use invitation for $id") and hasClickAction() and isEnabled()
        ui.waitUntil(15_000) { ui.onAllNodes(action).fetchSemanticsNodes().isNotEmpty() }
        list.performScrollToNode(action)
        ui.onNode(action).assertIsDisplayed().performClick()
        waitFor("Paste invitation")
        ui.onNodeWithText("Untrusted saved name").assertDoesNotExist()
    }

    private fun pasteAndPreview(link: String) {
        ui.onNodeWithText("Paste invitation").performScrollTo().assertIsDisplayed().performClick()
        ui.onNodeWithText("Invitation link").assertTextContains(link)
        click("Preview")
    }

    private fun inspectPreview(folder: File, label: String, name: String, admin: String, sender: String, id: String) {
        waitFor(name)
        previewText("Invitation details are provided by the sender.").performScrollTo().assertIsDisplayed()
        previewText("Join on saito.davidengelen.nl").performScrollTo().assertIsDisplayed()
        previewText("Group messages are unencrypted.").performScrollTo().assertIsDisplayed()
        captureNativeScreen(File(folder, "$label-preview.png"))
        for (key in listOf(admin, sender, id)) previewText(key).performScrollTo().assertIsDisplayed()
        captureNativeScreen(File(folder, "$label-preview-identities.png"))
    }

    private fun previewText(text: String) = ui.onNode(hasText(text) and hasAnyAncestor(
        isDialog() and hasAnyDescendant(hasText("Claimed admin"))))

    private fun physicalPreview() {
        ui.waitUntil(10_000) { focusedWindowImeBottom() > 0 }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        var target = requireNotNull(device.findObject(By.text("Preview")))
        while (!target.isClickable) target = requireNotNull(target.parent)
        val bounds = target.visibleBounds
        val minimum = kotlin.math.round(48 * instrumentation.targetContext.resources.displayMetrics.density).toInt()
        check(bounds.width() >= minimum && bounds.height() >= minimum && bounds.left >= 0 && bounds.top >= 0 &&
            bounds.right <= device.displayWidth && bounds.bottom <= device.displayHeight - focusedWindowImeBottom()) {
            "The complete Preview target stays above the real keyboard with at least 48dp visible"
        }
        check(device.click(bounds.centerX(), bounds.centerY()))
    }
    private fun waitFor(text: String) = ui.waitUntil(15_000) { ui.onNodeWithText(text).isDisplayed() }
    private fun click(text: String) {
        val action = ui.onNode(hasText(text) and hasClickAction() and isEnabled())
        ui.waitUntil(15_000) { action.isDisplayed() }
        action.assertIsDisplayed().performClick()
    }
}
