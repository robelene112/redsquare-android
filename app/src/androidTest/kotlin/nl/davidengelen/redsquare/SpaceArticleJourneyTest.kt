package nl.davidengelen.redsquare

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.AtomicFile
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A real Activity and Android keyboard publishing into the isolated signed
 * service. The host driver owns the force-stop between the two methods. */
class SpaceArticleJourneyTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext.also {
        check(it.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
    }
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val evidence get() = File(context.cacheDir, "spaces-articles-evidence").apply { mkdirs() }
    private val checkpoint get() = File(context.cacheDir, "spaces-articles-checkpoint.json")
    private val agreement get() = File(context.noBackupFilesDir, "publishing-policy-fixture.json")
    private val title = "Notes from the writing circle"
    private val subtitle = "An edition for our members"
    private val sequel = "A second draft waiting after restart"
    private val otherDraft = "Different writing in the other room"
    private val visualLine = "I wrote this first in the visual editor."
    private val body = "$visualLine\n\n## From Markdown\n\n" +
        (1..34).joinToString("\n\n") { "Paragraph $it records a separate detail for the reading group." }

    @Test fun reviewAndPublishArticleWithRealIme() {
        val wallet = AndroidWalletStore(context).loadOrCreate()
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        check(!agreement.exists())
        agreement.writeText("""{"revision":"spaces-article-fixture","ready":true,"enforce":false,"terms":"Isolated fixture terms","community":"Isolated fixture rules"}""")
        val fixture = request("article-invitation", JsonObject().apply { addProperty("recipient", wallet.publicKey) })
        launch(fixture.get("link").asString).use { activity ->
            withFailureCapture({ capture("articles-first-failure") }) {
                clickWhenVisible("Join community")
                clickWhenVisible("Write article", timeout = 40_000)
                val titleField = editField("Title")
                ui.waitUntil(10_000) { titleField.isDisplayed() }
                titleField.performClick()
                titleField.performTextInput(title)
                ui.waitUntil(8_000) { imeVisible(activity) }
                editField("Subtitle").performTextInput(subtitle)
                closeKeyboard(activity)
                val visual = ui.onNode(hasContentDescription("Article body") and hasSetTextAction(), useUnmergedTree = true)
                visual.performScrollTo().performTextInput(visualLine)
                closeKeyboard(activity)
                button("Markdown").performScrollTo().performClick()
                val source = editField("Markdown source")
                ui.waitUntil(8_000) { source.isDisplayed() }
                assertTrue(source.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText]
                    .text.contains(visualLine))
                source.performTextReplacement(body)
                closeKeyboard(activity)
                switchChannel("Other reading room")
                clickWhenVisible("Write article", timeout = 25_000)
                ui.waitUntil(8_000) { editField("Title").isDisplayed() }
                editField("Title").performTextInput(otherDraft)
                closeKeyboard(activity)
                switchChannel("Reading room")
                clickWhenVisible("Write article")
                ui.waitUntil(10_000) { editField("Title").isDisplayed() }
                editField("Title").assertEditableTextEquals(title)
                editField("Markdown source").assertEditableTextEquals(body)
                button("Review article").performScrollTo().performClick()
                val publish = button("Publish in this channel")
                ui.waitUntil(8_000) { publish.isDisplayed() }
                ui.onNodeWithText("Only members with access to this channel can read this article.").assertIsDisplayed()
                capture("articles-private-review", title)
                request("hold")
                publish.performTouchInput { click() }
                var proof = JsonObject()
                val expected = expected(fixture, wallet.publicKey)
                ui.waitUntil(20_000) {
                    proof = request("article-proof", expected)
                    proof.get("receivedCount").asInt == 1
                }
                assertTrue(proof.get("exactOriginal").asBoolean)
                assertEquals(proof.get("originalSubmissions").asInt, proof.get("encryptedOriginals").asInt)
                ui.waitUntil(12_000) { ui.onNodeWithText("Waiting for confirmation.").isDisplayed() }
                capture("articles-waiting", title)
                articleBack()
                clickWhenVisible("Write article")
                ui.waitUntil(8_000) { editField("Title").isDisplayed() }
                editField("Title").performTextInput(sequel)
                closeKeyboard(activity)
                articleBack()
                clickWhenVisible("Write article")
                ui.waitUntil(10_000) { ui.onNode(hasText(sequel) and hasSetTextAction()).isDisplayed() }
                val saved = JsonObject().apply {
                    addProperty("phase", "ready-for-process-kill")
                    addProperty("pid", Process.myPid())
                    addProperty("author", wallet.publicKey)
                    addProperty("space", fixture.get("space").asString)
                    addProperty("channel", fixture.get("channel").asString)
                    addProperty("original", proof.get("original").asString)
                    addProperty("link", proof.get("link").asString)
                }
                val file = AtomicFile(checkpoint)
                val output = file.startWrite()
                try { output.write(saved.toString().encodeToByteArray()); file.finishWrite(output) }
                catch (failure: Exception) { file.failWrite(output); throw failure }
                while (true) SystemClock.sleep(200)
            }
        }
    }

    @Test fun recoverIndependentArticleAndNextDraft() {
        val saved = JsonParser.parseString(checkpoint.readText()).asJsonObject
        assertNotEquals(saved.get("pid").asInt, Process.myPid())
        assertEquals(saved.get("author").asString, AndroidWalletStore(context).loadExisting().publicKey)
        request("release")
        launch(saved.get("link").asString).use {
            withFailureCapture({ capture("articles-recovery-failure") }) {
                ui.waitUntil(30_000) { ui.onNodeWithText(title).isDisplayed() }
                ui.onNodeWithText(subtitle).assertIsDisplayed()
                val pending = expected(saved, saved.get("author").asString)
                var proof = JsonObject()
                ui.waitUntil(25_000) {
                    proof = request("article-proof", pending)
                    proof.get("originalSubmissions").asInt >= 2
                }
                assertEquals(saved.get("original").asString, proof.get("original").asString)
                assertEquals(1, proof.get("receivedCount").asInt)
                assertTrue(proof.get("exactOriginal").asBoolean)
                assertEquals(proof.get("originalSubmissions").asInt, proof.get("encryptedOriginals").asInt)
                capture("articles-recovered-exact-reader", title)
                articleBack()
                clickWhenVisible("Write article")
                ui.waitUntil(10_000) { ui.onNode(hasText(sequel) and hasSetTextAction()).isDisplayed() }
                capture("articles-recovered-draft", sequel)
                switchChannel("Other reading room")
                clickWhenVisible("Write article")
                ui.waitUntil(10_000) { ui.onNode(hasText(otherDraft) and hasSetTextAction()).isDisplayed() }
                capture("articles-recovered-other-draft", otherDraft)
                request("article-hold-read")
                val follow = Intent(Intent.ACTION_VIEW, Uri.parse(saved.get("link").asString))
                    .setPackage(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                assertEquals(MainActivity::class.java.name, follow.resolveActivity(context.packageManager)?.className)
                context.startActivity(follow)
                ui.waitUntil(8_000) { request("article-held-read").get("held").asBoolean }
                assertTrue(UiDevice.getInstance(instrumentation).pressBack())
                ui.waitUntil(5_000) { !ui.onNodeWithText("Open Space article").isDisplayed() }
                switchChannel("Reading room")
                assertTrue(request("article-release-read").get("released").asBoolean)
                clickWhenVisible("Write article")
                ui.waitUntil(10_000) { ui.onNode(hasText(sequel) and hasSetTextAction()).isDisplayed() }
                ui.onNodeWithText(title).assertDoesNotExist()
                proof = request("article-proof", pending)
                assertEquals(saved.get("original").asString, proof.get("original").asString)
                assertEquals(1, proof.get("receivedCount").asInt)
                assertTrue(proof.get("exactOriginal").asBoolean)
                proof.addProperty("newPid", Process.myPid())
                proof.addProperty("newDraftRestored", true)
                proof.addProperty("exactDeepLinkRendered", true)
                proof.addProperty("channelDraftsStayedSeparate", true)
                proof.addProperty("lateLinkStayedInOriginalScope", true)
                File(evidence, "articles-result.json").writeText(proof.toString())
            }
        }
    }

    private fun expected(scope: JsonObject, author: String) = JsonObject().apply {
        addProperty("space", scope.get("space").asString); addProperty("channel", scope.get("channel").asString)
        addProperty("author", author); addProperty("title", title); addProperty("subtitle", subtitle); addProperty("markdown", body)
    }

    private fun launch(link: String): ActivityScenario<MainActivity> {
        val peer = port("spacesPeerPort")
        return ActivityScenario.launch(Intent(Intent.ACTION_VIEW, Uri.parse(link), context, MainActivity::class.java)
            .putExtra("feedPeerUrl", "ws://127.0.0.1:$peer/wsopen")
            .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:$peer/wsopen")
            .putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/"))
    }

    private fun port(name: String) = requireNotNull(arguments.getString(name)).toInt().also { check(it in 1024..65535) }
    private fun button(text: String) = ui.onNode(hasText(text) and hasClickAction())
    private fun editField(text: String) = ui.onNode(hasText(text) and hasSetTextAction())
    private fun clickWhenVisible(text: String, timeout: Long = 12_000) {
        val button = ui.onNode(hasText(text) and hasClickAction() and isEnabled())
        ui.waitUntil(timeout) { button.isDisplayed() }
        button.performClick()
    }
    private fun articleBack() {
        val back = ui.onNode(hasContentDescription(context.getString(R.string.browse_back)) and hasClickAction())
        back.assertIsDisplayed().performClick()
    }
    private fun switchChannel(name: String) {
        val switch = ui.onNodeWithContentDescription("Switch Space or channel")
        ui.waitUntil(8_000) { switch.isDisplayed() }
        switch.performTouchInput { click() }
        val target = button(name)
        ui.waitUntil(10_000) { target.isDisplayed() }
        target.performClick()
    }
    private fun imeVisible(activity: ActivityScenario<MainActivity>): Boolean {
        var visible = false
        activity.onActivity { visible = ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
        return visible
    }
    private fun closeKeyboard(activity: ActivityScenario<MainActivity>) {
        ui.waitForIdle()
        Espresso.closeSoftKeyboard()
        ui.waitUntil(5_000) { !imeVisible(activity) }
    }
    private fun capture(name: String, expected: String? = null) = captureNativeScreen(File(evidence, "$name.png"), expected)

    private fun request(path: String, body: JsonObject = JsonObject()): JsonObject {
        val connection = URL("http://127.0.0.1:${port("spacesControlPort")}/$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"; connection.doOutput = true
            connection.connectTimeout = 5_000; connection.readTimeout = 5_000
            connection.outputStream.use { it.write(body.toString().encodeToByteArray()) }
            assertEquals(200, connection.responseCode)
            return connection.inputStream.bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        } finally { connection.disconnect() }
    }
}
