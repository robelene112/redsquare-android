package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** User-facing publication gates use the real app and an owned signed-transaction peer. */
class PublishingSurfacesPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun prepare() {
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        context.noBackupFilesDir.listFiles()?.filter {
            it.name.startsWith("social.") || it.name.startsWith("feed.") ||
                it.name.startsWith("conversation.") || it.name.startsWith("profile.")
        }?.forEach { check(it.delete()) }
    }

    @Test fun likingRequiresAgreementAndCancellingDoesNotSaveOrTransmitTheAction() {
        val fixture = File(context.noBackupFilesDir, "publishing-policy-fixture.json")
        val previous = fixture.takeIf { it.exists() }?.readBytes()
        val revision = "owned-like-${java.util.UUID.randomUUID()}"
        fixture.writeText("""{"revision":"$revision","ready":true,"enforce":true,
            "terms":"Controlled social terms. No real publisher approval.",
            "community":"Controlled social rules. Publishing requires your choice."}""")
        try {
            val root = InstrumentationRegistry.getInstrumentation().context.assets.open("native-feed-reference.json")
                .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow") }
            val liked = AtomicReference<JsonObject?>()
            val peer = ControlledFeedPeer("[]", archiveResponse = { criteria ->
                if (criteria.get("field1")?.asString == "RedSquareLike") liked.get()?.let { "[$it]" } ?: "[]"
                else "[$root]"
            }, onTransaction = { frame ->
                val transaction = postingTransaction(frame)
                val action = SaitoEnvelope.decode(transaction.envelope())
                assertEquals("RedSquare", action.body.get("module").asString)
                assertEquals("like tweet", action.body.get("request").asString)
                assertEquals(root.get("sig").asString, action.body.getAsJsonObject("data").get("signature").asString)
                liked.set(JsonObject().apply {
                    addProperty("sig", action.id); addProperty("field1", "RedSquareLike")
                    addProperty("field2", action.author); addProperty("field4", root.get("sig").asString)
                    addProperty("created_at", action.createdAt); addProperty("tx", transaction.envelope())
                })
            })
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                    withFailureCapture({ capture("like-first-failure.png") }) {
                        waitFor("Native Red Square reference post")
                        val originalWallet = AndroidWalletStore(context).loadExisting().exportJson()
                        ui.clickVisiblePostContent("Like")
                        waitFor("Review publishing terms")
                        assertTrue(peer.transactions.isEmpty())
                        ui.onNodeWithText("Cancel").performClick()
                        ui.clickVisiblePostContent("Like")
                        waitFor("Review publishing terms")
                        assertTrue("Cancellation must not publish or turn Like into an uncertain retry", peer.transactions.isEmpty())
                        capture("like-cancelled-and-reviewed-again.png")
                        ui.onNodeWithText("Accept").performClick()
                        waitFor("Agreement saved. Choose your action again when you are ready.")
                        assertTrue("Agreement is separate from Like", peer.transactions.isEmpty())
                        ui.clickVisiblePostContent("Like")
                        waitFor("Liked")
                        assertEquals(1, peer.transactions.size)
                        assertTrue(originalWallet == AndroidWalletStore(context).loadExisting().exportJson())
                        assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
                        capture("like-deliberately-published-after-agreement.png")
                        peer.assertHealthy()
                    }
                }
            }
        } finally {
            if (previous == null) check(!fixture.exists() || fixture.delete()) else fixture.writeBytes(previous)
        }
    }

    private fun waitFor(text: String) = ui.waitUntil(20_000) { ui.onNodeWithText(text).isDisplayed() }
    private fun capture(name: String) = captureNativeScreen(File(context.cacheDir, "agreement-surfaces-evidence/$name"))
}
