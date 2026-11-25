package nl.davidengelen.redsquare

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
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
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class SpaceModerationJourneyTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext.also {
        check(it.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in setOf("ranchu", "goldfish"))
    }
    private val evidence get() = File(context.cacheDir, "spaces-moderation-evidence").apply { mkdirs() }

    @Test fun memberReportsChosenMessagePrivately() {
        val fixture = request("moderation-config")
        launch(fixture, seedIdentity = true).use {
            withFailureCapture({ capture("reporter-failure") }) {
                val message = hasText("Moderation proof message")
                val row = hasTestTag("spaces-message") and hasAnyDescendant(message)
                val actions = ui.onNode(hasText("Message actions") and hasClickAction() and hasAnyAncestor(row))
                ui.waitUntil(25_000) { ui.onNode(message).isDisplayed() }
                if (!actions.isDisplayed()) {
                    val conversation = ui.onNode(hasScrollToIndexAction() and hasAnyDescendant(message))
                    conversation.performTouchInput { swipeDown(durationMillis = 350) }
                }
                ui.waitUntil(12_000) { actions.isDisplayed() }
                actions.performClick()
                click("Report message")
                val field = ui.onNode(hasSetTextAction() and hasText("Reason") and isEnabled())
                ui.waitUntil(12_000) { field.isDisplayed() || runCatching { field.performScrollTo().isDisplayed() }.getOrDefault(false) }
                val reason = "Promotion repeated in a discussion"
                field.performTouchInput { click() }
                field.performTextReplacement(reason)
                field.assertTextContains(reason)
                ui.onNode(hasText("Send report") and hasClickAction()).assertIsEnabled()
                capture("report-composer")
                UiDevice.getInstance(instrumentation).pressBack()
                click("Send report")
                waitText("Report sent", 15_000)
                capture("report-confirmation")
                val independent = request("moderation-report-status")
                assertEquals(fixture.get("message").asString, independent.get("message_id").asString)
                assertEquals(fixture.get("nativeWallet").asString, independent.get("reporter").asString)
                assertEquals("Promotion repeated in a discussion", independent.get("reason").asString)
                assertTrue(independent.get("reporterDeniedStaffRead").asBoolean)
                click("Close")
                ui.onNodeWithText("Promotion repeated in a discussion").assertDoesNotExist()
                File(evidence, "reporter-result.json").writeText(JsonObject().apply {
                    addProperty("pid", Process.myPid())
                    addProperty("report", independent.get("id").asString)
                }.toString())
            }
        }
    }

    @Test fun promotedAdministratorReviewsReportAndChangesWritingScope() {
        val fixture = request("moderation-config")
        val prior = JsonParser.parseString(File(evidence, "reporter-result.json").readText()).asJsonObject
        assertNotEquals(prior.get("pid").asInt, Process.myPid())
        launch(fixture, seedIdentity = false).use {
            withFailureCapture({ capture("administrator-failure") }) {
                waitText("Moderation proof message", 25_000)
                click("Community information")
                click("Reports and admin log")
                waitText("Promotion repeated in a discussion", 15_000)
                click("Review report")
                waitText("Moderation proof message", 15_000)
                capture("report-review")
                click("Close")
                click("View admin log")
                val promoted = ui.onNodeWithText("Made a member an administrator")
                ui.waitUntil(15_000) { promoted.isDisplayed() || runCatching { promoted.performScrollTo().isDisplayed() }.getOrDefault(false) }
                capture("admin-log")
                UiDevice.getInstance(instrumentation).pressBack()
                waitText("Promotion repeated in a discussion", 15_000)
                UiDevice.getInstance(instrumentation).pressBack()
                click("Members")
                val manage = ui.onNode(hasText("Manage member") and hasClickAction() and isEnabled())
                val memberList = ui.onNodeWithTag("spaces-member-list")
                ui.waitUntil(12_000) { memberList.isDisplayed() }
                repeat(2) {
                    if (!manage.isDisplayed()) memberList.performTouchInput { swipeUp(durationMillis = 350) }
                }
                click("Manage member")
                click("Scope: Whole Space")
                click("General", popup = true)
                click("Choose duration")
                click("5 minutes", popup = true)
                click("Pause writing")
                val scoped = ui.onNode(hasText("General · paused until", substring = true))
                ui.waitUntil(15_000) { scoped.isDisplayed() || runCatching { scoped.performScrollTo().isDisplayed() }.getOrDefault(false) }
                capture("channel-restriction")
                val first = request("moderation-restriction-status")
                assertFalse(first.get("generalWritable").asBoolean)
                assertTrue(first.get("separateWritable").asBoolean)
                assertTrue(first.get("generalReadable").asBoolean)
                click("Restore writing in General")
                ui.waitUntil(15_000) { !scoped.isDisplayed() }
                click("Scope: General")
                click("Whole Space", popup = true)
                click("Pause writing")
                val whole = ui.onNode(hasText("Whole Space · paused until", substring = true))
                ui.waitUntil(15_000) { whole.isDisplayed() || runCatching { whole.performScrollTo().isDisplayed() }.getOrDefault(false) }
                capture("space-restriction")
                val second = request("moderation-restriction-status")
                assertFalse(second.get("generalWritable").asBoolean)
                assertFalse(second.get("separateWritable").asBoolean)
                assertTrue(second.get("generalReadable").asBoolean)
                assertTrue(second.get("actionsRecorded").asBoolean)
                assertEquals(prior.get("report").asString, request("moderation-report-status").get("id").asString)
                UiDevice.getInstance(instrumentation).pressBack()
                val manageDialog = hasText("Manage member") and hasAnyAncestor(isDialog()) and !hasClickAction()
                ui.waitUntil(12_000) { ui.onAllNodes(manageDialog).fetchSemanticsNodes().isEmpty() }
                click("Back to your communities")
                click("Reports and admin log")
                click("Review report")
                waitText("Moderation proof message", 15_000)
                assertTrue(request("moderation-demote-reporter").get("reporterDeniedStaffRead").asBoolean)
                waitText("Only the owner and administrators can see reports and the admin log.", 20_000)
                ui.onNodeWithText("Promotion repeated in a discussion").assertDoesNotExist()
                ui.onNodeWithText("Moderation proof message").assertDoesNotExist()
                capture("report-access-revoked")
                File(evidence, "administrator-result.json").writeText(JsonObject().apply {
                    addProperty("pid", Process.myPid())
                    addProperty("correctReport", prior.get("report").asString)
                    addProperty("channelOnly", true)
                    addProperty("spaceWide", true)
                    addProperty("independentReading", true)
                    addProperty("staffAudit", true)
                    addProperty("staffRevocationHidReport", true)
                }.toString())
            }
        }
    }

    private fun waitText(text: String, millis: Long) = ui.waitUntil(millis) { ui.onNodeWithText(text).isDisplayed() }

    private fun click(text: String, popup: Boolean = false) {
        val enabledAction = hasText(text) and hasClickAction() and isEnabled()
        val target = ui.onNode(if (popup) enabledAction and hasAnyAncestor(isPopup()) else enabledAction)
        ui.waitUntil(12_000) { target.isDisplayed() || runCatching { target.performScrollTo().isDisplayed() }.getOrDefault(false) }
        target.performClick()
    }

    private fun launch(fixture: JsonObject, seedIdentity: Boolean): ActivityScenario<MainActivity> {
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
        assertEquals(fixture.get("nativeWallet").asString, wallet.publicKey)
        val wallets = AndroidWalletStore(context)
        val backends = AndroidBackendStore(context)
        if (seedIdentity) {
            val current = wallets.loadOrCreate()
            if (current.publicKey != wallet.publicKey) wallets.replace(current, wallet)
            backends.save(SaitoBackend.OWNER)
        } else {
            assertEquals(wallet.publicKey, wallets.loadExisting().publicKey)
            assertEquals(SaitoBackend.OWNER, backends.load())
        }
        val peer = argument("spacesPeerPort")
        return ActivityScenario.launch(Intent(Intent.ACTION_VIEW, Uri.parse(fixture.get("link").asString), context, MainActivity::class.java)
            .putExtra("feedPeerUrl", "ws://127.0.0.1:$peer/wsopen")
            .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:$peer/wsopen")
            .putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/"))
    }

    private fun request(action: String): JsonObject {
        val connection = URL("http://127.0.0.1:${argument("spacesControlPort")}/$action").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 5000
            connection.readTimeout = 8000
            connection.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
            check(connection.responseCode == 200) { "Owned fixture $action returned HTTP ${connection.responseCode}" }
            return connection.inputStream.use { JsonParser.parseString(it.bufferedReader().readText()).asJsonObject }
        } finally { connection.disconnect() }
    }

    private fun argument(name: String) = requireNotNull(InstrumentationRegistry.getArguments().getString(name)).toInt().also { require(it in 1024..65535) }
    private fun capture(name: String) = captureNativeScreen(File(evidence, "$name.png"))
}
