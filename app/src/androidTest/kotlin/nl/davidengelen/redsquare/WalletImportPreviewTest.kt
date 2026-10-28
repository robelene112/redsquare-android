package nl.davidengelen.redsquare

import android.content.Intent
import android.app.KeyguardManager
import android.os.SystemClock
import android.provider.DocumentsContract
import android.system.Os
import android.system.OsConstants
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.UiObjectNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicReference
import mockwebserver3.MockWebServer
import mockwebserver3.MockResponse

class WalletImportPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    private fun openWallet(stage: String) {
        withFailureCapture({ captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "wallet-import-evidence/wallet-entry-$stage-failure.png")) }) {
            val action = hasText("Wallet") and hasClickAction() and isEnabled()
            ui.waitUntil(20_000) {
                try { ui.onNode(action).assertIsDisplayed(); true }
                catch (_: AssertionError) { false }
            }
            ui.onNode(action).performClick()
        }
    }

    @Test fun failedSavingThroughConfirmedImportRestoresTheVisibleAndSavedWallet() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertEquals("nl.davidengelen.redsquare.dev", context.packageName)
        val device = UiDevice.getInstance(instrumentation)
        val directory = context.noBackupFilesDir
        val originalMode = Os.stat(directory.path).st_mode and 0xfff
        val server = MockWebServer()
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest) = MockResponse.Builder()
                .body("${System.currentTimeMillis()}-1234-${"a".repeat(64)}.snap\n").build()
        }
        server.start(InetAddress.getByName("127.0.0.1"), 0)

        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedDisabled", true)
                .putExtra("balanceApiUrl", "http://127.0.0.1:${server.port}/balance/")).use { scenario ->
                openWallet("initial")
                val address = displayedWalletAddress(ui)
                val previous = AndroidWalletStore(context).loadExisting()
                ui.onNodeWithText("Import wallet").performClick()
                selectImportRoot(device)
                selectVisibleFile(device, "reference-import.json")
                ui.waitUntil(15_000) { ui.onAllNodes(hasText("Node reports zero SAITO")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                ui.onNodeWithText("Skip backup").performScrollTo().performClick()
                ui.onNodeWithContentDescription("Replacement risks acknowledged").performScrollTo().performClick()
                // Fault at the real filesystem boundary, solely in the isolated
                // app: reads still work, but creation of the recovery file fails.
                Os.chmod(directory.path, OsConstants.S_IRUSR or OsConstants.S_IXUSR)
                ui.onNodeWithText("Confirm replacement").performScrollTo().assertIsEnabled().performClick()
                val failed = "Your wallet could not be imported. Your previous wallet has been restored."
                try {
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText(failed)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                } catch (failure: Throwable) {
                    captureFeedEvidence("wallet-import-evidence/failed-save-first-failure.png")
                    throw failure
                }
                ui.onNodeWithText(failed).performScrollTo().assertIsDisplayed()
                assertEquals(address, displayedWalletAddress(ui))
                ui.onNodeWithText("Import wallet").performScrollTo().assertIsEnabled()
                val recovered = AndroidWalletStore(context).loadExisting()
                assertEquals(address, recovered.publicKey)
                assertTrue("Recovered options must equal the outgoing options",
                    previous.exportJson() == recovered.exportJson())
                captureFeedEvidence("wallet-import-evidence/failed-save-restored.png")
                Os.chmod(directory.path, originalMode)
                ui.onNodeWithText("Done").performClick()
                scenario.recreate()
                openWallet("reopened")
                assertEquals(address, displayedWalletAddress(ui))
                captureFeedEvidence("wallet-import-evidence/failed-save-restored-recreated.png")
            }
        } finally { Os.chmod(directory.path, originalMode); server.close() }
    }

    @Test fun invalidSelectedBackupShowsRecoveryAndRetainsTheCurrentWallet() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .putExtra("feedDisabled", true).putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/")).use { scenario ->
            openWallet("invalid-backup-entry")
            val address = displayedWalletAddress(ui)
            val failed = "That file is not a valid Saito wallet backup. Your wallet is unchanged."
            for (name in listOf("invalid-import.json", "malformed-utf8-import.json")) {
                ui.onNodeWithText("Import wallet").performClick()
                selectImportRoot(device)
                selectVisibleFile(device, name)
                try {
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText(failed)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                } catch (failure: Throwable) {
                    captureFeedEvidence("wallet-import-evidence/invalid-encoding-first-failure.png")
                    throw failure
                }
                ui.onNodeWithText(failed).assertIsDisplayed()
                assertEquals(address, displayedWalletAddress(ui))
                ui.onNodeWithText("Import wallet").assertIsEnabled()
                captureFeedEvidence("wallet-import-evidence/$name-rejected.png")
            }
            ui.onNodeWithText("Done").performClick()
            scenario.recreate()
            openWallet("invalid-backup-recreated-entry")
            assertEquals(address, displayedWalletAddress(ui))
            captureFeedEvidence("wallet-import-evidence/invalid-backup-recovery.png")
        }
    }

    @Test fun selectedValidBackupsPreviewIdentityAndRestoreConfirmedSameWallet() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        assertFalse(keyguard.isDeviceSecure)
        device.executeShellCommand("locksettings set-pin --user 0 $EXPORT_TEST_PIN")
        try {
            ui.waitUntil(10_000) { keyguard.isDeviceSecure }
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedDisabled", true).putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/")).use {
                openWallet("valid-backup-entry")
                val address = displayedWalletAddress(ui)
                val original = "same-import-${UUID.randomUUID()}.json"
                ui.onNodeWithText("Export wallet").performClick()
                chooseTestBackup(device, original)
                enterRealSystemPin(device)
                ui.waitUntil(15_000) { ui.onAllNodes(hasText("Wallet backup saved")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                // Second identity is published scalar1 from the independent Saito reference.
                // Never fund it. Neither fixture nor backup bytes are retained as evidence.
                val cases = listOf(
                    Triple(original, "Restore this wallet", address),
                    Triple("reference-import.json", "Replace your wallet", "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR"),
                )
                for ((name, title, expected) in cases) {
                    ui.onNodeWithText("Import wallet").performScrollTo().performClick()
                    selectImportRoot(device)
                    selectVisibleFile(device, name)
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText(title)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.onNodeWithText(title).assertIsDisplayed()
                    ui.onNodeWithContentDescription("Selected wallet address").assertTextEquals(expected)
                    assertEquals(address, displayedWalletAddress(ui))
                    captureFeedEvidence("wallet-import-evidence/valid-preview-${if (name == original) "same" else "different"}.png")
                    if (name == original) {
                        ui.onNodeWithText("Confirm restore").performScrollTo().assertIsNotEnabled()
                        ui.onNodeWithContentDescription("Replacement risks acknowledged").performScrollTo().performClick()
                        ui.onNodeWithText("Confirm restore").performScrollTo().assertIsEnabled().performClick()
                        ui.waitUntil(15_000) { ui.onAllNodes(hasText("Wallet imported")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                        assertEquals(address, displayedWalletAddress(ui))
                        captureFeedEvidence("wallet-import-evidence/confirmed-same-wallet-restore.png")
                    }
                }
                DocumentsContract.deleteDocument(context.contentResolver,
                    DocumentsContract.buildDocumentUri(ExportTestDocumentsProvider.AUTHORITY, original))
            }
        } finally { device.executeShellCommand("locksettings clear --old $EXPORT_TEST_PIN --user 0") }
    }

    @Test fun replacementRequiresANewBackupBoundToTheCurrentSelection() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        assertFalse(keyguard.isDeviceSecure)
        device.executeShellCommand("locksettings set-pin --user 0 $EXPORT_TEST_PIN")
        val oldBackup = "before-import-${UUID.randomUUID()}.json"
        val freshBackup = "guarded-import-${UUID.randomUUID()}.json"
        try {
            ui.waitUntil(10_000) { keyguard.isDeviceSecure }
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedDisabled", true).putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/")).use {
                openWallet("replacement-backup")
                val address = displayedWalletAddress(ui)
                ui.onNodeWithText("Export wallet").performClick()
                chooseTestBackup(device, oldBackup)
                enterRealSystemPin(device)
                ui.waitUntil(15_000) { ui.onAllNodes(hasText("Wallet backup saved")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                ui.onNodeWithText("Import wallet").performClick()
                selectImportRoot(device)
                selectVisibleFile(device, "reference-import.json")
                ui.waitUntil(15_000) { ui.onAllNodes(hasText("Replace your wallet")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                // A saved backup made before selection must not authorize replacement.
                val required = "Back up your current wallet before continuing with replacement."
                val saved = "Current wallet backed up for this replacement"
                ui.onNodeWithText(required).performScrollTo().assertIsDisplayed()
                assertEquals(address, displayedWalletAddress(ui))
                ui.onNodeWithText("Back up current wallet").performScrollTo().performClick()
                chooseTestBackup(device, freshBackup)
                enterRealSystemPin(device)
                ui.waitUntil(15_000) { ui.onAllNodes(hasText(saved)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                ui.onNodeWithText(saved).performScrollTo().assertIsDisplayed()
                ui.onNodeWithText(required).assertDoesNotExist()
                assertEquals(address, displayedWalletAddress(ui))
                captureFeedEvidence("wallet-import-evidence/fresh-candidate-backup.png")
                // Selecting again invalidates proof even when the file's identity is the same.
                ui.onNodeWithText("Back").performClick()
                ui.onNodeWithText("Import wallet").performScrollTo().performClick()
                selectImportRoot(device)
                selectVisibleFile(device, "reference-import.json")
                ui.waitUntil(15_000) { ui.onAllNodes(hasText("Replace your wallet")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                ui.onNodeWithText(required).performScrollTo().assertIsDisplayed()
                ui.onNodeWithText(saved).assertDoesNotExist()
                assertEquals(address, displayedWalletAddress(ui))
                captureFeedEvidence("wallet-import-evidence/reselected-backup-required.png")
            }
        } finally {
            for (name in listOf(oldBackup, freshBackup)) runCatching {
                DocumentsContract.deleteDocument(context.contentResolver,
                    DocumentsContract.buildDocumentUri(ExportTestDocumentsProvider.AUTHORITY, name))
            }
            device.executeShellCommand("locksettings clear --old $EXPORT_TEST_PIN --user 0")
        }
    }

    @Test fun onlyAFreshCompleteOutgoingBalanceAllowsSkippingTheRecommendedBackup() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val kind = AtomicReference("positive")
        val requestedAddress = AtomicReference<String?>(null)
        val server = MockWebServer()
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse {
                val publicKey = request.url.pathSegments.last()
                requestedAddress.set(publicKey)
                val timestamp = if (kind.get() == "stale") 1L else System.currentTimeMillis()
                val header = "$timestamp-1234-${"a".repeat(64)}.snap\n"
                val body = when (kind.get()) {
                    "positive" -> header + "$publicKey 1234 0 0 1 0\n"
                    "malformed" -> header + "not a complete balance row\n"
                    else -> header
                }
                return MockResponse.Builder().body(body).addHeader("Content-Type", "text/plain").build()
            }
        }
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedDisabled", true)
                .putExtra("balanceApiUrl", "http://127.0.0.1:${server.port}/balance/")).use {
                openWallet("balance-cases-entry")
                val address = displayedWalletAddress(ui)
                val cases = listOf(
                    Triple("positive", "Your current wallet has SAITO", false),
                    Triple("zero", "Node reports zero SAITO", true),
                    Triple("malformed", "Balance could not be checked", false),
                    Triple("stale", "Balance could not be checked", false),
                )
                for ((responseKind, status, skippable) in cases) {
                    kind.set(responseKind)
                    requestedAddress.set(null)
                    ui.onNodeWithText("Import wallet").performScrollTo().performClick()
                    selectImportRoot(device)
                selectVisibleFile(device, "reference-import.json")
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText(status)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.onNodeWithText(status).performScrollTo().assertIsDisplayed()
                    assertEquals(address, requestedAddress.get())
                    if (skippable) {
                        ui.onNodeWithText("Skip backup").performScrollTo().assertIsEnabled()
                        ui.onNodeWithText("A backup is strongly recommended, even with zero SAITO.").assertExists()
                        ui.onNodeWithText("Skip backup").performClick()
                        ui.onNodeWithText("Backup skipped for this replacement").assertExists()
                    } else {
                        ui.onNodeWithText("Skip backup").assertDoesNotExist()
                        ui.onNodeWithText("Back up your current wallet before continuing with replacement.").assertExists()
                    }
                    assertEquals(address, displayedWalletAddress(ui))
                    captureFeedEvidence("wallet-import-evidence/balance-$responseKind.png")
                    ui.onNodeWithText("Back").performClick()
                }
            }
        } finally { server.close() }
    }

    @Test fun aStalledBackupProviderReturnsControlAndCannotOverwriteAFreshPreview() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .putExtra("feedDisabled", true).putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/")).use {
            openWallet("stalled-provider")
            val address = displayedWalletAddress(ui)
            ui.onNodeWithText("Import wallet").performClick()
            selectImportRoot(device)
            selectVisibleFile(device, "stalled-read-import.json")
            val chosenAt = SystemClock.uptimeMillis()
            val failure = "Your wallet backup could not be opened. Try again with another file."
            ui.waitUntil(40_000) { ui.onAllNodes(hasText(failure)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNodeWithText(failure).assertIsDisplayed()
            assertEquals(address, displayedWalletAddress(ui))
            ui.onNodeWithText("Import wallet").assertIsEnabled().performClick()
            selectImportRoot(device)
            selectVisibleFile(device, "reference-import.json")
            ui.waitUntil(15_000) { ui.onAllNodes(hasText("Replace your wallet")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            // The controlled provider releases its stalled pipe after60 seconds.
            // Observe the new selection past that point, never the private bytes.
            ui.waitUntil(70_000) { SystemClock.uptimeMillis() >= chosenAt + 65_000 }
            ui.onNodeWithText("Replace your wallet").assertExists()
            ui.onNodeWithContentDescription("Selected wallet address")
                .assertTextEquals("jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR")
            ui.onNodeWithText(failure).assertDoesNotExist()
            assertEquals(address, displayedWalletAddress(ui))
            captureFeedEvidence("wallet-import-evidence/stalled-provider-fresh-preview.png")
        }
    }

    @Test fun explicitlyConfirmedReplacementPersistsWithoutAdoptingImportedPeers() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val device = UiDevice.getInstance(instrumentation)
        val wallets = AndroidWalletStore(context)
        val backends = nl.davidengelen.redsquare.saito.AndroidBackendStore(context)
        val originalBackend = backends.load()
        var originalWallet: nl.davidengelen.redsquare.saito.SaitoWallet? = null
        var primary: Throwable? = null
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        assertFalse(keyguard.isDeviceSecure)
        val originalFile = "activation-original-${UUID.randomUUID()}.json"
        val incomingAddress = "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR"
        val server = MockWebServer()
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest) = MockResponse.Builder()
                .body("${System.currentTimeMillis()}-1234-${"a".repeat(64)}.snap\n").build()
        }
        try {
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            device.executeShellCommand("locksettings set-pin --user 0 $EXPORT_TEST_PIN")
            ui.waitUntil(10_000) { keyguard.isDeviceSecure }
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedDisabled", true)
                .putExtra("balanceApiUrl", "http://127.0.0.1:${server.port}/balance/")).use { scenario ->
                withFailureCapture({
                    val evidence = File(context.cacheDir, "wallet-import-evidence")
                    cleanupAfterTest(null,
                        { captureNativeScreen(File(evidence, "activation-first-failure.png")) },
                        { File(evidence.apply { mkdirs() }, "activation-failure-state.txt").writeText(
                            "selectedBackend=${backends.load().id}\n") },
                        { captureActivationWindow(evidence) },
                        { File(evidence, "activation-app-logcat.txt").writeText(device.executeShellCommand(
                            "timeout 3 logcat -d -v threadtime --pid=${android.os.Process.myPid()} -t 300")) })
                }) {
                    openWallet("activation-entry")
                    val originalAddress = displayedWalletAddress(ui)
                    originalWallet = wallets.loadExisting()
                    ui.onNodeWithText("Export wallet").performClick()
                    chooseTestBackup(device, originalFile)
                    enterRealSystemPin(device)
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Wallet backup saved")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.onNodeWithText("Import wallet").performClick()
                    selectImportRoot(device)
                    selectVisibleFile(device, "reference-import.json")
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Node reports zero SAITO")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.onNodeWithText("Skip backup").performScrollTo().performClick()
                    ui.onNodeWithText("Confirm replacement").performScrollTo().assertIsNotEnabled()
                    assertEquals(originalAddress, displayedWalletAddress(ui))
                    ui.onNodeWithContentDescription("Replacement risks acknowledged").performScrollTo().performClick()
                    ui.onNodeWithText("Confirm replacement").performScrollTo().assertIsEnabled().performClick()
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Wallet imported")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    assertEquals(incomingAddress, displayedWalletAddress(ui))
                    captureFeedEvidence("wallet-import-evidence/activated-wallet.png")
                    ui.onNodeWithText("Done").performClick()
                    ui.waitUntil(15_000) { ui.onNodeWithText("Node: saito.davidengelen.nl").isDisplayed() }
                    ui.onNodeWithText("Node: saito.davidengelen.nl").assertIsDisplayed()
                    captureNativeScreen(File(context.cacheDir, "wallet-import-evidence/activation-owner-feed-visible.png"))
                    scenario.recreate()
                    ui.onNodeWithText("Wallet").performClick()
                    assertEquals(incomingAddress, displayedWalletAddress(ui))
                    captureFeedEvidence("wallet-import-evidence/activated-wallet-recreated.png")
                    ui.onNodeWithText("Done").performClick()
                    ui.onNodeWithText("Settings").performClick()
                    ui.onNodeWithText("Node").performScrollTo().performClick()
                    ui.onNodeWithText("saito.io").performScrollTo().performClick()
                    ui.waitUntil(10_000) { ui.onAllNodes(hasText("Node: saito.io")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.onNodeWithText("Wallet").performClick()
                    assertEquals(incomingAddress, displayedWalletAddress(ui))
                    // Restore the outgoing identity through the same real UI so this
                    // test leaves subsequent tests' original wallet intact.
                    ui.onNodeWithText("Import wallet").performClick()
                    selectImportRoot(device)
                    selectVisibleFile(device, originalFile)
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Node reports zero SAITO")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.onNodeWithText("Skip backup").performScrollTo().performClick()
                    ui.onNodeWithContentDescription("Replacement risks acknowledged").performScrollTo().performClick()
                    ui.onNodeWithText("Confirm replacement").performScrollTo().performClick()
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Wallet imported")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    assertEquals(originalAddress, displayedWalletAddress(ui))
                    ui.onNodeWithText("Done").performClick()
                    scenario.recreate()
                    ui.onNodeWithText("Wallet").performClick()
                    assertEquals(originalAddress, displayedWalletAddress(ui))
                    ui.onNodeWithText("Done").performClick()
                    ui.onNodeWithText("Settings").performClick()
                    ui.onNodeWithText("Node").performScrollTo().performClick()
                    ui.onNodeWithText("saito.davidengelen.nl").performScrollTo().performClick()
                    ui.waitUntil(10_000) { ui.onAllNodes(hasText("Node: saito.davidengelen.nl")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            // This restoration is independent of the successful UI round-trip above.
            // A failed assertion must not make later replacement cases same-wallet cases.
            cleanupAfterTest(primary,
                { originalWallet?.let { original ->
                    wallets.replace(wallets.loadExisting(), original)
                    check(wallets.loadExisting().exportJson() == original.exportJson())
                } },
                { backends.save(originalBackend); check(backends.load() == originalBackend) },
                { val document = File(instrumentation.context.filesDir, "wallet-test-documents/$originalFile")
                    check(!document.exists() || DocumentsContract.deleteDocument(context.contentResolver,
                        DocumentsContract.buildDocumentUri(ExportTestDocumentsProvider.AUTHORITY, originalFile))) },
                { device.executeShellCommand("locksettings clear --old $EXPORT_TEST_PIN --user 0")
                    ui.waitUntil(10_000) { !keyguard.isDeviceSecure } },
                { server.close() })
        }
    }

    /** Failure-only observation. Never refresh, recreate or dismiss the failed window. */
    private fun captureActivationWindow(folder: File) {
        val main = android.os.Looper.getMainLooper()
        File(folder, "activation-main-thread.txt").writeText("state=${main.thread.state}\n" +
            main.thread.stackTrace.joinToString("\n"))
        val observation = java.util.concurrent.FutureTask {
            val resumed = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
            "uptime=${SystemClock.uptimeMillis()} resumedActivities=${resumed.size}\n" + resumed.joinToString("\n") { activity ->
                val decor = activity.window.decorView
                val frame = android.graphics.Rect().also { decor.getWindowVisibleDisplayFrame(it) }
                val insets = androidx.core.view.ViewCompat.getRootWindowInsets(decor)
                val ime = androidx.core.view.WindowInsetsCompat.Type.ime()
                val bars = androidx.core.view.WindowInsetsCompat.Type.systemBars()
                val content = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
                listOf("activity=${activity.javaClass.name}@${System.identityHashCode(activity)}",
                    "changingConfigurations=${activity.isChangingConfigurations} finishing=${activity.isFinishing} destroyed=${activity.isDestroyed}",
                    "windowFocus=${decor.hasWindowFocus()} attached=${decor.isAttachedToWindow} shown=${decor.isShown}",
                    "decor=${decor.width}x${decor.height} visibleFrame=$frame contentChildren=${content?.childCount}",
                    "focusedView=${activity.currentFocus?.javaClass?.name}",
                    "imeVisible=${insets?.isVisible(ime)} imeInsets=${insets?.getInsets(ime)} systemBars=${insets?.getInsets(bars)}")
                    .joinToString("\n")
            }
        }
        android.os.Handler(main).post(observation)
        val text = try { observation.get(2, java.util.concurrent.TimeUnit.SECONDS) }
            catch (failure: Exception) { "Window observation unavailable: ${failure.javaClass.simpleName}" }
            finally { observation.cancel(false) }
        File(folder, "activation-window-state.txt").writeText(text)
    }

    private fun selectImportRoot(device: UiDevice) {
        val row = By.text(ExportTestDocumentsProvider.TITLE).res("android", "title")
        val title = By.text(ExportTestDocumentsProvider.TITLE)
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (!device.hasObject(row) && device.hasObject(title)) return
            try {
                val entry = device.findObject(row)
                if (entry != null) {
                    entry.click()
                    device.wait(Until.gone(row), 2_000)
                } else device.findObject(By.desc("Show roots"))?.click()
            } catch (_: StaleObjectException) { /* Observe the current picker after its layout changes. */ }
            SystemClock.sleep(100)
        }
        captureFeedEvidence("wallet-import-evidence/root-selection-failure.png")
        error("Test backup folder did not become the visible picker location")
    }

    private fun selectVisibleFile(device: UiDevice, name: String) {
        val selector = By.text(name)
        if (device.wait(Until.hasObject(selector), 2_000) != true) {
            try { UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().text(name)) }
            catch (_: UiObjectNotFoundException) { /* Retain the actual picker if the file remains unavailable. */ }
        }
        val file = device.wait(Until.findObject(selector), 10_000)
        if (file == null) captureFeedEvidence("wallet-import-evidence/file-selection-failure.png")
        requireNotNull(file).click()
    }
}
