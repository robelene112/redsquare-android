package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.net.InetAddress

/** Real import preview assesses an isolated outgoing identity, without replacing it. */
class LikeWalletImportPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun prepare() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
    }

    @Test fun uncertainSocialLikeIsDisclosedBeforeReplacingItsWallet() {
        previewLike(pending = true)
    }

    @Test fun settledLikeAndRateReceiptsAloneDoNotInventPendingWork() {
        previewLike(pending = false)
    }

    private fun previewLike(pending: Boolean) {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .putExtra("feedDisabled", true)).use {
            waitFor("Wallet")
            ui.onNodeWithText("Wallet").performClick()
            displayedWalletAddress(ui)
            ui.onNodeWithText("Done").performClick()
        }
        val store = AndroidWalletStore(context)
        val original = store.loadExisting()
        val isolated = SaitoWallet.create()
        val scalar = if (isolated.publicKey == "pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU") "3" else "2"
        val incoming = SaitoWallet.fromPrivateKey("0".repeat(63) + scalar) // Published unsafe fixture only.
        val filename = "00-social-import-$scalar.json"
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        check(device.executeShellCommand("run-as nl.davidengelen.redsquare.dev.test stat -c %s files/wallet-test-documents/$filename")
            .trim().toLongOrNull()?.let { it > 0 } == true) { "Social import fixture was not provisioned" }
        val peer = PostLikePeer().peer.apply { disconnectAfterTransaction = pending }
        var primaryFailure: Throwable? = null
        try {
            store.replace(original, isolated)
            MockWebServer().use { balances ->
                balances.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest) = MockResponse.Builder()
                        .body("${System.currentTimeMillis()}-1234-${"a".repeat(64)}.snap\n").build()
                }
                balances.start(InetAddress.getByName("127.0.0.1"), 0)
                peer.start().use { server ->
                    ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                        .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                        .putExtra("balanceApiUrl", "http://127.0.0.1:${balances.port}/balance/")).use {
                        withFailureCapture({ captureFeedEvidence("like-wallet-evidence/${if (pending) "pending" else "settled"}-failure.png") }) {
                            waitFor("4 likes")
                            ui.clickVisiblePostContent("Native Red Square reference post")
                            waitFor("Conversation")
                            ui.clickVisiblePostContent("Like")
                            if (pending) {
                                ui.waitUntil(20_000) { ui.onAllNodes(hasText("Retry like") and isEnabled())
                                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                            } else waitFor("5 likes")
                            assertEquals(1, peer.transactions.size)
                            ui.onNodeWithText("Wallet").performClick()
                            assertEquals(isolated.publicKey, displayedWalletAddress(ui))
                            ui.onNodeWithText("Import wallet").performScrollTo().performClick()
                            chooseWalletImport(ui, filename, incoming.publicKey)
                            waitFor("Node reports zero SAITO")
                            val warning = "Pending work belongs to your current wallet"
                            if (pending) ui.onNodeWithText(warning).performScrollTo().assertIsDisplayed()
                            else ui.onNodeWithText(warning).assertDoesNotExist()
                            ui.onNodeWithText("Confirm replacement").performScrollTo().assertIsNotEnabled()
                            assertEquals(isolated.publicKey, displayedWalletAddress(ui))
                            assertEquals(1, peer.transactions.size)
                            peer.assertHealthy()
                            captureFeedEvidence("like-wallet-evidence/${if (pending) "pending-work-disclosed" else "settled-receipts-not-pending"}.png")
                        }
                    }
                }
            }
        } catch (failure: Throwable) { primaryFailure = failure; throw failure }
        finally {
            // Every Activity/peer is closed before restoring the captured identity.
            cleanupAfterTest(primaryFailure, {
                val current = store.loadExisting()
                check(current.publicKey in setOf(original.publicKey, isolated.publicKey, incoming.publicKey))
                if (current.exportJson() != original.exportJson()) store.replace(current, original)
                assertTrue("Social import fixture must restore its original wallet",
                    store.loadExisting().exportJson() == original.exportJson())
            })
        }
    }

    private fun waitFor(text: String) {
        ui.waitUntil(20_000) { ui.onAllNodes(hasText(text))
            .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
    }
}
