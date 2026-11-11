package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import java.io.File
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real native labels over owned, authenticated peers; no public writes or invented ownership proof. */
class NameDirectoryRecoveryTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun aTransientDirectoryFailureRecoversWhileTheSamePostRemainsVisible() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val author = SaitoWallet.create()
        val name = "RecoveredAlice@saito"
        val row = postingArchiveRow(SaitoTransaction.create(author,
            RedSquarePosts.message("A real signed post with a directory display name"), 1_700_000_000_000L))
        val attempts = java.util.concurrent.atomic.AtomicInteger()
        val directory = ControlledFeedPeer("[]", registryResponse = { criteria ->
            check(criteria.get("request").asString == "registry query")
            if (attempts.incrementAndGet() <= 2) """{"err":"temporarily unavailable"}""" else
            JsonObject().apply {
                if (criteria.getAsJsonArray("keys").any { it.asString == author.publicKey })
                    addProperty(author.publicKey, name)
            }.toString()
        }, archiveResponse = { error("A name directory must never receive content requests") })
        directory.start().use { server ->
            withAppFrameFixture("directory-recovery", "name-directory-recovery-evidence", feedRows = "[$row]",
                registryResponse = { "{}" }, nameDirectoryPeerUrl = "ws://127.0.0.1:${server.port}/wsopen") {
                ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
                ui.onNodeWithText("Later").performClick()
                val authorAction = hasText(name) and hasContentDescription("View profile for $name")
                ui.waitUntil(20_000) { ui.onAllNodes(authorAction).fetchSemanticsNodes().isNotEmpty() }
                ui.onNode(authorAction).assertIsDisplayed().performClick()
                ui.waitUntil(10_000) { ui.onNodeWithText("Profile").isDisplayed() }
                ui.inspectProfileIdentity(author.publicKey) {
                    onNodeWithText(author.publicKey, useUnmergedTree = true).assertIsDisplayed()
                    onNode(hasText(name) and hasAnySibling(hasText(author.publicKey)), useUnmergedTree = true).assertIsDisplayed()
                    captureNativeScreen(File(context.cacheDir, "name-directory-recovery-evidence/recovered-profile-name.png"))
                }
                assertTrue("A transient read failure must retry without navigation", attempts.get() >= 3)
                assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
                assertTrue(directory.registryQueries.any { it.getAsJsonArray("keys").any { key -> key.asString == author.publicKey } })
                assertTrue(directory.transactions.isEmpty())
                directory.assertHealthy()
            }
        }
    }
}
