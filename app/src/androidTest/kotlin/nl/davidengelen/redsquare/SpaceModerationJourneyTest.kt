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
        launch(fixture).use {
            withFailureCapture({ capture("reporter-failure") }) {
                waitText("Moderation proof message", 25_000)
                val actions = ui.onNodeWithContentDescription("Message actions")
                ui.waitUntil(10_000) { actions.isDisplayed() }
                actions.performClick()
                click("Report message")
                val field = ui.onNode(hasSetTextAction() and isEnabled())
                ui.waitUntil(12_000) { field.isDisplayed() }
                field.performClick().performTextInput("Promotion repeated in a discussion")
                capture("report-composer")
                UiDevice.getInstance(instrumentation).pressBack()
                click("Send report")
                waitText("Report sent", 15_000)
                capture("report-confirmation")
                val independent = request("moderation-report-status")
                assertEquals(fixture.get("message").asString, independent.get("message").asString)
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
        launch(fixture).use {
            withFailureCapture({ capture("administrator-failure") }) {
                waitText("Moderation proof message", 25_000)
                openInformation()
                click("Reports and admin log")
                waitText("Promotion repeated in a discussion", 15_000)
                capture("report-review")
                click("View admin log")
                val promoted = ui.onNodeWithText("Made a member an administrator")
                ui.waitUntil(15_000) { runCatching { promoted.performScrollTo().isDisplayed() }.getOrDefault(false) }
                capture("admin-log")
                UiDevice.getInstance(instrumentation).pressBack()
                waitText("Promotion repeated in a discussion", 15_000)
                UiDevice.getInstance(instrumentation).pressBack()
                click("Members")
                click("Manage member")
                click("Scope: Whole Space")
                click("General")
                click("Choose duration")
                click("5 minutes")
                click("Pause writing")
                val scoped = ui.onNode(hasText("General · paused until", substring = true))
                ui.waitUntil(15_000) { runCatching { scoped.performScrollTo().isDisplayed() }.getOrDefault(false) }
                capture("channel-restriction")
                val first = request("moderation-restriction-status")
                assertFalse(first.get("generalWritable").asBoolean)
                assertTrue(first.get("separateWritable").asBoolean)
                assertTrue(first.get("generalReadable").asBoolean)
                click("Restore writing in General")
                ui.waitUntil(15_000) { !scoped.isDisplayed() }
                click("Scope: General")
                click("Whole Space")
                click("Pause writing")
                val whole = ui.onNode(hasText("Whole Space · paused until", substring = true))
                ui.waitUntil(15_000) { runCatching { whole.performScrollTo().isDisplayed() }.getOrDefault(false) }
                capture("space-restriction")
                val second = request("moderation-restriction-status")
                assertFalse(second.get("generalWritable").asBoolean)
                assertFalse(second.get("separateWritable").asBoolean)
                assertTrue(second.get("generalReadable").asBoolean)
                assertTrue(second.get("actionsRecorded").asBoolean)
                assertEquals(prior.get("report").asString, request("moderation-report-status").get("id").asString)
                File(evidence, "administrator-result.json").writeText(JsonObject().apply {
                    addProperty("pid", Process.myPid())
                    addProperty("correctReport", prior.get("report").asString)
                    addProperty("channelOnly", true)
                    addProperty("spaceWide", true)
                    addProperty("independentReading", true)
                    addProperty("staffAudit", true)
                }.toString())
            }
        }
    }

    private fun openInformation() {
        val button = ui.onNodeWithContentDescription("Community information")
        ui.waitUntil(12_000) { button.isDisplayed() }
        button.performClick()
    }

    private fun waitText(text: String, millis: Long) = ui.waitUntil(millis) { ui.onNodeWithText(text).isDisplayed() }

    private fun click(text: String) {
        val target = ui.onNode(hasText(text) and hasClickAction() and isEnabled())
        ui.waitUntil(12_000) { target.isDisplayed() || runCatching { target.performScrollTo().isDisplayed() }.getOrDefault(false) }
        target.performClick()
    }

    private fun launch(fixture: JsonObject): ActivityScenario<MainActivity> {
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
        assertEquals(fixture.get("nativeWallet").asString, wallet.publicKey)
        val wallets = AndroidWalletStore(context)
        val current = wallets.loadOrCreate()
        if (current.publicKey != wallet.publicKey) wallets.replace(current, wallet)
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
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
