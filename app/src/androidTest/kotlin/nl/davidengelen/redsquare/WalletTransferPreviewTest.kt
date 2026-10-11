package nl.davidengelen.redsquare

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import nl.davidengelen.redsquare.saito.SaitoWallet
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList

class WalletTransferPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun selectedServerBalanceAndReceivingAddressCanBeViewedAndCopied() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("nl.davidengelen.redsquare.dev", context.packageName)
        val requestedKeys = CopyOnWriteArrayList<String>()
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.target
                val key = path.removePrefix("/balance/")
                if (path != "/balance/$key" || !SaitoWallet.isPublicKey(key)) {
                    return MockResponse.Builder().code(400).build()
                }
                requestedKeys.add(key)
                return MockResponse.Builder().body(
                    "${System.currentTimeMillis()}-1234-${"a".repeat(64)}.snap\n" +
                        "$key 1001 7 2 500000000 0\n" +
                        "$key 1002 9 0 250000000 7\n").build()
            }
        }
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedDisabled", true)
                .putExtra("balanceApiUrl", "http://127.0.0.1:${server.port}/balance/")).use { scenario ->
                ui.onNodeWithText("Wallet").performClick()
                val address = displayedWalletAddress(ui)
                withFailureCapture({ captureNativeScreen(File(context.cacheDir,
                    "transfer-evidence/balance-first-failure.png")) }) {
                    ui.onNodeWithText("Balance and receive").performScrollTo().performClick()
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("7.5 SAITO"))
                        .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.onNodeWithText("7.5 SAITO").assertIsDisplayed()
                    ui.onNodeWithText("Balance from saito.davidengelen.nl").assertIsDisplayed()
                    ui.onNodeWithContentDescription("Receiving address").assertTextEquals(address)
                    assertEquals(listOf(address), requestedKeys.toList())
                    ui.onNodeWithText("Copy address").performClick()
                    scenario.onActivity { activity ->
                        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        assertEquals(address, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
                    }
                    captureNativeScreen(File(context.cacheDir, "transfer-evidence/balance-and-receive.png"), "7.5 SAITO")
                }
            }
        } finally { server.close() }
    }
}
