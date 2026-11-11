package nl.davidengelen.redsquare

import android.content.Intent
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import java.io.File
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import org.junit.Assert.*

/** Read-only frame peer and declared local state, restored after the Activity closes. No recomposer replacement. */
internal fun withAppFrameFixture(name: String, evidenceFolder: String = "app-frame-evidence", feedRows: String? = null, configurePeer: (ControlledFeedPeer) -> Unit = {}, archiveResponse: ((com.google.gson.JsonObject) -> String)? = null, registryResponse: ((com.google.gson.JsonObject) -> String)? = null, profileArchiveResponse: ((com.google.gson.JsonObject) -> String)? = null, quoteArchiveResponse: ((com.google.gson.JsonObject) -> String)? = null, balanceApiUrl: String? = null, nodeBlockApiUrl: String? = null, nodeBlockOfficialApiUrl: String? = null, giphyFixtureUrl: String? = null, stackDesignFixture: Boolean = false, block: (ActivityScenario<MainActivity>) -> Unit) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    check(context.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
    val wallets = AndroidWalletStore(context)
    val wallet = wallets.loadOrCreate()
    val backends = AndroidBackendStore(context)
    val originalBackend = backends.load()
    val retained = ChatFixtureState(context, listOf("feed.", "conversation.", "profile.", "profilemeta.", "profiledraft.", "search.",
        "authorposts.", "postdraft.", "namemap.", "following.", "quoteoriginal.", "stack."), wallet.publicKey)
    val reminder = context.getSharedPreferences("wallet-backup-reminders", 0)
    val rows = feedRows ?: JsonParser.parseReader(instrumentation.context.assets.open("native-feed-reference.json").reader())
        .asJsonObject.get("archiveRow").let { "[$it]" }
    val peer = ControlledFeedPeer(rows, registryResponse = registryResponse, profileArchiveResponse = profileArchiveResponse,
        quoteArchiveResponse = quoteArchiveResponse, archiveResponse = { criteria ->
        archiveResponse?.invoke(criteria) ?: when (criteria.get("field1").asString) {
            "RedSquare" -> rows
            "RedSquareLike" -> "[]"
            else -> error("Unexpected frame browsing request")
        }
    })
    configurePeer(peer)
    fun cacheObservation(stage: String) {
        val output = com.google.gson.JsonObject().apply {
            addProperty("stage", stage)
            addProperty("elapsedMillis", android.os.SystemClock.elapsedRealtime())
            addProperty("peerRequests", peer.requests.get())
            addProperty("peerConnections", peer.connectionAttempts.get())
            addProperty("incomingRows", runCatching { JsonParser.parseString(peer.rows).asJsonArray.size() }.getOrNull())
            add("publicFeedCaches", com.google.gson.JsonArray().apply {
                synchronized(nl.davidengelen.redsquare.feed.PublicStringStore.lock) {
                    context.noBackupFilesDir.listFiles().orEmpty().filter { it.isFile && it.name.startsWith("feed.") }
                        .sortedBy { it.name }.forEach { file ->
                            val bytes = file.readBytes()
                            add(com.google.gson.JsonObject().apply {
                                addProperty("name", file.name)
                                addProperty("bytes", bytes.size)
                                addProperty("sha256", java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
                                    .joinToString("") { "%02x".format(it.toInt() and 255) })
                                addProperty("entries", runCatching { JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonArray.size() }.getOrNull())
                            })
                        }
                }
            })
        }
        File(context.cacheDir, evidenceFolder).apply { mkdirs() }.resolve("$name-$stage-cache.json").writeText(output.toString())
    }
    var primary: Throwable? = null
    try {
        cacheObservation("before-prepare")
        retained.prepare()
        cacheObservation("after-prepare")
        check(reminder.edit().putBoolean("hidden:${wallet.publicKey}", false).commit())
        backends.save(SaitoBackend.OWNER)
        peer.start().use { server ->
            val launchIntent = Intent(context, MainActivity::class.java).putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/")
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                .putExtra("usernameFixtureRegistrar", registryResponse != null)
                .putExtra("stackDesignFixture", stackDesignFixture)
            giphyFixtureUrl?.let { launchIntent.putExtra("giphyFixtureUrl", it) }
            balanceApiUrl?.let { launchIntent.putExtra("balanceApiUrl", it) }
            nodeBlockApiUrl?.let { launchIntent.putExtra("nodeBlockApiUrl", it) }
            nodeBlockOfficialApiUrl?.let { launchIntent.putExtra("nodeBlockOfficialApiUrl", it) }
            ActivityScenario.launch<MainActivity>(launchIntent).use { activity ->
                withFailureCapture({
                    cacheObservation("failure")
                    captureNativeScreen(File(context.cacheDir, "$evidenceFolder/$name-failure.png"))
                }) {
                    block(activity)
                    assertTrue("Navigation must retain the complete wallet and portable options",
                        wallet.exportJson() == wallets.loadExisting().exportJson())
                    assertEquals(SaitoBackend.OWNER, backends.load())
                    assertTrue(peer.transactions.isEmpty()); assertTrue(peer.reports.isEmpty()); peer.assertHealthy()
                }
            }
        }
    } catch (failure: Throwable) { primary = failure; throw failure }
    finally {
        cleanupAfterTest(primary, { retained.restoreFiles() }, { retained.restorePreferences() },
            { backends.save(originalBackend); check(backends.load() == originalBackend) },
            { check(wallets.loadExisting().exportJson() == wallet.exportJson()) })
    }
}

