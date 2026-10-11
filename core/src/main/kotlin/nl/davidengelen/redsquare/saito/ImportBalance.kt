package nl.davidengelen.redsquare.saito

/** Selected-server asset indicator for the import guard, not independent ledger proof. */
enum class ImportBalance { UNKNOWN, ZERO, POSITIVE;
    companion object {
        const val MAX_SNAPSHOT_BYTES = SaitoBalanceSnapshot.MAX_BYTES

        fun assess(snapshot: ByteArray, publicKey: String, nowMillis: Long): ImportBalance = try {
            val total = SaitoBalanceSnapshot.parse(snapshot, publicKey, nowMillis).total
            if (total.signum() == 0) ZERO else POSITIVE
        } catch (_: IllegalArgumentException) { UNKNOWN }
    }
}
