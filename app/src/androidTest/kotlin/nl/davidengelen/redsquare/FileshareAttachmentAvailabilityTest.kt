package nl.davidengelen.redsquare

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.google.gson.JsonObject
import nl.davidengelen.redsquare.chat.ChatWorkStore
import nl.davidengelen.redsquare.fileshare.FileSession
import nl.davidengelen.redsquare.fileshare.FileshareRuntime
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Real Chat and Fileshare with only the Fileshare directory-read boundary held during initial loading. */
class FileshareAttachmentAvailabilityTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun initialLoadingExplainsOnlyTheFileRestrictionAndReadyOpensTheRealPickerOnce() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val author = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Published unsafe fixtures only.
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2").withChatContact(author.publicKey)
        val wallets = AndroidWalletStore(context)
        val previous = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context)
        val previousBackend = backends.load()
        val retained = ChatFixtureState(context, listOf("fileshare.", "personal.${wallet.publicKey}.",
            "chat.owner.${wallet.publicKey}.", "chatwork.owner.${wallet.publicKey}.", "chatreactions.owner.${wallet.publicKey}."), wallet.publicKey)
        val gate = InitialDirectoryRead(context)
        val device = UiDevice.getInstance(instrumentation)
        val evidence = File(context.cacheDir, "file-menu-evidence").apply { mkdirs() }
        val draft = "Keep this unsent file menu draft"
        var session: FileSession? = null
        var intentsReady = false
        var primary: Throwable? = null
        try {
            retained.prepare()
            wallets.replace(previous, wallet)
            backends.save(SaitoBackend.OWNER)
            val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = { "[]" })
            peer.start().use { server ->
                val endpoint = "ws://127.0.0.1:${server.port}/wsopen"
                session = FileshareRuntime.session(gate, wallet, SaitoBackend.OWNER, endpoint, author.publicKey)
                assertTrue("The real initial Fileshare directory read must be held", gate.entered.await(5, TimeUnit.SECONDS))
                Intents.init(); intentsReady = true
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/")
                    .putExtra("feedPeerUrl", endpoint).putExtra("feedOfficialPeerUrl", endpoint)).use {
                    withFailureCapture({ captureNativeScreen(File(evidence, "loading-picker-failure.png")) }) {
                        ui.waitUntil(15_000) { ui.onNodeWithText("Chat").isDisplayed() }
                        ui.onNodeWithText("Chat").performClick()
                        ui.waitUntil(15_000) { ui.onNodeWithText(author.publicKey).isDisplayed() }
                        ui.onNodeWithText(author.publicKey).performClick()
                        val editor = hasText("Message") and hasSetTextAction() and isEnabled()
                        ui.waitUntil(15_000) { ui.onNode(editor).isDisplayed() }
                        ui.onNode(editor).performTextInput(draft)
                        assertFalse(session!!.ready.value)
                        ui.onNodeWithContentDescription("Add media").performClick()
                        ui.onNodeWithText("Send a file").assertIsDisplayed().assertIsNotEnabled()
                        ui.onNodeWithText("Loading file sharing…").assertIsDisplayed()
                        ui.onNodeWithText("Add photo").assertIsEnabled()
                        ui.onNodeWithText("Add GIF").assertIsEnabled()
                        captureNativeScreen(File(evidence, "initial-loading-menu.png"))
                        device.pressBack()
                        ui.waitUntil(5_000) { ui.onAllNodesWithText("Send a file").fetchSemanticsNodes().isEmpty() }
                        ui.onNode(editor).assertIsEnabled().assertTextContains(draft)
                        gate.release()
                        ui.waitUntil(10_000) { session!!.ready.value }
                        ui.onNodeWithContentDescription("Add media").performClick()
                        ui.onNodeWithText("Send a file").assertIsEnabled().performClick()
                        ui.waitUntil(10_000) { device.currentPackageName in setOf("com.android.documentsui", "com.google.android.documentsui") }
                        Intents.intended(hasAction(Intent.ACTION_OPEN_DOCUMENT), androidx.test.espresso.intent.VerificationModes.times(1))
                        assertTrue(device.hasObject(By.pkg(device.currentPackageName)))
                        captureNativeScreen(File(evidence, "real-document-picker.png"))
                        device.pressBack()
                        ui.waitUntil(10_000) { device.currentPackageName == context.packageName && ui.onNode(editor).isDisplayed() }
                        ui.onNode(editor).assertTextContains(draft)
                        assertEquals(draft, ChatWorkStore(context, wallet, SaitoBackend.OWNER, author.publicKey).load().draft)
                        assertNull(session!!.preparation.value)
                        assertTrue(session!!.transfers.value.isEmpty())
                        assertTrue(peer.transactions.isEmpty())
                        peer.assertHealthy()
                        Intents.intended(hasAction(Intent.ACTION_OPEN_DOCUMENT), androidx.test.espresso.intent.VerificationModes.times(1))
                        captureNativeScreen(File(evidence, "picker-cancelled-draft-retained.png"))
                        File(evidence, "loading-and-picker.json").writeText(JsonObject().apply {
                            addProperty("realInitialFileshareLoadHeld", true)
                            addProperty("fileRestrictionExplained", true)
                            addProperty("photoAndGifEnabled", true)
                            addProperty("realAndroidDocumentPickerObserved", true)
                            addProperty("openDocumentIntents", 1)
                            addProperty("encryptedDraftRetained", true)
                            addProperty("outgoingTransactions", peer.transactions.size)
                        }.toString())
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            gate.release()
            session?.interrupt()
            cleanupAfterTest(primary,
                { if (intentsReady) Intents.release() },
                { ui.waitUntil(10_000) { session?.let { it.ready.value && !it.ownsDelivery && !it.foregroundOwned } != false } },
                { retained.restoreFiles() }, { retained.restorePreferences() },
                { val current = wallets.loadExisting(); check(current.publicKey == wallet.publicKey || current.exportJson() == previous.exportJson())
                    if (current.exportJson() != previous.exportJson()) wallets.replace(current, previous)
                    check(wallets.loadExisting().exportJson() == previous.exportJson()) },
                { backends.save(previousBackend); check(backends.load() == previousBackend) })
        }
    }

    /** Gate actual background Fileshare directory IO before it acquires the shared encrypted-store lock. */
    private class InitialDirectoryRead(base: Context) : ContextWrapper(base) {
        private val constructorThread = Thread.currentThread()
        private val held = AtomicBoolean(true)
        val entered = CountDownLatch(1)
        private val released = CountDownLatch(1)
        override fun getApplicationContext(): Context = this
        override fun getNoBackupFilesDir(): File {
            if (Thread.currentThread() !== constructorThread && held.get()) {
                entered.countDown()
                check(released.await(45, TimeUnit.SECONDS)) { "Initial Fileshare IO gate was not released" }
            }
            return super.getNoBackupFilesDir()
        }
        fun release() { held.set(false); released.countDown() }
    }
}
