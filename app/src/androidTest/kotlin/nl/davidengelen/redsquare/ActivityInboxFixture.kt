package nl.davidengelen.redsquare

import android.content.Intent
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import java.io.File
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet
import org.junit.Assert.*

/** Existing independently signed activity fixtures, isolated from every release identity. */
internal fun withActivityInbox(name: String, eventNames: List<String> = listOf("incomingLike"),
    block: (ActivityScenario<MainActivity>, ControlledFeedPeer) -> Unit) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    check(context.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
    val wallets = AndroidWalletStore(context)
    val prior = wallets.loadOrCreate()
    val priorJson = prior.exportJson()
    val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Published unsafe fixture only.
    val backends = AndroidBackendStore(context)
    val priorBackend = backends.load()
    val retained = ChatFixtureState(context, listOf("feed.", "conversation.", "profile.", "profilemeta.",
        "namemap.", "notifications.", "notificationread.", "following.", "personal."), wallet.publicKey)
    val reference = instrumentation.context.assets.open("native-notification-reference.json").reader().use {
        JsonParser.parseReader(it).asJsonObject
    }
    val root = reference.get("root")
    val events = eventNames.map { reference.getAsJsonObject(it) }
    fun rows(kind: String) = events.filter { it.get("field1").asString == kind }.joinToString(",", "[", "]")
    val peer = ControlledFeedPeer("[$root]", quoteArchiveResponse = { criteria ->
        if (criteria.has("field3")) rows("RedSquareRetweet") else "[]"
    }, archiveResponse = { criteria ->
        when (criteria.get("field1").asString) {
            "RedSquareLike" -> if (criteria.has("field3")) rows("RedSquareLike") else "[]"
            "RedSquare" -> if (criteria.get("field3")?.asString == wallet.publicKey || !criteria.get("field4")?.asString.isNullOrEmpty()) rows("RedSquare") else "[$root]"
            else -> error("Unexpected activity fixture read")
        }
    })
    var primary: Throwable? = null
    try {
        retained.prepare()
        wallets.replace(prior, wallet)
        backends.save(SaitoBackend.OWNER)
        check(context.getSharedPreferences("notification-permission", 0).edit()
            .putBoolean("social-offered:${wallet.publicKey}", true).commit())
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { activity ->
                withFailureCapture({ captureNativeScreen(File(context.cacheDir, "activity-inbox-evidence/$name-failure.png")) }) {
                    block(activity, peer)
                    assertEquals(wallet.exportJson(), wallets.loadExisting().exportJson())
                    assertEquals(SaitoBackend.OWNER, backends.load())
                    assertTrue(peer.transactions.isEmpty()); assertTrue(peer.reports.isEmpty())
                    peer.assertHealthy()
                }
            }
        }
    } catch (failure: Throwable) { primary = failure; throw failure }
    finally {
        cleanupAfterTest(primary, { retained.restoreFiles() }, { retained.restorePreferences() }, {
            val active = wallets.loadExisting()
            if (active.exportJson() != priorJson) wallets.replace(active, prior)
            check(wallets.loadExisting().exportJson() == priorJson)
        }, { backends.save(priorBackend); check(backends.load() == priorBackend) })
    }
}
