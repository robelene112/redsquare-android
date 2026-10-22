package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import nl.davidengelen.redsquare.chat.ChatHistoryStore
import nl.davidengelen.redsquare.saito.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Individual group selections through the rendered UI and actual signed socket boundary. */
class GroupSetupPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun aNamedGroupUsesKnownAndIndividuallyValidatedRemovableMembersWithLargeTextAndKeyboard() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val known = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixtures only.
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2").withChatContact(known.publicKey)
        val pasted = SaitoWallet.fromPrivateKey("0".repeat(63) + "3").publicKey
        val cached = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
        val wallets = AndroidWalletStore(context)
        val originalWallet = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context)
        val originalBackend = backends.load()
        val retained = ChatFixtureState(context, SaitoBackend.entries.flatMap { backend -> listOf(
            "chat.${backend.id}.${wallet.publicKey}.", "chatwork.${backend.id}.${wallet.publicKey}.",
            "chatreactions.${backend.id}.${wallet.publicKey}.", "groupchat.${backend.id}.${wallet.publicKey}.")
        } + "personal.${wallet.publicKey}.", wallet.publicKey)
        val cachedOriginal = PrivateChat.create(cached, wallet.publicKey, "Keep my existing local conversation.",
            1700000123400L).envelope()
        val history = ChatHistoryStore(context, wallet, SaitoBackend.OWNER, cached.publicKey)
        val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = { "[]" })
        val folder = File(context.cacheDir, "group-setup-evidence").apply { mkdirs() }
        var primary: Throwable? = null
        try {
            retained.prepare()
            wallets.replace(originalWallet, wallet)
            backends.save(SaitoBackend.OWNER)
            check(history.save(listOf(cachedOriginal)).entries.size == 1)
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { activity ->
                    ui.withPostViewports(activity, folder, "group-setup") { label, _ ->
                        withFailureCapture({
                            File(folder, "$label-failure.semantics.txt").writeText(
                                ui.onAllNodes(isRoot(), useUnmergedTree = true).printToString(maxDepth = Int.MAX_VALUE))
                        }) {
                            val before = wallets.loadExisting().exportJson()
                            val previousTransactions = peer.transactions.size
                            val previousRelays = peer.relayedMessages.size
                            click("Chat")
                            click("Groups")
                            click("Create group")
                            waitFor("Add by public key")
                            captureNativeScreen(File(folder, "$label-default.png"))
                            val name = "Selected crew ${label.substringAfter("group-setup-")}"
                            ui.onNodeWithText("Group name").performClick().performTextReplacement(name)
                            // Explicitly dismiss name entry before choosing contacts, never a validation keyboard.
                            androidx.test.espresso.Espresso.closeSoftKeyboard()
                            waitFor("Known contacts")
                            select(known.publicKey)
                            select(cached.publicKey)
                            remove(cached.publicKey)
                            ui.onNodeWithContentDescription("Remove member ${cached.publicKey}").assertDoesNotExist()
                            captureNativeScreen(File(folder, "$label-known-selection.png"))
                            click("Add by public key")
                            waitFor("Add group member")
                            ui.onNodeWithText("Public key").performClick().performTextReplacement("not-a-public-key")
                            physicalAdd()
                            waitFor("Enter a valid public key.")
                            check(wallets.loadExisting().exportJson() == before)
                            check(peer.transactions.size == previousTransactions && peer.relayedMessages.size == previousRelays)
                            captureNativeScreen(File(folder, "$label-invalid-key-with-ime.png"))
                            ui.onNodeWithText("Public key").performTextReplacement(wallet.publicKey)
                            physicalAdd()
                            waitFor("You will be the group admin.")
                            ui.onNodeWithText("Public key").performTextReplacement(known.publicKey)
                            physicalAdd()
                            waitFor("This member is already selected.")
                            check(wallets.loadExisting().exportJson() == before)
                            check(peer.transactions.size == previousTransactions && peer.relayedMessages.size == previousRelays)
                            ui.onNodeWithText("Public key").performTextReplacement("  $pasted  ")
                            physicalAdd()
                            ui.waitUntil(15_000) { ui.onAllNodesWithText("Add group member").fetchSemanticsNodes().isEmpty() }
                            remove(pasted)
                            ui.onNodeWithContentDescription("Remove member $pasted").assertDoesNotExist()
                            click("Add by public key")
                            waitFor("Add group member")
                            ui.onNodeWithText("Public key").performClick().performTextReplacement(pasted)
                            physicalAdd()
                            ui.waitUntil(15_000) { ui.onAllNodesWithText("Add group member").fetchSemanticsNodes().isEmpty() }
                            memberAction("Remove member $pasted").assertIsDisplayed()
                            memberAction("Remove member ${known.publicKey}").assertIsDisplayed()
                            check(wallets.loadExisting().exportJson() == before)
                            check(peer.transactions.size == previousTransactions && peer.relayedMessages.size == previousRelays)
                            captureNativeScreen(File(folder, "$label-ready-to-create.png"))
                            click("Create")
                            ui.waitForGroupStatus("Forwarded. Delivery is not confirmed.")
                            ui.waitUntil(15_000) {
                                peer.transactions.size == previousTransactions + 1 && peer.relayedMessages.size == previousRelays + 1
                            }
                            val envelope = SaitoEnvelope.fromSignedJson(peer.relayedMessages.last())
                            check(postingTransaction(peer.transactions.last()).envelope() == envelope)
                            val group = GroupChat.readCreation(envelope)
                            check(group.name == name && group.memberRoles == mapOf(wallet.publicKey to ChatGroupRole.ADMIN,
                                known.publicKey to ChatGroupRole.MEMBER, pasted to ChatGroupRole.MEMBER))
                            check(cached.publicKey !in group.memberRoles &&
                                peer.relayRecipients.last().toSet() == setOf(wallet.publicKey, known.publicKey, pasted))
                            ui.withConversationControl("Members") { assertIsDisplayed().performClick() }
                            val memberList = ui.onNode(hasScrollAction() and hasAnyAncestor(isDialog()))
                            memberList.performScrollToNode(hasText(known.publicKey))
                            ui.onNodeWithText(known.publicKey).assertIsDisplayed()
                            memberList.performScrollToNode(hasText(pasted))
                            ui.onNodeWithText(pasted).assertIsDisplayed()
                            ui.onNodeWithText(cached.publicKey).assertDoesNotExist()
                            captureNativeScreen(File(folder, "$label-created-members.png"))
                            click("Done")
                            check(wallets.loadExisting().publicKey == wallet.publicKey &&
                                group.id in wallets.loadExisting().namedChatGroupIds())
                            check(history.load().entries.map { it.envelope } == listOf(cachedOriginal))
                            check(backends.load() == SaitoBackend.OWNER && peer.reports.isEmpty())
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
        finally {
            cleanupAfterTest(primary, { retained.restoreFiles() },
                { wallets.replace(wallets.loadOrCreate(), originalWallet) }, { backends.save(originalBackend) },
                { retained.restorePreferences() },
                { check(wallets.loadExisting().exportJson() == originalWallet.exportJson() && backends.load() == originalBackend) })
        }
    }

    private fun select(key: String) {
        memberAction("Select member $key").assertIsOff().performClick().assertIsOn()
    }

    private fun memberAction(description: String): SemanticsNodeInteraction {
        ui.onNodeWithContentDescription("Group setup").performScrollToNode(hasContentDescription(description))
        return ui.onNodeWithContentDescription(description, useUnmergedTree = true)
    }

    private fun remove(key: String) = memberAction("Remove member $key").assertIsEnabled().performClick()

    private fun waitFor(text: String) = ui.waitUntil(15_000) { ui.onNodeWithText(text).isDisplayed() }

    private fun click(text: String) {
        val matcher = hasText(text) and hasClickAction() and isEnabled()
        val target = ui.onNode(matcher)
        if (!target.isDisplayed() && ui.onNodeWithContentDescription("Group setup").isDisplayed())
            ui.onNodeWithContentDescription("Group setup").performScrollToNode(matcher)
        ui.waitUntil(15_000) { target.isDisplayed() }
        target.assertIsDisplayed().performClick()
    }


    private fun physicalAdd() {
        waitFor("Add member")
        ui.waitUntil(10_000) { focusedWindowImeBottom() > 0 }
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        var target = requireNotNull(device.findObject(By.text("Add member")))
        while (!target.isClickable) target = requireNotNull(target.parent)
        val bounds = target.visibleBounds
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val minimum = kotlin.math.round(48 * density).toInt()
        check(bounds.width() >= minimum && bounds.height() >= minimum && bounds.top >= 0 &&
            bounds.bottom <= device.displayHeight - focusedWindowImeBottom()) { "The complete Add member target stays above the real keyboard" }
        check(device.click(bounds.centerX(), bounds.centerY()))
    }
}
