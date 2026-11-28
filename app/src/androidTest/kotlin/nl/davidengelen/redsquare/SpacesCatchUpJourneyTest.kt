package nl.davidengelen.redsquare

import android.app.NotificationManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Process
import android.view.KeyEvent
import android.view.Window
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.semantics.Role
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
import nl.davidengelen.redsquare.saito.SaitoPeer
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
        launch(config.get("link").asString).use { scenario ->
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
                request("hold-destination")
                click("First proof Needle")
                val pendingSearchRead = awaitHeldDestination()
                scenario.recreate()
                val retry = hasText(context.getString(R.string.spaces_retry)) and hasClickAction() and isEnabled()
                ui.waitUntil(10_000) { ui.onNode(retry).isDisplayed() }
                capture("search-recreated-retry")
                request("release-destination")
                ui.onNode(retry).performClick()
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
                observed.addProperty("heldSearchReadBeforeRecreation", pendingSearchRead)
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

                request("hold-destination")
                ui.onNode(grouped).performClick()
                observed.addProperty("heldActivityReadBeforeRecreation", awaitHeldDestination())
                scenario.recreate()
                ui.waitUntil(10_000) { ui.onNode(retry).isDisplayed() }
                capture("activity-recreated-retry")
                request("release-destination")
                ui.onNode(retry).performClick()
                waitForWholeMessage("Latest exact proof reply")
                click("Back to Activity")
                ui.waitUntil(15_000) { ui.onNode(grouped).isDisplayed() }

                click("My Spaces")
                val community = ui.onNode(hasContentDescription("Open Catch up proof Space") and
                    SemanticsMatcher("on-screen community") {
                        it.boundsInRoot.right > 0f && it.boundsInRoot.left >= 0f && it.boundsInRoot.bottom > 0f
                    })
                ui.waitUntil(15_000) { community.isDisplayed() }
                community.performClick()
                waitText("Separate proof conversation")
                val separateThread = ui.onNode(hasText("1 reply") and hasClickAction() and
                    hasAnyAncestor(hasTestTag("spaces-message") and hasAnyDescendant(hasText("Separate proof conversation"))))
                ui.waitUntil(15_000) {
                    separateThread.isDisplayed() || runCatching { separateThread.performScrollTo().isDisplayed() }.getOrDefault(false)
                }
                separateThread.performClick()
                waitForWholeMessage("Unrelated proof reply")
                val link = observed.get("link").asString
                request("hold-destination")
                openWarmLink(link)
                observed.addProperty("heldWarmReadBeforeRecreation", awaitHeldDestination())
                scenario.recreate()
                request("release-destination")
                waitForWholeMessage("Latest exact proof reply")
                scenario.recreate()
                waitForWholeMessage("Latest exact proof reply")
                device.executeShellCommand("input keyevent KEYCODE_BACK")
                waitForWholeMessage("Unrelated proof reply")
                ui.onNodeWithText("Latest exact proof reply").assertDoesNotExist()
                capture("warm-recreated-return-origin")

                request("hold-destination")
                openWarmLink(link)
                observed.addProperty("heldWarmReadBeforeBack", awaitHeldDestination())
                request("hold-recreated-destination")
                scenario.recreate()
                val dismissedRead = awaitHeldDestination()
                observed.addProperty("heldRestoredWarmReadBeforeBack", dismissedRead)
                val heldReply = request("held-destination")
                assertEquals(dismissedRead, heldReply.get("id").asString)
                val cancelledCallback = heldReply.get("index").asInt
                SaitoPeer.watchOwnedProofCallback(cancelledCallback)
                val opening = By.text(context.getString(R.string.spaces_destination_opening))
                ui.waitUntil(8_000) { device.findObject(opening) != null }
                ui.mainClock.autoAdvance = false
                try {
                    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                    ui.waitUntil(2_500) { SaitoPeer.cancelledOwnedProofCallback(cancelledCallback) }
                    // The exit animation cannot advance while the independently held response arrives.
                    assertTrue(request("release-destination").get("released").asBoolean)
                    ui.waitUntil(10_000) { SaitoPeer.discardedOwnedProofCallback(cancelledCallback) }
                } finally { ui.mainClock.autoAdvance = true }
                ui.waitUntil(10_000) {
                    !ui.onNodeWithText(context.getString(R.string.spaces_destination_opening)).isDisplayed()
                }
                observed.addProperty("discardedWarmReadAfterBack", dismissedRead)
                waitForWholeMessage("Unrelated proof reply")
                ui.onNodeWithText("Latest exact proof reply").assertDoesNotExist()
                scenario.recreate()
                waitForWholeMessage("Unrelated proof reply")
                ui.onNodeWithText("Latest exact proof reply").assertDoesNotExist()
                capture("warm-dismissed-recreated-origin")

                request("hold-destination")
                openWarmLink(link)
                observed.addProperty("heldWarmReadBeforeChannelReturn", awaitHeldDestination())
                assertTrue(request("release-destination").get("released").asBoolean)
                waitForWholeMessage("Latest exact proof reply")
                val backToChannel = context.getString(R.string.spaces_back_to_channel)
                click(backToChannel)
                waitText("Separate proof conversation")
                ui.onNodeWithText(backToChannel).assertDoesNotExist()
                scenario.recreate()
                waitText("Separate proof conversation")
                ui.onNodeWithText(backToChannel).assertDoesNotExist()
                ui.onNodeWithText("Latest exact proof reply").assertDoesNotExist()
                capture("explicit-channel-return-restored")
                save("notification-result", observed)
            }
        }
    }

    @Test fun visibleSearchDiscardsRevokedPrivateDataBeforeAnUnrelatedReply() {
        val config = request("config")
        prepareWallet(config)
        var armed = false
        var released = false
        var primary: Throwable? = null
        try {
            val otherSpace = request("search-other-community")
            launch(otherSpace.get("link").asString).use { waitText("Other Space discussion") }
            launch(config.get("link").asString).use {
                withFailureCapture({ capture("search-revocation-failure") }) {
                    val privateChannel = "Temporary search channel"
                    val privateMessage = "Temporary authorized Needle"
                    waitText("Separate proof conversation")
                    val target = request("search-channel").get("channel").asString
                    ui.waitUntil(25_000) { request("search-admit").get("ready").asBoolean }
                    val switcher = ui.onNode(hasContentDescription("Switch Space or channel") and hasClickAction())
                    val drawer = ui.onNode(hasScrollToNodeAction() and hasAnyAncestor(SemanticsMatcher.expectValue(
                        SemanticsProperties.PaneTitle, context.getString(R.string.spaces_navigation_panel))))
                    fun openChannel(name: String) {
                        ui.waitUntil(12_000) { switcher.isDisplayed() }
                        switcher.performClick()
                        ui.waitUntil(12_000) { drawer.isDisplayed() }
                        drawer.performScrollToNode(hasText(name) and hasClickAction())
                        click(name)
                    }
                    openChannel(privateChannel)
                    waitText(privateMessage)
                    openChannel("General")
                    waitText("Separate proof conversation")
                    click("Community information")
                    click("Search this Space")
                    val field = ui.onNode(hasSetTextAction() and hasText("Search saved history"))
                    ui.waitUntil(12_000) { field.isDisplayed() }
                    field.performTouchInput { click() }
                    field.performTextReplacement("Needle")
                    Espresso.closeSoftKeyboard()
                    click("All available channels")
                    val privateOption = ui.onNode(hasText(privateChannel) and hasAnyAncestor(isPopup()))
                    ui.waitUntil(12_000) { privateOption.isDisplayed() }
                    click(privateChannel, popup = true)
                    val results = ui.onNodeWithTag("spaces-search-list")
                    fun scrollToResult(text: String): Boolean = runCatching {
                        results.performScrollToNode(hasText(text))
                        ui.onNodeWithText(text).isDisplayed()
                    }.getOrDefault(false)
                    // At the compact font scale, the filters can fill the first viewport.
                    ui.waitUntil(25_000) { scrollToResult(privateMessage) }
                    capture("search-before-revocation")

                    val backgroundSpace = otherSpace.get("space").asString.take(10)
                    val backgroundChange = "access changed $backgroundSpace revision="
                    fun observations() = device.executeShellCommand("logcat -d -s SpacesAccount138:D '*:S'").lineSequence()
                    val earlierRoles = observations().count { backgroundChange in it }
                    val earlierSearchReads = observations().count { "search read " in it }
                    val backgroundRole = request("search-change-other")
                    assertEquals("MEMBER", backgroundRole.get("role").asString)
                    ui.waitUntil(25_000) {
                        ui.onNodeWithText(privateMessage).assertIsDisplayed()
                        observations().filter { backgroundChange in it }.drop(earlierRoles).any()
                    }
                    ui.onNodeWithText(privateMessage).assertIsDisplayed()
                    val selectedFilter = hasText(privateChannel) and hasClickAction() and
                        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
                    results.performScrollToNode(selectedFilter)
                    ui.onNode(selectedFilter).assertIsDisplayed()
                    ui.waitUntil(5_000) { scrollToResult(privateMessage) }
                    assertEquals("An unrelated Space change restarted this Search", earlierSearchReads,
                        observations().count { "search read " in it })
                    capture("search-retained-during-other-space-role")
                    File(evidence, "search-other-space-role.json").writeText(backgroundRole.toString())

                    armed = true
                    val revoked = request("search-revoke")
                    assertEquals(target, revoked.get("channel").asString)
                    var held: String? = null
                    ui.waitUntil(15_000) {
                        held = request("search-held").get("id")?.takeUnless { it.isJsonNull }?.asString
                        held != null
                    }
                    val read = requireNotNull(held)
                    ui.waitUntil(6_000) {
                        scrollToResult("First proof Needle") && !ui.onNodeWithText(privateMessage).isDisplayed()
                    }
                    assertFalse("The revoked result can still be scrolled into view", scrollToResult(privateMessage))
                    ui.onNodeWithText(privateMessage).assertDoesNotExist()
                    results.performScrollToIndex(0)
                    click("All available channels")
                    ui.onNode(hasText("General") and hasAnyAncestor(isPopup())).assertIsDisplayed()
                    privateOption.assertDoesNotExist()
                    assertEquals(read, request("search-held").get("id").asString)
                    capture("search-revoked-during-held-reply")
                    device.pressBack()
                    released = request("search-release").get("released").asBoolean
                    assertTrue(released)
                    ui.waitUntil(10_000) {
                        device.executeShellCommand("logcat -d -s SpacesAccount138:D '*:S'").lineSequence()
                            .dropWhile { "request=${read.take(10)} returnedMs=" !in it }
                            .drop(1).any { it.endsWith("refresh projected") }
                    }
                    ui.waitUntil(25_000) { scrollToResult("First proof Needle") }
                    assertFalse("The delayed reply restored the revoked result", scrollToResult(privateMessage))
                    ui.onNodeWithText(privateMessage).assertDoesNotExist()
                    results.performScrollToIndex(0)
                    click("All available channels")
                    ui.onNode(hasText("General") and hasAnyAncestor(isPopup())).assertIsDisplayed()
                    privateOption.assertDoesNotExist()
                    device.pressBack()
                    revoked.addProperty("heldRead", read)
                    File(evidence, "search-revocation-result.json").writeText(revoked.toString())
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally { cleanupAfterTest(primary, { if (armed && !released) request("search-release") }) }
    }

    @Test fun coldExactLinkAndRestrictedLink() {
        val config = request("config")
        val prior = JsonParser.parseString(File(evidence, "notification-result.json").readText()).asJsonObject
        val observed = request("observe")
        assertNotEquals(prior.get("pid").asInt, Process.myPid())
        assertEquals(prior.get("reply").asString, observed.get("reply").asString)
        assertEquals(config.get("nativeWallet").asString, AndroidWalletStore(context).loadExisting().publicKey)
        val link = observed.get("link").asString
        fun assertExactReply() {
            waitForWholeMessage("Latest exact proof reply")
            waitText("First proof Needle")
            ui.onNodeWithText("Unrelated proof reply").assertDoesNotExist()
        }
        request("hold-destination")
        launch(link).use {
            withFailureCapture({ capture("cold-exact-failure") }) {
                val held = awaitHeldDestination()
                assertTrue(device.pressHome())
                assertTrue(device.wait(Until.gone(By.pkg(context.packageName).depth(0)), 5_000))
                assertTrue(request("release-destination").get("released").asBoolean)
                device.executeShellCommand("am start -W --activity-single-top -n ${context.packageName}/${MainActivity::class.java.name}")
                assertExactReply()
                ui.onNodeWithText(context.getString(R.string.spaces_destination_saved)).assertDoesNotExist()
                ui.onNodeWithText("Showing saved history.", substring = true).assertDoesNotExist()
                observed.addProperty("heldReadResumedAfterHome", held)
                capture("cold-exact-reply")
            }
        }
        val owner = config.get("nativeWallet").asString
        request("hold-destination")
        launch(link, owner, SaitoBackend.OWNER).use { scenario ->
            withFailureCapture({ capture("recreated-exact-failure") }) {
                observed.addProperty("heldReadBeforeRecreation", awaitHeldDestination())
                var originalActivity = 0
                scenario.onActivity { originalActivity = System.identityHashCode(it) }
                scenario.recreate()
                scenario.onActivity { assertNotEquals(originalActivity, System.identityHashCode(it)) }
                request("release-destination")
                assertExactReply()
                capture("recreated-exact-reply")
            }
        }
        request("hold-destination")
        launch(link).use {
            withFailureCapture({ capture("dismissed-exact-failure") }) {
                observed.addProperty("heldReadBeforeBack", awaitHeldDestination())
                device.executeShellCommand("input keyevent KEYCODE_BACK")
                waitText("All posts")
                request("release-destination")
                ui.revealAppNavigation()
                val switch = ui.onNodeWithContentDescription("Switch app: Red Square")
                ui.waitUntil(10_000) { switch.isDisplayed() && ui.onNodeWithText("Home").isDisplayed() }
                switch.performTouchInput { click() }
                val spaces = ui.onNode(hasText("Spaces") and hasClickAction() and hasAnyAncestor(isPopup() or isDialog()))
                ui.waitUntil(5_000) { spaces.isDisplayed() }
                spaces.performClick()
                waitText("My Spaces")
                ui.onNodeWithText("Latest exact proof reply").assertDoesNotExist()
                capture("dismissed-exact-module-return")
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
                withFailureCapture({ captureNativeScreen(observations.resolve("route-failure.png")) }) {
                    waitText("Separate proof conversation")
                    val switcher = "Switch Space or channel"
                    assertTarget(hasContentDescription(switcher) and hasClickAction())
                    waitForInitialAppFocus()
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
                    // Let TalkBack finish restoring focus to the Activity tab before moving to the card.
                    waitForInitialAppFocus()
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
                    captureFocusedSpeech("unread-reply-group", summary, observations, target, completionTimeoutMillis = 25_000)
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
                val orientation = if (expectedLandscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
                ui.waitUntil(8_000) {
                    var matches = false
                    activity.onActivity { matches = it.resources.configuration.orientation == orientation }
                    matches
                }
                activity.onActivity { native ->
                    val actual = native.resources.configuration
                    assertEquals(expectedFont, actual.fontScale, 0.01f)
                    assertEquals(320, actual.densityDpi)
                    assertEquals(orientation, actual.orientation)
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
                assertTarget(hasText("Back to your communities") and hasClickAction() and
                    !hasAnyAncestor(hasScrollToNodeAction()))
                assertTarget(hasText("Community information") and hasClickAction())
                assertTarget(hasText("My Spaces") and hasClickAction())
                val stableBounds = button.getUnclippedBoundsInRoot()
                val switcherDescription = By.desc("Switch Space or channel")
                // Advance Compose's test clock while the native dialog relinquishes its window.
                ui.waitUntil(8_000) { device.findObject(switcherDescription) != null }
                captureNativeScreen(observations.resolve("channel.png"))
                requireNotNull(device.findObject(switcherDescription)).click()
                waitText("Your communities")
                val drawerClose = hasText(context.getString(R.string.spaces_navigation_close)) and hasClickAction()
                val drawerPanel = ui.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle,
                    context.getString(R.string.spaces_navigation_panel)), useUnmergedTree = true)
                val openPanel = drawerPanel.getUnclippedBoundsInRoot()
                assertTarget(drawerClose)
                captureNativeScreen(observations.resolve("drawer.png"))
                observeSystemDrawerBack(activity, observations) {
                    !ui.onNodeWithText("Your communities").isDisplayed() && button.isDisplayed()
                }
                assertEquals(stableBounds, button.getUnclippedBoundsInRoot())
                if (viewportId == "V04" || viewportId == "V05") {
                    ui.mainClock.autoAdvance = false
                    try {
                        button.performTouchInput { click() }
                        var entering = false
                        for (frame in 1..12) {
                            ui.mainClock.advanceTimeByFrame()
                            val bounds = drawerPanel.getUnclippedBoundsInRoot()
                            if (bounds.right > 8.dp && bounds.left < openPanel.left - 8.dp) {
                                entering = true
                                checked.addProperty("drawerEnteringVisibleRightDp", bounds.right.value)
                                break
                            }
                        }
                        assertTrue("The rendered drawer must enter only partway before Back", entering)
                        captureEnteringDrawer(observations)
                        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                    } finally { ui.mainClock.autoAdvance = true }
                    ui.waitUntil(12_000) {
                        !ui.onNodeWithText("Your communities").isDisplayed() && button.isDisplayed()
                    }
                    assertEquals(stableBounds, button.getUnclippedBoundsInRoot())
                    checked.addProperty("interruptedDrawerOpening", true)

                    val middle = device.displayHeight / 2
                    assertTrue(device.swipe(device.displayWidth / 8, middle, device.displayWidth * 7 / 8, middle, 35))
                    waitText("Your communities")
                    captureNativeScreen(observations.resolve("drawer-opened-by-swipe.png"))
                    ui.onNode(drawerClose).performTouchInput { click() }
                    ui.waitUntil(12_000) {
                        !ui.onNodeWithText("Your communities").isDisplayed() && button.isDisplayed()
                    }
                    assertEquals(stableBounds, button.getUnclippedBoundsInRoot())
                    checked.addProperty("drawerSwipe", true)
                }
                val composer = ui.onNode(hasSetTextAction())
                composer.assertIsDisplayed()
                if (useKeyboard) {
                    composer.performTouchInput { click() }
                    observeInsets(true, "keyboard")
                    composer.performTextInput("Viewport draft")
                    composer.assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertTextContains("Viewport draft")
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
        val bounds = ui.onNode(control).fetchSemanticsNode().touchBoundsInRoot
        val minimum = context.resources.displayMetrics.density * 48f
        assertTrue("Touch target must be at least 48dp in both directions: $bounds",
            bounds.width + .5f >= minimum && bounds.height + .5f >= minimum)
    }

    private fun captureEnteringDrawer(observations: File) {
        val closed = requireNotNull(BitmapFactory.decodeFile(observations.resolve("channel.png").path))
        val open = requireNotNull(BitmapFactory.decodeFile(observations.resolve("drawer.png").path))
        try {
            // This column lies inside the first visible part of the panel and outside its row text.
            val x = 4
            val samples = (2..4).map { closed.height * it / 6 }
                .filter { y -> closed.getPixel(x, y) != open.getPixel(x, y) }
            assertTrue("Open and closed drawer captures must contain distinct pixels", samples.isNotEmpty())
            ui.waitUntil(3_000) {
                val frame = instrumentation.uiAutomation.takeScreenshot() ?: return@waitUntil false
                try {
                    val drawn = samples.any { y -> frame.getPixel(x, y) == open.getPixel(x, y) }
                    if (drawn) observations.resolve("drawer-interrupted-before-back.png").outputStream().use {
                        check(frame.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                    drawn
                } finally { frame.recycle() }
            }
        } finally { closed.recycle(); open.recycle() }
    }

    private fun observeSystemDrawerBack(activity: ActivityScenario<MainActivity>, observations: File, closed: () -> Boolean) {
        val events = StringBuilder()
        var callback: Window.Callback? = null
        var originalActivity: MainActivity? = null
        fun currentWindowIsFocused(): Boolean {
            var focused = false
            activity.onActivity { native ->
                check(originalActivity == null || originalActivity === native)
                originalActivity = native
                focused = native.hasWindowFocus()
            }
            return focused
        }
        if (!currentWindowIsFocused()) {
            val windows = device.executeShellCommand("dumpsys window")
            events.appendLine(windows.lineSequence().filter { "mCurrentFocus" in it || "mFocusedApp" in it }.joinToString("\n"))
            events.appendLine(device.executeShellCommand("am start -W --activity-single-top " +
                "-n ${context.packageName}/${MainActivity::class.java.name}"))
            // Sending a real key requires the original Activity to own focus.
            // Viewport configuration changes can leave the emulator's system window focused.
        }
        activity.onActivity { native ->
            events.appendLine("focus=${native.hasWindowFocus()} enabled=${native.onBackPressedDispatcher.hasEnabledCallbacks()}")
            val original = native.window.callback
            callback = original
            native.window.callback = object : Window.Callback by original {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                    val handled = original.dispatchKeyEvent(event)
                    if (event.keyCode == KeyEvent.KEYCODE_BACK) events.appendLine("back action=${event.action} handled=$handled")
                    return handled
                }
            }
        }
        var failure: Throwable? = null
        try {
            ui.waitUntil(8_000) { currentWindowIsFocused() }
            Espresso.pressBack()
            ui.waitUntil(12_000, closed)
        } catch (problem: Throwable) {
            failure = problem
            withFailureCapture({
                captureNativeScreen(observations.resolve("before-dispatcher-diagnostic.png"))
                activity.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                ui.waitForIdle()
                events.appendLine("closedAfterDispatcher=${closed()}")
                captureNativeScreen(observations.resolve("after-dispatcher-diagnostic.png"))
            }) { throw problem }
        } finally {
            cleanupAfterTest(failure,
                { activity.onActivity { native -> callback?.let { native.window.callback = it } } },
                { observations.resolve("native-back-dispatch.txt").writeText(events.toString()) })
        }
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
            for (field in listOf("heldSearchReadBeforeRecreation", "heldActivityReadBeforeRecreation", "heldReadResumedAfterHome", "heldReadBeforeRecreation", "heldReadBeforeBack",
                "heldWarmReadBeforeRecreation", "heldWarmReadBeforeBack", "heldRestoredWarmReadBeforeBack", "discardedWarmReadAfterBack",
                "heldWarmReadBeforeChannelReturn"))
                observed.get(field)?.let { add(field, it) }
        }.toString())
    }

    private fun awaitHeldDestination(): String {
        var held: String? = null
        var failure: Throwable? = null
        try {
            ui.waitUntil(10_000) {
                held = request("held-destination").get("id")?.takeUnless { it.isJsonNull }?.asString
                held != null && ui.onNodeWithText(context.getString(R.string.spaces_destination_opening)).isDisplayed()
            }
        } catch (problem: Throwable) {
            failure = problem
            throw problem
        } finally {
            cleanupAfterTest(failure, { File(evidence, "held-destination-observations.txt").appendText("held=$held\n") })
        }
        return requireNotNull(held)
    }

    private fun openWarmLink(link: String) {
        check(link.matches(Regex("saito://spaces/target#[A-Za-z0-9_-]+")))
        // UiAutomation dispatches arguments directly. Quotation marks would
        // become part of the URI instead of escaping the checked Base64 token.
        val delivery = device.executeShellCommand("am start -W --activity-single-top -a android.intent.action.VIEW -d $link " +
            "-n ${context.packageName}/${MainActivity::class.java.name}")
        File(evidence, "warm-activity-delivery.txt").appendText(delivery + "\n")
        assertTrue("Android should deliver the shared link to the running Activity: $delivery",
            delivery.contains("delivered to currently running top-most instance"))
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
