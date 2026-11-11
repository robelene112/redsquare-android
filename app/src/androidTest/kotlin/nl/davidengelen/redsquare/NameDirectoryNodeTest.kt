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
class NameDirectoryNodeTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun aNodeRecoversItsDirectoryNameWithoutClaimingRegisteredOwnership() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val author = SaitoWallet.create()
        val name = "NodeAlice@saito"
        val row = postingArchiveRow(SaitoTransaction.create(author,
            RedSquarePosts.message("A real signed post with a directory display name"), 1_700_000_000_000L))
        val attempts = java.util.concurrent.atomic.AtomicInteger()
        withAppFrameFixture("directory-node", "name-directory-node-evidence", feedRows = "[$row]",
            registryResponse = { criteria ->
                if (criteria.get("request").asString == "registry namecheck") "[]"
                else if (attempts.incrementAndGet() <= 2) """{"err":"temporarily unavailable"}"""
                else JsonObject().apply {
                    if (criteria.getAsJsonArray("keys").any { it.asString == author.publicKey })
                        addProperty(author.publicKey, name)
                }.toString()
            }) {
                ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
                ui.onNodeWithText("Later").performClick()
                val authorAction = hasText(name) and hasContentDescription("View profile for $name")
                ui.waitUntil(20_000) { ui.onAllNodes(authorAction).fetchSemanticsNodes().isNotEmpty() }
                ui.onNode(authorAction).assertIsDisplayed().performClick()
                ui.waitUntil(10_000) { ui.onNodeWithText("Profile").isDisplayed() }
                ui.inspectProfileIdentity(author.publicKey) {
                    onNodeWithText(author.publicKey, useUnmergedTree = true).assertIsDisplayed()
                    onNode(hasText(name) and hasAnySibling(hasText(author.publicKey)), useUnmergedTree = true).assertIsDisplayed()
                    onNodeWithText("Display name from your selected node.").assertIsDisplayed()
                    captureNativeScreen(File(context.cacheDir, "name-directory-node-evidence/node-profile-name.png"))
                }
                assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
                assertTrue("The selected node must recover without navigation", attempts.get() >= 3)
        }
    }
}
