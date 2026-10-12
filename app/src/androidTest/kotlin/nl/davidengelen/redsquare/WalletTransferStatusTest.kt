package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.net.InetAddress
import java.util.Base64
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

    @Test fun anExistingExactPaymentShowsVerifiedSelectedChainInclusionWithoutResending() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val fixture = instrumentation.context.assets.open("native-block-reference.json")
            .bufferedReader().use { TransferInclusionFixture(it.readText()) }
        val wallets = AndroidWalletStore(context)
        val priorWallet = wallets.loadOrCreate()
        context.noBackupFilesDir.listFiles()?.filter { it.name.startsWith("transfer.") }?.forEach { check(it.delete()) }
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        wallets.replace(priorWallet, fixture.wallet)
        try {
            // Pre-existing independently signed candidate, not an authentication/send bypass.
            TransferStore(context, fixture.wallet.publicKey).reserve(SaitoBackend.OWNER, fixture.payment)
            val peer = ControlledFeedPeer("[]")
            MockWebServer().use { server ->
                server.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse = when (request.target) {
                        "/options" -> MockResponse.Builder().body("""{"consensus":{"genesis_period":80640}}""").build()
                        "/balance/${fixture.wallet.publicKey}" -> MockResponse.Builder()
                            .body("${fixture.timestamp}-2000003-${fixture.tip.first}.snap\n").build()
                        else -> fixture.blocks[request.target.removePrefix("/block/")]?.let {
                            MockResponse.Builder().body(Buffer().write(it)).build()
                        } ?: MockResponse.Builder().code(404).build()
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
                            peer.assertHealthy(); assertTrue(peer.transactions.isEmpty())
                            captureNativeScreen(File(context.cacheDir, "transfer-evidence/included-reopened.png"), included)
                        }
                    }
                }
            }
        } finally { wallets.replace(wallets.loadExisting(), priorWallet) }
    }
}
