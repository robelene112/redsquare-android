package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import nl.davidengelen.redsquare.feed.AndroidFeedCache
import nl.davidengelen.redsquare.feed.PublicStringStore
import nl.davidengelen.redsquare.saito.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

class FollowingPartialFailureTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun eitherFailureOrderingKeepsHealthyAndCachedPostsAndRetriesWithoutFalseRefreshSuccess() {
        for (firstFails in listOf(true, false)) exercise(firstFails)
    }

    private fun exercise(firstFails: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallets = AndroidWalletStore(context)
        val previousWallet = wallets.loadOrCreate()
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val other = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
        val authors = listOf(wallet, other).sortedBy { it.publicKey }
        val failed = authors[if (firstFails) 0 else 1]
        val healthy = authors[if (firstFails) 1 else 0]
        val backends = AndroidBackendStore(context)
        val previousBackend = backends.load()
        val retained = ChatFixtureState(context, listOf("feed.owner.",
            "followingfeed.owner." + wallet.publicKey + ".", "following." + wallet.publicKey + ".",
            "personal." + wallet.publicKey + "."), wallet.publicKey)
        val mode = if (firstFails) "first-disconnected" else "last-rejected"
        fun post(author: SaitoWallet, text: String, time: Long): FeedPost {
            val body = JsonObject().apply {
                addProperty("module", "RedSquare"); addProperty("request", "create tweet")
                add("data", JsonObject().apply { addProperty("text", text) })
            }
            return RedSquareFeed.decode(SaitoTransaction.create(author, body, time).envelope())
        }
        val cachedFailed = post(failed, "Retained post from temporarily unavailable author", 1700000000000)
        val cachedHealthy = post(healthy, "Retained post from healthy author", 1700000000001)
        val newHealthy = post(healthy, "Fresh post from healthy followed author", 1700000000002)
        val recovered = post(failed, "Fresh post from recovered followed author", 1700000000003)
        val wrongSigner = post(failed, "Wrong signer must not enter the healthy author page", 1700000000004)
        fun row(post: FeedPost, label: String = post.author) = JsonObject().apply {
            addProperty("field1", "RedSquare"); addProperty("field2", label)
            addProperty("created_at", post.createdAt); addProperty("tx", post.envelope)
        }
        val attempts = CopyOnWriteArrayList<String>()
        val peer = ControlledFeedPeer("[]", quoteArchiveResponse = { "[]" }, archiveResponse = { query ->
            val author = query.get("field2")?.asString
            if (query.get("field1")?.asString == "RedSquare" && author in authors.map { it.publicKey }) {
                attempts.add(requireNotNull(author))
                if (author == healthy.publicKey) JsonArray().apply {
                    add(row(newHealthy)); add(row(wrongSigner, healthy.publicKey))
                }.toString() else "[" + row(recovered) + "]"
            } else "[]"
        }).apply {
            if (firstFails) disconnectedArchiveAuthors = setOf(failed.publicKey)
            else rejectedArchiveAuthors = setOf(failed.publicKey)
        }
        var primary: Throwable? = null
        try {
            retained.prepare(); wallets.replace(previousWallet, wallet); backends.save(SaitoBackend.OWNER)
            PublicStringStore(context, "following." + wallet.publicKey + ".v1", 1000).save(authors.map { it.publicKey })
            AndroidFeedCache(context, SaitoBackend.OWNER, followingOwner = wallet.publicKey)
                .save(listOf(cachedFailed, cachedHealthy))
            peer.start().use { server ->
                val endpoint = "ws://127.0.0.1:" + server.port + "/wsopen"
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", endpoint).putExtra("feedOfficialPeerUrl", endpoint)).use {
                    withFailureCapture({
                        val folder = File(context.cacheDir, "following-partial-evidence").apply { mkdirs() }
                        File(folder, mode + "-failure-reads.json").writeText(JsonObject().apply {
                            add("authorReadOrder", JsonArray().apply { attempts.forEach { add(it) } })
                            add("callbacks", JsonArray().apply { peer.archiveObservations.forEach { add(it) } })
                        }.toString())
                        captureNativeScreen(File(folder, mode + "-failure.png"))
                    }) {
                        ui.waitUntil(15_000) { ui.onAllNodesWithText("Following").fetchSemanticsNodes().isNotEmpty() }
                        ui.onNodeWithText("Following").performClick()
                        val list = ui.onNodeWithTag("feed-post-list")
                        ui.waitUntil(20_000) {
                            peer.assertHealthy()
                            ui.onAllNodesWithTag("feed-post-list").fetchSemanticsNodes().singleOrNull()
                                ?.config?.get(SemanticsProperties.CollectionInfo)?.rowCount == 3
                        }
                        fun display(text: String) {
                            list.performScrollToIndex(0); list.performScrollToNode(hasText(text))
                            ui.onNodeWithText(text).assertIsDisplayed()
                        }
                        display(newHealthy.text); display(cachedHealthy.text); display(cachedFailed.text)
                        ui.onAllNodesWithText(wrongSigner.text).assertCountEquals(0)
                        ui.waitUntil(10_000) { ui.onAllNodesWithText("Some followed accounts could not load.").fetchSemanticsNodes().isNotEmpty() }
                        ui.onNodeWithText("Retry").assertIsEnabled()
                        check(attempts.indexOf(authors[0].publicKey) < attempts.indexOf(authors[1].publicKey))
                        val beforePull = attempts.size
                        ui.waitForRefreshableContent(10_000); ui.refreshVisibleView()
                        ui.waitUntil(15_000) { attempts.size >= beforePull + 2 && ui.onAllNodes(hasText("Retry") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                        ui.onAllNodesWithText("Feed updated.").assertCountEquals(0)
                        display(newHealthy.text); display(cachedFailed.text)
                        captureNativeScreen(File(context.cacheDir, "following-partial-evidence/" + mode + "-partial.png"))
                        val failedBeforeRecovery = attempts.count { it == failed.publicKey }
                        peer.disconnectedArchiveAuthors = emptySet(); peer.rejectedArchiveAuthors = emptySet()
                        ui.onNodeWithText("Retry").performClick()
                        ui.waitUntil(15_000) { list.fetchSemanticsNode().config[SemanticsProperties.CollectionInfo].rowCount == 4 }
                        display(recovered.text); display(newHealthy.text); display(cachedHealthy.text); display(cachedFailed.text)
                        ui.waitUntil(10_000) { ui.onAllNodesWithText("Some followed accounts could not load.").fetchSemanticsNodes().isEmpty() }
                        check(attempts.count { it == failed.publicKey } > failedBeforeRecovery)
                        check(peer.transactions.isEmpty())
                        check(backends.load() == SaitoBackend.OWNER)
                        check(wallets.loadExisting().exportJson() == wallet.exportJson())
                        if (firstFails) check(peer.archiveObservations.any { it.get("event").asString == "disconnect" })
                        val folder = File(context.cacheDir, "following-partial-evidence").apply { mkdirs() }
                        File(folder, mode + ".json").writeText(JsonObject().apply {
                            addProperty("firstAuthorFailed", firstFails); addProperty("cachedPostsPreserved", 2)
                            addProperty("loadedPosts", 4); addProperty("failedAuthorAttemptsBeforeRecovery", failedBeforeRecovery)
                            addProperty("failedAuthorAttemptsAfterRecovery", attempts.count { it == failed.publicKey })
                            addProperty("publications", peer.transactions.size)
                            add("authorReadOrder", JsonArray().apply { attempts.forEach { add(it) } })
                            add("callbacks", JsonArray().apply { peer.archiveObservations.forEach { add(it) } })
                        }.toString())
                        captureNativeScreen(File(folder, mode + "-recovered.png"))
                        peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, { retained.restoreFiles() }, { retained.restorePreferences() },
                { if (backends.load() != previousBackend) backends.save(previousBackend) },
                { wallets.replace(wallets.loadExisting(), previousWallet) })
        }
    }
}
