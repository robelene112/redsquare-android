package nl.davidengelen.redsquare

import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
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
    private val evidence get() = File(context.cacheDir, "spaces-management-evidence").apply { mkdirs() }
    private val checkpoint get() = File(context.cacheDir, "spaces-management-checkpoint.json")
    private val fixtureOwner get() = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
    private val fixtureService get() = SaitoWallet.fromPrivateKey("0".repeat(63) + "2").publicKey
    private val saveError get() = context.getString(R.string.spaces_save_failed)
    private fun launch(link: String) = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW; data = Uri.parse(link)
        putExtra("feedPeerUrl", "ws://127.0.0.1:${port("spacesPeerPort")}/wsopen")
        putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${port("spacesPeerPort")}/wsopen")
        putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/")
    })

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
            openMembers(); openRole()
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
            openMembers(); openRole()
            command("management-drop-next", JsonObject().apply { addProperty("kind", "set-role") })
            clickDialog("Make administrator")
            waitText("Waiting for confirmation.", 5_000)
            var received = JsonObject()
            waitUntil(5_000) { received = status(); received.getAsJsonArray("dropped").size() == 1 }
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
            openMembers(); openRole()
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
            waitUntil(5_000) { received = status(); received.getAsJsonArray("dropped").size() == 2 }
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
            waitUntil { ui.onNode(hasText("Archive channel") and hasClickAction() and isEnabled()).isDisplayed() }
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
            waitText("Management message 5")
            chooseChannel("announcements"); openInformation(); clickDialog("Archive channel")
            waitUntil { ui.onNode(hasText("Reopen channel") and hasClickAction() and isEnabled()).isDisplayed() }
            device.pressBack()
            waitText(context.getString(R.string.spaces_channel_archived))
            ui.onNodeWithText(context.getString(R.string.spaces_announcements_read_only)).assertDoesNotExist()
            capture("12-owner-archived-announcement")
            openInformation(); clickDialog("Reopen channel")
            waitUntil { ui.onNode(hasText("Archive channel") and hasClickAction() and isEnabled()).isDisplayed() }
            device.pressBack(); chooseChannel("general")
            ui.onNode(hasSetTextAction() and hasText("New general draft while archive awaits")).assertIsDisplayed()
            capture("13-normal-management-return")
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
            openMembers(); openRole()
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
            openMembers(); openRole(); clickDialog("Make administrator")
            waitUntil { ui.onNode(hasText("Remove administrator role") and hasClickAction() and isEnabled()).isDisplayed() }
            capture("16-compact-confirmed-administrator")
            clickDialog("Remove administrator role")
            waitUntil { ui.onNode(hasText("Make administrator") and hasClickAction() and isEnabled()).isDisplayed() }
            dismissRole(); device.pressBack(); closeInformationIfPresent(); openInformation()
            clickDialog("Archive channel")
            waitUntil { ui.onNode(hasText("Reopen channel") and hasClickAction() and isEnabled()).isDisplayed() }
            capture("17-compact-archived-channel-settings")
            clickDialog("Reopen channel")
            waitUntil { ui.onNode(hasText("Archive channel") and hasClickAction() and isEnabled()).isDisplayed() }
            capture("18-compact-reopened-channel")
            saveCompletion("compact-completed.json", fixtures)
        } }
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
    private fun openMembers() { openInformation(); clickDialog("Members"); waitText("MemberOfTheLab@saito") }
    private fun openRole() { ui.onNode(hasText("Manage role") and hasClickAction()).clickReady() }
    private fun dismissRole() { device.pressBack(); waitUntil(5_000) { !ui.onNode(hasText("Make administrator") and hasClickAction()).isDisplayed() } }
    private fun closeInformationIfPresent() {
        waitUntil(5_000) { ui.onNode(hasText("Members") and hasClickAction() and hasAnyAncestor(isDialog())).isDisplayed() }
        device.pressBack()
        waitUntil(5_000) { ui.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
    }
    private fun chooseChannel(name: String) {
        waitUntil(5_000) { ui.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithContentDescription(context.getString(R.string.spaces_switch_channel)).clickReady()
        val list = ui.onNode(hasScrollToNodeAction() and hasAnyAncestor(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.PaneTitle, context.getString(R.string.spaces_navigation_panel))))
        list.performScrollToNode(hasText(name) and hasClickAction())
        ui.onNode(hasText(name) and hasClickAction()).clickReady()
        waitUntil { ui.onNode(hasSetTextAction() and isEnabled()).isDisplayed() }
    }
    private fun clickDialog(text: String) {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        val match = hasText(text) and hasClickAction() and hasAnyAncestor(isDialog())
        waitUntil(5_000) { ui.onAllNodes(match).fetchSemanticsNodes().size == 1 }
        val node = ui.onNode(match)
        if (!node.isDisplayed()) node.performScrollTo()
        node.clickReady((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1))
    }
    private fun waitDialogError() = waitUntil(5_000) {
        val node = ui.onNode(hasText(saveError) and hasAnyAncestor(isDialog()))
        node.isDisplayed() && (node.getUnclippedBoundsInRoot().bottom - node.getUnclippedBoundsInRoot().top).value * context.resources.displayMetrics.density <=
            node.fetchSemanticsNode().boundsInRoot.height + 1f
    }
    private fun history() = ui.onNode(hasScrollToNodeAction() and onScreen)
    private fun closeKeyboard() { ui.waitForIdle(); androidx.test.espresso.Espresso.closeSoftKeyboard() }
    private fun pixel(text: String) = ui.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top
    private fun assertPixel(text: String, expected: Float) = assertEquals("Actual channel reading pixel is preserved", expected, pixel(text), 1f)
    private val onScreen get() = SemanticsMatcher("on-screen management route") { it.boundsInRoot.left >= 0 && it.boundsInRoot.top >= 0 && it.boundsInRoot.right > 0 }
    private fun SemanticsNodeInteraction.clickReady(timeout: Long = 5_000) {
        waitUntil(timeout) { isDisplayed() && !fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) }
        val raw = getUnclippedBoundsInRoot()
        val clipped = fetchSemanticsNode().boundsInRoot
        val density = context.resources.displayMetrics.density
        assertTrue("The actual control is at least48dp", (raw.bottom - raw.top).value >= 47.5f && (raw.right - raw.left).value >= 47.5f)
        assertTrue("The complete control remains visible", (raw.bottom - raw.top).value * density <= clipped.height + 1f)
        assertIsEnabled().performClick()
    }
    private fun waitText(text: String, timeout: Long = 15_000) = waitUntil(timeout) { ui.onNode(hasText(text) and onScreen).isDisplayed() }
    private fun waitUntil(timeout: Long = 15_000, condition: () -> Boolean) = ui.waitUntil(timeout, condition)
    private fun capture(name: String) = captureNativeScreen(File(evidence, "$name.png"))
    private fun status() = command("management-status")
    private fun savedDraft(fixtures: JsonObject, channel: String): String = AndroidSpaceClientStore(context,
        fixtureOwner.publicKey, SaitoBackend.OWNER.webUrl, fixtureService).use { store ->
        SpaceClient(fixtureOwner, SaitoBackend.OWNER.webUrl, fixtureService, store, Clock.systemUTC()) { error("No checkpoint transport") }
            .draft(fixtures.get("space").asString, fixtures.get(channel).asString)
    }
    private fun assertRetriedOriginal(previous: JsonObject, kind: String) {
        waitUntil {
            val actual = status(); val id = previous.get("original").asString
            val rows = actual.getAsJsonArray("originals").map { it.asJsonObject }.filter { it.get("id").asString == id }
            rows.size == 2 && rows.all { it.get("kind").asString == kind } && rows.map { it.get("sha256").asString }.distinct().size == 1
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
        val temporary = File(context.cacheDir, "spaces-management-checkpoint.tmp")
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
        assertTrue(remote.getAsJsonObject("channels").entrySet().all { !it.value.asJsonObject.get("archived").asBoolean })
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
