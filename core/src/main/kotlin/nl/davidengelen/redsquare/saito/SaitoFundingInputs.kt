package nl.davidengelen.redsquare.saito

import java.math.BigInteger

/** Shared funded-wire mechanics. Factories independently admit their own destinations and message schemas. */
internal object SaitoFundingInputs {
    fun checked(inputs: List<SaitoBalanceSnapshot.Slip>): BigInteger {
            require(inputs.size in 1..254)
            val locations = HashSet<Triple<BigInteger, BigInteger, Int>>()
            var total = BigInteger.ZERO
            inputs.forEach { slip ->
                require(slip.block.signum() > 0 && slip.block.bitLength() <= 64)
                require(slip.ordinal.signum() >= 0 && slip.ordinal.bitLength() <= 64)
                require(slip.index in 0..255 && slip.type in setOf(0, 1, 5, 7))
                require(slip.amount.signum() > 0 && slip.amount.bitLength() <= 64)
                require(locations.add(Triple(slip.block, slip.ordinal, slip.index)))
                total += slip.amount
                require(total.bitLength() <= 64)
            }
            return total
        }

    fun slip(bytes: ByteArray, offset: Int) = SaitoBalanceSnapshot.Slip(
            BigInteger(1, bytes.copyOfRange(offset + 41, offset + 49)),
            BigInteger(1, bytes.copyOfRange(offset + 49, offset + 57)), bytes[offset + 57].toInt() and 255,
            BigInteger(1, bytes.copyOfRange(offset + 33, offset + 41)), bytes[offset + 58].toInt() and 255)
}
