package nl.davidengelen.redsquare.saito

import java.math.BigInteger

/** Selected-server asset indicator for the import guard, not independent ledger proof. */
enum class ImportBalance { UNKNOWN, ZERO, POSITIVE;
    companion object {
        const val MAX_SNAPSHOT_BYTES = 2 * 1024 * 1024
        private val unsigned = Regex("[0-9]{1,20}")
        private val header = Regex("([0-9]{1,20})-([0-9]{1,20})-([0-9a-fA-F]{64})\\.snap")

        fun assess(snapshot: ByteArray, publicKey: String, nowMillis: Long): ImportBalance = try {
            require(snapshot.size in 1..MAX_SNAPSHOT_BYTES)
            require(snapshot.all { it >= 0 }) // The source format is ASCII.
            val text = snapshot.toString(Charsets.US_ASCII)
            require(text.endsWith('\n'))
            val lines = text.dropLast(1).split('\n')
            val tip = requireNotNull(header.matchEntire(lines.first()))
            val timestamp = requireNotNull(tip.groupValues[1].toLongOrNull())
            require(timestamp > 0 && timestamp >= nowMillis - 15 * 60_000L && timestamp <= nowMillis + 60_000L)
            val height = u64(tip.groupValues[2])
            require(height.signum() > 0 && tip.groupValues[3].any { it != '0' })
            var total = BigInteger.ZERO
            val locations = HashSet<String>()
            for (row in lines.drop(1)) {
                val columns = row.split(' ')
                require(columns.size == 6 && columns[0] == publicKey)
                val block = u64(columns[1])
                require(block <= height)
                u64(columns[2])
                require(columns[3].matches(unsigned) && columns[3].toIntOrNull() in 0..255)
                require(columns[5].matches(unsigned) && columns[5].toIntOrNull() in 0..10)
                require(locations.add("${columns[1]}:${columns[2]}:${columns[3]}"))
                total += u64(columns[4])
                require(total.bitLength() <= 64)
            }
            if (total.signum() == 0) ZERO else POSITIVE
        } catch (_: IllegalArgumentException) { UNKNOWN }

        private fun u64(value: String): BigInteger {
            require(value.matches(unsigned))
            return BigInteger(value).also { require(it.bitLength() <= 64) }
        }
    }
}
