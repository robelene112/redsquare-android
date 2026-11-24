package nl.davidengelen.redsquare

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Real Activity, authenticated socket and independent host client. The host
 * driver terminates the first Android PID after a durable checkpoint. */
class SpacesJourneyTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext.also {
        check(it.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
    }
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val peerPort get() = requireNotNull(arguments.getString("spacesPeerPort")).toInt().also { check(it in 1024..65535) }
    private val controlPort get() = requireNotNull(arguments.getString("spacesControlPort")).toInt().also { check(it in 1024..65535) }
    private val evidence get() = File(context.cacheDir, "spaces-journey-evidence").apply { mkdirs() }
    private val checkpoint get() = File(context.cacheDir, "spaces-journey-checkpoint.json")
    private val community = "Independent voices"
    private val originalText = "Original awaiting Android restart"
    private val draft = "Unsent writing survives Android restart"
    private fun launch() = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
        .putExtra("feedPeerUrl", "ws://127.0.0.1:$peerPort/wsopen")
        .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:$peerPort/wsopen")
        .putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/"))

    @Test fun messageMenusAndEditSurviveIncomingMessagesAndRealIme() {
        val original = "Native overlay original"
        val edited = "Preserved native edit while the live conversation moves"
        val reactionText = "Native reaction details original"
        launch().use { activity -> withFailureCapture({ capture("overlay-failure") }) {
            openSpaces("Red Square")
            ui.waitUntil(15_000) { ui.onNode(hasText("Create a Space") and hasClickAction() and isEnabled()).isDisplayed() }
            ui.onNode(hasText("Create a Space") and hasClickAction()).performClick()
            ui.onNodeWithText("Space name").performTextInput("Stable native message overlays")
            closeKeyboard(activity)
            listOf("announcements", "the lounge", "reading room", "resources").forEach { name ->
                ui.onNodeWithContentDescription("Remove $name").performScrollTo().performClick()
            }
            ui.onNode(hasText("Create a Space") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Message general").isDisplayed() }
            ui.onNodeWithContentDescription("Switch Space or channel").performClick()
            ui.waitUntil(5_000) { ui.onNode(hasText("Invite people") and hasClickAction() and isEnabled()).isDisplayed() }
            ui.onNode(hasText("Invite people") and hasClickAction() and isEnabled()).performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("Copy invitation") and hasClickAction() and isEnabled()).isDisplayed() }
            ui.onNode(hasText("Copy invitation") and hasClickAction() and isEnabled()).performClick()
            ui.waitUntil(5_000) { ui.onNodeWithText("Invitation copied").isDisplayed() }
            var link = ""
            activity.onActivity { link = (it.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text.toString() }
            val joined = command("join", JsonObject().apply { addProperty("link", link) })
            val space = joined.get("space").asString
            UiDevice.getInstance(instrumentation).pressBack()
            ui.waitUntil(20_000) { ui.onNodeWithText("Reply from independent member").isDisplayed() }
            // Keep the reviewed message at the live edge. Opening an older
            // scrolled window would correctly retain it under incoming updates.
            ui.onNodeWithText("Message general").performTextInput(original)
            ui.waitUntil(10_000) { ui.onNode(hasContentDescription("Send message") and isEnabled()).isDisplayed() }
            ui.onNodeWithContentDescription("Send message").performClick(); closeKeyboard(activity)
            ui.waitUntil(15_000) { ui.onNodeWithText(original).isDisplayed() }
            var remembered = JsonObject()
            ui.waitUntil(15_000) {
                try { remembered = command("message-observation", JsonObject().apply { addProperty("space", space); addProperty("text", original) }); true }
                catch (_: AssertionError) { false }
            }
            fun rowAction(text: String, description: String) = ui.onNode(hasText(description) and hasClickAction() and hasAnyAncestor(
                hasTestTag("spaces-message") and hasAnyDescendant(hasText(text))))
            ui.waitUntil(5_000) { rowAction(original, "Message actions").isDisplayed() }
            rowAction(original, "Message actions").assertIsEnabled().performClick()
            ui.waitUntil(5_000) { ui.onNode(hasText("Edit message") and hasClickAction() and isEnabled() and hasAnyAncestor(isDialog())).isDisplayed() }
            capture("overlay-01-actions")
            command("populate", JsonObject().apply { addProperty("space", space); addProperty("from", 0); addProperty("count", 60) })
            ui.waitUntil(20_000) { ui.onAllNodesWithText(original).fetchSemanticsNodes().isEmpty() }
            ui.onNode(hasText("Edit message") and hasClickAction() and isEnabled() and hasAnyAncestor(isDialog())).assertIsDisplayed().performClick()
            fun editor() = ui.onNode(hasText("Message") and hasSetTextAction() and hasAnyAncestor(isDialog()))
            ui.waitUntil(5_000) { editor().isDisplayed() }
            editor().performClick()
            ui.waitUntil(5_000) { focusedWindowImeBottom() > 0 }
            // Establish the real input connection before replacing its text.
            // Android 10 can still recreate that connection on first focus.
            editor().performTextReplacement(edited)
            ui.waitUntil(5_000) {
                editor().fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text == edited &&
                    focusedWindowImeBottom() > 0
            }
            val ime = focusedWindowImeBottom()
            capture("overlay-02-real-ime", edited)
            val incoming = command("populate", JsonObject().apply { addProperty("space", space); addProperty("from", 60); addProperty("count", 8) })
            var consumed = JsonObject()
            ui.waitUntil(15_000) {
                consumed = command("message-consumption", incoming)
                consumed.get("observed").asBoolean
            }
            assertEquals(AndroidWalletStore(context).loadOrCreate().publicKey, consumed.get("author").asString)
            editor().assertIsDisplayed()
            assertEquals(edited, editor().fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text)
            assertTrue("Actual keyboard remains open during incoming messages", focusedWindowImeBottom() > 0)
            UiDevice.getInstance(instrumentation).pressBack()
            ui.waitUntil(5_000) { focusedWindowImeBottom() == 0 && editor().isDisplayed() }
            activity.recreate()
            ui.waitUntil(15_000) { editor().isDisplayed() && editor().fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text == edited }
            capture("overlay-03-recreated-edit")
            ui.onNode(hasText("Save changes") and hasClickAction() and hasAnyAncestor(isDialog())).performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onAllNodes(hasText("Message") and hasSetTextAction() and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isEmpty() }
            var observed = JsonObject()
            ui.waitUntil(15_000) {
                observed = command("message-observation", remembered.deepCopy().apply { addProperty("space", space) })
                observed.get("text").asString == edited && observed.get("edited").asBoolean
            }
            assertEquals(remembered.get("id").asString, observed.get("id").asString)
            ui.onNodeWithText("Message general").performTextInput(reactionText)
            ui.waitUntil(10_000) { ui.onNode(hasContentDescription("Send message") and isEnabled()).isDisplayed() }
            ui.onNodeWithContentDescription("Send message").performClick(); closeKeyboard(activity)
            ui.waitUntil(15_000) { ui.onNodeWithText(reactionText).isDisplayed() }
            var reactionMessage = JsonObject()
            ui.waitUntil(15_000) {
                try { reactionMessage = command("react-to-message", JsonObject().apply { addProperty("space", space); addProperty("text", reactionText) }); true }
                catch (_: AssertionError) { false }
            }
            ui.waitUntil(15_000) { ui.onNode(hasText(context.getString(R.string.spaces_all_reactions)) and hasClickAction()).isDisplayed() }
            ui.onNode(hasText(context.getString(R.string.spaces_all_reactions)) and hasClickAction()).performClick()
            ui.waitUntil(10_000) { ui.onNode(hasText("Reactions") and hasAnyAncestor(isDialog())).isDisplayed() }
            capture("overlay-04-reaction-details")
            command("populate", JsonObject().apply { addProperty("space", space); addProperty("from", 68); addProperty("count", 60) })
            ui.waitUntil(20_000) { ui.onAllNodesWithText(reactionText).fetchSemanticsNodes().isEmpty() }
            ui.onNode(hasText("Reactions") and hasAnyAncestor(isDialog())).assertIsDisplayed()
            ui.onNode(hasText("❤️") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
            var reactionObserved = JsonObject()
            ui.waitUntil(15_000) {
                reactionObserved = command("message-observation", reactionMessage.deepCopy().apply { addProperty("space", space) })
                reactionObserved.getAsJsonArray("reactions").any { it.asJsonObject.get("emoji").asString == "❤️" && it.asJsonObject.get("count")?.asLong == 2L }
            }
            val confirmedHeart = hasText("❤️") and hasText("2") and isSelected() and hasAnyAncestor(isDialog())
            ui.waitUntil(5_000) { ui.onNode(confirmedHeart).isDisplayed() }
            capture("overlay-05-reaction-after-row-disposal")
            ui.onNode(hasText("Close") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
            ui.waitUntil(5_000) { ui.onAllNodes(hasText("Reactions") and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isEmpty() }
            // Establish an actual reading gesture before aligning the exact row.
            visibleSpacesList().performTouchInput { swipeDown(durationMillis = 350) }
            ui.waitForIdle()
            visibleSpacesList().performScrollToNode(hasText("Fixture line 100"))
            ui.waitUntil(5_000) { ui.onNodeWithText("Fixture line 100").isDisplayed() }
            val returnToLatest = hasText("Back to latest messages") and hasClickAction()
            val newerActivity = newerMessagesAvailable()
            ui.onNode(returnToLatest).assertIsDisplayed()
            val readingBefore = ui.onNodeWithText("Fixture line 100").fetchSemanticsNode().boundsInRoot.top
            capture("overlay-06-deliberate-reading-position", "Fixture line 100")
            val readingIncoming = command("populate", JsonObject().apply { addProperty("space", space); addProperty("from", 0); addProperty("count", 8) })
            var readingConsumption = JsonObject()
            ui.waitUntil(20_000) {
                if (readingConsumption.get("observed")?.asBoolean != true)
                    readingConsumption = command("message-consumption", readingIncoming)
                readingConsumption.get("observed").asBoolean && ui.onNode(returnToLatest and newerActivity).isDisplayed()
            }
            assertEquals(consumed.get("author").asString, readingConsumption.get("author").asString)
            val readingAfter = ui.onNodeWithText("Fixture line 100").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
            assertEquals("Incoming activity preserves deliberate older reading", readingBefore, readingAfter, 1f)
            capture("overlay-07-incoming-retains-reading-position", "Fixture line 100")
            val moderated = command("message-observation", JsonObject().apply {
                addProperty("space", space); addProperty("text", "Fixture line 100")
            })
            rowAction("Fixture line 100", "Message actions").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
            val removeChoice = hasText("Delete message") and hasClickAction() and hasAnyAncestor(isDialog())
            ui.waitUntil(5_000) { ui.onNode(removeChoice).isDisplayed() }
            ui.onNode(removeChoice).performClick()
            val deletionDetail = context.getString(R.string.spaces_delete_message_detail)
            ui.waitUntil(5_000) { ui.onNodeWithText(deletionDetail).isDisplayed() && ui.onAllNodes(removeChoice).fetchSemanticsNodes().size == 1 }
            ui.onNode(removeChoice and isEnabled()).assertIsDisplayed().performClick()
            val moderatedId = moderated.get("id").asString
            val placeholder = ui.onNode(hasTestTag("spaces-deleted-$moderatedId") and hasText("Message deleted"))
            var deleted = JsonObject()
            ui.waitUntil(15_000) {
                if (deleted.get("deleted")?.asBoolean != true)
                    deleted = command("message-observation", moderated.deepCopy().apply { addProperty("space", space) })
                deleted.get("deleted").asBoolean && placeholder.isDisplayed()
            }
            assertEquals(moderatedId, deleted.get("id").asString)
            ui.onNodeWithText("Fixture line 100").assertDoesNotExist()
            capture("overlay-08-moderated-placeholder", "Message deleted")
            File(evidence, "overlay-results.json").writeText(JsonObject().apply {
                addProperty("pid", Process.myPid()); addProperty("imeBottom", ime)
                add("editedMessage", observed); add("reactedMessage", reactionObserved)
                addProperty("readingBefore", readingBefore); addProperty("readingAfter", readingAfter)
                add("readingIncoming", readingIncoming); add("readingConsumption", readingConsumption)
                add("deletedMessage", deleted)
                addProperty("activityRecreated", true); addProperty("incomingBeforeEdit", 60); add("incomingDuringIme", incoming); add("androidConsumptionWhileImeOpen", consumed); addProperty("incomingDuringDetails", 60)
            }.toString())
        } }
    }

    @Test fun pinsOpenTheirExactRootAndThreadWhilePreservingWriting() {
        val rootText = "A real native pinned reference"
        val replyText = "A real pinned thread reply"
        val channelDraft = "Keep my channel writing while opening a pin"
        val threadDraft = "Keep my thread writing while opening another pin"
        launch().use { activity -> withFailureCapture({ capture("pins-failure") }) {
            openSpaces("Red Square")
            ui.onNode(hasText("My Spaces") and hasClickAction()).performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("Create a Space") and hasClickAction() and isEnabled()).isDisplayed() }
            ui.onNode(hasText("Create a Space") and hasClickAction()).performClick()
            ui.onNodeWithText("Space name").performTextInput("Native pinned conversations")
            closeKeyboard(activity)
            listOf("announcements", "the lounge", "reading room", "resources").forEach { name ->
                ui.onNodeWithContentDescription("Remove $name").performScrollTo().performClick()
            }
            ui.onNode(hasText("Create a Space") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Message general").isDisplayed() }
            ui.onNodeWithContentDescription("Switch Space or channel").performClick()
            ui.waitUntil(5_000) { ui.onNode(hasText("Invite people") and hasClickAction() and isEnabled()).isDisplayed() }
            ui.onNode(hasText("Invite people") and hasClickAction()).performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("Copy invitation") and hasClickAction() and isEnabled()).isDisplayed() }
            ui.onNode(hasText("Copy invitation") and hasClickAction()).performClick()
            ui.waitUntil(5_000) { ui.onNodeWithText("Invitation copied").isDisplayed() }
            var link = ""
            activity.onActivity { link = (it.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text.toString() }
            val space = command("join", JsonObject().apply { addProperty("link", link) }).get("space").asString
            UiDevice.getInstance(instrumentation).pressBack()
            ui.waitUntil(20_000) { ui.onNodeWithText("Reply from independent member").isDisplayed() }
            fun rowAction(text: String, label: String) = ui.onNode(hasText(label) and hasClickAction() and hasAnyAncestor(
                hasTestTag("spaces-message") and hasAnyDescendant(hasText(text))))
            fun observation(text: String, parent: String? = null): JsonObject = command("message-observation", JsonObject().apply {
                addProperty("space", space); addProperty("text", text); parent?.let { addProperty("parent", it) }
            })
            fun send(text: String, placeholder: String) {
                ui.onNodeWithText(placeholder).performTextInput(text)
                ui.waitUntil(10_000) { ui.onNode(hasContentDescription("Send message") and isEnabled()).isDisplayed() }
                ui.onNodeWithContentDescription("Send message").performClick(); closeKeyboard(activity)
                ui.waitUntil(15_000) { ui.onNodeWithText(text).isDisplayed() }
            }
            fun pin(text: String) {
                ui.waitUntil(5_000) { rowAction(text, "Message actions").isDisplayed() }
                rowAction(text, "Message actions").performClick()
                ui.waitUntil(5_000) { ui.onNode(hasText("Pin message") and hasClickAction() and hasAnyAncestor(isDialog())).isDisplayed() }
                ui.onNode(hasText("Pin message") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
            }
            send(rootText, "Message general")
            var root = JsonObject()
            ui.waitUntil(15_000) { try { root = observation(rootText); true } catch (_: AssertionError) { false } }
            pin(rootText)
            ui.waitUntil(15_000) { root = observation(rootText); root.get("pinned").asBoolean && !root.get("pinPending").asBoolean }
            ui.onNodeWithText("Message general").performTextInput(channelDraft); closeKeyboard(activity)
            ui.onNode(hasText("Pinned messages") and hasClickAction()).performClick()
            ui.waitUntil(5_000) { ui.onNode(hasText(rootText) and hasAnyAncestor(isDialog())).isDisplayed() }
            capture("pins-01-root-list")
            val incomingPins = command("populate", JsonObject().apply { addProperty("space", space); addProperty("count", 60) })
            var pinsConsumed = JsonObject()
            ui.waitUntil(15_000) {
                pinsConsumed = command("message-consumption", incomingPins.deepCopy())
                pinsConsumed.get("observed").asBoolean && ui.onAllNodes(hasText(rootText) and !hasAnyAncestor(isDialog())).fetchSemanticsNodes().isEmpty()
            }
            ui.onNode(hasText(rootText) and hasAnyAncestor(isDialog())).assertIsDisplayed()
            activity.recreate()
            ui.waitUntil(15_000) { ui.onNode(hasText(rootText) and hasAnyAncestor(isDialog())).isDisplayed() }
            capture("pins-01b-restored-route")
            ui.onNode(hasText("Open message") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
            ui.waitUntil(10_000) { ui.onNodeWithText(rootText).isDisplayed() && ui.onNodeWithText(channelDraft).isDisplayed() }
            rowAction(rootText, "Reply").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
            ui.waitUntil(10_000) { ui.onNodeWithText("Reply in this thread").isDisplayed() }
            send(replyText, "Reply in this thread")
            var reply = JsonObject()
            ui.waitUntil(15_000) {
                // The independent wallet may see acceptance before Android receives
                // its own receipt. Wait for both boundaries without polling the
                // independent wallet again after it has verified the original.
                if (!reply.has("id")) {
                    try { reply = observation(replyText, root.get("id").asString) } catch (_: AssertionError) { }
                }
                reply.has("id") && rowAction(replyText, "Message actions").isDisplayed()
            }
            pin(replyText)
            val pinnedReply = ui.onNodeWithText(replyText)
            fun replyFullyVisible() = pinnedReply.isDisplayed() &&
                pinnedReply.fetchSemanticsNode().let { it.boundsInRoot.height >= it.size.height }
            ui.waitUntil(15_000) {
                if (reply.get("pinned")?.asBoolean != true) reply = observation(replyText, root.get("id").asString)
                reply.get("pinned").asBoolean && ui.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() && replyFullyVisible()
            }
            capture("pins-01c-pinned-reply-stays-readable", replyText)
            ui.onNodeWithText("Reply in this thread").performTextInput(threadDraft); closeKeyboard(activity)
            ui.onNode(hasText("Pinned messages") and hasClickAction()).performClick()
            ui.waitUntil(5_000) { ui.onNode(hasText(replyText) and hasAnyAncestor(isDialog())).isDisplayed() }
            capture("pins-02-root-and-thread-list")
            ui.onNode(hasText("Open reply in thread") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
            ui.waitUntil(10_000) { replyFullyVisible() && ui.onNodeWithText(threadDraft).isDisplayed() }
            capture("pins-03-exact-thread-draft", replyText)
            ui.onNode(hasText("Back to channel") and hasClickAction()).performClick()
            ui.waitUntil(10_000) { ui.onNodeWithText(channelDraft).isDisplayed() }
            capture("pins-04-channel-draft-restored")
            File(evidence, "pins-results.json").writeText(JsonObject().apply {
                addProperty("pid", Process.myPid()); addProperty("space", space); add("root", root); add("reply", reply)
                addProperty("channelDraftPreserved", true); addProperty("threadDraftPreserved", true); addProperty("incomingWhileOpen", 60)
                addProperty("pinSheetRestoredAfterRecreate", true)
                add("androidConsumptionWhilePinsOpen", pinsConsumed)
            }.toString())
        } }
    }

    @Test fun unreadBadgeDistinguishesOrdinaryMessagesFromPersonalMentions() {
        val spaceName = "Native pinned conversations"
        val space = JsonParser.parseString(File(evidence, "pins-results.json").readText()).asJsonObject.get("space").asString
        launch().use { activity -> withFailureCapture({ capture("attention-failure") }) {
            openSpaces("Red Square")
            ui.onNode(hasText("My Spaces") and hasClickAction()).performClick()
            ui.waitUntil(15_000) { communityRow(spaceName).isDisplayed() }
            communityRow(spaceName).assertIsEnabled().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Keep my channel writing while opening a pin").isDisplayed() }
            val latest = ui.onNode(hasText("Back to latest messages") and hasClickAction())
            if (latest.isDisplayed()) latest.performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Fixture line 59").isDisplayed() }
            ui.onNode(hasText("Community information") and hasClickAction()).performClick()
            val silentChoice = hasText("Silent") and isSelectable() and hasAnyAncestor(isDialog())
            val silent = ui.onNode(silentChoice)
            val personal = ui.onNode(hasText("Mentions and replies") and isSelectable() and hasAnyAncestor(isDialog()))
            personal.performScrollTo().assertIsSelected()
            silent.performScrollTo()
            val enabledSilent = ui.onNode(silentChoice and isEnabled())
            ui.waitUntil(5_000) { enabledSilent.isDisplayed() }
            enabledSilent.performClick()
            ui.waitUntil(5_000) { ui.onNode(hasText("Silent") and isSelected() and hasAnyAncestor(isDialog())).isDisplayed() }
            capture("attention-01-channel-preferences")
            ui.onNode(hasText("Close") and hasClickAction() and hasAnyAncestor(isDialog())).performScrollTo().assertIsDisplayed().performClick()
            ui.waitUntil(5_000) { ui.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
            backToCommunities()
            ui.waitUntil(10_000) { communityRow(spaceName).isDisplayed() }
            fun badge(label: String) = ui.onNode(hasContentDescription(label) and hasAnyAncestor(onScreenCommunity(spaceName)),
                useUnmergedTree = true)
            val unread = badge("Unread activity")
            ui.waitUntil(15_000) { !unread.isDisplayed() }
            fun send(mention: Boolean) = command("attention-message", JsonObject().apply {
                addProperty("space", space); addProperty("mention", mention)
            })
            val ordinary = send(false)
            ui.waitUntil(15_000) { unread.isDisplayed() }
            capture("attention-02-ordinary-dot")
            val mention = send(true)
            val count = badge("1 unread personal mentions and replies")
            ui.waitUntil(15_000) { count.isDisplayed() }
            capture("attention-03-personal-number")
            communityRow(spaceName).performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText(mention.get("text").asString).isDisplayed() }
            backToCommunities()
            ui.waitUntil(15_000) { communityRow(spaceName).isDisplayed() && !count.isDisplayed() && !unread.isDisplayed() }
            activity.recreate()
            ui.waitUntil(15_000) { communityRow(spaceName).isDisplayed() }
            communityRow(spaceName).performClick()
            ui.waitUntil(10_000) { ui.onNode(hasText("Community information") and hasClickAction()).isDisplayed() }
            ui.onNode(hasText("Community information") and hasClickAction()).performClick()
            ui.waitUntil(5_000) { silent.isDisplayed() || ui.onAllNodes(hasText("Silent") and isSelectable() and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isNotEmpty() }
            silent.performScrollTo().assertIsSelected()
            File(evidence, "attention-results.json").writeText(JsonObject().apply {
                add("ordinary", ordinary); add("mention", mention)
                addProperty("ordinaryDot", true); addProperty("personalCount", 1)
                addProperty("acknowledgedAfterReading", true); addProperty("silentRestored", true)
            }.toString())
        } }
    }

    @Test fun createInviteJoinReadAndCheckpointAnUncertainOriginal() {
        check(!checkpoint.exists())
        val wallet = AndroidWalletStore(context).loadOrCreate()
        val backend = AndroidBackendStore(context).load()
        val originalWallet = wallet.exportJson()
        launch().use { activity -> withFailureCapture({ capture("native-failure") }) {
            openSpaces("Red Square")
            ui.waitUntil(15_000) { ui.onNode(hasText("Create a Space") and hasClickAction() and isEnabled()).isDisplayed() }
            capture("01-communities")
            ui.onNode(hasText("Create a Space") and hasClickAction()).performClick()
            ui.onNodeWithText("Space name").performTextInput(community)
            closeKeyboard(activity)
            // Retain the original137 single-channel goals and original deadlines.
            listOf("announcements", "the lounge", "reading room", "resources").forEach { name ->
                ui.onNodeWithContentDescription("Remove $name").performScrollTo().performClick()
            }
            ui.onNode(hasText("Create a Space") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Message general").isDisplayed() && ui.onNode(hasSetTextAction() and isEnabled()).isDisplayed() }
            ui.onNodeWithText("Message general").performTextInput("Hello from the native Android client")
            ui.waitUntil(10_000) { ui.onNode(hasContentDescription("Send message") and isEnabled()).isDisplayed() }
            ui.onNodeWithContentDescription("Send message").performClick()
            closeKeyboard(activity)
            ui.waitUntil(15_000) { ui.onNodeWithText("Hello from the native Android client").isDisplayed() }
            ui.onNodeWithContentDescription("Switch Space or channel").performClick()
            val invite = ui.onNode(hasText("Invite people") and hasClickAction() and isEnabled())
            ui.waitUntil(5_000) { ui.onNodeWithText("Your communities").isDisplayed() && invite.isDisplayed() }
            capture("02-drawer-b", "Your communities")
            invite.performClick()
            val copy = ui.onNode(hasText("Copy invitation") and hasClickAction() and isEnabled())
            ui.waitUntil(15_000) { copy.isDisplayed() }
            copy.performClick()
            ui.waitUntil(5_000) { ui.onNodeWithText("Invitation copied").isDisplayed() }
            var link = ""
            activity.onActivity {
                val clipboard = it.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                link = clipboard.primaryClip?.getItemAt(0)?.text.toString()
                assertTrue(clipboard.primaryClipDescription?.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true)
            }
            assertTrue(link.startsWith("saito://spaces/join#"))
            capture("03-confirmed-invitation")
            val joined = command("join", JsonObject().apply { addProperty("link", link) })
            assertTrue(joined.get("previewDidNotJoin").asBoolean)
            UiDevice.getInstance(instrumentation).pressBack()
            ui.waitUntil(20_000) { ui.onNodeWithText("Reply from independent member").isDisplayed() }
            val status = command("status")
            assertTrue(status.getAsJsonArray("communities").any { row ->
                row.asJsonObject.getAsJsonArray("received").any { it.asString == "Hello from the native Android client" }
            })
            capture("04-independent-received-reply")
            ui.onNode(hasText("Community information") and hasClickAction()).assertIsDisplayed().performClick()
            ui.waitUntil(5_000) { ui.onNodeWithText("You are a member of this community.").isDisplayed() }
            capture("04e-community-information", "You are a member of this community.")
            val closeInformation = ui.onNode(hasText("Close") and hasClickAction() and hasAnyAncestor(isDialog()))
            closeInformation.performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
            ui.waitUntil(5_000) { ui.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
            command("populate", JsonObject().apply { addProperty("space", joined.get("space").asString) })
            ui.waitUntil(15_000) { ui.onNodeWithText("Fixture line 19").isDisplayed() }
            visibleSpacesList().performTouchInput { swipeDown(durationMillis = 350) }
            visibleSpacesList().performScrollToNode(hasText("Fixture line 5"))
            val returnToLatest = hasText("Back to latest messages") and hasClickAction()
            ui.onNode(returnToLatest).assertIsDisplayed().assert(!newerMessagesAvailable())
            val anchor = ui.onNodeWithText("Fixture line 5").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
            capture("04a-scrolled-before-module-switch", "Fixture line 5")
            command("populate", JsonObject().apply {
                addProperty("space", joined.get("space").asString); addProperty("from", 20); addProperty("count", 60)
            })
            ui.waitUntil(20_000) { ui.onNode(returnToLatest and newerMessagesAvailable()).isDisplayed() }
            assertEquals("Unrelated incoming pages keep the actual reading pixel", anchor,
                ui.onNodeWithText("Fixture line 5").fetchSemanticsNode().boundsInRoot.top, 1f)
            capture("04a-incoming-messages-preserve-anchor", "Fixture line 5")
            switchModule("Spaces", "Red Square")
            openSpaces("Red Square")
            ui.waitUntil(10_000) { ui.onNodeWithText("Fixture line 5").isDisplayed() }
            assertEquals("Module return keeps the actual reading pixel", anchor,
                ui.onNodeWithText("Fixture line 5").fetchSemanticsNode().boundsInRoot.top, 1f)
            capture("04b-scrolled-after-module-switch", "Fixture line 5")
            val history = visibleSpacesList()
            history.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { scroll -> scroll(0f, 100_000f) }
            val reachedEnd = SystemClock.elapsedRealtime()
            ui.waitUntil(5_000) {
                SystemClock.elapsedRealtime() - reachedEnd >= 500 &&
                    // Two accepted initial messages precede the 48 fixture lines
                    // that fit in the first 50-message window.
                    ui.onNodeWithText("Fixture line 47").isDisplayed() &&
                    ui.onNode(returnToLatest and newerMessagesAvailable()).isDisplayed()
            }
            capture("04c-historical-page-end", "Fixture line 47")
            ui.onNodeWithText("Back to latest messages").performClick()
            ui.waitUntil(10_000) { ui.onNodeWithText("Fixture line 79").isDisplayed() }
            capture("04d-explicit-latest-return", "Fixture line 79")
            // A second, independently created community exercises the native
            // preview and explicit join path rather than only owner admission.
            val invitationDetails = command("invitation")
            val invitation = invitationDetails.get("link").asString
            backToCommunities()
            ui.waitUntil(5_000) { communityRow(community).isDisplayed() }
            val joinWithInvitation = hasText("Join with an invitation") and hasClickAction()
            visibleSpacesList().performScrollToNode(joinWithInvitation)
            val joinAction = ui.onNode(joinWithInvitation and isEnabled())
            ui.waitUntil(5_000) { joinAction.isDisplayed() }
            joinAction.performClick()
            val invitationInput = ui.onNode(hasSetTextAction() and hasAnyAncestor(isDialog()))
            ui.waitUntil(5_000) { invitationInput.isDisplayed() }
            invitationInput.performTextInput(invitation)
            val viewInvitation = ui.onNode(hasText("View invitation") and hasClickAction() and isEnabled() and hasAnyAncestor(isDialog()))
            ui.waitUntil(5_000) { viewInvitation.isDisplayed() }
            viewInvitation.performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Builders on another device").isDisplayed() &&
                ui.onNode(hasText("Join community") and hasClickAction() and isEnabled()).isDisplayed() }
            ui.onNodeWithText("Builders on another device").assertIsDisplayed()
            ui.onNode(hasText("Join community") and hasClickAction()).assertIsDisplayed()
            capture("05-preview-without-joining", "Join community")
            ui.onNode(hasText("Use another invitation") and hasClickAction()).performClick()
            ui.waitUntil(5_000) { ui.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).isDisplayed() }
            ui.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextReplacement(invitation)
            ui.waitUntil(5_000) { viewInvitation.isDisplayed() }
            viewInvitation.performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Builders on another device").isDisplayed() &&
                ui.onNode(hasText("Join community") and hasClickAction() and isEnabled()).isDisplayed() }
            ui.onNode(hasText("Join community") and hasClickAction()).performClick()
            ui.waitUntil(20_000) { ui.onNodeWithText("Reply from independent member").isDisplayed() &&
                ui.onNode(hasSetTextAction() and isEnabled()).isDisplayed() }
            capture("06-joined-independent-community")
            ui.onNode(hasSetTextAction()).performTextInput("A reply after joining from Android")
            ui.onNodeWithContentDescription("Send message").assertIsEnabled().performClick()
            closeKeyboard(activity)
            ui.waitUntil(10_000) {
                command("status").getAsJsonArray("communities").any { row ->
                    row.asJsonObject.get("id").asString == invitationDetails.get("space").asString &&
                        row.asJsonObject.getAsJsonArray("received").any { it.asString == "A reply after joining from Android" }
                }
            }
            ui.onNodeWithContentDescription("Switch Space or channel").performClick()
            communityRow(community).assertIsDisplayed().performClick()
            ui.waitUntil(10_000) { ui.onNode(hasSetTextAction() and isEnabled()).isDisplayed() }
            ui.onNode(hasSetTextAction()).performTextReplacement("Draft survives a module switch")
            closeKeyboard(activity)
            switchModule("Spaces", "Red Square")
            openSpaces("Red Square")
            ui.waitUntil(10_000) { ui.onNode(hasSetTextAction() and hasText("Draft survives a module switch")).isDisplayed() }
            capture("07-returned-with-draft")
            command("hold")
            ui.onNode(hasSetTextAction()).performTextReplacement(originalText)
            ui.waitUntil(10_000) { ui.onNode(hasContentDescription("Send message") and isEnabled()).isDisplayed() }
            ui.onNodeWithContentDescription("Send message").performClick()
            closeKeyboard(activity)
            var acceptedOriginal: String? = null
            ui.waitUntil(15_000) {
                val observed = command("status")
                val originals = observed.getAsJsonArray("originals").map { it.asJsonObject }
                acceptedOriginal = originals.lastOrNull { it.get("kind").asString == "content" }?.get("id")?.asString
                val received = observed.getAsJsonArray("communities").any { row ->
                    row.asJsonObject.getAsJsonArray("received").any { it.asString == originalText }
                }
                acceptedOriginal != null && received && ui.onNodeWithText("Waiting for confirmation").isDisplayed()
            }
            ui.onNode(hasSetTextAction()).performTextReplacement(draft)
            closeKeyboard(activity)
            switchModule("Spaces", "Red Square")
            openSpaces("Red Square")
            ui.waitUntil(10_000) { ui.onNode(hasSetTextAction() and hasText(draft)).isDisplayed() }
            capture("08-uncertain-original-and-new-draft")
            assertTrue("The original Android wallet must be preserved",
                originalWallet == AndroidWalletStore(context).loadExisting().exportJson())
            assertEquals(backend, AndroidBackendStore(context).load())
            val savedCheckpoint = JsonObject().apply {
                addProperty("phase", "ready-for-process-kill"); addProperty("pid", Process.myPid())
                addProperty("owner", wallet.publicKey); addProperty("backend", backend.id)
                addProperty("original", acceptedOriginal); addProperty("community", community)
            }.toString().toByteArray(Charsets.UTF_8)
            val atomicCheckpoint = android.util.AtomicFile(checkpoint)
            val checkpointOutput = atomicCheckpoint.startWrite()
            try {
                checkpointOutput.write(savedCheckpoint)
                atomicCheckpoint.finishWrite(checkpointOutput)
            } catch (failure: Throwable) {
                atomicCheckpoint.failWrite(checkpointOutput)
                throw failure
            }
            // The host, outside this PID, observes the durable checkpoint and
            // force-stops this actual process. No Activity.recreate substitute.
            while (true) SystemClock.sleep(250)
        } }
    }

    @Test fun coldReturnReconcilesTheSameOriginalAndKeepsNewWriting() {
        val saved = JsonParser.parseString(checkpoint.readText()).asJsonObject
        assertEquals("ready-for-process-kill", saved.get("phase").asString)
        assertNotEquals(saved.get("pid").asInt, Process.myPid())
        assertEquals(saved.get("owner").asString, AndroidWalletStore(context).loadExisting().publicKey)
        assertEquals(saved.get("backend").asString, AndroidBackendStore(context).load().id)
        command("release")
        launch().use { withFailureCapture({ capture("recovery-native-failure") }) {
            openSpaces("Red Square")
            ui.waitUntil(15_000) { communityRow(community).isDisplayed() }
            communityRow(community).performClick()
            ui.waitUntil(20_000) { ui.onNodeWithText(originalText).isDisplayed() &&
                ui.onNode(hasSetTextAction() and hasText(draft)).isDisplayed() &&
                ui.onAllNodesWithText("Waiting for confirmation").fetchSemanticsNodes().isEmpty() }
            capture("09-cold-return-original-and-draft")
            val original = saved.get("original").asString
            val status = command("status")
            assertTrue(status.getAsJsonArray("originals").count { it.asJsonObject.get("id").asString == original } >= 2)
            assertEquals(1, status.getAsJsonArray("communities").flatMap { it.asJsonObject.getAsJsonArray("received").map { it.asString } }
                .count { it == originalText })
            evidence.resolve("recovered.json").writeText(saved.deepCopy().apply {
                addProperty("phase", "recovered"); addProperty("recoveredPid", Process.myPid()); add("observed", status)
            }.toString())
        } }
    }

    private fun backToCommunities() {
        ui.onNode(hasText("Back to your communities") and hasClickAction() and
            !hasAnyAncestor(hasScrollToNodeAction())).assertIsDisplayed().assertIsEnabled().performClick()
    }

    private fun newerMessagesAvailable() = SemanticsMatcher.expectValue(
        androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
        context.getString(R.string.spaces_newer_messages_available))

    private fun visibleSpacesList() = ui.onNode(hasScrollToNodeAction() and SemanticsMatcher("on-screen Spaces list") {
        it.boundsInRoot.right > 0f && it.boundsInRoot.left >= 0f && it.boundsInRoot.bottom > 0f
    })
    private fun onScreenCommunity(name: String) = hasContentDescription("Open $name") and
        SemanticsMatcher("on-screen community") {
            it.boundsInRoot.right > 0f && it.boundsInRoot.left >= 0f && it.boundsInRoot.bottom > 0f
        }
    private fun communityRow(name: String) = ui.onNode(onScreenCommunity(name))
    private fun openSpaces(current: String) = switchModule(current, "Spaces")
    private fun switchModule(current: String, next: String) {
        val reveal = appNavigationRevealAction()
        val revealedHosts = mutableSetOf<Int>()
        val switch = ui.onNode(hasContentDescription("Switch app: $current") and isEnabled())
        var previousBounds: androidx.compose.ui.geometry.Rect? = null
        // The cold header and a masked reading header can both expose the
        // switcher before it is ready to receive a real tap.
        ui.waitUntil(15_000) {
            val host = ui.onAllNodes(reveal).fetchSemanticsNodes(atLeastOneRootRequired = false).singleOrNull()
            if (host != null && revealedHosts.add(host.id)) ui.revealAppNavigation()
            val ready = host == null && switch.isDisplayed() &&
                (current != "Red Square" || ui.onNodeWithText("Home").isDisplayed())
            val bounds = if (ready) switch.fetchSemanticsNode().boundsInRoot else null
            val settled = bounds != null && bounds == previousBounds
            previousBounds = bounds
            settled
        }
        switch.assertIsDisplayed().performTouchInput { click() }
        val target = hasText(next) and hasClickAction() and hasAnyAncestor(isPopup() or isDialog())
        ui.waitUntil(5_000) { ui.onAllNodes(target).fetchSemanticsNodes().size == 1 }
        ui.onNode(target).performScrollTo().assertIsEnabled().performClick()
        ui.waitUntil(10_000) { ui.onNodeWithContentDescription("Switch app: $next").isDisplayed() }
    }
    private fun closeKeyboard(activity: ActivityScenario<MainActivity>) {
        // Text replacement can return before the pending input connection shows
        // the IME. Use Espresso's synchronized keyboard action, rather than
        // sampling insets once and potentially skipping the dismissal entirely.
        ui.waitForIdle()
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        fun visible(): Boolean {
            var shown = false
            activity.onActivity { shown = androidx.core.view.ViewCompat.getRootWindowInsets(it.window.decorView)
                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true }
            return shown
        }
        ui.waitUntil(5_000) { !visible() }
    }
    private fun capture(name: String, expected: String? = null) = captureNativeScreen(File(evidence, "$name.png"), expected)
    private fun command(action: String, body: JsonObject = JsonObject()): JsonObject {
        val connection = URL("http://127.0.0.1:$controlPort/$action").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"; connection.doOutput = true
            connection.connectTimeout = 5_000; connection.readTimeout = 5_000
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            assertEquals("Owned fixture command failed", 200, connection.responseCode)
            return connection.inputStream.bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        } finally { connection.disconnect() }
    }
}
