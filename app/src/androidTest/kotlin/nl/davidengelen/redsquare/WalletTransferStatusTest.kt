package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File
import java.net.InetAddress
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.math.BigInteger
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import nl.davidengelen.redsquare.saito.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WalletTransferStatusTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun pendingPaymentStaysReservedAcrossBackendsAndReturnsOnlyWithItsOriginalWallet() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(instrumentation.context.packageName == "nl.davidengelen.redsquare.dev.test")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val fixture = instrumentation.context.assets.open("native-block-reference.json")
            .bufferedReader().use { TransferInclusionFixture(it.readText()) }
        val incoming = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        assertEquals("pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU", incoming.publicKey)
        val wallets = AndroidWalletStore(context)
        val prior = wallets.loadOrCreate()
        val funded = AtomicBoolean(true)
        val positiveReads = AtomicInteger()
        val rows = fixture.payment.inputs.joinToString("\n", postfix = "\n") {
            "${fixture.wallet.publicKey} ${it.block} ${it.ordinal} ${it.index} ${it.amount} ${it.type}"
        }
        context.noBackupFilesDir.listFiles()?.filter { it.name.startsWith("transfer.") }?.forEach { check(it.delete()) }
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        var primaryFailure: Throwable? = null
        try {
            wallets.replace(prior, fixture.wallet)
            // Independent pre-existing signed candidate; this journey never authenticates or sends money.
            TransferStore(context, fixture.wallet.publicKey).reserve(SaitoBackend.OWNER, fixture.payment)
            val owner = ControlledFeedPeer("[]")
            val official = ControlledFeedPeer("[]")
            MockWebServer().use { server ->
                server.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        if (request.target == "/options") return MockResponse.Builder()
                            .body("""{"consensus":{"genesis_period":80640}}""").build()
                        val key = request.target.removePrefix("/balance/")
                        if (request.target != "/balance/$key" || !SaitoWallet.isPublicKey(key))
                            return MockResponse.Builder().code(404).build()
                        val slips = if (key == fixture.wallet.publicKey && funded.get()) {
                            positiveReads.incrementAndGet(); rows
                        } else ""
                        return MockResponse.Builder().body(
                            "${System.currentTimeMillis()}-2000003-${"c".repeat(64)}.snap\n" + slips).build()
                    }
                }
                server.start(InetAddress.getByName("127.0.0.1"), 0)
                owner.start().use { ownerTransport -> official.start().use { officialTransport ->
                    ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                        .putExtra("feedDisabled", true)
                        .putExtra("feedPeerUrl", "ws://127.0.0.1:${ownerTransport.port}/wsopen")
                        .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${officialTransport.port}/wsopen")
                        .putExtra("balanceApiUrl", "http://127.0.0.1:${server.port}/balance/")).use { scenario ->
                        withFailureCapture({ captureNativeScreen(File(context.cacheDir,
                            "transfer-evidence/isolation-first-failure.png")) }) {
                            val pending = "Pending. Inclusion has not been verified."
                            for ((index, backend) in listOf(SaitoBackend.OWNER, SaitoBackend.OFFICIAL,
                                SaitoBackend.OFFICIAL, SaitoBackend.OWNER).withIndex()) {
                                if (index == 2) scenario.recreate()
                                else if (index > 0) {
                                    ui.onNodeWithText("Settings").performClick()
                                    ui.onNodeWithText(backend.domain).performClick()
                                }
                                ui.waitUntil(15_000) { ui.onNodeWithText("Server: ${backend.domain}").isDisplayed() }
                                ui.onNodeWithText("Wallet").performClick()
                                ui.onNodeWithContentDescription("Wallet address").assertTextEquals(fixture.wallet.publicKey)
                                val previousReads = positiveReads.get()
                                ui.onNodeWithText("Send SAITO").performScrollTo().performClick()
                                ui.waitUntil(15_000) { positiveReads.get() > previousReads &&
                                    ui.onNodeWithText("Available to send: 0 SAITO").isDisplayed() }
                                ui.onNodeWithText(pending).assertIsDisplayed()
                                ui.onNodeWithText("Transfer server: saito.davidengelen.nl.").assertIsDisplayed()
                                if (backend == SaitoBackend.OWNER) ui.onNodeWithText("Retry transfer").assertIsEnabled()
                                else ui.onNodeWithText("Retry transfer").assertIsNotEnabled()
                                owner.assertHealthy(); official.assertHealthy()
                                assertTrue(owner.transactions.isEmpty() && official.transactions.isEmpty())
                                captureNativeScreen(File(context.cacheDir, "transfer-evidence/isolation-backend-$index.png"), pending)
                                closeTransferAndWallet()
                            }
                            funded.set(false) // Actual fresh zero permits optional backup skipping, never auth bypass.
                            replaceWalletThroughPicker(incoming, "00-transfer-import-2.json", expectPendingWork = true)
                            ui.onNodeWithText("Send SAITO").performScrollTo().performClick()
                            ui.waitUntil(15_000) { ui.onNodeWithText("Available to send: 0 SAITO").isDisplayed() }
                            ui.onAllNodesWithText(pending).assertCountEquals(0)
                            ui.onAllNodesWithText("Retry transfer").assertCountEquals(0)
                            owner.assertHealthy(); official.assertHealthy()
                            assertTrue(owner.transactions.isEmpty() && official.transactions.isEmpty())
                            captureNativeScreen(File(context.cacheDir, "transfer-evidence/isolation-incoming.png"),
                                "Available to send: 0 SAITO")
                            closeTransferAndWallet()
                            replaceWalletThroughPicker(fixture.wallet, "00-transfer-import-1.json", expectPendingWork = false)
                            funded.set(true)
                            reopenTransfers(scenario, fixture.wallet.publicKey)
                            ui.waitUntil(15_000) { ui.onNodeWithText(pending).isDisplayed() }
                            ui.onNodeWithText("Available to send: 0 SAITO").assertIsDisplayed()
                            ui.onNodeWithText("Transfer server: saito.davidengelen.nl.").assertIsDisplayed()
                            ui.onNodeWithText("Retry transfer").assertIsEnabled()
                            assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
                            owner.assertHealthy(); official.assertHealthy()
                            assertTrue(owner.transactions.isEmpty() && official.transactions.isEmpty())
                            captureNativeScreen(File(context.cacheDir, "transfer-evidence/isolation-restored.png"), pending)
                        }
                    }
                } }
            }
        } catch (failure: Throwable) { primaryFailure = failure; throw failure }
        finally {
            cleanupAfterTest(primaryFailure, {
                val current = wallets.loadExisting()
                when (current.exportJson()) {
                    prior.exportJson() -> Unit
                    fixture.wallet.exportJson(), incoming.exportJson() -> wallets.replace(current, prior)
                    else -> error("Unexpected transfer isolation fixture wallet state")
                }
                check(wallets.loadExisting().exportJson() == prior.exportJson())
            }, {
                removeOwnedDocument("00-transfer-import-1.json", fixture.wallet.exportJson())
            }, {
                removeOwnedDocument("00-transfer-import-2.json", incoming.exportJson())
            })
        }
    }

    private fun closeTransferAndWallet() {
        ui.onNode(hasText("Done") and hasAnyAncestor(isDialog() and
            hasAnyDescendant(hasText("Available to send: 0 SAITO")))).performClick()
        ui.onNodeWithText("Done").performClick()
    }

    private fun replaceWalletThroughPicker(incoming: SaitoWallet, filename: String, expectPendingWork: Boolean) {
        // The standalone provider is a different UID. The host provisions only these unsafe fixtures.
        val device = UiDevice.getInstance(instrumentation)
        check(device.executeShellCommand("run-as nl.davidengelen.redsquare.dev.test stat -c %s files/wallet-test-documents/$filename")
            .trim().toLongOrNull()?.let { it > 0 } == true)
        ui.onNodeWithText("Wallet").performClick()
        ui.onNodeWithText("Import wallet").performScrollTo().performClick()
        chooseWalletImport(ui, filename, incoming.publicKey)
        ui.waitUntil(15_000) { ui.onAllNodesWithText("Server reports zero SAITO")
            .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        ui.onNodeWithText("Server reports zero SAITO").performScrollTo().assertIsDisplayed()
        if (expectPendingWork) ui.onNodeWithText("Pending work belongs to your current wallet")
            .performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Confirm replacement").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithText("Skip backup").performScrollTo().performClick()
        ui.onNodeWithText("Confirm replacement").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithContentDescription("Replacement risks acknowledged").performScrollTo().performClick()
        ui.onNodeWithText("Confirm replacement").performScrollTo().assertIsEnabled().performClick()
        ui.waitUntil(15_000) { ui.onNodeWithText("Wallet imported").isDisplayed() }
        ui.onNodeWithContentDescription("Wallet address").assertTextEquals(incoming.publicKey)
    }

    private fun removeOwnedDocument(filename: String, knownContents: String) {
        val device = UiDevice.getInstance(instrumentation)
        val expected = java.security.MessageDigest.getInstance("SHA-256")
            .digest(knownContents.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
        val observed = device.executeShellCommand(
            "run-as nl.davidengelen.redsquare.dev.test sha256sum files/wallet-test-documents/$filename")
            .trim().substringBefore(' ')
        check(observed == expected) // Preserve unexpected contents rather than deleting an unknown file.
        check(device.executeShellCommand(
            "run-as nl.davidengelen.redsquare.dev.test rm files/wallet-test-documents/$filename").isBlank())
    }

    @Test fun exactInclusionUnknownHistoryAndVerifiedReorganizationNeverAutomaticallyResend() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val fixture = instrumentation.context.assets.open("native-block-reference.json")
            .bufferedReader().use { TransferInclusionFixture(it.readText()) }
        assertEquals(BigInteger("750000000"), fixture.payment.inputs.fold(BigInteger.ZERO) { sum, input -> sum + input.amount })
        val phase = AtomicInteger(0) // Included, unavailable history, then verified alternative.
        val positiveSnapshots = AtomicInteger()
        val unavailableBlockReads = AtomicInteger()
        val alternateBlockReads = AtomicInteger()
        val changeSnapshots = AtomicInteger()
        val unavailableTip = "c".repeat(64) // A newly advertised tip with no available block body.
        val originalInputs = fixture.payment.inputs.joinToString("\n", postfix = "\n") {
            "${fixture.wallet.publicKey} ${it.block} ${it.ordinal} ${it.index} ${it.amount} ${it.type}"
        }
        val wallets = AndroidWalletStore(context)
        val priorWallet = wallets.loadOrCreate()
        context.noBackupFilesDir.listFiles()?.filter { it.name.startsWith("transfer.") }?.forEach { check(it.delete()) }
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        var primaryFailure: Throwable? = null
        try {
            wallets.replace(priorWallet, fixture.wallet)
            // Pre-existing independently signed candidate, not an authentication/send bypass.
            TransferStore(context, fixture.wallet.publicKey).reserve(SaitoBackend.OWNER, fixture.payment)
            val peer = ControlledFeedPeer("[]")
            MockWebServer().use { server ->
                server.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse = when (request.target) {
                        "/options" -> MockResponse.Builder().body("""{"consensus":{"genesis_period":80640}}""").build()
                        "/balance/${fixture.wallet.publicKey}" -> {
                            val currentPhase = phase.get()
                            if (currentPhase > 0) positiveSnapshots.incrementAndGet()
                            val header = when (currentPhase) {
                                0 -> "${fixture.timestamp}-2000003-${fixture.tip.first}.snap\n"
                                1 -> "${fixture.timestamp + 1000}-2000004-$unavailableTip.snap\n"
                                2 -> "${fixture.timestamp + 2000}-2000003-${fixture.alternateTip.first}.snap\n"
                                else -> "${fixture.timestamp}-2000003-${fixture.tip.first}.snap\n"
                            }
                            val slips = when (currentPhase) {
                                0 -> ""
                                3 -> {
                                    changeSnapshots.incrementAndGet()
                                    "${fixture.wallet.publicKey} 2000002 1 1 415000000 0\n"
                                }
                                else -> originalInputs
                            }
                            MockResponse.Builder().body(header + slips).build()
                        }
                        else -> {
                            if (phase.get() == 1 && request.target == "/block/$unavailableTip")
                                unavailableBlockReads.incrementAndGet()
                            if (phase.get() == 2 && request.target == "/block/${fixture.alternate.first}")
                                alternateBlockReads.incrementAndGet()
                            fixture.blocks[request.target.removePrefix("/block/")]?.takeIf { phase.get() != 1 }?.let {
                                MockResponse.Builder().body(Buffer().write(it)).build()
                            } ?: MockResponse.Builder().code(404).build()
                        }
                    }
                }
                server.start(InetAddress.getByName("127.0.0.1"), 0)
                peer.start().use { transport ->
                    ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                        .putExtra("feedDisabled", true)
                        .putExtra("feedPeerUrl", "ws://127.0.0.1:${transport.port}/wsopen")
                        .putExtra("balanceApiUrl", "http://127.0.0.1:${server.port}/balance/")).use { scenario ->
                        withFailureCapture({ captureNativeScreen(File(context.cacheDir,
                            "transfer-evidence/inclusion-first-failure.png")) }) {
                            ui.onNodeWithText("Wallet").performClick()
                            ui.onNodeWithContentDescription("Wallet address").assertTextEquals(fixture.payment.author)
                            ui.onNodeWithText("Send SAITO").performScrollTo().performClick()
                            val included = "Included in block 2000002."
                            ui.waitUntil(20_000) { ui.onNodeWithText(included).isDisplayed() }
                            ui.onNodeWithText(included).assertIsDisplayed()
                            ui.onNodeWithText("Observed on the selected server. This status can change.")
                                .performScrollTo().assertIsDisplayed()
                            ui.onAllNodesWithText("Pending. Inclusion has not been verified.").assertCountEquals(0)
                            ui.onAllNodesWithText("Retry transfer").assertCountEquals(0)
                            ui.onNodeWithText("Transfer server: saito.davidengelen.nl.").performScrollTo().assertIsDisplayed()
                            ui.onNodeWithText("Available to send: 0 SAITO").performScrollTo().assertIsDisplayed()
                            peer.assertHealthy(); assertTrue(peer.transactions.isEmpty())
                            captureNativeScreen(File(context.cacheDir, "transfer-evidence/included-payment.png"), included)
                            for ((name, block) in listOf("inclusion" to fixture.inclusion, "tip" to fixture.tip)) {
                                File(context.cacheDir, "transfer-evidence/$name-block.b64")
                                    .writeText(Base64.getEncoder().encodeToString(block.second))
                            }
                            reopenTransfers(scenario, fixture.payment.author)
                            ui.waitUntil(20_000) { ui.onNodeWithText(included).isDisplayed() }
                            ui.onNodeWithText("Observed on the selected server. This status can change.")
                                .performScrollTo().assertIsDisplayed()
                            ui.onAllNodesWithText("Pending. Inclusion has not been verified.").assertCountEquals(0)
                            ui.onAllNodesWithText("Retry transfer").assertCountEquals(0)
                            ui.onNodeWithText("Transfer server: saito.davidengelen.nl.").performScrollTo().assertIsDisplayed()
                            ui.onNodeWithText("Available to send: 0 SAITO").performScrollTo().assertIsDisplayed()
                            peer.assertHealthy(); assertTrue(peer.transactions.isEmpty())
                            captureNativeScreen(File(context.cacheDir, "transfer-evidence/included-reopened.png"), included)

                            // Missing blocks cannot prove a reorg or unlock the original positive inputs.
                            phase.set(1)
                            reopenTransfers(scenario, fixture.payment.author)
                            withFailureCapture({ captureNativeScreen(File(context.cacheDir,
                                "transfer-evidence/history-first-failure.png")) }) {
                                ui.waitUntil(20_000) { positiveSnapshots.get() > 0 && unavailableBlockReads.get() > 0 }
                                // Fresh snapshot again lists all original7.5SAITO; reservations still leave zero.
                                ui.waitUntil(20_000) { ui.onNodeWithText("Available to send: 0 SAITO").isDisplayed() }
                                val unknown = "Current inclusion unknown. Last seen in block 2000002."
                                ui.waitUntil(20_000) { ui.onNodeWithText(unknown).isDisplayed() }
                                ui.onAllNodesWithText(included).assertCountEquals(0)
                                ui.onAllNodesWithText("Pending. Inclusion has not been verified.").assertCountEquals(0)
                                ui.onNodeWithText("Transfer server: saito.davidengelen.nl.").performScrollTo().assertIsDisplayed()
                                ui.onNodeWithText("Available to send: 0 SAITO").performScrollTo().assertIsDisplayed()
                                peer.assertHealthy(); assertTrue(peer.transactions.isEmpty())
                                captureNativeScreen(File(context.cacheDir, "transfer-evidence/history-unknown.png"), unknown)
                            }

                            // Only verified coverage of the old height can establish displacement.
                            phase.set(2)
                            reopenTransfers(scenario, fixture.payment.author)
                            withFailureCapture({ captureNativeScreen(File(context.cacheDir,
                                "transfer-evidence/reorganization-first-failure.png")) }) {
                                ui.waitUntil(20_000) { alternateBlockReads.get() > 0 }
                                ui.waitUntil(20_000) { ui.onNodeWithText("Available to send: 0 SAITO").isDisplayed() }
                                for ((name, block) in listOf("alternate" to fixture.alternate, "alternate-tip" to fixture.alternateTip)) {
                                    File(context.cacheDir, "transfer-evidence/$name-block.b64")
                                        .writeText(Base64.getEncoder().encodeToString(block.second))
                                }
                                val reorganized = "Chain changed. Previous inclusion in block 2000002 was reorganized."
                                ui.waitUntil(20_000) { ui.onNodeWithText(reorganized).isDisplayed() }
                                ui.onNodeWithText("Observed on the selected server. This status can change.")
                                    .performScrollTo().assertIsDisplayed()
                                ui.onAllNodesWithText(included).assertCountEquals(0)
                                ui.onAllNodesWithText("Current inclusion unknown. Last seen in block 2000002.").assertCountEquals(0)
                                ui.onAllNodesWithText("Pending. Inclusion has not been verified.").assertCountEquals(0)
                                ui.onNodeWithText("Transfer server: saito.davidengelen.nl.").performScrollTo().assertIsDisplayed()
                                ui.onNodeWithText("Available to send: 0 SAITO").performScrollTo().assertIsDisplayed()
                                peer.assertHealthy(); assertTrue(peer.transactions.isEmpty())
                                captureNativeScreen(File(context.cacheDir, "transfer-evidence/reorganized-payment.png"), reorganized)
                                reopenTransfers(scenario, fixture.payment.author)
                                ui.waitUntil(20_000) { ui.onNodeWithText(reorganized).isDisplayed() }
                                ui.onNodeWithText("Observed on the selected server. This status can change.")
                                    .performScrollTo().assertIsDisplayed()
                                ui.onAllNodesWithText(included).assertCountEquals(0)
                                ui.onAllNodesWithText("Current inclusion unknown. Last seen in block 2000002.").assertCountEquals(0)
                                ui.onAllNodesWithText("Pending. Inclusion has not been verified.").assertCountEquals(0)
                                ui.onNodeWithText("Transfer server: saito.davidengelen.nl.").performScrollTo().assertIsDisplayed()
                                ui.onNodeWithText("Available to send: 0 SAITO").performScrollTo().assertIsDisplayed()
                                peer.assertHealthy(); assertTrue(peer.transactions.isEmpty())
                                captureNativeScreen(File(context.cacheDir, "transfer-evidence/reorganized-reopened.png"), reorganized)
                            }

                            // Reobserved exact inclusion leaves its distinct4.15 change available.
                            phase.set(3)
                            reopenTransfers(scenario, fixture.payment.author)
                            withFailureCapture({ captureNativeScreen(File(context.cacheDir,
                                "transfer-evidence/change-first-failure.png")) }) {
                                ui.waitUntil(20_000) { changeSnapshots.get() > 0 }
                                ui.waitUntil(20_000) { ui.onNodeWithText("Available to send: 4.15 SAITO").isDisplayed() }
                                ui.onNodeWithText(included).assertIsDisplayed()
                                ui.onAllNodesWithText("Retry transfer").assertCountEquals(0)
                                val recipient = "pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU"
                                ui.onNodeWithContentDescription("Recipient address").performScrollTo().performTextInput(recipient)
                                ui.onNodeWithContentDescription("Amount in SAITO").performScrollTo().performTextInput("0.25")
                                ui.onNodeWithContentDescription("Fee in SAITO").performScrollTo().performTextReplacement("0.1")
                                reviewNativeTransfer(ui, UiDevice.getInstance(instrumentation))
                                ui.waitUntil(20_000) { ui.onNodeWithText("Confirm SAITO transfer").isDisplayed() }
                                ui.onNodeWithText("Recipient: $recipient").assertIsDisplayed()
                                ui.onNodeWithText("Amount: 0.25 SAITO").assertIsDisplayed()
                                ui.onNodeWithText("Fee: 0.1 SAITO").assertIsDisplayed()
                                ui.onNodeWithText("Send through saito.davidengelen.nl").assertIsDisplayed()
                                peer.assertHealthy(); assertTrue(peer.transactions.isEmpty())
                                captureNativeScreen(File(context.cacheDir, "transfer-evidence/change-reviewed.png"), "Confirm SAITO transfer")
                                ui.onNode(hasText("Done") and hasAnyAncestor(isDialog() and
                                    hasAnyDescendant(hasText("Confirm SAITO transfer")))).performClick()
                                peer.assertHealthy(); assertTrue(peer.transactions.isEmpty())
                            }
                        }
                    }
                }
            }
        } catch (failure: Throwable) { primaryFailure = failure; throw failure }
        finally {
            try {
                val current = wallets.loadExisting()
                when (current.exportJson()) {
                    priorWallet.exportJson() -> Unit
                    fixture.wallet.exportJson() -> wallets.replace(current, priorWallet)
                    else -> error("Unexpected inclusion fixture wallet state")
                }
                check(wallets.loadExisting().exportJson() == priorWallet.exportJson())
            } catch (cleanup: Throwable) {
                val primary = primaryFailure
                if (primary == null) throw cleanup else primary.addSuppressed(cleanup)
            }
        }
    }

    private fun reopenTransfers(scenario: ActivityScenario<MainActivity>, author: String) {
        scenario.recreate()
        ui.waitUntil(15_000) { ui.onNodeWithText("Wallet").isDisplayed() }
        ui.onNodeWithText("Wallet").performClick()
        ui.onNodeWithContentDescription("Wallet address").assertTextEquals(author)
        ui.onNodeWithText("Send SAITO").performScrollTo().performClick()
    }
}
