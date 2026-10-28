package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import org.junit.After
import org.junit.Before
import org.junit.rules.TestName
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File

class BackendPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @get:Rule val testName = TestName()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Before fun preparePublicState() {
        listOf("feed.owner.v1", "feed.official.v1", "backend.v1", "backend.pending").forEach { name ->
            listOf("", ".bak", ".new").forEach { suffix ->
                File(instrumentation.targetContext.noBackupFilesDir, name + suffix).delete()
            }
        }
    }

    @After fun resetSelectionOnly() {
        listOf("", ".bak", ".new").forEach {
            File(instrumentation.targetContext.noBackupFilesDir, "backend.v1$it").delete()
        }
        File(instrumentation.targetContext.noBackupFilesDir, "backend.pending").delete()
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
                        waitForText("Node: saito.io")
                        waitForText("New reference post")
                        waitForText("Node unavailable")
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
                    } finally { captureEvidence() }
                }
            }
        }
    }

    @Test fun failedSelectionSaveKeepsTheCurrentServerAndCanBeRetried() {
        val original = asset("native-feed-reference.json").get("archiveRow")
        val newer = asset("native-feed-scroll-reference.json").getAsJsonArray("refreshedRows")[0]
        val owner = ControlledFeedPeer("[$original]")
        val official = ControlledFeedPeer("[$newer]")
        owner.start().use { ownerServer ->
            official.start().use { officialServer ->
                val intent = Intent(instrumentation.targetContext, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${ownerServer.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${officialServer.port}/wsopen")
                ActivityScenario.launch<MainActivity>(intent).use {
                    try {
                        waitForText("Native Red Square reference post")
                        chooseServer("saito.davidengelen.nl")
                        ui.waitUntil(15_000) {
                            ui.onAllNodes(hasText("Settings")).fetchSemanticsNodes().size == 1
                        }
                        waitForText("Node: saito.davidengelen.nl")
                        val file = File(instrumentation.targetContext.noBackupFilesDir, "backend.v1")
                        val preserved = File(instrumentation.targetContext.noBackupFilesDir, "backend.test.saved")
                        check(file.renameTo(preserved))
                        check(file.mkdir())
                        val obstruction = File(file, "obstruction").apply { writeText("selection fixture") }
                        try {
                            chooseServer("saito.io")
                            waitForText("Your node selection could not be saved. Try again.")
                            ui.onNodeWithText("Done").performClick()
                            waitForText("Node: saito.davidengelen.nl")
                            ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
                            assertTrue(official.clientKeys.isEmpty())
                        } finally {
                            check(obstruction.delete())
                            check(file.delete())
                            check(preserved.renameTo(file))
                        }
                        chooseServer("saito.io")
                        waitForText("New reference post")
                        ui.onNodeWithText("Native Red Square reference post").assertDoesNotExist()
                        owner.assertHealthy()
                        official.assertHealthy()
                    } finally { captureEvidence() }
                }
            }
        }
    }

    private fun captureEvidence() {
        captureFeedEvidence("backend-ui-evidence/${testName.methodName}.png")
        captureFeedEvidence("backend-ui-evidence/screen.png")
    }

    @Test fun uncertainSelectionCommitStopsConnectionsUntilExplicitRecoveryAfterRestart() {
        val owner = ControlledFeedPeer("[${asset("native-feed-reference.json").get("archiveRow")}]")
        val official = ControlledFeedPeer("[${asset("native-feed-scroll-reference.json").getAsJsonArray("refreshedRows")[0]}]")
        owner.start().use { ownerServer ->
            official.start().use { officialServer ->
                val intent = Intent(instrumentation.targetContext, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${ownerServer.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${officialServer.port}/wsopen")
                ActivityScenario.launch<MainActivity>(intent).use { activity ->
                    try {
                        waitForText("Native Red Square reference post")
                        activity.onActivity { it.intent.putExtra("backendCommitSyncUnavailable", true) }
                        chooseServer("saito.io")
                        waitForText("Your node selection could not be saved. Try again.")
                        ui.onNodeWithText("Done").performClick()
                        waitForText("Your node selection could not be opened. Choose a node in Settings.")
                        ui.onNodeWithText("Native Red Square reference post").assertDoesNotExist()
                        assertTrue(official.clientKeys.isEmpty())
                        activity.recreate()
                        waitForText("Your node selection could not be opened. Choose a node in Settings.")
                        assertTrue(official.clientKeys.isEmpty())
                        activity.onActivity { it.intent.putExtra("backendCommitSyncUnavailable", false) }
                        chooseServer("saito.davidengelen.nl")
                        waitForText("Native Red Square reference post")
                        activity.recreate()
                        waitForText("Node: saito.davidengelen.nl")
                        waitForText("Native Red Square reference post")
                        assertTrue(official.clientKeys.isEmpty())
                        owner.assertHealthy()
                    } finally { captureEvidence() }
                }
            }
        }
    }

    @Test fun unreadableSelectionRequiresAnExplicitChoiceBeforeConnecting() {
        File(instrumentation.targetContext.noBackupFilesDir, "backend.v1")
            .writeText("https://unapproved.invalid")
        val owner = ControlledFeedPeer("[${asset("native-feed-reference.json").get("archiveRow")}]")
        val official = ControlledFeedPeer("[${asset("native-feed-scroll-reference.json").getAsJsonArray("refreshedRows")[0]}]")
        owner.start().use { ownerServer ->
            official.start().use { officialServer ->
                val intent = Intent(instrumentation.targetContext, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${ownerServer.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${officialServer.port}/wsopen")
                ActivityScenario.launch<MainActivity>(intent).use { activity ->
                    try {
                        waitForText("Your node selection could not be opened. Choose a node in Settings.")
                        assertTrue(owner.clientKeys.isEmpty() && official.clientKeys.isEmpty())
                        chooseServer("saito.io")
                        waitForText("New reference post")
                        activity.recreate()
                        waitForText("Node: saito.io")
                        waitForText("New reference post")
                        ui.onNodeWithText("Native Red Square reference post").assertDoesNotExist()
                        assertTrue(owner.clientKeys.isEmpty())
                        official.assertHealthy()
                    } finally { captureEvidence() }
                }
            }
        }
    }

    private fun chooseServer(domain: String) {
        ui.onNodeWithText("Settings").performClick()
        ui.onNodeWithText("Node").performScrollTo().performClick()
        ui.waitUntil(15_000) { ui.onAllNodes(hasText(domain)).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText(domain).performScrollTo().performClick()
    }
    private fun waitForText(text: String) {
        ui.waitUntil(15_000) { ui.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText(text).assertIsDisplayed()
    }
    private fun asset(name: String) = JsonParser.parseReader(
        instrumentation.context.assets.open(name).reader()).asJsonObject
}
