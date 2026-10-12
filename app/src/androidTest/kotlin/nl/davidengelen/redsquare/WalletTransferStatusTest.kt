package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.net.InetAddress
import java.util.Base64
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
                                else -> "${fixture.timestamp + 2000}-2000003-${fixture.alternateTip.first}.snap\n"
                            }
                            MockResponse.Builder().body(header + if (currentPhase == 0) "" else originalInputs).build()
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
                            scenario.recreate()
                            ui.waitUntil(15_000) { ui.onNodeWithText("Wallet").isDisplayed() }
                            ui.onNodeWithText("Wallet").performClick()
                            ui.onNodeWithContentDescription("Wallet address").assertTextEquals(fixture.payment.author)
                            ui.onNodeWithText("Send SAITO").performScrollTo().performClick()
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
                            scenario.recreate()
                            ui.waitUntil(15_000) { ui.onNodeWithText("Wallet").isDisplayed() }
                            ui.onNodeWithText("Wallet").performClick()
                            ui.onNodeWithContentDescription("Wallet address").assertTextEquals(fixture.payment.author)
                            ui.onNodeWithText("Send SAITO").performScrollTo().performClick()
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
                            scenario.recreate()
                            ui.waitUntil(15_000) { ui.onNodeWithText("Wallet").isDisplayed() }
                            ui.onNodeWithText("Wallet").performClick()
                            ui.onNodeWithContentDescription("Wallet address").assertTextEquals(fixture.payment.author)
                            ui.onNodeWithText("Send SAITO").performScrollTo().performClick()
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
                                scenario.recreate()
                                ui.waitUntil(15_000) { ui.onNodeWithText("Wallet").isDisplayed() }
                                ui.onNodeWithText("Wallet").performClick()
                                ui.onNodeWithContentDescription("Wallet address").assertTextEquals(fixture.payment.author)
                                ui.onNodeWithText("Send SAITO").performScrollTo().performClick()
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
}
