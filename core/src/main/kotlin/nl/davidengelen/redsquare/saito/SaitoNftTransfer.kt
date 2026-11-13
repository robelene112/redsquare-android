package nl.davidengelen.redsquare.saito

import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.Base64

/** One whole Vault key and its locked deposit. No partial transfer or implicit temporary access. */
class SaitoNftTransfer private constructor(val transaction: SaitoTransaction, val holding: SaitoNft,
    val key: VaultKey, val recipient: String, val timestamp: Long) {
    val author: String get() = holding.owner
    companion object {
        fun create(wallet: SaitoWallet, key: VaultKey, recipient: String, timestamp: Long): SaitoNftTransfer {
            require(key.holding.owner == wallet.publicKey && SaitoWallet.isPublicKey(recipient) && recipient != wallet.publicKey)
            val inputs = key.holding.witnesses.map(::witnessRow)
            val outputs = inputs.mapIndexed { index, row ->
                SaitoTransaction.OwnedSlip(if (index == 1) recipient else row.publicKey, row.position)
            }
            val body = SaitoEnvelope.decodeBound(key.envelope).body
            val transaction = SaitoTransaction.createNftTransfer(wallet, body, timestamp,
                inputs.map { SaitoTransaction.OwnedSlip(it.publicKey, it.position) }, outputs)
            return restore(transaction.encoded())
        }

        fun restore(encoded: String): SaitoNftTransfer {
            require(encoded.length <= 128 * 1024)
            val bytes = Base64.getDecoder().decode(encoded)
            require(Base64.getEncoder().encodeToString(bytes) == encoded && bytes.size in 448..96 * 1024)
            val header = ByteBuffer.wrap(bytes)
            require(header.getInt(0) == 3 && header.getInt(4) == 3 && header.getInt(12) == 0 &&
                header.getInt(88) == 1 && bytes[92] == 8.toByte() && header.getLong(80) >= 0)
            require(header.getInt(8) > 0 && bytes.size == 93 + 6 * 59 + header.getInt(8))
            val transaction = SaitoTransaction.fromVaultMintBytes(bytes)
            val signed = SaitoEnvelope.decodeBound(transaction.envelope())
            fun row(index: Int) = networkRow(bytes, 93 + index * 59)
            val holding = SaitoNft.fromSnapshot(row(1), row(0), row(2))
            require(holding.owner == signed.author && row(1).position.block.signum() > 0)
            val target = SaitoNft.fromSnapshot(row(4), row(3), row(5))
            require(row(0) == row(3) && row(2) == row(5) && row(1).position == row(4).position &&
                target.id == holding.id && target.owner != holding.owner)
            // The signed metadata still binds the exact creator, file and rules.
            val key = SaitoVault.readKey(holding, transaction.envelope())
            return SaitoNftTransfer(transaction, holding, key, target.owner, signed.createdAt)
        }

        private fun witnessRow(value: String): SaitoNft.Row {
            require(value.matches(Regex("[0-9a-f]{118}")))
            val bytes = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            return SaitoNft.Row(Base58.encode(bytes.copyOfRange(0, 33)), SaitoBalanceSnapshot.Slip(
                BigInteger(1, bytes.copyOfRange(33, 41)), BigInteger(1, bytes.copyOfRange(41, 49)),
                bytes[49].toInt() and 255, BigInteger(1, bytes.copyOfRange(50, 58)), bytes[58].toInt() and 255))
        }
        private fun networkRow(bytes: ByteArray, start: Int) = SaitoNft.Row(Base58.encode(bytes.copyOfRange(start, start + 33)),
            SaitoBalanceSnapshot.Slip(BigInteger(1, bytes.copyOfRange(start + 41, start + 49)),
                BigInteger(1, bytes.copyOfRange(start + 49, start + 57)), bytes[start + 57].toInt() and 255,
                BigInteger(1, bytes.copyOfRange(start + 33, start + 41)), bytes[start + 58].toInt() and 255))
    }
}
