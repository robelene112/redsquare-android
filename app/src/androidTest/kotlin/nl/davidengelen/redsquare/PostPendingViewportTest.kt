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
import java.util.concurrent.atomic.AtomicInteger
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Like taps dispatch without waiting for readback and leave counts to the selected node. */
class PostPendingViewportTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun pendingRetrySettlesAndRefreshesAvailableCountInBothThemes() {
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
        val receivedLikes = ConcurrentHashMap.newKeySet<String>()
        val indexedLikes = ConcurrentHashMap<String, JsonObject>()
        val likeArchiveReads = AtomicInteger()
        val servedLikeCount = AtomicInteger(4)
        fun countedRow(): JsonObject = row.deepCopy().apply {
            val tx = JsonParser.parseString(get("tx").asString).asJsonObject
            tx.addProperty("opt", Base64.getEncoder().encodeToString(
                "{\"num_likes\":${servedLikeCount.get()}}".toByteArray()))
            addProperty("tx", tx.toString())
        }
        val peer = ControlledFeedPeer("[]", onTransaction = { frame ->
            val candidate = postingTransaction(frame)
            val action = RedSquareActions.decode(candidate.envelope())
            assertEquals(wallet.publicKey, action.author)
            assertEquals(row.get("sig").asString, action.target)
            assertEquals(RedSquareActionKind.LIKE, action.kind)
            assertEquals(listOf(wallet.publicKey, author.publicKey).distinct(), action.recipients)
            receivedLikes += action.id
            indexedLikes[candidate.id] = JsonObject().apply {
                addProperty("sig", candidate.id); addProperty("field1", "RedSquareLike")
                addProperty("field2", action.author); addProperty("field4", action.target)
                addProperty("created_at", action.createdAt); addProperty("tx", candidate.envelope())
            }
            if (indexedLikes.size >= 2) servedLikeCount.set(8)
        }, archiveResponse = { criteria ->
            when {
                criteria.get("field1").asString == "RedSquareLike" -> {
                    likeArchiveReads.incrementAndGet()
                    indexedLikes[criteria.get("sig").asString]?.let { "[$it]" } ?: "[]"
                }
                criteria.has("sig") && criteria.get("sig").asString == row.get("sig").asString -> "[${countedRow()}]"
                criteria.has("sig") -> "[]"
                else -> "[${countedRow()}]"
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
                    var expectedLikes = 4
                    ui.withPostViewports(activity, folder, "immediate-like", scales = listOf(1f)) { label, _ ->
                        val like = hasContentDescription("Like post by $name") and hasClickAction()
                        ui.waitUntil(15_000) { ui.onAllNodes(like and isEnabled()).fetchSemanticsNodes().size == 1 }
                        val before = peer.transactions.size
                        ui.onNode(like).performClick()
                        ui.waitUntil(20_000) { peer.transactions.size == before + 1 }
                        assertEquals(before + 1, peer.transactions.size)
                        ui.onNode(like).assertIsEnabled()
                        expectedLikes++
                        ui.waitUntil(20_000) {
                            ui.onNodeWithText("$expectedLikes likes", useUnmergedTree = true).isDisplayed() ||
                                (expectedLikes == 6 && ui.onNodeWithText("8 likes", useUnmergedTree = true).isDisplayed())
                        }
                        if (!ui.onNodeWithText("$expectedLikes likes", useUnmergedTree = true).isDisplayed()) {
                            assertEquals(6, expectedLikes)
                            ui.onNodeWithText("8 likes", useUnmergedTree = true).assertIsDisplayed()
                        }
                        if (expectedLikes == 6) {
                            ui.waitUntil(20_000) { likeArchiveReads.get() >= 2 }
                            ui.waitUntil(20_000) {
                                ui.onNodeWithText("8 likes", useUnmergedTree = true).isDisplayed()
                            }
                            ui.onNodeWithText("8 likes", useUnmergedTree = true).assertIsDisplayed()
                        }
                        ui.onNodeWithText("Liked").assertDoesNotExist()
                        ui.onNodeWithText("Like pending").assertDoesNotExist()
                        ui.onNodeWithText("Retry like").assertDoesNotExist()
                        captureNativeScreen(File(folder, "$label-like-sent-without-confirmation.png"))
                    }
                    val transactionIds = peer.transactions.map { postingTransaction(it).id }
                    assertEquals("Each theme sends one distinct intentional Like", 2, transactionIds.size)
                    assertEquals(2, transactionIds.distinct().size)
                    assertEquals(2, receivedLikes.size)
                    ui.waitUntil(20_000) { likeArchiveReads.get() >= 2 }
                    assertEquals("Each Like receipt is checked after dispatch", 2, likeArchiveReads.get())
                    assertTrue(peer.reports.isEmpty())
                    assertTrue("The complete wallet and portable options must remain unchanged",
                        wallet.exportJson() == wallets.loadExisting().exportJson())
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
