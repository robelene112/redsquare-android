package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoRegistry
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class UsernamePreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Before fun prepare() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        context.noBackupFilesDir.listFiles()?.filter {
            it.name.startsWith("namerequest.") || it.name.startsWith("namemap.")
        }?.forEach { check(it.delete()) { "Could not reset emulator username fixtures" } }
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
    }

    @Test fun anUnavailableUsernameCheckDoesNotBlockPostingOrChangeTheWallet() {
        val peer = ControlledFeedPeer("[]", registryResponse = { criteria ->
            assertEquals("registry namecheck", criteria.get("request").asString)
            assertEquals("NativeCase7@saito", criteria.get("identifier").asString)
            "{\"err\":\"no response\"}"
        })
        peer.start().use { server ->
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
            ActivityScenario.launch<MainActivity>(intent).use {
                withFailureCapture({ capture("username-unavailable-failure.png") }) {
                    waitForEnabled("New post")
                    peer.assertRequestObserved()
                    val original = peer.clientKeys.first()
                    if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) {
                        ui.onNodeWithText("Later").performClick()
                    }
                    ui.onNodeWithText("Settings").performClick()
                    ui.onNodeWithText("Saito username").assertIsEnabled().performClick()
                    waitForEnabled("Check name")
                    ui.onNodeWithText("Choose a Saito username").assertIsDisplayed()
                    ui.onNode(hasSetTextAction()).performTextReplacement("NativeCase7")
                    ui.onNodeWithText("Check name").performClick()
                    val error = "Username could not be checked. Try again."
                    ui.waitUntil(20_000) { ui.onAllNodesWithText(error).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText(error).assertIsDisplayed()
                    assertEquals("The native flow must actually check with Registry", 1, peer.registryQueries.size)
                    capture("username-unavailable.png", error)
                    ui.onNodeWithText("Back").assertIsEnabled().performClick()
                    waitForEnabled("New post")
                    ui.onNodeWithText("Wallet").performClick()
                    ui.onNodeWithText(original).assertIsDisplayed()
                    assertTrue("A name check must not publish a transaction", peer.transactions.isEmpty())
                    peer.assertHealthy()
                }
            }
        }
    }

    @Test fun aPendingRegistrationSurvivesReopeningAndRetriesTheExactSignedRequest() {
        val peer = ControlledFeedPeer("[]", registryResponse = { criteria ->
            assertEquals("registry namecheck", criteria.get("request").asString)
            assertEquals("NativeCase7@saito", criteria.get("identifier").asString)
            "[]"
        })
        val pending = "Registration requested. Confirmation is still pending."
        peer.start().use { server ->
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                withFailureCapture({ capture("username-pending-failure.png") }) {
                    openUsername()
                    ui.onNode(hasSetTextAction()).performTextReplacement("NativeCase7")
                    ui.onNodeWithText("Check name").performClick()
                    waitForText("This username is currently available. Checking does not reserve it.")
                    assertTrue(peer.transactions.isEmpty())
                    ui.onNodeWithText("Register name").assertIsEnabled().performClick()
                    waitForEnabled("Retry registration")
                    ui.onNodeWithText(pending).assertIsDisplayed()
                    assertEquals(1, peer.transactions.size)
                    val transaction = postingTransaction(peer.transactions.single())
                    val signed = SaitoEnvelope.decode(transaction.envelope())
                    val original = peer.clientKeys.first()
                    assertEquals(original, signed.author)
                    assertEquals(listOf(SaitoRegistry.REGISTRAR, original), signed.recipients)
                    assertEquals(setOf("module", "request", "identifier"), signed.body.keySet())
                    assertEquals("Registry", signed.body.get("module").asString)
                    assertEquals("register", signed.body.get("request").asString)
                    assertEquals("NativeCase7@saito", signed.body.get("identifier").asString)
                    ui.onNode(hasSetTextAction()).assertIsNotEnabled()
                    capture("username-pending.png", pending)
                    File(context.cacheDir, "username-evidence/username-native-frame.json").writeText(JsonObject().apply {
                        addProperty("native", true)
                        addProperty("frame", java.util.Base64.getEncoder().encodeToString(peer.transactions.single()))
                        addProperty("envelope", transaction.envelope())
                    }.toString())
                    scenario.recreate()
                    openUsername()
                    waitForEnabled("Retry registration")
                    ui.onNodeWithText(pending).assertIsDisplayed()
                    ui.onNode(hasSetTextAction()).assertIsNotEnabled()
                    assertEquals("Reopening must only reconcile, never broadcast", 1, peer.transactions.size)
                    capture("username-pending-reopened.png", pending)
                    ui.onNodeWithText("Retry registration").performClick()
                    waitForEnabled("Retry registration")
                    ui.onNodeWithText(pending).assertIsDisplayed()
                    assertEquals(2, peer.transactions.size)
                    assertArrayEquals(peer.transactions.first(), peer.transactions.last())
                    assertTrue(peer.clientKeys.all { it == original })
                    peer.assertHealthy()
                }
            }
        }
    }

    private fun openUsername() {
        waitForEnabled("New post")
        if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
        ui.onNodeWithText("Settings").performClick()
        ui.onNodeWithText("Saito username").assertIsEnabled().performClick()
        waitForEnabled("Back")
    }

    private fun waitForText(text: String) {
        ui.waitUntil(20_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForEnabled(text: String) {
        ui.waitUntil(20_000) { ui.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun capture(name: String, expectedText: String? = null) =
        captureNativeScreen(File(context.cacheDir, "username-evidence/$name"), expectedText)
}
