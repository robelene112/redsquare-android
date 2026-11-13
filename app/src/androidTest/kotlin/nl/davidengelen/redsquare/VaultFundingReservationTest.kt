package nl.davidengelen.redsquare

import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class VaultFundingReservationTest {
    @Test fun aRetainedVaultMintBlocksReuseByPaymentsOrOtherMintsAcrossNodes() =
        withVaultReadingFixture("mint-reservation", captureFolder = "vault-funding-evidence", retainTransfers = true) { scene ->
            val reference = InstrumentationRegistry.getInstrumentation().context.assets.open("native-vault-mint-reference.json")
                .reader().use { JsonParser.parseReader(it).asJsonObject }
            val candidate = SaitoVaultMint.readCandidate(reference.getAsJsonObject("mint").get("networkBase64").asString)
            val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Published unsafe fixture only.
            val store = TransferStore(scene.context, wallet.publicKey)
            assertEquals(candidate.inputs, store.available(candidate.inputs))
            store.reserveMint(SaitoBackend.OWNER, candidate)
            val reopened = TransferStore(scene.context, wallet.publicKey)
            assertTrue(reopened.available(candidate.inputs).isEmpty())
            assertEquals(candidate.transaction.encoded(), reopened.pendingMints().single().candidate.transaction.encoded())
            // An exact retry is idempotent; neither another node nor another action can take its inputs.
            reopened.reserveMint(SaitoBackend.OWNER, candidate)
            assertEquals(1, reopened.pendingMints().size)
            val payment = SaitoPayment.create(wallet, candidate.inputs, reference.get("destination").asString,
                BigInteger("50000000"), BigInteger.ZERO, 1700001000000L, "owned-reservation-conflict", "Owned fixture")
            assertThrows(IllegalArgumentException::class.java) { reopened.reserve(SaitoBackend.OFFICIAL, payment) }
            val other = SaitoVaultMint.create(wallet, candidate.inputs, "Another file.txt", "text/plain",
                "Another file".toByteArray(), 1700001000001L)
            assertThrows(IllegalArgumentException::class.java) {
                reopened.reserveMint(SaitoBackend.OFFICIAL, SaitoVaultMint.readCandidate(other.mint.encoded()))
            }
            assertTrue(scene.peer.transactions.isEmpty())
            assertTrue(scene.peer.vaultUploads.isEmpty())
        }
}
