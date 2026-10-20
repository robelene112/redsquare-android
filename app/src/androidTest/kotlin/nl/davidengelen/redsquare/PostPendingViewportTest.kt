package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real signed pending actions settle by exact readback, while unavailable counts stay truthful. */
class PostPendingViewportTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun pendingRetryAndAvailableActionWithFailedCountRefreshStayExplicitInBothThemes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallets = AndroidWalletStore(context)
        val wallet = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context)
        val originalBackend = backends.load()
        val retained = ChatFixtureState(context, listOf("feed.", "profile.", "profilemeta.", "search.", "namemap.") +
            SaitoBackend.entries.map { "social.${it.id}.${wallet.publicKey}." } + "socialrate.${wallet.publicKey}.", wallet.publicKey)
        val author = SaitoWallet.create()
        val name = "Anon-${author.publicKey.take(6)}"
        val row = postingArchiveRow(SaitoTransaction.create(author,
            RedSquarePosts.message("Pending action viewport preview"), 1_700_000_000_000))
        val envelope = JsonParser.parseString(row.get("tx").asString).asJsonObject
        envelope.addProperty("opt", Base64.getEncoder().encodeToString("{\"num_likes\":4}".toByteArray()))
        row.addProperty("tx", envelope.toString())
        val indexed = ConcurrentHashMap<String, JsonObject>()
        val visible = AtomicBoolean(false)
        val peer = ControlledFeedPeer("[]", onTransaction = { frame ->
            val candidate = postingTransaction(frame)
            val action = RedSquareActions.decode(candidate.envelope())
            assertEquals(wallet.publicKey, action.author)
            assertEquals(row.get("sig").asString, action.target)
            assertEquals(RedSquareActionKind.LIKE, action.kind)
            assertEquals(listOf(wallet.publicKey, author.publicKey).distinct(), action.recipients)
            indexed[action.id] = JsonObject().apply {
                addProperty("sig", action.id); addProperty("created_at", action.createdAt)
                addProperty("field1", "RedSquareLike"); addProperty("field2", action.author)
                addProperty("field4", action.target); addProperty("tx", candidate.envelope())
            }
        }, archiveResponse = { criteria ->
            when {
                criteria.get("field1").asString == "RedSquareLike" ->
                    if (visible.get()) indexed[criteria.get("sig").asString]?.let { "[$it]" } ?: "[]" else "[]"
                criteria.has("sig") -> "[]" // Actual missing target readback, after the signed action settles.
                else -> "[$row]"
            }
        })
        var primary: Throwable? = null
        try {
            retained.prepare()
            check(context.getSharedPreferences("wallet-backup-reminders", 0).edit()
                .putBoolean("hidden:${wallet.publicKey}", false).commit())
            backends.save(SaitoBackend.OWNER)
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { activity ->
                    val folder = File(context.cacheDir, "post-pending-evidence").apply { mkdirs() }
                    ui.waitUntil(20_000) { ui.onNodeWithText("Pending action viewport preview").isDisplayed() }
                    ui.onNodeWithText("Later").performClick()
                    ui.withPostViewports(activity, folder, "pending", scales = listOf(1f)) { label, _ ->
                        visible.set(false)
                        val like = hasContentDescription("Like post by $name") and hasClickAction()
                        ui.waitUntil(15_000) { ui.onAllNodes(like and isEnabled()).fetchSemanticsNodes().size == 1 }
                        val before = peer.transactions.size
                        ui.onNode(like).performClick()
                        val pending = hasContentDescription("Details: Like pending") and hasClickAction()
                        ui.waitUntil(20_000) { ui.onNode(pending).isDisplayed() }
                        assertEquals(before + 1, peer.transactions.size)
                        val candidate = postingTransaction(peer.transactions.last()).id
                        ui.onNodeWithText("4 likes", useUnmergedTree = true).assertIsDisplayed()
                        captureNativeScreen(File(folder, "$label-pending.png"))
                        ui.onNode(pending).performClick()
                        ui.onNodeWithText("Pending. Availability on this node has not been verified.")
                            .performScrollTo().assertIsDisplayed()
                        captureNativeScreen(File(folder, "$label-pending-details.png"))
                        ui.onNodeWithText("Done").performClick()
                        visible.set(true)
                        val retry = hasContentDescription("Retry: Like post by $name") and hasClickAction()
                        ui.onNode(retry).assertIsEnabled().performClick()
                        val counts = hasContentDescription("Details: Like counts unavailable") and hasClickAction()
                        ui.waitUntil(20_000) { ui.onNode(counts).isDisplayed() }
                        ui.onNodeWithText("Liked").assertIsDisplayed()
                        ui.onNodeWithText("4 likes", useUnmergedTree = true).assertIsDisplayed()
                        ui.onNodeWithText("5 likes").assertDoesNotExist()
                        assertEquals("Exact retry must not rebroadcast", before + 1, peer.transactions.size)
                        assertEquals(candidate, postingTransaction(peer.transactions.last()).id)
                        captureNativeScreen(File(folder, "$label-available-counts-unavailable.png"))
                        ui.onNode(counts).performClick()
                        ui.onNodeWithText("Like post by $name").assertIsDisplayed()
                        ui.onNodeWithText("Could not refresh counts. Refresh this view to try again.")
                            .performScrollTo().assertIsDisplayed()
                        captureNativeScreen(File(folder, "$label-counts-details.png"))
                        ui.onNodeWithText("Done").performClick()
                    }
                    assertEquals("One deliberate action per theme", 2, peer.transactions.size)
                    assertEquals(2, indexed.size)
                    assertTrue(peer.reports.isEmpty())
                    assertEquals(wallet.exportJson(), wallets.loadExisting().exportJson())
                    assertEquals(SaitoBackend.OWNER, backends.load())
                    peer.assertHealthy()
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, { retained.restoreFiles() }, { retained.restorePreferences() },
                { backends.save(originalBackend); check(backends.load() == originalBackend) },
                { check(wallets.loadExisting().exportJson() == wallet.exportJson()) })
        }
    }
}
