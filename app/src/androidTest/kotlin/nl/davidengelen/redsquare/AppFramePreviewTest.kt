package nl.davidengelen.redsquare

import android.view.inspector.WindowInspector
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real first-use layout against a read-only compatible peer, with independent restoration. */
class AppFramePreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun firstUsePrioritizesVisiblePostsWithAnIdentifiableHomeDestination() = withFirstUseFeed("first-use") {
        ui.onNode(hasText("Home") and isSelected()).assertIsDisplayed()
        ui.onNodeWithText("Back up your wallet").assertIsDisplayed()
        ui.onNodeWithText("Import an existing wallet").assertIsDisplayed()
        val post = ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val viewport = ui.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("First-use reading must begin in the upper half of the viewport", post.top < viewport.height / 2)
        capture("first-use-content-first.png")
    }

    @Test fun homeReturnsFromADirectThreadWithoutDismissingTheBackupReminder() = withFirstUseFeed("home-return") {
        ui.onNodeWithText("Native Red Square reference post").performClick()
        ui.waitUntil(10_000) { ui.onNodeWithText("Conversation").isDisplayed() }
        ui.onNodeWithText("Back up your wallet").assertDoesNotExist()
        ui.onNode(hasText("Home") and isSelected()).performClick()
        ui.waitUntil(10_000) { ui.onNode(hasText("New post") and hasClickAction()).isDisplayed() }
        ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
        ui.onNodeWithText("Back up your wallet").assertIsDisplayed()
        capture("home-return-preserved-reminder.png")
        ui.onNodeWithText("Native Red Square reference post").performClick()
        ui.waitUntil(10_000) { ui.onNodeWithText("Conversation").isDisplayed() }
        ui.onNode(hasText("Anon-jesTu2") and hasClickAction()).performScrollTo().performClick()
        ui.waitUntil(10_000) { ui.onNode(hasText("Profile") and !hasClickAction()).isDisplayed() }
        ui.onNodeWithText("Back up your wallet").assertDoesNotExist()
        ui.onNodeWithText("Back").performClick()
        ui.waitUntil(10_000) { ui.onNodeWithText("Conversation").isDisplayed() }
        ui.onNode(hasText("Chat") and hasClickAction()).performClick()
        ui.waitUntil(10_000) { ui.onNodeWithText("New chat").isDisplayed() }
        ui.onNode(hasText("Chat") and isSelected()).assertIsDisplayed()
        ui.onNodeWithText("Back up your wallet").assertDoesNotExist()
        capture("chat-keeps-frame-without-backup-chrome.png")
        ui.onNode(hasText("Home") and hasClickAction()).performClick()
        ui.waitUntil(10_000) { ui.onNode(hasText("New post") and hasClickAction()).isDisplayed() }
        ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
        ui.onNodeWithText("Back up your wallet").assertIsDisplayed()
        capture("home-return-after-thread-profile-chat.png")
    }

    @Test fun primaryNavigationKeepsUnsentProfileEditsWhenReturningToTheEditor() = withFirstUseFeed("profile-navigation") {
        val draft = "Unsent profile edit retained through primary navigation"
        ui.onNode(hasText("Your profile") and hasClickAction()).performClick()
        ui.waitUntil(15_000) { ui.onNode(hasText("Edit profile") and hasClickAction()).isDisplayed() }
        ui.onNode(hasText("Edit profile") and hasClickAction()).performScrollTo().performClick()
        ui.waitUntil(10_000) { ui.onNode(hasSetTextAction() and isEnabled()).isDisplayed() }
        ui.onNode(hasSetTextAction()).performTextReplacement(draft)
        ui.onNode(hasSetTextAction()).performClick()
        ui.waitUntil(10_000) { imeVisible() == true }
        ui.onNode(hasSetTextAction()).assertTextContains(draft)
        capture("unsent-profile-real-ime.png")
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        ui.waitUntil(10_000) { imeVisible() == false }
        ui.onNode(hasSetTextAction()).assertTextContains(draft)
        ui.waitUntil(10_000) { ui.onNode(hasText("Home") and hasClickAction()).isDisplayed() }
        ui.onNode(hasText("Home") and hasClickAction()).assertIsDisplayed().performClick()
        ui.waitUntil(10_000) { ui.onNode(hasText("New post") and hasClickAction()).isDisplayed() }
        ui.onNodeWithText("Back up your wallet").assertIsDisplayed()
        ui.onNode(hasText("Your profile") and hasClickAction()).performClick()
        val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
        val restoredEditor = hasSetTextAction() and hasText(draft)
        ui.waitUntil(10_000) { ui.onAllNodes(restoredEditor).fetchSemanticsNodes().isNotEmpty() }
        // The retained biography also grows the preview above its editable field.
        ui.onNode(restoredEditor).performScrollTo().assertIsDisplayed()
        check(android.os.SystemClock.elapsedRealtime() <= deadline) {
            "Revealing the restored editor must meet its existing10s observation deadline"
        }
        ui.onNode(hasSetTextAction()).assertTextContains(draft)
        capture("profile-edit-retained-after-home.png")
    }

    @Test fun largerTextKeepsFrameControlsAndBackupGuidanceReachableAtBothViewportSizes() = withFirstUseFeed("frame-viewport") { activity ->
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val folder = File(context.cacheDir, "app-frame-evidence")
        val font = EmulatorFontScale(context, folder, "frame")
        val originalSize = device.executeShellCommand("wm size")
        val originalDensity = device.executeShellCommand("wm density")
        check(Regex("Physical size: [0-9]+x[0-9]+").containsMatchIn(originalSize))
        check(Regex("Physical density: [0-9]+").containsMatchIn(originalDensity))
        val sizeOverride = Regex("Override size: ([0-9]+x[0-9]+)").find(originalSize)?.groupValues?.get(1)
        val densityOverride = Regex("Override density: ([0-9]+)").find(originalDensity)?.groupValues?.get(1)
        val appearance = context.getSharedPreferences("appearance", 0)
        val originalAppearance = appearance.getString("mode", null)
        var primary: Throwable? = null
        try {
            device.executeShellCommand("wm density 160")
            for ((size, width) in listOf("320x640" to 320, "412x915" to 412)) {
                device.executeShellCommand("wm size $size")
                for (scale in listOf(1.3f, 2f)) {
                    font.apply(scale)
                    ui.recreateHomeViewport(activity, width, scale)
                    for (theme in listOf("Light", "Dark")) {
                        ui.onNode(hasText("Settings") and hasClickAction()).assertIsDisplayed().performClick()
                        ui.onNodeWithText("Appearance").performScrollTo().performClick()
                        ui.onNodeWithText(theme).performScrollTo().performClick()
                        ui.waitUntil(10_000) { ui.onNode(hasText(theme) and isSelected()).isDisplayed() }
                        ui.onNodeWithText("Done").performClick()
                        ui.waitUntil(10_000) {
                            var focused = false
                            activity.onActivity { focused = it.window.decorView.hasWindowFocus() }
                            focused && ui.onNode(hasText("Home") and isSelected()).isDisplayed()
                        }
                        val name = "frame-$width-font${(scale * 100).toInt()}-${theme.lowercase()}"
                        withFailureCapture({ capture("$name-first-failure.png") }) {
                        val viewport = ui.onRoot().fetchSemanticsNode().boundsInRoot
                        val minimum = 48 * context.resources.displayMetrics.density - 1
                        for (label in listOf("Wallet", "Settings", "Home", "Search", "Notifications", "Chat", "Your profile",
                            "Back up", "Later", "Import an existing wallet", "New post", "More feed actions")) {
                            val node = ui.onNode(hasText(label) and hasClickAction()).assertIsDisplayed().fetchSemanticsNode()
                            val visualBounds = node.boundsInRoot
                            val bounds = node.touchBoundsInRoot
                            val selectedTab = node.config.getOrNull(SemanticsProperties.Role) == Role.Tab &&
                                node.config.getOrNull(SemanticsProperties.Selected) == true
                            var native = device.findObject(By.text(label))
                            // Compose advertises a selected tab as selected rather than ACTION_CLICK.
                            while (native != null && !native.isClickable && !(selectedTab && native.isSelected)) native = native.parent
                            val nativeBounds = native?.visibleBounds
                            val nativeAction = native?.isEnabled == true && (native?.isClickable == true ||
                                (selectedTab && native?.isSelected == true))
                            File(folder, "$name-${label.lowercase().replace(' ', '-')}-target.json").writeText(
                                com.google.gson.JsonObject().apply {
                                    addProperty("label", label); addProperty("minimumPixels", minimum)
                                    addProperty("composeBounds", visualBounds.toString()); addProperty("touchBounds", bounds.toString())
                                    addProperty("viewportBounds", viewport.toString())
                                    addProperty("nativeTargetBounds", nativeBounds?.toShortString())
                                    addProperty("composeSelectedTab", selectedTab)
                                    addProperty("nativeSelected", native?.isSelected)
                                    addProperty("nativeClickable", native?.isClickable)
                                    addProperty("fontScale", context.resources.configuration.fontScale)
                                    addProperty("density", context.resources.displayMetrics.density)
                                    addProperty("screenWidthDp", context.resources.configuration.screenWidthDp)
                                }.toString())
                            assertTrue("$label must retain a full reachable target at $name", bounds.width >= minimum &&
                                bounds.height >= minimum && bounds.left >= viewport.left && bounds.right <= viewport.right &&
                                bounds.top >= viewport.top && bounds.bottom <= viewport.bottom && nativeAction && nativeBounds != null &&
                                nativeBounds.width() >= minimum && nativeBounds.height() >= minimum)
                        }
                        ui.onNodeWithText("Node: saito.davidengelen.nl").assertIsDisplayed()
                        if (scale <= 1.3f) assertTrue("Reading remains dominant at $name",
                            ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
                                .fetchSemanticsNode().boundsInRoot.top < viewport.height / 2)
                        File(folder, "$name.json").writeText(com.google.gson.JsonObject().apply {
                            addProperty("widthPixels", device.displayWidth); addProperty("heightPixels", device.displayHeight)
                            addProperty("widthDp", context.resources.configuration.screenWidthDp)
                            addProperty("density", context.resources.displayMetrics.density)
                            addProperty("fontScale", context.resources.configuration.fontScale)
                            addProperty("theme", theme); addProperty("layoutOnly", true)
                        }.toString())
                        capture("$name.png")
                        ui.onNode(hasText("Back up") and hasClickAction()).performClick()
                        ui.onNodeWithText(context.getString(R.string.wallet_backup_private)).performScrollTo().assertIsDisplayed()
                        capture("$name-backup-guidance.png")
                        ui.onNodeWithText("Done").performClick()
                        ui.onNodeWithText("Back up your wallet").assertIsDisplayed()
                        }
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, { activity.close() }, { font.restore() },
                { device.executeShellCommand("wm size ${sizeOverride ?: "reset"}")
                    check(device.executeShellCommand("wm size").trim() == originalSize.trim()) },
                { device.executeShellCommand("wm density ${densityOverride ?: "reset"}")
                    check(device.executeShellCommand("wm density").trim() == originalDensity.trim()) },
                { check(appearance.edit().apply { if (originalAppearance == null) remove("mode")
                    else putString("mode", originalAppearance) }.commit()) })
        }
    }

    private fun withFirstUseFeed(name: String, block: (ActivityScenario<MainActivity>) -> Unit) =
        withAppFrameFixture(name) { activity ->
            ui.waitUntil(15_000) {
                ui.onAllNodesWithText("Native Red Square reference post").fetchSemanticsNodes().isNotEmpty()
            }
            block(activity)
        }

    private fun imeVisible(): Boolean? {
        var visible: Boolean? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val root = WindowInspector.getGlobalWindowViews().singleOrNull { it.isShown && it.hasWindowFocus() }
            visible = root?.let { ViewCompat.getRootWindowInsets(it)?.isVisible(WindowInsetsCompat.Type.ime()) }
        }
        return visible
    }

    private fun capture(name: String) = captureNativeScreen(File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "app-frame-evidence/$name"))
}
