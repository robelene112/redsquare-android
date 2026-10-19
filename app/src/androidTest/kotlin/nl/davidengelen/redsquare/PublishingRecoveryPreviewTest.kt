package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import nl.davidengelen.redsquare.feed.PostDraftStore
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Retained potentially transmitted attempts can still reconcile without authorizing another send. */
class PublishingRecoveryPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also {
        check(it.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
    }

    @Test fun unapprovedPolicyStillAllowsManualReadOnlyReconciliationOfTheOriginalPost() = withIsolatedDraft { wallet, fixture ->
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
                    peer.assertHealthy()
                    capture("manual-original-reconciled-without-policy-acceptance.png")
                }
            }
        }
    }

    @Test fun unreadyNewAndUnreadableAgreementStatesPreserveTheDraftUntilExplicitCurrentAcceptance() = withIsolatedDraft { _, fixture ->
        val text = "Keep this unsent draft through current policy changes 😀 ${UUID.randomUUID()}"
        val first = "owned-first-${UUID.randomUUID()}"
        val current = "owned-current-${UUID.randomUUID()}"
        fun ready(revision: String) = fixture.writeText(JsonObject().apply {
            addProperty("enforce", true); addProperty("ready", true); addProperty("revision", revision)
            addProperty("terms", "Controlled terms for $revision. No real publisher approval.")
            addProperty("community", "Controlled current rules. Keep the draft and deliberate send.")
        }.toString())
        val indexed = AtomicReference<JsonObject?>()
        val peer = ControlledFeedPeer("[]", onTransaction = { frame ->
            val transaction = postingTransaction(frame)
            assertEquals(text, RedSquareFeed.decode(transaction.envelope()).text)
            indexed.set(postingArchiveRow(transaction))
        }, archiveResponse = { criteria ->
            indexed.get()?.takeIf { row -> criteria.get("field1")?.asString == "RedSquare" &&
                (!criteria.has("sig") || criteria.get("sig").asString == row.get("sig").asString) }
                ?.let { "[$it]" } ?: "[]"
        })
        ready(first)
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                withFailureCapture({ capture("policy-revision-recovery-failure.png") }) {
                    click("New post")
                    ui.waitUntil(20_000) { ui.onNode(hasSetTextAction() and isEnabled()).isDisplayed() }
                    ui.onNode(hasSetTextAction()).performTextReplacement(text)
                    androidx.test.espresso.Espresso.closeSoftKeyboard()
                    click("Send"); waitFor("Review publishing terms"); click("Accept")
                    waitFor("Agreement saved. New content stays unsent. Queued messages may resume.")
                    assertTrue(peer.transactions.isEmpty())
                    val record = File(context.noBackupFilesDir, "publishing-agreement.v1")
                    val firstRecord = record.readBytes()
                    assertTrue(firstRecord.isNotEmpty())

                    // An earlier explicit agreement cannot approve a draft or missing current bundle.
                    fixture.writeText("""{"ready":false,"enforce":true}""")
                    click("Send"); waitFor("Publishing terms are being prepared")
                    ui.onAllNodesWithText("Accept").assertCountEquals(0)
                    assertTrue(peer.transactions.isEmpty())
                    assertArrayEquals(firstRecord, record.readBytes())
                    capture("unready-policy-cannot-reuse-earlier-agreement.png")
                    click("Done")
                    ui.onNode(hasSetTextAction()).assertTextEquals(text).assertIsEnabled()

                    ready(current)
                    click("Send"); waitFor("Review publishing terms")
                    ui.onNodeWithText("Controlled terms for $current. No real publisher approval.").assertIsDisplayed()
                    assertTrue(peer.transactions.isEmpty())
                    capture("new-revision-requires-current-explicit-agreement.png")
                    click("Accept")
                    waitFor("Agreement saved. New content stays unsent. Queued messages may resume.")
                    assertTrue(peer.transactions.isEmpty())

                    // Ordinary damaged persisted input, only inside the declared emulator-owned scope.
                    val damaged = ByteArray(128)
                    record.writeBytes(damaged)
                    click("Send"); waitFor("Review publishing terms")
                    ui.onNodeWithText("Controlled terms for $current. No real publisher approval.").assertIsDisplayed()
                    assertTrue(peer.transactions.isEmpty())
                    capture("unreadable-record-does-not-authorize-publishing.png")
                    click("Cancel")
                    ui.onNode(hasSetTextAction()).assertTextEquals(text).assertIsEnabled()
                    assertArrayEquals(damaged, record.readBytes())
                    click("Send"); waitFor("Review publishing terms"); click("Accept")
                    waitFor("Agreement saved. New content stays unsent. Queued messages may resume.")
                    assertTrue(peer.transactions.isEmpty())
                    click("Send"); waitFor("Available on this server.")
                    assertEquals(1, peer.transactions.size)
                    peer.assertHealthy()
                    capture("current-agreement-recovered-and-draft-deliberately-published.png")
                }
            }
        }
    }

    private fun withIsolatedDraft(action: (SaitoWallet, File) -> Unit) {
        val wallets = AndroidWalletStore(context)
        val wallet = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context)
        val previousBackend = backends.load()
        val retained = ChatFixtureState(context, listOf("feed.", "conversation.",
            "postdraft.owner.${wallet.publicKey}", "publishing-agreement."), wallet.publicKey)
        val fixture = File(context.noBackupFilesDir, "publishing-policy-fixture.json")
        val previousPolicy = fixture.takeIf { it.exists() }?.readBytes()
        var primary: Throwable? = null
        try {
            retained.prepare()
            backends.save(SaitoBackend.OWNER)
            action(wallet, fixture)
            assertTrue("The complete wallet must be unchanged", wallet.exportJson() == wallets.loadExisting().exportJson())
            assertEquals(SaitoBackend.OWNER, backends.load())
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
