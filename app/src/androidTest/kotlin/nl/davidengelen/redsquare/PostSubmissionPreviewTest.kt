package nl.davidengelen.redsquare

import android.content.Intent
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import java.io.File
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PostSubmissionPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun submittedPostReturnsToReadingAndOpensAFreshDraftWhileArchiveIsDelayed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallets = AndroidWalletStore(context)
        val wallet = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context)
        val originalBackend = backends.load()
        val retained = ChatFixtureState(context, listOf("postdraft.", "feed."), wallet.publicKey)
        val root = InstrumentationRegistry.getInstrumentation().context.assets
            .open("native-following-reference.json").reader().use { JsonParser.parseReader(it).asJsonObject.get("root") }
        val peer = ControlledFeedPeer("[$root]", archiveResponse = { query ->
            if (query.has("sig")) "[]" else "[$root]"
        })
        var primary: Throwable? = null
        try {
            retained.prepare()
            backends.save(SaitoBackend.OWNER)
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { activity ->
                    withFailureCapture({ captureNativeScreen(File(context.cacheDir, "post-submission-evidence/delayed-failure.png")) }) {
                        ui.waitUntil(15_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
                        ui.onNode(hasText("New post") and hasClickAction()).performClick()
                        ui.waitUntil(10_000) { ui.onNode(hasSetTextAction()).isDisplayed() }
                        ui.onNode(hasSetTextAction()).performTextReplacement("Submitted once while the archive catches up")
                        ui.onNodeWithText("Send").performClick()
                        ui.waitUntil(10_000) { peer.transactions.size == 1 }
                        ui.waitUntil(15_000) { ui.onNodeWithText("Post submitted").isDisplayed() }
                        ui.onNodeWithText("Retry").assertDoesNotExist()
                        ui.onNode(hasSetTextAction()).assertDoesNotExist()
                        captureNativeScreen(File(context.cacheDir, "post-submission-evidence/submitted-reading.png"))
                        assertEquals(1, peer.transactions.size)
                        ui.onNode(hasText("New post") and hasClickAction()).performClick()
                        ui.waitUntil(10_000) { ui.onNode(hasSetTextAction() and isEnabled()).isDisplayed() }
                        ui.onNode(hasSetTextAction()).assertEditableTextEquals("")
                        activity.recreate()
                        ui.waitUntil(15_000) { ui.onNode(hasSetTextAction() and isEnabled()).isDisplayed() }
                        ui.onNode(hasSetTextAction()).assertEditableTextEquals("")
                        assertEquals(1, peer.transactions.size)
                        assertEquals(wallet.exportJson(), wallets.loadExisting().exportJson())
                        peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, {
                peer.transactions.firstOrNull()?.let { frame ->
                    androidx.work.WorkManager.getInstance(context)
                        .cancelUniqueWork("post:${postingTransaction(frame).id}").result.get()
                }
            }, { retained.restoreFiles() }, { retained.restorePreferences() }, {
                backends.save(originalBackend)
                check(wallets.loadExisting().exportJson() == wallet.exportJson())
            })
        }
    }
}
