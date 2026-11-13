package nl.davidengelen.redsquare.saito

/** Selected-server asset indicator for the import guard, not independent ledger proof. */
enum class ImportBalance { UNKNOWN, ZERO, POSITIVE;
    companion object {
        const val MAX_SNAPSHOT_BYTES = SaitoBalanceSnapshot.MAX_BYTES

        fun assess(snapshot: ByteArray, publicKey: String, nowMillis: Long): ImportBalance = try {
            val observed = SaitoBalanceSnapshot.parse(snapshot, publicKey, nowMillis)
            if (observed.total.signum() == 0 && observed.nfts.isEmpty()) ZERO else POSITIVE
        } catch (_: IllegalArgumentException) { UNKNOWN }
    }
}
