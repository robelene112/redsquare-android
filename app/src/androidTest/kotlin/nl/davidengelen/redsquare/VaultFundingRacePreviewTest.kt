package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.math.BigInteger

class VaultFundingRacePreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun anotherPaymentReservingTheReviewedInputDuringAuthenticationLeavesVaultUnsentAndDiscardable() = withVaultDeviceCredential { device ->
        withVaultReadingFixture("funding-race", captureFolder = "vault-funding-race-evidence", retainTransfers = true,
            minting = true, expectedMintCount = 0) { scene ->
            ui.reviewVaultFile(scene, "vault-reservation-race.bin", "Keep this file while another wallet action reserves its input".toByteArray())
            waitForSystemPin(device)
            val reference = InstrumentationRegistry.getInstrumentation().context.assets.open("native-vault-mint-reference.json")
                .reader().use { JsonParser.parseReader(it).asJsonObject }
            val inputReference = SaitoVaultMint.readCandidate(reference.getAsJsonObject("mint").get("networkBase64").asString)
            val wallet = AndroidWalletStore(scene.context).loadExisting()
            val store = TransferStore(scene.context, wallet.publicKey)
            val otherPayment = SaitoPayment.create(wallet, inputReference.inputs, reference.get("destination").asString,
                BigInteger("50000000"), BigInteger.ZERO, 1700001000000L, "owned-authentication-race", "Owned fixture")
            // The existing wallet-wide reservation API represents another authorized payment
            // whose input is still reported unspent by the selected node.
            store.reserve(SaitoBackend.OFFICIAL, otherPayment)
            enterRealSystemPin(device)
            ui.waitUntil(15_000) {
                ui.onNodeWithText("The reviewed funds are no longer available. Nothing else was sent.").isDisplayed()
            }
            assertTrue(scene.peer.transactions.isEmpty()); assertTrue(scene.peer.vaultUploads.isEmpty())
            assertTrue(store.pendingMints().isEmpty())
            assertEquals(otherPayment.transaction.encoded(), store.pending().single().payment.transaction.encoded())
            ui.onNodeWithText("Discard file").performScrollTo().assertIsEnabled()
            captureNativeScreen(File(scene.evidence, "funding-changed-original-provably-unsent.png"))
            ui.onNodeWithText("Discard file").performClick()
            ui.waitUntil(10_000) { ui.onNodeWithText("Your files").isDisplayed() }
            assertTrue(store.pendingMints().isEmpty())
            assertEquals(otherPayment.transaction.encoded(), store.pending().single().payment.transaction.encoded())
            captureNativeScreen(File(scene.evidence, "unsent-vault-discarded-other-payment-retained.png"))
        }
    }
}
