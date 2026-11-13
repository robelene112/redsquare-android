package nl.davidengelen.redsquare.saito

import java.math.BigDecimal
import java.math.BigInteger

/** A complete, fresh selected-server observation. It is not independent ledger proof. */
class SaitoBalanceSnapshot private constructor(
    val publicKey: String,
    val timestamp: Long,
    val height: BigInteger,
    val blockHash: String,
    slips: List<Slip>,
    val total: BigInteger,
    nfts: List<SaitoNft>,
) {
    val slips: List<Slip> = java.util.Collections.unmodifiableList(slips.toList())
    val nfts: List<SaitoNft> = java.util.Collections.unmodifiableList(nfts.toList())
    data class Slip(val block: BigInteger, val ordinal: BigInteger, val index: Int,
        val amount: BigInteger, val type: Int)

    val saito: String get() = BigDecimal(total, 8).stripTrailingZeros().toPlainString()

    companion object {
        const val MAX_BYTES = 2 * 1024 * 1024
        private val unsigned = Regex("[0-9]{1,20}")
        private val header = Regex("([0-9]{1,20})-([0-9]{1,20})-([0-9a-fA-F]{64})\\.snap")

        fun parse(snapshot: ByteArray, publicKey: String, nowMillis: Long): SaitoBalanceSnapshot {
            require(SaitoWallet.isPublicKey(publicKey))
            require(snapshot.size in 1..MAX_BYTES && snapshot.all { it >= 0 })
            val text = snapshot.toString(Charsets.US_ASCII)
            require(text.endsWith('\n'))
            val lines = text.dropLast(1).split('\n')
            val tip = requireNotNull(header.matchEntire(lines.first()))
            val timestamp = requireNotNull(tip.groupValues[1].toLongOrNull())
            require(timestamp > 0 && timestamp >= nowMillis - 15 * 60_000L && timestamp <= nowMillis + 60_000L)
            val height = u64(tip.groupValues[2])
            require(height.signum() > 0 && tip.groupValues[3].any { it != '0' })
            val locations = HashSet<Triple<BigInteger, BigInteger, Int>>()
            val rows = lines.drop(1).map { row ->
                val columns = row.split(' ')
                require(columns.size == 6 && Base58.decode(columns[0]).size == 33)
                val block = u64(columns[1])
                require(block <= height)
                val ordinal = u64(columns[2])
                require(columns[3].matches(unsigned) && columns[3].toIntOrNull() in 0..255)
                require(columns[5].matches(unsigned) && columns[5].toIntOrNull() in 0..10)
                val index = columns[3].toInt()
                require(locations.add(Triple(block, ordinal, index)))
                val amount = u64(columns[4])
                SaitoNft.Row(columns[0], Slip(block, ordinal, index, amount, columns[5].toInt()))
            }
            val slips = ArrayList<Slip>()
            val nfts = ArrayList<SaitoNft>()
            var index = 0
            while (index < rows.size) {
                val current = rows[index]
                require(current.publicKey == publicKey)
                if (index + 2 < rows.size && current.position.type in setOf(0, 1) &&
                    rows[index + 1].position.type == 9 && rows[index + 2].position.type == 9) {
                    nfts.add(SaitoNft.fromSnapshot(current, rows[index + 1], rows[index + 2]))
                    index += 3
                } else {
                    require(current.position.type != 9) { "Incomplete NFT tuple" }
                    slips.add(current.position)
                    index++
                }
            }
            val total = slips.fold(BigInteger.ZERO) { sum, slip ->
                (sum + slip.amount).also { require(it.bitLength() <= 64) }
            }
            return SaitoBalanceSnapshot(publicKey, timestamp, height, tip.groupValues[3].lowercase(), slips, total, nfts)
        }

        private fun u64(value: String): BigInteger {
            require(value.matches(unsigned))
            return BigInteger(value).also { require(it.bitLength() <= 64) }
        }
    }
}
