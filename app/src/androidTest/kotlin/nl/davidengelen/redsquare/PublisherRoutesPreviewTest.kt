package nl.davidengelen.redsquare

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.ActivityNotFoundException
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Real native navigation and Android intent interception; no email or public write. */
class PublisherRoutesPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun readyPublisherSupportOnlyOpensTheShownEmailDraftAndKeepsTextWhenNoEmailAppOpens() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallets = AndroidWalletStore(context)
        val originalWallet = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context)
        val originalBackend = backends.load()
        val retained = ChatFixtureState(context, listOf("publisher-routes-fixture.json",
            "publishing-policy-fixture.json"), originalWallet.publicKey)
        val unavailable = AtomicBoolean(false)
        val description = "The draft stays on my phone until I choose my email app 😀"
        var primary: Throwable? = null
        Intents.init()
        try {
            Intents.intending(hasAction(Intent.ACTION_SENDTO)).respondWithFunction {
                if (unavailable.get()) throw ActivityNotFoundException("Owned unavailable email handler")
                Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
            Intents.intending(hasAction(Intent.ACTION_VIEW)).respondWith(
                Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
            retained.prepare()
            File(context.noBackupFilesDir, "publisher-routes-fixture.json").writeText(
                """{"ready":true,"supportEmail":"support@publisher.example"}""")
            File(context.noBackupFilesDir, "publishing-policy-fixture.json")
                .writeText("""{"enforce":true,"ready":false}""")
            backends.save(SaitoBackend.OWNER)
            val peer = ControlledFeedPeer("[]")
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { activity ->
                    withFailureCapture({ capture("ready-support-first-failure.png") }) {
                        waitFor("Settings", action = true)
                        ui.onNodeWithText("Settings").performClick()
                        ui.onNodeWithText("Privacy and safety").performScrollTo().performClick()
                        ui.onNodeWithText("Policy Center").performClick()
                        waitFor("Contact publisher", action = true)
                        fun openSupport() {
                            ui.onNodeWithText("Contact publisher").performScrollTo().performClick()
                            waitFor("Email: support@publisher.example")
                            ui.onNodeWithText("Publisher: Angels’ Foundry").assertIsDisplayed()
                            ui.onNodeWithText("App: Red Square 0.1.0").assertIsDisplayed()
                            waitForEditor()
                        }
                        fun external() = Intents.getIntents().filter {
                            it.action in listOf(Intent.ACTION_SENDTO, Intent.ACTION_VIEW)
                        }
                        openSupport()
                        ui.onNode(hasSetTextAction()).performTextReplacement("Cancel this unpublished description")
                        ui.onNodeWithText("Cancel").performClick()
                        waitFor("Contact publisher", action = true)
                        assertTrue(external().isEmpty())
                        openSupport()
                        ui.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(
                            androidx.compose.ui.semantics.SemanticsProperties.EditableText,
                            androidx.compose.ui.text.AnnotatedString("")))
                        ui.onNode(hasSetTextAction()).performTextReplacement(description)
                        activity.recreate()
                        waitForEditor()
                        ui.onNode(hasSetTextAction()).assertTextContains(description)
                        ui.onNodeWithText("Compose email").performClick()
                        waitFor("Email draft requested. Review and send it in your email app. No message is sent automatically.")
                        val draft = external().single()
                        assertPublisherEmailDraft(draft, "support@publisher.example", "Red Square support",
                            "App: Red Square 0.1.0\n\nDescription:\n$description")
                        capture("ready-support-draft-requested.png")
                        ui.onNodeWithText("Done").performClick()
                        waitFor("Contact publisher", action = true)
                        openSupport()
                        unavailable.set(true)
                        ui.onNode(hasSetTextAction()).performTextReplacement("Keep this editable description")
                        ui.onNodeWithText("Compose email").performClick()
                        waitFor("No email app could be opened. Your description is still here. Install or enable an email app, then try again.")
                        ui.onNode(hasSetTextAction()).assertTextContains("Keep this editable description")
                        assertEquals(2, external().size)
                        capture("ready-support-no-email-app.png")
                        ui.onNodeWithText("Cancel").performClick()
                        assertTrue("Support must preserve complete wallet options",
                            originalWallet.exportJson() == wallets.loadExisting().exportJson())
                        assertEquals(SaitoBackend.OWNER, backends.load())
                        assertTrue(peer.transactions.isEmpty())
                        assertTrue(peer.reports.isEmpty())
                        peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, { Intents.release() }, { retained.restoreFiles() },
                { wallets.replace(wallets.loadExisting(), originalWallet) },
                { backends.save(originalBackend); check(backends.load() == originalBackend) },
                { retained.restorePreferences() })
        }
    }

    @Test fun safetyAndPrivacyContactsKeepTheirShownDestinationsSeparate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallets = AndroidWalletStore(context)
        val originalWallet = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context)
        val originalBackend = backends.load()
        val retained = ChatFixtureState(context, listOf("publisher-routes-fixture.json",
            "publishing-policy-fixture.json"), originalWallet.publicKey)
        var primary: Throwable? = null
        Intents.init()
        try {
            for (action in listOf(Intent.ACTION_SENDTO, Intent.ACTION_VIEW)) {
                Intents.intending(hasAction(action)).respondWith(
                    Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
            }
            retained.prepare()
            File(context.noBackupFilesDir, "publisher-routes-fixture.json").writeText(
                """{"ready":true,"supportEmail":"support@publisher.example","safetyEmail":"safety@publisher.example","privacyEmail":"privacy@publisher.example"}""")
            File(context.noBackupFilesDir, "publishing-policy-fixture.json")
                .writeText("""{"enforce":true,"ready":false}""")
            backends.save(SaitoBackend.OWNER)
            val peer = ControlledFeedPeer("[]")
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                    withFailureCapture({ capture("purpose-contacts-first-failure.png") }) {
                        waitFor("Settings", action = true)
                        ui.onNodeWithText("Settings").performClick()
                        ui.onNodeWithText("Privacy and safety").performScrollTo().performClick()
                        ui.onNodeWithText("Policy Center").performClick()
                        val cases = listOf(
                            ContactCase("Safety concerns", "Publisher safety", "safety@publisher.example",
                                "Red Square safety concern", "A safety concern for the publisher"),
                            ContactCase("Privacy inquiries", "Publisher privacy", "privacy@publisher.example",
                                "Red Square privacy inquiry", "A privacy question for the publisher"))
                        for ((index, case) in cases.withIndex()) {
                            ui.waitUntil(20_000) {
                                ui.onAllNodes(hasText(case.entry) and hasClickAction() and isEnabled())
                                    .fetchSemanticsNodes().isNotEmpty()
                            }
                            ui.onNodeWithText(case.entry).performScrollTo().assertIsDisplayed().performClick()
                            waitFor(case.title)
                            ui.onNodeWithText("Email: ${case.recipient}").assertIsDisplayed()
                            ui.onNodeWithText("Publisher: Angels’ Foundry").assertIsDisplayed()
                            ui.onNodeWithText("App: Red Square 0.1.0").assertIsDisplayed()
                            waitForEditor()
                            val externalBefore = Intents.getIntents().filter {
                                it.action in listOf(Intent.ACTION_SENDTO, Intent.ACTION_VIEW)
                            }
                            assertEquals(index, externalBefore.size)
                            ui.onNode(hasSetTextAction()).performTextReplacement(case.description)
                            ui.onNodeWithText("Compose email").performClick()
                            waitFor("Email draft requested. Review and send it in your email app. No message is sent automatically.")
                            val external = Intents.getIntents().filter {
                                it.action in listOf(Intent.ACTION_SENDTO, Intent.ACTION_VIEW)
                            }
                            assertEquals(index + 1, external.size)
                            val draft = external.last()
                            assertPublisherEmailDraft(draft, case.recipient, case.subject,
                                "App: Red Square 0.1.0\n\nDescription:\n${case.description}")
                            capture("purpose-contact-$index.png")
                            ui.onNodeWithText("Done").performClick()
                        }
                        assertTrue("Publisher routes must preserve complete wallet options",
                            originalWallet.exportJson() == wallets.loadExisting().exportJson())
                        assertEquals(SaitoBackend.OWNER, backends.load())
                        assertTrue(peer.transactions.isEmpty())
                        assertTrue(peer.reports.isEmpty())
                        peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, { Intents.release() }, { retained.restoreFiles() },
                { wallets.replace(wallets.loadExisting(), originalWallet) },
                { backends.save(originalBackend); check(backends.load() == originalBackend) },
                { retained.restorePreferences() })
        }
    }

    private data class ContactCase(val entry: String, val title: String, val recipient: String,
        val subject: String, val description: String)

    @Test fun unconfiguredPublisherSupportExplainsAvailabilityWithoutHandingOffOrLosingDrafts() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallets = AndroidWalletStore(context)
        val originalWallet = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context)
        val originalBackend = backends.load()
        val retained = ChatFixtureState(context, listOf("postdraft.", "publishing-policy-fixture.json",
            "publisher-routes-fixture.json"), originalWallet.publicKey)
        val text = "Keep this unsent draft while checking publisher support 😀"
        var primary: Throwable? = null
        Intents.init()
        try {
            for (action in listOf(Intent.ACTION_SENDTO, Intent.ACTION_VIEW)) {
                Intents.intending(hasAction(action)).respondWith(
                    Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
            }
            retained.prepare()
            File(context.noBackupFilesDir, "publishing-policy-fixture.json")
                .writeText("""{"enforce":true,"ready":false}""")
            backends.save(SaitoBackend.OWNER)
            val peer = ControlledFeedPeer("[]")
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                    withFailureCapture({ capture("unconfigured-support-first-failure.png") }) {
                        waitFor("New post", action = true)
                        ui.onNodeWithText("New post").performClick()
                        waitForEditor()
                        ui.onNode(hasSetTextAction()).performTextReplacement(text)
                        ui.onNodeWithText("Save draft").performClick()
                        waitFor("Settings", action = true)
                        ui.onNodeWithText("Settings").performClick()
                        ui.onNodeWithText("Privacy and safety").performScrollTo().performClick()
                        waitFor("Draft policies")
                        ui.waitUntil(20_000) {
                            ui.onAllNodes(hasText("Contact publisher") and hasClickAction())
                                .fetchSemanticsNodes().isNotEmpty()
                        }
                        ui.onNodeWithText("Contact publisher").performScrollTo().performClick()
                        waitFor("Publisher support")
                        ui.onNodeWithText("Publisher support is not available in this preview. Contact details are still being prepared.")
                            .assertIsDisplayed()
                        ui.onAllNodes(hasSetTextAction()).assertCountEquals(0)
                        ui.onAllNodesWithText("Compose email").assertCountEquals(0)
                        assertTrue("Unconfigured contact must not hand off to another app",
                            Intents.getIntents().none { it.action in listOf(Intent.ACTION_SENDTO, Intent.ACTION_VIEW) })
                        capture("unconfigured-support-unavailable.png")
                        ui.onNodeWithText("Done").performClick()
                        ui.onNodeWithContentDescription("Close privacy and safety").performClick()
                        waitFor("New post", action = true)
                        ui.onNodeWithText("New post").performClick()
                        waitForEditor()
                        ui.onNode(hasSetTextAction()).assertTextEquals(text)
                        assertTrue("Publisher navigation must preserve complete wallet options",
                            originalWallet.exportJson() == wallets.loadExisting().exportJson())
                        assertEquals(SaitoBackend.OWNER, backends.load())
                        assertTrue(peer.transactions.isEmpty())
                        assertTrue(peer.reports.isEmpty())
                        peer.assertHealthy()
                        capture("unconfigured-support-keeps-draft.png")
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, { Intents.release() }, { retained.restoreFiles() },
                { wallets.replace(wallets.loadExisting(), originalWallet) },
                { backends.save(originalBackend); check(backends.load() == originalBackend) },
                { retained.restorePreferences() })
        }
    }

    private fun waitFor(text: String, action: Boolean = false) = ui.waitUntil(20_000) {
        ui.onNode(if (action) hasText(text) and hasClickAction() and isEnabled() else hasText(text)).isDisplayed()
    }

    private fun waitForEditor() = ui.waitUntil(20_000) {
        ui.onNode(hasSetTextAction() and isEnabled()).isDisplayed()
    }

    private fun capture(name: String) = captureNativeScreen(File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "publisher-routes-evidence/$name"))
}
