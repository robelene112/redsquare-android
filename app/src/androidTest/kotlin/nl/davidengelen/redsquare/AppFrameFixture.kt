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
internal fun withAppFrameFixture(name: String, evidenceFolder: String = "app-frame-evidence", feedRows: String? = null, block: (ActivityScenario<MainActivity>) -> Unit) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    check(context.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
    val wallets = AndroidWalletStore(context)
    val wallet = wallets.loadOrCreate()
    val backends = AndroidBackendStore(context)
    val originalBackend = backends.load()
    val retained = ChatFixtureState(context, listOf("feed.", "profile.", "profilemeta.", "profiledraft."), wallet.publicKey)
    val reminder = context.getSharedPreferences("wallet-backup-reminders", 0)
    val rows = feedRows ?: JsonParser.parseReader(instrumentation.context.assets.open("native-feed-reference.json").reader())
        .asJsonObject.get("archiveRow").let { "[$it]" }
    val peer = ControlledFeedPeer(rows, archiveResponse = { criteria ->
        when (criteria.get("field1").asString) {
            "RedSquare" -> rows
            "RedSquareLike" -> "[]"
            else -> error("Unexpected frame browsing request")
        }
    })
    var primary: Throwable? = null
    try {
        retained.prepare()
        check(reminder.edit().putBoolean("hidden:${wallet.publicKey}", false).commit())
        backends.save(SaitoBackend.OWNER)
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { activity ->
                withFailureCapture({ captureNativeScreen(File(context.cacheDir, "$evidenceFolder/$name-failure.png")) }) {
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

