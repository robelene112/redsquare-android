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
        .putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/"))

    @Test fun createInviteJoinReadAndCheckpointAnUncertainOriginal() {
        check(!checkpoint.exists())
        val wallet = AndroidWalletStore(context).loadOrCreate()
        val backend = AndroidBackendStore(context).load()
        val originalWallet = wallet.exportJson()
        launch().use { activity ->
            openSpaces("Red Square")
            ui.waitUntil(15_000) { ui.onNode(hasText("Create a community") and hasClickAction() and isEnabled()).isDisplayed() }
            capture("01-communities")
            ui.onNode(hasText("Create a community") and hasClickAction()).performClick()
            ui.onNode(hasSetTextAction()).performTextInput(community)
            ui.onNode(hasText("Create a community") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Message general").isDisplayed() && ui.onNode(hasSetTextAction() and isEnabled()).isDisplayed() }
            ui.onNodeWithText("Message general").performTextInput("Hello from the native Android client")
            ui.waitUntil(10_000) { ui.onNode(hasContentDescription("Send message") and isEnabled()).isDisplayed() }
            ui.onNodeWithContentDescription("Send message").performClick()
            closeKeyboard(activity)
            ui.waitUntil(15_000) { ui.onNodeWithText("Hello from the native Android client").isDisplayed() }
            ui.onNodeWithContentDescription("Switch Space or channel").performClick()
            capture("02-drawer-b")
            ui.onNodeWithText("Invite people").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Copy invitation").isDisplayed() }
            ui.onNodeWithText("Copy invitation").performClick()
            ui.onNodeWithText("Invitation copied").assertIsDisplayed()
            var link = ""
            activity.onActivity {
                link = (it.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text.toString()
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
            command("populate", JsonObject().apply { addProperty("space", joined.get("space").asString) })
            ui.waitUntil(15_000) { ui.onNodeWithText("Fixture line 19").isDisplayed() }
            ui.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Fixture line 5"))
            val anchor = ui.onNodeWithText("Fixture line 5").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
            capture("04a-scrolled-before-module-switch")
            switchModule("Spaces", "Red Square")
            openSpaces("Red Square")
            ui.waitUntil(10_000) { ui.onNodeWithText("Fixture line 5").isDisplayed() }
            assertEquals("Module return keeps the actual reading pixel", anchor,
                ui.onNodeWithText("Fixture line 5").fetchSemanticsNode().boundsInRoot.top, 1f)
            capture("04b-scrolled-after-module-switch")
            // A second, independently created community exercises the native
            // preview and explicit join path rather than only owner admission.
            val invitation = command("invitation").get("link").asString
            UiDevice.getInstance(instrumentation).pressBack()
            ui.waitUntil(5_000) { ui.onNode(hasText("Join with an invitation") and hasClickAction()).isDisplayed() }
            ui.onNode(hasText("Join with an invitation") and hasClickAction()).performClick()
            ui.onNode(hasSetTextAction()).performTextInput(invitation)
            ui.onNode(hasText("View invitation") and hasClickAction()).performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("You are invited to Builders on another device").isDisplayed() }
            capture("05-preview-without-joining")
            ui.onNode(hasText("Join community") and hasClickAction()).performClick()
            ui.waitUntil(20_000) { ui.onNodeWithText("Reply from independent member").isDisplayed() &&
                ui.onNode(hasSetTextAction() and isEnabled()).isDisplayed() }
            capture("06-joined-independent-community")
            ui.onNodeWithContentDescription("Switch Space or channel").performClick()
            ui.onNodeWithContentDescription("Open $community").performClick()
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
                val originals = command("status").getAsJsonArray("originals").map { it.asJsonObject }
                acceptedOriginal = originals.lastOrNull { it.get("kind").asString == "content" }?.get("id")?.asString
                acceptedOriginal != null && ui.onNodeWithText("Waiting for confirmation").isDisplayed()
            }
            ui.onNode(hasSetTextAction()).performTextReplacement(draft)
            closeKeyboard(activity)
            switchModule("Spaces", "Red Square")
            openSpaces("Red Square")
            ui.waitUntil(10_000) { ui.onNode(hasSetTextAction() and hasText(draft)).isDisplayed() }
            capture("08-uncertain-original-and-new-draft")
            assertEquals(originalWallet, AndroidWalletStore(context).loadExisting().exportJson())
            assertEquals(backend, AndroidBackendStore(context).load())
            checkpoint.writeText(JsonObject().apply {
                addProperty("phase", "ready-for-process-kill"); addProperty("pid", Process.myPid())
                addProperty("owner", wallet.publicKey); addProperty("backend", backend.id)
                addProperty("original", acceptedOriginal); addProperty("community", community)
            }.toString())
            // The host, outside this PID, observes the durable checkpoint and
            // force-stops this actual process. No Activity.recreate substitute.
            while (true) SystemClock.sleep(250)
        }
    }

    @Test fun coldReturnReconcilesTheSameOriginalAndKeepsNewWriting() {
        val saved = JsonParser.parseString(checkpoint.readText()).asJsonObject
        assertEquals("ready-for-process-kill", saved.get("phase").asString)
        assertNotEquals(saved.get("pid").asInt, Process.myPid())
        assertEquals(saved.get("owner").asString, AndroidWalletStore(context).loadExisting().publicKey)
        assertEquals(saved.get("backend").asString, AndroidBackendStore(context).load().id)
        command("release")
        launch().use {
            openSpaces("Red Square")
            ui.waitUntil(15_000) { ui.onNodeWithContentDescription("Open $community").isDisplayed() }
            ui.onNodeWithContentDescription("Open $community").performClick()
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
        }
    }

    private fun openSpaces(current: String) = switchModule(current, "Spaces")
    private fun switchModule(current: String, next: String) {
        ui.waitUntil(15_000) { ui.onNodeWithContentDescription("Switch app: $current").isDisplayed() }
        ui.onNodeWithContentDescription("Switch app: $current").performClick()
        ui.onNode(hasText(next) and hasClickAction() and hasAnyAncestor(isPopup() or isDialog())).performScrollTo().performClick()
        ui.waitUntil(10_000) { ui.onNodeWithContentDescription("Switch app: $next").isDisplayed() }
    }
    private fun closeKeyboard(activity: ActivityScenario<MainActivity>) {
        fun visible(): Boolean {
            var shown = false
            activity.onActivity { shown = androidx.core.view.ViewCompat.getRootWindowInsets(it.window.decorView)
                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true }
            return shown
        }
        if (visible()) {
            UiDevice.getInstance(instrumentation).pressBack()
            ui.waitUntil(5_000) { !visible() }
        }
    }
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
