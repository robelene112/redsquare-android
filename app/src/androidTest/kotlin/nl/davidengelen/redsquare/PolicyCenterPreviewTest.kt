package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
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
                    waitFor("New post", action = true)
                    val originalWallet = AndroidWalletStore(context).loadExisting().exportJson()
                    ui.onNodeWithText("New post").performClick()
                    ui.onNode(hasSetTextAction()).performTextReplacement("Unsent policy navigation draft")
                    ui.onNodeWithText("Save draft").performClick()
                    waitFor("New post", action = true)

                    fun center() {
                        waitFor("Settings", action = true)
                        ui.onNodeWithText("Settings").performClick()
                        ui.onNodeWithText("Notifications and privacy").assertIsDisplayed()
                        ui.onNodeWithText("Node").performClick()
                        ui.onNode(hasText(SaitoBackend.OWNER.domain) and SemanticsMatcher.expectValue(
                            androidx.compose.ui.semantics.SemanticsProperties.Role,
                            androidx.compose.ui.semantics.Role.RadioButton))
                            .assertIsSelected()
                        ui.onNode(hasText(SaitoBackend.OFFICIAL.domain) and SemanticsMatcher.expectValue(
                            androidx.compose.ui.semantics.SemanticsProperties.Role,
                            androidx.compose.ui.semantics.Role.RadioButton))
                            .assertIsNotSelected()
                        UiDevice.getInstance(instrumentation).pressBack()
                        ui.onNodeWithText("Privacy and safety").assertIsDisplayed()
                        ui.onNode(hasText("Privacy and safety") and hasClickAction()).performClick()
                        ui.onNodeWithText("Muted and blocked accounts").assertIsDisplayed()
                        ui.onNodeWithText("Policy Center").performClick()
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
                        waitFor("Settings", action = true)
                        ui.onNodeWithText("Settings").performClick()
                        ui.onNodeWithText("Appearance").performClick()
                        ui.onNodeWithText(theme).performScrollTo().performClick()
                        ui.onNodeWithText("Done").performClick()
                        if (theme == "Dark") {
                            peer.rejectNewConnections = true
                            peer.disconnectAuthenticatedSockets()
                        }
                        center()
                        capture("${theme.lowercase()}-overview.png")
                        val documents = linkedMapOf(
                            "Terms of use" to "Registered publisher details,",
                            "Privacy notice" to "The final privacy contact,",
                            "Community guidelines" to "Angels’ Foundry has accepted responsibility",
                            "Child safety" to "The responsible public contact,",
                            "Data and deletion" to "The request contact and web address,",
                            "Contact and support" to "Public support, privacy/deletion",
                        )
                        for ((title, lastParagraph) in documents) {
                            ui.onNodeWithText(title).performScrollTo().performClick()
                            waitFor("Draft policies")
                            ui.onNodeWithText(lastParagraph, substring = true).performScrollTo().assertIsDisplayed()
                            ui.onNodeWithText("Draft").assertIsDisplayed()
                            ui.onAllNodesWithText("Accept").assertCountEquals(0)
                            ui.onAllNodesWithText("Send request").assertCountEquals(0)
                            capture("${theme.lowercase()}-${title.lowercase().replace(' ', '-')}.png")
                            if (title == "Data and deletion") {
                                activity.recreate()
                                waitFor("Data and deletion")
                                ui.onNodeWithText(lastParagraph, substring = true).performScrollTo().assertIsDisplayed()
                                ui.onNodeWithText("Draft").assertIsDisplayed()
                                capture("${theme.lowercase()}-deletion-restored.png")
                            }
                            ui.onNodeWithContentDescription("Back to privacy and safety").performClick()
                            ui.onNodeWithText("Privacy and safety").assertIsDisplayed()
                            unchanged()
                        }
                        // Android Back closes the center without losing the underlying feed.
                        UiDevice.getInstance(instrumentation).pressBack()
                        waitFor("New post", action = true)
                        unchanged()
                    }
                    ui.onNodeWithText("New post").performClick()
                    ui.waitUntil(20_000) {
                        ui.onNode(hasSetTextAction() and hasText("Unsent policy navigation draft")).isDisplayed()
                    }
                    ui.onNode(hasSetTextAction()).assertTextEquals("Unsent policy navigation draft")
                    capture("saved-draft-restored.png")
                    ui.onNode(hasSetTextAction()).performTextReplacement("")
                    ui.onNodeWithText("Save draft").performClick()
                    unchanged()
                }
            }
            val talkBackFolder = File(context.cacheDir, "policy-evidence/settings-talkback").apply { mkdirs() }
            NativeTalkBackObservation(talkBackFolder).observe {
                withAppFrameFixture("settings-node-talkback", "policy-evidence/settings-talkback", "[]") {
                    check(device.wait(Until.hasObject(By.text("Later")), 15_000))
                    click(automation, "Later")
                    click(automation, "Settings")
                    check(device.wait(Until.hasObject(By.text("Node")), 10_000))
                    click(automation, "Node")

                    val ownerNode = actionNode(SaitoBackend.OWNER.domain, textLabel = true)
                    assertTrue("The current node remains selected", ownerNode.isSelected)
                    assertTrue("The complete node row accepts keyboard focus",
                        ownerNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS))
                    val inputFocusedNode = actionNode(SaitoBackend.OWNER.domain, textLabel = true)
                    assertTrue("Keyboard focus remains visible on the selected node row", inputFocusedNode.isFocused)
                    capture(automation, File(talkBackFolder, "node-keyboard-focus.png"))
                    focusAndHear("owner-node", SaitoBackend.OWNER.domain, talkBackFolder, textLabel = true)
                    waitFor(10_000) { completedFragments(device, SaitoBackend.OWNER.domain).isNotEmpty() }
                    assertTrue("TalkBack announces the selected radio state",
                        Regex("\\bselected\\b", RegexOption.IGNORE_CASE).containsMatchIn(speechLog(device)))
                    File(talkBackFolder, "settings-result.json").writeText(com.google.gson.JsonObject().apply {
                        addProperty("selectedNode", SaitoBackend.OWNER.domain)
                        addProperty("selected", ownerNode.isSelected)
                        addProperty("actualAccessibilityFocus", focusedText(automation) == SaitoBackend.OWNER.domain)
                        addProperty("completedNodeUtterances", completedFragments(device, SaitoBackend.OWNER.domain).size)
                        addProperty("physicalAudibility", false)
                        addProperty("serviceProcess", serviceProcess)
                    }.toString())
                    assertEquals("TalkBack focus remains on the selected node", SaitoBackend.OWNER.domain,
                        focusedText(automation))
                }
            }
        }
    }

    private fun waitFor(text: String, action: Boolean = false) = ui.waitUntil(20_000) {
        val matcher = if (action) hasText(text) and hasClickAction() and isEnabled() else hasText(text)
        ui.onNode(matcher).isDisplayed()
    }

    private fun capture(name: String) = captureNativeScreen(File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "policy-evidence/$name"))
}
