package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File

class BackendPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Before fun preparePublicState() {
        listOf("feed.owner.v1", "feed.official.v1", "backend.v1").forEach { name ->
            listOf("", ".bak", ".new").forEach { suffix ->
                File(instrumentation.targetContext.noBackupFilesDir, name + suffix).delete()
            }
        }
    }

    @After fun resetSelectionOnly() {
        listOf("", ".bak", ".new").forEach {
            File(instrumentation.targetContext.noBackupFilesDir, "backend.v1$it").delete()
        }
    }

    @Test fun switchingServersKeepsIdentityAndRestoresSeparateCachesThroughOutage() {
        val original = asset("native-feed-reference.json").get("archiveRow")
        val newer = asset("native-feed-scroll-reference.json").getAsJsonArray("refreshedRows")[0]
        val owner = ControlledFeedPeer("[$original]")
        val official = ControlledFeedPeer("[$newer]")
        owner.start().use { ownerServer ->
            official.start().use { officialServer ->
                val intent = Intent(instrumentation.targetContext, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${ownerServer.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${officialServer.port}/wsopen")
                ActivityScenario.launch<MainActivity>(intent).use { activity ->
                    try {
                        waitForText("Native Red Square reference post")
                        ui.onNodeWithText("Wallet").performClick()
                        val address = displayedWalletAddress(ui)
                        ui.onNodeWithText("Done").performClick()
                        val ownerRequests = owner.requests.get()
                        chooseServer("saito.io")
                        waitForText("New reference post")
                        ui.onNodeWithText("Native Red Square reference post").assertDoesNotExist()
                        officialServer.close()
                        activity.recreate()
                        waitForText("Server: saito.io")
                        waitForText("New reference post")
                        waitForText("Server unavailable")
                        ui.onNodeWithText("Native Red Square reference post").assertDoesNotExist()
                        ui.onNodeWithText("Wallet").performClick()
                        assertEquals(address, displayedWalletAddress(ui))
                        ui.onNodeWithText("Done").performClick()
                        assertEquals(ownerRequests, owner.requests.get())
                        chooseServer("saito.davidengelen.nl")
                        waitForText("Native Red Square reference post")
                        ui.onNodeWithText("New reference post").assertDoesNotExist()
                        assertTrue(owner.clientKeys.isNotEmpty() && official.clientKeys.isNotEmpty())
                        assertTrue((owner.clientKeys + official.clientKeys).all { it == address })
                        owner.assertHealthy()
                        official.assertHealthy()
                    } finally { captureFeedEvidence("backend-ui-evidence/screen.png") }
                }
            }
        }
    }

    private fun chooseServer(domain: String) {
        ui.onNodeWithText("Settings").performClick()
        waitForText(domain)
        ui.onNodeWithText(domain).performClick()
    }
    private fun waitForText(text: String) {
        ui.waitUntil(15_000) { ui.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText(text).assertIsDisplayed()
    }
    private fun asset(name: String) = JsonParser.parseReader(
        instrumentation.context.assets.open(name).reader()).asJsonObject
}
