package nl.davidengelen.redsquare

import android.graphics.Rect
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
                            ui.onNodeWithText("Policy Center").assertIsDisplayed()
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
                    if (ui.onAllNodes(hasText("Back") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()) {
                        ui.onNode(hasText("Back") and hasClickAction()).performClick()
                    }
                    waitFor("Settings", action = true)
                    unchanged()
                    val talkBackFolder = File(context.cacheDir, "policy-evidence/settings-talkback").apply { mkdirs() }
                    NativeTalkBackObservation(talkBackFolder).observe {
                        device.waitForIdle(5_000)
                        waitFor("Settings", action = true)
                        ui.onNodeWithText("Settings").performClick()
                        waitFor("Node")
                        ui.onNodeWithText("Node").performClick()
                        waitFor("Each node has its own available history and moderation. Your wallet stays the same.")

                        fun collectRadioRows(node: android.view.accessibility.AccessibilityNodeInfo,
                            rows: MutableList<android.view.accessibility.AccessibilityNodeInfo>) {
                            if (node.isVisibleToUser && node.isFocusable && node.isCheckable) rows += node
                            for (index in 0 until node.childCount) node.getChild(index)?.let { collectRadioRows(it, rows) }
                        }
                        val radioRows = appRoots(automation).flatMap { root ->
                            mutableListOf<android.view.accessibility.AccessibilityNodeInfo>().also { collectRadioRows(root, it) }
                        }
                        assertEquals("The node settings expose exactly two focusable radio choices", 2, radioRows.size)
                        val ownerLabel = node(automation, SaitoBackend.OWNER.domain)
                        val ownerBounds = Rect().also(ownerLabel::getBoundsInScreen)
                        val ownerRow = radioRows.singleOrNull { row ->
                            val bounds = Rect().also(row::getBoundsInScreen)
                            bounds.contains(ownerBounds.centerX(), ownerBounds.centerY())
                        }
                        assertNotNull("The owner node label belongs to one focusable radio row", ownerRow)
                        val selectedOwnerRow = requireNotNull(ownerRow)
                        assertTrue("The current node remains selected", selectedOwnerRow.isChecked)
                        assertTrue("The complete node row accepts keyboard focus",
                            selectedOwnerRow.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS))
                        assertTrue("Keyboard focus remains visible on the selected node row", selectedOwnerRow.isFocused)
                        capture(automation, File(talkBackFolder, "node-keyboard-focus.png"))
                        selectedOwnerRow.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
                        device.executeShellCommand("logcat -c")
                        assertTrue("TalkBack can focus the selected node radio",
                            selectedOwnerRow.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
                        captureFocusedSpeech("owner-node", SaitoBackend.OWNER.domain, talkBackFolder, selectedOwnerRow)
                        waitFor(10_000) { completedFragments(device, SaitoBackend.OWNER.domain).isNotEmpty() }
                        assertTrue("TalkBack announces the selected radio state",
                            Regex("\\bselected\\b", RegexOption.IGNORE_CASE).containsMatchIn(speechLog(device)))
                        File(talkBackFolder, "settings-result.json").writeText(com.google.gson.JsonObject().apply {
                            addProperty("selectedNode", SaitoBackend.OWNER.domain)
                            addProperty("selected", selectedOwnerRow.isChecked)
                            addProperty("actualAccessibilityFocus", appRoots(automation).any {
                                it.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_ACCESSIBILITY) == selectedOwnerRow
                            })
                            addProperty("completedNodeUtterances", completedFragments(device, SaitoBackend.OWNER.domain).size)
                            addProperty("physicalAudibility", false)
                            addProperty("serviceProcess", serviceProcess)
                        }.toString())
                        assertTrue("TalkBack focus remains on the selected radio row",
                            appRoots(automation).any { it.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_ACCESSIBILITY) == selectedOwnerRow })
                        ui.onNodeWithText("Done").performClick()
                        unchanged()
                    }
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
