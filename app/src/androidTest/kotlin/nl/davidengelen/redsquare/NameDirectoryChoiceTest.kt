package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import java.io.File
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real native labels over owned, authenticated peers; no public writes or invented ownership proof. */
class NameDirectoryChoiceTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun theNameProviderChoiceIsReachableAndPersistsWithoutChangingTheContentNode() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val author = SaitoWallet.create()
        val name = "ChoiceAlice@saito"
        val row = postingArchiveRow(SaitoTransaction.create(author,
            RedSquarePosts.message("A real signed post with a directory display name"), 1_700_000_000_000L))
        val directory = ControlledFeedPeer("[]", registryResponse = { criteria ->
            check(criteria.get("request").asString == "registry query")
            JsonObject().apply {
                if (criteria.getAsJsonArray("keys").any { it.asString == author.publicKey })
                    addProperty(author.publicKey, name)
            }.toString()
        }, archiveResponse = { error("A name directory must never receive content requests") })
        directory.start().use { server ->
            withAppFrameFixture("directory-choice", "name-directory-choice-evidence", feedRows = "[$row]",
                registryResponse = { "{}" }, nameDirectoryPeerUrl = "ws://127.0.0.1:${server.port}/wsopen") { activity ->
                ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
                ui.onNodeWithText("Later").performClick()
                val authorAction = hasText(name) and hasContentDescription("View profile for $name")
                ui.waitUntil(20_000) { ui.onAllNodes(authorAction).fetchSemanticsNodes().isNotEmpty() }
                val folder = File(context.cacheDir, "name-directory-choice-evidence").apply { mkdirs() }
                var recreatedWhileOff = false
                ui.withPostViewports(activity, folder, "name-directory", widthDp = 320) { label, _ ->
                    ui.openSettings()
                    ui.onNodeWithText("Node").performScrollTo().performClick()
                    val choice = hasText("Use Saito Registry for missing names") and isToggleable()
                    ui.onNode(choice).performScrollTo().assertIsOn()
                        .assertHeightIsAtLeast(48.dp)
                    ui.onNode(choice).performClick()
                    ui.waitUntil(5_000) { ui.onNode(choice and androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.ToggleableState, androidx.compose.ui.state.ToggleableState.Off) and isEnabled()).isDisplayed() }
                    captureNativeScreen(File(folder, "$label-disabled.png"))
                    ui.onNodeWithText("Back to Settings").performClick()
                    ui.closeSettingsOverview()
                    val anonymous = hasText("Anon-${author.publicKey.take(6)}") and
                        hasContentDescription("View profile for Anon-${author.publicKey.take(6)}")
                    ui.waitUntil(15_000) { ui.onAllNodes(anonymous).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(authorAction).assertDoesNotExist()
                    val readsWhileOff = directory.registryQueries.size
                    if (!recreatedWhileOff) {
                        activity.recreate()
                        ui.waitUntil(15_000) { ui.onAllNodes(anonymous).fetchSemanticsNodes().isNotEmpty() }
                        assertEquals("A disabled provider must not be queried after recreation", readsWhileOff,
                            directory.registryQueries.size)
                        recreatedWhileOff = true
                    }
                    ui.openSettings()
                    ui.onNodeWithText("Node").performScrollTo().performClick()
                    ui.onNode(choice).performScrollTo().assertIsOff().performClick()
                    ui.waitUntil(5_000) { ui.onNode(choice and androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.ToggleableState, androidx.compose.ui.state.ToggleableState.On) and isEnabled()).isDisplayed() }
                    captureNativeScreen(File(folder, "$label-enabled.png"))
                    ui.onNodeWithText("Back to Settings").performClick()
                    ui.closeSettingsOverview()
                    ui.waitUntil(20_000) { ui.onAllNodes(authorAction).fetchSemanticsNodes().isNotEmpty() }
                }
                assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
                assertTrue(directory.registryQueries.any { it.getAsJsonArray("keys").any { key -> key.asString == author.publicKey } })
                assertTrue(directory.transactions.isEmpty())
                directory.assertHealthy()
            }
        }
    }
}
