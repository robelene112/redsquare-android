package nl.davidengelen.redsquare

import android.graphics.Rect
import android.content.Intent
import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
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
        val device = UiDevice.getInstance(instrumentation)
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
                        ui.waitForAccountMenu(20_000)
                        ui.openSettings()
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
                        ui.waitForAccountMenu(20_000)
                        ui.openSettings()
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
                    ui.waitForAccountMenu(20_000)
                    unchanged()
                    val talkBackFolder = File(context.cacheDir, "policy-evidence/settings-talkback").apply { mkdirs() }
                    ui.openSettings()
                    waitFor("Node")
                    ui.onNodeWithText("Node").performClick()
                    waitFor("Each node has its own available history and moderation. Your wallet stays the same.")
                    val ownerChoice = hasText(SaitoBackend.OWNER.domain) and SemanticsMatcher.expectValue(
                        SemanticsProperties.Role, Role.RadioButton)
                    check(device.pressKeyCode(KeyEvent.KEYCODE_TAB))
                    ui.onNode(ownerChoice).performSemanticsAction(SemanticsActions.RequestFocus) { check(it()) }
                    ui.onNode(ownerChoice).assertIsFocused().assertIsDisplayed()
                    captureNativeScreen(File(talkBackFolder, "node-keyboard-focus.png"))
                    // End the keyboard journey through its actual foreground window.
                    // The underlying Account control can remain in the semantics tree
                    // while this full-screen dialog is still open.
                    ui.onNodeWithText("Done").assertIsDisplayed().assertIsEnabled().performClick()
                    ui.onNode(hasText("Back to Settings") and hasAnyAncestor(isDialog())).assertDoesNotExist()
                    ui.onNode(hasContentDescription("Account") and hasClickAction())
                        .assertIsDisplayed().assertIsEnabled()
                    // [DEBUG-94-policy-focus] Bounded stages, no native calls added to the timed path.
                    val focusStages = com.google.gson.JsonArray()
                    var focusStarted = 0L
                    fun markFocusStage(stage: String) {
                        if (focusStarted != 0L) focusStages.add(com.google.gson.JsonObject().apply {
                            addProperty("stage", stage)
                            addProperty("elapsedMillis", android.os.SystemClock.elapsedRealtime() - focusStarted)
                        })
                    }
                    try {
                        NativeTalkBackObservation(talkBackFolder).observe {
                            device.waitForIdle(5_000)
                            ui.waitForAccountMenu(20_000)
                            // Retain only this window's initial TalkBack handoff.
                            device.executeShellCommand("logcat -c")
                            ui.openSettings()
                            waitFor("Node")
                            ui.onNodeWithText("Node").performClick()
                            waitFor("Each node has its own available history and moderation. Your wallet stays the same.")
    
                            val ownerFocusBy = android.os.SystemClock.elapsedRealtime() + 5_000
                            focusStarted = ownerFocusBy - 5_000
                            markFocusStage("budget-start")
                            fun ownerFocusRemaining(): Long = (ownerFocusBy - android.os.SystemClock.elapsedRealtime()).also {
                                markFocusStage("budget-check")
                                check(it > 0) { "Initial window handoff and owner-radio focus must complete within five seconds" }
                            }
                            // The service initializes the new window asynchronously.
                            // Observe its actual first focus and fresh completed speech
                            // before one radio-focus request, sharing the original5s.
                            waitFor(ownerFocusRemaining()) {
                                focusedText(automation) == "Back to Settings" &&
                                    completedFragments(device, "Back to Settings").isNotEmpty()
                            }
                            markFocusStage("initial-handoff-ready")
                            fun collectRadioRows(node: android.view.accessibility.AccessibilityNodeInfo,
                                rows: MutableList<android.view.accessibility.AccessibilityNodeInfo>) {
                                if (node.isVisibleToUser && node.isFocusable && node.isCheckable) rows += node
                                for (index in 0 until node.childCount) node.getChild(index)?.let { collectRadioRows(it, rows) }
                            }
                            // Resolve the choices in the actual foreground Settings window.
                            // Repeatedly traversing covered feed windows consumes the same5s focus budget.
                            val radioRows = mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
                            collectRadioRows(requireNotNull(automation.rootInActiveWindow), radioRows)
                            markFocusStage("radio-rows-collected")
                            assertEquals("The node settings expose exactly two focusable radio choices", 2, radioRows.size)
                            fun labelIn(row: android.view.accessibility.AccessibilityNodeInfo, domain: String): android.view.accessibility.AccessibilityNodeInfo? {
                                if (row.isVisibleToUser && row.text?.toString() == domain) return row
                                for (index in 0 until row.childCount) row.getChild(index)?.let { child ->
                                    labelIn(child, domain)?.let { return it }
                                }
                                return null
                            }
                            fun rowFor(domain: String) = radioRows.singleOrNull { row ->
                                val label = labelIn(row, domain) ?: return@singleOrNull false
                                val labelBounds = Rect().also(label::getBoundsInScreen)
                                val rowBounds = Rect().also(row::getBoundsInScreen)
                                rowBounds.contains(labelBounds.centerX(), labelBounds.centerY())
                            }
                            val selectedOwnerRow = requireNotNull(rowFor(SaitoBackend.OWNER.domain)) {
                                "The owner node label belongs to one focusable radio row"
                            }
                            val officialRow = requireNotNull(rowFor(SaitoBackend.OFFICIAL.domain)) {
                                "The official node label belongs to one focusable radio row"
                            }
                            markFocusStage("choices-resolved")
                            assertTrue("The current owner node remains selected", selectedOwnerRow.isChecked)
                            assertFalse("The other official node is not selected", officialRow.isChecked)
                            markFocusStage("before-log-clear")
                            device.executeShellCommand("logcat -c")
                            markFocusStage("after-log-clear")
                            assertTrue("TalkBack can focus the selected node radio",
                                selectedOwnerRow.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
                            markFocusStage("owner-focus-request-returned")
                            val ownerFocusRequestedAfter = android.os.SystemClock.elapsedRealtime() - (ownerFocusBy - 5_000)
                            captureFocusedSpeech("owner-node", SaitoBackend.OWNER.domain, talkBackFolder, selectedOwnerRow,
                                focusTimeoutMillis = ownerFocusRemaining())
                            waitFor(10_000) { completedFragments(device, SaitoBackend.OWNER.domain).isNotEmpty() }
                            assertTrue("TalkBack announces the selected radio state",
                                Regex("\\bselected\\b", RegexOption.IGNORE_CASE).containsMatchIn(speechLog(device)))
                            val ownerNodeUtterances = completedFragments(device, SaitoBackend.OWNER.domain).size
                            officialRow.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
                            device.executeShellCommand("logcat -c")
                            assertTrue("TalkBack can focus the official node radio",
                                officialRow.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
                            captureFocusedSpeech("official-node", SaitoBackend.OFFICIAL.domain, talkBackFolder, officialRow)
                            assertTrue("TalkBack identifies the official node as a radio choice",
                                Regex("\\bradio button\\b", RegexOption.IGNORE_CASE).containsMatchIn(speechLog(device)))
                            File(talkBackFolder, "settings-result.json").writeText(com.google.gson.JsonObject().apply {
                                addProperty("ownerFocusRequestedAfterMillis", ownerFocusRequestedAfter)
                                addProperty("ownerFocusBudgetMillis", 5_000)
                                addProperty("selectedNode", SaitoBackend.OWNER.domain)
                                addProperty("selected", selectedOwnerRow.isChecked)
                                addProperty("completedNodeUtterances", ownerNodeUtterances)
                                addProperty("officialNode", SaitoBackend.OFFICIAL.domain)
                                addProperty("officialNodeRadioUtterances", completedFragments(device, SaitoBackend.OFFICIAL.domain).size)
                                addProperty("physicalAudibility", false)
                                addProperty("serviceProcess", serviceProcess)
                            }.toString())
                            ui.onNodeWithText("Done").performClick()
                            unchanged()
                        }
                    } finally {
                        File(talkBackFolder, "owner-focus-stage-observations.json").writeText(com.google.gson.JsonObject().apply {
                            addProperty("probe", "DEBUG-94-policy-focus")
                            add("observations", focusStages)
                        }.toString())
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
