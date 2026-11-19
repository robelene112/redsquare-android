package nl.davidengelen.redsquare

import android.content.Context
import android.content.Intent
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
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Real Activity/peer/MLS/store. The external driver kills each checkpoint PID. */
class SpacesDiscoveryJourneyTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext.also {
        check(it.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
    }
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val peerPort get() = requireNotNull(arguments.getString("spacesPeerPort")).toInt().also { check(it in 1024..65535) }
    private val controlPort get() = requireNotNull(arguments.getString("spacesControlPort")).toInt().also { check(it in 1024..65535) }
    private val device get() = UiDevice.getInstance(instrumentation)
    private val evidence get() = File(context.cacheDir, "spaces-discovery-evidence").apply { mkdirs() }
    private val checkpoint get() = File(context.cacheDir, "spaces-discovery-checkpoint.json")
    private fun intent(link: String? = null) = Intent(context, MainActivity::class.java)
        .putExtra("feedPeerUrl", "ws://127.0.0.1:$peerPort/wsopen")
        .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:$peerPort/wsopen")
        .putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/").apply {
            if (link != null) { action = Intent.ACTION_VIEW; data = Uri.parse(link) }
        }
    private fun launch(link: String? = null) = ActivityScenario.launch<MainActivity>(intent(link))

    @Test fun publicDiscoveryReadsWithoutJoiningAndCheckpointsTheActualVisitorPixel() {
        val wallet = AndroidWalletStore(context).loadOrCreate()
        val fixtures = command("fixtures")
        launch().use { activity -> withFailureCapture({ capture("visitor-failure") }) {
            switchModule("Red Square", "Spaces")
            capture("01-my-spaces-b")
            waitUntil { ui.onNode(hasText("Create a Space") and hasClickAction() and isEnabled()).isDisplayed() }
            ui.onNode(hasText("Create a Space") and hasClickAction()).performClick()
            ui.onNodeWithText("Space name").performTextInput("Navigation origin")
            closeKeyboard()
            ui.onNode(hasText("Create a Space") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
            waitUntil { ui.onNode(hasSetTextAction() and isEnabled()).isDisplayed() }
            ui.onNode(hasSetTextAction()).performTextReplacement("Keep the chosen origin draft")
            closeKeyboard()
            waitUntil(20_000) { ui.onNode(hasContentDescription("Send message") and isEnabled()).isDisplayed() }
            ui.onNodeWithContentDescription("Back to your communities").performClick()
            ui.onNode(hasText("Discover") and hasClickAction()).performClick()
            waitText("Saito Builders")
            ui.onNodeWithText("Private Builders").assertDoesNotExist()
            ui.onNodeWithText("Independent Reading").assertDoesNotExist()
            capture("02-real-discovery")
            command("hold-public"); openRow("Saito Builders")
            waitUntil(5_000) { command("held-public").get("entered").asBoolean }
            ui.onNode(hasText("My Spaces") and hasClickAction()).performClick()
            openRow("Navigation origin")
            waitUntil(5_000) { ui.onNode(hasSetTextAction() and hasText("Keep the chosen origin draft")).isDisplayed() }
            command("release-public")
            // Send is disabled by the held operation. Its re-enablement proves
            // completion while the newly chosen route stays untouched.
            waitUntil(10_000) { ui.onNode(hasContentDescription("Send message") and isEnabled()).isDisplayed() }
            ui.onNode(hasText("Navigation origin") and onScreen).assertIsDisplayed()
            ui.onNode(hasSetTextAction() and hasText("Keep the chosen origin draft")).assertIsDisplayed()
            ui.onNodeWithText("Join this Space").assertDoesNotExist()
            capture("02a-late-public-read-keeps-new-navigation")
            ui.onNodeWithContentDescription("Back to your communities").performClick()
            ui.onNode(hasText("Discover") and hasClickAction()).performClick()
            openRow("Saito Builders")
            waitText("Builders message 27")
            ui.onNode(hasText("Join this Space") and hasClickAction()).assertIsDisplayed()
            assertFalse(members(fixtures, "public").contains(wallet.publicKey))
            capture("03-public-reading-no-membership")
            history().performScrollToNode(hasText("Builders message 10"))
            val pixel = ui.onNodeWithText("Builders message 10").fetchSemanticsNode().boundsInRoot.top
            capture("04-public-visitor-pixel")
            switchModule("Spaces", "Red Square"); switchModule("Red Square", "Spaces")
            waitText("Builders message 10"); assertPixel("Builders message 10", pixel)
            activity.recreate()
            waitText("Builders message 10"); assertPixel("Builders message 10", pixel)
            assertFalse(members(fixtures, "public").contains(wallet.publicKey))
            capture("05-recreated-visitor-pixel")
            publishCheckpoint("visitor", fixtures, pixel)
        } }
    }

    @Test fun coldPublicReferenceReviewsRulesAndKeepsPerChannelDraftsAndMemberReturns() {
        val saved = readCheckpoint("visitor")
        val fixtures = command("fixtures")
        val link = fixtures.getAsJsonObject("public").get("link").asString
        launch(link).use { activity -> withFailureCapture({ capture("member-failure") }) {
            waitText("Builders message 10"); assertPixel("Builders message 10", saved.get("pixel").asFloat)
            capture("06-cold-public-reference-pixel")
            ui.onNode(hasText("Join this Space") and hasClickAction()).performClick()
            waitText("Be kind. Share ideas, never private keys.", 5_000)
            val join = hasText("Join this Space") and hasClickAction() and hasAnyAncestor(isDialog())
            ui.onNode(join).assertIsNotEnabled()
            ui.onNodeWithText("I have read and accept these community rules").performClick()
            ui.onNode(join).assertIsEnabled().performClick()
            waitUntil { members(fixtures, "public").contains(saved.get("owner").asString) }
            waitUntil { ui.onNode(hasSetTextAction() and isEnabled()).isDisplayed() }
            capture("07-reviewed-real-public-join")
            ui.onNode(hasSetTextAction()).performTextReplacement("General draft kept in its own channel")
            closeKeyboard()
            chooseChannel("workshop")
            ui.onNode(hasSetTextAction()).performTextReplacement("Workshop draft kept in its own channel")
            closeKeyboard()
            chooseChannel("general")
            waitUntil { ui.onNode(hasSetTextAction() and hasText("General draft kept in its own channel")).isDisplayed() }
            history().performScrollToNode(hasText("Builders message 10"))
            val pixel = ui.onNodeWithText("Builders message 10").fetchSemanticsNode().boundsInRoot.top
            // Preserve a real pre-existing Chat overlay before the external member route.
            switchModule("Spaces", "Red Square")
            ui.onNode(hasText("Chat") and hasClickAction()).performClick()
            waitUntil(5_000) { ui.onNode(hasText("Chat") and !hasClickAction()).isDisplayed() }
            switchModule("Red Square", "Spaces")
            ui.onNodeWithContentDescription("Community information").performClick()
            ui.onNode(hasText("Members") and hasClickAction()).performScrollTo().performClick()
            waitText("Owner"); waitText("BuildersHost@saito")
            capture("08-real-members")
            ui.onNode(hasText("Owner") and hasClickAction()).performClick()
            waitText("Profile")
            capture("09-member-profile")
            device.pressBack(); waitText("Members", 5_000)
            val memberChat = SemanticsMatcher("member private chat action") { node ->
                node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)?.any { it.startsWith("Message ") } == true
            } and hasClickAction()
            ui.onNode(memberChat).performClick()
            waitUntil { ui.onNodeWithContentDescription("Switch app: Red Square").isDisplayed() && ui.onNode(hasSetTextAction() and hasText("Message")).isDisplayed() }
            capture("10-member-private-chat")
            device.pressBack(); waitText("Members", 5_000)
            device.pressBack(); waitText("Community information", 5_000)
            ui.onNode(hasText("Close") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
            waitText("Builders message 10"); assertPixel("Builders message 10", pixel)
            chooseChannel("workshop")
            waitUntil { ui.onNode(hasSetTextAction() and hasText("Workshop draft kept in its own channel")).isDisplayed() }
            chooseChannel("general")
            waitText("Builders message 10"); assertPixel("Builders message 10", pixel)
            capture("11-channel-drafts-and-pixel")
            publishCheckpoint("member", fixtures, pixel)
        } }
    }

    @Test fun coldMemberReturnCreatesAndReviewsInvitationsAndReadsUnlistedLinks() {
        val saved = readCheckpoint("member")
        val fixtures = command("fixtures")
        launch().use { activity -> withFailureCapture({ capture("final-failure") }) {
            switchModule("Red Square", "Spaces"); openRow("Saito Builders")
            waitUntil { ui.onNode(hasSetTextAction() and hasText("General draft kept in its own channel")).isDisplayed() }
            waitText("Builders message 10"); assertPixel("Builders message 10", saved.get("pixel").asFloat)
            chooseChannel("workshop")
            waitUntil { ui.onNode(hasSetTextAction() and hasText("Workshop draft kept in its own channel")).isDisplayed() }
            capture("12-cold-member-drafts")
            // A current public selection must not dismiss another invitation's review/error.
            openLink(activity, fixtures.getAsJsonObject("private").get("link").asString)
            waitText("Private Builders")
            val join = hasText("Join community") and hasClickAction() and hasAnyAncestor(isDialog())
            ui.onNode(join).assertIsNotEnabled()
            capture("13-private-rules-preview")
            ui.onNode(hasText("Use another invitation") and hasClickAction()).performScrollTo().performClick()
            waitUntil(5_000) { ui.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).isDisplayed() }
            command("expire")
            ui.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextReplacement(fixtures.getAsJsonObject("expiring").get("link").asString)
            ui.onNode(hasText("View invitation") and hasClickAction()).performClick()
            waitText("This invitation has expired. Create a new one to invite people.")
            capture("14-expired-invitation-over-public-selection")
            command("reset-clock")
            ui.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextReplacement(fixtures.getAsJsonObject("private").get("link").asString)
            ui.onNode(hasText("View invitation") and hasClickAction()).performClick()
            waitUntil { ui.onAllNodesWithText("Respect this private room.").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText("Respect this private room.").performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("I have read and accept these community rules").performScrollTo().performClick()
            ui.onNode(join).assertIsEnabled().performClick()
            waitUntil { members(fixtures, "private").contains(saved.get("owner").asString) }
            waitUntil { ui.onNode(hasSetTextAction() and isEnabled()).isDisplayed() }
            capture("15-private-real-join")
            command("revoke-private")
            chooseChannel("design")
            ui.onNode(hasSetTextAction()).performTextReplacement("Private child survives invitation revocation")
            closeKeyboard()
            waitUntil { ui.onNode(hasContentDescription("Send message") and isEnabled()).isDisplayed() }
            ui.onNodeWithContentDescription("Send message").performClick()
            waitText("Private child survives invitation revocation")
            var receivedChild = JsonObject()
            waitUntil(20_000) {
                val received = command("channel-received", JsonObject().apply {
                    addProperty("space", fixtures.getAsJsonObject("private").get("space").asString); addProperty("channel", "design")
                }).getAsJsonArray("received").map { it.asJsonObject }.filter {
                    it.get("text").asString == "Private child survives invitation revocation" && it.get("author").asString == saved.get("owner").asString
                }
                if (received.size == 1) receivedChild = received.single()
                received.size == 1
            }
            assertTrue(receivedChild.get("id").asString.matches(Regex("[0-9a-f]{128}")))
            evidence.resolve("private-child-received.json").writeText(receivedChild.toString())
            capture("16-private-child-after-revocation")
            openLink(activity, fixtures.getAsJsonObject("nonchat").get("link").asString)
            waitText("Open Reading Room")
            ui.onNode(hasText("Join this Space") and hasClickAction()).assertIsDisplayed().performClick()
            waitUntil { members(fixtures, "nonchat").contains(saved.get("owner").asString) }
            capture("17-nonchat-public-join")
            openLink(activity, fixtures.getAsJsonObject("unlisted").get("link").asString)
            waitText("Independent Reading")
            ui.onNode(hasText("Join this Space") and hasClickAction()).assertIsDisplayed()
            assertFalse(members(fixtures, "unlisted").contains(saved.get("owner").asString))
            capture("18-unlisted-permanent-public-link")
            ui.onNodeWithContentDescription("Back to your communities").performClick()
            overview().performScrollToNode(hasText("Create a Space") and hasClickAction())
            ui.onNode(hasText("Create a Space") and hasClickAction()).performClick()
            ui.onNodeWithText("Space name").performTextInput("Native Created Space")
            closeKeyboard()
            ui.onNode(hasText("Public") and hasClickAction()).performScrollTo().performClick()
            capture("19-real-editable-creation")
            ui.onNode(hasText("Create a Space") and hasClickAction() and hasAnyAncestor(isDialog())).assertIsEnabled().performClick()
            waitText("Native Created Space")
            waitUntil { ui.onNode(hasSetTextAction() and isEnabled()).isDisplayed() }
            ui.onNodeWithContentDescription("Switch Space or channel").performClick()
            drawerList().performScrollToNode(hasText("resources"))
            ui.onNodeWithText("resources").assertIsDisplayed()
            drawerList().performScrollToNode(hasText("reading room"))
            ui.onNodeWithText("reading room").assertIsDisplayed()
            capture("20-created-starters-drawer-b")
            device.pressBack()
            ui.onNodeWithContentDescription("Community information").performClick()
            ui.onNode(isToggleable() and hasAnyAncestor(isDialog())).performScrollTo().assertIsOn().performClick()
            waitUntil(20_000) { command("directory").getAsJsonArray("names").none { it.asString == "Native Created Space" } }
            capture("20a-owner-unlisted-community")
            ui.onNode(isToggleable() and hasAnyAncestor(isDialog())).assertIsOff().performClick()
            waitUntil(20_000) { command("directory").getAsJsonArray("names").any { it.asString == "Native Created Space" } }
            capture("20b-owner-listed-community")
            ui.onNode(hasText("Members") and hasClickAction()).performScrollTo().performClick()
            waitText("Owner")
            capture("21-created-owner-role")
            evidence.resolve("completed.json").writeText(JsonObject().apply {
                addProperty("pid", Process.myPid()); addProperty("owner", saved.get("owner").asString)
                addProperty("ordinaryBetaChanged", false); addProperty("productionNodeActivated", false)
            }.toString())
        } }
    }

    @Test fun compactLargeTypeKeepsRealCommunityAndCreationControlsReachable() {
        fun override(command: String) = device.executeShellCommand("wm $command").lineSequence()
            .firstOrNull { it.startsWith("Override ") }?.substringAfter(": ")?.trim()
        val size = override("size"); val density = override("density")
        val font = device.executeShellCommand("settings get system font_scale").trim()
        var primary: Throwable? = null
        try {
            device.executeShellCommand("wm size 720x1280")
            device.executeShellCommand("wm density 320")
            device.executeShellCommand("settings put system font_scale 2.0")
            launch().use { withFailureCapture({ capture("compact-failure") }) {
                switchModule("Red Square", "Spaces")
                assertEquals(2f, context.resources.configuration.fontScale, .01f)
                assertTrue(context.resources.configuration.screenWidthDp in 320..360)
                capture("22-compact-large-type-overview")
                openRow("Native Created Space")
                ui.onNodeWithContentDescription("Switch Space or channel").performClick()
                drawerList().performScrollToNode(hasText("resources"))
                ui.onNodeWithText("resources").assertIsDisplayed()
                capture("23-compact-large-type-drawer-b")
                device.pressBack()
                ui.onNodeWithContentDescription("Community information").performClick()
                ui.onNode(hasText("Members") and hasClickAction()).performScrollTo().performClick()
                waitText("Owner", 5_000)
                capture("24-compact-large-type-members")
                device.pressBack()
                ui.onNode(hasText("Close") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
                ui.onNodeWithContentDescription("Back to your communities").performClick()
                overview().performScrollToNode(hasText("Create a Space") and hasClickAction())
                ui.onNode(hasText("Create a Space") and hasClickAction()).performClick()
                ui.onNodeWithText("Space name").performScrollTo().assertIsDisplayed()
                capture("25-compact-large-type-creation")
                ui.onNode(hasText("Public") and hasClickAction()).performScrollTo().assertIsDisplayed()
                capture("26-compact-large-type-privacy")
                device.pressBack()
                evidence.resolve("compact-completed.json").writeText(JsonObject().apply {
                    addProperty("pid", Process.myPid()); addProperty("fontScale", context.resources.configuration.fontScale)
                    addProperty("screenWidthDp", context.resources.configuration.screenWidthDp)
                }.toString())
            } }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary,
                {
                    device.executeShellCommand(if (font == "null") "settings delete system font_scale" else "settings put system font_scale $font")
                    assertEquals("Original system font setting restored", font, device.executeShellCommand("settings get system font_scale").trim())
                },
                {
                    device.executeShellCommand("wm size ${size ?: "reset"}")
                    assertEquals("Original display size override restored", size, override("size"))
                },
                {
                    device.executeShellCommand("wm density ${density ?: "reset"}")
                    assertEquals("Original display density override restored", density, override("density"))
                })
        }
    }

    private fun drawerList() = ui.onNode(hasScrollToNodeAction() and hasAnyAncestor(
        SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.PaneTitle, "Space and channel navigation")))
    private fun overview() = ui.onNode(hasScrollToNodeAction() and SemanticsMatcher("on-screen overview") {
        it.boundsInRoot.left >= 0f && it.boundsInRoot.right > 0f && it.boundsInRoot.top >= 0f
    })
    private fun publishCheckpoint(phase: String, fixtures: JsonObject, pixel: Float): Nothing {
        val value = JsonObject().apply {
            addProperty("phase", phase); addProperty("pid", Process.myPid()); addProperty("pixel", pixel)
            addProperty("owner", AndroidWalletStore(context).loadExisting().publicKey)
            addProperty("backend", AndroidBackendStore(context).load().id); add("fixtures", fixtures)
        }
        evidence.resolve("checkpoint-$phase.json").writeText(value.toString())
        val temporary = File(context.cacheDir, "spaces-discovery-checkpoint.tmp")
        temporary.writeText(value.toString()); check(temporary.renameTo(checkpoint))
        while (true) SystemClock.sleep(250)
    }
    private fun readCheckpoint(phase: String): JsonObject = JsonParser.parseString(checkpoint.readText()).asJsonObject.also {
        assertEquals(phase, it.get("phase").asString); assertNotEquals(it.get("pid").asInt, Process.myPid())
        assertEquals(it.get("owner").asString, AndroidWalletStore(context).loadExisting().publicKey)
        assertEquals(it.get("backend").asString, AndroidBackendStore(context).load().id)
    }
    private fun members(fixtures: JsonObject, key: String): Set<String> = command("membership", JsonObject().apply {
        addProperty("space", fixtures.getAsJsonObject(key).get("space").asString)
    }).getAsJsonArray("members").map { it.asString }.toSet()
    private fun openLink(activity: ActivityScenario<MainActivity>, link: String) = activity.onActivity {
        it.startActivity(intent(link).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP))
    }
    private fun chooseChannel(name: String) {
        ui.onNodeWithContentDescription("Switch Space or channel").performClick()
        drawerList().performScrollToNode(hasText(name) and hasClickAction())
        ui.onNode(hasText(name) and hasClickAction()).performClick()
        waitUntil { ui.onNode(hasSetTextAction() and isEnabled()).isDisplayed() }
    }
    private fun history() = ui.onNode(hasScrollToNodeAction() and SemanticsMatcher("on-screen message history") {
        it.boundsInRoot.left >= 0f && it.boundsInRoot.top >= 0f && it.boundsInRoot.bottom > 0f
    })
    private fun openRow(name: String) {
        val row = hasContentDescription("Open $name") and hasClickAction()
        overview().performScrollToNode(row)
        waitUntil { ui.onNode(row and onScreen).isDisplayed() }
        ui.onNode(row and onScreen).performClick()
    }
    private fun assertPixel(text: String, expected: Float) = assertEquals("Actual native return retains the message pixel", expected,
        ui.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top, 1f)
    private fun switchModule(current: String, next: String) {
        ui.revealAppNavigation(); waitUntil { ui.onNodeWithContentDescription("Switch app: $current").isDisplayed() }
        ui.onNodeWithContentDescription("Switch app: $current").performClick()
        ui.onNode(hasText(next) and hasClickAction() and hasAnyAncestor(isPopup() or isDialog())).performScrollTo().performClick()
        waitUntil { ui.onNodeWithContentDescription("Switch app: $next").isDisplayed() }
    }
    private fun closeKeyboard() { ui.waitForIdle(); androidx.test.espresso.Espresso.closeSoftKeyboard() }
    private val onScreen get() = SemanticsMatcher("on-screen actual route") {
        it.boundsInRoot.left >= 0f && it.boundsInRoot.right > 0f && it.boundsInRoot.top >= 0f && it.boundsInRoot.bottom > 0f
    }
    private fun waitText(text: String, timeoutMillis: Long = 15_000) = waitUntil(timeoutMillis) { ui.onNode(hasText(text) and onScreen).isDisplayed() }
    private fun waitUntil(timeoutMillis: Long = 15_000, condition: () -> Boolean) = ui.waitUntil(timeoutMillis, condition)
    private fun capture(name: String) = captureNativeScreen(File(evidence, "$name.png"))
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
