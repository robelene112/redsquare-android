package nl.davidengelen.redsquare

import android.app.NotificationManager
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Process
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class SpacesCatchUpJourneyTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrumentation)
    private val context get() = instrumentation.targetContext.also {
        check(it.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in setOf("ranchu", "goldfish"))
    }
    private val evidence get() = File(context.cacheDir, "spaces-catch-up-evidence").apply { mkdirs() }

    @Test fun searchAndOpenRealSystemNotification() {
        val config = request("config")
        prepareWallet(config)
        launch(config.get("link").asString).use {
            withFailureCapture({ capture("search-and-activity-failure") }) {
                waitText("Separate proof conversation")
                click("Community information")
                click("Search this Space")
                val field = ui.onNode(hasSetTextAction() and hasText("Search saved history"))
                ui.waitUntil(12_000) { field.isDisplayed() }
                field.performTouchInput { click() }
                field.performTextReplacement("Needle")
                field.assertTextContains("Needle")
                Espresso.closeSoftKeyboard()
                click("All available channels")
                ui.onNodeWithText("Restricted proof channel").assertDoesNotExist()
                click("General", popup = true)
                click("All content types")
                click("Messages", popup = true)
                ui.onNodeWithText("Needle that must stay private").assertDoesNotExist()
                val results = ui.onNodeWithTag("spaces-search-list")
                ui.waitUntil(18_000) {
                    runCatching { results.performScrollToNode(hasText("First proof Needle")).isDisplayed() }.getOrDefault(false)
                }
                capture("search-filtered")
                click("First proof Needle")
                ui.waitUntil(25_000) {
                    ui.onNode(hasTestTag("spaces-message") and hasAnyDescendant(hasText("First proof Needle"))).isDisplayed()
                }
                click("Back to search")
                ui.waitUntil(25_000) { results.isDisplayed() }
                results.performScrollToIndex(0)
                waitText("Search saved history")
                field.assertTextContains("Needle")
                click("Activity")
                assertEquals(2, request("initial").get("delivered").asInt)
                val replies = hasText("replied to your message", substring = true) and hasClickAction()
                ui.waitUntil(25_000) { ui.onAllNodes(replies).fetchSemanticsNodes().size == 2 }
                val notifications = context.getSystemService(NotificationManager::class.java)
                ui.waitUntil(15_000) { notifications.activeNotifications.count { it.tag?.startsWith("spaces:") == true } == 2 }
                notifications.activeNotifications.filter { it.tag?.startsWith("spaces:") == true }.forEach {
                    notifications.cancel(it.tag, it.id)
                }
                val observed = request("latest")
                val grouped = hasText("and 1 more replied to your message", substring = true) and hasClickAction()
                ui.waitUntil(25_000) {
                    ui.onNode(grouped).isDisplayed() && ui.onAllNodes(replies).fetchSemanticsNodes().size == 2
                }
                val recent = ui.onNode(grouped).fetchSemanticsNode().boundsInRoot.top
                val older = ui.onNode(replies and !grouped).fetchSemanticsNode().boundsInRoot.top
                assertTrue("New activity moves the same group above the other conversation", recent < older)
                capture("activity-bubbled")
                ui.waitUntil(15_000) { notifications.activeNotifications.count { it.tag?.startsWith("spaces:") == true } == 1 }
                device.openNotification()
                val notification = device.wait(Until.findObject(By.text("You have new Space activity.")), 12_000)
                assertNotNull("Actually posted Android notification must appear in the shade", notification)
                capture("notification-shade")
                notification.click()
                waitForWholeMessage("Latest exact proof reply")
                waitText("First proof Needle")
                ui.onNodeWithText("Unrelated proof reply").assertDoesNotExist()
                capture("notification-exact-reply")
                click("Back to Activity")
                ui.waitUntil(15_000) { ui.onNode(grouped).isDisplayed() }
                assertNotEquals("Unread", ui.onNode(grouped).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
                save("notification-result", observed)
            }
        }
    }

    @Test fun coldExactLinkAndRestrictedLink() {
        val config = request("config")
        val prior = JsonParser.parseString(File(evidence, "notification-result.json").readText()).asJsonObject
        val observed = request("observe")
        assertNotEquals(prior.get("pid").asInt, Process.myPid())
        assertEquals(prior.get("reply").asString, observed.get("reply").asString)
        assertEquals(config.get("nativeWallet").asString, AndroidWalletStore(context).loadExisting().publicKey)
        launch(observed.get("link").asString).use {
            withFailureCapture({ capture("cold-exact-failure") }) {
                waitForWholeMessage("Latest exact proof reply")
                waitText("First proof Needle")
                ui.onNodeWithText("Unrelated proof reply").assertDoesNotExist()
                capture("cold-exact-reply")
            }
        }
        launch(config.get("privateLink").asString).use {
            withFailureCapture({ capture("restricted-link-failure") }) {
                waitText("Access to this community is unavailable. Your saved writing is preserved.")
                ui.onNodeWithText("Needle that must stay private").assertDoesNotExist()
                ui.onNodeWithText("Restricted proof channel").assertDoesNotExist()
                capture("restricted-link")
            }
        }
        val otherWallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "5").publicKey
        for ((owner, node) in listOf(otherWallet to SaitoBackend.OWNER, config.get("nativeWallet").asString to SaitoBackend.OFFICIAL)) {
            launch(observed.get("link").asString, owner, node).use {
                withFailureCapture({ capture("wrong-scope-failure") }) {
                    waitText("All posts")
                    ui.onNodeWithText("Latest exact proof reply").assertDoesNotExist()
                }
            }
        }
        save("cold-result", observed)
    }

    @Test fun talkBackReadsTheOptionBControlUnreadReplyAndComposer() {
        val config = request("config")
        prepareWallet(config)
        val observations = evidence.resolve("talkback").apply { mkdir() }
        NativeTalkBackObservation(observations).observe {
            launch(config.get("link").asString).use {
                waitText("Separate proof conversation")
                val switcher = "Switch Space or channel"
                assertTarget(hasContentDescription(switcher) and hasClickAction())
                focusAndHear("option-b", switcher, observations)
                assertTrue(actionNode(switcher).performAction(AccessibilityNodeInfo.ACTION_CLICK))
                waitText("Your communities")
                device.pressBack()
                ui.waitUntil(12_000) { !ui.onNodeWithText("Your communities").isDisplayed() }

                click("Activity")
                assertEquals(2, request("initial").get("delivered").asInt)
                request("latest")
                val group = hasText("and 1 more replied to your message", substring = true) and hasClickAction()
                ui.waitUntil(25_000) { ui.onNode(group).isDisplayed() }
                assertTarget(group)
                val summary = "and 1 more replied to your message"
                fun findGroup(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
                    if (node.isVisibleToUser && node.text?.contains(summary) == true) {
                        return generateSequence(node) { it.parent }.firstOrNull { it.isVisibleToUser && it.isClickable }
                    }
                    return (0 until node.childCount).firstNotNullOfOrNull { node.getChild(it)?.let(::findGroup) }
                }
                val target = requireNotNull(appRoots(automation).firstNotNullOfOrNull(::findGroup))
                target.performAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
                device.executeShellCommand("logcat -c")
                assertTrue(target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
                captureFocusedSpeech("unread-reply-group", summary, observations, target)
                waitFor(10_000) { completedFragments(device, "Unread").isNotEmpty() }
                observations.resolve("unread-state-speech.txt").writeText(speechLog(device))
                assertTrue(target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                waitForWholeMessage("Latest exact proof reply")
                waitText("First proof Needle")
                val field = "Reply in this thread"
                assertTarget(hasText(field) and hasSetTextAction())
                focusAndHear("thread-composer", field, observations, textLabel = true)
            }
        }
    }

    @Test fun captureOptionBAndTheComposerInAnActualViewport() {
        val viewportId = stringArgument("spacesVisualId")
        require(viewportId.matches(Regex("V(0[1-9]|1[0-4])")))
        val expectedFont = stringArgument("spacesVisualFont").toFloat()
        val expectedLandscape = stringArgument("spacesVisualOrientation") == "landscape"
        val expectedDark = stringArgument("spacesVisualTheme") == "dark"
        val useKeyboard = stringArgument("spacesVisualIme") == "open"
        val observations = evidence.resolve(viewportId).apply { mkdir() }
        val config = request("config")
        prepareWallet(config)
        launch(config.get("link").asString).use { activity ->
            withFailureCapture({ captureNativeScreen(observations.resolve("failure.png")) }) {
                val checked = JsonObject()
                activity.onActivity { native ->
                    val actual = native.resources.configuration
                    assertEquals(expectedFont, actual.fontScale, 0.01f)
                    assertEquals(320, actual.densityDpi)
                    assertEquals(
                        if (expectedLandscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT,
                        actual.orientation,
                    )
                    assertEquals(expectedDark, actual.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
                    checked.addProperty("widthDp", actual.screenWidthDp)
                    checked.addProperty("heightDp", actual.screenHeightDp)
                    checked.addProperty("fontScale", actual.fontScale)
                    checked.addProperty("landscape", expectedLandscape)
                    checked.addProperty("night", expectedDark)
                }
                fun observeInsets(expectedIme: Boolean, stage: String) {
                    ui.waitUntil(10_000) {
                        var matches = false
                        activity.onActivity { native ->
                            val insets = ViewCompat.getRootWindowInsets(native.window.decorView) ?: return@onActivity
                            matches = insets.isVisible(WindowInsetsCompat.Type.ime()) == expectedIme
                            if (matches) for ((label, type) in listOf("ime" to WindowInsetsCompat.Type.ime(),
                                "navigation" to WindowInsetsCompat.Type.navigationBars(), "status" to WindowInsetsCompat.Type.statusBars())) {
                                val bounds = insets.getInsets(type)
                                checked.add("$stage-$label", JsonObject().apply {
                                    addProperty("left", bounds.left); addProperty("top", bounds.top)
                                    addProperty("right", bounds.right); addProperty("bottom", bounds.bottom)
                                })
                            }
                        }
                        matches
                    }
                }
                observeInsets(false, "initial")
                val switcher = hasContentDescription("Switch Space or channel") and hasClickAction()
                val button = ui.onNode(switcher)
                ui.waitUntil(25_000) {
                    button.isDisplayed() && button.fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription) == "General"
                }
                assertTarget(switcher)
                assertTarget(hasText("Back to your communities") and hasClickAction())
                assertTarget(hasText("Community information") and hasClickAction())
                val stableBounds = button.getUnclippedBoundsInRoot()
                captureNativeScreen(observations.resolve("channel.png"))
                button.performTouchInput { click() }
                waitText("Your communities")
                captureNativeScreen(observations.resolve("drawer.png"))
                device.pressBack()
                ui.waitUntil(12_000) {
                    !ui.onNodeWithText("Your communities").isDisplayed() && button.isDisplayed()
                }
                assertEquals(stableBounds, button.getUnclippedBoundsInRoot())
                val composer = ui.onNode(hasSetTextAction())
                composer.assertIsDisplayed()
                if (useKeyboard) {
                    composer.performTouchInput { click() }
                    observeInsets(true, "keyboard")
                    composer.performTextInput("Viewport draft")
                    composer.assertIsDisplayed().assertTextContains("Viewport draft")
                    assertTarget(hasSetTextAction())
                    captureNativeScreen(observations.resolve("composer-ime.png"))
                    composer.performTextClearance()
                    Espresso.closeSoftKeyboard()
                    observeInsets(false, "dismissed")
                    ui.waitUntil(12_000) { button.isDisplayed() }
                    assertEquals(stableBounds, button.getUnclippedBoundsInRoot())
                }
                observations.resolve("native-configuration.json").writeText(checked.toString())
            }
        }
    }

    private fun assertTarget(control: SemanticsMatcher) {
        val bounds = ui.onNode(control).getUnclippedBoundsInRoot()
        assertTrue("Touch target must be at least 48dp in both directions: $bounds",
            bounds.right - bounds.left >= 48.dp && bounds.bottom - bounds.top >= 48.dp)
    }

    private fun prepareWallet(config: JsonObject) {
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
        assertEquals(config.get("nativeWallet").asString, wallet.publicKey)
        val store = AndroidWalletStore(context)
        val previous = store.loadOrCreate()
        if (previous.publicKey != wallet.publicKey) store.replace(previous, wallet)
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
    }

    private fun save(name: String, observed: JsonObject) {
        File(evidence, "$name.json").writeText(JsonObject().apply {
            addProperty("pid", Process.myPid())
            addProperty("reply", observed.get("reply").asString)
            addProperty("root", observed.get("root").asString)
            addProperty("privateDenied", observed.get("privateDenied").asBoolean)
        }.toString())
    }

    private fun launch(link: String, owner: String? = null, backend: SaitoBackend? = null): ActivityScenario<MainActivity> {
        val peer = "ws://127.0.0.1:${argument("spacesPeerPort")}/wsopen"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(context.packageName)
            .putExtra("feedPeerUrl", peer).putExtra("feedOfficialPeerUrl", peer)
            .putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/")
        if (owner != null && backend != null) {
            intent.putExtra("spacesNotificationOwner", owner)
            intent.putExtra("spacesNotificationBackend", backend.id)
        }
        return ActivityScenario.launch(intent)
    }

    private fun click(label: String, popup: Boolean = false) {
        val enabled = hasText(label) and hasClickAction() and isEnabled()
        val target = ui.onNode(if (popup) enabled and hasAnyAncestor(isPopup()) else enabled)
        ui.waitUntil(12_000) {
            target.isDisplayed() || runCatching { target.performScrollTo().isDisplayed() }.getOrDefault(false)
        }
        target.performClick()
    }

    private fun waitForWholeMessage(label: String) {
        val messageText = hasText(label)
        val body = ui.onNode(messageText)
        val list = ui.onNode(hasScrollToIndexAction() and hasAnyDescendant(messageText))
        ui.waitUntil(25_000) {
            if (!body.isDisplayed() || !list.isDisplayed()) false else {
                val message = body.getUnclippedBoundsInRoot()
                val viewport = list.getUnclippedBoundsInRoot()
                message.top >= viewport.top - 1.dp && message.bottom <= viewport.bottom + 1.dp
            }
        }
    }

    private fun waitText(label: String) = ui.waitUntil(25_000) {
        val node = ui.onNodeWithText(label)
        node.isDisplayed() || runCatching { node.performScrollTo().isDisplayed() }.getOrDefault(false)
    }

    private fun request(path: String): JsonObject {
        val connection = URL("http://127.0.0.1:${argument("spacesControlPort")}/catch-up-$path")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 5_000
            connection.readTimeout = 8_000
            connection.outputStream.use { it.write("{}".toByteArray()) }
            check(connection.responseCode == 200) { "Owned catch-up fixture $path returned HTTP ${connection.responseCode}" }
            return connection.inputStream.use { JsonParser.parseReader(it.reader()).asJsonObject }
        } finally { connection.disconnect() }
    }

    private fun stringArgument(name: String) = requireNotNull(InstrumentationRegistry.getArguments().getString(name))

    private fun argument(name: String) = stringArgument(name).toInt()
        .also { require(it in 1024..65535) }

    private fun capture(name: String) = captureNativeScreen(File(evidence, "$name.png"))
}
