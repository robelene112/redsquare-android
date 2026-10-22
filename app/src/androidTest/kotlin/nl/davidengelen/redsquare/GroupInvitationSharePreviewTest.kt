package nl.davidengelen.redsquare

import android.app.Activity
import android.app.Instrumentation
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.chat.GroupChatStore
import nl.davidengelen.redsquare.saito.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Actual device clipboard and Android chooser boundary, without delivering an invitation externally. */
class GroupInvitationSharePreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Suppress("DEPRECATION")
    @Test fun aGroupMemberCanCopyAndShareTheExactCompatibleInvitationAtEveryTextSize() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val reference = instrumentation.context.assets.open("native-group-chat-creation-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val vector = reference.getAsJsonArray("vectors")[0].asJsonObject
        val invitation = reference.getAsJsonArray("invitations")[0].asJsonObject
        val expectedLink = invitation.get("link").asString
        val groupId = vector.get("groupId").asString
        val groupName = vector.get("name").asString
        val originalEnvelope = vector.get("envelope").asString
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixture only.
            .withNamedChatGroup(groupId, groupName)
        check(wallet.publicKey == invitation.get("admin").asString)
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
        val folder = File(context.cacheDir, "group-invitation-share-evidence").apply { mkdirs() }
        var primary: Throwable? = null
        Intents.init()
        try {
            Intents.intending(hasAction(Intent.ACTION_CHOOSER)).respondWith(
                Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
            retained.prepare()
            wallets.replace(originalWallet, wallet)
            backends.save(SaitoBackend.OFFICIAL)
            val groups = GroupChatStore(context, wallet, SaitoBackend.OFFICIAL)
            groups.receive(originalEnvelope)
            val originalGroup = requireNotNull(groups.load(groupId)).group
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { activity ->
                    ui.withPostViewports(activity, folder, "group-invitation-share") { label, _ ->
                        withFailureCapture({
                            File(folder, "$label-failure.semantics.txt").writeText(
                                ui.onAllNodes(isRoot(), useUnmergedTree = true).printToString(maxDepth = Int.MAX_VALUE))
                        }) {
                            click("Chat")
                            click("Groups")
                            click(groupName)
                            ui.withConversationControl("Invite") { assertIsDisplayed().assertIsEnabled().performClick() }
                            waitFor("Copy invitation")
                            captureNativeScreen(File(folder, "$label-default.png"))
                            var previousClip: ClipData? = null
                            ui.runOnIdle { previousClip = clipboard.primaryClip }
                            var copyFailure: Throwable? = null
                            try {
                                val copies = Intents.getIntents().count { it.action == Intent.ACTION_CHOOSER }
                                action("Copy invitation").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
                                waitFor("Invitation copied.")
                                ui.runOnIdle {
                                    val copied = requireNotNull(clipboard.primaryClip)
                                    check(copied.itemCount == 1 && copied.getItemAt(0).text.toString() == expectedLink)
                                }
                                captureNativeScreen(File(folder, "$label-copied.png"))
                                action("Share invitation").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
                                ui.waitUntil(15_000) {
                                    Intents.getIntents().count { it.action == Intent.ACTION_CHOOSER } == copies + 1
                                }
                                val chooser = Intents.getIntents().last { it.action == Intent.ACTION_CHOOSER }
                                val shared = requireNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
                                check(shared.action == Intent.ACTION_SEND && shared.type == "text/plain")
                                check(shared.getStringExtra(Intent.EXTRA_TEXT) == expectedLink)
                                check(shared.extras?.keySet() == setOf(Intent.EXTRA_TEXT) && shared.data == null && shared.clipData == null)
                                waitFor("Share invitation")
                                captureNativeScreen(File(folder, "$label-share-return.png"))
                            } catch (failure: Throwable) { copyFailure = failure; throw failure }
                            finally {
                                cleanupAfterTest(copyFailure, { ui.runOnIdle {
                                    previousClip?.let(clipboard::setPrimaryClip) ?: clipboard.clearPrimaryClip()
                                } })
                            }
                            check(wallets.loadExisting().exportJson() == wallet.exportJson())
                            check(backends.load() == SaitoBackend.OFFICIAL)
                            val saved = requireNotNull(groups.load(groupId))
                            check(saved.group.creationEnvelope == originalEnvelope && saved.group.memberRoles == originalGroup.memberRoles)
                            check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty() && peer.reports.isEmpty())
                            click("Done")
                            click("Back")
                            waitFor("Create group")
                            click("Back")
                            waitFor("New chat")
                            click("Back")
                            ui.waitUntil(15_000) { ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
                        }
                    }
                }
                check(peer.clientKeys.isNotEmpty() && peer.clientKeys.all { it == wallet.publicKey })
                check(Intents.getIntents().count { it.action == Intent.ACTION_CHOOSER } == 6)
                peer.assertHealthy()
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, { Intents.release() }, { retained.restoreFiles() },
                { wallets.replace(wallets.loadOrCreate(), originalWallet) }, { backends.save(originalBackend) },
                { retained.restorePreferences() },
                { check(wallets.loadExisting().exportJson() == originalWallet.exportJson() && backends.load() == originalBackend) })
        }
    }

    private fun action(text: String) = ui.onNode(hasText(text) and hasClickAction() and isEnabled())
    private fun waitFor(text: String) = ui.waitUntil(15_000) { ui.onNodeWithText(text).isDisplayed() }
    private fun click(text: String) {
        ui.waitUntil(15_000) { action(text).isDisplayed() }
        action(text).assertIsDisplayed().performClick()
    }
}
