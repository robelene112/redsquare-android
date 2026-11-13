package nl.davidengelen.redsquare

import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Test

class VaultKeyReservationTest {
    @Test fun anUncertainKeyTransferKeepsTheExactWholeKeyReservedAcrossRestartAndNodes() =
        withVaultReadingFixture("key-reservation", captureFolder = "vault-key-reservation-evidence", retainTransfers = true) { scene ->
            val vector = InstrumentationRegistry.getInstrumentation().context.assets.open("native-vault-transfer-reference.json")
                .reader().use { JsonParser.parseReader(it).asJsonObject }
            val candidate = SaitoNftTransfer.restore(vector.getAsJsonObject("transfer").get("networkBase64").asString)
            val store = TransferStore(scene.context, candidate.author)
            assertEquals(1, store.availableKeys(listOf(candidate.holding)).size)
            store.reserveKey(SaitoBackend.OWNER, candidate)
            val reopened = TransferStore(scene.context, candidate.author)
            assertTrue(reopened.hasWork())
            assertTrue(reopened.availableKeys(listOf(candidate.holding)).isEmpty())
            assertEquals(candidate.transaction.encoded(), reopened.pendingKeys().single().transfer.transaction.encoded())
            reopened.reserveKey(SaitoBackend.OWNER, candidate)
            assertEquals(1, reopened.pendingKeys().size)
            assertThrows(IllegalArgumentException::class.java) { reopened.reserveKey(SaitoBackend.OFFICIAL, candidate) }
            assertThrows(IllegalStateException::class.java) {
                reopened.releaseUnsubmittedKey(SaitoBackend.OWNER, candidate) { error("Not verified unsent") }
            }
            reopened.releaseUnsubmittedKey(SaitoBackend.OWNER, candidate) { }
            assertEquals(1, reopened.availableKeys(listOf(candidate.holding)).size)
            reopened.reserveKey(SaitoBackend.OWNER, candidate)
            reopened.markKeyMayHaveSent(SaitoBackend.OWNER, candidate)
            val uncertain = TransferStore(scene.context, candidate.author)
            assertTrue(uncertain.pendingKeys().single().mayHaveSent)
            assertThrows(IllegalArgumentException::class.java) { uncertain.releaseUnsubmittedKey(SaitoBackend.OWNER, candidate) { } }
            assertTrue(uncertain.availableKeys(listOf(candidate.holding)).isEmpty())
            assertTrue(scene.peer.transactions.isEmpty()); assertTrue(scene.peer.vaultUploads.isEmpty())
        }
}
