package nl.davidengelen.redsquare

import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.AndroidSpaceClientStore
import nl.davidengelen.redsquare.spaces.SpaceClient
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Clock
import java.security.MessageDigest

/** Disposable dev-package baseline, real Activity/SQLite/peer and two actual
 * externally terminated PIDs. No production wallet, transaction or node. */
class SpacesManagementJourneyTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext.also {
        check(it.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
    }
    private val arguments get() = InstrumentationRegistry.getArguments()
    private fun port(name: String) = requireNotNull(arguments.getString(name)).toInt().also { require(it in 1024..65535) }
    private val device get() = UiDevice.getInstance(instrumentation)
    private val privateManagement get() = arguments.getString("spacesPrivateManagement") == "true"
    private val privateReadiness get() = arguments.getString("spacesPrivateReadiness") == "true"
    private val evidenceLabel get() = if (privateReadiness) "spaces-private-readiness" else if (privateManagement) "spaces-private-management" else "spaces-management"
    private val evidence get() = File(context.cacheDir, "$evidenceLabel-evidence").apply { mkdirs() }
    private val checkpoint get() = File(context.cacheDir, "$evidenceLabel-checkpoint.json")
    private val peerEndpoint get() = "ws://127.0.0.1:${port("spacesPeerPort")}/wsopen"
    private val fixtureOwner get() = SaitoWallet.fromPrivateKey("0".repeat(63) + if (privateReadiness) "5" else "3")
    private val fixtureService get() = SaitoWallet.fromPrivateKey("0".repeat(63) + "2").publicKey
    private val saveError get() = context.getString(R.string.spaces_save_failed)
    private fun launch(link: String) = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW; data = Uri.parse(link)
        putExtra("feedPeerUrl", peerEndpoint)
        putExtra("feedOfficialPeerUrl", peerEndpoint)
        putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/")
    })

    @Test fun waitingAdministratorCanGrantButCannotRemoveAndRecoversAnActuallyRejectedGrant() {
        check(privateReadiness && arguments.getString("spacesDisposableBaseline") == "true")
        val walletStore = AndroidWalletStore(context)
        walletStore.replace(walletStore.loadOrCreate(), fixtureOwner)
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        val fixtures = command("readiness-fixtures")
        launch(fixtures.get("link").asString).use { activity -> withFailureCapture({ capture("readiness-normal-failure") }) {
            waitText("Readiness General 17")
            waitUntil { status().getAsJsonArray("joinRequests").size() > 0 }
            command("readiness-admit-initial")
            chooseChannel("readiness-room", fixtures.get("private").asString)
            ui.onNode(hasSetTextAction()).performTextReplacement("This device cached the private channel")
            val send = ui.onNodeWithContentDescription(context.getString(R.string.spaces_send))
            waitUntil(15_000) { send.isDisplayed() && !send.fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) }
            send.clickReady()
            waitUntil { status().getAsJsonArray("privateMessages").any { it.asString == "This device cached the private channel" } }
            assertEquals(setOf(fixtures.get("holder").asString, fixtures.get("actor").asString),
                status().getAsJsonArray("keyHolders").map { it.asString }.toSet())
            capture("01-real-private-reading-before-removal")
            command("readiness-revoke-and-promote")
            activity.recreate()
            waitText(context.getString(R.string.spaces_waiting_channel_keys))
            openPrivateAccess()
            readinessMember(fixtures, "allowed").assertIsOn().assertIsNotEnabled()
            capture("02-waiting-keys-cannot-remove")
            readinessMember(fixtures, "grant").assertIsOff().assertIsEnabled().clickReady()
            waitUntil {
                status().getAsJsonArray("allowedMembers").any { it.asString == fixtures.get("grant").asString } && readinessGrantVisible(fixtures, "grant")
            }
            readinessMember(fixtures, "grant").assertIsOn().assertIsNotEnabled()
            assertReadinessKeys(fixtures)
            capture("03-policy-granted-without-current-keys")
            verifyReadinessRejection(fixtures, "normal")
            saveReadinessCompletion("completed.json", "normal", fixtures)
        } }
    }

    @Test fun compactWaitingAdministratorSeesARealRejectionAndExplicitlyRecovers() {
        check(privateReadiness)
        assertEquals(2f, context.resources.configuration.fontScale, .01f)
        assertEquals(320, context.resources.configuration.screenWidthDp)
        val fixtures = command("readiness-fixtures")
        command("readiness-reset-retry")
        launch(fixtures.get("link").asString).use { _ -> withFailureCapture({ capture("readiness-compact-failure") }) {
            waitText(context.getString(R.string.spaces_waiting_channel_keys))
            openPrivateAccess()
            readinessMember(fixtures, "allowed").assertIsOn().assertIsNotEnabled()
            capture("07-compact-waiting-keys-cannot-remove")
            verifyReadinessRejection(fixtures, "compact")
            saveReadinessCompletion("compact-completed.json", "compact", fixtures)
        } }
    }

    private fun readinessMember(fixtures: JsonObject, member: String): SemanticsNodeInteraction {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        val list = history()
        val key = fixtures.get(member).asString
        waitUntil(15_000) {
            list.fetchSemanticsNode().config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.IndexForKey)?.invoke(key)?.let { it >= 0 } == true
        }
        list.performScrollToKey(key)
        val target = ui.onNode(hasText(fixtures.get(member + "Name").asString) and
            SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ToggleableState) and onScreen)
        val remaining = deadline - SystemClock.elapsedRealtime()
        assertTrue("The actual member is visible inside15seconds", remaining > 0)
        waitUntil(remaining) { target.isDisplayed() }
        return target
    }

    private fun readinessGrantVisible(fixtures: JsonObject, member: String): Boolean {
        val row = ui.onNode(hasText(fixtures.get(member + "Name").asString) and
            SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ToggleableState) and onScreen)
        return row.isDisplayed() && row.fetchSemanticsNode().config.let {
            it[androidx.compose.ui.semantics.SemanticsProperties.ToggleableState] == androidx.compose.ui.state.ToggleableState.On &&
                it.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
        }
    }

    private fun assertReadinessKeys(fixtures: JsonObject) {
        val remote = status()
        assertEquals("ADMIN", remote.getAsJsonObject("members").get(fixtures.get("actor").asString).asString)
        assertEquals(listOf(fixtures.get("holder").asString), remote.getAsJsonArray("keyHolders").map { it.asString })
        assertEquals(listOf("This device cached the private channel", "Future private content needs the new keys"),
            remote.getAsJsonArray("privateMessages").map { it.asString })
    }

    private fun verifyReadinessRejection(fixtures: JsonObject, label: String) {
        val before = status()
        val rejectedBefore = before.getAsJsonArray("rejections").size()
        val originalsBefore = before.getAsJsonArray("originals").size()
        readinessMember(fixtures, "retry").assertIsOff().assertIsEnabled()
        command("readiness-reject-next-grant")
        readinessMember(fixtures, "retry").clickReady()
        waitUntil(5_000) { status().getAsJsonArray("rejections").size() == rejectedBefore + 1 }
        waitText(context.getString(R.string.spaces_management_rejected), 5_000)
        readinessMember(fixtures, "retry").assertIsOff()
        val rejected = status().getAsJsonArray("rejections").last().asJsonObject.get("id").asString
        capture(if (label == "normal") "04-normal-rejected-visible" else "08-compact-rejected-visible")
        val refresh = ui.onNode(hasText(context.getString(R.string.spaces_refresh_settings)) and hasClickAction())
        refresh.performScrollTo().clickReady()
        val retry = readinessMember(fixtures, "retry")
        waitUntil { retry.confirmedToggle(false) }
        retry.assertIsOff().clickReady()
        waitUntil {
            status().getAsJsonArray("allowedMembers").any { it.asString == fixtures.get("retry").asString } && readinessGrantVisible(fixtures, "retry")
        }
        readinessMember(fixtures, "retry").assertIsOn().assertIsNotEnabled()
        waitUntil(5_000) { !ui.onNodeWithText(context.getString(R.string.spaces_management_rejected)).isDisplayed() }
        val remote = status()
        val originals = remote.getAsJsonArray("originals").drop(originalsBefore).map { it.asJsonObject }
        assertEquals(2, originals.size)
        assertEquals(rejected, originals.first().get("id").asString)
        assertNotEquals(rejected, originals.last().get("id").asString)
        assertEquals(2, originals.map { it.get("sha256").asString }.distinct().size)
        assertReadinessKeys(fixtures)
        saveJson("$label-rejection-recovery.json", JsonObject().apply {
            addProperty("pid", Process.myPid()); addProperty("actor", fixtures.get("actor").asString)
            addProperty("space", fixtures.get("space").asString); addProperty("channel", fixtures.get("private").asString)
            addProperty("rejectedOriginal", rejected); addProperty("freshOriginal", originals.last().get("id").asString)
            add("originals", JsonArray().apply { originals.forEach(::add) })
        })
        capture(if (label == "normal") "05-normal-explicit-fresh-grant" else "09-compact-explicit-fresh-grant")
    }

    private fun saveReadinessCompletion(name: String, label: String, fixtures: JsonObject) {
        assertReadinessKeys(fixtures)
        saveJson("$label-host.json", status())
        saveJson(name, JsonObject().apply {
            addProperty("pid", Process.myPid()); addProperty("owner", AndroidWalletStore(context).loadExisting().publicKey)
            addProperty("space", fixtures.get("space").asString); addProperty("channel", fixtures.get("private").asString)
            addProperty("fontScale", context.resources.configuration.fontScale); addProperty("screenWidthDp", context.resources.configuration.screenWidthDp)
        })
    }

    @Test fun privateCreationRestoresSelectedMembersAndCheckpointsARealUnconfirmedRevocation() {
        check(privateManagement && arguments.getString("spacesDisposableBaseline") == "true" && !checkpoint.exists())
        val walletStore = AndroidWalletStore(context)
        walletStore.replace(walletStore.loadOrCreate(), fixtureOwner)
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        val fixtures = command("private-management-fixtures")
        launch(fixtures.get("link").asString).use { activity -> withFailureCapture({ capture("private-creation-failure") }) {
            waitText("Private management General 17")
            ui.onNode(hasSetTextAction()).performTextReplacement("Keep General writing through private access changes")
            closeKeyboard(); history().performScrollToNode(hasText("Private management General 5"))
            val readingPixel = pixel("Private management General 5")
            openInformation(); clickDialog(context.getString(R.string.spaces_add_channel))
            editorField("Channel name").performScrollTo().performTextReplacement("native-private-lounge")
            editorField("Category (optional)").performScrollTo().performTextReplacement("Team")
            closeKeyboard()
            privateRestriction().performScrollTo().clickReady()
            privateRestriction().assertIsOn()
            privateMember(fixtures, true).clickReady()
            capture("01-private-members-selected")
            activity.recreate()
            waitUntil { editorField("Channel name").isDisplayed() }
            editorField("Channel name").assertTextContains("native-private-lounge")
            privateRestriction().performScrollTo().assertIsOn()
            privateMember(fixtures, true).assertIsOn()
            capture("02-private-creation-restored")
            command("private-management-drop-next", JsonObject().apply { addProperty("kind", "create-channel") })
            val started = SystemClock.elapsedRealtime(); val deadline = started + 27_000
            clickDialog("Create channel")
            waitText("Waiting for confirmation.", 5_000)
            clickDialog("Close"); clickDialog(context.getString(R.string.spaces_add_channel))
            editorField("Channel name").assertIsNotEnabled()
            privateRestriction().performScrollTo().assertIsOn().assertIsNotEnabled()
            privateMember(fixtures, true).assertIsOn().assertIsNotEnabled()
            capture("03-private-creation-pending-reentry")
            var received = JsonObject()
            val remaining = deadline - SystemClock.elapsedRealtime()
            assertTrue("Private creation keeps its full27second budget", remaining > 0)
            waitUntil(remaining) {
                received = status()
                received.getAsJsonArray("originals").map { it.asJsonObject }.count { it.get("kind").asString == "create-channel" } == 2 &&
                    ui.onNode(hasContentDescription(context.getString(R.string.spaces_switch_channel)) and
                        SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "native-private-lounge")).isDisplayed()
            }
            val completed = SystemClock.elapsedRealtime()
            assertTrue(completed <= deadline)
            val channel = received.get("privateChannel").asString
            saveJson("private-creation-recovery.json", JsonObject().apply {
                addProperty("pid", Process.myPid()); addProperty("owner", fixtures.get("owner").asString)
                addProperty("space", fixtures.get("space").asString); addProperty("channel", channel)
                addProperty("original", received.getAsJsonArray("dropped").first().asString)
                addProperty("recoveryStartedAtMs", started); addProperty("recoveryCompletedAtMs", completed)
            })
            fixtures.addProperty("private", channel)
            waitUntil { status().getAsJsonObject("channels").has(channel) }
            assertFalse(status().getAsJsonArray("outsiderChannels").any { it.asString == channel })
            capture("04-private-created-and-selected")
            ui.onNode(hasSetTextAction()).performTextReplacement("A native encrypted message before removal")
            ui.onNodeWithContentDescription(context.getString(R.string.spaces_send)).clickReady()
            waitUntil { status().getAsJsonArray("privateMessages").any { it.asString == "A native encrypted message before removal" } }
            openPrivateAccess()
            val fixedRole = hasText(fixtures.get("adminName").asString) and
                SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ToggleableState)
            history().performScrollToKey(fixtures.get("admin").asString)
            waitUntil { ui.onNode(fixedRole).isDisplayed() }
            ui.onNode(fixedRole).assertIsOn().assertIsNotEnabled()
            capture("05a-private-administrator-access-fixed")
            privateMember(fixtures).assertIsOn()
            activity.recreate(); waitText(context.getString(R.string.spaces_channel_access))
            privateMember(fixtures).assertIsOn()
            capture("05-private-access-restored")
            command("private-management-drop-next", JsonObject().apply { addProperty("kind", "revoke-channel-access") })
            privateMember(fixtures).clickReady()
            clickDialog(context.getString(R.string.spaces_remove_channel_access))
            waitText("Waiting for confirmation.", 5_000)
            waitUntil(5_000) { !status().getAsJsonObject("channels").has(channel) }
            assertEquals("Keep General writing through private access changes", savedDraft(fixtures, "general"))
            capture("06-private-revoke-awaiting")
            saveJson("private-revoke-pending-host.json", status())
            publishCheckpoint("private-revoke-pending", fixtures, readingPixel)
        } }
    }

    @Test fun coldPrivateRevocationRecoversTheSameOriginalThenCheckpointsARealGrant() {
        check(privateManagement)
        val previous = readCheckpoint("private-revoke-pending")
        val fixtures = previous.getAsJsonObject("fixtures")
        launch(fixtures.get("link").asString).use { _ -> withFailureCapture({ capture("private-grant-failure") }) {
            waitText("A native encrypted message before removal")
            openPrivateAccess()
            val member = privateMember(fixtures)
            assertRetriedOriginal(previous, "revoke-channel-access") { member.confirmedToggle(false) }
            member.assertIsOff().assertIsEnabled()
            val received = status(); val channel = fixtures.get("private").asString
            assertFalse(received.getAsJsonObject("channels").has(channel))
            assertEquals("MEMBER", received.getAsJsonObject("members").get(fixtures.get("member").asString).asString)
            assertEquals("Keep General writing through private access changes", savedDraft(fixtures, "general"))
            capture("07-cold-private-revocation")
            command("private-management-drop-next", JsonObject().apply { addProperty("kind", "grant-channel-access") })
            privateMember(fixtures).clickReady()
            waitText("Waiting for confirmation.", 5_000)
            waitUntil(5_000) { status().getAsJsonArray("dropped").size() == 3 }
            capture("08-private-grant-awaiting")
            saveJson("private-grant-pending-host.json", status())
            publishCheckpoint("private-grant-pending", fixtures, previous.get("pixel").asFloat)
        } }
    }

    @Test fun coldPrivateGrantRestoresActualEncryptedReadingAndKeepsTheGeneralDraft() {
        check(privateManagement)
        val previous = readCheckpoint("private-grant-pending")
        val fixtures = previous.getAsJsonObject("fixtures")
        launch(fixtures.get("link").asString).use { activity -> withFailureCapture({ capture("private-grant-recovery-failure") }) {
            waitText("A native encrypted message before removal")
            openPrivateAccess()
            val member = privateMember(fixtures)
            assertRetriedOriginal(previous, "grant-channel-access") { member.confirmedToggle(true) }
            member.assertIsOn().assertIsEnabled()
            waitUntil { status().getAsJsonObject("channels").getAsJsonObject(fixtures.get("private").asString)?.get("ready")?.asBoolean == true }
            capture("09-cold-private-grant")
            device.pressBack(); clickDialog("Close")
            waitText("A native encrypted message before removal")
            ui.onNode(hasSetTextAction()).performTextReplacement("A native encrypted message after fresh admission")
            ui.onNodeWithContentDescription(context.getString(R.string.spaces_send)).clickReady()
            waitUntil { status().getAsJsonArray("privateMessages").any { it.asString == "A native encrypted message after fresh admission" } }
            capture("10-private-reader-after-fresh-admission")
            verifyPrivateEditorRecovery(activity, fixtures)
            openPrivateAccess(); privateMember(fixtures).assertIsOn()
            withWriteFailure(activity, fixtures, "private-access") {
                val before = status().getAsJsonArray("originals").size()
                privateMember(fixtures).clickReady(); clickDialog(context.getString(R.string.spaces_remove_channel_access))
                waitAccessError()
                assertEquals(before, status().getAsJsonArray("originals").size())
                capture("11-private-long-roster-error-visible")
                ui.onNode(hasText("Close") and hasClickAction() and !hasAnyAncestor(isDialog())).performScrollTo().clickReady()
                waitUntil(5_000) { !ui.onNodeWithText(saveError).isDisplayed() }
                privateMember(fixtures).assertIsOn().assertIsEnabled()
                capture("12-private-error-dismissed")
            }
        } }
        launch(fixtures.get("link").asString).use { _ ->
            waitText("A native encrypted message after fresh admission")
            chooseChannel("general")
            waitText("Private management General 5"); assertPixel("Private management General 5", previous.get("pixel").asFloat)
            waitUntil { ui.onNode(hasSetTextAction() and hasText("Keep General writing through private access changes")).isDisplayed() }
            saveJson("private-draft-pixel.json", JsonObject().apply {
                addProperty("pid", Process.myPid()); addProperty("owner", fixtures.get("owner").asString)
                addProperty("space", fixtures.get("space").asString); addProperty("channel", fixtures.get("general").asString)
                addProperty("expectedPixel", previous.get("pixel").asFloat); addProperty("actualPixel", pixel("Private management General 5"))
                addProperty("draft", ui.onNode(hasSetTextAction() and hasText("Keep General writing through private access changes"))
                    .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text)
            })
            capture("13-private-general-draft-preserved")
            saveCompletion("completed.json", fixtures)
        }
    }

    @Test fun compactPrivateRosterKeepsTheActualLocalErrorAndRemovalControlsReachable() {
        check(privateManagement)
        val fixtures = readCheckpoint("private-grant-pending").getAsJsonObject("fixtures")
        launch(fixtures.get("link").asString).use { activity -> withFailureCapture({ capture("private-compact-failure") }) {
            waitUntil { ui.onNode(hasContentDescription(context.getString(R.string.spaces_switch_channel)) and
                SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "general")).isDisplayed() }
            waitUntil { ui.onNode(hasSetTextAction() and hasText("Keep General writing through private access changes")).isDisplayed() }
            chooseChannel("native-private-lounge")
            waitText("A native encrypted message after fresh admission")
            openPrivateAccess(); privateMember(fixtures).assertIsOn()
            capture("14-compact-private-roster")
            withWriteFailure(activity, fixtures, "compact-private-access") {
                val before = status().getAsJsonArray("originals").size()
                privateMember(fixtures).clickReady()
                capture("15-compact-private-remove-confirmation")
                clickDialog(context.getString(R.string.spaces_remove_channel_access))
                waitAccessError()
                assertEquals(before, status().getAsJsonArray("originals").size())
                capture("16-compact-private-error-visible")
                ui.onNode(hasText("Close") and hasClickAction() and !hasAnyAncestor(isDialog())).performScrollTo().clickReady()
                waitUntil(5_000) { !ui.onNodeWithText(saveError).isDisplayed() }
                privateMember(fixtures).assertIsOn().assertIsEnabled()
                capture("17-compact-private-error-dismissed")
            }
        } }
        launch(fixtures.get("link").asString).use { _ ->
            waitText("A native encrypted message after fresh admission")
            saveCompletion("compact-completed.json", fixtures)
        }
    }

    private fun verifyPrivateEditorRecovery(activity: ActivityScenario<MainActivity>, fixtures: JsonObject) {
        val channel = fixtures.get("private").asString
        val renamed = "native-private-reading"
        openInformation(); openChannelEditor()
        editorField("Channel name").performTextReplacement(renamed)
        assertFocusedFieldVisible("Channel name", renamed)
        editorField("Category (optional)").performTextReplacement("Shared plans")
        assertFocusedFieldVisible("Category (optional)", "Shared plans")
        closeKeyboard(); activity.recreate()
        waitUntil { editorField("Channel name").isDisplayed() }
        editorField("Channel name").assertTextContains(renamed)
        editorField("Category (optional)").assertTextContains("Shared plans")
        waitDialogButton("Save changes")
        capture("18-private-editor-restored")
        command("private-management-drop-next", JsonObject().apply { addProperty("kind", "edit-channel") })
        val started = SystemClock.elapsedRealtime(); val deadline = started + 27_000
        clickDialog("Save changes"); waitText("Waiting for confirmation.", 5_000)
        var received = JsonObject()
        waitUntil(5_000) {
            received = status()
            val remote = received.getAsJsonObject("channels").getAsJsonObject(channel)
            received.getAsJsonArray("dropped").size() == 4 && remote.get("name").asString == renamed && remote.get("category").asString == "Shared plans"
        }
        val original = received.getAsJsonArray("dropped").last().asString
        clickDialog("Close"); openChannelEditor()
        editorField("Channel name").assertTextContains(renamed).assertIsNotEnabled()
        ui.onNode(hasText("Save changes") and hasClickAction() and hasAnyAncestor(isDialog())).assertIsNotEnabled()
        capture("19-private-editor-pending-reentry")
        val remaining = deadline - SystemClock.elapsedRealtime()
        assertTrue("Private metadata recovery keeps its original27second budget", remaining > 0)
        waitUntil(remaining) {
            received = status()
            editRows(received).count { it.get("id").asString == original } == 2 &&
                ui.onAllNodes(hasText("Waiting for confirmation.") and onScreen).fetchSemanticsNodes().isEmpty()
        }
        val attempts = editRows(received).filter { it.get("id").asString == original }
        assertEquals(1, attempts.map { it.get("sha256").asString }.distinct().size)
        assertTrue(attempts.all { it.get("privateMetadata").asBoolean })
        if (editorField("Channel name").isDisplayed()) clickDialog("Close")
        val confirmationRemaining = deadline - SystemClock.elapsedRealtime()
        assertTrue(confirmationRemaining > 0)
        waitInformationName(renamed, confirmationRemaining)
        val completed = SystemClock.elapsedRealtime()
        assertTrue(completed <= deadline)
        saveJson("private-edit-host.json", received)
        saveJson("private-edit-recovery.json", JsonObject().apply {
            addProperty("pid", Process.myPid()); addProperty("owner", fixtures.get("owner").asString)
            addProperty("space", fixtures.get("space").asString); addProperty("channel", channel)
            addProperty("original", original); addProperty("name", renamed); addProperty("category", "Shared plans")
            addProperty("recoveryStartedAtMs", started); addProperty("recoveryCompletedAtMs", completed)
        })
        capture("20-private-editor-confirmed")
        openChannelEditor()
        editorField("Channel name").performTextReplacement("native-private-lounge")
        editorField("Category (optional)").performTextReplacement("Team")
        closeKeyboard(); clickDialog("Save changes")
        waitInformationName("native-private-lounge")
        waitUntil {
            val restored = status().getAsJsonObject("channels").getAsJsonObject(channel)
            restored.get("name").asString == "native-private-lounge" && restored.get("category").asString == "Team"
        }
        capture("21-private-editor-name-restored")
        clickDialog("Close")
        waitText("A native encrypted message after fresh admission")
    }

    private fun privateRestriction() = ui.onNode(hasText(context.getString(R.string.spaces_channel_restricted)) and
        SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ToggleableState) and hasAnyAncestor(isDialog()))
    private fun privateMember(fixtures: JsonObject, dialog: Boolean = false): SemanticsNodeInteraction {
        val match = hasText(fixtures.get("memberName").asString) and
            SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ToggleableState)
        val list = ui.onNode(hasScrollToNodeAction() and if (dialog) hasAnyAncestor(isDialog()) else !hasAnyAncestor(isDialog()) and onScreen)
        val deadline = SystemClock.elapsedRealtime() + 15_000
        val member = fixtures.get("member").asString
        waitUntil(15_000) {
            list.fetchSemanticsNode().config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.IndexForKey)?.invoke(member)?.let { it >= 0 } == true
        }
        if (dialog) list.performScrollTo()
        list.performScrollToKey(member)
        val target = ui.onNode(match and if (dialog) hasAnyAncestor(isDialog()) else !hasAnyAncestor(isDialog()) and onScreen)
        val remaining = deadline - SystemClock.elapsedRealtime()
        assertTrue("The restored member remains inside15seconds", remaining > 0)
        waitUntil(remaining) { target.isDisplayed() }
        return target
    }
    private fun openChannelEditor() {
        clickDialog(context.getString(R.string.spaces_edit_channel))
        waitUntil(15_000) { editorField("Channel name").isDisplayed() }
    }
    private fun openPrivateAccess() { openInformation(); clickDialog(context.getString(R.string.spaces_channel_access)); waitText(context.getString(R.string.spaces_channel_access)) }

    private fun waitAccessError() = waitUntil(5_000) {
        val error = ui.onNode(hasText(saveError) and !hasAnyAncestor(isDialog()))
        error.isDisplayed() && error.fetchSemanticsNode().let { it.size.height <= it.boundsInRoot.height + 1f }
    }

    @Test fun ownerSeesLocalRoleErrorsAndCheckpointsAnActuallyAcceptedUnconfirmedPromotion() {
        check(arguments.getString("spacesDisposableBaseline") == "true")
        check(!checkpoint.exists())
        val walletStore = AndroidWalletStore(context)
        walletStore.replace(walletStore.loadOrCreate(), fixtureOwner)
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        val fixtures = command("management-fixtures")
        assertEquals(fixtures.get("owner").asString, walletStore.loadExisting().publicKey)
        var readingPixel = 0f
        launch(fixtures.get("link").asString).use { activity -> withFailureCapture({ capture("role-local-error-failure") }) {
            waitText("Management message 17")
            ui.onNodeWithText("Join this Space").assertDoesNotExist()
            ui.onNode(hasSetTextAction()).performTextReplacement("Keep the general draft")
            closeKeyboard()
            history().performScrollToNode(hasText("Management message 5"))
            readingPixel = pixel("Management message 5")
            capture("01-owner-reading")
            openMembers(fixtures); openRole()
            withWriteFailure(activity, fixtures, "role") {
                clickDialog("Make administrator")
                waitDialogError()
                capture("02-local-role-error-visible")
                val status = status()
                assertEquals("MEMBER", status.getAsJsonObject("members").get(fixtures.get("member").asString).asString)
                assertEquals(0, status.getAsJsonArray("originals").size())
            }
        } }
        launch(fixtures.get("link").asString).use { activity -> withFailureCapture({ capture("role-pending-failure") }) {
            waitText("Management message 5"); assertPixel("Management message 5", readingPixel)
            openMembers(fixtures); openRole()
            command("management-drop-next", JsonObject().apply { addProperty("kind", "set-role") })
            clickDialog("Make administrator")
            waitText("Waiting for confirmation.", 5_000)
            var received = JsonObject()
            waitUntil(5_000) {
                received = status()
                received.getAsJsonArray("dropped").size() == 1 &&
                    received.getAsJsonObject("members").get(fixtures.get("member").asString).asString == "ADMIN"
            }
            assertEquals("ADMIN", received.getAsJsonObject("members").get(fixtures.get("member").asString).asString)
            capture("03-role-awaiting-real-receipt")
            saveJson("role-pending-host.json", received)
            publishCheckpoint("role-pending", fixtures, readingPixel)
        } }
    }

    @Test fun coldRoleRecoveryKeepsTheOriginalThenCheckpointsAnUnconfirmedChildArchive() {
        val previous = readCheckpoint("role-pending")
        val fixtures = previous.getAsJsonObject("fixtures")
        launch(fixtures.get("link").asString).use { activity -> withFailureCapture({ capture("archive-pending-failure") }) {
            waitText("Management message 5"); assertPixel("Management message 5", previous.get("pixel").asFloat)
            ui.onNode(hasSetTextAction() and hasText("Keep the general draft")).assertIsDisplayed()
            openMembers(fixtures); openRole()
            waitUntil { ui.onNode(hasText("Remove administrator role") and hasClickAction() and isEnabled()).isDisplayed() }
            assertRetriedOriginal(previous, "set-role")
            capture("04-cold-role-recovered")
            clickDialog("Remove administrator role")
            waitUntil { ui.onNode(hasText("Make administrator") and hasClickAction() and isEnabled()).isDisplayed() }
            waitUntil { status().getAsJsonObject("members").get(fixtures.get("member").asString).asString == "MEMBER" }
            capture("05-confirmed-role-demotion")
            dismissRole(); device.pressBack(); closeInformationIfPresent()
            chooseChannel("workshop")
            waitText("A workshop message kept through archive")
            ui.onNode(hasSetTextAction()).performTextReplacement("Keep the workshop draft")
            closeKeyboard(); openInformation()
            command("management-drop-next", JsonObject().apply { addProperty("kind", "archive-channel") })
            clickDialog("Archive channel")
            waitText("Waiting for confirmation.", 5_000)
            var received = JsonObject()
            waitUntil(5_000) {
                received = status()
                received.getAsJsonArray("dropped").size() == 2 &&
                    received.getAsJsonObject("channels").getAsJsonObject(fixtures.get("workshop").asString).get("archived").asBoolean
            }
            assertTrue(received.getAsJsonObject("channels").getAsJsonObject(fixtures.get("workshop").asString).get("archived").asBoolean)
            capture("06-child-archive-awaiting")
            device.pressBack(); chooseChannel("general")
            ui.onNode(hasSetTextAction()).performTextReplacement("New general draft while archive awaits")
            closeKeyboard()
            waitUntil(5_000) { savedDraft(fixtures, "general") == "New general draft while archive awaits" }
            assertPixel("Management message 5", previous.get("pixel").asFloat)
            ui.onNodeWithText(context.getString(R.string.spaces_channel_archived)).assertDoesNotExist()
            capture("07-other-channel-draft-isolation")
            saveJson("archive-pending-host.json", received)
            publishCheckpoint("archive-pending", fixtures, previous.get("pixel").asFloat)
        } }
    }

    @Test fun coldArchiveRecoveryAndRealReopeningKeepHistoryAndLocalErrorsReachable() {
        val previous = readCheckpoint("archive-pending")
        val fixtures = previous.getAsJsonObject("fixtures")
        launch(fixtures.get("link").asString).use { activity -> withFailureCapture({ capture("normal-management-failure") }) {
            waitText("Management message 5"); assertPixel("Management message 5", previous.get("pixel").asFloat)
            ui.onNode(hasSetTextAction() and hasText("New general draft while archive awaits")).assertIsDisplayed()
            assertRetriedOriginal(previous, "archive-channel")
            capture("08-cold-archive-other-channel")
            chooseChannel("workshop")
            waitText(context.getString(R.string.spaces_channel_archived))
            waitText("A workshop message kept through archive")
            ui.onNode(hasSetTextAction() and hasText("Keep the workshop draft")).assertIsDisplayed()
            ui.onNodeWithContentDescription("Send message").assertIsNotEnabled()
            capture("09-archived-child-readable")
            openInformation(); clickDialog("Reopen channel")
            waitDialogButton("Archive channel")
            assertFalse(status().getAsJsonObject("channels").getAsJsonObject(fixtures.get("workshop").asString).get("archived").asBoolean)
            capture("10-confirmed-child-reopening")
            withWriteFailure(activity, fixtures, "archive") {
                clickDialog("Archive channel")
                waitDialogError()
                capture("11-local-archive-error-scrolled-visible")
                assertFalse(status().getAsJsonObject("channels").getAsJsonObject(fixtures.get("workshop").asString).get("archived").asBoolean)
            }
        } }
        launch(fixtures.get("link").asString).use { activity -> withFailureCapture({ capture("announcement-management-failure") }) {
            // Reopening the Space preserves its last selected channel, workshop.
            waitText("A workshop message kept through archive")
            chooseChannel("announcements"); openInformation(); clickDialog("Archive channel")
            waitDialogButton("Reopen channel")
            device.pressBack()
            waitText(context.getString(R.string.spaces_channel_archived))
            ui.onNodeWithText(context.getString(R.string.spaces_announcements_read_only)).assertDoesNotExist()
            capture("12-owner-archived-announcement")
            openInformation(); clickDialog("Reopen channel")
            waitDialogButton("Archive channel")
            device.pressBack(); chooseChannel("general")
            ui.onNode(hasSetTextAction() and hasText("New general draft while archive awaits")).assertIsDisplayed()
            capture("13-normal-management-return")
            verifyChannelEditorRecovery(activity, fixtures)
            verifyChannelCreationRecovery(activity, fixtures)
            saveCompletion("completed.json", fixtures)
        } }
    }

    @Test fun compactLargeTypeShowsActualRoleErrorsAndArchiveControlsWithoutClipping() {
        val fixtures = command("management-fixtures")
        waitUntil { kotlin.math.abs(context.resources.configuration.fontScale - 2f) < .01f &&
            context.resources.configuration.screenWidthDp == 320 }
        assertEquals(2f, context.resources.configuration.fontScale, .01f)
        assertEquals(320, context.resources.configuration.screenWidthDp)
        launch(fixtures.get("link").asString).use { activity -> withFailureCapture({ capture("compact-management-failure") }) {
            waitUntil { ui.onNode(hasText(context.getString(R.string.spaces_information)) and hasClickAction()).isDisplayed() }
            openMembers(fixtures); openRole()
            ui.onNode(hasText("Make administrator") and hasClickAction()).assertIsDisplayed()
            capture("14-compact-role-review")
            withWriteFailure(activity, fixtures, "compact-role") {
                clickDialog("Make administrator"); waitDialogError()
                capture("15-compact-local-role-error")
                assertEquals("MEMBER", status().getAsJsonObject("members").get(fixtures.get("member").asString).asString)
            }
        } }
        launch(fixtures.get("link").asString).use { activity -> withFailureCapture({ capture("compact-confirmation-failure") }) {
            waitUntil { ui.onNode(hasText(context.getString(R.string.spaces_information)) and hasClickAction()).isDisplayed() }
            openMembers(fixtures); openRole(); clickDialog("Make administrator")
            waitUntil { ui.onNode(hasText("Remove administrator role") and hasClickAction() and isEnabled()).isDisplayed() }
            capture("16-compact-confirmed-administrator")
            clickDialog("Remove administrator role")
            waitUntil { ui.onNode(hasText("Make administrator") and hasClickAction() and isEnabled()).isDisplayed() }
            dismissRole(); device.pressBack(); closeInformationIfPresent(); openInformation()
            clickDialog("Archive channel")
            waitDialogButton("Reopen channel")
            capture("17-compact-archived-channel-settings")
            clickDialog("Reopen channel")
            waitDialogButton("Archive channel")
            capture("18-compact-reopened-channel")
            openChannelEditor()
            editorField("Channel name").performScrollTo().performTextReplacement("compact-commons")
            assertFocusedFieldVisible("Channel name", "compact-commons")
            editorField("Category (optional)").performScrollTo().performTextReplacement("Projects")
            assertFocusedFieldVisible("Category (optional)", "Projects")
            closeKeyboard()
            capture("26-compact-channel-editor")
            val before = editRows(status()).size
            withWriteFailure(activity, fixtures, "compact-editor") {
                clickDialog("Save changes"); waitDialogError()
                capture("27-compact-channel-editor-error")
                assertEquals(before, editRows(status()).size)
                assertEquals("general", status().getAsJsonObject("channels").getAsJsonObject(fixtures.get("general").asString).get("name").asString)
            }
        } }
        launch(fixtures.get("link").asString).use { activity -> withFailureCapture({ capture("compact-editor-confirmation-failure") }) {
            openInformation(); openChannelEditor()
            editorField("Channel name").performScrollTo().performTextReplacement("compact-commons")
            assertFocusedFieldVisible("Channel name", "compact-commons")
            editorField("Category (optional)").performScrollTo().performTextReplacement("Projects")
            assertFocusedFieldVisible("Category (optional)", "Projects")
            closeKeyboard(); clickDialog("Save changes")
            waitInformationName("compact-commons")
            capture("28-compact-editor-confirmed")
            restoreGeneralName(fixtures)
            capture("29-compact-editor-restored-channel")
            verifyCompactCreationError(activity, fixtures)
            saveCompletion("compact-completed.json", fixtures)
        } }
    }

    private fun creationRows(value: JsonObject) = value.getAsJsonArray("originals").map { it.asJsonObject }
        .filter { it.get("kind").asString == "create-channel" }

    private fun verifyChannelCreationRecovery(activity: ActivityScenario<MainActivity>, fixtures: JsonObject) {
        openInformation(); clickDialog(context.getString(R.string.spaces_add_channel))
        editorField("Channel name").performScrollTo().performTextReplacement("native-reading-room")
        clickDialog(context.getString(R.string.spaces_kind_chat))
        ui.onNodeWithText(context.getString(R.string.spaces_kind_article)).clickReady()
        editorField("Category (optional)").performScrollTo().performTextReplacement("Knowledge")
        closeKeyboard(); activity.recreate()
        waitUntil { editorField("Channel name").isDisplayed() }
        editorField("Channel name").assertTextContains("native-reading-room")
        editorField("Category (optional)").assertTextContains("Knowledge")
        ui.onNode(hasText(context.getString(R.string.spaces_kind_article)) and hasClickAction() and hasAnyAncestor(isDialog())).assertIsDisplayed()
        capture("30-new-channel-restored-fields")
        command("management-drop-next", JsonObject().apply { addProperty("kind", "create-channel") })
        val started = SystemClock.elapsedRealtime(); val deadline = started + 27_000
        clickDialog("Create channel")
        lateinit var received: JsonObject
        waitUntil(5_000) {
            received = status()
            received.getAsJsonArray("dropped").size() == 4 && received.getAsJsonObject("channels").entrySet().any {
                it.value.asJsonObject.get("name").asString == "native-reading-room" && it.value.asJsonObject.get("category").asString == "Knowledge"
            } && ui.onNode(hasText("Waiting for confirmation.") and hasAnyAncestor(isDialog())).isDisplayed()
        }
        val original = received.getAsJsonArray("dropped").last().asString
        val first = creationRows(received).filter { it.get("id").asString == original }
        assertEquals(1, first.size)
        val channel = first.single().get("channel").asString
        assertEquals("native-reading-room", received.getAsJsonObject("channels").getAsJsonObject(channel).get("name").asString)
        capture("31-new-channel-uncertain")
        clickDialog("Close"); clickDialog(context.getString(R.string.spaces_add_channel))
        editorField("Channel name").assertIsNotEnabled()
        ui.onNode(hasText("Create channel") and hasClickAction() and hasAnyAncestor(isDialog())).assertIsNotEnabled()
        capture("32-new-channel-pending-reentry")
        val remaining = deadline - SystemClock.elapsedRealtime()
        assertTrue("Creation recovery retains its original27-second workflow deadline", remaining > 0)
        waitUntil(remaining) {
            received = status()
            creationRows(received).count { it.get("id").asString == original } == 2 &&
                ui.onNode(hasContentDescription(context.getString(R.string.spaces_switch_channel)) and
                    SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "native-reading-room")).isDisplayed()
        }
        val completed = SystemClock.elapsedRealtime()
        assertTrue("Visible created-channel navigation is inside the recovery budget", completed <= deadline)
        val attempts = creationRows(received).filter { it.get("id").asString == original }
        assertEquals(1, attempts.map { it.get("sha256").asString }.distinct().size)
        assertTrue(attempts.all { it.getAsJsonObject("profile").get("kind").asString == "article" && it.getAsJsonObject("profile").get("category").asString == "Knowledge" })
        saveJson("creation-recovery-host.json", received)
        saveJson("creation-recovery.json", JsonObject().apply {
            addProperty("pid", Process.myPid()); addProperty("owner", fixtures.get("owner").asString)
            addProperty("space", fixtures.get("space").asString); addProperty("channel", channel); addProperty("original", original)
            addProperty("name", "native-reading-room"); addProperty("category", "Knowledge"); addProperty("kind", "article")
            addProperty("recoveryStartedAtMs", started); addProperty("recoveryCompletedAtMs", completed)
        })
        capture("33-new-channel-confirmed-navigation")
        chooseChannel("general")
        waitUntil { ui.onNode(hasSetTextAction() and hasText("New general draft while archive awaits")).isDisplayed() }
        assertEquals("New general draft while archive awaits", savedDraft(fixtures, "general"))
        capture("34-new-channel-return-preserves-draft")
    }

    private fun verifyCompactCreationError(activity: ActivityScenario<MainActivity>, fixtures: JsonObject) {
        clickDialog(context.getString(R.string.spaces_add_channel))
        editorField("Channel name").performScrollTo().performTextReplacement("compact-new-room")
        assertFocusedFieldVisible("Channel name", "compact-new-room")
        closeKeyboard()
        val before = creationRows(status()).size
        withWriteFailure(activity, fixtures, "compact-creation") {
            clickDialog("Create channel"); waitDialogError()
            capture("35-compact-creation-local-error")
            assertEquals(before, creationRows(status()).size)
            // The first Close belongs to the visible local-error message, not the sheet.
            ui.onAllNodes(hasText("Close") and hasClickAction() and hasAnyAncestor(isDialog())).onFirst().clickReady()
            waitUntil(5_000) { ui.onAllNodes(hasText(context.getString(R.string.spaces_save_failed)) and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isEmpty() }
            editorField("Channel name").assertIsEnabled().performScrollTo().performTextReplacement("compact-editable-after-error")
            assertFocusedFieldVisible("Channel name", "compact-editable-after-error")
            ui.onNode(hasText("Create channel") and hasClickAction() and hasAnyAncestor(isDialog())).assertIsEnabled()
            capture("36-compact-creation-error-dismissed-editable")
            clickDialog("Close"); clickDialog("Discard changes")
            waitInformationName("general")
        }
    }

    private fun assertFocusedFieldVisible(label: String, value: String) {
        val field = editorField(label)
        var proof = JsonObject()
        waitUntil(5_000) {
            val imeBottom = focusedWindowImeBottom()
            val physical = if (imeBottom > 0) device.findObjects(By.text(value)).singleOrNull { it.isFocused }?.visibleBounds else null
            if (physical == null || !field.isDisplayed()) false else field.fetchSemanticsNode().let { node ->
                val bounds = node.boundsInRoot
                val root = generateSequence(node) { it.parent }.last().boundsInRoot
                val visible = node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Focused) == true &&
                    node.config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text == value &&
                    node.size.height <= bounds.height + 1f && node.size.width <= bounds.width + 1f &&
                    bounds.left >= root.left - 1f && bounds.top >= root.top - 1f && bounds.right <= root.right + 1f && bounds.bottom <= root.bottom + 1f &&
                    physical.height() >= node.size.height - 1 && physical.width() >= node.size.width - 1 &&
                    physical.left >= 0 && physical.top >= 0 && physical.right <= device.displayWidth && physical.bottom <= device.displayHeight - imeBottom
                if (visible) proof = JsonObject().apply {
                    addProperty("pid", Process.myPid()); addProperty("label", label); addProperty("value", value)
                    addProperty("focused", true); addProperty("fontScale", context.resources.configuration.fontScale)
                    addProperty("width", node.size.width); addProperty("height", node.size.height)
                    addProperty("clippedWidth", bounds.width); addProperty("clippedHeight", bounds.height)
                    add("bounds", JsonArray().apply { add(bounds.left); add(bounds.top); add(bounds.right); add(bounds.bottom) })
                    add("root", JsonArray().apply { add(root.left); add(root.top); add(root.right); add(root.bottom) })
                    addProperty("imeBottom", imeBottom); addProperty("displayWidth", device.displayWidth); addProperty("displayHeight", device.displayHeight)
                    add("physicalBounds", JsonArray().apply { add(physical.left); add(physical.top); add(physical.right); add(physical.bottom) })
                    addProperty("timeMs", SystemClock.elapsedRealtime())
                }
                visible
            }
        }
        File(evidence, "focused-field-geometry.jsonl").appendText(proof.toString() + "\n")
    }

    private fun editorField(label: String) = ui.onNode(
        SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.EditableText) and hasText(label) and hasAnyAncestor(isDialog()))
    private fun editRows(value: JsonObject) = value.getAsJsonArray("originals").map { it.asJsonObject }.filter { it.get("kind").asString == "edit-channel" }
    private fun waitInformationName(name: String, timeout: Long = 15_000) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        val information = isDialog() and hasAnyDescendant(hasText(context.getString(R.string.spaces_information)))
        val match = hasText(name) and !SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.EditableText) and hasAnyAncestor(information)
        waitUntil(timeout) { ui.onAllNodes(match).fetchSemanticsNodes().size == 1 }
        val node = ui.onNode(match)
        if (ui.onAllNodes(match and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().size == 1) node.performScrollTo()
        val remaining = deadline - SystemClock.elapsedRealtime()
        assertTrue("Visible canonical name remains inside its original deadline", remaining > 0)
        waitUntil(remaining) { node.isDisplayed() }
    }
    private fun restoreGeneralName(fixtures: JsonObject) {
        openChannelEditor()
        editorField("Channel name").performScrollTo().performTextReplacement("general")
        editorField("Category (optional)").performScrollTo().performTextReplacement("")
        closeKeyboard(); clickDialog("Save changes"); waitInformationName("general")
        val actual = status().getAsJsonObject("channels").getAsJsonObject(fixtures.get("general").asString)
        assertEquals("general", actual.get("name").asString); assertEquals("", actual.get("category").asString)
    }
    private fun verifyChannelEditorRecovery(activity: ActivityScenario<MainActivity>, fixtures: JsonObject) {
        openInformation(); openChannelEditor()
        editorField("Channel name").performTextReplacement("commons")
        editorField("Category (optional)").performTextReplacement("Projects")
        closeKeyboard(); capture("19-editor-unsaved")
        activity.recreate()
        waitUntil { editorField("Channel name").isDisplayed() && ui.onNode(hasSetTextAction() and hasText("commons") and hasAnyAncestor(isDialog())).isDisplayed() }
        editorField("Category (optional)").assertTextContains("Projects")
        capture("20-editor-restored")
        device.pressBack(); waitText("Discard changes?", 5_000)
        capture("21-editor-discard-choice")
        clickDialog("Keep editing")
        command("management-drop-next", JsonObject().apply { addProperty("kind", "edit-channel") })
        val recoveryStarted = SystemClock.elapsedRealtime()
        val recoveryDeadline = recoveryStarted + 27_000
        clickDialog("Save changes"); waitText("Waiting for confirmation.", 5_000)
        var received = JsonObject()
        waitUntil(5_000) {
            received = status()
            val general = received.getAsJsonObject("channels").getAsJsonObject(fixtures.get("general").asString)
            received.getAsJsonArray("dropped").size() == 3 && general.get("name").asString == "commons" && general.get("category").asString == "Projects"
        }
        val original = received.getAsJsonArray("dropped").last().asString
        val remote = received.getAsJsonObject("channels").getAsJsonObject(fixtures.get("general").asString)
        assertEquals("commons", remote.get("name").asString); assertEquals("Projects", remote.get("category").asString)
        clickDialog("Close"); openChannelEditor()
        waitText("Waiting for confirmation.", 5_000)
        editorField("Channel name").assertTextContains("commons")
        ui.onNode(hasText("Save changes") and hasClickAction() and hasAnyAncestor(isDialog())).assertIsNotEnabled()
        capture("22-editor-pending-reentry")
        val remaining = recoveryDeadline - SystemClock.elapsedRealtime()
        assertTrue("The editor recovery budget remains after pending reentry", remaining > 0)
        waitUntil(remaining) {
            received = status()
            val attempts = editRows(received).filter { it.get("id").asString == original }
            attempts.size == 2 && ui.onAllNodes(hasText("Waiting for confirmation.") and onScreen).fetchSemanticsNodes().isEmpty()
        }
        val attempts = editRows(received).filter { it.get("id").asString == original }
        assertEquals(2, attempts.size); assertEquals(1, attempts.map { it.get("sha256").asString }.distinct().size)
        if (editorField("Channel name").isDisplayed()) clickDialog("Close")
        val confirmationRemaining = recoveryDeadline - SystemClock.elapsedRealtime()
        assertTrue("Confirmed metadata remains inside the original recovery budget", confirmationRemaining > 0)
        waitInformationName("commons", confirmationRemaining)
        val recoveryCompleted = SystemClock.elapsedRealtime()
        assertTrue("The complete editor recovery takes at most27 seconds", recoveryCompleted <= recoveryDeadline)
        saveJson("editor-recovery-host.json", received)
        saveJson("editor-recovery.json", JsonObject().apply {
            addProperty("pid", Process.myPid()); addProperty("owner", fixtures.get("owner").asString)
            addProperty("space", fixtures.get("space").asString); addProperty("channel", fixtures.get("general").asString)
            addProperty("original", original); addProperty("name", "commons"); addProperty("category", "Projects")
            // These are configured components; the independently retained
            // measurement covers their combined budget through visible confirmation.
            addProperty("peerDeadlineSeconds", 12); addProperty("freshReadSeconds", 15); addProperty("combinedRecoverySeconds", 27)
            addProperty("recoveryStartedAtMs", recoveryStarted); addProperty("recoveryCompletedAtMs", recoveryCompleted)
        })
        capture("23-editor-confirmed")
        device.pressBack()
        waitUntil(5_000) { ui.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithContentDescription(context.getString(R.string.spaces_switch_channel)).clickReady()
        val list = ui.onNode(hasScrollToNodeAction() and hasAnyAncestor(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.PaneTitle, context.getString(R.string.spaces_navigation_panel))))
        list.performScrollToNode(hasText("Projects", ignoreCase = true))
        ui.onNode(hasText("Projects", ignoreCase = true)).assertIsDisplayed()
        capture("24-editor-category-drawer")
        list.performScrollToNode(hasText("commons") and hasClickAction())
        drawerChannel("commons").clickReady()
        waitUntil { ui.onNode(hasSetTextAction() and hasText("New general draft while archive awaits")).isDisplayed() }
        openInformation(); restoreGeneralName(fixtures)
        capture("25-editor-restored-channel")
        device.pressBack()
        waitUntil(5_000) { ui.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
        ui.onNode(hasSetTextAction() and hasText("New general draft while archive awaits")).assertIsDisplayed()
    }

    private fun withWriteFailure(activity: ActivityScenario<MainActivity>, fixtures: JsonObject, label: String, action: () -> Unit) {
        val candidates = context.noBackupFilesDir.listFiles().orEmpty().filter { it.name.matches(Regex("spaces-[0-9a-f]{64}\\.sqlite")) }
        assertEquals("One owned account/service store in this disposable baseline", 1, candidates.size)
        val file = candidates.single()
        check(file.canonicalFile.parentFile == context.noBackupFilesDir.canonicalFile)
        val space = fixtures.get("space").asString.also { require(it.matches(Regex("[0-9a-f]{64}"))) }
        val trigger = "spaces_owned_management_write_failure"
        fun database() = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
        database().use { db ->
            db.rawQuery("SELECT name FROM sqlite_master WHERE type='trigger' AND name=?", arrayOf(trigger)).use { assertFalse(it.moveToFirst()) }
            db.execSQL("CREATE TRIGGER $trigger BEFORE INSERT ON entries WHEN NEW.key='$space/state' BEGIN SELECT RAISE(ABORT,'Owned management storage failure'); END")
        }
        var primary: Throwable? = null
        try { action() } catch (failure: Throwable) { primary = failure; throw failure }
        finally { cleanupAfterTest(primary, { activity.close() }, {
            database().use { db ->
                db.execSQL("DROP TRIGGER $trigger")
                db.rawQuery("SELECT name FROM sqlite_master WHERE type='trigger' AND name=?", arrayOf(trigger)).use { assertFalse(it.moveToFirst()) }
            }
            saveJson("cleanup-$label.json", JsonObject().apply {
                addProperty("removed", true); addProperty("pid", Process.myPid()); addProperty("space", space)
                addProperty("owner", AndroidWalletStore(context).loadExisting().publicKey)
            })
        }) }
    }

    private fun openInformation() {
        waitUntil(5_000) { ui.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
        ui.onNode(hasText(context.getString(R.string.spaces_information)) and hasClickAction()).clickReady()
    }
    private fun openMembers(fixtures: JsonObject) {
        openInformation(); clickDialog("Members")
        val deadline = SystemClock.elapsedRealtime() + 15_000
        val list = history()
        val key = fixtures.get("member").asString
        waitUntil(15_000) { list.fetchSemanticsNode().config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.IndexForKey)?.invoke(key)?.let { it >= 0 } == true }
        list.performScrollToKey(key)
        val remaining = deadline - SystemClock.elapsedRealtime()
        assertTrue("The actual member remains inside15seconds", remaining > 0)
        waitText("MemberOfTheLab@saito", remaining)
    }
    private fun openRole() {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        val node = ui.onNode(hasText("Manage role") and hasClickAction())
        node.performScrollTo()
        val remaining = deadline - SystemClock.elapsedRealtime()
        assertTrue("Role action remains inside its original5second budget", remaining > 0)
        node.clickReady(remaining, scrollIntoView = true)
        assertTrue("Scrolling and opening the role action take at most5seconds", SystemClock.elapsedRealtime() <= deadline)
    }
    private fun dismissRole() { device.pressBack(); waitUntil(5_000) { !ui.onNode(hasText("Make administrator") and hasClickAction()).isDisplayed() } }
    private fun closeInformationIfPresent() {
        waitUntil(5_000) { ui.onAllNodes(isDialog()).fetchSemanticsNodes().size == 1 }
        device.pressBack()
        waitUntil(5_000) { ui.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
    }
    private fun chooseChannel(name: String, key: String? = null) {
        waitUntil(5_000) { ui.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithContentDescription(context.getString(R.string.spaces_switch_channel)).clickReady()
        val list = ui.onNode(hasScrollToNodeAction() and hasAnyAncestor(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.PaneTitle, context.getString(R.string.spaces_navigation_panel))))
        if (key != null) {
            val deadline = SystemClock.elapsedRealtime() + 15_000
            waitUntil(15_000) {
                list.fetchSemanticsNode().config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.IndexForKey)?.invoke(key)?.let { it >= 0 } == true
            }
            list.performScrollToKey(key)
            val remaining = deadline - SystemClock.elapsedRealtime()
            assertTrue("The authenticated channel appears inside15seconds", remaining > 0)
            waitUntil(remaining) { drawerChannel(name).isDisplayed() }
            assertTrue(SystemClock.elapsedRealtime() <= deadline)
        } else list.performScrollToNode(hasText(name) and hasClickAction())
        drawerChannel(name).clickReady()
        waitUntil { ui.onNode(hasSetTextAction() and isEnabled()).isDisplayed() }
    }
    private fun drawerChannel(name: String) = ui.onNode(hasText(name) and hasClickAction() and hasAnyAncestor(
        SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.PaneTitle, context.getString(R.string.spaces_navigation_panel))))
    private fun clickDialog(text: String) {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        val match = hasText(text) and hasClickAction() and hasAnyAncestor(isDialog())
        waitUntil(5_000) { ui.onAllNodes(match).fetchSemanticsNodes().size == 1 }
        val node = ui.onNode(match)
        val scrollable = ui.onAllNodes(match and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().size == 1
        val remaining = deadline - SystemClock.elapsedRealtime()
        assertTrue("The dialog action remains inside5seconds", remaining > 0)
        node.clickReady(remaining, scrollIntoView = scrollable)
        assertTrue(SystemClock.elapsedRealtime() <= deadline)
    }
    private fun waitDialogButton(text: String) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        val match = hasText(text) and hasClickAction() and isEnabled() and hasAnyAncestor(isDialog())
        waitUntil(15_000) { ui.onAllNodes(match).fetchSemanticsNodes().size == 1 }
        val node = ui.onNode(match)
        if (ui.onAllNodes(match and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().size == 1) node.performScrollTo()
        val remaining = deadline - SystemClock.elapsedRealtime()
        assertTrue("Visible confirmed action remains inside15seconds", remaining > 0)
        waitUntil(remaining) { node.isDisplayed() }
    }
    private fun waitDialogError() = waitUntil(5_000) {
        val node = ui.onNode(hasText(saveError) and hasAnyAncestor(isDialog()))
        node.isDisplayed() && node.fetchSemanticsNode().let { it.size.height <= it.boundsInRoot.height + 1f }
    }
    private fun history() = ui.onNode(hasScrollToNodeAction() and onScreen)
    private fun closeKeyboard() { ui.waitForIdle(); androidx.test.espresso.Espresso.closeSoftKeyboard() }
    private fun pixel(text: String) = ui.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top
    private fun assertPixel(text: String, expected: Float) = assertEquals("Actual channel reading pixel is preserved", expected, pixel(text), 1f)
    private val onScreen get() = SemanticsMatcher("on-screen management route") { it.boundsInRoot.left >= 0 && it.boundsInRoot.top >= 0 && it.boundsInRoot.right > 0 }
    private fun SemanticsNodeInteraction.clickReady(timeout: Long = 5_000, scrollIntoView: Boolean = false) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        waitUntil(timeout) {
            if (scrollIntoView) performScrollTo()
            if (!isDisplayed()) false else fetchSemanticsNode().let { node ->
                val bounds = node.boundsInRoot
                val touch = node.touchBoundsInRoot
                val density = node.layoutInfo.density.density
                val root = generateSequence(node) { it.parent }.last().boundsInRoot
                !node.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) &&
                    touch.height / density >= 47.5f && touch.width / density >= 47.5f &&
                    node.size.height <= bounds.height + 1f && node.size.width <= bounds.width + 1f &&
                    touch.left >= root.left - 1f && touch.top >= root.top - 1f &&
                    touch.right <= root.right + 1f && touch.bottom <= root.bottom + 1f
            }
        }
        assertTrue("The complete actionable control settles inside its original deadline", SystemClock.elapsedRealtime() <= deadline)
        val node = fetchSemanticsNode()
        val clipped = node.boundsInRoot
        val touch = node.touchBoundsInRoot
        val density = node.layoutInfo.density.density
        val root = generateSequence(node) { it.parent }.last().boundsInRoot
        fun rectangle(value: androidx.compose.ui.geometry.Rect) = JsonArray().apply {
            add(value.left); add(value.top); add(value.right); add(value.bottom)
        }
        val geometry = JsonObject().apply {
            addProperty("pid", Process.myPid()); addProperty("node", node.id)
            addProperty("density", density); addProperty("timeMs", SystemClock.elapsedRealtime())
            addProperty("layoutWidth", node.size.width); addProperty("layoutHeight", node.size.height)
            add("clipped", rectangle(clipped)); add("touch", rectangle(touch)); add("root", rectangle(root))
        }
        File(evidence, "control-geometry.jsonl").appendText(geometry.toString() + "\n")
        assertTrue("The actual touch target is at least48dp: $geometry", touch.height / density >= 47.5f && touch.width / density >= 47.5f)
        assertTrue("The complete layout remains visible: $geometry", node.size.height <= clipped.height + 1f && node.size.width <= clipped.width + 1f)
        assertTrue("The touch target stays inside its window: $geometry", touch.left >= root.left - 1f && touch.top >= root.top - 1f &&
            touch.right <= root.right + 1f && touch.bottom <= root.bottom + 1f)
        assertIsEnabled().performClick()
        assertTrue("The actual click completes inside its original deadline", SystemClock.elapsedRealtime() <= deadline)
    }
    private fun waitText(text: String, timeout: Long = 15_000) = waitUntil(timeout) { ui.onNode(hasText(text) and onScreen).isDisplayed() }
    private fun waitUntil(timeout: Long = 15_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        ui.waitUntil(timeout, condition)
        assertTrue("The observed condition completes inside its original deadline", SystemClock.elapsedRealtime() <= deadline)
    }
    private fun capture(name: String) = captureNativeScreen(File(evidence, "$name.png"))
    private fun status() = command(if (privateReadiness) "readiness-status" else if (privateManagement) "private-management-status" else "management-status")
    private fun savedDraft(fixtures: JsonObject, channel: String): String {
        val route = "${SaitoBackend.OWNER}:$peerEndpoint"
        val namespace = MessageDigest.getInstance("SHA-256")
            .digest("SpacesLocal/v1:${fixtureOwner.publicKey}:$route:$fixtureService".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        check(File(context.noBackupFilesDir, "spaces-$namespace.sqlite").isFile) { "The declared owned application store must already exist" }
        return AndroidSpaceClientStore(context, fixtureOwner.publicKey, route, fixtureService).use { store ->
            SpaceClient(fixtureOwner, SaitoBackend.OWNER.webUrl, fixtureService, store, Clock.systemUTC()) { error("No checkpoint transport") }
                .draft(fixtures.get("space").asString, fixtures.get(channel).asString)
        }
    }
    private fun SemanticsNodeInteraction.confirmedToggle(checked: Boolean): Boolean = isDisplayed() && fetchSemanticsNode().config.let {
        it[androidx.compose.ui.semantics.SemanticsProperties.ToggleableState] ==
            (if (checked) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off) &&
            !it.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
    }
    private fun assertRetriedOriginal(previous: JsonObject, kind: String, receivedInUi: () -> Boolean = { true }) {
        waitUntil {
            val actual = status(); val id = previous.get("original").asString
            val rows = actual.getAsJsonArray("originals").map { it.asJsonObject }.filter { it.get("id").asString == id }
            rows.size == 2 && rows.all { it.get("kind").asString == kind } && rows.map { it.get("sha256").asString }.distinct().size == 1 && receivedInUi()
        }
    }
    private fun saveJson(name: String, value: JsonObject) { File(evidence, name).writeText(value.toString()) }
    private fun publishCheckpoint(phase: String, fixtures: JsonObject, pixel: Float): Nothing {
        val remote = status()
        val dropped = remote.getAsJsonArray("dropped").last().asString
        val value = JsonObject().apply {
            addProperty("phase", phase); addProperty("pid", Process.myPid()); addProperty("pixel", pixel)
            addProperty("owner", AndroidWalletStore(context).loadExisting().publicKey)
            addProperty("backend", AndroidBackendStore(context).load().id); addProperty("original", dropped)
            add("fixtures", fixtures)
        }
        saveJson("checkpoint-$phase.json", value)
        val temporary = File(context.cacheDir, "$evidenceLabel-checkpoint.tmp")
        temporary.writeText(value.toString()); check(temporary.renameTo(checkpoint))
        while (true) SystemClock.sleep(250)
    }
    private fun readCheckpoint(phase: String) = JsonParser.parseString(checkpoint.readText()).asJsonObject.also {
        assertEquals(phase, it.get("phase").asString); assertNotEquals(it.get("pid").asInt, Process.myPid())
        assertEquals(it.get("owner").asString, AndroidWalletStore(context).loadExisting().publicKey)
        assertEquals(it.get("backend").asString, AndroidBackendStore(context).load().id)
    }
    private fun saveCompletion(name: String, fixtures: JsonObject) {
        val remote = status()
        assertEquals("MEMBER", remote.getAsJsonObject("members").get(fixtures.get("member").asString).asString)
        if (privateManagement) {
            val channel = fixtures.get("private").asString
            assertEquals(26, remote.getAsJsonObject("members").size())
            assertTrue(remote.getAsJsonObject("channels").getAsJsonObject(channel).get("ready").asBoolean)
            assertFalse(remote.getAsJsonArray("outsiderChannels").any { it.asString == channel })
            assertEquals(listOf("A native encrypted message before removal", "A native encrypted message after fresh admission"),
                remote.getAsJsonArray("privateMessages").map { it.asString })
        } else assertTrue(remote.getAsJsonObject("channels").entrySet().all { !it.value.asJsonObject.get("archived").asBoolean })
        saveJson(if (name == "completed.json") "normal-host.json" else "compact-host.json", remote)
        saveJson(name, JsonObject().apply {
            addProperty("pid", Process.myPid()); addProperty("owner", AndroidWalletStore(context).loadExisting().publicKey)
            addProperty("fontScale", context.resources.configuration.fontScale); addProperty("screenWidthDp", context.resources.configuration.screenWidthDp)
        })
    }
    private fun command(action: String, body: JsonObject = JsonObject()): JsonObject {
        val connection = URL("http://127.0.0.1:${port("spacesControlPort")}/$action").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"; connection.doOutput = true; connection.connectTimeout = 5_000; connection.readTimeout = 5_000
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            assertEquals("Owned management command", 200, connection.responseCode)
            return connection.inputStream.bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        } finally { connection.disconnect() }
    }
}
