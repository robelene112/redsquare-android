package nl.davidengelen.redsquare

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.view.inspector.WindowInspector
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Actual 1.3 font rendering, focused dialog IME and physical actions, retaining an unsent Chat draft. */
class PublisherViewportPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun largerTextKeepsPublisherActionsReachableWithTheKeyboardAndPreservesUnsentChat() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val contact = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixtures only.
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2").withChatContact(contact.publicKey)
        val wallets = AndroidWalletStore(context)
        val originalWallet = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context)
        val originalBackend = backends.load()
        val prefixes = SaitoBackend.entries.flatMap { backend -> listOf("chat.${backend.id}.${wallet.publicKey}.",
            "chatwork.${backend.id}.${wallet.publicKey}.", "chatreactions.${backend.id}.${wallet.publicKey}.") } +
            listOf("personal.${wallet.publicKey}.", "publisher-routes-fixture.json", "publishing-policy-fixture.json")
        val retained = ChatFixtureState(context, prefixes, wallet.publicKey)
        val folder = File(context.cacheDir, "publisher-viewport-evidence")
        val font = EmulatorFontScale(context, folder, "publisher")
        val reads = AtomicInteger()
        val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = {
            reads.incrementAndGet(); "[]"
        })
        val chatDraft = "Unsent private Chat draft 😀\nStill here after contacting the publisher"
        val description = (1..12).joinToString("\n") { "Public support description line $it" }
        val url = "HTTPS://publisher.example/requests?context=" + "shown-context-".repeat(16)
        val expectedBrowserUrl = "https://publisher.example/requests?context=" + "shown-context-".repeat(16)
        var primary: Throwable? = null
        Intents.init()
        try {
            for (action in listOf(Intent.ACTION_SENDTO, Intent.ACTION_VIEW)) {
                Intents.intending(hasAction(action)).respondWith(
                    Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
            }
            font.apply(1.3f)
            retained.prepare()
            File(context.noBackupFilesDir, "publisher-routes-fixture.json").writeText(
                """{"ready":true,"supportEmail":"support@publisher.example","requestUrl":"$url"}""")
            File(context.noBackupFilesDir, "publishing-policy-fixture.json")
                .writeText("""{"enforce":true,"ready":false}""")
            wallets.replace(originalWallet, wallet)
            backends.save(SaitoBackend.OWNER)
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                    withFailureCapture({ captureNativeScreen(File(folder, "publisher-font130-first-failure.png")) }) {
                        fun openChat() {
                            waitFor("Chat", action = true)
                            ui.onNodeWithText("Chat").performClick()
                            waitFor("Groups", action = true)
                            val row = hasClickAction() and isEnabled() and hasAnyDescendant(hasText(contact.publicKey))
                            ui.waitUntil(20_000) { ui.onNode(row, useUnmergedTree = true).isDisplayed() }
                            ui.onNode(row, useUnmergedTree = true).performClick()
                            ui.waitUntil(20_000) { ui.onNode(hasSetTextAction() and isEnabled()).isDisplayed() && reads.get() > 0 }
                        }
                        fun external() = Intents.getIntents().filter {
                            it.action in listOf(Intent.ACTION_SENDTO, Intent.ACTION_VIEW)
                        }
                        openChat()
                        ui.onNode(hasSetTextAction()).performTextReplacement(chatDraft)
                        ui.onNodeWithText("Back").performClick()
                        waitFor("Groups", action = true)
                        ui.onNodeWithText("Back").performClick()
                        waitFor("Settings", action = true)
                        ui.onNodeWithText("Settings").performClick()
                        ui.onNodeWithText("Privacy and safety").performScrollTo().performClick()
                        ui.onNodeWithText("Policy Center").performScrollTo().performClick()
                        fun openSupport() {
                            entry("Contact publisher")
                            ui.waitUntil(20_000) {
                                ui.onAllNodesWithText("Email: support@publisher.example").fetchSemanticsNodes().isNotEmpty()
                            }
                            for (text in listOf("Publisher: Angels’ Foundry", "Email: support@publisher.example",
                                "App: Red Square 0.1.0")) ui.onNodeWithText(text).performScrollTo().assertIsDisplayed()
                            ui.onNode(hasSetTextAction()).performScrollTo().performTextReplacement(description)
                            ui.onNode(hasSetTextAction()).performClick()
                            ui.waitUntil(10_000) { (windowInsets()?.imeBottom ?: 0) > 0 }
                            ui.onNode(hasSetTextAction()).assertTextContains(description)
                        }
                        openSupport()
                        physicalAction("Compose email")
                        captureNativeScreen(File(folder, "publisher-font130-real-ime-cancel.png"))
                        val cancel = physicalAction("Cancel")
                        assertTrue(external().isEmpty())
                        tap(cancel)
                        ui.waitUntil(10_000) { windowInsets()?.imeBottom == 0 }
                        assertTrue(external().isEmpty())
                        openSupport()
                        val compose = physicalAction("Compose email")
                        captureNativeScreen(File(folder, "publisher-font130-real-ime-compose.png"))
                        tap(compose)
                        waitFor("Email draft requested. Review and send it in your email app. No message is sent automatically.")
                        ui.waitUntil(10_000) { windowInsets()?.imeBottom == 0 }
                        assertPublisherEmailDraft(external().single(), "support@publisher.example", "Red Square support",
                            "App: Red Square 0.1.0\n\nDescription:\n$description")
                        ui.onNode(hasSetTextAction()).assertTextContains(description)
                        tap(physicalAction("Done"))
                        entry("Data requests")
                        ui.waitUntil(20_000) { ui.onAllNodesWithText(url).fetchSemanticsNodes().isNotEmpty() }
                        ui.onNodeWithText(url).performScrollTo().assertIsDisplayed()
                        ui.onNodeWithText("Opening this page does not delete your wallet, local messages or server data. The publisher must verify and handle requests through its own process. Independent network or recipient copies may remain.")
                            .performScrollTo().assertIsDisplayed()
                        tap(physicalAction("Open request page"))
                        waitFor("Continue in your browser. Opening the page does not submit a request or delete data.")
                        assertEquals(2, external().size)
                        val browser = external().last()
                        assertEquals(Intent.ACTION_VIEW, browser.action)
                        assertEquals(expectedBrowserUrl, browser.dataString)
                        assertTrue(browser.extras == null || browser.extras!!.isEmpty)
                        assertNull(browser.clipData)
                        assertEquals(0, browser.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
                        captureNativeScreen(File(folder, "publisher-font130-long-request-page.png"))
                        tap(physicalAction("Done"))
                        ui.onNodeWithContentDescription("Close privacy and safety").performClick()
                        openChat()
                        ui.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(
                            androidx.compose.ui.semantics.SemanticsProperties.EditableText,
                            androidx.compose.ui.text.AnnotatedString(chatDraft)))
                        assertTrue(wallet.exportJson() == wallets.loadExisting().exportJson())
                        assertEquals(SaitoBackend.OWNER, backends.load())
                        assertTrue(peer.transactions.isEmpty())
                        assertTrue(peer.relayedMessages.isEmpty())
                        assertTrue(peer.reports.isEmpty())
                        peer.assertHealthy()
                        captureNativeScreen(File(folder, "publisher-font130-keeps-unsent-chat.png"))
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, { Intents.release() }, { retained.restoreFiles() },
                { wallets.replace(wallets.loadExisting(), originalWallet)
                    check(wallets.loadExisting().exportJson() == originalWallet.exportJson()) },
                { backends.save(originalBackend); check(backends.load() == originalBackend) },
                { retained.restorePreferences() }, { font.restore() })
        }
    }

    private fun entry(text: String) {
        ui.waitUntil(20_000) {
            ui.onAllNodes(hasText(text) and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithText(text).performScrollTo().assertIsDisplayed().performClick()
    }

    private fun waitFor(text: String, action: Boolean = false) = ui.waitUntil(20_000) {
        ui.onNode(if (action) hasText(text) and hasClickAction() and isEnabled() else hasText(text)).isDisplayed()
    }

    // Dialogs own separate Android windows. Observe the actual focused app root,
    // rather than borrowing the underlying Activity's potentially different IME state.
    private data class PhysicalInsets(val top: Int, val bottom: Int, val imeBottom: Int)

    private fun windowInsets(): PhysicalInsets? {
        var result: PhysicalInsets? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val root = WindowInspector.getGlobalWindowViews().singleOrNull { it.isShown && it.hasWindowFocus() }
            val insets = root?.let { ViewCompat.getRootWindowInsets(it) }
            if (insets != null) {
                val imeBottom = if (insets.isVisible(WindowInsetsCompat.Type.ime()))
                    insets.getInsets(WindowInsetsCompat.Type.ime()).bottom else 0
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                result = PhysicalInsets(bars.top, maxOf(bars.bottom, imeBottom), imeBottom)
            }
        }
        return result
    }

    private fun physicalAction(text: String): android.graphics.Rect {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        waitFor(text, action = true)
        var target = requireNotNull(device.findObject(By.text(text)))
        while (!target.isClickable) target = requireNotNull(target.parent)
        val bounds = target.visibleBounds
        val minimum = kotlin.math.round(48 * instrumentation.targetContext.resources.displayMetrics.density).toInt()
        val (top, bottom) = requireNotNull(windowInsets())
        if (!(bounds.width() >= minimum && bounds.height() >= minimum && bounds.top >= top &&
            bounds.bottom <= device.displayHeight - bottom)) {
            val failure = IllegalStateException("Full $text action must remain reachable: $bounds, insets=$top/$bottom")
            try { recordWindowMetrics(text, bounds) }
            catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
            throw failure
        }
        return bounds
    }

    /** Separate raw accessibility and actual app-window snapshots, without collecting user content. */
    private fun recordWindowMetrics(label: String, visibleBounds: android.graphics.Rect) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val record = com.google.gson.JsonObject().apply {
            addProperty("action", label)
            addProperty("uiautomatorVisibleBounds", visibleBounds.toShortString())
            addProperty("displayWidth", device.displayWidth)
            addProperty("displayHeight", device.displayHeight)
            addProperty("density", context.resources.displayMetrics.density)
            addProperty("fontScale", context.resources.configuration.fontScale)
            addProperty("accessibilityObservedAt", android.os.SystemClock.uptimeMillis())
        }
        val active = instrumentation.uiAutomation.rootInActiveWindow
        record.addProperty("activeRootIsApp", active?.packageName?.toString() == context.packageName)
        if (active != null && active.packageName?.toString() == context.packageName) {
            val rectangle = android.graphics.Rect()
            active.getBoundsInScreen(rectangle)
            record.addProperty("accessibilityRootBounds", rectangle.toShortString())
            active.window?.let { window ->
                window.getBoundsInScreen(rectangle)
                record.addProperty("accessibilityWindowBounds", rectangle.toShortString())
            }
            var node = active.findAccessibilityNodeInfosByText(label).singleOrNull { it.text?.toString() == label }
            while (node != null && !node.isClickable) node = node.parent
            node?.let {
                it.getBoundsInScreen(rectangle)
                record.addProperty("rawClickableBounds", rectangle.toShortString())
            }
        }
        val windows = com.google.gson.JsonArray()
        instrumentation.runOnMainSync {
            record.addProperty("appWindowsObservedAt", android.os.SystemClock.uptimeMillis())
            fun describe(view: android.view.View, depth: Int): com.google.gson.JsonObject {
                val location = IntArray(2).also(view::getLocationOnScreen)
                val rectangle = android.graphics.Rect()
                return com.google.gson.JsonObject().apply {
                    addProperty("viewClass", view.javaClass.name)
                    addProperty("width", view.width); addProperty("height", view.height)
                    addProperty("elevation", view.elevation)
                    addProperty("x", location[0]); addProperty("y", location[1])
                    addProperty("hasWindowFocus", view.hasWindowFocus())
                    addProperty("isShown", view.isShown)
                    addProperty("globallyVisible", view.getGlobalVisibleRect(rectangle))
                    addProperty("globalVisibleBounds", rectangle.toShortString())
                    if (view is android.view.ViewGroup && depth < 4) {
                        add("children", com.google.gson.JsonArray().apply {
                            for (index in 0 until view.childCount) add(describe(view.getChildAt(index), depth + 1))
                        })
                    }
                }
            }
            WindowInspector.getGlobalWindowViews().forEach { windows.add(describe(it, 0)) }
        }
        record.add("appWindows", windows)
        val folder = File(context.cacheDir, "publisher-viewport-evidence").apply { mkdirs() }
        File(folder, "physical-$label-window-metrics.json").writeText(record.toString())
    }

    private fun tap(bounds: android.graphics.Rect) {
        check(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).click(bounds.centerX(), bounds.centerY()))
    }
}
