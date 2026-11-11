package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NameDirectoryIsolationTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun unavailableProofsDoNotDelayNamesOrTheNextVisibleBatch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val first = SaitoWallet.create()
        val next = SaitoWallet.create()
        val names = mapOf(first.publicKey to "FastFirst@saito", next.publicKey to "FastNext@saito")
        val firstRow = row(first, "The first independent directory name", 1_700_000_000_000L)
        val rows = AtomicReference("[$firstRow]")
        withAppFrameFixture("directory-proof-isolation", "name-directory-isolation-evidence", rows.get(),
            archiveResponse = { criteria -> if (criteria.get("field1").asString == "RedSquare") rows.get() else "[]" },
            registryResponse = { criteria ->
                if (criteria.get("request").asString == "registry namecheck") """{"err":"no response"}"""
                else mappings(criteria, names)
            }) {
            ready()
            ui.waitUntil(5_000) { ui.onNodeWithText("FastFirst@saito").isDisplayed() }
            rows.set("[${row(next, "A new independent directory batch", 1_700_000_010_000L)},$firstRow]")
            ui.waitUntil(10_000) { ui.refreshableContentReady() }
            ui.refreshVisibleView()
            ui.waitUntil(5_000) { ui.onNodeWithText("FastNext@saito").isDisplayed() }
            captureNativeScreen(File(context.cacheDir, "name-directory-isolation-evidence/next-batch.png"))
        }
    }

    @Test fun disablingTheExtraProviderKeepsTheSelectedNodesOfflineNames() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val author = SaitoWallet.create()
        val name = "LocalCached@saito"
        val offline = AtomicBoolean(false)
        val directory = ControlledFeedPeer("[]", registryResponse = { "{}" },
            archiveResponse = { error("A separate name directory must not receive content") })
        directory.start().use { server ->
            withAppFrameFixture("directory-node-offline", "name-directory-isolation-evidence",
                feedRows = "[${row(author, "A retained name from the selected node", 1_700_000_000_000L)}]",
                nameDirectoryPeerUrl = "ws://127.0.0.1:${server.port}/wsopen",
                registryResponse = { criteria ->
                    when {
                        offline.get() -> """{"err":"temporarily unavailable"}"""
                        criteria.get("request").asString == "registry namecheck" -> "[]"
                        else -> mappings(criteria, mapOf(author.publicKey to name))
                    }
                }) { activity ->
                ready()
                ui.waitUntil(20_000) { ui.onNodeWithText(name).isDisplayed() }
                ui.openSettings()
                ui.onNodeWithText("Node").performScrollTo().performClick()
                val choice = hasText("Use Saito Registry for missing names") and isToggleable()
                offline.set(true)
                ui.onNode(choice).performScrollTo().assertIsOn().performClick()
                ui.waitUntil(5_000) {
                    ui.onNode(choice).fetchSemanticsNode().config[SemanticsProperties.ToggleableState] == ToggleableState.Off
                }
                ui.onNodeWithText("Back to Settings").performClick()
                ui.closeSettingsOverview()
                ui.waitUntil(5_000) { ui.onNodeWithText(name).isDisplayed() }
                activity.recreate()
                ui.waitUntil(10_000) { ui.onNodeWithText(name).isDisplayed() }
                ui.onNode(hasText(name) and hasContentDescription("View profile for $name")).performClick()
                ui.waitUntil(10_000) { ui.onNodeWithText("Profile").isDisplayed() }
                ui.inspectProfileIdentity(author.publicKey) {
                    onNodeWithText(author.publicKey, useUnmergedTree = true).assertIsDisplayed()
                    onNodeWithText("Saved name from your selected node.").assertIsDisplayed()
                    captureNativeScreen(File(context.cacheDir, "name-directory-isolation-evidence/selected-offline.png"))
                }
                assertTrue(directory.transactions.isEmpty())
                directory.assertHealthy()
            }
        }
    }

    private fun ready() {
        ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
        ui.onNodeWithText("Later").performClick()
    }

    private fun mappings(criteria: JsonObject, names: Map<String, String>): String = JsonObject().apply {
        criteria.getAsJsonArray("keys").forEach { key -> names[key.asString]?.let { addProperty(key.asString, it) } }
    }.toString()

    private fun row(author: SaitoWallet, text: String, timestamp: Long): JsonObject =
        postingArchiveRow(SaitoTransaction.create(author, RedSquarePosts.message(text), timestamp))
}
