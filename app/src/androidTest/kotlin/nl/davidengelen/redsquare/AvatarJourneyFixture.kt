package nl.davidengelen.redsquare

import android.content.Intent
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** All protocol data and signing keys are published disposable synthetic identities. */
internal fun withAvatarJourney(theme: String, block: (ActivityScenario<MainActivity>, ControlledFeedPeer,
    AtomicReference<String>, AtomicInteger, SaitoWallet) -> Unit) {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    check(context.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
    val author = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
    val reader = SaitoWallet.fromPrivateKey("0".repeat(63) + "2").withChatContact(author.publicKey)
    val wallets = AndroidWalletStore(context)
    val prior = wallets.loadOrCreate()
    val backend = AndroidBackendStore(context)
    val priorBackend = backend.load()
    val appearance = context.getSharedPreferences("appearance", 0)
    val priorAppearance = appearance.getString("mode", null)
    val state = ChatFixtureState(context, listOf("feed.", "conversation.", "profile.", "profilemeta.", "search.",
        "authorposts.", "namemap.", "notifications.", "notificationread.", "following.", "personal.", "chat."), reader.publicKey)
    val root = SaitoTransaction.create(author, RedSquarePosts.message("Shared avatar journey"), 1_790_000_000_000L)
    val own = SaitoTransaction.create(reader, RedSquarePosts.message("Avatar activity target"), 1_790_000_000_001L)
    val like = SaitoTransaction.create(author, JsonObject().apply {
        addProperty("module", "RedSquare"); addProperty("request", "like tweet")
        add("data", JsonObject().apply { addProperty("signature", own.id) })
    }, 1_790_000_000_002L, listOf(reader.publicKey))
    fun row(tx: SaitoTransaction) = "[{\"tx\":${com.google.gson.Gson().toJson(tx.envelope())}}]"
    val response = AtomicReference("[]")
    val requests = AtomicInteger()
    val peer = ControlledFeedPeer(row(root), advertisedServices = "archive||;relay||", profileArchiveResponse = { query ->
        if (query.get("field2").asString == author.publicKey) { requests.incrementAndGet(); response.get() } else "[]"
    }, archiveResponse = { query -> when (query.get("field1").asString) {
        "RedSquareLike" -> if (query.get("field3")?.asString == reader.publicKey) row(like) else "[]"
        "RedSquare" -> when {
            query.get("sig")?.asString == own.id -> row(own)
            query.has("sig") -> row(root)
            query.has("field3") || !query.get("field4")?.asString.isNullOrEmpty() -> "[]"
            else -> row(root)
        }
        "Chat" -> "[]"
        else -> error("Unexpected avatar journey read")
    } })
    var failure: Throwable? = null
    try {
        state.prepare()
        wallets.replace(prior, reader)
        backend.save(SaitoBackend.OWNER)
        check(appearance.edit().putString("mode", theme.uppercase()).commit())
        check(context.getSharedPreferences("notification-permission", 0).edit()
            .putBoolean("social-offered:${reader.publicKey}", true).commit())
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { activity ->
                withFailureCapture({ captureNativeScreen(File(context.cacheDir, "avatar-evidence/$theme-journey-failure.png")) }) {
                    block(activity, peer, response, requests, author)
                    assertEquals(reader.exportJson(), wallets.loadExisting().exportJson())
                    assertEquals(SaitoBackend.OWNER, backend.load())
                    assertTrue(peer.transactions.isEmpty()); assertTrue(peer.reports.isEmpty()); assertTrue(peer.relayedMessages.isEmpty())
                    peer.assertHealthy()
                }
            }
        }
    } catch (error: Throwable) { failure = error; throw error }
    finally {
        cleanupAfterTest(failure, { state.restoreFiles() }, { state.restorePreferences() },
            { wallets.replace(wallets.loadExisting(), prior); check(wallets.loadExisting().exportJson() == prior.exportJson()) },
            { backend.save(priorBackend); check(backend.load() == priorBackend) },
            { check(appearance.edit().apply { if (priorAppearance == null) remove("mode") else putString("mode", priorAppearance) }.commit()) })
    }
}
