package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.feed.PostDraftStore
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Retained potentially transmitted attempts can still reconcile without authorizing another send. */
class PublishingRecoveryPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also {
        check(it.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
    }

    @Test fun unapprovedPolicyStillAllowsManualReadOnlyReconciliationOfTheOriginalPost() {
        val wallets = AndroidWalletStore(context)
        val wallet = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context)
        val previousBackend = backends.load()
        val retained = ChatFixtureState(context, listOf("feed.", "conversation.",
            "postdraft.owner.${wallet.publicKey}", "publishing-agreement."), wallet.publicKey)
        val fixture = File(context.noBackupFilesDir, "publishing-policy-fixture.json")
        val previousPolicy = fixture.takeIf { it.exists() }?.readBytes()
        val text = "Recognize this previously sent original ${UUID.randomUUID()}"
        val candidate = SaitoTransaction.create(wallet, RedSquarePosts.message(text), System.currentTimeMillis(), emptyList())
        val archived = postingArchiveRow(candidate)
        val originalLookups = AtomicInteger()
        val peer = ControlledFeedPeer("[]", archiveResponse = { criteria ->
            if (criteria.get("field1")?.asString == "RedSquare" &&
                criteria.get("sig")?.asString == candidate.id) {
                originalLookups.incrementAndGet()
                "[$archived]"
            } else "[]"
        })
        var primary: Throwable? = null
        try {
            retained.prepare()
            backends.save(SaitoBackend.OWNER)
            // Owned persisted input represents a legacy manual uncertain attempt, not queued intent.
            PostDraftStore(context, wallet.publicKey, SaitoBackend.OWNER)
                .save(PostDraftStore.Saved(text, candidate, queued = false, automatic = false))
            fixture.writeText("""{"ready":false,"enforce":true}""")
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                    withFailureCapture({ capture("manual-reconciliation-failure.png") }) {
                        click("New post")
                        click("Retry")
                        waitFor("Available on this server.")
                        assertTrue("The exact original must actually be queried", originalLookups.get() > 0)
                        ui.onAllNodesWithText("Publishing terms are being prepared").assertCountEquals(0)
                        ui.onAllNodesWithText("Review publishing terms").assertCountEquals(0)
                        assertTrue("Reading must not rebroadcast", peer.transactions.isEmpty())
                        val saved = PostDraftStore(context, wallet.publicKey, SaitoBackend.OWNER).load()
                        assertTrue(saved.available)
                        assertEquals(candidate.envelope(), saved.transaction?.envelope())
                        assertFalse(saved.queued || saved.automatic)
                        assertTrue("The complete wallet must be unchanged", wallet.exportJson() == wallets.loadExisting().exportJson())
                        assertEquals(SaitoBackend.OWNER, backends.load())
                        peer.assertHealthy()
                        capture("manual-original-reconciled-without-policy-acceptance.png")
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, { retained.restoreFiles() }, { backends.save(previousBackend) },
                { retained.restorePreferences() },
                { if (previousPolicy == null) check(!fixture.exists() || fixture.delete()) else fixture.writeBytes(previousPolicy) })
        }
    }

    private fun click(text: String) {
        ui.waitUntil(20_000) { ui.onAllNodes(hasText(text) and hasClickAction() and isEnabled()).fetchSemanticsNodes().size == 1 }
        ui.onNodeWithText(text).performClick()
    }
    private fun waitFor(text: String) = ui.waitUntil(20_000) { ui.onNodeWithText(text).isDisplayed() }
    private fun capture(name: String) = captureNativeScreen(File(context.cacheDir, "agreement-recovery-evidence/$name"))
}
