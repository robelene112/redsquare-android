package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class SaitoNftTransferTest {
    private fun resource(name: String) = requireNotNull(javaClass.getResourceAsStream("/$name.json"))
        .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }

    @Test fun permanentTransferMovesTheEntireIndependentNftTupleAndLockedDeposit() {
        val source = resource("native-vault-reference")
        val vector = resource("native-vault-transfer-reference")
        val holding = SaitoBalanceSnapshot.parse(source.get("snapshot").asString.toByteArray(),
            source.get("owner").asString, 1700001000000L).nfts.single()
        val key = SaitoVault.readKey(holding, source.getAsJsonObject("key").get("envelope").asString)
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Published unsafe identity only.
        val transfer = SaitoNftTransfer.create(wallet, key, vector.get("recipient").asString, vector.get("timestamp").asLong)
        assertEquals(vector.getAsJsonObject("transfer").get("networkBase64").asString, transfer.transaction.encoded())
        assertEquals(holding.quantity, transfer.holding.quantity)
        assertEquals(holding.deposit, transfer.holding.deposit)
        assertEquals(holding.witnesses, transfer.holding.witnesses)
        val retained = SaitoNftTransfer.restore(transfer.transaction.encoded())
        assertEquals(transfer.transaction.encoded(), retained.transaction.encoded())
        assertEquals(vector.get("recipient").asString, retained.recipient)
        assertThrows(IllegalArgumentException::class.java) { SaitoTransaction.restore(transfer.transaction.encoded()) }
    }

    @Test fun transferRequiresTheActualCurrentOwnerAndAnotherValidRecipient() {
        val source = resource("native-vault-reference")
        val vector = resource("native-vault-transfer-reference")
        val holding = SaitoBalanceSnapshot.parse(source.get("snapshot").asString.toByteArray(),
            source.get("owner").asString, 1700001000000L).nfts.single()
        val key = SaitoVault.readKey(holding, source.getAsJsonObject("key").get("envelope").asString)
        val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        assertThrows(IllegalArgumentException::class.java) { SaitoNftTransfer.create(owner, key, owner.publicKey, 1700001000001L) }
        assertThrows(IllegalArgumentException::class.java) { SaitoNftTransfer.create(owner, key, "invalid", 1700001000001L) }
        assertThrows(IllegalArgumentException::class.java) {
            SaitoNftTransfer.create(SaitoWallet.fromPrivateKey("0".repeat(63) + "1"), key, vector.get("recipient").asString, 1700001000001L)
        }
    }
}
