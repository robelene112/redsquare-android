package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Controlled policy input through the real app, persistent state and owned peer. */
class PublishingAgreementPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun requiredTermsCanBeReadAndCancelledWithoutPublishingOrLosingThePost() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val fixture = File(context.noBackupFilesDir, "publishing-policy-fixture.json")
        val previous = fixture.takeIf { it.exists() }?.readBytes()
        fixture.writeText("""{"revision":"owned-cancel-policy-one","ready":true,"enforce":true,
            "terms":"Controlled terms for native verification only. No real publisher approval.",
            "community":"Controlled community policy for native verification only. Preserve your unsent work."}""")
        try {
            AndroidBackendStore(context).save(SaitoBackend.OWNER)
            val root = instrumentation.context.assets.open("native-following-reference.json").bufferedReader().use {
                JsonParser.parseReader(it).asJsonObject.getAsJsonObject("root")
            }
            val peer = ControlledFeedPeer("[$root]")
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                    withFailureCapture({ capture("cancel-first-failure.png") }) {
                        waitFor("New post")
                        val originalWallet = AndroidWalletStore(context).loadExisting().exportJson()
                        ui.onNodeWithText("New post").performClick()
                        ui.onNode(hasSetTextAction()).performTextReplacement("Keep my unsent publishing agreement draft")
                        androidx.test.espresso.Espresso.closeSoftKeyboard()
                        repeat(2) { attempt ->
                            ui.onNodeWithText("Send").performClick()
                            waitFor("Review publishing terms")
                            ui.onNodeWithText("Controlled terms for native verification only.", substring = true)
                                .performScrollTo().assertIsDisplayed()
                            ui.onNodeWithText("Controlled community policy for native verification only.", substring = true)
                                .performScrollTo().assertIsDisplayed()
                            assertTrue("No publication before deliberate agreement", peer.transactions.isEmpty())
                            capture("cancel-$attempt-review.png")
                            ui.onNodeWithText("Cancel").performClick()
                            ui.onNode(hasSetTextAction()).assertTextEquals("Keep my unsent publishing agreement draft")
                            assertTrue("Cancelling must not publish", peer.transactions.isEmpty())
                            assertTrue("Cancelling must preserve the complete wallet",
                                originalWallet == AndroidWalletStore(context).loadExisting().exportJson())
                            assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
                        }
                        capture("cancel-keeps-draft.png")
                        ui.onNode(hasSetTextAction()).performTextReplacement("")
                        ui.onNodeWithText("Save draft").performClick()
                        peer.assertHealthy()
                    }
                }
            }
        } finally {
            if (previous == null) check(!fixture.exists() || fixture.delete()) else fixture.writeBytes(previous)
        }
    }

    private fun waitFor(text: String) = ui.waitUntil(20_000) { ui.onNodeWithText(text).isDisplayed() }
    private fun capture(name: String) = captureNativeScreen(File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "agreement-evidence/$name"))
}
