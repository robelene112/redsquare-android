package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Read-only policy navigation through the actual app, with an owned peer. */
class PolicyCenterPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun draftPoliciesAreReadableAndReopenWithoutPublishingOrLosingSavedWork() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        val root = instrumentation.context.assets.open("native-following-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("root")
        }
        val peer = ControlledFeedPeer("[$root]")
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { activity ->
                withFailureCapture({ capture("first-failure.png") }) {
                    waitFor("New post")
                    val originalWallet = AndroidWalletStore(context).loadExisting().exportJson()
                    ui.onNodeWithText("New post").performClick()
                    ui.onNode(hasSetTextAction()).performTextReplacement("Unsent policy navigation draft")
                    ui.onNodeWithText("Save draft").performClick()
                    waitFor("New post")

                    fun center() {
                        ui.onNodeWithText("Settings").performClick()
                        ui.onNodeWithText("Privacy and safety").performScrollTo().performClick()
                        waitFor("Draft policies")
                    }
                    fun unchanged() {
                        assertTrue("Policy navigation must preserve the full wallet",
                            originalWallet == AndroidWalletStore(context).loadExisting().exportJson())
                        assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
                        assertTrue(peer.transactions.isEmpty())
                        assertTrue(peer.reports.isEmpty())
                        peer.assertHealthy()
                    }
                    // Theme controls remain the app's real controls; never change system settings.
                    for (theme in listOf("Light", "Dark")) {
                        ui.onNodeWithText("Settings").performClick()
                        ui.onNodeWithText(theme).performScrollTo().performClick()
                        ui.onNodeWithText("Done").performClick()
                        center()
                        capture("${theme.lowercase()}-overview.png")
                        val documents = linkedMapOf(
                            "Terms of use" to "Release details pending",
                            "Privacy notice" to "Retention details pending",
                            "Community guidelines" to "Moderation process pending",
                            "Child safety" to "Safety contact pending",
                            "Data and deletion" to "Deletion process pending",
                            "Contact and support" to "Contact details pending",
                        )
                        for ((title, lastSection) in documents) {
                            ui.onNodeWithText(title).performScrollTo().performClick()
                            waitFor("Draft policies")
                            ui.onNodeWithText(lastSection).performScrollTo().assertIsDisplayed()
                            ui.onAllNodesWithText("Accept").assertCountEquals(0)
                            ui.onAllNodesWithText("Send request").assertCountEquals(0)
                            capture("${theme.lowercase()}-${title.lowercase().replace(' ', '-')}.png")
                            if (title == "Data and deletion") {
                                activity.recreate()
                                waitFor("Data and deletion")
                                ui.onNodeWithText(lastSection).performScrollTo().assertIsDisplayed()
                                capture("${theme.lowercase()}-deletion-restored.png")
                            }
                            ui.onNodeWithContentDescription("Back to privacy and safety").performClick()
                            ui.onNodeWithText("Privacy and safety").assertIsDisplayed()
                            unchanged()
                        }
                        // Android Back closes the center without losing the underlying feed.
                        UiDevice.getInstance(instrumentation).pressBack()
                        waitFor("New post")
                        unchanged()
                    }
                    ui.onNodeWithText("New post").performClick()
                    ui.onNode(hasSetTextAction()).assertTextEquals("Unsent policy navigation draft")
                    ui.onNode(hasSetTextAction()).performTextReplacement("")
                    ui.onNodeWithText("Save draft").performClick()
                    unchanged()
                }
            }
        }
    }

    private fun waitFor(text: String) = ui.waitUntil(20_000) {
        ui.onNodeWithText(text).isDisplayed()
    }

    private fun capture(name: String) = captureNativeScreen(File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "policy-evidence/$name"))
}
