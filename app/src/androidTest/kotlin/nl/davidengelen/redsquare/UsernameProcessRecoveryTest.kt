package nl.davidengelen.redsquare

import android.content.Intent
import android.os.Process
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Base64

/** Host-separated processes; public checkpoints never contain wallet signing material. */
class UsernameProcessRecoveryTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val checkpoint get() = File(context.cacheDir, "username-process-checkpoint.json")
    private val pending = "Registration requested. Confirmation is still pending."

    private fun guard() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
    }

    @Test fun terminateAfterPersistingAnUnconfirmedNativeRegistration() {
        guard()
        // Only preparation resets isolated emulator Registry fixtures. Recovery never resets identity/work.
        context.noBackupFilesDir.listFiles()?.filter {
            it.name.startsWith("namerequest.") || it.name.startsWith("namemap.")
        }?.forEach { check(it.delete()) }
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        val author = AndroidWalletStore(context).loadExisting().publicKey
        val peer = registryPeer()
        launch(peer) {
            openUsername()
            waitForEnabled("Check name")
            ui.onNode(hasSetTextAction()).performTextReplacement("NativeCase7")
            ui.onNodeWithText("Check name").performScrollTo().performClick()
            waitFor("This username is currently available. Checking does not reserve it.")
            assertTrue(peer.transactions.isEmpty())
            ui.onNodeWithText("Register name").performScrollTo().performClick()
            waitForEnabled("Retry registration")
            ui.onNodeWithText(pending).performScrollTo().assertIsDisplayed()
            assertEquals(1, peer.transactions.size)
            val frame = peer.transactions.single()
            val request = postingTransaction(frame)
            val signed = SaitoEnvelope.decode(request.envelope())
            assertEquals(author, signed.author)
            assertEquals(listOf(SaitoRegistry.REGISTRAR, author), signed.recipients)
            assertEquals("Registry", signed.body.get("module").asString)
            assertEquals("register", signed.body.get("request").asString)
            assertEquals("NativeCase7@saito", signed.body.get("identifier").asString)
            assertTrue(peer.clientKeys.all { it == author }); peer.assertHealthy()
            capture("pending-before-death.png", pending)
            val row = JsonObject().apply {
                addProperty("phase", "pending"); addProperty("native", true)
                addProperty("author", author); addProperty("backend", AndroidBackendStore(context).load().id)
                addProperty("pid", Process.myPid()); addProperty("sig", request.id)
                addProperty("frame", Base64.getEncoder().encodeToString(frame))
                addProperty("envelope", request.envelope())
            }
            checkpoint.outputStream().use { output ->
                output.write(row.toString().toByteArray()); output.fd.sync()
            }
            Process.killProcess(Process.myPid())
            error("Expected actual process termination")
        }
    }

    @Test fun coldRegistrationReconcilesReadOnlyBeforeAnExactExplicitRetry() {
        guard()
        val row = JsonParser.parseString(checkpoint.readText()).asJsonObject
        assertEquals("pending", row.get("phase").asString)
        val author = row.get("author").asString
        assertEquals(author, AndroidWalletStore(context).loadExisting().publicKey)
        assertEquals("owner", row.get("backend").asString)
        assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
        assertNotEquals(row.get("pid").asInt, Process.myPid())
        val original = Base64.getDecoder().decode(row.get("frame").asString)
        val originalRequest = postingTransaction(original)
        assertEquals(row.get("sig").asString, originalRequest.id)
        assertEquals(row.get("envelope").asString, originalRequest.envelope())
        val peer = registryPeer()
        launch(peer) {
            openUsername()
            waitForEnabled("Retry registration")
            ui.onNodeWithText(pending).performScrollTo().assertIsDisplayed()
            ui.onNode(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("NativeCase7")))
                .assertIsNotEnabled()
            assertTrue("Cold reopening must perform an actual read-only Registry namecheck", peer.registryQueries.any {
                it.get("request").asString == "registry namecheck" && it.get("identifier").asString == "NativeCase7@saito"
            })
            assertTrue("Recovery must not automatically publish", peer.transactions.isEmpty())
            capture("pending-cold-recovery.png", pending)
            ui.onNodeWithText("Retry registration").performScrollTo().performClick()
            waitForEnabled("Retry registration")
            ui.onNodeWithText(pending).performScrollTo().assertIsDisplayed()
            assertEquals(1, peer.transactions.size)
            assertArrayEquals(original, peer.transactions.single())
            assertEquals(author, AndroidWalletStore(context).loadExisting().publicKey)
            assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
            assertTrue(peer.clientKeys.all { it == author }); peer.assertHealthy()
            row.addProperty("phase", "recovered"); row.addProperty("recoveredPid", Process.myPid())
            checkpoint.outputStream().use { output -> output.write(row.toString().toByteArray()); output.fd.sync() }
        }
    }

    private fun registryPeer() = ControlledFeedPeer("[]", registryResponse = response@ { criteria ->
        if (criteria.get("request").asString == "registry query") return@response "{}"
        assertEquals("registry namecheck", criteria.get("request").asString)
        assertEquals("NativeCase7@saito", criteria.get("identifier").asString)
        "[]"
    })

    private fun launch(peer: ControlledFeedPeer, block: () -> Unit) {
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                withFailureCapture({ capture("username-process-failure.png") }, block)
            }
        }
    }

    private fun openUsername() {
        waitForEnabled("New post")
        if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
        ui.onNodeWithText("Settings").performClick()
        ui.onNodeWithText("Privacy and safety").performScrollTo().performClick()
        ui.onNodeWithText("Saito username").performClick()
        waitForEnabled("Back")
    }

    private fun waitFor(text: String) = ui.waitUntil(20_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun waitForEnabled(text: String) = ui.waitUntil(20_000) {
        ui.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
    }
    private fun capture(name: String, expected: String? = null) =
        captureNativeScreen(File(context.cacheDir, "username-process-evidence/$name"), expected)
}
